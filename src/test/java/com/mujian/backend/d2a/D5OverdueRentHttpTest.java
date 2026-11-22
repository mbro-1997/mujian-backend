package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.delivery.service.ReturnClosure;
import com.mujian.backend.dispute.controller.ReceivableController;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.ExtensionPayment;
import com.mujian.backend.payment.mapper.PaymentNotificationMapper;
import com.mujian.backend.payment.service.*;
import com.mujian.backend.payment.service.PaymentExecution;
import com.mujian.backend.payment.service.PaymentNotificationRecovery;
import com.mujian.backend.payment.service.RefundExecution;
import com.mujian.backend.settlement.service.SettlementGeneration;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;

class D5OverdueRentHttpTest
    extends com.mujian.backend.infrastructure.payment.wechat.SignedWechatHttpFixture {
  /** 提取用于断言的金额。 */
  private int amount(D3BQuoteTest d3BQuoteTest, String sql, String id) {
    return d3BQuoteTest.f.db.queryForObject(sql, Integer.class, id);
  }

  /** 更新当前测试会话或期限。 */
  private void renew(D3BQuoteTest d3BQuoteTest) throws Exception {
    D5ClaimStockHttpTest.renewAdmin(d3BQuoteTest);
    d3BQuoteTest.buyer =
        new D2ApplicationTest.User(
            d3BQuoteTest.buyer.id(), d3BQuoteTest.f.sessions.create(d3BQuoteTest.buyer.id(), "miniapp").session().getId());
    d3BQuoteTest.supplier =
        new D2ApplicationTest.User(
            d3BQuoteTest.supplier.id(), d3BQuoteTest.f.sessions.create(d3BQuoteTest.supplier.id(), "miniapp").session().getId());
  }

  /** 将用例推进到指定状态。 */
  private void move(D3BQuoteTest d3BQuoteTest, String order, long ms) throws Exception {
    d3BQuoteTest.f.clock.at =
        d3BQuoteTest.f
            .db
            .queryForObject(
                "SELECT current_due_date FROM rental_order WHERE id=?", LocalDate.class, order)
            .plusDays(1)
            .atStartOfDay(ZONE)
            .toInstant()
            .plusMillis(ms);
    renew(d3BQuoteTest);
  }

  /** 准备承运方式下的五百元可用额场景。 */
  @Test
  void availableFiveHundredCarrier() throws Exception {
    example(0);
  }

  /** 验证自行配送中退款在途的边界。 */
  @Test
  void refundInFlightSelfDelivery() throws Exception {
    example(30000);
  }

  /** 检查拆分回执后的佣金尾差。 */
  @Test
  void splitReceiptsCarryFractionalCommission() throws Exception {
    example(29998);
  }

  /** 执行给定输入的代表场景。 */
  private void example(int priorRefund) throws Exception {
    boolean partial = priorRefund > 0;
    int rentOffset = Math.min(36000, 50000 - priorRefund),
        liabilityOffset = Math.min(15000, 50000 - priorRefund - rentOffset);
    com.mujian.backend.d2a.D4RecoveryHttpTest setup = new D4RecoveryHttpTest();
    com.mujian.backend.d2a.D3BQuoteTest d3BQuoteTest = setup.fixture();
    d3BQuoteTest.extraSources = new Class<?>[0];
    try {
      d3BQuoteTest.start();
      org.springframework.jdbc.core.JdbcTemplate db = d3BQuoteTest.f.db;
      db.update(
          "UPDATE prop_sku SET deposit_amount_fen=25000,daily_rent_price_fen=6000 WHERE id=?",
          d3BQuoteTest.sku);
      String order = setup.dispatch(d3BQuoteTest, d3BQuoteTest.paidOrder(account.mchId())),
          buyer = "/api/me/rental-orders/" + order,
          admin = "/api/admin/rental-orders/" + order;
      d3BQuoteTest.call("POST", buyer + "/receipt/confirm", view("expectedVersion", 0), d3BQuoteTest.buyer, d3BQuoteTest.key(), 200);
      String item =
          db.queryForObject(
              "SELECT id FROM rental_order_item WHERE rental_order_id=?", String.class, order);
      String refund = null;
      if (partial) {
        Map<String, Object> evidence =
            view("evidenceIds", List.of(), "uploadReceiptIds", List.of());
        int version = d3BQuoteTest.call("GET", buyer, null, d3BQuoteTest.buyer, null, 200).path("version").asInt();
        String issue =
            d3BQuoteTest.call(
                    "POST",
                    buyer + "/issues",
                    view(
                        "expectedVersion",
                        version,
                        "scene",
                        "in_use",
                        "effect",
                        "continue",
                        "primaryIssueType",
                        "damaged",
                        "summary",
                        "双方协商减押",
                        "items",
                        List.of(
                            view(
                                "itemId",
                                item,
                                "issueType",
                                "damaged",
                                "quantity",
                                1,
                                "description",
                                "使用中复核")),
                        "evidence",
                        evidence),
                    d3BQuoteTest.buyer,
                    d3BQuoteTest.key(),
                    200)
                .path("resourceId")
                .asString();
        String path = "/api/admin/issues/" + issue;
        d3BQuoteTest.call(
            "POST",
            path + "/resolution",
            view(
                "expectedVersion",
                0,
                "direction",
                "continue",
                "solutionSummary",
                "双方同意原路退还三百元",
                "agreement",
                view(
                    "summary",
                    "双方确认",
                    "confirmedAt",
                    time(now(d3BQuoteTest.f.clock)),
                    "evidenceIds",
                    List.of()),
                "references",
                List.of(),
                "serviceRefund",
                view(
                    "components",
                    view("rentFen", 0, "depositFen", priorRefund, "freightFen", 0),
                    "basis",
                    "双方同意减押")),
            null,
            d3BQuoteTest.key(),
            200);
        refund =
            d3BQuoteTest.call("GET", path, null, null, null, 200)
                .path("plan")
                .path("refunds")
                .get(0)
                .path("refundId")
                .asString();
      }
      move(d3BQuoteTest, order, 3 * 86400000L);
      String at = time(now(d3BQuoteTest.f.clock)), method = partial ? "self_delivery" : "carrier";
      if (priorRefund == 30000 && System.getenv("MJ_D5_NODE") != null)
        D5ClaimStockHttpTest.pages(d3BQuoteTest, order, "", "overdueSelfSend");
      else
        d3BQuoteTest.call(
            "POST",
            buyer + "/return-shipment/send",
            view(
                "expectedVersion",
                0,
                "sentAt",
                at,
                "handoverMethod",
                method,
                "trackingNo",
                partial ? null : "YT123456",
                "carrierName",
                null,
                "remark",
                "实际交接凭据",
                "disputed",
                false),
            d3BQuoteTest.buyer,
            d3BQuoteTest.key(),
            200);
      if (!partial) {
        // 重建升级前已有运单、费用来源尚不存在的在途订单。
        assertEquals(
            1,
            db.update(
                "DELETE FROM rental_overdue_fee WHERE rental_order_id=? AND"
                    + " fee_status='pending_review'",
                order));
      }
      JsonNode fee =
          d3BQuoteTest.call("GET", buyer, null, d3BQuoteTest.buyer, null, 200).path("disputes").path("overdueFee");
      assertEquals("pending_review", fee.path("status").asString());
      assertEquals(3, fee.path("days").asInt());
      assertEquals(36000, fee.path("rentFen").asInt());
      assertEquals(15000, fee.path("capFen").asInt());
      String factory = "/api/me/factory-orders/" + order;
      assertEquals(
          fee,
          d3BQuoteTest.call("GET", factory + "/fulfillment", null, d3BQuoteTest.supplier, null, 200)
              .path("disputes")
              .path("overdueFee"));
      assertEquals(
          0, amount(d3BQuoteTest, "SELECT COUNT(*) FROM order_deduction WHERE rental_order_id=?", order));
      d3BQuoteTest.call(
          "POST",
          admin + "/return-check/arrival",
          view("expectedVersion", 0, "reason", "到厂核对"),
          null,
          d3BQuoteTest.key(),
          200);
      JsonNode atCheck =
          d3BQuoteTest.call("GET", admin + "/fulfillment", null, null, null, 200)
              .path("disputes")
              .path("overdueFee");
      assertEquals("pending_review", atCheck.path("status").asString());
      assertEquals(at, atCheck.path("handoverAt").asString());
      int checkVersion =
          amount(d3BQuoteTest, "SELECT version FROM order_return_check WHERE rental_order_id=?", order);
      Map<String, Object> close = view("expectedVersion", checkVersion, "reason", "实际已到厂，无其他损失");
      d3BQuoteTest.call("POST", admin + "/return-check/close", close, null, d3BQuoteTest.key(), 409);
      assertEquals("OVERDUE_FEE_PENDING", d3BQuoteTest.f.lastReason);
      Map<String, Object> review =
          view(
              "expectedVersion",
              fee.path("version").asInt(),
              "handoverMethod",
              method,
              "handoverAt",
              at,
              "excludedMs",
              0,
              "items",
              List.of(view("itemId", item, "quantity", 2)),
              "liabilityFen",
              15000,
              "unreportedBuyerFault",
              false,
              "basis",
              "据交接凭证和买家未告知记录核定责任");
      d3BQuoteTest.call("POST", admin + "/return-check/overdue-fee", review, null, d3BQuoteTest.key(), 400);
      review.put("unreportedBuyerFault", true);
      review.put("liabilityFen", 15001);
      d3BQuoteTest.call("POST", admin + "/return-check/overdue-fee", review, null, d3BQuoteTest.key(), 400);
      review.put("liabilityFen", 15000);
      if (priorRefund == 0) {
        D2ApplicationTest.Fault fault = d3BQuoteTest.f.app.getBean(D2ApplicationTest.Fault.class);
        fault.table = "order_compensation_receivable";
        try {
          d3BQuoteTest.call("POST", admin + "/return-check/overdue-fee", review, null, d3BQuoteTest.key(), 500);
        } finally {
          fault.table = null;
        }
        assertEquals(
            0, amount(d3BQuoteTest, "SELECT COUNT(*) FROM order_deduction WHERE rental_order_id=?", order));
        assertEquals(
            0, amount(d3BQuoteTest, "SELECT deposit_deducted_amount_fen FROM rental_order WHERE id=?", order));
        assertEquals(
            "pending_review",
            d3BQuoteTest.call("GET", buyer, null, d3BQuoteTest.buyer, null, 200)
                .path("disputes")
                .path("overdueFee")
                .path("status")
                .asString());
      }
      CountDownLatch latch = new CountDownLatch(1);
      ExecutorService pool = Executors.newSingleThreadExecutor();
      String key = d3BQuoteTest.key();
      try {
        com.mujian.backend.order.dto.TradingCommand tradingCommand =
            new TradingCommand(
                d3BQuoteTest.f.admin, d3BQuoteTest.key(), TradingOperation.adminCloseReturn, "3".repeat(64));
        Future<Boolean> contender =
            pool.submit(
                () -> {
                  latch.await();
                  try {
                    d3BQuoteTest.f
                        .app
                        .getBean(ReturnClosure.class)
                        .confirm(tradingCommand, order, checkVersion, "原路退还剩余押金");
                    return true;
                  } catch (ApiFailure e) {
                    assertEquals("OVERDUE_FEE_PENDING", e.reason);
                    return false;
                  }
                });
        latch.countDown();
        d3BQuoteTest.call("POST", admin + "/return-check/overdue-fee", review, null, key, 200);
        if (!contender.get())
          d3BQuoteTest.call("POST", admin + "/return-check/close", close, null, d3BQuoteTest.key(), 200);
      } finally {
        pool.shutdownNow();
      }
      d3BQuoteTest.call("POST", admin + "/return-check/overdue-fee", review, null, key, 200);
      JsonNode finalFee =
          d3BQuoteTest.call("GET", buyer, null, d3BQuoteTest.buyer, null, 200).path("disputes").path("overdueFee");
      assertEquals(rentOffset, finalFee.path("rentOffsetFen").asInt());
      assertEquals(liabilityOffset, finalFee.path("liabilityOffsetFen").asInt());
      assertEquals(0, finalFee.path("availableDepositFen").asInt());
      if (partial) {
        String id = refund;
        Map<String, Object> s =
            db.queryForMap(
                "SELECT r.channel_out_trade_no,r.channel_transaction_id,s.paid_amount_fen FROM"
                    + " refund_order r JOIN payment_channel_suborder s ON"
                    + " s.id=r.payment_channel_suborder_id WHERE r.id=?",
                id);
        reply.set(
            json.writeValueAsString(
                view(
                    "refund_id",
                    "wx-" + id,
                    "out_refund_no",
                    id,
                    "out_trade_no",
                    s.get("channel_out_trade_no"),
                    "transaction_id",
                    s.get("channel_transaction_id"),
                    "status",
                    "SUCCESS",
                    "success_time",
                    at,
                    "amount",
                    view(
                        "total",
                        s.get("paid_amount_fen"),
                        "refund",
                        priorRefund,
                        "currency",
                        "CNY"))));
        RefundExecution executor =
            new RefundExecution(
                d3BQuoteTest.f.app.getBean(RefundWorkTransaction.class),
                gateway,
                d3BQuoteTest.f.app.getBean(UnformedBatchRefunds.class));
        executor.execute(id);
        executor.execute(id);
        d3BQuoteTest.f.app.getBean(ReturnClosure.class).reconcile(order);
      }
      assertEquals(
          50000,
          amount(
              d3BQuoteTest,
              "SELECT deposit_refunded_amount_fen+deposit_deducted_amount_fen FROM rental_order"
                  + " WHERE id=?",
              order));
      com.mujian.backend.settlement.service.SettlementGeneration settlement =
          d3BQuoteTest.f.app.getBean(SettlementGeneration.class);
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plusSeconds(2 * 86400);
      renew(d3BQuoteTest);
      settlement.generate(d3BQuoteTest.factoryId);
      assertEquals(
          rentOffset / 5,
          amount(
              d3BQuoteTest,
              "SELECT overdue_commission_amount_fen FROM factory_settlement_detail WHERE"
                  + " rental_order_id=?",
              order));
      String debt = finalFee.path("receivableId").asString();
      if (priorRefund == 30000 && System.getenv("MJ_D5_NODE") != null)
        D5ClaimStockHttpTest.pages(d3BQuoteTest, order, debt, "overdueMixed");
      pay(d3BQuoteTest, debt, 36000 - rentOffset, 15000 - liabilityOffset);
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plusSeconds(2 * 86400);
      renew(d3BQuoteTest);
      settlement.generate(d3BQuoteTest.factoryId);
      settlement.generate(d3BQuoteTest.factoryId);
      assertEquals(
          2,
          amount(
              d3BQuoteTest, "SELECT COUNT(*) FROM factory_settlement_detail WHERE rental_order_id=?", order));
      assertEquals(
          7200,
          amount(
              d3BQuoteTest,
              "SELECT SUM(overdue_commission_amount_fen) FROM factory_settlement_detail WHERE"
                  + " rental_order_id=?",
              order));
      assertEquals(
          43800,
          amount(
              d3BQuoteTest,
              "SELECT"
                  + " SUM(factory_settle_amount_fen-(rent_settle_base_amount_fen-overdue_rent_amount_fen)+(platform_commission_amount_fen-overdue_commission_amount_fen)-freight_reimbursement_amount_fen)"
                  + " FROM factory_settlement_detail WHERE rental_order_id=?",
              order));
      String batchId =
          db.queryForObject(
              "SELECT settlement_batch_id FROM factory_settlement_detail WHERE rental_order_id=?"
                  + " AND overdue_rent_amount_fen=? LIMIT 1",
              String.class,
              order,
              rentOffset);
      JsonNode settlementItem =
          d3BQuoteTest.call(
                  "GET",
                  "/api/admin/settlements/" + batchId + "/items?page=1&pageSize=20",
                  null,
                  null,
                  null,
                  200)
              .path("items")
              .get(0);
      assertEquals(rentOffset, settlementItem.path("overdueRentFen").asInt());
      assertEquals(rentOffset / 5, settlementItem.path("overdueCommissionFen").asInt());
      JsonNode paid =
          d3BQuoteTest.call("GET", admin + "/fulfillment", null, null, null, 200)
              .path("disputes")
              .path("overdueFee");
      assertEquals(36000 - rentOffset, paid.path("rentPaidFen").asInt());
      assertEquals(15000 - liabilityOffset, paid.path("liabilityPaidFen").asInt());
    } finally {
      d3BQuoteTest.stop();
    }
  }

  /** 检查提货延迟出发与有争议的交接。 */
  @Test
  void pickupDepartureDelayAndDisputedHandover() throws Exception {
    pickup(true);
  }

  /** 验证超过七十二小时须复核数量及责任。 */
  @Test
  void beyond72HoursRequiresReviewOfQuantityAndLiability() throws Exception {
    pickup(false);
  }

  /** 登记测试提货事实。 */
  private void pickup(boolean factoryDelay) throws Exception {
    com.mujian.backend.d2a.D4RecoveryHttpTest setup = new D4RecoveryHttpTest();
    com.mujian.backend.d2a.D3BQuoteTest d3BQuoteTest = setup.fixture();
    d3BQuoteTest.extraSources = new Class<?>[0];
    try {
      d3BQuoteTest.start();
      d3BQuoteTest.prepareFreightPool();
      d3BQuoteTest.f.db.update(
          "UPDATE prop_sku SET deposit_amount_fen=25000,daily_rent_price_fen=6000 WHERE id=?",
          d3BQuoteTest.sku);
      String order = setup.dispatch(d3BQuoteTest, d3BQuoteTest.paidOrder(account.mchId())),
          buyer = "/api/me/rental-orders/" + order,
          factory = "/api/me/factory-orders/" + order,
          admin = "/api/admin/rental-orders/" + order;
      d3BQuoteTest.call("POST", buyer + "/receipt/confirm", view("expectedVersion", 0), d3BQuoteTest.buyer, d3BQuoteTest.key(), 200);
      move(d3BQuoteTest, order, 0);
      d3BQuoteTest.call(
          "POST",
          factory + "/return-shipment/start-pickup",
          view(
              "expectedVersion",
              0,
              "expectedArrivalAt",
              time(now(d3BQuoteTest.f.clock).plusHours(80)),
              "remark",
              "仅派车"),
          d3BQuoteTest.supplier,
          d3BQuoteTest.key(),
          200);
      JsonNode pending =
          d3BQuoteTest.call("GET", buyer, null, d3BQuoteTest.buyer, null, 200).path("disputes").path("overdueFee");
      assertEquals("awaiting_handover", pending.path("status").asString());
      assertTrue(pending.path("handoverAt").isNull());
      assertTrue(pending.path("rentFen").isNull());
      long ms = 3 * 86400000L + 1;
      move(d3BQuoteTest, order, ms);
      String at = time(now(d3BQuoteTest.f.clock));
      Map<String, Object> body =
          view(
              "expectedVersion",
              0,
              "handoverAt",
              at,
              "basis",
              "买家与司机实物交接记录",
              "disputed",
              factoryDelay);
      String key = d3BQuoteTest.key();
      d3BQuoteTest.call(
          "POST", factory + "/return-shipment/handover", body, d3BQuoteTest.fixture.supplier(), d3BQuoteTest.key(), 404);
      if (!factoryDelay && System.getenv("MJ_D5_NODE") != null)
        D5ClaimStockHttpTest.pages(d3BQuoteTest, order, "", "overduePickup");
      else {
        d3BQuoteTest.call("POST", factory + "/return-shipment/handover", body, d3BQuoteTest.supplier, key, 200);
        d3BQuoteTest.call("POST", factory + "/return-shipment/handover", body, d3BQuoteTest.supplier, key, 200);
      }
      d3BQuoteTest.call("POST", factory + "/return-shipment/handover", body, d3BQuoteTest.supplier, d3BQuoteTest.key(), 409);
      d3BQuoteTest.call(
          "POST",
          admin + "/return-check/arrival",
          view("expectedVersion", 0, "reason", "到厂接收"),
          null,
          d3BQuoteTest.key(),
          200);
      JsonNode preview =
          d3BQuoteTest.call("GET", admin + "/fulfillment", null, null, null, 200)
              .path("disputes")
              .path("overdueFee");
      assertEquals(4, preview.path("days").asInt());
      assertEquals(50000, preview.path("capFen").asInt());
      assertEquals("pending_review", preview.path("status").asString());
      assertTrue(preview.path("liabilityFen").isNull());
      assertEquals(
          0, amount(d3BQuoteTest, "SELECT COUNT(*) FROM order_deduction WHERE rental_order_id=?", order));
      List<Map<String, Object>> items =
          List.of(
              view(
                  "itemId", preview.path("lines").get(0).path("itemId").asString(), "quantity", 1));
      Map<String, Object> review =
          view(
              "expectedVersion",
              preview.path("version").asInt(),
              "handoverMethod",
              "factory_pickup",
              "handoverAt",
              at,
              "excludedMs",
              factoryDelay ? ms : 0,
              "items",
              items,
              "liabilityFen",
              factoryDelay ? 0 : 700,
              "unreportedBuyerFault",
              !factoryDelay,
              "basis",
              factoryDelay ? "买家按时备妥，平台核实全部为工厂晚取" : "审核交接凭证，一件由买家迟还，核定七元责任扣款");
      if (factoryDelay && System.getenv("MJ_D5_NODE") != null)
        D5ClaimStockHttpTest.pages(d3BQuoteTest, order, "", "overdueDelay");
      else d3BQuoteTest.call("POST", admin + "/return-check/overdue-fee", review, null, d3BQuoteTest.key(), 200);
      JsonNode fee =
          d3BQuoteTest.call("GET", buyer, null, d3BQuoteTest.buyer, null, 200).path("disputes").path("overdueFee");
      assertEquals(factoryDelay ? 0 : 24000, fee.path("rentFen").asInt());
      assertEquals(factoryDelay ? 0 : 700, fee.path("liabilityFen").asInt());
      assertEquals(factoryDelay ? 50000 : 25300, fee.path("availableDepositFen").asInt());
      assertTrue(fee.path("receivableId").isNull());
      d3BQuoteTest.call(
          "POST",
          admin + "/return-check/close",
          view(
              "expectedVersion",
              amount(d3BQuoteTest, "SELECT version FROM order_return_check WHERE rental_order_id=?", order),
              "reason",
              "归还完毕"),
          null,
          d3BQuoteTest.key(),
          200);
      assertEquals(
          factoryDelay ? 50000 : 25300,
          amount(
              d3BQuoteTest,
              "SELECT SUM(deposit_refund_amount_fen) FROM refund_order WHERE rental_order_id=?",
              order));
    } finally {
      d3BQuoteTest.stop();
    }
  }

  /** 验证上海截点的承运交接不因迟到而计费。 */
  @Test
  void carrierAtShanghaiCutoffRemainsFreeAfterLateArrival() throws Exception {
    com.mujian.backend.d2a.D4RecoveryHttpTest setup = new D4RecoveryHttpTest();
    com.mujian.backend.d2a.D3BQuoteTest d3BQuoteTest = setup.fixture();
    d3BQuoteTest.extraSources = new Class<?>[0];
    try {
      d3BQuoteTest.start();
      String order = setup.dispatch(d3BQuoteTest), buyer = "/api/me/rental-orders/" + order;
      d3BQuoteTest.call("POST", buyer + "/receipt/confirm", view("expectedVersion", 0), d3BQuoteTest.buyer, d3BQuoteTest.key(), 200);
      move(d3BQuoteTest, order, 0);
      String sent = time(now(d3BQuoteTest.f.clock));
      Map<String, Object> shipment =
          view(
              "expectedVersion",
              0,
              "sentAt",
              sent,
              "trackingNo",
              "YT00001",
              "carrierName",
              null,
              "remark",
              "交接寄件");
      d3BQuoteTest.call("POST", buyer + "/return-shipment/save", shipment, d3BQuoteTest.buyer, d3BQuoteTest.key(), 200);
      assertTrue(
          d3BQuoteTest.call("GET", buyer, null, d3BQuoteTest.buyer, null, 200)
              .path("disputes")
              .path("overdueFee")
              .isNull());
      shipment.put("expectedVersion", 1);
      d3BQuoteTest.call("POST", buyer + "/return-shipment/send", shipment, d3BQuoteTest.buyer, d3BQuoteTest.key(), 200);
      move(d3BQuoteTest, order, 4 * 86400000L);
      d3BQuoteTest.call(
          "POST",
          "/api/admin/rental-orders/" + order + "/return-check/arrival",
          view("expectedVersion", 0, "reason", "运输四天后到厂"),
          null,
          d3BQuoteTest.key(),
          200);
      JsonNode fee =
          d3BQuoteTest.call("GET", buyer, null, d3BQuoteTest.buyer, null, 200).path("disputes").path("overdueFee");
      assertEquals("effective", fee.path("status").asString());
      assertEquals(sent, fee.path("handoverAt").asString());
      assertEquals(0, fee.path("days").asInt());
      assertEquals(0, fee.path("rentFen").asInt());
      assertTrue(fee.path("receivableId").isNull());
    } finally {
      d3BQuoteTest.stop();
    }
  }

  /** 到厂晚于截止也可能是按时交运，核对凭据前保留押金及原结算来源。 */
  @Test
  void missingHandoverIsResolvedFromOriginalReturnCheck() throws Exception {
    D4RecoveryHttpTest setup = new D4RecoveryHttpTest();
    D3BQuoteTest d3BQuoteTest = setup.fixture();
    d3BQuoteTest.extraSources = new Class<?>[0];
    try {
      d3BQuoteTest.start();
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET deposit_amount_fen=25000 WHERE id=?", d3BQuoteTest.sku);
      String order = setup.dispatch(d3BQuoteTest),
          buyer = "/api/me/rental-orders/" + order,
          admin = "/api/admin/rental-orders/" + order;
      d3BQuoteTest.call("POST", buyer + "/receipt/confirm", view("expectedVersion", 0), d3BQuoteTest.buyer, d3BQuoteTest.key(), 200);
      move(d3BQuoteTest, order, 0);
      String handedOver = time(now(d3BQuoteTest.f.clock));
      move(d3BQuoteTest, order, 86400001L);
      d3BQuoteTest.call(
          "POST",
          admin + "/return-check/arrival",
          view("expectedVersion", 0, "reason", "登记到厂，实际交接待查"),
          null,
          d3BQuoteTest.key(),
          200);
      JsonNode fee =
          d3BQuoteTest.call("GET", admin + "/fulfillment", null, null, null, 200)
              .path("disputes")
              .path("overdueFee");
      assertEquals("awaiting_handover", fee.path("status").asString());
      assertTrue(fee.path("rentFen").isNull());
      int version =
          amount(d3BQuoteTest, "SELECT version FROM order_return_check WHERE rental_order_id=?", order);
      d3BQuoteTest.call(
          "POST",
          admin + "/return-check/close",
          view("expectedVersion", version, "reason", "核验完成"),
          null,
          d3BQuoteTest.key(),
          409);
      assertEquals("OVERDUE_FEE_PENDING", d3BQuoteTest.f.lastReason);
      assertEquals(
          0, amount(d3BQuoteTest, "SELECT COUNT(*) FROM refund_order WHERE rental_order_id=?", order));
      JsonNode line = fee.path("lines").get(0);
      d3BQuoteTest.call(
          "POST",
          admin + "/return-check/overdue-fee",
          view(
              "expectedVersion",
              fee.path("version").asInt(),
              "handoverMethod",
              "carrier",
              "handoverAt",
              handedOver,
              "excludedMs",
              0,
              "items",
              List.of(view("itemId", line.path("itemId").asString(), "quantity", 0)),
              "liabilityFen",
              0,
              "unreportedBuyerFault",
              false,
              "basis",
              "查承运签收，截止时已交运"),
          null,
          d3BQuoteTest.key(),
          200);
      d3BQuoteTest.call(
          "POST",
          admin + "/return-check/close",
          view("expectedVersion", version, "reason", "可退余额原路退还"),
          null,
          d3BQuoteTest.key(),
          200);
      assertEquals(
          0,
          amount(
              d3BQuoteTest,
              "SELECT COUNT(*) FROM order_compensation_receivable WHERE rental_order_id=?",
              order));
      assertEquals(
          0, amount(d3BQuoteTest, "SELECT deposit_deducted_amount_fen FROM rental_order WHERE id=?", order));
      assertEquals(
          50000,
          amount(
              d3BQuoteTest,
              "SELECT deposit_refund_amount_fen FROM refund_order WHERE rental_order_id=?",
              order));
    } finally {
      d3BQuoteTest.stop();
    }
  }

  /** 验证工厂及管理员可确认出车时交接。 */
  @Test
  void pickupAtDispatchAcceptedByFactoryAndAdmin() throws Exception {
    verifyPickupTimes(PickupDispatch.shipment, Duration.ZERO);
  }

  /** 验证工厂及管理员可确认出车后交接。 */
  @Test
  void pickupAfterDispatchAcceptedByFactoryAndAdmin() throws Exception {
    verifyPickupTimes(PickupDispatch.shipment, Duration.ofMillis(1));
  }

  /** 验证无出车记录的历史提货可用管理凭证。 */
  @Test
  void historicalPickupWithoutDispatchAcceptsAdminEvidence() throws Exception {
    verifyPickupTimes(PickupDispatch.missing, Duration.ZERO);
  }

  /** 验证已有返程出车时拒绝更早的历史交接。 */
  @Test
  void historicalPickupWithReturnLegDispatchRejectsEarlierHandover() throws Exception {
    verifyPickupTimes(PickupDispatch.returnLegOnly, Duration.ZERO);
  }

  private enum PickupDispatch {
    shipment,
    returnLegOnly,
    missing
  }

  /** 核对提货各阶段时间。 */
  private void verifyPickupTimes(PickupDispatch dispatch, Duration lag) throws Exception {
    D4RecoveryHttpTest setup = new D4RecoveryHttpTest();
    D3BQuoteTest d3BQuoteTest = setup.fixture();
    d3BQuoteTest.extraSources = new Class<?>[0];
    try {
      d3BQuoteTest.start();
      d3BQuoteTest.prepareFreightPool();
      d3BQuoteTest.f.db.update(
          "UPDATE prop_sku SET deposit_amount_fen=2000,daily_rent_price_fen=6000 WHERE id=?",
          d3BQuoteTest.sku);
      String order = setup.dispatch(d3BQuoteTest, d3BQuoteTest.paidOrder(account.mchId()));
      String factory = "/api/me/factory-orders/" + order + "/return-shipment";
      String admin = "/api/admin/rental-orders/" + order;
      d3BQuoteTest.call(
          "POST",
          "/api/me/rental-orders/" + order + "/receipt/confirm",
          view("expectedVersion", 0),
          d3BQuoteTest.buyer,
          d3BQuoteTest.key(),
          200);
      move(d3BQuoteTest, order, Duration.ofDays(1).toMillis() + 1);
      LocalDateTime departure = now(d3BQuoteTest.f.clock);
      String early = time(departure.minusNanos(1_000_000)), valid = time(departure.plus(lag));
      if (dispatch == PickupDispatch.shipment) {
        d3BQuoteTest.call(
            "POST",
            factory + "/start-pickup",
            view(
                "expectedVersion",
                0,
                "expectedArrivalAt",
                time(departure.plusHours(2)),
                "remark",
                "司机已出发"),
            d3BQuoteTest.supplier,
            d3BQuoteTest.key(),
            200);
      }
      if (dispatch == PickupDispatch.returnLegOnly) {
        assertEquals(
            1,
            d3BQuoteTest.f.db.update(
                "UPDATE order_transport_leg SET leg_status='dispatched',dispatched_at=? WHERE"
                    + " rental_order_id=? AND leg_type='return' AND service_mode='factory_pickup'",
                departure,
                order));
      }
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plusMillis(2);
      if (dispatch == PickupDispatch.shipment) {
        Map<String, Object> pickup =
            view(
                "expectedVersion",
                0,
                "handoverAt",
                early,
                "basis",
                "司机与买家的交接记录",
                "disputed",
                false);
        JsonNode before = unchargedPickup(d3BQuoteTest, order, 0);
        d3BQuoteTest.call("POST", factory + "/handover", pickup, d3BQuoteTest.supplier, d3BQuoteTest.key(), 409);
        assertEquals("SOURCE_ACTION_REQUIRED", d3BQuoteTest.f.lastReason);
        assertEquals(before, unchargedPickup(d3BQuoteTest, order, 0));
        assertEquals(
            0, amount(d3BQuoteTest, "SELECT COUNT(*) FROM rental_overdue_fee WHERE rental_order_id=?", order));
        pickup.put("handoverAt", valid);
        d3BQuoteTest.call("POST", factory + "/handover", pickup, d3BQuoteTest.supplier, d3BQuoteTest.key(), 200);
        JsonNode reported = unchargedPickup(d3BQuoteTest, order, 1);
        assertEquals(valid, reported.path("handoverAt").asString());
        assertEquals("pending_review", reported.path("status").asString());
      }
      d3BQuoteTest.call(
          "POST",
          admin + "/return-check/arrival",
          view("expectedVersion", 0, "reason", "已验交接凭证并实物到厂"),
          null,
          d3BQuoteTest.key(),
          200);
      int version = dispatch == PickupDispatch.shipment ? 1 : 0;
      JsonNode before = unchargedPickup(d3BQuoteTest, order, version);
      String item = before.path("lines").get(0).path("itemId").asString();
      Map<String, Object> review =
          view(
              "expectedVersion",
              version,
              "handoverMethod",
              "factory_pickup",
              "handoverAt",
              early,
              "excludedMs",
              0,
              "items",
              List.of(view("itemId", item, "quantity", 2)),
              "liabilityFen",
              0,
              "unreportedBuyerFault",
              false,
              "basis",
              "核对司机与买家的交接凭证");
      if (dispatch != PickupDispatch.missing) {
        d3BQuoteTest.call("POST", admin + "/return-check/overdue-fee", review, null, d3BQuoteTest.key(), 409);
        assertEquals("SOURCE_ACTION_REQUIRED", d3BQuoteTest.f.lastReason);
        assertEquals(before, unchargedPickup(d3BQuoteTest, order, version));
      } else {
        assertEquals("awaiting_handover", before.path("status").asString());
        assertEquals(
            0,
            amount(
                d3BQuoteTest,
                "SELECT COUNT(*) FROM order_return_shipment WHERE rental_order_id=? AND sent_at IS"
                    + " NOT NULL",
                order));
      }
      review.put("handoverAt", valid);
      d3BQuoteTest.call("POST", admin + "/return-check/overdue-fee", review, null, d3BQuoteTest.key(), 200);
      JsonNode confirmed =
          d3BQuoteTest.call("GET", admin + "/fulfillment", null, null, null, 200)
              .path("disputes")
              .path("overdueFee");
      assertEquals("effective", confirmed.path("status").asString());
      assertEquals(version + 1, confirmed.path("version").asInt());
      assertEquals(valid, confirmed.path("handoverAt").asString());
      assertEquals(4000, confirmed.path("rentOffsetFen").asInt());
      assertEquals(20000, confirmed.path("rentDueFen").asInt());
    } finally {
      d3BQuoteTest.stop();
    }
  }

  /** 检查不计费提货场景。 */
  private JsonNode unchargedPickup(D3BQuoteTest d3BQuoteTest, String order, int version) throws Exception {
    JsonNode fee =
        d3BQuoteTest.call("GET", "/api/admin/rental-orders/" + order + "/fulfillment", null, null, null, 200)
            .path("disputes")
            .path("overdueFee");
    assertEquals(version, fee.path("version").asInt());
    assertEquals(4000, fee.path("availableDepositFen").asInt());
    assertTrue(fee.path("receivableId").isNull());
    assertEquals(
        0, amount(d3BQuoteTest, "SELECT deposit_deducted_amount_fen FROM rental_order WHERE id=?", order));
    assertEquals(
        0, amount(d3BQuoteTest, "SELECT COUNT(*) FROM order_deduction WHERE rental_order_id=?", order));
    assertEquals(
        0,
        amount(
            d3BQuoteTest,
            "SELECT COUNT(*) FROM order_compensation_receivable WHERE rental_order_id=?",
            order));
    assertEquals(0, amount(d3BQuoteTest, "SELECT COUNT(*) FROM refund_order WHERE rental_order_id=?", order));
    return fee;
  }

  /** 对用例订单发起付款。 */
  private void pay(D3BQuoteTest d3BQuoteTest, String debt, int rent, int liability) throws Exception {
    PaymentExecution exec = d3BQuoteTest.f.app.getBean(PaymentExecution.class);
    ReflectionTestUtils.setField(exec, "paymentGateway", gateway);
    ReflectionTestUtils.setField(d3BQuoteTest.f.app.getBean(ReceivableController.class), "paymentGateway", gateway);
    ReflectionTestUtils.setField(d3BQuoteTest.f.app.getBean(ExtensionPayment.class), "paymentGateway", gateway);
    String path = "/api/me/compensation-receivables/" + debt;
    JsonNode cash = d3BQuoteTest.call("GET", path, null, d3BQuoteTest.buyer, null, 200);
    int owed = rent + liability;
    assertEquals(owed, cash.path("outstandingFen").asInt());
    assertEquals(rent, cash.path("overdueRentDueFen").asInt());
    assertEquals(liability, cash.path("overdueLiabilityDueFen").asInt());
    reply.set("{\"prepay_id\":\"isolated-overdue-prepay\"}");
    String pid =
        d3BQuoteTest.call(
                "POST",
                path + "/pay",
                view("expectedVersion", cash.path("version").asInt()),
                d3BQuoteTest.buyer,
                d3BQuoteTest.key(),
                200)
            .path("payment")
            .path("id")
            .asString();
    Map<String, Object> s =
        d3BQuoteTest.f.db.queryForMap(
            "SELECT channel_sub_order_no,sub_mchid_snapshot FROM payment_channel_suborder WHERE"
                + " payment_order_id=?",
            pid);
    Map<String, Object> fact =
        view(
            "combine_appid",
            "test-app",
            "combine_mchid",
            account.mchId(),
            "combine_out_trade_no",
            pid,
            "sub_orders",
            List.of(
                view(
                    "out_trade_no",
                    s.get("channel_sub_order_no"),
                    "mchid",
                    account.mchId(),
                    "sub_mchid",
                    s.get("sub_mchid_snapshot"),
                    "transaction_id",
                    "wx-" + pid,
                    "trade_state",
                    "SUCCESS",
                    "success_time",
                    d3BQuoteTest.f.clock.instant().toString(),
                    "amount",
                    view(
                        "total_amount",
                        owed,
                        "payer_amount",
                        owed,
                        "currency",
                        "CNY",
                        "payer_currency",
                        "CNY"))));
    String event = Ids.next();
    assertEquals(204, d3BQuoteTest.notifyPayment(event, fact, true));
    assertEquals(204, d3BQuoteTest.notifyPayment(event, fact, true));
    PaymentNotificationMapper inbox = d3BQuoteTest.f.app.getBean(PaymentNotificationMapper.class);
    PaymentNotificationRecovery worker = d3BQuoteTest.f.app.getBean(PaymentNotificationRecovery.class);
    worker.recover(inbox.selectNotificationByEvent(event));
    worker.recover(inbox.selectNotificationByEvent(event));
    cash = d3BQuoteTest.call("GET", path, null, d3BQuoteTest.buyer, null, 200);
    assertEquals(rent, cash.path("overdueRentPaidFen").asInt());
    assertEquals(liability, cash.path("overdueLiabilityPaidFen").asInt());
    assertEquals(
        1, amount(d3BQuoteTest, "SELECT COUNT(*) FROM payment_allocation WHERE payment_order_id=?", pid));
  }
}
