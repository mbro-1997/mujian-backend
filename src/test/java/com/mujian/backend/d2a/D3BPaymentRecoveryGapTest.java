package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mujian.backend.common.enums.PaymentCloseResult;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.order.mapper.CheckoutBatchMapper;
import com.mujian.backend.payment.domain.*;
import com.mujian.backend.payment.job.TradingRecoveryJob;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import com.mujian.backend.payment.service.*;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;

/** B-R1/R2只在既有隔离库制造故障；正式调度入口接续，不用直接reconcile冒充恢复。 */
class D3BPaymentRecoveryGapTest {
  final D3BQuoteTest quoteTest = new D3BQuoteTest();
  D2ApplicationTest fixture;
  PaymentGateway paymentGateway;
  PaymentOrderMapper paymentOrderMapper;
  PaymentWorkTransaction paymentWorkTransaction;
  PaymentExecution paymentExecution;
  TradingRecoveryJob tradingRecoveryJob;
  String batchId;
  PaymentOrderDO originalPayment;

  /** 准备本执行者专属买家、券和批次，保留生产恢复服务及真实事务。 */
  @BeforeEach
  void start() throws Exception {
    quoteTest.start();
    fixture = quoteTest.f;
    quoteTest.input.put("couponId", quoteTest.coupon);
    batchId = quoteTest.submitQuoted(quoteTest.buyer, quoteTest.input);
    paymentGateway = fixture.app.getBean(PaymentGateway.class);
    paymentOrderMapper = fixture.app.getBean(PaymentOrderMapper.class);
    paymentWorkTransaction = fixture.app.getBean(PaymentWorkTransaction.class);
    paymentExecution = fixture.app.getBean(PaymentExecution.class);
    originalPayment = paymentOrderMapper.selectPaymentByBatch(batchId, false);
    tradingRecoveryJob =
        new TradingRecoveryJob(
            fixture.app.getBean(TradingRecovery.class),
            new MockEnvironment().withProperty("mj.trading.recovery-enabled", "true"));
  }

  /** 仅回收本轮隔离身份及其业务后代，不触碰开发库。 */
  @AfterEach
  void stop() {
    quoteTest.stop();
  }

  /** 从未请求渠道的关闭提交后，释放事务失败仍能被定时入口接回。 */
  @Test
  void unattemptedCloseSurvivesReleaseRollback() throws Exception {
    paymentWorkTransaction.closeIntent(batchId, "user_cancel");
    failReleaseThenRecover("closed");
    verify(paymentGateway, never()).create(any());
    verify(paymentGateway, never()).query(any(PaymentGateway.Reference.class));
  }

  /** 已尝试支付到期查明全部CLOSED，提交后同一故障不能漏出扫描。 */
  @Test
  void channelClosedSurvivesReleaseRollback() throws Exception {
    assertNotNull(paymentWorkTransaction.claim(batchId));
    fixture.clock.at = originalPayment.expireAt().plusSeconds(31).toInstant(ZoneOffset.ofHours(8));
    doReturn(fact("CLOSED")).when(paymentGateway).query(any(PaymentGateway.Reference.class));
    failReleaseThenRecover("expired");
  }

  /** 创建前进程退出仅留下claim，定时任务按原号核实不存在并到期收尾。 */
  @Test
  void claimedButNeverDispatchedExpiresThroughScheduler() {
    PaymentWorkTransaction.Ticket stale = paymentWorkTransaction.claim(batchId);
    absentChannel(PaymentCloseResult.not_found);
    fixture.clock.at = originalPayment.expireAt().plusSeconds(31).toInstant(ZoneOffset.ofHours(8));
    tradingRecoveryJob.recover();
    assertReleased("expired");
    assertFalse(paymentWorkTransaction.canDispatchCreate(stale));
    verify(paymentGateway, never()).create(any());
  }

