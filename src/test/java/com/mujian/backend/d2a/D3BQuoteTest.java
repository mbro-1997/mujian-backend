package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.security.D2Contract;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import com.mujian.backend.infrastructure.map.RouteGateway;
import com.mujian.backend.order.domain.CheckoutQuoteData;
import com.mujian.backend.order.service.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import tools.jackson.databind.JsonNode;

/** 真HTTP、真隔离数据库；仅地图外部调用使用明确替身，不表示真实渠道验收。 */
class D3BQuoteTest {
  final D2CApplicationTest fixture = new D2CApplicationTest();
  final D2ApplicationTest f = fixture.f;
  User buyer, supplier;
  String factoryId, sku, checklist, address, coupon;
  Map<String, Object> input;
  Class<?>[] extraSources = new Class<?>[0];
  String[] extraArguments = new String[0];

  static class Routes implements RouteGateway {
    int calls;
    boolean fail;
    Clock clock;

    Routes(Clock clock) {
      this.clock = clock;
    }

    /** 提供受控的地址编码结果。 */
    public Point geocode(String address) {
      return new Point("31.2", address.contains("接收路") ? "121.5" : "121.4", "310100", "310106");
    }

    /** 提供受控的驾车距离。 */
    public Route driving(Point from, Point point) {
      calls++;
      if (fail) throw unavailable("MAP_UNAVAILABLE");
      return new Route(from, point, from.longitude().equals("121.4") ? 10200 : 11001, clock.instant());
    }
  }

  static class PaymentTestKeys implements AutoCloseable {
    final java.nio.file.Path directory;
    final java.security.KeyPair platform;
    final org.springframework.mock.env.MockEnvironment environment;

    PaymentTestKeys() throws Exception {
      directory = java.nio.file.Files.createTempDirectory("mujian-d3b-notify-");
      java.security.KeyPairGenerator generator = java.security.KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      platform = generator.generateKeyPair();
      java.nio.file.Files.writeString(
          directory.resolve("public.pem"),
          "-----BEGIN PUBLIC KEY-----\n"
              + Base64.getMimeEncoder().encodeToString(platform.getPublic().getEncoded())
              + "\n-----END PUBLIC KEY-----");
      environment =
          new org.springframework.mock.env.MockEnvironment()
              .withProperty(
                  "mj.payment.wechat.platform-keys.PUB_KEY_ID_TEST",
                  directory.resolve("public.pem").toString())
              .withProperty("mj.payment.wechat.api-v3-key", "0123456789abcdef0123456789abcdef");
    }

    /** 关闭夹具持有的资源。 */
    public void close() throws Exception {
      java.nio.file.Files.deleteIfExists(directory.resolve("public.pem"));
      java.nio.file.Files.deleteIfExists(directory);
    }
  }

  @TestConfiguration
  static class Fakes {
    /** 装配隔离付款网关。 */
    @Bean
    @Primary
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway testPaymentGateway(
        com.mujian.backend.infrastructure.payment.wechat.WechatPaymentGateway official) {
      return org.mockito.Mockito.spy(official);
    }

    /** 装配测试用租赁奖励映射。 */
    @Bean
    @Primary
    com.mujian.backend.reward.mapper.RentalRewardMapper testRentalRewardMapper(
        org.mybatis.spring.SqlSessionTemplate sqlSessionTemplate) {
      return org.mockito.Mockito.spy(
          sqlSessionTemplate.getMapper(com.mujian.backend.reward.mapper.RentalRewardMapper.class));
    }

    /** 构造渠道测试密钥配置。 */
    @Bean
    PaymentTestKeys paymentTestKeys() throws Exception {
      return new PaymentTestKeys();
    }

    /** 装配支付签名及解密测试能力。 */
    @Bean
    @Primary
    com.mujian.backend.infrastructure.payment.wechat.WechatPaymentCrypto paymentTestCrypto(
        PaymentTestKeys keys, D2ApplicationTest.MutableClock mutableClock) {
      return new com.mujian.backend.infrastructure.payment.wechat.WechatPaymentCrypto(
          keys.environment, mutableClock);
    }

    /** 准备报价涉及的路线。 */
    @Bean
    @Primary
    Routes quoteRoutes(D2ApplicationTest.MutableClock mutableClock) {
      return new Routes(mutableClock);
    }
  }

  /** 初始化本组隔离依赖。 */
  @BeforeEach
  void start() throws Exception {
    List<Class<?>> sources = new ArrayList<>(List.of(Fakes.class));
    sources.addAll(List.of(extraSources));
    f.additionalSources = sources.toArray(Class<?>[]::new);
    List<String> arguments =
        new ArrayList<>(
            List.of("--mj.prop.qr-worker-enabled=false", "--mj.trading.recovery-enabled=false"));
    arguments.addAll(List.of(extraArguments));
    f.start(0, arguments.toArray(String[]::new));
    buyer = f.user();
    supplier = fixture.supplier();
    String real =
        f.call(
                "POST",
                "/api/me/realname-verifications",
                f.body(buyer, "realname"),
                buyer,
                Ids.next(),
                200)
            .path("application")
            .path("id")
            .asString();
    String adminPath = "/api/admin/realname-verifications/" + real;
    JsonNode detail = f.call("GET", adminPath, null, null, null, 200);
    f.call(
        "POST",
        adminPath + "/approve",
        f.reviewBody(detail, true, "realname"),
        null,
        Ids.next(),
        200);
    JsonNode made = fixture.create(supplier, "B_CONSUMABLE", 6);
    sku = made.path("skuId").asString();
    fixture.approve("sku_audit", made.path("targetId").asString(), 0);
    factoryId =
        f.db.queryForObject("SELECT factory_id FROM prop_sku WHERE id=?", String.class, sku);
    f.db.update(
        "INSERT INTO"
            + " factory_wechat_merchant_account(id,factory_id,channel_provider,sub_mchid,subject_type,subject_name_snapshot,account_status,profit_sharing_status,created_at,updated_at)"
            + " VALUES"
            + " (?,?,'wechat_platform_collection',?,'SUBJECT_TYPE_ENTERPRISE','隔离测试收款主体','active','active',?,?)",
        Ids.next(),
        factoryId,
        "test_" + Ids.next(),
        now(f.clock),
        now(f.clock));
    String date = now(f.clock).toLocalDate().plusDays(5).toString();
    checklist =
        call(
                "POST",
                "/api/me/checklists",
                view("sourceType", "manual", "startDate", date, "endDate", date),
                buyer,
                key(),
                200)
            .path("resourceId")
            .asString();
    call(
        "POST",
        "/api/me/checklists/" + checklist + "/items",
        view("expectedVersion", 0, "skuId", sku, "quantity", 2, "remark", null),
        buyer,
        key(),
        200);
    address =
        call(
                "POST",
                "/api/me/addresses",
                view(
                    "receiverName",
                    "隔离接收人",
                    "phone",
                    "13800000001",
                    "regionText",
                    "上海市 静安区",
                    "detailAddress",
                    "接收路1号",
                    "isDefault",
                    true),
                buyer,
                key(),
                200)
            .path("resourceId")
            .asString();
    input =
        view(
            "checklistId",
            checklist,
            "expectedVersion",
            1,
            "addressId",
            address,
            "addressVersion",
            0,
            "couponId",
            null,
            "deliveries",
            List.of(
                view(
                    "factoryId",
                    factoryId,
                    "outboundService",
                    "self_pickup",
                    "returnService",
                    "user_return",
                    "buyerRemark",
                    null)));
    String template = Ids.next();
    coupon = Ids.next();
    f.db.update(
        "INSERT INTO"
            + " coupon_template(id,template_code,template_name,template_version,coupon_type,face_amount_fen,threshold_rent_amount_fen,applicable_scope_type,cost_bearer,validity_type,valid_days,refund_return_policy,return_extension_days,template_status,created_by_admin_user_id,published_at,created_at,updated_at)"
            + " VALUES (?,?,"
            + " '隔离优惠券',1,'fixed_amount',5000,1,'all','shared_by_settlement_ratio','relative_days',30,'return_with_extension',7,'active',?,?,?,?)",
        template,
        "test_" + template,
        f.admin,
        now(f.clock),
        now(f.clock),
        now(f.clock));
    f.db.update(
        "INSERT INTO"
            + " user_coupon(id,user_coupon_no,template_id,user_id,issue_source_type,issue_source_id,issue_request_id,coupon_status,coupon_type_snapshot,face_amount_fen_snapshot,threshold_rent_amount_fen_snapshot,cost_bearer_snapshot,valid_from,valid_until,created_at,updated_at)"
            + " VALUES (?,?,?,?,"
            + " 'manual_operation',?,?,'available','fixed_amount',5000,1,'shared_by_settlement_ratio',?,?,?,?)",
        coupon,
        coupon,
        template,
        buyer.id(),
        template,
        coupon,
        now(f.clock).minusDays(1),
        now(f.clock).plusDays(30),
        now(f.clock),
        now(f.clock));
  }

  /** 释放本组创建的资源。 */
  @AfterEach
  void stop() {
    try {
      D3BFixtureCleanup.remove(f);
    } finally {
      fixture.stop();
    }
  }

  /** 生成本次用例的独立标识。 */
  String key() {
    return UUID.randomUUID().toString();
  }

  /** 执行请求并检查预期响应。 */
  JsonNode call(String method, String path, Object body, User user, String request, int expected)
      throws Exception {
    JsonNode data = f.call(method, path, body, user, request, expected);
    if (expected == 200) {
      D2Contract contract = f.app.getBean(D2Contract.class);
      contract.validate(
          contract
              .route(path.split("\\?")[0], method)
              .operation()
              .path("responses")
              .path("200")
              .path("content")
              .path("application/json")
              .path("schema"),
          f.json.valueToTree(
              view("code", "OK", "message", "成功", "data", data, "traceId", Ids.next())));
    }
    return data;
  }

