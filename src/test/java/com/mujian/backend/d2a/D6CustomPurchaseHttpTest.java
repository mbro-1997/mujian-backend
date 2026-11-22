package com.mujian.backend.d2a;

import com.mujian.backend.common.security.D2Contract;
import com.mujian.backend.payment.domain.ChannelSuborderDO;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.custom.mapper.*;
import com.mujian.backend.custom.service.*;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.payment.dto.PaymentAccount;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import com.mujian.backend.payment.service.*;
import com.mujian.backend.payment.service.PaymentWorkTransaction;
import com.mujian.backend.settlement.service.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

/** 隔离库、真实 HTTP 及数据库，渠道边界由可控替身提供事实。 */
class D6CustomPurchaseHttpTest {
  D3BQuoteTest f = new D4RecoveryHttpTest().fixture();
  String buyer = "/api/custom-requirements", factory = "/api/me/factory/custom-requirements";

  /** 初始化本组隔离依赖。 */
  @BeforeEach
  void start() throws Exception {
    f.start();
    f.f.clock.at = f.f.clock.at.truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
    PaymentGateway gateway = f.f.app.getBean(PaymentGateway.class);
    doReturn(
            new PaymentAccount(
                "test-app",
                "test-platform",
                "https://example.invalid/api/wechat-pay/notifications/payment"))
        .when(gateway)
        .requireReady();
    doReturn("test-prepay").when(gateway).create(any());
    doAnswer(
            call -> {
              PaymentGateway.Reference reference = call.getArgument(0);
              return new PaymentGateway.PaymentFact(
                  reference.account().appId(), reference.account().mchId(), reference.outTradeNo(), List.of(), true);
            })
        .when(gateway)
        .query(any());
    doReturn(com.mujian.backend.common.enums.PaymentCloseResult.accepted)
        .when(gateway)
        .close(any());
    doReturn(
            new PaymentGateway.Parameters(
                "test-app", "1234567890", "nonce", "prepay_id=test", "RSA", "test-signature"))
        .when(gateway)
        .parameters(anyString(), anyString());
  }

  /** 释放本组创建的资源。 */
  @AfterEach
  void stop() {
    if (f.f.app != null) f.stop();
  }

  /** 以测试身份提交请求。 */
  JsonNode post(String path, Object body, boolean supplier, int status) throws Exception {
    return f.call("POST", path, body, supplier ? f.supplier : f.buyer, f.key(), status);
  }

  /** 读取指定测试资源。 */
  JsonNode get(String path, boolean supplier) throws Exception {
    return f.call("GET", path, null, supplier ? f.supplier : f.buyer, null, 200);
  }

  /** 构造可继续提交的草稿。 */
  Map<String, Object> draft(String mode) {
    ArrayList<Object> items = new ArrayList<>();
    for (int quantity : List.of(2, 1))
      items.add(
          view(
              "sourceSkuId",
              f.sku,
              "title",
              "组合桌",
              "quantity",
              quantity,
              "dimension",
              "120厘米",
              "material",
              "实木",
              "style",
              "原木",
              "acceptanceNotes",
              "逐件核对"));
    return view(
        "factoryId",
        f.factoryId,
        "title",
        "巡演定制",
        "usageScene",
        "剧场",
        "deliveryCity",
        "上海",
        "deliveryMode",
        mode,
        "expectedCompletionDate",
        now(f.f.clock).toLocalDate().plusDays(10).toString(),
        "remark",
        "侧台使用",
        "items",
        items);
  }

  /** 创建已报价的测试需求。 */
  String quoted(String mode) throws Exception {
    return quoted(mode, 320000, mode.equals("buyer_pickup") ? 0 : 30000);
  }

  /** 创建已报价的测试需求。 */
  String quoted(String mode, int unitPrice, int freight) throws Exception {
    String id = post(buyer, draft(mode), false, 200).get("resourceId").asString();
    String b = buyer + "/" + id;
    String s = factory + "/" + id;
    post(b + "/submit", view("expectedVersion", 0), false, 200);
    post(s + "/start-review", view("expectedVersion", 1), true, 200);
    post(b + "/withdraw", view("expectedVersion", 1), false, 409);
    ArrayList<Object> items = new ArrayList<>();
    for (JsonNode i : get(s, true).path("form").path("items"))
      items.add(
          view(
              "itemId",
              i.get("id").asString(),
              "title",
              "组合桌",
              "quantity",
              i.get("quantity").asInt(),
              "unitPriceFen",
              unitPrice,
              "dimension",
              "120厘米",
              "material",
              "实木",
              "style",
              "原木",
              "acceptanceStandard",
              "逐件核对"));
    Map<String,Object> terms =
        view(
            "items",
            items,
            "deliveryMode",
            mode,
            "freightFen",
            freight,
            "productionDays",
            5,
            "promisedDate",
            now(f.f.clock).toLocalDate().plusDays(8).toString(),
            "revisionTerms",
            "一次",
            "includedAndExcluded",
            "含制作",
            "afterSaleTerms",
            "可返工",
            "remark",
            null);
    post(s + "/quote-draft", view("expectedVersion", 2, "terms", terms), true, 200);
    assertEquals(0, get(b, false).get("quotes").size());
    String q = get(s, true).get("quotes").get(0).get("id").asString();
    post(s + "/publish-quote", view("expectedVersion", 3, "quoteId", q), true, 200);
    return id;
  }

  /** 确认当前场景的业务申请。 */
  JsonNode accept(String id, String mode) throws Exception {
    JsonNode q = get(buyer + "/" + id, false).get("quotes").get(0);
    return post(
        buyer + "/" + id + "/accept-quote",
        view(
            "expectedVersion",
            4,
            "quoteId",
            q.get("id").asString(),
            "addressId",
            mode.equals("buyer_pickup") ? null : f.address),
        false,
        200);
  }

  /** 将测试付款推进到成功。 */
  void paid(String payment, Instant at) {
    ChannelSuborderDO channelSuborderDO =
        f.f.app.getBean(PaymentOrderMapper.class).selectSubordersByPayment(payment, false).get(0);
    int total = channelSuborderDO.channelSuborderAmountFen();
    PaymentGateway.PaymentFact fact =
        new PaymentGateway.PaymentFact(
            "test-app",
            "test-platform",
            payment,
            List.of(
                new PaymentGateway.SuborderFact(
                    "test-platform",
                    channelSuborderDO.subMchidSnapshot(),
                    channelSuborderDO.id(),
                    "D6-" + Ids.next(),
                    "SUCCESS",
                    total,
                    "CNY",
                    total,
                    "CNY",
                    at)),
            false);
    PaymentWorkTransaction work = f.f.app.getBean(PaymentWorkTransaction.class);
    work.observe(payment, fact, true);
    work.observe(payment, fact, true);
  }

