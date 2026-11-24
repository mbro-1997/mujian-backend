package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.PlanningOperation;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import com.mujian.backend.order.domain.CheckoutQuoteData;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.*;
import com.mujian.backend.payment.dto.PaymentAccount;
import com.mujian.backend.payment.service.PaymentExecution;
import com.mujian.backend.planning.dto.PlanningCommand;
import com.mujian.backend.planning.dto.PlanningDtos.ExpectedVersion;
import com.mujian.backend.planning.service.ChecklistService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/** 只复测删除及支付根互斥，复用既有隔离夹具，不调用真实支付渠道。 */
class D3ChecklistDeleteTest {
  final D3BQuoteTest tradingFixture = new D3BQuoteTest();
  final D2ApplicationTest fixture = tradingFixture.f;

  /** 复用现有库、受控时钟及外部渠道替身，关闭隔离后台任务。 */
  @BeforeEach
  void start() throws Exception {
    tradingFixture.extraArguments =
        new String[] {"--mj.fulfillment.enabled=false", "--mj.reward.recovery-enabled=false"};
    tradingFixture.start();
  }

  /** 仅回收本用例创建的账号、业务对象及会话。 */
  @AfterEach
  void stop() {
    tradingFixture.stop();
  }

  /** 生成独立业务意图的幂等键。 */
  String key() {
    return UUID.randomUUID().toString();
  }

  /** 经真实HTTP和共同响应契约执行。 */
  JsonNode call(String method, String path, Object body, User user, String key, int status)
      throws Exception {
    return tradingFixture.call(method, path, body, user, key, status);
  }

  /** 创建空清单，用于验证释放名额后的原入口。 */
  String create(User user) throws Exception {
    String date = now(fixture.clock).toLocalDate().plusDays(5).toString();
    return call(
            "POST",
            "/api/me/checklists",
            view("sourceType", "manual", "startDate", date, "endDate", date),
            user,
            key(),
            200)
        .path("resourceId")
        .asString();
  }

  /** 查询实际根版本，不修改被测版本。 */
  int version(String id) {
    return fixture.db.queryForObject(
        "SELECT version FROM rental_checklist WHERE id=?", Integer.class, id);
  }

  /** 删除请求只带原版本，明确复用同键。 */
  JsonNode delete(User user, String id, int version, String requestId, int status)
      throws Exception {
    return call(
        "POST",
        "/api/me/checklists/" + id + "/delete",
        view("expectedVersion", version),
        user,
        requestId,
        status);
  }

  /** 当前及挂起空清单可直接删；不切换其他根，同键不重复释放，列表总数与详情同步排除。 */
  @Test
  void slotsListsReplayAndMembersRemainCorrect() throws Exception {
    User user = fixture.user();
    String first = create(user), second = create(user), current = create(user);
    String others =
        fixture
            .db
            .queryForList(
                "SELECT id,checklist_status,version FROM rental_checklist WHERE id IN (?,?) ORDER"
                    + " BY id",
                second,
                current)
            .toString();
    delete(tradingFixture.buyer, first, version(first), key(), 404);
    String requestId = key();
    int before = version(first);
    JsonNode receipt = delete(user, first, before, requestId, 200);
    assertEquals("deleteChecklist", receipt.path("operation").asString());
    assertEquals(before + 1, receipt.path("resourceVersion").asInt());
    assertEquals(
        others,
        fixture
            .db
            .queryForList(
                "SELECT id,checklist_status,version FROM rental_checklist WHERE id IN (?,?) ORDER"
                    + " BY id",
                second,
                current)
            .toString());
    assertEquals(
        "replayed", delete(user, first, before, requestId, 200).path("outcome").asString());
    assertEquals(before + 1, version(first));
    delete(user, first, before, key(), 404);
    delete(user, first, before + 1, requestId, 409);
    assertEquals("IDEMPOTENCY_KEY_REUSED", fixture.lastReason);
    for (String scope : List.of("active", "all", "history")) {
      JsonNode page = call("GET", "/api/me/checklists?scope=" + scope, null, user, null, 200);
      assertEquals(scope.equals("history") ? "0" : "2", page.path("total").asString());
      for (JsonNode item : page.path("items")) assertNotEquals(first, item.path("id").asString());
    }
    call("GET", "/api/me/checklists/" + first, null, user, null, 404);
    String fourth = create(user);
    String suspended =
        fixture
            .db
            .queryForList(
                "SELECT id,checklist_status,version FROM rental_checklist WHERE id IN (?,?) ORDER"
                    + " BY id",
                second,
                current)
            .toString();
    delete(user, fourth, version(fourth), key(), 200);
    assertEquals(
        suspended,
        fixture
            .db
            .queryForList(
                "SELECT id,checklist_status,version FROM rental_checklist WHERE id IN (?,?) ORDER"
                    + " BY id",
                second,
                current)
            .toString());
    assertEquals(
        0,
        fixture.db.queryForObject(
            "SELECT COUNT(*) FROM rental_checklist WHERE owner_user_id=? AND current_owner_guard IS"
                + " NOT NULL",
            Integer.class,
            user.id()));
    assertNotNull(create(user));
    // 保留实际明细，同时日期已过也允许删除。
    String itemRows =
        fixture
            .db
            .queryForList(
                "SELECT * FROM rental_checklist_item WHERE checklist_id=?",
                tradingFixture.checklist)
            .toString();
    fixture.clock.at = fixture.clock.at.plusSeconds(10 * 86400);
    delete(
        tradingFixture.buyer,
        tradingFixture.checklist,
        version(tradingFixture.checklist),
        key(),
        200);
    assertEquals(
        itemRows,
        fixture
            .db
            .queryForList(
                "SELECT * FROM rental_checklist_item WHERE checklist_id=?",
                tradingFixture.checklist)
            .toString());
    assertEquals(
        0,
        fixture.db.queryForObject(
            "SELECT COUNT(*) FROM rental_order WHERE buyer_user_id=?",
            Integer.class,
            tradingFixture.buyer.id()));
  }

