package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.service.TradingRoots;
import com.mujian.backend.payment.domain.ChannelSuborderDO;
import com.mujian.backend.payment.dto.CashComponents;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import com.mujian.backend.payment.mapper.RefundMapper;
import com.mujian.backend.payment.service.RefundObligations;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 真实原支付与隔离数据库验证新退款义务，渠道未调用。 */
class D4RefundObligationTest {
  /** 关闭事实和额度保留原子回滚；原义务重放不受后来关闭门禁影响。 */
  @Test
  void sourceGateRollbackReplayAndSharingGuardProtectOriginalCash() throws Exception {
    D3BQuoteTest fixture = new D3BQuoteTest();
    fixture.extraSources = new Class<?>[] {D4OutboundHttpTest.Ready.class};
    fixture.extraArguments = new String[] {"--mj.fulfillment.enabled=true"};
    try {
      fixture.start();
      fixture.f.db.update("UPDATE prop_sku SET deposit_amount_fen=500 WHERE id=?", fixture.sku);
      String orderId = fixture.paidOrder();
      RentalOrderDO order = fixture.f.app.getBean(RentalOrderMapper.class).selectOrderById(orderId);
      PaymentOrderMapper payments = fixture.f.app.getBean(PaymentOrderMapper.class);
      RefundMapper refunds = fixture.f.app.getBean(RefundMapper.class);
      RefundObligations obligations = fixture.f.app.getBean(RefundObligations.class);
      TransactionTemplate tx =
          new TransactionTemplate(fixture.f.app.getBean(PlatformTransactionManager.class));
      ChannelSuborderDO channelSuborderDO =
          payments.selectSubordersByPayment(order.paymentOrderId(), false).get(0);
      CashComponents original =
          new CashComponents(
              order.rentPaidAmountFen(),
              order.depositPaidAmountFen(),
              order.freightPrepaidPaidAmountFen());
      CashComponents deposit = new CashComponents(0, original.depositFen(), 0);
      assertTrue(deposit.depositFen() > 0);
      String check = Ids.next();
      fixture.f.app.getBean(D4OutboundHttpTest.KnownFacts.class).ready = false;
      assertThrows(
          ApiFailure.class,
          () ->
              tx.execute(
                  status -> {
                    fixture.f.app.getBean(TradingRoots.class).lock(order.checkoutBatchId());
                    closedCheck(fixture, check, orderId);
                    return obligations.reserve(
                        fixture.supplier.id(),
                        "rental_order",
                        orderId,
                        "return_check",
                        check,
                        "deposit_release",
                        "正常退押",
                        channelSuborderDO,
                        original,
                        deposit);
                  }));
      assertEquals(
          0,
          fixture.f.db.queryForObject(
              "SELECT COUNT(*) FROM order_return_check WHERE id=?", Integer.class, check));
      assertTrue(refunds.selectRefundIdsByOrder(orderId).isEmpty());
      fixture.f.app.getBean(D4OutboundHttpTest.KnownFacts.class).ready = true;
      closedCheck(fixture, check, orderId);
      assertThrows(
          IllegalStateException.class,
          () ->
              tx.execute(
                  status ->
                      obligations.reserve(
                          fixture.supplier.id(),
                          "rental_order",
                          orderId,
                          "return_check",
                          Ids.next(),
                          "deposit_release",
                          "来源不符",
                          channelSuborderDO,
                          original,
                          deposit)));
      assertThrows(
          IllegalStateException.class,
          () ->
              tx.execute(
                  status ->
                      obligations.reserve(
                          fixture.supplier.id(),
                          "rental_order",
                          orderId,
                          "return_check",
                          check,
                          "deposit_release",
                          "越科目",
                          channelSuborderDO,
                          original,
                          new CashComponents(1, 1, 0))));
      fixture.f.db.update(
          "UPDATE payment_channel_suborder SET channel_suborder_status='sharing' WHERE id=?",
          channelSuborderDO.id());
      ChannelSuborderDO sharing =
          payments.selectSubordersByPayment(order.paymentOrderId(), false).get(0);
      assertThrows(
          IllegalStateException.class,
          () ->
              tx.execute(
                  status ->
                      obligations.reserve(
                          fixture.supplier.id(),
                          "rental_order",
                          orderId,
                          "return_check",
                          check,
                          "deposit_release",
                          "来源分账中",
                          sharing,
                          original,
                          deposit)));
      fixture.f.db.update(
          "UPDATE payment_channel_suborder SET channel_suborder_status='paid_frozen' WHERE id=?",
          channelSuborderDO.id());
      String refund =
          tx.execute(
              status -> {
                fixture.f.app.getBean(TradingRoots.class).lock(order.checkoutBatchId());
                return obligations.reserve(
                    fixture.supplier.id(),
                    "rental_order",
                    orderId,
                    "return_check",
                    check,
                    "deposit_release",
                    "正常退押",
                    channelSuborderDO,
                    original,
                    deposit);
              });
      assertEquals(orderId, refunds.selectRefundById(refund, false).bizOrderId());
      fixture.f.app.getBean(D4OutboundHttpTest.KnownFacts.class).ready = false;
      assertEquals(
          refund,
          tx.execute(
              status ->
                  obligations.reserve(
                      fixture.supplier.id(),
                      "rental_order",
                      orderId,
                      "return_check",
                      check,
                      "deposit_release",
                      "正常退押",
                      channelSuborderDO,
                      original,
                      deposit)));
      assertEquals(1, refunds.selectRefundIdsByOrder(orderId).size());
      fixture.f.db.update("UPDATE refund_order SET refund_status='failed' WHERE id=?", refund);
      assertEquals(
          deposit.depositFen(), refunds.selectRefundExposureBySuborder(channelSuborderDO.id()).depositFen());
      refunds.refreshOrderRefunds(orderId, now(fixture.f.clock));
      assertEquals(
          "refunding",
          fixture.f.db.queryForObject(
              "SELECT deposit_status FROM rental_order WHERE id=?", String.class, orderId));
    } finally {
      if (fixture.f.db != null && fixture.factoryId != null)
        fixture.f.db.update("DELETE FROM order_return_check WHERE factory_id=?", fixture.factoryId);
      fixture.stop();
    }
  }

  /** 测试构造明确已关闭且无争议的本单核验，未冒充真实履约验收。 */
  private void closedCheck(D3BQuoteTest fixture, String id, String orderId) {
    fixture.f.db.update(
        "INSERT INTO"
            + " order_return_check(id,rental_order_id,factory_id,check_status,check_start_source,check_start_at,closed_by_user_id,closed_at,created_at,updated_at)"
            + " VALUES(?,?,?,'no_issue','factory_scan',?,?,?,?,?)",
        id,
        orderId,
        fixture.factoryId,
        now(fixture.f.clock),
        fixture.supplier.id(),
        now(fixture.f.clock),
        now(fixture.f.clock),
        now(fixture.f.clock));
  }
}