  /** 验证预算约束及全额付款。 */
  @Test
  void budgetAndFullPayment() throws Exception {
    for (List<Integer> range :
        List.of(List.of(10, 20), Arrays.asList(0, null), Arrays.asList(null, 20), List.of(0, 0))) {
      Map<String,Object> input = draft("buyer_pickup");
      input.put("budgetRangeFen", range);
      String id = post(buyer, input, false, 200).path("resourceId").asString();
      assertEquals(
          f.f.json.valueToTree(range),
          get(buyer + "/" + id, false).path("form").get("budgetRangeFen"));
    }
    post(buyer, view("factoryId", f.factoryId, "budgetRangeFen", List.of(2, 1)), false, 400);
    post(
        buyer,
        view("factoryId", f.factoryId, "budgetRangeFen", Arrays.asList(null, null)),
        false,
        400);
    JsonNode result = accept(quoted("factory_delivery"), "factory_delivery");
    String id = result.get("orderId").asString(), p = result.get("payment").get("id").asString();
    paid(p, f.f.clock.instant());
    post("/api/custom-orders/" + id + "/cancel", view("expectedVersion", 2), false, 200);
    Map<String,Object> row =
        f.f.db.queryForMap(
            "SELECT custom_goods_refund_amount_fen goods,freight_refund_amount_fen freight FROM"
                + " refund_order WHERE biz_order_id=?",
            id);
    assertEquals(960000, row.get("goods"));
    assertEquals(30000, row.get("freight"));
  }

  /** 固定用例时间点。 */
  void at(Instant at) throws Exception {
    f.f.clock.at = at;
    renew();
  }