  /** 创建失败后主动取消不因一次不存在提前释放，截止后同一任务正常收尾。 */
  @Test
  void failedCreateCancelWaitsOriginalDeadlineAndRecovers() throws Exception {
    doThrow(unavailable("PAYMENT_CHANNEL_UNAVAILABLE")).when(paymentGateway).create(any());
    paymentExecution.execute(batchId);
    absentChannel(PaymentCloseResult.not_found);
    JsonNode current =
        quoteTest.call(
            "GET", "/api/me/checkout-batches/" + batchId, null, quoteTest.buyer, null, 200);
    quoteTest.call(
        "POST",
        "/api/me/checkout-batches/" + batchId + "/cancel",
        view("expectedVersion", current.path("version").asInt()),
        quoteTest.buyer,
        quoteTest.key(),
        200);
    fixture.clock.at = fixture.clock.at.plusSeconds(31);
    tradingRecoveryJob.recover();
    assertHeld();
    fixture.clock.at = originalPayment.expireAt().toInstant(ZoneOffset.ofHours(8));
    tradingRecoveryJob.recover();
    assertHeld();
    fixture.clock.at = fixture.clock.at.plusSeconds(31);
    tradingRecoveryJob.recover();
    assertReleased("closed");
    verify(paymentGateway, times(1)).create(any());
    current =
        quoteTest.call(
            "GET", "/api/me/checkout-batches/" + batchId, null, quoteTest.buyer, null, 200);
    assertEquals("return_checklist", current.path("nextAction").asString());
  }

  /** 204后仍查无订单也须经同一截止/租约保护，不能永远停留核实。 */
  @Test
  void acknowledgedCloseAndAbsentRequeryCanFinish() {
    paymentWorkTransaction.claim(batchId);
    absentChannel(PaymentCloseResult.accepted);
    fixture.clock.at = originalPayment.expireAt().plusSeconds(31).toInstant(ZoneOffset.ofHours(8));
    tradingRecoveryJob.recover();
    assertReleased("expired");
    verify(paymentGateway, times(2)).query(any(PaymentGateway.Reference.class));
  }

  /** 关单未知、查询未知或已有prepay证据都不能借不存在分支释放占用。 */
  @Test
  void unknownCloseAndKnownPrepayRemainHeld() {
    PaymentWorkTransaction.Ticket ticket = paymentWorkTransaction.claim(batchId);
    absentChannel(PaymentCloseResult.not_found);
    doThrow(unavailable("PAYMENT_CHANNEL_UNAVAILABLE")).when(paymentGateway).close(any());
    fixture.clock.at = originalPayment.expireAt().plusSeconds(31).toInstant(ZoneOffset.ofHours(8));
    tradingRecoveryJob.recover();
    assertHeld();
    doReturn(PaymentCloseResult.not_found).when(paymentGateway).close(any());
    doThrow(unavailable("PAYMENT_CHANNEL_UNAVAILABLE"))
        .when(paymentGateway)
        .query(any(PaymentGateway.Reference.class));
    fixture.clock.at = fixture.clock.at.plusSeconds(301);
    tradingRecoveryJob.recover();
    assertHeld();
    paymentWorkTransaction.finish(ticket, "late-original-prepay", null);
    absentChannel(PaymentCloseResult.not_found);
    fixture.clock.at = fixture.clock.at.plusSeconds(301);
    tradingRecoveryJob.recover();
    assertHeld();
    doReturn(fact("CLOSED")).when(paymentGateway).query(any(PaymentGateway.Reference.class));
    fixture.clock.at = fixture.clock.at.plusSeconds(31);
    tradingRecoveryJob.recover();
    assertReleased("expired");
  }