  /** 检查报价、优惠方向和数据归属。 */
  @Test
  void quotationCouponIntegrityDirectionsAndOwnership() throws Exception {
    call("GET", "/api/me/transport-price-sheet", null, buyer, null, 200);
    Routes routes = f.app.getBean(Routes.class);
    JsonNode choices = call("POST", "/api/me/checkout-coupons", input, buyer, null, 200);
    assertEquals(5000, choices.path("items").get(0).path("savingFen").asInt());
    JsonNode noCoupon = call("POST", "/api/me/checkout-quotes", input, buyer, null, 200);
    assertEquals(0, noCoupon.path("amounts").path("couponDiscountFen").asInt());
    assertEquals(0, routes.calls);
    input.put("couponId", coupon);
    JsonNode quoted = call("POST", "/api/me/checkout-quotes", input, buyer, null, 200);
    assertEquals(5000, quoted.path("amounts").path("couponDiscountFen").asInt());
    TradingTokens tokens = f.app.getBean(TradingTokens.class);
    CheckoutQuoteData confirmed =
        tokens.readQuote(buyer.id(), quoted.path("quoteToken").asString());
    assertEquals(
        confirmed,
        f.app
            .getBean(CheckoutQuotation.class)
            .calculate(buyer.id(), confirmed.input(), confirmed)
            .data());
    assertThrows(
        ApiFailure.class,
        () -> tokens.readQuote(f.user().id(), quoted.path("quoteToken").asString()));
    String token = quoted.path("quoteToken").asString();
    assertThrows(
        ApiFailure.class,
        () -> tokens.readQuote(buyer.id(), token.substring(0, token.length() - 8) + "AAAAAAAA"));
    call("GET", "/api/me/coupons?status=available", null, buyer, null, 200);
    call("GET", "/api/me/coupons/" + coupon, null, buyer, null, 200);
    call("GET", "/api/me/coupons/" + coupon, null, f.user(), null, 404);
    call("GET", "/api/me/coupon-use-records", null, buyer, null, 200);
    f.db.update(
        "UPDATE prop_sku SET daily_rent_price_fen=daily_rent_price_fen+1,version=version+1 WHERE"
            + " id=?",
        sku);
    assertThrows(
        ApiFailure.class,
        () ->
            f.app
                .getBean(CheckoutQuotation.class)
                .calculate(buyer.id(), confirmed.input(), confirmed));
    f.db.update(
        "UPDATE prop_sku SET daily_rent_price_fen=daily_rent_price_fen-1,version=version+1 WHERE"
            + " id=?",
        sku);
    String rule = prepareFreightPool();
    JsonNode transport = call("POST", "/api/me/checkout-quotes", input, buyer, null, 200);
    assertEquals(10900, transport.path("amounts").path("freightFen").asInt());
    assertEquals(2, routes.calls);
    assertEquals(
        10200, transport.path("groups").get(0).path("outbound").path("distanceMeter").asInt());
    assertEquals(
        11001, transport.path("groups").get(0).path("return").path("distanceMeter").asInt());
    routes.fail = true;
    call("POST", "/api/me/checkout-quotes", input, buyer, null, 503);
    assertEquals("MAP_UNAVAILABLE", f.lastReason);
    routes.fail = false;
    call("POST", "/api/me/checkout-quotes", input, f.user(), null, 409);
    assertEquals("REALNAME_REQUIRED", f.lastReason);
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM checkout_batch WHERE buyer_user_id=?",
            Integer.class,
            buyer.id()));
    assertEquals(
        "available",
        f.db.queryForObject(
            "SELECT coupon_status FROM user_coupon WHERE id=?", String.class, coupon));
    f.clock.at = f.clock.at.plusSeconds(301);
    assertThrows(ApiFailure.class, () -> tokens.readQuote(buyer.id(), token));
  }

  /** 提交内注入回滚，验证快照、资金、库存、券和回执无部分保存。 */
  @Test
  void submitAtomicityStableReplayAndHeldBuffer() throws Exception {
    input.put("couponId", coupon);
    String token =
        call("POST", "/api/me/checkout-quotes", input, buyer, null, 200)
            .path("quoteToken")
            .asString();
    CheckoutQuoteData confirmed = f.app.getBean(TradingTokens.class).readQuote(buyer.id(), token);
    com.mujian.backend.order.dto.TradingCommand command =
        new com.mujian.backend.order.dto.TradingCommand(
            buyer.id(),
            key(),
            com.mujian.backend.common.enums.TradingOperation.submitCheckout,
            "a".repeat(64));
    com.mujian.backend.payment.dto.PaymentAccount account =
        new com.mujian.backend.payment.dto.PaymentAccount(
            "test-app",
            "test-platform",
            "https://example.invalid/api/wechat-pay/notifications/payment");
    CheckoutSubmissionTransaction transaction = f.app.getBean(CheckoutSubmissionTransaction.class);
    org.springframework.transaction.support.TransactionTemplate template =
        new org.springframework.transaction.support.TransactionTemplate(
            f.app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    template.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    assertThrows(
        IllegalStateException.class,
        () ->
            template.execute(
                status -> {
                  transaction.submit(command, confirmed, account);
                  throw new IllegalStateException("isolated commit failure");
                }));
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM checkout_batch WHERE buyer_user_id=?",
            Integer.class,
            buyer.id()));
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM inventory_reservation WHERE sku_id=?", Integer.class, sku));
    assertEquals(
        "available",
        f.db.queryForObject(
            "SELECT coupon_status FROM user_coupon WHERE id=?", String.class, coupon));
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM trading_operation_record WHERE actor_user_id=?",
            Integer.class,
            buyer.id()));
    com.mujian.backend.order.vo.TradingReceipt receipt =
        transaction.submit(command, confirmed, account);
    String batch = receipt.resourceId();
    assertEquals("applied", receipt.outcome());
    assertEquals(
        "checkout_processing",
        f.db.queryForObject(
            "SELECT checklist_status FROM rental_checklist WHERE id=?", String.class, checklist));
    assertEquals(
        "locked",
        f.db.queryForObject(
            "SELECT coupon_status FROM user_coupon WHERE id=?", String.class, coupon));
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM payment_order WHERE checkout_batch_id=?", Integer.class, batch));
    assertEquals(
        confirmed.endDate().plusDays(1),
        f.db
            .queryForObject(
                "SELECT occupy_end_date FROM inventory_reservation WHERE checkout_batch_id=?",
                java.sql.Date.class,
                batch)
            .toLocalDate());
    LocalDateTime expiry =
        f.db
            .queryForObject(
                "SELECT hold_expire_at FROM checkout_batch WHERE id=?",
                java.sql.Timestamp.class,
                batch)
            .toLocalDateTime();
    f.clock.at = f.clock.at.plusSeconds(601);
    assertEquals("replayed", transaction.submit(command, confirmed, account).outcome());
    assertEquals(
        expiry,
        f.db
            .queryForObject(
                "SELECT hold_expire_at FROM checkout_batch WHERE id=?",
                java.sql.Timestamp.class,
                batch)
            .toLocalDateTime());
    assertEquals(
        "held",
        f.db.queryForObject(
            "SELECT reservation_status FROM inventory_reservation WHERE checkout_batch_id=?",
            String.class,
            batch));
    call(
        "GET",
        "/api/me/trading-operations/by-request/" + command.requestId(),
        null,
        buyer,
        null,
        200);
    assertThrows(
        ApiFailure.class,
        () ->
            transaction.submit(
                new com.mujian.backend.order.dto.TradingCommand(
                    buyer.id(), command.requestId(), command.operation(), "b".repeat(64)),
                confirmed,
                account));
    com.mujian.backend.payment.mapper.PaymentOrderMapper payments =
        f.app.getBean(com.mujian.backend.payment.mapper.PaymentOrderMapper.class);
    com.mujian.backend.payment.domain.PaymentOrderDO payment =
        payments.selectPaymentByBatch(batch, false);
    com.mujian.backend.payment.domain.ChannelSuborderDO channelSuborderDO =
        payments.selectSubordersByPayment(payment.id(), false).get(0);
    java.time.Instant actualPaid = expiry.minusSeconds(1).atZone(ZONE).toInstant();
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact fact =
        new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact(
            account.appId(),
            account.mchId(),
            payment.id(),
            List.of(
                new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.SuborderFact(
                    account.mchId(),
                    channelSuborderDO.subMchidSnapshot(),
                    channelSuborderDO.id(),
                    "wx-" + Ids.next(),
                    "SUCCESS",
                    channelSuborderDO.channelSuborderAmountFen(),
                    "CNY",
                    channelSuborderDO.channelSuborderAmountFen(),
                    "CNY",
                    actualPaid)),
            false);
    com.mujian.backend.payment.service.PaymentWorkTransaction work =
        f.app.getBean(com.mujian.backend.payment.service.PaymentWorkTransaction.class);
    assertTrue(work.observe(batch, fact, true).allPaid());
    D2ApplicationTest.Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    fault.table = "rental_order_delivery";
    try {
      assertThrows(RuntimeException.class, () -> f.app.getBean(OrderFormation.class).form(batch));
    } finally {
      fault.table = null;
    }
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM rental_order WHERE checkout_batch_id=?", Integer.class, batch));
    assertEquals("success", payments.selectPaymentByBatch(batch, false).paymentStatus());
    assertEquals(
        "held",
        f.db.queryForObject(
            "SELECT reservation_status FROM inventory_reservation WHERE checkout_batch_id=?",
            String.class,
            batch));
    List<String> orders = f.app.getBean(OrderFormation.class).form(batch);
    assertEquals(1, orders.size());
    assertEquals(orders, f.app.getBean(OrderFormation.class).form(batch));
    assertEquals(
        "used",
        f.db.queryForObject(
            "SELECT coupon_status FROM user_coupon WHERE id=?", String.class, coupon));
    assertEquals(
        "paid_locked",
        f.db.queryForObject(
            "SELECT reservation_status FROM inventory_reservation WHERE checkout_batch_id=?",
            String.class,
            batch));
    assertEquals(
        now(f.clock).plusHours(2),
        f.db
            .queryForObject(
                "SELECT accept_deadline_at FROM rental_order WHERE id=?",
                java.sql.Timestamp.class,
                orders.get(0))
            .toLocalDateTime());
    assertEquals(
        actualPaid,
        f.db
            .queryForObject(
                "SELECT paid_at FROM rental_order WHERE id=?",
                java.sql.Timestamp.class,
                orders.get(0))
            .toLocalDateTime()
            .atZone(ZONE)
            .toInstant());
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM coupon_reward_record WHERE user_id=? AND"
                + " event_type='first_rental_paid'",
            Integer.class,
            buyer.id()));
    assertEquals(
        2,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM order_transport_leg WHERE rental_order_id=?",
            Integer.class,
            orders.get(0)));
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact lateUnpaid =
        new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact(
            account.appId(),
            account.mchId(),
            payment.id(),
            List.of(
                new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.SuborderFact(
                    account.mchId(),
                    channelSuborderDO.subMchidSnapshot(),
                    channelSuborderDO.id(),
                    null,
                    "NOTPAY",
                    channelSuborderDO.channelSuborderAmountFen(),
                    "CNY",
                    null,
                    null,
                    null)),
            false);
    work.observe(batch, lateUnpaid, true);
    assertEquals("success", payments.selectPaymentByBatch(batch, false).paymentStatus());
  }

  /** 未成单迟付整批退款：失败不放额度，原号查询重试，重复成功不多退或多返券。 */
  @Test
  void lateCashRefundRecoveryKeepsOriginalObligationAndReturnsCouponOnce() throws Exception {
    input.put("couponId", coupon);
    String token =
        call("POST", "/api/me/checkout-quotes", input, buyer, null, 200)
            .path("quoteToken")
            .asString();
    CheckoutQuoteData confirmed = f.app.getBean(TradingTokens.class).readQuote(buyer.id(), token);
    com.mujian.backend.payment.dto.PaymentAccount account =
        new com.mujian.backend.payment.dto.PaymentAccount(
            "test-app",
            "test-platform",
            "https://example.invalid/api/wechat-pay/notifications/payment");
    String batch =
        f.app
            .getBean(CheckoutSubmissionTransaction.class)
            .submit(
                new com.mujian.backend.order.dto.TradingCommand(
                    buyer.id(),
                    key(),
                    com.mujian.backend.common.enums.TradingOperation.submitCheckout,
                    "d".repeat(64)),
                confirmed,
                account)
            .resourceId();
    com.mujian.backend.payment.mapper.PaymentOrderMapper payments =
        f.app.getBean(com.mujian.backend.payment.mapper.PaymentOrderMapper.class);
    com.mujian.backend.payment.domain.PaymentOrderDO payment =
        payments.selectPaymentByBatch(batch, false);
    com.mujian.backend.payment.domain.ChannelSuborderDO channelSuborderDO =
        payments.selectSubordersByPayment(payment.id(), false).get(0);
    f.clock.at = f.clock.at.plusSeconds(601);
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact fact =
        new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact(
            account.appId(),
            account.mchId(),
            payment.id(),
            List.of(
                new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.SuborderFact(
                    account.mchId(),
                    channelSuborderDO.subMchidSnapshot(),
                    channelSuborderDO.id(),
                    "wx-late-" + Ids.next(),
                    "SUCCESS",
                    channelSuborderDO.channelSuborderAmountFen(),
                    "CNY",
                    channelSuborderDO.channelSuborderAmountFen(),
                    "CNY",
                    f.clock.instant())),
            false);
    assertTrue(
        f.app
            .getBean(com.mujian.backend.payment.service.PaymentWorkTransaction.class)
            .observe(batch, fact, true)
            .refundBatch());
    assertTrue(f.app.getBean(OrderFormation.class).form(batch).isEmpty());
    com.mujian.backend.payment.service.UnformedBatchRefunds unformed =
        f.app.getBean(com.mujian.backend.payment.service.UnformedBatchRefunds.class);
    String refundId = unformed.reconcile(batch).get(0);
    assertEquals(refundId, unformed.reconcile(batch).get(0));
    assertEquals(
        "held",
        f.db.queryForObject(
            "SELECT reservation_status FROM inventory_reservation WHERE checkout_batch_id=?",
            String.class,
            batch));
    com.mujian.backend.payment.mapper.RefundMapper refunds =
        f.app.getBean(com.mujian.backend.payment.mapper.RefundMapper.class);
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway gateway =
        org.mockito.Mockito.mock(
            com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.class);
    com.mujian.backend.payment.service.RefundWorkTransaction work =
        f.app.getBean(com.mujian.backend.payment.service.RefundWorkTransaction.class);
    com.mujian.backend.payment.service.RefundExecution execution =
        new com.mujian.backend.payment.service.RefundExecution(work, gateway, unformed);
    org.mockito.Mockito.when(gateway.refund(org.mockito.ArgumentMatchers.any()))
        .thenThrow(unavailable("PAYMENT_CHANNEL_UNAVAILABLE"));
    execution.execute(refundId);
    assertEquals("pending", refunds.selectRefundById(refundId, false).refundStatus());
    assertEquals(
        channelSuborderDO.channelSuborderAmountFen(),
        refunds.selectRefundExposureBySuborder(channelSuborderDO.id()).totalFen());
    assertEquals(
        "locked",
        f.db.queryForObject(
            "SELECT coupon_status FROM user_coupon WHERE id=?", String.class, coupon));
    f.clock.at = f.clock.at.plusSeconds(11);
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.RefundFact success =
        new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.RefundFact(
            account.mchId(),
            channelSuborderDO.subMchidSnapshot(),
            channelSuborderDO.id(),
            fact.suborders().get(0).transactionId(),
            refundId,
            "wx-refund-" + refundId,
            "SUCCESS",
            channelSuborderDO.channelSuborderAmountFen(),
            channelSuborderDO.channelSuborderAmountFen(),
            null,
            f.clock.instant(),
            false);
    org.mockito.Mockito.when(gateway.queryRefund(org.mockito.ArgumentMatchers.any()))
        .thenReturn(success);
    execution.execute(refundId);
    work.observe(refundId, success);
    unformed.reconcile(batch);
    execution.execute(refundId);
    assertEquals("success", refunds.selectRefundById(refundId, false).refundStatus());
    assertEquals(
        channelSuborderDO.channelSuborderAmountFen(),
        payments.selectPaymentByBatch(batch, false).refundedAmountFen());
    assertEquals(
        "refunded_closed",
        f.db.queryForObject(
            "SELECT batch_status FROM checkout_batch WHERE id=?", String.class, batch));
    assertEquals(
        "released",
        f.db.queryForObject(
            "SELECT reservation_status FROM inventory_reservation WHERE checkout_batch_id=?",
            String.class,
            batch));
    assertEquals(
        "selecting",
        f.db.queryForObject(
            "SELECT checklist_status FROM rental_checklist WHERE id=?", String.class, checklist));
    assertEquals(
        "returned",
        f.db.queryForObject(
            "SELECT coupon_status FROM user_coupon WHERE id=?", String.class, coupon));
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM coupon_operation_log WHERE user_coupon_id=? AND"
                + " operation_type='returned'",
            Integer.class,
            coupon));
    org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(1))
        .refund(
            org.mockito.ArgumentMatchers.argThat(request -> request.refundNo().equals(refundId)));
    org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(1))
        .queryRefund(
            org.mockito.ArgumentMatchers.argThat(request -> request.refundNo().equals(refundId)));
  }

  /** 关单意图不能抢走正在执行的创建租约，迟到prepay只补原号且不能重新开放付款。 */
  @Test
  void closeIntentRespectsCreateLeaseAndPreservesOriginalDeadline() throws Exception {
    String token =
        call("POST", "/api/me/checkout-quotes", input, buyer, null, 200)
            .path("quoteToken")
            .asString();
    com.mujian.backend.order.domain.CheckoutQuoteData confirmed =
        f.app.getBean(TradingTokens.class).readQuote(buyer.id(), token);
    String batch =
        f.app
            .getBean(CheckoutSubmissionTransaction.class)
            .submit(
                new com.mujian.backend.order.dto.TradingCommand(
                    buyer.id(),
                    key(),
                    com.mujian.backend.common.enums.TradingOperation.submitCheckout,
                    "e".repeat(64)),
                confirmed,
                new com.mujian.backend.payment.dto.PaymentAccount(
                    "test-app",
                    "test-platform",
                    "https://example.invalid/api/wechat-pay/notifications/payment"))
            .resourceId();
    com.mujian.backend.payment.service.PaymentWorkTransaction work =
        f.app.getBean(com.mujian.backend.payment.service.PaymentWorkTransaction.class);
    com.mujian.backend.payment.service.PaymentWorkTransaction.Ticket ticket = work.claim(batch);
    assertNotNull(ticket);
    work.closeIntent(batch, "user_cancel");
    assertNull(work.claim(batch));
    work.finish(ticket, "original-prepay", null);
    assertNull(work.claim(batch));
    com.mujian.backend.payment.domain.PaymentOrderDO payment =
        f.app
            .getBean(com.mujian.backend.payment.mapper.PaymentOrderMapper.class)
            .selectPaymentByBatch(batch, false);
    assertEquals("original-prepay", payment.channelPrepayId());
    assertEquals(ticket.payment().expireAt(), payment.expireAt());
    assertEquals("user_cancel", payment.closeReason());
    f.clock.at = f.clock.at.plusSeconds(31);
    assertNotNull(work.claim(batch));
  }

  /** 正式提交/调起/取消HTTP与恢复共用原批次，渠道缺失不创建交易事实。 */
  @Test
  void checkoutHttpUsesOriginalBatchAndTruthfulPaymentState() throws Exception {
    String token =
        call("POST", "/api/me/checkout-quotes", input, buyer, null, 200)
            .path("quoteToken")
            .asString();
    String submitKey = key();
    call("POST", "/api/me/checkout-batches", view("quoteToken", token), buyer, submitKey, 503);
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM checkout_batch WHERE buyer_user_id=?",
            Integer.class,
            buyer.id()));
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway gateway =
        f.app.getBean(com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.class);
    com.mujian.backend.payment.dto.PaymentAccount account =
        new com.mujian.backend.payment.dto.PaymentAccount(
            "test-app",
            "test-platform",
            "https://example.invalid/api/wechat-pay/notifications/payment");
    org.mockito.Mockito.doReturn(account).when(gateway).requireReady();
    org.mockito.Mockito.doReturn("test-prepay")
        .when(gateway)
        .create(org.mockito.ArgumentMatchers.any());
    org.mockito.Mockito.doReturn(
            new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.Parameters(
                "test-app",
                "1234567890",
                "testnonce",
                "prepay_id=test-prepay",
                "RSA",
                "synthetic-test-signature"))
        .when(gateway)
        .parameters(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    tools.jackson.databind.JsonNode submitted =
        call("POST", "/api/me/checkout-batches", view("quoteToken", token), buyer, submitKey, 200);
    String batch = submitted.path("batch").path("id").asString();
    assertEquals("pay", submitted.path("batch").path("nextAction").asString());
    assertEquals("paying", submitted.path("batch").path("payment").path("status").asString());
    call("GET", "/api/me/checkout-batches/" + batch, null, f.user(), null, 404);
    String launchKey = key();
    tools.jackson.databind.JsonNode current =
        call("GET", "/api/me/checkout-batches/" + batch, null, buyer, null, 200);
    call(
        "POST",
        "/api/me/checkout-batches/" + batch + "/pay",
        view("expectedVersion", current.path("version").asInt()),
        buyer,
        launchKey,
        200);
    call(
        "POST",
        "/api/me/checkout-batches/" + batch + "/pay",
        view("expectedVersion", current.path("version").asInt()),
        buyer,
        launchKey,
        200);
    current = call("GET", "/api/me/checkout-batches/" + batch, null, buyer, null, 200);
    call(
        "POST",
        "/api/me/checkout-batches/" + batch + "/cancel",
        view("expectedVersion", current.path("version").asInt()),
        buyer,
        key(),
        200);
    current = call("GET", "/api/me/checkout-batches/" + batch, null, buyer, null, 200);
    assertFalse(current.path("canContinuePay").asBoolean());
    assertEquals("wait", current.path("nextAction").asString());
    assertEquals(
        "held",
        f.db.queryForObject(
            "SELECT reservation_status FROM inventory_reservation WHERE checkout_batch_id=?",
            String.class,
            batch));
    f.clock.at = f.clock.at.plusSeconds(31);
    org.mockito.Mockito.doAnswer(
            invocation -> {
              com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.Reference reference =
                  (com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.Reference)
                      invocation.getArgument(0);
              return new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway
                  .PaymentFact(
                  account.appId(),
                  account.mchId(),
                  reference.outTradeNo(),
                  reference.suborders().stream()
                      .map(
                          sub ->
                              new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway
                                  .SuborderFact(
                                  account.mchId(),
                                  sub.subMchid(),
                                  sub.outTradeNo(),
                                  null,
                                  "CLOSED",
                                  sub.totalFen(),
                                  "CNY",
                                  null,
                                  null,
                                  null))
                      .toList(),
                  false);
            })
        .when(gateway)
        .query(org.mockito.ArgumentMatchers.any());
    f.app.getBean(com.mujian.backend.payment.service.PaymentExecution.class).execute(batch);
    current = call("GET", "/api/me/checkout-batches/" + batch, null, buyer, null, 200);
    assertEquals("closed", current.path("status").asString());
    assertEquals("return_checklist", current.path("nextAction").asString());
    f.clock.at = f.clock.at.plusSeconds(301);
    tools.jackson.databind.JsonNode replayed =
        call("POST", "/api/me/checkout-batches", view("quoteToken", token), buyer, submitKey, 200);
    assertEquals("replayed", replayed.path("receipt").path("outcome").asString());
    assertTrue(replayed.path("parameters").isNull());
    org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(1))
        .create(org.mockito.ArgumentMatchers.any());
  }

  /** 准备可用于运输费用的资金来源。 */
  String prepareFreightPool() {
    String vehicle = Ids.next(), rule = Ids.next();
    f.cleanupSeeds.put("transport_vehicle_tier", Set.of(vehicle));
    f.db.update(
        "INSERT INTO"
            + " transport_vehicle_tier(id,tier_code,tier_name,cargo_length_mm,cargo_width_mm,cargo_height_mm,effective_volume_cm3,safe_payload_gram,sort_no,tier_status,created_at,updated_at)"
            + " VALUES (?,?,'隔离车型',9000,9000,9000,999999999999,999999999999,0,'enabled',?,?)",
        vehicle,
        "test_" + vehicle.substring(0, 10),
        now(f.clock),
        now(f.clock));
    f.db.update(
        "INSERT INTO"
            + " transport_pricing_rule(id,rule_version,city_code,vehicle_tier_id,distance_min_meter,included_distance_meter,guide_min_amount_fen,guide_max_amount_fen,own_vehicle_settle_amount_fen,extra_km_amount_fen,allowed_extra_fee_json,rule_status,effective_at,created_at,updated_at)"
            + " VALUES (?,?,'310100',?,0,10000,4000,5000,3500,300,'[]','active',?,?,?)",
        rule,
        rule,
        vehicle,
        now(f.clock).minusMinutes(1),
        now(f.clock),
        now(f.clock));
    input.put(
        "deliveries",
        List.of(
            view(
                "factoryId",
                factoryId,
                "outboundService",
                "factory_delivery",
                "returnService",
                "factory_pickup",
                "buyerRemark",
                null)));
    return rule;
  }

  /** 用与数据库相同的毫秒精度检查返还券的列表和统计在截止点同步切换。 */
  @Test
  void walletExpiryUsesSameInstantForRowsAndCounts() throws Exception {
    Instant boundary = f.clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS).plusSeconds(1);
    f.db.update("UPDATE user_coupon SET coupon_status='returned', valid_until=? WHERE id=?",
        LocalDateTime.ofInstant(boundary, ZONE), coupon);
    for (boolean expired : List.of(false, true)) {
      f.clock.at = expired ? boundary : boundary.minusMillis(1);
      JsonNode wallet = call("GET", "/api/me/coupons?status=available", null, buyer, null, 200);
      assertEquals(expired ? 0 : 1, wallet.path("items").size());
      assertEquals(expired ? "0" : "1", wallet.path("availableCount").asString());
      assertEquals(expired ? "1" : "0", wallet.path("unavailableCount").asString());
    }
  }

  /** 对照一张和两张订单的实际争议查询次数。 */
  @Test
  void disputeSqlOnceForOneAndTwoRentalCards() throws Exception {
    paidOrder();
    D3BQuoteTest other = new D3BQuoteTest();
    try {
      other.start();
      String otherId = other.paidOrder();
      try {
        // 两笔均为本测试新建的合成订单，只改变第二笔的列表归属。
        f.db.update("UPDATE rental_order SET buyer_user_id=? WHERE id=?", buyer.id(), otherId);
        for (int size : List.of(1, 2)) {
          int before = f.app.getBean(D2ApplicationTest.Fault.class).disputeFactReads.get();
          JsonNode result = call("GET", "/api/me/rental-orders?pageSize=" + size, null, buyer, null, 200);
          assertEquals(size, result.path("items").size());
          assertEquals(1, f.app.getBean(D2ApplicationTest.Fault.class).disputeFactReads.get() - before);
        }
      } finally {
        f.db.update("UPDATE rental_order SET buyer_user_id=? WHERE id=?", other.buyer.id(), otherId);
      }
    } finally {
      other.stop();
    }
  }

  /** 建立已付款订单。 */
  String paidOrder() throws Exception {
    return paidOrder("test-platform");
  }

  /** 使用签名夹具的商户号创建原支付来源。 */
  String paidOrder(String platformMerchant) throws Exception {
    input.put("couponId", coupon);
    String token =
        call("POST", "/api/me/checkout-quotes", input, buyer, null, 200)
            .path("quoteToken")
            .asString();
    com.mujian.backend.order.domain.CheckoutQuoteData confirmed =
        f.app.getBean(TradingTokens.class).readQuote(buyer.id(), token);
    com.mujian.backend.payment.dto.PaymentAccount account =
        new com.mujian.backend.payment.dto.PaymentAccount(
            "test-app",
            platformMerchant,
            "https://example.invalid/api/wechat-pay/notifications/payment");
    String batch =
        f.app
            .getBean(CheckoutSubmissionTransaction.class)
            .submit(
                new com.mujian.backend.order.dto.TradingCommand(
                    buyer.id(),
                    key(),
                    com.mujian.backend.common.enums.TradingOperation.submitCheckout,
                    "f".repeat(64)),
                confirmed,
                account)
            .resourceId();
    com.mujian.backend.payment.mapper.PaymentOrderMapper payments =
        f.app.getBean(com.mujian.backend.payment.mapper.PaymentOrderMapper.class);
    com.mujian.backend.payment.domain.PaymentOrderDO payment =
        payments.selectPaymentByBatch(batch, false);
    com.mujian.backend.payment.domain.ChannelSuborderDO channelSuborderDO =
        payments.selectSubordersByPayment(payment.id(), false).get(0);
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact fact =
        new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact(
            account.appId(),
            account.mchId(),
            payment.id(),
            List.of(
                new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.SuborderFact(
                    account.mchId(),
                    channelSuborderDO.subMchidSnapshot(),
                    channelSuborderDO.id(),
                    "wx-paid-" + Ids.next(),
                    "SUCCESS",
                    channelSuborderDO.channelSuborderAmountFen(),
                    "CNY",
                    channelSuborderDO.channelSuborderAmountFen(),
                    "CNY",
                    f.clock.instant())),
            false);
    f.app
        .getBean(com.mujian.backend.payment.service.PaymentWorkTransaction.class)
        .observe(batch, fact, true);
    return f.app.getBean(OrderFormation.class).form(batch).get(0);
  }

  /** 检查接单后取消跨时间边界重算及退款扣款。 */
  @Test
  void acceptedCancellationRequotesAtBoundaryAndKeepsDeductionOnRefund() throws Exception {
    String order = paidOrder();
    com.mujian.backend.order.mapper.RentalOrderMapper orders =
        f.app.getBean(com.mujian.backend.order.mapper.RentalOrderMapper.class);
    String acceptKey = key();
    tools.jackson.databind.JsonNode orderDetail =
        call("GET", "/api/me/rental-orders/" + order, null, buyer, null, 200);
    assertEquals(order, orderDetail.path("id").asString());
    assertEquals(1, orderDetail.path("items").size());
    assertFalse(orderDetail.path("supplyWarning").asBoolean());
    call("GET", "/api/me/factory-orders/" + order, null, supplier, null, 200);
    call("GET", "/api/me/rental-orders/" + order, null, f.user(), null, 404);
    assertEquals(
        "1", call("GET", "/api/me/rental-orders", null, buyer, null, 200).path("total").asString());
    call("GET", "/api/me/factory-orders?view=pending_accept&pageSize=1", null, supplier, null, 200);
    assertEquals(
        "1",
        call("GET", "/api/me/factory-orders/overview", null, supplier, null, 200)
            .path("pendingAcceptCount")
            .asString());

    call(
        "POST",
        "/api/me/factory-orders/" + order + "/accept",
        view("expectedVersion", 0),
        supplier,
        acceptKey,
        200);
    call(
        "POST",
        "/api/me/factory-orders/" + order + "/accept",
        view("expectedVersion", 0),
        supplier,
        acceptKey,
        200);
    com.mujian.backend.order.domain.RentalOrderDO original = orders.selectOrderById(order);
    f.clock.at =
        original
            .rentalStartDate()
            .atStartOfDay()
            .minusHours(24)
            .minusSeconds(1)
            .atZone(ZONE)
            .toInstant();
    tools.jackson.databind.JsonNode quote =
        call(
            "POST",
            "/api/me/rental-orders/" + order + "/cancel-quote",
            view("expectedVersion", 1),
            buyer,
            null,
            200);
    assertEquals(3000, quote.path("rateBps").asInt());
    f.clock.at = f.clock.at.plusSeconds(2);
    call(
        "POST",
        "/api/me/rental-orders/" + order + "/cancel",
        view("confirmToken", quote.path("confirmToken").asString(), "reason", "隔离取消"),
        buyer,
        key(),
        409);
    assertEquals("CANCELLATION_QUOTE_CHANGED", f.lastReason);
    quote =
        call(
            "POST",
            "/api/me/rental-orders/" + order + "/cancel-quote",
            view("expectedVersion", 1),
            buyer,
            null,
            200);
    assertEquals(6000, quote.path("rateBps").asInt());
    int fee =
        (int)
            Math.min(
                original.rentPaidAmountFen(),
                (long) original.rentOriginalAmountFen() * 6000 / 10000);
    assertEquals(fee, quote.path("deductionFen").asInt());
    String cancelKey = key();
    java.util.Map<java.lang.String, java.lang.Object> body =
        view("confirmToken", quote.path("confirmToken").asString(), "reason", "隔离取消");
    D2ApplicationTest.Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    fault.table = "order_deduction";
    try {
      call("POST", "/api/me/rental-orders/" + order + "/cancel", body, buyer, cancelKey, 500);
    } finally {
      fault.table = null;
    }
    assertEquals("accepted", orders.selectOrderById(order).orderStatus());
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM refund_order WHERE rental_order_id=?", Integer.class, order));
    call("POST", "/api/me/rental-orders/" + order + "/cancel", body, buyer, cancelKey, 200);
    f.clock.at = f.clock.at.plusSeconds(301);
    call("POST", "/api/me/rental-orders/" + order + "/cancel", body, buyer, cancelKey, 200);
    assertEquals("user_cancelled", orders.selectOrderById(order).orderStatus());
    assertEquals("processing", orders.selectOrderById(order).refundStatus());
    assertEquals(fee, orders.selectOrderById(order).rentDeductedAmountFen());
    JsonNode deduction =
        f.json.readTree(
            f.db.queryForObject(
                "SELECT calculation_snapshot_json FROM order_deduction WHERE rental_order_id=?",
                String.class,
                order));
    assertEquals(
        original.rentalStartDate().atStartOfDay().toString(),
        java.time.LocalDateTime.parse(deduction.path("rentalStartAt").asString()).toString());
    assertEquals(original.rentOriginalAmountFen(), deduction.path("originalRentFen").asInt());
    assertEquals(original.rentPaidAmountFen(), deduction.path("cashRentFen").asInt());
    assertEquals(86399, deduction.path("secondsUntilStart").asLong());
    assertEquals(0, deduction.path("originalRefundInFlight").path("rentFen").asInt());
    assertEquals(0, deduction.path("originalRefunded").path("rentFen").asInt());
    JsonNode detail = call("GET", "/api/me/rental-orders/" + order, null, buyer, null, 200);
    String documentId = detail.path("cancellationRule").path("documentId").asString();
    JsonNode document = call("GET", "/api/public/rules/" + documentId, null, buyer, null, 200);
    assertEquals(
        document.path("version").asString(),
        detail.path("cancellationRule").path("version").asString());
    assertEquals(document.path("version").asString(), deduction.path("ruleVersion").asString());
    assertTrue(document.path("contentText").asString().contains("48小时"));
    assertEquals(
        5,
        f.app
            .getBean(com.mujian.backend.account.service.RuleAcceptanceService.class)
            .current()
            .size());

    assertEquals(
        "released",
        f.db.queryForObject(
            "SELECT reservation_status FROM inventory_reservation WHERE rental_order_id=?",
            String.class,
            order));
    String refundId =
        f.db.queryForObject(
            "SELECT id FROM refund_order WHERE rental_order_id=?", String.class, order);
    com.mujian.backend.payment.service.RefundWorkTransaction work =
        f.app.getBean(com.mujian.backend.payment.service.RefundWorkTransaction.class);
    com.mujian.backend.payment.service.RefundWorkTransaction.Ticket ticket = work.claim(refundId);
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.RefundRequest request =
        ticket.request();
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.RefundFact success =
        new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.RefundFact(
            request.account().mchId(),
            request.subMchid(),
            request.outTradeNo(),
            request.transactionId(),
            request.refundNo(),
            "wx-refund-test",
            "SUCCESS",
            request.totalFen(),
            request.refundFen(),
            "CNY",
            f.clock.instant(),
            false);
    work.observe(refundId, success);
    work.observe(refundId, success);
    assertEquals(
        original.totalPaidAmountFen() - fee,
        orders.selectOrderById(order).totalRefundedAmountFen());
    assertEquals("partial_refunded", orders.selectOrderById(order).refundStatus());
    assertEquals(
        "used",
        f.db.queryForObject(
            "SELECT coupon_status FROM user_coupon WHERE id=?", String.class, coupon));
    call("GET", "/api/me/refunds/" + refundId, null, buyer, null, 200);
    call("GET", "/api/me/refunds/" + refundId, null, f.user(), null, 404);
    call("GET", "/api/me/refunds/" + refundId, null, supplier, null, 403);
    f.adminCookie = "mj_admin_dev=" + f.sessions.create(f.admin, "admin").session().getId();
    assertEquals(
        "1",
        call("GET", "/api/admin/rental-orders?factoryId=" + factoryId, null, null, null, 200)
            .path("total")
            .asString());
    call("GET", "/api/admin/rental-orders/" + order, null, null, null, 200);
    assertEquals(
        "1",
        call("GET", "/api/admin/payments?factoryId=" + factoryId, null, null, null, 200)
            .path("total")
            .asString());
    tools.jackson.databind.JsonNode trace =
        call("GET", "/api/admin/payments/" + original.paymentOrderId(), null, null, null, 200);
    assertEquals(
        original.rentOriginalAmountFen(),
        trace.path("batch").path("amounts").path("rentOriginalFen").asInt());
    assertEquals(
        original.rentPaidAmountFen(),
        trace.path("batch").path("amounts").path("rentCashFen").asInt());
    assertEquals(
        "1",
        call("GET", "/api/admin/refunds?factoryId=" + factoryId, null, null, null, 200)
            .path("total")
            .asString());
    call("GET", "/api/admin/refunds/" + refundId, null, null, null, 200);
    assertEquals(
        3,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM admin_operation_audit WHERE admin_user_id=? AND"
                + " operation_code='trade_detail_read' AND target_id IN (?,?,?)",
            Integer.class,
            f.admin,
            order,
            original.paymentOrderId(),
            refundId));
  }

  /** 验证超时和人工拒绝互斥且记录系统来源。 */
  @Test
  void timeoutAndManualRejectionRemainExclusiveAndSystemAttributed() throws Exception {
    String order = paidOrder();
    com.mujian.backend.order.mapper.RentalOrderMapper orders =
        f.app.getBean(com.mujian.backend.order.mapper.RentalOrderMapper.class);
    com.mujian.backend.order.domain.RentalOrderDO original = orders.selectOrderById(order);
    f.clock.at = original.acceptDeadlineAt().atZone(ZONE).toInstant();
    call(
        "POST",
        "/api/me/factory-orders/" + order + "/accept",
        view("expectedVersion", 0),
        supplier,
        key(),
        409);
    assertEquals("ACCEPT_DEADLINE_PASSED", f.lastReason);
    com.mujian.backend.order.service.RentalOrderCommands rentalOrderCommands =
        f.app.getBean(RentalOrderCommands.class);
    rentalOrderCommands.timeout(order);
    rentalOrderCommands.timeout(order);
    assertEquals("timeout_rejected", orders.selectOrderById(order).orderStatus());
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM refund_order WHERE rental_order_id=?", Integer.class, order));
    assertEquals(
        com.mujian.backend.common.support.SystemIdentity.SYSTEM_USER_ID,
        f.db.queryForObject(
            "SELECT requested_by FROM refund_order WHERE rental_order_id=?", String.class, order));
    call(
        "POST",
        "/api/me/factory-orders/" + order + "/reject",
        view("expectedVersion", 1, "reason", "迟到拒单"),
        supplier,
        key(),
        409);
    assertEquals("ORDER_STATE_CHANGED", f.lastReason);
  }

  /** 商户查询只核对可信原编号，开户完成不能自动开启分账；同步有回执和权限。 */
  User secondFactory() throws Exception {
    User owner = fixture.supplier();
    JsonNode made = fixture.create(owner, "B_CONSUMABLE", 6);
    String otherSku = made.path("skuId").asString();
    fixture.approve("sku_audit", made.path("targetId").asString(), 0);
    String factory =
        f.db.queryForObject("SELECT factory_id FROM prop_sku WHERE id=?", String.class, otherSku);
    f.db.update(
        "INSERT INTO"
            + " factory_wechat_merchant_account(id,factory_id,channel_provider,sub_mchid,subject_type,subject_name_snapshot,account_status,profit_sharing_status,created_at,updated_at)"
            + " VALUES"
            + " (?,?,'wechat_platform_collection',?,'SUBJECT_TYPE_ENTERPRISE','隔离第二工厂','active','active',?,?)",
        Ids.next(),
        factory,
        "test_" + Ids.next(),
        now(f.clock),
        now(f.clock));
    call(
        "POST",
        "/api/me/checklists/" + checklist + "/items",
        view("expectedVersion", 1, "skuId", otherSku, "quantity", 1, "remark", null),
        buyer,
        key(),
        200);
    input.put("expectedVersion", 2);
    input.put(
        "deliveries",
        List.of(
            view(
                "factoryId",
                factoryId,
                "outboundService",
                "self_pickup",
                "returnService",
                "user_return",
                "buyerRemark",
                null),
            view(
                "factoryId",
                factory,
                "outboundService",
                "self_pickup",
                "returnService",
                "user_return",
                "buyerRemark",
                null)));
    return owner;
  }

  /** 使用已确认报价提交订单。 */
  String submitQuoted(User actor, Map<String, Object> body) throws Exception {
    String token =
        call("POST", "/api/me/checkout-quotes", body, actor, null, 200)
            .path("quoteToken")
            .asString();
    CheckoutQuoteData quote = f.app.getBean(TradingTokens.class).readQuote(actor.id(), token);
    return f.app
        .getBean(CheckoutSubmissionTransaction.class)
        .submit(
            new com.mujian.backend.order.dto.TradingCommand(
                actor.id(),
                key(),
                com.mujian.backend.common.enums.TradingOperation.submitCheckout,
                "b".repeat(64)),
            quote,
            new com.mujian.backend.payment.dto.PaymentAccount(
                "test-app",
                "test-platform",
                "https://example.invalid/api/wechat-pay/notifications/payment"))
        .resourceId();
  }

  /** 读取场景相关现金金额。 */
  com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.SuborderFact cash(
      com.mujian.backend.payment.domain.ChannelSuborderDO channelSuborderDO, String state) {
    boolean paid = "SUCCESS".equals(state);
    return new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.SuborderFact(
        "test-platform",
        channelSuborderDO.subMchidSnapshot(),
        channelSuborderDO.channelSubOrderNo(),
        paid ? "wx-" + channelSuborderDO.id() : null,
        state,
        channelSuborderDO.channelSuborderAmountFen(),
        "CNY",
        paid ? channelSuborderDO.channelSuborderAmountFen() : null,
        paid ? "CNY" : null,
        paid ? f.clock.instant() : null);
  }

  /** 推进退款至完成并核对结果。 */
  void completeRefund(String refundId) {
    com.mujian.backend.payment.mapper.RefundMapper refundMapper =
        f.app.getBean(com.mujian.backend.payment.mapper.RefundMapper.class);
    com.mujian.backend.payment.domain.RefundOrderDO refund =
        refundMapper.selectRefundById(refundId, false);
    com.mujian.backend.payment.mapper.PaymentOrderMapper paymentMapper =
        f.app.getBean(com.mujian.backend.payment.mapper.PaymentOrderMapper.class);
    com.mujian.backend.payment.domain.ChannelSuborderDO channelSuborderDO =
        paymentMapper.selectSubordersByPayment(refund.paymentOrderId(), false).stream()
            .filter(item -> item.id().equals(refund.paymentChannelSuborderId()))
            .findFirst()
            .orElseThrow();
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.RefundFact fact =
        new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.RefundFact(
            "test-platform",
            channelSuborderDO.subMchidSnapshot(),
            channelSuborderDO.channelSubOrderNo(),
            refund.channelTransactionId(),
            refund.channelOutRefundNo(),
            "wx-r-" + refundId,
            "SUCCESS",
            channelSuborderDO.paidAmountFen(),
            refund.refundAmountFen(),
            null,
            f.clock.instant(),
            false);
    f.app
        .getBean(com.mujian.backend.payment.service.RefundWorkTransaction.class)
        .observe(refundId, fact);
  }

  /** 验证部分回调等待完整查询且退款决定不重开订单。 */
  @Test
  void partialCallbackWaitsForCompleteQueryAndRefundDecisionNeverReopensOrders() throws Exception {
    secondFactory();
    input.put("couponId", coupon);
    String batch = submitQuoted(buyer, input);
    com.mujian.backend.payment.mapper.PaymentOrderMapper payments =
        f.app.getBean(com.mujian.backend.payment.mapper.PaymentOrderMapper.class);
    com.mujian.backend.payment.domain.PaymentOrderDO payment =
        payments.selectPaymentByBatch(batch, false);
    java.util.List<com.mujian.backend.payment.domain.ChannelSuborderDO> subs =
        payments.selectSubordersByPayment(payment.id(), false);
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.SuborderFact first =
        cash(subs.get(0), "SUCCESS");
    com.mujian.backend.payment.service.PaymentWorkTransaction work =
        f.app.getBean(com.mujian.backend.payment.service.PaymentWorkTransaction.class);
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact fragment =
        new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact(
            "test-app", "test-platform", payment.id(), List.of(first), false);
    assertFalse(work.observe(batch, fragment, false).refundBatch());
    assertTrue(f.app.getBean(OrderFormation.class).form(batch).isEmpty());
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact partial =
        new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact(
            "test-app",
            "test-platform",
            payment.id(),
            List.of(first, cash(subs.get(1), "NOTPAY")),
            false);
    assertTrue(work.observe(batch, partial, true).refundBatch());
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact allPaid =
        new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact(
            "test-app",
            "test-platform",
            payment.id(),
            List.of(first, cash(subs.get(1), "SUCCESS")),
            false);
    assertTrue(work.observe(batch, allPaid, true).refundBatch());
    assertTrue(f.app.getBean(OrderFormation.class).form(batch).isEmpty());
    com.mujian.backend.payment.service.UnformedBatchRefunds unformed =
        f.app.getBean(com.mujian.backend.payment.service.UnformedBatchRefunds.class);
    List<String> refunds = unformed.reconcile(batch);
    assertEquals(2, refunds.size());
    completeRefund(refunds.get(0));
    unformed.reconcile(batch);
    assertEquals(
        "locked",
        f.db.queryForObject(
            "SELECT coupon_status FROM user_coupon WHERE id=?", String.class, coupon));
    assertEquals(
        2,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM inventory_reservation WHERE checkout_batch_id=? AND"
                + " reservation_status='held'",
            Integer.class,
            batch));
    completeRefund(refunds.get(1));
    unformed.reconcile(batch);
    work.observe(batch, allPaid, true);
    unformed.reconcile(batch);
    assertEquals(
        "refunded_closed",
        f.db.queryForObject(
            "SELECT batch_status FROM checkout_batch WHERE id=?", String.class, batch));
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM rental_order WHERE checkout_batch_id=?", Integer.class, batch));
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM coupon_reward_record WHERE user_id=? AND"
                + " event_type='first_rental_paid'",
            Integer.class,
            buyer.id()));
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM coupon_operation_log WHERE user_coupon_id=? AND"
                + " operation_type='returned'",
            Integer.class,
            coupon));
  }

  /** 验证全部子单退款后原券按约定返还。 */
  @Test
  void noDiscountFactoryMustRefundBeforeOriginalCouponReturnsWithSevenDays() throws Exception {
    User second = secondFactory();
    String template =
        f.db.queryForObject("SELECT template_id FROM user_coupon WHERE id=?", String.class, coupon);
    f.db.update("UPDATE coupon_template SET applicable_scope_type='factory' WHERE id=?", template);
    f.db.update(
        "INSERT INTO coupon_scope_target(id,coupon_template_id,scope_type,target_id,created_at)"
            + " VALUES (?,?,'factory',?,?)",
        Ids.next(),
        template,
        factoryId,
        now(f.clock));
    f.db.update(
        "UPDATE user_coupon SET valid_until=? WHERE id=?", now(f.clock).plusDays(1), coupon);
    input.put("couponId", coupon);
    String batch = submitQuoted(buyer, input);
    com.mujian.backend.payment.mapper.PaymentOrderMapper payments =
        f.app.getBean(com.mujian.backend.payment.mapper.PaymentOrderMapper.class);
    com.mujian.backend.payment.domain.PaymentOrderDO payment =
        payments.selectPaymentByBatch(batch, false);
    java.util.List<com.mujian.backend.payment.domain.ChannelSuborderDO> subs =
        payments.selectSubordersByPayment(payment.id(), false);
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact allPaid =
        new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact(
            "test-app",
            "test-platform",
            payment.id(),
            subs.stream().map(item -> cash(item, "SUCCESS")).toList(),
            false);
    f.app
        .getBean(com.mujian.backend.payment.service.PaymentWorkTransaction.class)
        .observe(batch, allPaid, true);
    List<String> orders = f.app.getBean(OrderFormation.class).form(batch);
    assertEquals(2, orders.size());
    String
        discounted =
            f.db.queryForObject(
                "SELECT id FROM rental_order WHERE checkout_batch_id=? AND factory_id=?",
                String.class,
                batch,
                factoryId),
        other = orders.stream().filter(id -> !id.equals(discounted)).findFirst().orElseThrow();
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT coupon_discount_amount_fen FROM rental_order WHERE id=?",
            Integer.class,
            other));
    call(
        "POST",
        "/api/me/factory-orders/" + discounted + "/reject",
        view("expectedVersion", 0, "reason", "隔离拒单退款"),
        supplier,
        key(),
        200);
    JsonNode quote =
        call(
            "POST",
            "/api/me/rental-orders/" + other + "/cancel-quote",
            view("expectedVersion", 0),
            buyer,
            null,
            200);
    call(
        "POST",
        "/api/me/rental-orders/" + other + "/cancel",
        view("confirmToken", quote.path("confirmToken").asString(), "reason", "隔离待接单取消"),
        buyer,
        key(),
        200);
    String
        firstRefund =
            f.db.queryForObject(
                "SELECT id FROM refund_order WHERE rental_order_id=?", String.class, discounted),
        secondRefund =
            f.db.queryForObject(
                "SELECT id FROM refund_order WHERE rental_order_id=?", String.class, other);
    f.clock.at = f.clock.at.plusSeconds(2 * 86400);
    completeRefund(firstRefund);
    assertEquals(
        "used",
        f.db.queryForObject(
            "SELECT coupon_status FROM user_coupon WHERE id=?", String.class, coupon));
    completeRefund(secondRefund);
    completeRefund(firstRefund);
    completeRefund(secondRefund);
    assertEquals(
        "returned",
        f.db.queryForObject(
            "SELECT coupon_status FROM user_coupon WHERE id=?", String.class, coupon));
    assertEquals(
        now(f.clock).plusDays(7),
        f.db
            .queryForObject(
                "SELECT valid_until FROM user_coupon WHERE id=?", java.sql.Timestamp.class, coupon)
            .toLocalDateTime());
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM coupon_operation_log WHERE user_coupon_id=? AND"
                + " operation_type='returned'",
            Integer.class,
            coupon));
  }

  /** 验证两人竞争最后库存只提交一单。 */
  @Test
  void twoBuyersCompeteForLastQuantityOnlyOneSubmissionCommits() throws Exception {
    User other = f.user();
    String application =
        f.call(
                "POST",
                "/api/me/realname-verifications",
                f.body(other, "realname"),
                other,
                Ids.next(),
                200)
            .path("application")
            .path("id")
            .asString();
    String path = "/api/admin/realname-verifications/" + application;
    JsonNode detail = f.call("GET", path, null, null, null, 200);
    f.call(
        "POST", path + "/approve", f.reviewBody(detail, true, "realname"), null, Ids.next(), 200);
    f.db.update(
        "UPDATE prop_sku_stock SET total_quantity=2,rentable_base_quantity=2,available_quantity=2"
            + " WHERE sku_id=?",
        sku);
    String date = now(f.clock).toLocalDate().plusDays(5).toString();
    String otherChecklist =
        call(
                "POST",
                "/api/me/checklists",
                view("sourceType", "manual", "startDate", date, "endDate", date),
                other,
                key(),
                200)
            .path("resourceId")
            .asString();
    call(
        "POST",
        "/api/me/checklists/" + otherChecklist + "/items",
        view("expectedVersion", 0, "skuId", sku, "quantity", 2, "remark", null),
        other,
        key(),
        200);
    String otherAddress =
        call(
                "POST",
                "/api/me/addresses",
                view(
                    "receiverName",
                    "另一隔离买家",
                    "phone",
                    "13800000002",
                    "regionText",
                    "上海市 静安区",
                    "detailAddress",
                    "隔离路2号",
                    "isDefault",
                    true),
                other,
                key(),
                200)
            .path("resourceId")
            .asString();
    Map<String, Object> otherInput = new LinkedHashMap<>(input);
    otherInput.put("checklistId", otherChecklist);
    otherInput.put("addressId", otherAddress);
    com.mujian.backend.order.service.TradingTokens tokens = f.app.getBean(TradingTokens.class);
    com.mujian.backend.order.domain.CheckoutQuoteData quote1 =
        tokens.readQuote(
            buyer.id(),
            call("POST", "/api/me/checkout-quotes", input, buyer, null, 200)
                .path("quoteToken")
                .asString());
    com.mujian.backend.order.domain.CheckoutQuoteData quote2 =
        tokens.readQuote(
            other.id(),
            call("POST", "/api/me/checkout-quotes", otherInput, other, null, 200)
                .path("quoteToken")
                .asString());
    com.mujian.backend.order.service.CheckoutSubmissionTransaction checkoutSubmissionTransaction =
        f.app.getBean(CheckoutSubmissionTransaction.class);
    com.mujian.backend.payment.dto.PaymentAccount account =
        new com.mujian.backend.payment.dto.PaymentAccount(
            "test-app",
            "test-platform",
            "https://example.invalid/api/wechat-pay/notifications/payment");
    java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
    java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      java.util.List<java.util.concurrent.Future<String>> results = new ArrayList<>();
      for (com.mujian.backend.order.domain.CheckoutQuoteData quote : List.of(quote1, quote2)) {
        String actor = quote.input().checklistId().equals(checklist) ? buyer.id() : other.id();
        results.add(
            executor.submit(
                () -> {
                  barrier.await();
                  try {
                    checkoutSubmissionTransaction.submit(
                        new com.mujian.backend.order.dto.TradingCommand(
                            actor,
                            key(),
                            com.mujian.backend.common.enums.TradingOperation.submitCheckout,
                            "c".repeat(64)),
                        quote,
                        account);
                    return "success";
                  } catch (ApiFailure failure) {
                    return failure.reason;
                  }
                }));
      }
      List<String> outcomes =
          List.of(
              results.get(0).get(10, java.util.concurrent.TimeUnit.SECONDS),
              results.get(1).get(10, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(1, Collections.frequency(outcomes, "success"));
      assertEquals(1, Collections.frequency(outcomes, "STOCK_NOT_ENOUGH"));
      assertEquals(
          1,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM checkout_batch WHERE source_checklist_id IN (?,?)",
              Integer.class,
              checklist,
              otherChecklist));
      assertEquals(
          2,
          f.db.queryForObject(
              "SELECT SUM(quantity) FROM inventory_reservation WHERE sku_id=? AND"
                  + " reservation_status='held'",
              Integer.class,
              sku));
    } finally {
      executor.shutdownNow();
    }
  }

  /** 准备库存占用记录。 */
  String reservation(
      String targetSku,
      String sn,
      LocalDate start,
      LocalDate end,
      int quantity,
      String state,
      LocalDateTime changed) {
    String id = Ids.next();
    f.db.update(
        "INSERT INTO"
            + " inventory_reservation(id,sku_id,sn_id,quantity,source_type,source_id,rental_start_date,rental_end_date,occupy_start_date,occupy_end_date,reservation_status,expires_at,idempotency_key,created_at,updated_at)"
            + " VALUES (?,?,?,?,'system',?,?,?,?,?,?,?,?,?,?)",
        id,
        targetSku,
        sn,
        quantity,
        id,
        start,
        end,
        start,
        end,
        state,
        now(f.clock).minusHours(1),
        id,
        changed,
        changed);
    return id;
  }

  /** 对照逐日结果检查峰值及当日库存时效。 */
  @Test
  void stockPeakMatchesDailyOracleAndCurrentDayDoesNotUseStaleCache() throws Exception {
    com.mujian.backend.prop.mapper.PropSkuStockMapper propSkuStockMapper =
        f.app.getBean(com.mujian.backend.prop.mapper.PropSkuStockMapper.class);
    LocalDate day = now(f.clock).toLocalDate();
    Random random = new Random(4107);
    List<String> ids = new ArrayList<>();
    try {
      for (int scenario = 0; scenario < 60; scenario++) {
        long[] occupied = new long[8];
        int count = 1 + random.nextInt(5);
        for (int index = 0; index < count; index++) {
          int start = random.nextInt(7),
              end = start + random.nextInt(8 - start),
              quantity = 1 + random.nextInt(8);
          ids.add(
              reservation(
                  sku,
                  null,
                  day.plusDays(start),
                  day.plusDays(end),
                  quantity,
                  "held",
                  now(f.clock)));
          for (int date = start; date <= end; date++) occupied[date] += quantity;
        }
        for (int start = 0; start < 8; start++)
          for (int end = start; end < 8; end++) {
            long peak = 0;
            for (int date = start; date <= end; date++) peak = Math.max(peak, occupied[date]);
            long actual =
                propSkuStockMapper
                    .selectStockOccupancyListBySkuIdsAndDates(
                        List.of(sku),
                        day.plusDays(start),
                        day.plusDays(end),
                        now(f.clock).toLocalDate())
                    .get(0)
                    .getAmount()
                    .longValueExact();
            assertEquals(
                Math.max(6 - peak, 0),
                actual,
                "scenario=" + scenario + " dates=" + start + ".." + end);
          }
        for (String id : ids) f.db.update("DELETE FROM inventory_reservation WHERE id=?", id);
        ids.clear();
      }
      ids.add(reservation(sku, null, day, day, 2, "paid_locked", now(f.clock)));
      ids.add(reservation(sku, null, day.plusDays(3), day.plusDays(4), 5, "held", now(f.clock)));
      com.mujian.backend.prop.service.PropInventory inventory =
          f.app.getBean(com.mujian.backend.prop.service.PropInventory.class);
      assertEquals(
          6,
          f.db.queryForObject(
              "SELECT available_quantity FROM prop_sku_stock WHERE sku_id=?", Integer.class, sku));
      assertEquals(4, inventory.stock(sku, false).getAvailableQuantity());
      assertThrows(
          ApiFailure.class, () -> inventory.assertReduction(inventory.stock(sku, false), -2));
      inventory.assertReduction(inventory.stock(sku, false), -1);
      f.clock.at = f.clock.at.plusSeconds(86400);
      assertEquals(6, inventory.stock(sku, false).getAvailableQuantity());
      for (String id : ids) f.db.update("DELETE FROM inventory_reservation WHERE id=?", id);
      ids.clear();
      ids.add(
          reservation(sku, null, day, day.plusDays(6), Integer.MAX_VALUE, "held", now(f.clock)));
      ids.add(
          reservation(sku, null, day, day.plusDays(6), Integer.MAX_VALUE, "held", now(f.clock)));
      assertEquals(
          0,
          propSkuStockMapper
              .selectStockOccupancyListBySkuIdsAndDates(
                  List.of(sku), day, day.plusDays(6), now(f.clock).toLocalDate())
              .get(0)
              .getAmount()
              .intValueExact());
    } finally {
      for (String id : ids) f.db.update("DELETE FROM inventory_reservation WHERE id=?", id);
    }
  }

  /** 验证终态占用归档复制、核对、回滚及去重。 */
  @Test
  void terminalArchiveCopiesVerifiesAndRollsBackWithoutDoubleCounting() throws Exception {
    LocalDate day = now(f.clock).toLocalDate();
    LocalDateTime before = now(f.clock).minusMonths(6);
    String ended =
        reservation(sku, null, day.minusYears(1), day.minusYears(1), 2, "released", before);
    String held =
        reservation(
            sku, null, day.minusYears(1), day.minusYears(1), 2, "held", before.minusDays(1));
    com.mujian.backend.prop.mapper.InventoryReservationMapper inventoryReservationMapper =
        f.app.getBean(com.mujian.backend.prop.mapper.InventoryReservationMapper.class);
    com.mujian.backend.prop.service.ReservationArchive archive =
        f.app.getBean(com.mujian.backend.prop.service.ReservationArchive.class);
    D2ApplicationTest.Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    try {
      fault.table = "inventory_reservation_history";
      assertThrows(RuntimeException.class, archive::archiveBatch);
      fault.table = null;
      assertNotNull(inventoryReservationMapper.selectInventoryReservation(ended, true));
      assertNull(inventoryReservationMapper.selectInventoryReservationHistoryByOrigin(ended));
      archive.archiveBatch();
      assertNull(
          f.db.query(
              "SELECT id FROM inventory_reservation WHERE id=?",
              rs -> rs.next() ? rs.getString(1) : null,
              ended));
      assertNotNull(inventoryReservationMapper.selectInventoryReservation(ended, false));
      assertNotNull(inventoryReservationMapper.selectInventoryReservationHistoryByOrigin(ended));
      assertNotNull(inventoryReservationMapper.selectInventoryReservation(held, true));
      archive.archiveBatch();
      assertEquals(
          1,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM inventory_reservation_history WHERE origin_reservation_id=?",
              Integer.class,
              ended));
      com.mujian.backend.prop.dto.ReservationQueryDTO reservationQueryDTO =
          new com.mujian.backend.prop.dto.ReservationQueryDTO().skuId(sku);
      assertEquals(2, inventoryReservationMapper.selectInventoryReservationCountByQuery(reservationQueryDTO));
    } finally {
      fault.table = null;
      f.db.update(
          "DELETE FROM inventory_reservation_history WHERE origin_reservation_id IN (?,?)",
          ended,
          held);
      f.db.update("DELETE FROM inventory_reservation WHERE id IN (?,?)", ended, held);
    }
  }

  /** 验证首次付款不足一分钟直接关闭。 */
  @Test
  void firstUnattemptedPaymentWithUnderOneMinuteClosesWithoutChannelOrRenewal() throws Exception {
    String token =
        call("POST", "/api/me/checkout-quotes", input, buyer, null, 200)
            .path("quoteToken")
            .asString();
    CheckoutQuoteData quote = f.app.getBean(TradingTokens.class).readQuote(buyer.id(), token);
    com.mujian.backend.order.dto.TradingCommand command =
        new com.mujian.backend.order.dto.TradingCommand(
            buyer.id(),
            key(),
            com.mujian.backend.common.enums.TradingOperation.submitCheckout,
            "d".repeat(64));
    com.mujian.backend.payment.dto.PaymentAccount account =
        new com.mujian.backend.payment.dto.PaymentAccount(
            "test-app",
            "test-platform",
            "https://example.invalid/api/wechat-pay/notifications/payment");
    String batch =
        f.app
            .getBean(CheckoutSubmissionTransaction.class)
            .submit(command, quote, account)
            .resourceId();
    LocalDateTime expiry =
        f.db
            .queryForObject(
                "SELECT expire_at FROM payment_order WHERE checkout_batch_id=?",
                java.sql.Timestamp.class,
                batch)
            .toLocalDateTime();
    f.clock.at = f.clock.at.plusSeconds(541);
    f.app.getBean(com.mujian.backend.payment.service.PaymentExecution.class).execute(batch);
    com.mujian.backend.payment.domain.PaymentOrderDO payment =
        f.app
            .getBean(com.mujian.backend.payment.mapper.PaymentOrderMapper.class)
            .selectPaymentByBatch(batch, false);
    assertEquals("closed", payment.paymentStatus());
    assertEquals(expiry, payment.expireAt());
    org.mockito.Mockito.verify(
            f.app.getBean(com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.class),
            org.mockito.Mockito.never())
        .create(org.mockito.ArgumentMatchers.any());
    assertEquals(
        "released",
        f.db.queryForObject(
            "SELECT reservation_status FROM inventory_reservation WHERE checkout_batch_id=?",
            String.class,
            batch));
    assertEquals(
        "selecting",
        f.db.queryForObject(
            "SELECT checklist_status FROM rental_checklist WHERE id=?", String.class, checklist));
  }

  /** 验证首次付款奖励固定原模板并原子重试。 */
  @Test
  void firstPaymentRewardRetainsOriginalTemplateAndRetriesAtomically() throws Exception {
    String template =
        f.db.queryForObject("SELECT template_id FROM user_coupon WHERE id=?", String.class, coupon);
    String rule = Ids.next();
    int version =
        f.db.queryForObject(
            "SELECT COALESCE(MAX(rule_version),0)+1 FROM coupon_reward_rule WHERE"
                + " event_type='first_rental_paid'",
            Integer.class);
    f.db.update(
        "INSERT INTO"
            + " coupon_reward_rule(id,event_type,rule_version,template_id,status,created_by_admin_user_id,created_at,activated_at)"
            + " VALUES (?,'first_rental_paid',?,?,'active',?,?,?)",
        rule,
        version,
        template,
        f.admin,
        now(f.clock),
        now(f.clock));
    try {
      paidOrder();
      String reward =
          f.db.queryForObject(
              "SELECT id FROM coupon_reward_record WHERE user_id=? AND"
                  + " event_type='first_rental_paid'",
              String.class,
              buyer.id());
      f.db.update(
          "UPDATE coupon_reward_rule SET status='ended',ended_at=? WHERE id=?", now(f.clock), rule);
      f.db.update("UPDATE coupon_template SET template_status='paused' WHERE id=?", template);
      com.mujian.backend.reward.service.RentalRewardIssuer issuer =
          f.app.getBean(com.mujian.backend.reward.service.RentalRewardIssuer.class);
      issuer.issue(reward);
      assertEquals(
          "pending",
          f.db.queryForObject(
              "SELECT reward_status FROM coupon_reward_record WHERE id=?", String.class, reward));
      assertEquals(
          "TEMPLATE_NOT_ACTIVE",
          f.db.queryForObject(
              "SELECT last_error_code FROM coupon_reward_record WHERE id=?", String.class, reward));
      f.db.update("UPDATE coupon_template SET template_status='active' WHERE id=?", template);
      com.mujian.backend.reward.mapper.RentalRewardMapper rentalRewardMapper =
          f.app.getBean(com.mujian.backend.reward.mapper.RentalRewardMapper.class);
      org.mockito.Mockito.doThrow(new IllegalStateException("isolated reward rollback"))
          .when(rentalRewardMapper)
          .increaseTemplateIssuedCount(
              org.mockito.ArgumentMatchers.eq(template), org.mockito.ArgumentMatchers.any());
      assertThrows(IllegalStateException.class, () -> issuer.issue(reward));
      assertEquals(
          0,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM user_coupon WHERE issue_request_id=?",
              Integer.class,
              "reward:" + reward));
      org.mockito.Mockito.reset(rentalRewardMapper);
      issuer.issue(reward);
      issuer.issue(reward);
      assertEquals(
          1,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM user_coupon WHERE issue_request_id=? AND template_id=?",
              Integer.class,
              "reward:" + reward,
              template));
      assertEquals(
          1,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM coupon_operation_log WHERE operation_request_id=?",
              Integer.class,
              "reward:" + reward));
      assertEquals(
          1L,
          f.db.queryForObject(
              "SELECT issued_count FROM coupon_template WHERE id=?", Long.class, template));
      assertEquals(
          "granted",
          f.db.queryForObject(
              "SELECT reward_status FROM coupon_reward_record WHERE id=?", String.class, reward));
    } finally {
      f.db.update(
          "UPDATE coupon_reward_rule SET status='ended',ended_at=? WHERE id=?", now(f.clock), rule);
    }
  }

  /** 检查商户同步只采用可信身份和分账事实。 */
  @Test
  void merchantSyncUsesTrustedIdentityWithoutInventingProfitSharing() throws Exception {
    tools.jackson.databind.JsonNode current =
        call("GET", "/api/me/factory-wechat-account", null, supplier, null, 200);
    assertFalse(current.path("onboardingAvailable").asBoolean());
    assertTrue(current.path("actionUrl").isNull());
    call(
        "POST",
        "/api/me/factory-wechat-account/onboarding",
        view("expectedVersion", current.path("version").asInt()),
        supplier,
        key(),
        503);
    assertEquals("MERCHANT_ONBOARDING_NOT_READY", f.lastReason);
    call("POST", "/api/me/factory-wechat-account/sync", view(), supplier, key(), 503);
    String applyment = "test_" + Ids.next(),
        requestNo = "isolated_" + Ids.next(),
        subMerchant = "test_" + Ids.next() + "0001";
    f.db.update(
        "UPDATE factory_wechat_merchant_account SET"
            + " applyment_id=?,onboarding_request_no=?,account_status='reviewing',profit_sharing_status='pending',sub_mchid=NULL"
            + " WHERE factory_id=?",
        applyment,
        requestNo,
        factoryId);
    com.mujian.backend.infrastructure.payment.wechat.MerchantGateway gateway =
        f.app.getBean(com.mujian.backend.infrastructure.payment.wechat.MerchantGateway.class);
    org.mockito.Mockito.doReturn(
            new com.mujian.backend.infrastructure.payment.wechat.MerchantGateway.Status(
                applyment, requestNo, subMerchant, "FINISH", null))
        .when(gateway)
        .query(applyment, requestNo);
    String request = key();
    tools.jackson.databind.JsonNode synced =
        call("POST", "/api/me/factory-wechat-account/sync", view(), supplier, request, 200);
    assertEquals("active", synced.path("accountStatus").asString());
    assertEquals("pending", synced.path("profitSharingStatus").asString());
    assertEquals("contact_platform", synced.path("nextAction").asString());
    assertEquals("****0001", synced.path("subMerchantMasked").asString());
    call("POST", "/api/me/factory-wechat-account/sync", view(), supplier, request, 200);
    org.mockito.Mockito.verify(gateway, org.mockito.Mockito.times(1)).query(applyment, requestNo);
    call(
        "POST",
        "/api/admin/factory-wechat-accounts/" + factoryId + "/sync",
        view(),
        null,
        key(),
        200);
    org.mockito.Mockito.doReturn(
            new com.mujian.backend.infrastructure.payment.wechat.MerchantGateway.Status(
                applyment, requestNo, "1900000999", "FINISH", null))
        .when(gateway)
        .query(applyment, requestNo);
    call("POST", "/api/me/factory-wechat-account/sync", view(), supplier, key(), 409);
    assertEquals("CHANNEL_IDENTITY_MISMATCH", f.lastReason);
    call("GET", "/api/admin/factory-wechat-accounts?factoryId=" + factoryId, null, null, null, 200);
    call("GET", "/api/admin/factory-wechat-accounts/" + factoryId, null, null, null, 200);
    call("GET", "/api/me/factory-wechat-account", null, buyer, null, 403);
  }

  /** 真RSA/AES通知经过原HTTP过滤链；伪造、错商户和落库失败都不抢占事件号。 */
  @Test
  void signedNotificationPersistsBeforeAcknowledgingAndRejectsMismatches() throws Exception {
    String token =
        call("POST", "/api/me/checkout-quotes", input, buyer, null, 200)
            .path("quoteToken")
            .asString();
    CheckoutQuoteData confirmed = f.app.getBean(TradingTokens.class).readQuote(buyer.id(), token);
    com.mujian.backend.order.dto.TradingCommand command =
        new com.mujian.backend.order.dto.TradingCommand(
            buyer.id(),
            key(),
            com.mujian.backend.common.enums.TradingOperation.submitCheckout,
            "c".repeat(64));
    String batch =
        f.app
            .getBean(CheckoutSubmissionTransaction.class)
            .submit(
                command,
                confirmed,
                new com.mujian.backend.payment.dto.PaymentAccount(
                    "test-app",
                    "test-platform",
                    "https://example.invalid/api/wechat-pay/notifications/payment"))
            .resourceId();
    com.mujian.backend.payment.domain.PaymentOrderDO payment =
        f.app
            .getBean(com.mujian.backend.payment.mapper.PaymentOrderMapper.class)
            .selectPaymentByBatch(batch, false);
    com.mujian.backend.payment.domain.ChannelSuborderDO channelSuborderDO =
        f.app
            .getBean(com.mujian.backend.payment.mapper.PaymentOrderMapper.class)
            .selectSubordersByPayment(payment.id(), false)
            .get(0);
    Map<String, Object> amount =
        view(
            "total_amount",
            channelSuborderDO.channelSuborderAmountFen(),
            "payer_amount",
            channelSuborderDO.channelSuborderAmountFen(),
            "currency",
            "CNY",
            "payer_currency",
            "CNY");
    Map<String, Object> suborder =
        view(
            "mchid",
            payment.channelMchId(),
            "sub_mchid",
            channelSuborderDO.subMchidSnapshot(),
            "out_trade_no",
            channelSuborderDO.channelSubOrderNo(),
            "transaction_id",
            "wx-" + channelSuborderDO.id(),
            "trade_state",
            "SUCCESS",
            "success_time",
            time(now(f.clock)),
            "amount",
            amount);
    Map<String, Object> fact =
        view(
            "combine_appid",
            payment.channelAppId(),
            "combine_mchid",
            payment.channelMchId(),
            "combine_out_trade_no",
            payment.id(),
            "sub_orders",
            List.of(suborder));
    String event = Ids.next();
    assertEquals(400, notifyPayment(event, fact, false));
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM payment_channel_notify_log WHERE channel_event_id=?",
            Integer.class,
            event));
    amount.put("total_amount", channelSuborderDO.channelSuborderAmountFen() + 1);
    assertEquals(400, notifyPayment(event, fact, true));
    amount.put("total_amount", channelSuborderDO.channelSuborderAmountFen());
    suborder.put("sub_mchid", "wrong-merchant");
    assertEquals(400, notifyPayment(event, fact, true));
    suborder.put("sub_mchid", channelSuborderDO.subMchidSnapshot());
    amount.put("currency", "USD");
    assertEquals(400, notifyPayment(event, fact, true));
    amount.put("currency", "CNY");
    D2ApplicationTest.Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    fault.table = "payment_channel_notify_log";
    try {
      assertEquals(500, notifyPayment(event, fact, true));
    } finally {
      fault.table = null;
    }
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM payment_channel_notify_log WHERE channel_event_id=?",
            Integer.class,
            event));
    assertEquals(204, notifyPayment(event, fact, true));
    assertEquals(204, notifyPayment(event, fact, true));
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM payment_channel_notify_log WHERE channel_event_id=? AND"
                + " process_status='received'",
            Integer.class,
            event));
    String stored =
        f.db.queryForObject(
            "SELECT raw_notify_json FROM payment_channel_notify_log WHERE channel_event_id=?",
            String.class,
            event);
    assertFalse(stored.contains(channelSuborderDO.channelSubOrderNo()));
    assertTrue(stored.contains("PUB_KEY_ID_TEST"));
    f.clock.at = f.clock.at.plusSeconds(601);
    com.mujian.backend.payment.mapper.PaymentNotificationMapper notifications =
        f.app.getBean(com.mujian.backend.payment.mapper.PaymentNotificationMapper.class);
    com.mujian.backend.payment.service.PaymentNotificationRecovery recovery =
        f.app.getBean(com.mujian.backend.payment.service.PaymentNotificationRecovery.class);
    recovery.recover(notifications.selectNotificationByEvent(event));
    recovery.recover(notifications.selectNotificationByEvent(event));
    assertEquals("processed", notifications.selectNotificationByEvent(event).processStatus());
    assertEquals(
        channelSuborderDO.channelSuborderAmountFen(),
        f.app
            .getBean(com.mujian.backend.payment.mapper.PaymentOrderMapper.class)
            .selectPaymentByBatch(batch, false)
            .paidAmountFen());
    assertEquals(1, f.app.getBean(OrderFormation.class).form(batch).size());

    java.net.http.HttpRequest wrong =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(f.base + "/api/wechat-pay/notifications/payment"))
            .GET()
            .build();
    assertEquals(
        405, f.http.send(wrong, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
    wrong =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(f.base + "/api/wechat-pay/notifications/other"))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{}"))
            .header("Content-Type", "application/json")
            .build();
    assertNotEquals(
        204, f.http.send(wrong, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
  }

  /** 每次构造新随机密文，但同事件只能有一份持久接收记录。 */
  int notifyPayment(String event, Map<String, Object> fact, boolean valid) throws Exception {
    return notifyChannel("payment", "TRANSACTION.SUCCESS", event, fact, valid);
  }

  /** 支付/退款共用原始RSA/AES构造，但各自通过精确HTTP入口核对原资金来源。 */
  int notifyChannel(
      String kind, String eventType, String event, Map<String, Object> fact, boolean valid)
      throws Exception {
    PaymentTestKeys keys = f.app.getBean(PaymentTestKeys.class);
    String nonce = Ids.next().substring(0, 12), aad = "transaction";
    javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        javax.crypto.Cipher.ENCRYPT_MODE,
        new javax.crypto.spec.SecretKeySpec(
            keys.environment
                .getProperty("mj.payment.wechat.api-v3-key")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
            "AES"),
        new javax.crypto.spec.GCMParameterSpec(
            128, nonce.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    cipher.updateAAD(aad.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    String raw =
        f.json.writeValueAsString(
            view(
                "id",
                event,
                "event_type",
                eventType,
                "resource_type",
                "encrypt-resource",
                "resource",
                view(
                    "algorithm",
                    "AEAD_AES_256_GCM",
                    "nonce",
                    nonce,
                    "associated_data",
                    aad,
                    "ciphertext",
                    Base64.getEncoder()
                        .encodeToString(cipher.doFinal(f.json.writeValueAsBytes(fact))))));
    String timestamp = Long.toString(f.clock.instant().getEpochSecond()), headerNonce = Ids.next();
    java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
    signature.initSign(keys.platform.getPrivate());
    signature.update(
        (timestamp + "\n" + headerNonce + "\n" + (valid ? raw : "forged") + "\n")
            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    java.net.http.HttpRequest request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(f.base + "/api/wechat-pay/notifications/" + kind))
            .header("Content-Type", "application/json")
            .header("Wechatpay-Timestamp", timestamp)
            .header("Wechatpay-Nonce", headerNonce)
            .header("Wechatpay-Serial", "PUB_KEY_ID_TEST")
            .header("Wechatpay-Signature", Base64.getEncoder().encodeToString(signature.sign()))
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(raw))
            .build();
    java.net.http.HttpResponse<String> response =
        f.http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() == 204) assertEquals("", response.body());
    else {
      JsonNode error = f.json.readTree(response.body());
      assertEquals("FAIL", error.path("code").asString());
      assertEquals(2, error.size());
      assertFalse(error.path("message").asString().isBlank());
    }
    System.out.println(
        "D3B_HTTP "
            + (kind.equals("payment") ? "wechatPaymentNotification" : "wechatRefundNotification")
            + " "
            + response.statusCode()
            + " channel_envelope");
    return response.statusCode();
  }

  /** 旧关单晚到账只退旧来源；新批次guard、清单和占用保持原样。 */
  @Test
  void oldClosedLateCashCannotTakeNewChecklistGuard() throws Exception {
    String oldBatch = submitQuoted(buyer, input);
    com.mujian.backend.payment.mapper.PaymentOrderMapper paymentMapper =
        f.app.getBean(com.mujian.backend.payment.mapper.PaymentOrderMapper.class);
    com.mujian.backend.payment.domain.PaymentOrderDO oldPayment =
        paymentMapper.selectPaymentByBatch(oldBatch, false);
    com.mujian.backend.payment.domain.ChannelSuborderDO oldSub =
        paymentMapper.selectSubordersByPayment(oldPayment.id(), false).get(0);
    com.mujian.backend.payment.service.PaymentWorkTransaction work =
        f.app.getBean(com.mujian.backend.payment.service.PaymentWorkTransaction.class);
    work.closeIntent(oldBatch, "user_cancel");
    work.observe(
        oldBatch,
        new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact(
            "test-app", "test-platform", oldPayment.id(), List.of(cash(oldSub, "CLOSED")), false),
        true);
    com.mujian.backend.payment.service.UnformedBatchRefunds refunds =
        f.app.getBean(com.mujian.backend.payment.service.UnformedBatchRefunds.class);
    refunds.reconcile(oldBatch);
    input.put(
        "expectedVersion",
        f.db.queryForObject(
            "SELECT version FROM rental_checklist WHERE id=?", Integer.class, checklist));
    String newBatch = submitQuoted(buyer, input);
    int checklistVersion =
        f.db.queryForObject(
            "SELECT version FROM rental_checklist WHERE id=?", Integer.class, checklist);
    assertTrue(
        work.observe(
                oldBatch,
                new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.PaymentFact(
                    "test-app",
                    "test-platform",
                    oldPayment.id(),
                    List.of(cash(oldSub, "SUCCESS")),
                    false),
                false)
            .refundBatch());
    assertTrue(f.app.getBean(OrderFormation.class).form(oldBatch).isEmpty());
    List<String> obligations = refunds.reconcile(oldBatch);
    assertEquals(1, obligations.size());
    completeRefund(obligations.get(0));
    refunds.reconcile(oldBatch);
    assertEquals(
        newBatch,
        f.db.queryForObject(
            "SELECT current_checkout_batch_id FROM rental_checklist WHERE id=?",
            String.class,
            checklist));
    assertEquals(
        checklistVersion,
        f.db.queryForObject(
            "SELECT version FROM rental_checklist WHERE id=?", Integer.class, checklist));
    assertEquals(
        "checkout_processing",
        f.db.queryForObject(
            "SELECT checklist_status FROM rental_checklist WHERE id=?", String.class, checklist));
    assertEquals(
        "held",
        f.db.queryForObject(
            "SELECT reservation_status FROM inventory_reservation WHERE checkout_batch_id=?",
            String.class,
            newBatch));
    assertEquals(
        "refunded_closed",
        f.db.queryForObject(
            "SELECT batch_status FROM checkout_batch WHERE id=?", String.class, oldBatch));
  }

  /** 同一原单人工接拒和买家取消同时提交，只能一个版本写入；失败方必须重新确认。 */
  @Test
  void concurrentAcceptanceRejectionAndCancellationHaveOneWinner() throws Exception {
    String order = paidOrder();
    JsonNode quote =
        call(
            "POST",
            "/api/me/rental-orders/" + order + "/cancel-quote",
            view("expectedVersion", 0),
            buyer,
            null,
            200);
    com.mujian.backend.order.service.RentalOrderCommands rentalOrderCommands =
        f.app.getBean(RentalOrderCommands.class);
    String token = quote.path("confirmToken").asString();
    java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(3);
    java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newFixedThreadPool(3);
    try {
      List<java.util.concurrent.Future<String>> results = new ArrayList<>();
      for (int index = 0; index < 3; index++) {
        int action = index;
        results.add(
            executor.submit(
                () -> {
                  barrier.await();
                  try {
                    if (action == 2)
                      rentalOrderCommands.cancel(
                          new com.mujian.backend.order.dto.TradingCommand(
                              buyer.id(),
                              key(),
                              com.mujian.backend.common.enums.TradingOperation.cancelRentalOrder,
                              "1".repeat(64)),
                          order,
                          new com.mujian.backend.order.dto.OrderDtos.Cancel(token, "隔离并发取消"));
                    else
                      rentalOrderCommands.factory(
                          new com.mujian.backend.order.dto.TradingCommand(
                              supplier.id(),
                              key(),
                              action == 0
                                  ? com.mujian.backend.common.enums.TradingOperation
                                      .acceptFactoryOrder
                                  : com.mujian.backend.common.enums.TradingOperation
                                      .rejectFactoryOrder,
                              "2".repeat(64)),
                          order,
                          0,
                          action == 0 ? null : "隔离并发拒单");
                    return "success";
                  } catch (ApiFailure failure) {
                    return failure.reason;
                  }
                }));
      }
      List<String> outcomes = new ArrayList<>();
      for (java.util.concurrent.Future<java.lang.String> result : results)
        outcomes.add(result.get(10, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(1, Collections.frequency(outcomes, "success"));
      com.mujian.backend.order.domain.RentalOrderDO actual =
          f.app
              .getBean(com.mujian.backend.order.mapper.RentalOrderMapper.class)
              .selectOrderById(order);
      assertEquals(1, actual.version());
      assertEquals(
          "accepted".equals(actual.orderStatus()) ? 0 : 1,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM refund_order WHERE rental_order_id=?", Integer.class, order));
      f.clock.at = actual.acceptDeadlineAt().atZone(ZONE).toInstant();
      rentalOrderCommands.timeout(order);
      assertEquals(
          actual.orderStatus(),
          f.app
              .getBean(com.mujian.backend.order.mapper.RentalOrderMapper.class)
              .selectOrderById(order)
              .orderStatus());
    } finally {
      executor.shutdownNow();
    }
  }

  /** 禁用仅阻止新增/继续付款，原订单查询和合法取消仍可用；所有正文遵守统一严格边界。 */
  @Test
  void disabledBuyerKeepsHistoricalCancellationAndStrictInputBoundary() throws Exception {
    String order = paidOrder();
    call(
        "POST",
        "/api/me/rental-orders/" + order + "/cancel-quote",
        view("expectedVersion", 0, "factoryId", factoryId),
        buyer,
        null,
        400);
    call("POST", "/api/me/rental-orders/" + order + "/cancel-quote", null, buyer, null, 415);
    java.net.http.HttpRequest nullRequest =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(f.base + "/api/me/rental-orders/" + order + "/cancel-quote"))
            .header("Authorization", "Bearer " + buyer.token())
            .header("Content-Type", "application/json")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("null"))
            .build();
    assertEquals(
        400,
        f.http.send(nullRequest, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
    f.db.update("UPDATE sys_user SET user_status='disabled' WHERE id=?", buyer.id());
    call("GET", "/api/me/rental-orders/" + order, null, buyer, null, 200);
    call("POST", "/api/me/checkout-quotes", input, buyer, null, 403);
    JsonNode quote =
        call(
            "POST",
            "/api/me/rental-orders/" + order + "/cancel-quote",
            view("expectedVersion", 0),
            buyer,
            null,
            200);
    call(
        "POST",
        "/api/me/rental-orders/" + order + "/cancel",
        view("confirmToken", quote.path("confirmToken").asString(), "reason", "隔离禁用后合法退款"),
        buyer,
        key(),
        200);
  }

  /** 减库批准与结算争同一库存根，不允许批准后仍提交超量占用。 */
  @Test
  void reductionApprovalAndCheckoutUseSameStockRoot() throws Exception {
    JsonNode adjustment =
        f.call(
            "POST",
            "/api/me/factory-props/" + sku + "/stock-adjustments",
            view("adjustmentType", "decrease", "quantity", 5, "reason", "隔离并发减库"),
            supplier,
            Ids.next(),
            200);
    CheckoutQuoteData quote =
        f.app
            .getBean(TradingTokens.class)
            .readQuote(
                buyer.id(),
                call("POST", "/api/me/checkout-quotes", input, buyer, null, 200)
                    .path("quoteToken")
                    .asString());
    java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(2);
    java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      java.util.concurrent.Future<String> submitted =
          executor.submit(
              () -> {
                barrier.await();
                try {
                  f.app
                      .getBean(CheckoutSubmissionTransaction.class)
                      .submit(
                          new com.mujian.backend.order.dto.TradingCommand(
                              buyer.id(),
                              key(),
                              com.mujian.backend.common.enums.TradingOperation.submitCheckout,
                              "4".repeat(64)),
                          quote,
                          new com.mujian.backend.payment.dto.PaymentAccount(
                              "test-app",
                              "test-platform",
                              "https://example.invalid/api/wechat-pay/notifications/payment"));
                  return "success";
                } catch (ApiFailure failure) {
                  return failure.reason;
                }
              });
      java.util.concurrent.Future<String> approved =
          executor.submit(
              () -> {
                barrier.await();
                try {
                  f.app
                      .getBean(com.mujian.backend.prop.service.PropReviewService.class)
                      .decide(
                          f.admin,
                          "stock_adjustment",
                          adjustment.path("targetId").asString(),
                          Ids.next(),
                          true,
                          new com.mujian.backend.prop.dto.ReviewDecisionDTO(0, "隔离并发批准"),
                          Ids.next());
                  return "success";
                } catch (ApiFailure failure) {
                  return failure.reason;
                }
              });
      List<String> outcomes =
          List.of(
              submitted.get(10, java.util.concurrent.TimeUnit.SECONDS),
              approved.get(10, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(1, Collections.frequency(outcomes, "success"));
      int base =
          f.db.queryForObject(
              "SELECT rentable_base_quantity FROM prop_sku_stock WHERE sku_id=?",
              Integer.class,
              sku);
      int held =
          f.db.queryForObject(
              "SELECT COALESCE(SUM(quantity),0) FROM inventory_reservation WHERE sku_id=? AND"
                  + " reservation_status='held'",
              Integer.class,
              sku);
      assertTrue(base >= held);
      assertTrue(base == 1 && held == 0 || base == 6 && held == 2);
    } finally {
      executor.shutdownNow();
    }
  }

  /** A类缓冲只追加结束一天，验证前后四个真实SQL边界。 */
  @Test
  void assetBufferUsesFullSingleTailDayOnBothBoundaries() throws Exception {
    JsonNode made = fixture.create(supplier, "A_ASSET", 1);
    String asset = made.path("skuId").asString();
    fixture.approve("sku_audit", made.path("targetId").asString(), 0);
    String sn =
        f.db.queryForObject("SELECT id FROM prop_sn_instance WHERE sku_id=?", String.class, asset);
    LocalDate start = now(f.clock).toLocalDate().plusDays(10), end = start.plusDays(10);
    f.db.update("UPDATE prop_sn_instance SET instance_status='idle' WHERE id=?", sn);
    reservation(asset, sn, start, end.plusDays(1), 1, "held", now(f.clock));
    com.mujian.backend.prop.policy.PropAvailability availability =
        f.app.getBean(com.mujian.backend.prop.policy.PropAvailability.class);
    for (int offset : List.of(-2, -1, 11, 12)) {
      LocalDate date = start.plusDays(offset);
      int expected = offset == -2 || offset == 12 ? 1 : 0;
      assertEquals(
          expected,
          availability
              .quantities(
                  List.of(asset),
                  new com.mujian.backend.prop.policy.PropAvailability.Dates(date, date))
              .getOrDefault(asset, 0));
    }
  }

  /** 实际HTTP跨11点重放原提交，原批次仍保留11:09截止，新请求不能继续提交。 */
  @Test
  void originalPaymentCanCrossElevenButNewSubmissionCannot() throws Exception {
    LocalDate start =
        f.db
            .queryForObject(
                "SELECT rental_start_date FROM rental_checklist WHERE id=?",
                java.sql.Date.class,
                checklist)
            .toLocalDate();
    f.clock.at = start.minusDays(2).atTime(10, 59).atZone(ZONE).toInstant();
    com.mujian.backend.infrastructure.payment.wechat.PaymentGateway gateway =
        f.app.getBean(com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.class);
    org.mockito.Mockito.doReturn(
            new com.mujian.backend.payment.dto.PaymentAccount(
                "test-app",
                "test-platform",
                "https://example.invalid/api/wechat-pay/notifications/payment"))
        .when(gateway)
        .requireReady();
    org.mockito.Mockito.doReturn("test-original-prepay")
        .when(gateway)
        .create(org.mockito.ArgumentMatchers.any());
    org.mockito.Mockito.doReturn(
            new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.Parameters(
                "test-app",
                "1234567890",
                "test-nonce",
                "prepay_id=test-original-prepay",
                "RSA",
                "synthetic-test-signature"))
        .when(gateway)
        .parameters(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    String
        token =
            call("POST", "/api/me/checkout-quotes", input, buyer, null, 200)
                .path("quoteToken")
                .asString(),
        request = key();
    JsonNode initial =
        call("POST", "/api/me/checkout-batches", view("quoteToken", token), buyer, request, 200);
    String batch = initial.path("batch").path("id").asString(),
        expiry = initial.path("batch").path("payment").path("expireAt").asString();
    assertTrue(expiry.contains("11:09:00"));
    f.clock.at = f.clock.at.plusSeconds(61);
    JsonNode replay =
        call("POST", "/api/me/checkout-batches", view("quoteToken", token), buyer, request, 200);
    assertEquals(batch, replay.path("batch").path("id").asString());
    assertEquals(expiry, replay.path("batch").path("payment").path("expireAt").asString());
    assertTrue(replay.path("batch").path("canContinuePay").asBoolean());
    call("POST", "/api/me/checkout-batches", view("quoteToken", token), buyer, key(), 409);
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM checkout_batch WHERE source_checklist_id=?",
            Integer.class,
            checklist));
  }

  /** 退款通知持久化、金额核对、延迟恢复与迟失败不回退成功。 */
  @Test
  void signedRefundNotificationRecoversOriginalCashWithoutDoubleRefund() throws Exception {
    String order = paidOrder();
    JsonNode quote =
        call(
            "POST",
            "/api/me/rental-orders/" + order + "/cancel-quote",
            view("expectedVersion", 0),
            buyer,
            null,
            200);
    call(
        "POST",
        "/api/me/rental-orders/" + order + "/cancel",
        view("confirmToken", quote.path("confirmToken").asString(), "reason", "隔离通知取消"),
        buyer,
        key(),
        200);
    String refundId =
        f.db.queryForObject(
            "SELECT id FROM refund_order WHERE rental_order_id=?", String.class, order);
    com.mujian.backend.payment.domain.RefundOrderDO refund =
        f.app
            .getBean(com.mujian.backend.payment.mapper.RefundMapper.class)
            .selectRefundById(refundId, false);
    com.mujian.backend.payment.projection.RefundSourceProjection source =
        f.app
            .getBean(com.mujian.backend.payment.mapper.RefundMapper.class)
            .selectRefundSourceByChannelNo(refund.channelOutRefundNo());
    Map<String, Object> amount =
        view(
            "refund",
            refund.refundAmountFen(),
            "total",
            source.channelSuborderAmountFen(),
            "currency",
            "CNY");
    Map<String, Object> fact =
        view(
            "sp_mchid",
            source.channelMchId(),
            "sub_mchid",
            source.subMchidSnapshot(),
            "out_trade_no",
            refund.channelOutTradeNo(),
            "transaction_id",
            refund.channelTransactionId(),
            "out_refund_no",
            refund.channelOutRefundNo(),
            "refund_id",
            "wx-r-" + refund.id(),
            "refund_status",
            "SUCCESS",
            "success_time",
            time(now(f.clock)),
            "amount",
            amount);
    String event = Ids.next();
    assertEquals(400, notifyChannel("refund", "REFUND.SUCCESS", event, fact, false));
    amount.put("refund", refund.refundAmountFen() + 1);
    assertEquals(400, notifyChannel("refund", "REFUND.SUCCESS", event, fact, true));
    amount.put("refund", refund.refundAmountFen());
    D2ApplicationTest.Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    fault.table = "payment_channel_notify_log";
    try {
      assertEquals(500, notifyChannel("refund", "REFUND.SUCCESS", event, fact, true));
    } finally {
      fault.table = null;
    }
    assertEquals(204, notifyChannel("refund", "REFUND.SUCCESS", event, fact, true));
    assertEquals(204, notifyChannel("refund", "REFUND.SUCCESS", event, fact, true));
    f.clock.at = f.clock.at.plusSeconds(601);
    com.mujian.backend.payment.mapper.PaymentNotificationMapper notifications =
        f.app.getBean(com.mujian.backend.payment.mapper.PaymentNotificationMapper.class);
    com.mujian.backend.payment.service.PaymentNotificationRecovery recovery =
        f.app.getBean(com.mujian.backend.payment.service.PaymentNotificationRecovery.class);
    recovery.recover(notifications.selectNotificationByEvent(event));
    recovery.recover(notifications.selectNotificationByEvent(event));
    assertEquals("processed", notifications.selectNotificationByEvent(event).processStatus());
    fact.put("refund_status", "ABNORMAL");
    String late = Ids.next();
    assertEquals(204, notifyChannel("refund", "REFUND.ABNORMAL", late, fact, true));
    recovery.recover(notifications.selectNotificationByEvent(late));
    assertEquals(
        "success",
        f.app
            .getBean(com.mujian.backend.payment.mapper.RefundMapper.class)
            .selectRefundById(refundId, false)
            .refundStatus());
    assertEquals(
        refund.refundAmountFen(),
        f.db.queryForObject(
            "SELECT total_refunded_amount_fen FROM rental_order WHERE id=?", Integer.class, order));
  }

  /** 运输段从原明细冻结包装尺寸，查询和后续道具更新不回写原单。 */
  @Test
  void prepaidTransportKeepsOriginalItemDimensions() throws Exception {
    quotationCouponIntegrityDirectionsAndOwnership();
    f.clock.at = f.clock.at.minusSeconds(301);
    String order = paidOrder();
    List<Map<String, Object>> legs =
        f.db.queryForList(
            "SELECT max_item_length_mm,max_item_width_mm,max_item_height_mm FROM"
                + " order_transport_leg WHERE rental_order_id=?",
            order);
    assertEquals(2, legs.size());
    Map<String, Object> dimensions =
        f.db.queryForMap(
            "SELECT CEIL(length_cm*10) AS l,CEIL(width_cm*10) AS w,CEIL(height_cm*10) AS h FROM"
                + " prop_sku WHERE id=?",
            sku);
    for (Map<String, Object> leg : legs) {
      assertEquals(
          ((Number) dimensions.get("l")).intValue(),
          ((Number) leg.get("max_item_length_mm")).intValue());
      assertEquals(
          ((Number) dimensions.get("w")).intValue(),
          ((Number) leg.get("max_item_width_mm")).intValue());
      assertEquals(
          ((Number) dimensions.get("h")).intValue(),
          ((Number) leg.get("max_item_height_mm")).intValue());
    }
    f.db.update("UPDATE prop_sku SET length_cm=length_cm+1 WHERE id=?", sku);
    assertEquals(
        legs,
        f.db.queryForList(
            "SELECT max_item_length_mm,max_item_width_mm,max_item_height_mm FROM"
                + " order_transport_leg WHERE rental_order_id=?",
            order));
    call("GET", "/api/me/rental-orders/" + order, null, buyer, null, 200);
  }
}