  /** 过期版本与已转单必须拒绝；原历史保持只读。 */
  @Test
  void staleVersionAndConvertedAreRejected() throws Exception {
    String id = tradingFixture.checklist;
    delete(tradingFixture.buyer, id, version(id) - 1, key(), 409);
    assertEquals("VERSION_CONFLICT", fixture.lastReason);
    fixture.db.update(
        "UPDATE rental_checklist SET"
            + " checklist_status='converted_to_order',active_slot_no=NULL,converted_order_at=?"
            + " WHERE id=?",
        now(fixture.clock),
        id);
    delete(tradingFixture.buyer, id, version(id), key(), 409);
    assertEquals("CHECKLIST_READ_ONLY", fixture.lastReason);
    assertEquals(
        "converted_to_order",
        fixture.db.queryForObject(
            "SELECT checklist_status FROM rental_checklist WHERE id=?", String.class, id));
  }

  /** 读取真实隔离报价快照，后续提交走原交易事务。 */
  CheckoutQuoteData quote() throws Exception {
    String token =
        call(
                "POST",
                "/api/me/checkout-quotes",
                tradingFixture.input,
                tradingFixture.buyer,
                null,
                200)
            .path("quoteToken")
            .asString();
    return fixture.app.getBean(TradingTokens.class).readQuote(tradingFixture.buyer.id(), token);
  }

  /** 预留支付批次，测试账户不调用外部创建支付。 */
  String submit(CheckoutQuoteData checkoutQuoteData) {
    return fixture
        .app
        .getBean(CheckoutSubmissionTransaction.class)
        .submit(
            new TradingCommand(
                tradingFixture.buyer.id(), key(), TradingOperation.submitCheckout, "d".repeat(64)),
            checkoutQuoteData,
            new PaymentAccount(
                "test-app",
                "test-platform",
                "https://example.invalid/api/wechat-pay/notifications/payment"))
        .resourceId();
  }