  /** 创建调用在途时取消仍保留占用；迟到原prepay被记录但不允许重新创建。 */
  @Test
  void inFlightCreateCancellationAndLateResultKeepOriginalSource() throws Exception {
    CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              PaymentGateway.Create create = invocation.getArgument(0);
              assertEquals(originalPayment.channelCombineOutTradeNo(), create.outTradeNo());
              assertEquals(originalPayment.expireAt(), create.expiry());
              entered.countDown();
              assertTrue(resume.await(10, TimeUnit.SECONDS));
              return "late-prepay";
            })
        .when(paymentGateway)
        .create(any());
    ExecutorService executorService = Executors.newSingleThreadExecutor();
    Future<?> future = executorService.submit(() -> paymentExecution.execute(batchId));
    try {
      assertTrue(entered.await(10, TimeUnit.SECONDS));
      paymentWorkTransaction.closeIntent(batchId, "user_cancel");
      absentChannel(PaymentCloseResult.not_found);
      fixture.clock.at = fixture.clock.at.plusSeconds(31);
      tradingRecoveryJob.recover();
      assertHeld();
      resume.countDown();
      future.get(10, TimeUnit.SECONDS);
      assertEquals(
          "late-prepay", paymentOrderMapper.selectPaymentByBatch(batchId, false).channelPrepayId());
      fixture.clock.at =
          originalPayment.expireAt().plusSeconds(31).toInstant(ZoneOffset.ofHours(8));
      tradingRecoveryJob.recover();
      assertHeld();
      doReturn(fact("CLOSED")).when(paymentGateway).query(any(PaymentGateway.Reference.class));
      fixture.clock.at = fixture.clock.at.plusSeconds(31);
      tradingRecoveryJob.recover();
      assertReleased("closed");
      verify(paymentGateway, times(1)).create(any());
    } finally {
      resume.countDown();
      executorService.shutdownNow();
    }
  }

  /** 查询后收到实收时，确认不存在事务必须重读事实并拒绝释放。 */
  @Test
  void cashArrivingBetweenQueriesCannotBeOverwrittenByAbsence() {
    paymentWorkTransaction.claim(batchId);
    absentChannel(PaymentCloseResult.not_found);
    fixture.clock.at = originalPayment.expireAt().plusSeconds(31).toInstant(ZoneOffset.ofHours(8));
    doAnswer(
            invocation -> {
              paymentWorkTransaction.observe(batchId, fact("SUCCESS"), false);
              return PaymentCloseResult.not_found;
            })
        .when(paymentGateway)
        .close(any());
    tradingRecoveryJob.recover();
    assertHeld();
    assertEquals(
        "success", paymentOrderMapper.selectPaymentByBatch(batchId, false).paymentStatus());
    assertEquals(
        "refund_batch",
        fixture.db.queryForObject(
            "SELECT recovery_mode FROM checkout_batch WHERE id=?", String.class, batchId));
  }

  /** 查单期间取消会使原执行上下文过期，返回不存在后仍不得再调用创建。 */
  @Test
  void cancellationDuringQueryFencesRetryCreation() {
    PaymentWorkTransaction.Ticket stale = paymentWorkTransaction.claim(batchId);
    fixture.clock.at = fixture.clock.at.plusSeconds(31);
    absentChannel(PaymentCloseResult.not_found);
    doAnswer(
            invocation -> {
              paymentWorkTransaction.closeIntent(batchId, "user_cancel");
              return new PaymentGateway.PaymentFact(
                  originalPayment.channelAppId(),
                  originalPayment.channelMchId(),
                  originalPayment.channelCombineOutTradeNo(),
                  List.of(),
                  true);
            })
        .when(paymentGateway)
        .query(any(PaymentGateway.Reference.class));
    tradingRecoveryJob.recover();
    assertHeld();
    assertFalse(paymentWorkTransaction.canDispatchCreate(stale));
    verify(paymentGateway, never()).create(any());
  }

  /** 不存在来源安全释放后的迟到实收只走旧来源退款，不抢新批次guard。 */
  @Test
  void absentClosedLateCashRefundsWithoutTakingNewGuard() throws Exception {
    paymentWorkTransaction.claim(batchId);
    absentChannel(PaymentCloseResult.not_found);
    fixture.clock.at = originalPayment.expireAt().plusSeconds(31).toInstant(ZoneOffset.ofHours(8));
    tradingRecoveryJob.recover();
    assertReleased("expired");
    quoteTest.input.put(
        "expectedVersion",
        fixture.db.queryForObject(
            "SELECT version FROM rental_checklist WHERE id=?", Integer.class, quoteTest.checklist));
    quoteTest.input.put("couponId", null);
    String newBatch = quoteTest.submitQuoted(quoteTest.buyer, quoteTest.input);
    PaymentOrderDO newPayment = paymentOrderMapper.selectPaymentByBatch(newBatch, false);
    assertTrue(paymentWorkTransaction.observe(batchId, fact("SUCCESS"), false).refundBatch());
    // 新批次留在原创建租约内，仅旧来源进入本轮恢复。
    assertNotNull(paymentWorkTransaction.claim(newBatch));
    tradingRecoveryJob.recover();
    String refundId =
        fixture.db.queryForObject(
            "SELECT id FROM refund_order WHERE biz_order_type='checkout_batch' AND biz_order_id=?",
            String.class,
            batchId);
    quoteTest.completeRefund(refundId);
    fixture.clock.at = fixture.clock.at.plusSeconds(31);
    doAnswer(
            invocation -> {
              PaymentGateway.Reference reference = invocation.getArgument(0);
              if (reference.outTradeNo().equals(newPayment.channelCombineOutTradeNo()))
                throw unavailable("PAYMENT_CHANNEL_UNAVAILABLE");
              return fact("SUCCESS");
            })
        .when(paymentGateway)
        .query(any(PaymentGateway.Reference.class));
    tradingRecoveryJob.recover();
    assertEquals(
        "refunded_closed",
        fixture.db.queryForObject(
            "SELECT batch_status FROM checkout_batch WHERE id=?", String.class, batchId));
    assertEquals(
        newBatch,
        fixture.db.queryForObject(
            "SELECT current_checkout_batch_id FROM rental_checklist WHERE id=?",
            String.class,
            quoteTest.checklist));
    assertEquals(
        "held",
        fixture.db.queryForObject(
            "SELECT reservation_status FROM inventory_reservation WHERE checkout_batch_id=?",
            String.class,
            newBatch));
    assertEquals(
        "available",
        fixture.db.queryForObject(
            "SELECT coupon_status FROM user_coupon WHERE id=?", String.class, quoteTest.coupon));
    assertEquals(
        1,
        fixture.db.queryForObject(
            "SELECT COUNT(*) FROM refund_order WHERE biz_order_type='checkout_batch' AND"
                + " biz_order_id=?",
            Integer.class,
            batchId));
    assertEquals(
        0,
        fixture.db.queryForObject(
            "SELECT COUNT(*) FROM rental_order WHERE checkout_batch_id=?", Integer.class, batchId));
    verify(paymentGateway, never()).create(any());
  }

  /** 在释放事务最后一步注入异常，验证之前的库存/券释放与batch收尾均回滚。 */
  void failReleaseThenRecover(String expectedStatus) throws Exception {
    UnformedBatchRefunds target =
        AopTestUtils.getUltimateTargetObject(fixture.app.getBean(UnformedBatchRefunds.class));
    CheckoutBatchMapper originalMapper = fixture.app.getBean(CheckoutBatchMapper.class);
    CheckoutBatchMapper failingMapper =
        mock(CheckoutBatchMapper.class, org.mockito.AdditionalAnswers.delegatesTo(originalMapper));
    doThrow(new IllegalStateException("isolated release rollback"))
        .when(failingMapper)
        .restoreChecklist(eq(quoteTest.checklist), eq(batchId), any());
    ReflectionTestUtils.setField(target, "checkoutBatchMapper", failingMapper);
    try {
      assertThrows(IllegalStateException.class, () -> paymentExecution.execute(batchId));
      assertEquals(
          "closed", paymentOrderMapper.selectPaymentByBatch(batchId, false).paymentStatus());
      assertHeld();
      assertEquals(
          "pending_payment",
          fixture.db.queryForObject(
              "SELECT batch_status FROM checkout_batch WHERE id=?", String.class, batchId));
    } finally {
      ReflectionTestUtils.setField(target, "checkoutBatchMapper", originalMapper);
    }
    fixture.clock.at = fixture.clock.at.plusSeconds(31);
    assertTrue(paymentOrderMapper.selectDuePaymentBatches(now(fixture.clock)).contains(batchId));
    tradingRecoveryJob.recover();
    assertReleased(expectedStatus);
  }

  /** 明确替身仅报告原号不存在，关单结果不自动改本地状态。 */
  void absentChannel(PaymentCloseResult paymentCloseResult) {
    doReturn(
            new PaymentGateway.PaymentFact(
                originalPayment.channelAppId(),
                originalPayment.channelMchId(),
                originalPayment.channelCombineOutTradeNo(),
                List.of(),
                true))
        .when(paymentGateway)
        .query(any(PaymentGateway.Reference.class));
    doReturn(paymentCloseResult).when(paymentGateway).close(any());
  }

  /** 构造本隔离批次的完整原商户/原金额子单事实。 */
  PaymentGateway.PaymentFact fact(String state) {
    List<ChannelSuborderDO> suborders =
        paymentOrderMapper.selectSubordersByPayment(originalPayment.id(), false);
    return new PaymentGateway.PaymentFact(
        originalPayment.channelAppId(),
        originalPayment.channelMchId(),
        originalPayment.channelCombineOutTradeNo(),
        List.of(quoteTest.cash(suborders.get(0), state)),
        false);
  }

  /** 未核实终态时库存、券和清单守卫必须一起保留。 */
  void assertHeld() {
    assertEquals(
        "held",
        fixture.db.queryForObject(
            "SELECT reservation_status FROM inventory_reservation WHERE checkout_batch_id=?",
            String.class,
            batchId));
    assertEquals(
        "locked",
        fixture.db.queryForObject(
            "SELECT coupon_status FROM user_coupon WHERE id=?", String.class, quoteTest.coupon));
    assertEquals(
        batchId,
        fixture.db.queryForObject(
            "SELECT current_checkout_batch_id FROM rental_checklist WHERE id=?",
            String.class,
            quoteTest.checklist));
    assertNull(
        fixture.db.queryForObject(
            "SELECT guard_released_at FROM checkout_batch WHERE id=?",
            java.sql.Timestamp.class,
            batchId));
  }

  /** 安全收尾只执行一次，原号/原截止不变且终态离开正式调度扫描。 */
  void assertReleased(String expectedStatus) {
    assertEquals(
        expectedStatus,
        fixture.db.queryForObject(
            "SELECT batch_status FROM checkout_batch WHERE id=?", String.class, batchId));
    assertEquals(
        "expired".equals(expectedStatus) ? "expired" : "released",
        fixture.db.queryForObject(
            "SELECT reservation_status FROM inventory_reservation WHERE checkout_batch_id=?",
            String.class,
            batchId));
    assertEquals(
        "available",
        fixture.db.queryForObject(
            "SELECT coupon_status FROM user_coupon WHERE id=?", String.class, quoteTest.coupon));
    assertEquals(
        "selecting",
        fixture.db.queryForObject(
            "SELECT checklist_status FROM rental_checklist WHERE id=?",
            String.class,
            quoteTest.checklist));
    assertNull(
        fixture.db.queryForObject(
            "SELECT current_checkout_batch_id FROM rental_checklist WHERE id=?",
            String.class,
            quoteTest.checklist));
    assertNotNull(
        fixture.db.queryForObject(
            "SELECT guard_released_at FROM checkout_batch WHERE id=?",
            java.sql.Timestamp.class,
            batchId));
    PaymentOrderDO paymentOrderDO = paymentOrderMapper.selectPaymentByBatch(batchId, false);
    assertEquals(originalPayment.expireAt(), paymentOrderDO.expireAt());
    assertEquals(
        originalPayment.channelCombineOutTradeNo(), paymentOrderDO.channelCombineOutTradeNo());
    Integer version =
        fixture.db.queryForObject(
            "SELECT version FROM checkout_batch WHERE id=?", Integer.class, batchId);
    fixture.clock.at = fixture.clock.at.plusSeconds(301);
    assertFalse(paymentOrderMapper.selectDuePaymentBatches(now(fixture.clock)).contains(batchId));
    tradingRecoveryJob.recover();
    assertEquals(
        version,
        fixture.db.queryForObject(
            "SELECT version FROM checkout_batch WHERE id=?", Integer.class, batchId));
    assertEquals(
        paymentOrderDO.attemptCount(),
        paymentOrderMapper.selectPaymentByBatch(batchId, false).attemptCount());
    assertEquals(
        0,
        fixture.db.queryForObject(
            "SELECT COUNT(*) FROM rental_order WHERE checkout_batch_id=?", Integer.class, batchId));
  }
}