  /** 读取当前业务状态供断言。 */
  int status(String path, Object body, D2ApplicationTest.User actor) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(f.f.base + path))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + actor.token())
            .header("Idempotency-Key", f.key())
            .POST(HttpRequest.BodyPublishers.ofString(f.f.json.writeValueAsString(body)))
            .build();
    return f.f.http.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
  }

  /** 并发执行竞争操作并收集结果。 */
  void race(
      String left,
      D2ApplicationTest.User first,
      String right,
      D2ApplicationTest.User second,
      int version)
      throws Exception {
    CyclicBarrier ready = new java.util.concurrent.CyclicBarrier(2);
    ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      Map<String,Object> body = view("expectedVersion", version);
      Future<Integer> a =
          pool.submit(
              () -> {
                ready.await();
                return status(left, body, first);
              });
      Future<Integer> b =
          pool.submit(
              () -> {
                ready.await();
                return status(right, body, second);
              });
      assertEquals(
          Set.of(200, 409),
          Set.of(
              a.get(20, java.util.concurrent.TimeUnit.SECONDS),
              b.get(20, java.util.concurrent.TimeUnit.SECONDS)));
    } finally {
      pool.shutdownNow();
    }
  }

  /** 验证归属校验及写入竞争。 */
  @Test
  void ownershipAndRacingWrites() throws Exception {
    D2ApplicationTest.User other = f.fixture.supplier();
    String id = post(buyer, draft("buyer_pickup"), false, 200).get("resourceId").asString();
    post(buyer + "/" + id + "/submit", view("expectedVersion", 0), false, 200);
    f.call("GET", factory + "/" + id, null, other, null, 404);
    f.call(
        "POST",
        factory + "/" + id + "/start-review",
        view("expectedVersion", 1),
        other,
        f.key(),
        404);
    race(
        buyer + "/" + id + "/withdraw",
        f.buyer,
        factory + "/" + id + "/start-review",
        f.supplier,
        1);
    JsonNode launch = accept(quoted("buyer_pickup"), "buyer_pickup");
    String order = launch.get("orderId").asString();
    paid(launch.get("payment").get("id").asString(), f.f.clock.at);
    race(
        "/api/custom-orders/" + order + "/cancel",
        f.buyer,
        "/api/me/factory/custom-orders/" + order + "/start-production",
        f.supplier,
        2);
    assertTrue(
        Set.of("producing", "user_cancelled")
            .contains(get("/api/custom-orders/" + order, false).get("state").asString()));
  }

  /** 检查报价与原付款记录边界。 */
  @Test
  void quoteAndOriginalPaymentBoundaries() throws Exception {
    Instant sharedQ = f.f.clock.at.plus(Duration.ofHours(72));
    ArrayList<String> ids = new ArrayList<String>();
    for (int i = 0; i < 4; i++) ids.add(quoted("buyer_pickup"));
    at(sharedQ.minusMillis(1));
    JsonNode original = accept(ids.get(0), "buyer_pickup");
    String order = original.get("orderId").asString(),
        payment = original.get("payment").get("id").asString();
    Instant expiry = f.f.clock.at.plusSeconds(600);
    assertEquals(
        expiry,
        OffsetDateTime.parse(original.get("payment").get("expireAt").asString()).toInstant());
    JsonNode late = accept(ids.get(1), "buyer_pickup");
    for (int i = 2; i < 4; i++) {
      at(sharedQ.plusMillis(i - 2));
      String id = ids.get(i), path = buyer + "/" + id;
      JsonNode q = get(path, false).get("quotes").get(0);
      post(
          path + "/accept-quote",
          view("expectedVersion", 4, "quoteId", q.get("id").asString(), "addressId", null),
          false,
          409);
      post(path + "/request-requote", view("expectedVersion", 4), false, 200);
    }
    at(expiry.plusSeconds(60));
    String path = "/api/custom-orders/" + order;
    PaymentGateway.PaymentFact unknown =
        new PaymentGateway.PaymentFact("test-app", "test-platform", payment, List.of(), true);
    f.f.app.getBean(PaymentWorkTransaction.class).observe(payment, unknown, true);
    JsonNode current = get(path + "/payment", false);
    assertEquals(payment, current.path("payment").get("id").asString());
    assertTrue(current.get("parameters").isNull());
    JsonNode repeated =
        post(path + "/payments", view("expectedVersion", version(path, false)), false, 200);
    assertEquals(payment, repeated.get("payment").get("id").asString());
    paid(payment, expiry);
    assertEquals("paid_waiting_production", get(path, false).get("state").asString());
    paid(late.get("payment").get("id").asString(), expiry.plusMillis(1));
    String other = late.get("orderId").asString();
    assertEquals(
        "payment_closed", get("/api/custom-orders/" + other, false).get("state").asString());
    assertEquals(1, get("/api/custom-orders/" + other + "/refunds", false).size());
    at(expiry.plus(Duration.ofHours(2)));
    post(path + "/cancel", view("expectedVersion", version(path, false)), false, 409);
  }

  /** 验证改价与接受报价共用同一需求版本。 */
  @Test
  void repricingAndAcceptanceShareRequirementVersion() throws Exception {
    String id = quoted("buyer_pickup"), b = buyer + "/" + id, s = factory + "/" + id;
    JsonNode first = get(b, false).get("quotes").get(0);
    post(s + "/quote-draft", view("expectedVersion", 4, "terms", first.get("terms")), true, 200);
    JsonNode next = get(s, true).get("quotes").get(0);
    Map<String,Object> accept =
        view("expectedVersion", 5, "quoteId", first.get("id").asString(), "addressId", null);
    Map<String,Object> publish = view("expectedVersion", 5, "quoteId", next.get("id").asString());
    CyclicBarrier ready = new java.util.concurrent.CyclicBarrier(2);
    ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      Future<Integer> a =
          pool.submit(
              () -> {
                ready.await();
                return status(b + "/accept-quote", accept, f.buyer);
              });
      Future<Integer> q =
          pool.submit(
              () -> {
                ready.await();
                return status(s + "/publish-quote", publish, f.supplier);
              });
      assertEquals(
          Set.of(200, 409),
          Set.of(
              a.get(20, java.util.concurrent.TimeUnit.SECONDS),
              q.get(20, java.util.concurrent.TimeUnit.SECONDS)));
    } finally {
      pool.shutdownNow();
    }
    JsonNode detail = get(b, false);
    if (detail.get("orderId").isNull()) {
      assertEquals("superseded", detail.get("quotes").get(1).get("state").asString());
      post(
          b + "/accept-quote",
          view("expectedVersion", 6, "quoteId", next.get("id").asString(), "addressId", null),
          false,
          200);
    }
    assertEquals(
        0,
        f.f.db.queryForObject(
            "SELECT COUNT(*) FROM custom_quote WHERE requirement_id=? AND quote_status='draft'",
            Integer.class,
            id));
    assertEquals(
        1,
        f.f.db.queryForObject(
            "SELECT COUNT(*) FROM custom_order WHERE requirement_id=?", Integer.class, id));
  }

  /** 确认已关闭付款尝试仍有独立资金轨迹。 */
  @Test
  void priorClosedAttemptRetainsItsOwnMoneyTrail() throws Exception {
    JsonNode launch = accept(quoted("buyer_pickup"), "buyer_pickup");
    String order = launch.get("orderId").asString(),
        payment = launch.get("payment").get("id").asString();
    ChannelSuborderDO channelSuborderDO =
        f.f.app.getBean(PaymentOrderMapper.class).selectSubordersByPayment(payment, false).get(0);
    PaymentGateway.PaymentFact closed =
        new PaymentGateway.PaymentFact(
            "test-app",
            "test-platform",
            payment,
            List.of(
                new PaymentGateway.SuborderFact(
                    "test-platform",
                    channelSuborderDO.subMchidSnapshot(),
                    channelSuborderDO.id(),
                    null,
                    "CLOSED",
                    960000,
                    "CNY",
                    null,
                    null,
                    null)),
            false);
    f.f.app.getBean(PaymentWorkTransaction.class).observe(payment, closed, true);
    String path = "/api/custom-orders/" + order;
    JsonNode next = post(path + "/payments", view("expectedVersion", version(path, false)), false, 200);
    String nextId = next.get("payment").get("id").asString();
    assertNotEquals(payment, nextId);
    paid(nextId, f.f.clock.at);
    paid(payment, f.f.clock.at);
    assertEquals(payment, get(path + "/refunds", false).get(0).get("paymentId").asString());
    assertEquals(nextId, get(path, false).get("payment").get("id").asString());
    at(f.f.clock.at.plus(Duration.ofHours(2)).minusMillis(1));
    post(path + "/cancel", view("expectedVersion", version(path, false)), false, 200);
    assertEquals(2, get(path + "/refunds", false).size());
  }

  /** 验证交易准入及渠道不可用时保留报价。 */
  @Test
  void tradeAdmissionAndUnavailableGatewayLeaveQuoteAvailable() throws Exception {
    String id = quoted("buyer_pickup"), path = buyer + "/" + id;
    JsonNode detail = get(path, false);
    Map<String,Object> input = view("expectedVersion", detail.get("version").asInt(),
        "quoteId", detail.get("quotes").get(0).get("id").asString(), "addressId", null);
    f.f.db.update(
        "UPDATE user_realname_verification SET verification_status='expired' WHERE user_id=? AND"
            + " verification_status='verified'",
        f.buyer.id());
    post(path + "/accept-quote", input, false, 409);
    assertTrue(get(path, false).get("orderId").isNull());
    f.f.db.update(
        "UPDATE user_realname_verification SET verification_status='verified' WHERE user_id=? AND"
            + " verification_status='expired'",
        f.buyer.id());
    doThrow(com.mujian.backend.common.support.D2Values.unavailable("PAYMENT_CHANNEL_NOT_READY"))
        .when(f.f.app.getBean(PaymentGateway.class)).requireReady();
    post(path + "/accept-quote", input, false, 503);
    assertEquals("quoted", get(path, false).get("state").asString());
    assertTrue(get(path, false).get("orderId").isNull());
  }

  record DeliverySample(
      String order,
      String buyerPath,
      String factoryPath,
      String delivery,
      String deliveryFile,
      List<String> items,
      String extension,
      String extensionFile) {}

  /** 将订单推进到已交付。 */
  DeliverySample delivered(boolean pickup, boolean withExtension, int index) throws Exception {
    String mode = pickup ? "buyer_pickup" : "factory_delivery";
    JsonNode a = accept(quoted(mode, 320000 + index, pickup ? 0 : 30000 + index), mode);
    String id = a.get("orderId").asString(),
        b = "/api/custom-orders/" + id,
        s = "/api/me/factory/custom-orders/" + id;
    paid(a.get("payment").get("id").asString(), f.f.clock.at);
    post(s + "/start-production", view("expectedVersion", version(s, true)), true, 200);
    String extension = null, extensionFile = null;
    if (withExtension) {
      extensionFile = upload(f, s, "extension", f.supplier, 200);
      extension =
          post(
                  s + "/extension-drafts",
                  view(
                      "expectedVersion",
                      version(s, true),
                      "requestedDate",
                      now(f.f.clock).toLocalDate().plusDays(15).toString(),
                      "reason",
                      "交期核对",
                      "evidenceIds",
                      List.of(extensionFile)),
                  true,
                  200)
              .get("resourceId")
              .asString();
      post(
          "/api/me/factory/custom-extensions/" + extension + "/submit",
          view("expectedVersion", 0),
          true,
          200);
      f.call(
          "POST",
          "/api/admin/custom-extensions/" + extension + "/decision",
          view("expectedVersion", 1, "decision", "approved", "note", "核准交期"),
          null,
          f.key(),
          200);
    }
    post(s + "/ready", view("expectedVersion", version(s, true)), true, 200);
    if (!pickup) post(s + "/dispatch", view("expectedVersion", version(s, true)), true, 200);
    ArrayList<String> items = new ArrayList<String>();
    for (JsonNode item : get(s, true).get("items")) items.add(item.get("id").asString());
    String file = upload(f, s, "delivery", f.supplier, 200);
    String d =
        post(
                s + (pickup ? "/register-pickup" : "/deliver"),
                view(
                    "expectedVersion",
                    version(s, true),
                    "remark",
                    "逐件交接",
                    "orderItemIds",
                    items,
                    "materialIds",
                    List.of(file),
                    "occurredAt",
                    time(now(f.f.clock))),
                true,
                200)
            .get("resourceId")
            .asString();
    if (pickup) {
      assertFalse(canReport(get(b, false)));
      post(
          b + "/confirm-pickup",
          view("expectedVersion", version(b, false), "deliveryId", d),
          false,
          200);
    }
    return new DeliverySample(id, b, s, d, file, items, extension, extensionFile);
  }

  /** 检查该状态能否上报问题。 */
  boolean canReport(JsonNode detail) {
    for (JsonNode action : detail.get("allowedActions"))
      if ("reportCustomAfterSale".equals(action.asString())) return true;
    return false;
  }

  /** 准备问题上报正文。 */
  Map<String, Object> issueInput(DeliverySample sample, String delivery, String file)
      throws Exception {
    return view(
        "expectedVersion",
        version(sample.buyerPath(), false),
        "deliveryId",
        delivery,
        "type",
        "quality_issue",
        "problem",
        "核对交接",
        "requestedSolution",
        "rework",
        "evidenceIds",
        List.of(file));
  }

  /** 验证售后申请与到期验收仅一个成功。 */
  @Test
  void afterSaleAndDueAcceptanceHaveOneWinner() throws Exception {
    D6CustomPurchaseHttpTest.DeliverySample deliverySample = delivered(false, false, 0);
    at(f.f.clock.at.plus(Duration.ofHours(48)).minusMillis(1));
    HttpResponse<Void> response =
        f.f.http.send(
            HttpRequest.newBuilder(URI.create(f.f.base + deliverySample.buyerPath()))
                .header("Authorization", "Bearer " + f.buyer.token())
                .GET()
                .build(),
            HttpResponse.BodyHandlers.discarding());
    assertEquals(200, response.statusCode());
    assertEquals(
        f.f.clock.millis(), response.headers().firstValueAsLong("X-Mujian-Time-Ms").orElseThrow());
    String proof = upload(f, deliverySample.buyerPath(), "after_sale", f.buyer, 200);
    Map<String,Object> input = issueInput(deliverySample, deliverySample.delivery(), proof);
    String path = deliverySample.buyerPath() + "/after-sales";
    assertTrue(canReport(get(deliverySample.buyerPath(), false)));
    at(f.f.clock.at.plusMillis(1));
    Integer before = f.f.db.queryForObject("SELECT COUNT(*) FROM admin_case", Integer.class);
    assertFalse(canReport(get(deliverySample.buyerPath(), false)));
    post(path, input, false, 409);
    at(f.f.clock.at.plusMillis(1));
    post(path, input, false, 409);
    assertEquals("running", get(deliverySample.buyerPath(), false).get("acceptanceState").asString());
    assertEquals(before, f.f.db.queryForObject("SELECT COUNT(*) FROM admin_case", Integer.class));
    CyclicBarrier gate = new java.util.concurrent.CyclicBarrier(2);
    ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> finish =
          pool.submit(
              () -> {
                gate.await();
                return f.f
                    .app
                    .getBean(CustomFulfillment.class)
                    .automatic(new CustomFulfillmentMapper.Due(deliverySample.order(), deliverySample.delivery(), 1));
              });
      Future<Integer> report =
          pool.submit(
              () -> {
                gate.await();
                return status(path, input, f.buyer);
              });
      assertTrue(finish.get(20, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(409, report.get(20, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals("completed", get(deliverySample.buyerPath(), false).get("state").asString());
    } finally {
      pool.shutdownNow();
    }
  }

  /** 验证自提返工使用当前期限并回放及时提交的问题。 */
  @Test
  void pickupReworkUsesCurrentDeadlineAndReplaysTimelyIssue() throws Exception {
    D6CustomPurchaseHttpTest.DeliverySample deliverySample = delivered(true, false, 0);
    at(f.f.clock.at.plus(Duration.ofHours(48)).minusMillis(1));
    String b = deliverySample.buyerPath(), path = b + "/after-sales", key = f.key();
    String evidence = upload(f, b, "after_sale", f.buyer, 200);
    Map<String,Object> input = issueInput(deliverySample, deliverySample.delivery(), evidence);
    assertTrue(canReport(get(b, false)));
    JsonNode receipt = f.call("POST", path, input, f.buyer, key, 200);
    at(f.f.clock.at.plusMillis(2));
    JsonNode replay = f.call("POST", path, input, f.buyer, key, 200);
    assertEquals("replayed", replay.get("outcome").asString());
    for (String field :
        List.of(
            "requestId", "resourceId", "resourceType", "resourceVersion", "operation", "appliedAt"))
      assertEquals(receipt.get(field), replay.get(field));
    assertEquals(1, get(b, false).get("afterSales").size());
    f.call(
        "POST",
        "/api/admin/custom-after-sales/" + receipt.get("resourceId").asString() + "/resolution",
        view(
            "expectedVersion",
            0,
            "resolutionType",
            "rework",
            "goodsRefundFen",
            0,
            "freightRefundFen",
            0,
            "reason",
            "重新交付"),
        null,
        f.key(),
        200);
    String supplier = deliverySample.factoryPath();
    String next =
        post(
                supplier + "/register-pickup",
                view(
                    "expectedVersion",
                    version(supplier, true),
                    "remark",
                    "返工交接",
                    "orderItemIds",
                    deliverySample.items(),
                    "materialIds",
                    List.of(deliverySample.deliveryFile()),
                    "occurredAt",
                    time(now(f.f.clock))),
                true,
                200)
            .get("resourceId")
            .asString();
    assertFalse(canReport(get(b, false)));
    post(
        b + "/confirm-pickup",
        view("expectedVersion", version(b, false), "deliveryId", next),
        false,
        200);
    assertFalse(
        f.f
            .app
            .getBean(CustomFulfillment.class)
            .automatic(new CustomFulfillmentMapper.Due(deliverySample.order(), deliverySample.delivery(), 1)));
    assertTrue(canReport(get(b, false)));
    at(f.f.clock.at.plus(Duration.ofHours(48)).minusMillis(1));
    assertTrue(canReport(get(b, false)));
    String file = upload(f, b, "after_sale", f.buyer, 200);
    post(path, issueInput(deliverySample, deliverySample.delivery(), file), false, 409);
    Map<String,Object> second = issueInput(deliverySample, next, file);
    at(f.f.clock.at.plusMillis(1));
    assertFalse(canReport(get(b, false)));
    post(path, second, false, 409);
    assertEquals(1, get(b, false).get("afterSales").size());
  }

  /** 验证恢复后的期限控制原期限之后的准入。 */
  @Test
  void resumedDeadlineControlsAdmissionAfterOriginalDeadline() throws Exception {
    D6CustomPurchaseHttpTest.DeliverySample deliverySample = delivered(false, false, 0);
    Instant original = f.f.clock.at.plus(Duration.ofHours(48));
    at(original.minusSeconds(120));
    String path = deliverySample.buyerPath() + "/after-sales";
    JsonNode first =
        post(
            path,
            issueInput(deliverySample, deliverySample.delivery(), upload(f, deliverySample.buyerPath(), "after_sale", f.buyer, 200)),
            false,
            200);
    at(original.plusSeconds(60));
    f.call(
        "POST",
        "/api/admin/custom-after-sales/" + first.get("resourceId").asString() + "/resolution",
        view(
            "expectedVersion",
            0,
            "resolutionType",
            "record_only",
            "goodsRefundFen",
            0,
            "freightRefundFen",
            0,
            "reason",
            "恢复原单剩余验收时间"),
        null,
        f.key(),
        200);
    JsonNode detail = get(deliverySample.buyerPath(), false);
    assertEquals(
        original.plusSeconds(180), Instant.parse(detail.get("acceptanceDeadlineAt").asString()));
    at(original.plusSeconds(180).minusMillis(1));
    assertTrue(canReport(get(deliverySample.buyerPath(), false)));
    Map<String,Object> second = issueInput(deliverySample, deliverySample.delivery(), upload(f, deliverySample.buyerPath(), "after_sale", f.buyer, 200));
    at(original.plusSeconds(180));
    assertFalse(canReport(get(deliverySample.buyerPath(), false)));
    post(path, second, false, 409);
    assertEquals(1, get(deliverySample.buyerPath(), false).get("afterSales").size());
  }

  record PageSample(String saleFile, String extFile, int goods, int freight) {}

  /** 核对列表返回与预期摘要。 */
  int verifyList(
      String path,
      boolean sale,
      int size,
      int page,
      int total,
      Map<String, PageSample> expected,
      List<String> orderIds)
      throws Exception {
    AtomicInteger count = f.f.app.getBean(D2ApplicationTest.Fault.class).sqlCalls;
    count.set(0);
    JsonNode result = f.call("GET", path + "&page=" + page + "&pageSize=" + size, null, null, null, 200);
    int sql = count.get();
    assertEquals(total, result.get("total").asInt());
    assertEquals(page, result.get("page").asInt());
    assertEquals(size, result.get("pageSize").asInt());
    JsonNode rows = result.get("items");
    ArrayList<String> ids = new ArrayList<String>();
    for (JsonNode row : rows) {
      String order = row.get("orderId").asString();
      ids.add(order);
      D6CustomPurchaseHttpTest.PageSample facts = expected.get(order);
      assertNotNull(facts);
      assertEquals(1, row.get("evidence").size());
      assertEquals(
          sale ? facts.saleFile() : facts.extFile(),
          row.get("evidence").get(0).get("materialId").asString());
      if (sale) {
        assertEquals(facts.goods(), row.get("goodsAvailableFen").asInt());
        assertEquals(facts.freight(), row.get("freightAvailableFen").asInt());
      }
    }
    assertEquals(orderIds, ids);
    System.out.println(
        "D6 R2 "
            + (sale ? "after-sales" : "extensions")
            + " page="
            + page
            + " pageSize="
            + size
            + " rows="
            + ids.size()
            + " total="
            + total
            + " preparedSQL="
            + sql);
    return sql;
  }

  /** 核对管理分页批量读取不同订单和金额。 */
  @Test
  void adminPagesBatchDifferentOrdersAndAmounts() throws Exception {
    HashMap<String,D6CustomPurchaseHttpTest.PageSample> expected = new HashMap<String, PageSample>();
    ArrayList<String> ids = new ArrayList<String>();
    String extPath = "/api/admin/custom-extensions?status=approved",
        salePath = "/api/admin/custom-after-sales?status=refund_processing";
    int singleExt = 0, singleSale = 0;
    for (int i = 1; i <= 20; i++) {
      f.f.clock.at = f.f.clock.at.plusSeconds(1);
      D6CustomPurchaseHttpTest.DeliverySample deliverySample = delivered(false, true, i);
      String file = upload(f, deliverySample.buyerPath(), "after_sale", f.buyer, 200);
      JsonNode issue =
          post(deliverySample.buyerPath() + "/after-sales", issueInput(deliverySample, deliverySample.delivery(), file), false, 200);
      Map<String,Object> refund =
          view(
              "expectedVersion",
              0,
              "resolutionType",
              "partial_refund",
              "goodsRefundFen",
              i * 5,
              "freightRefundFen",
              i * 2,
              "reason",
              "分科目核准");
      String resolution =
          "/api/admin/custom-after-sales/" + issue.get("resourceId").asString() + "/resolution";
      if (i == 1) {
        f.f.db.update(
            "UPDATE admin_menu SET enabled=0 WHERE permission_code='admin:custom:refund-approve'");
        try {
          f.call("POST", resolution, refund, null, f.key(), 403);
        } finally {
          f.f.db.update(
              "UPDATE admin_menu SET enabled=1 WHERE"
                  + " permission_code='admin:custom:refund-approve'");
        }
      }
      f.call("POST", resolution, refund, null, f.key(), 200);
      if (i == 1) {
        JsonNode buyerRefund = get(deliverySample.buyerPath() + "/refunds", false).get(0);
        JsonNode adminRefund =
            f.call(
                    "GET",
                    "/api/admin/refunds/" + buyerRefund.get("id").asString(),
                    null,
                    null,
                    null,
                    200)
                .get("refund");
        assertEquals(buyerRefund, adminRefund);
        assertEquals(i * 5, buyerRefund.get("goodsFen").asInt());
        assertEquals(i * 2, buyerRefund.get("freightFen").asInt());
        assertEquals(
            f.f.db.queryForObject(
                "SELECT id FROM payment_channel_suborder WHERE custom_order_id=?",
                String.class,
                deliverySample.order()),
            buyerRefund.get("suborderId").asString());
      }
      ids.add(0, deliverySample.order());
      expected.put(
          deliverySample.order(),
          new PageSample(file, deliverySample.extensionFile(), 960000 + i * 3 - i * 5, 30000 + i - i * 2));
      if (i == 1) {
        singleExt = verifyList(extPath, false, 20, 1, 1, expected, ids);
        singleSale = verifyList(salePath, true, 20, 1, 1, expected, ids);
      }
    }
    assertEquals(singleExt, verifyList(extPath, false, 20, 1, 20, expected, ids));
    assertEquals(singleSale, verifyList(salePath, true, 20, 1, 20, expected, ids));
    verifyList(extPath, false, 10, 2, 20, expected, ids.subList(10, 20));
    verifyList(salePath, true, 10, 2, 20, expected, ids.subList(10, 20));
    verifyList(extPath, false, 20, 2, 20, expected, List.of());
    verifyList(salePath, true, 20, 2, 20, expected, List.of());
  }

  /** 验证正式工厂选择仅允许买家。 */
  @Test
  void formalFactorySelectionRequiresBuyer() throws Exception {
    D2Contract contract = f.f.app.getBean(com.mujian.backend.common.security.D2Contract.class);
    try (InputStream source = getClass().getResourceAsStream("/contracts/d6.openapi.json")) {
      JsonNode formal = f.f.json.readTree(source);
      int checked = 0;
      for (Map.Entry<String,JsonNode> path : formal.get("paths").properties()) {
        for (Map.Entry<String,JsonNode> method : path.getValue().properties()) {
          D2Contract.Route route = contract.route(path.getKey().replaceAll("\\{[^}]+}", "123456789abc4def8abc123456789abc"), method.getKey());
          assertNotNull(route);
          assertEquals(path.getKey(), route.template());
          JsonNode expected = f.f.json.readTree(f.f.json.writeValueAsString(method.getValue())
              .replace("#/components/schemas/", "#/components/schemas/D6_"));
          assertEquals(expected, route.operation());
          checked++;
        }
      }
      assertEquals(63, checked);
    }
    for (String path : List.of("/api/custom-factories", "/api/custom-factories/" + f.factoryId)) {
      f.call("GET", path, null, null, null, 401);
      f.call("GET", path, null, f.supplier, null, 403);
      f.call("GET", path, null, f.buyer, null, 200);
    }
  }

  /** 检查部分退款恢复剩余、全退回执后取消。 */
  @Test
  void partialRefundResumesRemainderAndFullRefundCancelsOnlyAfterReceipt() throws Exception {
    JsonNode original = accept(quoted("factory_delivery"), "factory_delivery");
    String id = original.get("orderId").asString(),
        payment = original.get("payment").get("id").asString();
    paid(payment, f.f.clock.at);
    String b = "/api/custom-orders/" + id, s = "/api/me/factory/custom-orders/" + id;
    for (String action : List.of("start-production", "ready", "dispatch"))
      post(s + "/" + action, view("expectedVersion", version(s, true)), true, 200);
    ArrayList<Object> items = new ArrayList<>();
    for (JsonNode item : get(s, true).get("items")) items.add(item.get("id").asString());
    Map<String,Object> input =
        view(
            "expectedVersion",
            version(s, true),
            "remark",
            "全量签收",
            "materialIds",
            List.of(upload(f, s, "delivery", f.supplier, 200)),
            "orderItemIds",
            items,
            "occurredAt",
            time(now(f.f.clock)));
    String d = post(s + "/deliver", input, true, 200).get("resourceId").asString();
    at(f.f.clock.at.plus(Duration.ofHours(8)));
    String image = upload(f, b, "after_sale", f.buyer, 200);
    ChannelSuborderDO channelSuborderDO =
        f.f.app.getBean(PaymentOrderMapper.class).selectSubordersByPayment(payment, false).get(0);
    for (boolean full : List.of(false, true)) {
      String type = full ? "full_refund" : "partial_refund";
      int goods = full ? 940000 : 20000, freight = full ? 29500 : 500;
      String sale =
          post(
                  b + "/after-sales",
                  view(
                      "expectedVersion",
                      version(b, false),
                      "deliveryId",
                      d,
                      "type",
                      "quality_issue",
                      "problem",
                      "材料需核对",
                      "requestedSolution",
                      type,
                      "evidenceIds",
                      List.of(image)),
                  false,
                  200)
              .get("resourceId")
              .asString();
      CustomFulfillmentMapper.Due due = new CustomFulfillmentMapper.Due(id, d, 1);
      assertFalse(f.f.app.getBean(CustomFulfillment.class).automatic(due));
      String admin = "/api/admin/custom-after-sales/" + sale;
      f.call(
          "POST",
          admin + "/resolution",
          view(
              "expectedVersion",
              0,
              "resolutionType",
              type,
              "goodsRefundFen",
              goods,
              "freightRefundFen",
              freight,
              "reason",
              "逐项核准"),
          null,
          f.key(),
          200);
      assertEquals("after_sale", get(b, false).get("state").asString());
      String refund = f.call("GET", admin, null, null, null, 200).get("refundId").asString();
      at(f.f.clock.at.plusSeconds(600));
      PaymentGateway.RefundFact fact =
          new PaymentGateway.RefundFact(
              "test-platform",
              channelSuborderDO.subMchidSnapshot(),
              channelSuborderDO.id(),
              channelSuborderDO.channelSubTransactionId(),
              refund,
              "T" + Ids.next(),
              "SUCCESS",
              channelSuborderDO.paidAmountFen(),
              goods + freight,
              "CNY",
              f.f.clock.at,
              false);
      RefundWorkTransaction work = f.f.app.getBean(RefundWorkTransaction.class);
      work.observe(refund, fact);
      work.observe(refund, fact);
      JsonNode o = get(b, false);
      assertEquals(full ? "cancelled" : "pending_acceptance", o.get("state").asString());
      if (!full)
        assertEquals(time(now(f.f.clock).plusHours(40)), o.get("acceptanceDeadlineAt").asString());
    }
    assertEquals(990000, get(b, false).get("refundedFen").asInt());
  }

  /** 检查非法预算及草稿引用的数据边界。 */
  @Test
  void databaseRejectsInvalidBudgetAndDraftReferencesStayScoped() throws Exception {
    String id =
        post(buyer, view("factoryId", f.factoryId), false, 200).get("resourceId").asString();
    for (String bad :
        List.of(
            "[null,null]",
            "[2,1]",
            "[2147483648,null]",
            "[-1,null]",
            "[true,5]",
            "[1.5,2]",
            "[1]",
            "{}",
            "[\"5\",6]")) {
      assertThrows(
          org.springframework.dao.DataAccessException.class,
          () ->
              f.f.db.update(
                  "UPDATE custom_requirement SET budget_range_fen=CAST(? AS JSON) WHERE id=?",
                  bad,
                  id),
          bad);
    }
    assertNull(
        f.f.db.queryForObject(
            "SELECT budget_range_fen FROM custom_requirement WHERE id=?", String.class, id));
    D2ApplicationTest.User other = f.f.user();
    String path = buyer + "/" + id;
    String file = upload(f, path, "requirement", f.buyer, 200);
    f.call("GET", path + "/materials/" + file + "/view", null, other, null, 404);
    f.call("GET", path + "/materials/" + file + "/view", null, f.supplier, null, 403);
    Map<String,Object> draft = draft("factory_delivery");
    draft.put("expectedVersion", 0);
    draft.put(
        "attachments", List.of(view("materialId", file, "name", "test.png", "type", "image")));
    post(path + "/draft", draft, false, 200);
    String key = f.key();
    Map<String,Object> action = view("expectedVersion", 1);
    f.call("POST", path + "/submit", action, f.buyer, key, 200);
    f.call("POST", path + "/submit", action, f.buyer, key, 200);
    f.call("POST", path + "/submit", view("expectedVersion", 2), f.buyer, key, 409);
    f.call("GET", factory + "/" + id + "/materials/" + file + "/view", null, f.supplier, null, 200);
  }

  /** 读取用于乐观并发校验的版本。 */
  int version(String root, boolean factory) throws Exception {
    return get(root, factory).get("version").asInt();
  }

  /** 更新当前测试会话或期限。 */
  void renew() throws Exception {
    D5ClaimStockHttpTest.renewAdmin(f);
    f.buyer =
        new D2ApplicationTest.User(
            f.buyer.id(), f.f.sessions.create(f.buyer.id(), "miniapp").session().getId());
    f.supplier =
        new D2ApplicationTest.User(
            f.supplier.id(), f.f.sessions.create(f.supplier.id(), "miniapp").session().getId());
  }

  /** 验证自提等待买家且只确认当前交付记录。 */
  @Test
  void pickupWaitsForBuyerAndOnlyCurrentDeliveryCanComplete() throws Exception {
    delivery(true);
  }

  /** 核对返工退款按净货款和运费结算。 */
  @Test
  void reworkWithGoodsRefundSettlesNetGoodsAndFreight() throws Exception {
    delivery(false);
  }

  /** 读取待核对的分页面。 */
  JsonNode pages(String mode, String order) throws Exception {
    Process script =
        new ProcessBuilder(
                System.getenv("MJ_D6_NODE"), "../mujian-frontend/tests/d6-pages-http.cjs")
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    try {
      try (OutputStream in = script.getOutputStream()) {
        in.write(
            f.f.json.writeValueAsBytes(
                view(
                    "mode",
                    mode,
                    "cookie",
                    f.f.adminCookie,
                    "base",
                    f.f.base,
                    "token",
                    f.buyer.token(),
                    "supplier",
                    f.supplier.token(),
                    "address",
                    f.address,
                    "factory",
                    f.factoryId,
                    "sku",
                    f.sku,
                    "form",
                    draft("factory_delivery"),
                    "order",
                    order)));
      }
      assertTrue(script.waitFor(45, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(0, script.exitValue());
      return f.f.json.readTree(script.getInputStream().readAllBytes());
    } finally {
      if (script.isAlive()) script.destroyForcibly();
    }
  }

  /** 准备对应的交付记录。 */
  void delivery(boolean pickup) throws Exception {
    if (pickup) {
      f.acceptedCancellationRequotesAtBoundaryAndKeepsDeductionOnRefund();
      renew();
    }
    String mode = pickup ? "buyer_pickup" : "factory_delivery";
    boolean usePages = !pickup && System.getenv("MJ_D6_NODE") != null;
    JsonNode attempt = usePages ? pages("create", null) : accept(quoted(mode), mode);
    String id = attempt.get(usePages ? "order" : "orderId").asString();
    String payment =
        usePages ? attempt.get("payment").asString() : attempt.get("payment").get("id").asString();
    paid(payment, f.f.clock.instant());
    String b = "/api/custom-orders/" + id, s = "/api/me/factory/custom-orders/" + id;
    post(s + "/start-production", view("expectedVersion", version(s, true)), true, 200);
    JsonNode draft =
        post(
            s + "/extension-drafts",
            view(
                "expectedVersion",
                version(s, true),
                "requestedDate",
                now(f.f.clock).toLocalDate().plusDays(15).toString(),
                "reason",
                "按买方安排延后",
                "evidenceIds",
                List.of()),
            true,
            200);
    String ext = draft.get("resourceId").asString();
    post(
        "/api/me/factory/custom-extensions/" + ext + "/submit",
        view("expectedVersion", 0),
        true,
        200);
    Map<String,Object> decision = view("expectedVersion", 1, "decision", "approved", "note", "双方确认日期");
    String key = f.key();
    f.call("POST", "/api/admin/custom-extensions/" + ext + "/decision", decision, null, key, 200);
    f.call("POST", "/api/admin/custom-extensions/" + ext + "/decision", decision, null, key, 200);
    post(s + "/ready", view("expectedVersion", version(s, true)), true, 200);
    if (!pickup) post(s + "/dispatch", view("expectedVersion", version(s, true)), true, 200);
    ArrayList<String> items = new ArrayList<String>();
    for (JsonNode item : get(s, true).get("items")) items.add(item.get("id").asString());
    String file = upload(f, s, "delivery", f.supplier, 200),
        path = s + (pickup ? "/register-pickup" : "/deliver");
    Map<String,Object> data =
        view(
            "expectedVersion",
            version(s, true),
            "remark",
            "全部逐件清点",
            "orderItemIds",
            List.of(items.get(0)),
            "materialIds",
            List.of(file),
            "occurredAt",
            time(now(f.f.clock)));
    post(path, data, true, 400);
    data.put("orderItemIds", items);
    String d = post(path, data, true, 200).get("resourceId").asString();
    CustomFulfillment flow = f.f.app.getBean(CustomFulfillment.class);
    if (pickup) {
      assertTrue(get(b, false).get("acceptanceDeadlineAt").isNull());
      assertFalse(flow.automatic(new CustomFulfillmentMapper.Due(id, d, 1)));
      Map<String,Object> handover = view("expectedVersion", version(b, false), "deliveryId", d);
      post(b + "/confirm-pickup", handover, false, 200);
      JsonNode deadline = get(b, false).get("acceptanceDeadlineAt");
      post(b + "/confirm-pickup", handover, false, 200);
      assertEquals(deadline, get(b, false).get("acceptanceDeadlineAt"));
    }
    f.f.clock.at = f.f.clock.at.plus(Duration.ofHours(8));
    renew();
    String evidence = upload(f, b, "after_sale", f.buyer, 200);
    String after =
        post(
                b + "/after-sales",
                view(
                    "expectedVersion",
                    version(b, false),
                    "deliveryId",
                    d,
                    "type",
                    "quality_issue",
                    "problem",
                    "桌角需补做",
                    "requestedSolution",
                    "rework",
                    "evidenceIds",
                    List.of(evidence)),
                false,
                200)
            .get("resourceId")
            .asString();
    assertEquals(40 * 3600, get(b, false).path("remainingSeconds").asInt());
    String admin = "/api/admin/custom-after-sales/" + after;
    String statements = "/api/me/factory/custom-after-sales/" + after + "/statements";
    Map<String,Object> facts = view("expectedVersion", 0, "summary", "已复核交付照片，桌角补做", "evidenceIds", List.of(file));
    String statementKey = f.key();
    f.call("POST", statements, facts, f.supplier, statementKey, 200);
    f.call("POST", statements, facts, f.supplier, statementKey, 200);
    assertEquals(1, get(statements, true).get("items").size());
    JsonNode caseContext = f.call("GET", admin + "/case", null, null, null, 200);
    assertEquals(1, caseContext.get("factoryStatements").size());
    String caseId = caseContext.get("caseId").asString();
    Map<String,Object> note =
        view(
            "caseVersion",
            caseContext.get("caseVersion").asInt(),
            "contactChannel",
            "phone",
            "summary",
            "已核对本次交付",
            "references",
            List.of());
    String noteKey = f.key();
    f.call("POST", "/api/admin/cases/" + caseId + "/contact-records", note, null, noteKey, 200);
    f.call("POST", "/api/admin/cases/" + caseId + "/contact-records", note, null, noteKey, 200);
    assertEquals(1, f.call("GET", admin + "/case", null, null, null, 200).get("notes").size());
    String mainCookie = f.f.adminCookie, mainCsrf = f.f.csrf, readerName = "d6." + Ids.next();
    f.f
        .app
        .getBean(com.mujian.backend.application.service.admin.AdminInitializationService.class)
        .initialize(readerName, UUID.randomUUID().toString());
    String readerId =
        f.f.db.queryForObject(
            "SELECT user_id FROM admin_account WHERE username=?", String.class, readerName);
    f.f.createdUsers.add(readerId);
    f.f.adminCookie = "mj_admin_dev=" + f.f.sessions.create(readerId, "admin").session().getId();
    f.f.csrf =
        f.f.call("GET", "/api/admin/auth/csrf", null, null, null, 200).get("csrfToken").asString();
    for (String perm : List.of("admin:custom:after-sale-resolve", "admin:custom:refund-approve")) {
      f.f.db.update("UPDATE admin_menu SET enabled=0 WHERE permission_code=?", perm);
      try {
        f.call("GET", admin, null, null, null, 200);
        f.call(
            "POST",
            admin + "/resolution",
            view(
                "expectedVersion",
                1,
                "resolutionType",
                "rework",
                "goodsRefundFen",
                20000,
                "freightRefundFen",
                0,
                "reason",
                "权限核对"),
            null,
            f.key(),
            403);
      } finally {
        f.f.db.update("UPDATE admin_menu SET enabled=1 WHERE permission_code=?", perm);
      }
    }
    f.f.adminCookie = mainCookie;
    f.f.csrf = mainCsrf;
    f.call(
        "POST",
        admin + "/resolution",
        view(
            "expectedVersion",
            1,
            "resolutionType",
            "rework",
            "goodsRefundFen",
            960001,
            "freightRefundFen",
            0,
            "reason",
            "超额"),
        null,
        f.key(),
        409);
    if (usePages) pages("review", id);
    else
      f.call(
          "POST",
          admin + "/resolution",
          view(
              "expectedVersion",
              1,
              "resolutionType",
              "rework",
              "goodsRefundFen",
              20000,
              "freightRefundFen",
              0,
              "reason",
              "补做桌角及差价"),
          null,
          f.key(),
          200);
    data.put("expectedVersion", version(s, true));
    data.put("occurredAt", time(now(f.f.clock)));
    String next = post(path, data, true, 200).get("resourceId").asString();
    assertNotEquals(d, next);
    if (pickup)
      post(
          b + "/confirm-pickup",
          view("expectedVersion", version(b, false), "deliveryId", next),
          false,
          200);
    assertEquals("after_sale", get(b, false).get("state").asString());
    ChannelSuborderDO channelSuborderDO =
        f.f.app.getBean(PaymentOrderMapper.class).selectSubordersByPayment(payment, false).get(0);
    String refund = f.call("GET", admin, null, null, null, 200).get("refundId").asString();
    PaymentGateway.RefundFact fact =
        new PaymentGateway.RefundFact(
            "test-platform",
            channelSuborderDO.subMchidSnapshot(),
            channelSuborderDO.id(),
            channelSuborderDO.channelSubTransactionId(),
            refund,
            "RF" + Ids.next(),
            "SUCCESS",
            channelSuborderDO.paidAmountFen(),
            20000,
            "CNY",
            f.f.clock.at,
            false);
    RefundWorkTransaction refunds = f.f.app.getBean(RefundWorkTransaction.class);
    refunds.observe(refund, fact);
    refunds.observe(refund, fact);
    JsonNode current = get(b, false);
    assertEquals("pending_acceptance", current.get("state").asString());
    assertEquals(
        time(now(f.f.clock).plusHours(48)), current.get("acceptanceDeadlineAt").asString());
    f.f.clock.at = f.f.clock.at.plus(Duration.ofHours(48));
    renew();
    assertFalse(flow.automatic(new CustomFulfillmentMapper.Due(id, d, 1)));
    assertTrue(flow.automatic(new CustomFulfillmentMapper.Due(id, next, 2)));
    f.f.clock.at = f.f.clock.at.plus(Duration.ofDays(2));
    renew();
    f.f.app.getBean(SettlementGeneration.class).generate(f.factoryId);
    Map<String,Object> money =
        f.f.db.queryForMap(
            "SELECT goods_settle_base_amount_fen base,platform_commission_amount_fen"
                + " fee,factory_settle_amount_fen net FROM custom_order_settlement WHERE"
                + " custom_order_id=?",
            id);
    assertEquals(940000, money.get("base"));
    assertEquals(188000, money.get("fee"));
    assertEquals(pickup ? 752000 : 782000, money.get("net"));
    JsonNode trace = f.call("GET", "/api/admin/payments/" + payment, null, null, null, 200);
    assertEquals(id, trace.get("customOrderId").asString());
    JsonNode refundDetail = f.call("GET", "/api/admin/refunds/" + refund, null, null, null, 200);
    assertEquals(20000, refundDetail.path("refund").get("goodsFen").asInt());
    String batch =
        f.f.db.queryForObject(
            "SELECT settlement_batch_id FROM custom_order_settlement WHERE custom_order_id=?",
            String.class,
            id);
    JsonNode all =
        f.call("GET", "/api/admin/settlements/" + batch + "/items", null, null, null, 200)
            .get("items");
    assertEquals(pickup ? 2 : 1, all.size());
    boolean found = false;
    for (JsonNode item : all) {
      if (item.get("orderId").asString().equals(id)) {
        found = true;
        assertEquals(940000, item.get("goodsBaseFen").asInt());
      } else assertFalse(item.has("goodsBaseFen"));
    }
    assertTrue(found);
    f.f.app.getBean(SettlementGeneration.class).generate(f.factoryId);
    if (usePages) pages("read", id);
    System.out.println(
        "D6 synthetic "
            + mode
            + " requirement="
            + f.f.db.queryForObject(
                "SELECT requirement_id FROM custom_order WHERE id=?", String.class, id)
            + " case="
            + caseId
            + " batch="
            + batch
            + " suborder="
            + channelSuborderDO.id()
            + " order="
            + id
            + " payment="
            + payment
            + " refund="
            + refund
            + " goods_base="
            + money.get("base")
            + " commission="
            + money.get("fee")
            + " factory_net="
            + money.get("net"));
    String chain =
        f.f.db.queryForObject(
            "SELECT id FROM wechat_profit_sharing_order WHERE custom_order_id=?", String.class, id);
    SharingWork sharing = f.f.app.getBean(SharingWork.class);
    for (boolean finish : List.of(false, true)) {
      SharingWork.Claim claim = sharing.claim(chain);
      assertNotNull(claim);
      assertEquals(finish, claim.request().finish());
      assertFalse(claim.query());
      if (pickup && !finish) {
        String originalNo = claim.request().outNo();
        sharing.observe(
            claim,
            new com.mujian.backend.infrastructure.payment.wechat.ProfitSharingGateway.Observation(
                com.mujian.backend.common.enums.ProfitSharingOutcome.UNKNOWN, 0, null));
        assertNull(sharing.claim(chain));
        f.f.clock.at = f.f.clock.at.plusSeconds(30);
        claim = sharing.claim(chain);
        assertNotNull(claim);
        assertTrue(claim.query());
        assertEquals(originalNo, claim.request().outNo());
      }
      sharing.observe(
          claim,
          new com.mujian.backend.infrastructure.payment.wechat.ProfitSharingGateway.Observation(
              com.mujian.backend.common.enums.ProfitSharingOutcome.ACCEPTED, 0, null));
      f.f.clock.at = f.f.clock.at.plusSeconds(10);
      claim = sharing.claim(chain);
      assertNotNull(claim);
      assertTrue(claim.query());
      sharing.observe(
          claim,
          new com.mujian.backend.infrastructure.payment.wechat.ProfitSharingGateway.Observation(
              com.mujian.backend.common.enums.ProfitSharingOutcome.SUCCESS,
              claim.request().amount(),
              "TEST" + Ids.next()));
    }
    assertEquals(
        "settled",
        f.f.db.queryForObject(
            "SELECT settlement_status FROM custom_order_settlement WHERE custom_order_id=?",
            String.class,
            id));
  }

  /** 上传场景所需材料。 */
  static String upload(
      D3BQuoteTest fixture, String root, String node, D2ApplicationTest.User actor, int status)
      throws Exception {
    String boundary = "Order" + Ids.next(), path = root + "/materials";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\n"
                + node
                + "\r\n--"
                + boundary
                + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"test.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    out.write(D2ApplicationTest.png());
    out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(fixture.f.base + path))
            .header("Idempotency-Key", fixture.key())
            .header("Authorization", "Bearer " + actor.token())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
            .build();
    return fixture
        .f
        .received(
            "POST",
            path,
            fixture.f.http.send(request, HttpResponse.BodyHandlers.ofString()),
            status)
        .path("materialId")
        .asString();
  }
}