  /** 主动取消及自动到期都要等原支付流程确认未付、解除在途状态后才允许删。 */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void closeMustFinishBeforeDelete(boolean timeout) throws Exception {
    String batch = submit(quote());
    String id = tradingFixture.checklist;
    delete(tradingFixture.buyer, id, version(id), key(), 409);
    assertEquals("CHECKLIST_PAYMENT_IN_PROGRESS", fixture.lastReason);
    if (timeout) fixture.clock.at = fixture.clock.at.plusSeconds(601);
    else
      fixture
          .app
          .getBean(CheckoutPaymentCommands.class)
          .apply(
              new TradingCommand(
                  tradingFixture.buyer.id(),
                  key(),
                  TradingOperation.cancelCheckoutPayment,
                  "c".repeat(64)),
              batch,
              0);
    delete(tradingFixture.buyer, id, version(id), key(), 409);
    assertEquals("CHECKLIST_PAYMENT_IN_PROGRESS", fixture.lastReason);
    fixture.app.getBean(PaymentExecution.class).execute(batch);
    assertEquals(
        "selecting",
        fixture.db.queryForObject(
            "SELECT checklist_status FROM rental_checklist WHERE id=?", String.class, id));
    delete(tradingFixture.buyer, id, version(id), key(), 200);
    // 删除后原终态批次仍可定位，重复恢复不能复活清单。
    fixture.app.getBean(PaymentExecution.class).execute(batch);
    assertEquals(
        "deleted",
        fixture.db.queryForObject(
            "SELECT checklist_status FROM rental_checklist WHERE id=?", String.class, id));
    assertEquals(
        0,
        fixture.db.queryForObject(
            "SELECT COUNT(*) FROM rental_order WHERE buyer_user_id=?",
            Integer.class,
            tradingFixture.buyer.id()));
  }

  /** 两种抢锁次序分别验证：删除赢则不能提交，提交赢则删除失败，不会同时删除和成单。 */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void deleteAndSubmissionSerialize(boolean deleteFirst) throws Exception {
    CheckoutQuoteData checkoutQuoteData = quote();
    int expectedVersion = version(tradingFixture.checklist);
    ChecklistService checklistService = fixture.app.getBean(ChecklistService.class);
    PlanningCommand planningCommand =
        new PlanningCommand(
            tradingFixture.buyer.id(), key(), PlanningOperation.deleteChecklist, "e".repeat(64));
    Callable<String> deletion =
        () -> {
          try {
            checklistService.delete(
                planningCommand, tradingFixture.checklist, new ExpectedVersion(expectedVersion));
            return "deleted";
          } catch (ApiFailure failure) {
            return failure.reason;
          }
        };
    Callable<String> submission =
        () -> {
          try {
            submit(checkoutQuoteData);
            return "submitted";
          } catch (ApiFailure failure) {
            return failure.reason;
          }
        };
    CountDownLatch changed = new CountDownLatch(1),
        release = new CountDownLatch(1),
        competing = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    TransactionTemplate transactionTemplate =
        new TransactionTemplate(fixture.app.getBean(PlatformTransactionManager.class));
    try {
      Future<String> first =
          executor.submit(
              () ->
                  transactionTemplate.execute(
                      status -> {
                        try {
                          String result = (deleteFirst ? deletion : submission).call();
                          changed.countDown();
                          if (!release.await(10, TimeUnit.SECONDS))
                            throw new IllegalStateException("barrier timeout");
                          return result;
                        } catch (Exception exception) {
                          throw new IllegalStateException(exception);
                        }
                      }));
      assertTrue(changed.await(10, TimeUnit.SECONDS));
      Future<String> second =
          executor.submit(
              () -> {
                competing.countDown();
                return (deleteFirst ? submission : deletion).call();
              });
      assertTrue(competing.await(10, TimeUnit.SECONDS));
      release.countDown();
      assertEquals(deleteFirst ? "deleted" : "submitted", first.get(10, TimeUnit.SECONDS));
      String rejected = second.get(10, TimeUnit.SECONDS);
      assertTrue(
          deleteFirst
              ? rejected.equals("RESOURCE_NOT_FOUND")
              : Set.of("VERSION_CONFLICT", "CHECKLIST_PAYMENT_IN_PROGRESS").contains(rejected),
          rejected);
      assertEquals(
          deleteFirst ? "deleted" : "checkout_processing",
          fixture.db.queryForObject(
              "SELECT checklist_status FROM rental_checklist WHERE id=?",
              String.class,
              tradingFixture.checklist));
      assertEquals(
          deleteFirst ? 0 : 1,
          fixture.db.queryForObject(
              "SELECT COUNT(*) FROM checkout_batch WHERE source_checklist_id=?",
              Integer.class,
              tradingFixture.checklist));
      assertEquals(
          deleteFirst ? 0 : 1,
          fixture.db.queryForObject(
              "SELECT COUNT(*) FROM payment_order WHERE buyer_user_id=?",
              Integer.class,
              tradingFixture.buyer.id()));
      assertEquals(
          0,
          fixture.db.queryForObject(
              "SELECT COUNT(*) FROM rental_order WHERE source_checklist_id=?",
              Integer.class,
              tradingFixture.checklist));
    } finally {
      release.countDown();
      executor.shutdownNow();
    }
  }
}
