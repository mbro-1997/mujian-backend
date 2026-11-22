package com.mujian.backend.d2a;

import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.infrastructure.payment.wechat.ProfitSharingGateway;
import com.mujian.backend.payment.service.RefundWorkTransaction;
import com.mujian.backend.settlement.service.SettlementGeneration;
import com.mujian.backend.settlement.service.SharingWork;
import java.io.OutputStream;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.enums.ProfitSharingOutcome;
import com.mujian.backend.infrastructure.payment.wechat.ProfitSharingGateway.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class D4ExpenseHttpTest {
  /** 在同一张隔离订单分别提交往返草稿，两个读取入口都须保留外部单号原值。 */
  @Test
  void mixedCarrierReferencesSurviveReopeningBothLegs() throws Exception {
    D4RecoveryHttpTest setup = new D4RecoveryHttpTest();
    D3BQuoteTest fixture = setup.fixture();
    try {
      fixture.start();
      fixture.prepareFreightPool();
      String draftOrder = setup.dispatch(fixture);
      String base = "/api/me/factory-orders/" + draftOrder + "/transport-expense";
      Map<String, Object> outbound =
          view(
              "source",
              "third_party",
              "carrierName",
              "去程公司",
              "carrierOrderNo",
              "沪去程Ab209",
              "baseAmountFen",
              3100,
              "extraFees",
              List.of());
      Map<String, Object> returning =
          view(
              "source",
              "third_party",
              "carrierName",
              "返程公司",
              "carrierOrderNo",
              "京返程Xy802",
              "baseAmountFen",
              2100,
              "extraFees",
              List.of());
      String id =
          fixture
              .call(
                  "POST",
                  base + "/save",
                  view(
                      "expectedVersion",
                      0,
                      "previousStatementId",
                      null,
                      "reusedEvidenceIds",
                      List.of(),
                      "outbound",
                      outbound,
                      "return",
                      returning),
                  fixture.supplier,
                  fixture.key(),
                  200)
              .path("resourceId")
              .asString();
      JsonNode latest = fixture.call("GET", base, null, fixture.supplier, null, 200).path("latest");
      JsonNode detail = fixture.call("GET", base + "s/" + id, null, fixture.supplier, null, 200);
      D4EvidenceHttpTest.nativeDraft(fixture, draftOrder, fixture.supplier.token(), "expense");
      for (JsonNode stored : List.of(latest, detail)) {
        assertEquals("沪去程Ab209", stored.path("outbound").path("carrierOrderNo").asString());
        assertEquals("京返程Xy802", stored.path("return").path("carrierOrderNo").asString());
      }
    } finally {
      if (fixture.f.db != null && fixture.factoryId != null)
        fixture.f.db.update(
            "DELETE FROM order_transport_expense_statement WHERE factory_id=?", fixture.factoryId);
      setup.cleanup(fixture);
    }
  }

  /** 验证自提和第三方修订仍按原事实审批并只预留余额。 */
  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(ints = {1100, 6800, 7500})
  void frozenPickupAndThirdPartyRevisionApproveOriginalAndReserveOnlyPoolRemainder(int thirdParty)
      throws Exception {
    int total = 4100 + thirdParty,
        reimbursement = Math.min(10900, total),
        remainder = 10900 - reimbursement;
    D4RecoveryHttpTest setup = new D4RecoveryHttpTest();
    D3BQuoteTest d3BQuoteTest = setup.fixture();
    try {
      d3BQuoteTest.start();
      D2ApplicationTest.User otherFactory = thirdParty == 1100 ? d3BQuoteTest.fixture.supplier() : null;
      String rule = d3BQuoteTest.prepareFreightPool();
      String order = setup.dispatch(d3BQuoteTest);
      String factory = "/api/me/factory-orders/" + order,
          buyer = "/api/me/rental-orders/" + order,
          base = factory + "/transport-expense",
          admin = "/api/admin/rental-orders/" + order + "/transport-expenses/";
      JsonNode context = d3BQuoteTest.call("GET", base, null, d3BQuoteTest.supplier, null, 200);
      assertTrue(context.path("required").asBoolean());
      assertEquals(4100, context.path("return").path("frozenOwnVehicleAmountFen").asInt());
      String outbound = context.path("outbound").path("transportLegId").asString();
      d3BQuoteTest.f.db.update(
          "UPDATE transport_pricing_rule SET own_vehicle_settle_amount_fen=1 WHERE id=?", rule);
      Map<String,Object> third =
          view(
              "source",
              "third_party",
              "carrierName",
              "测试承运方",
              "carrierOrderNo",
              "TEST-CARRIER-101",
              "baseAmountFen",
              1000,
              "extraFees",
              List.of());
      Map<String,Object> own =
          view(
              "source",
              "factory_vehicle",
              "carrierName",
              null,
              "carrierOrderNo",
              null,
              "baseAmountFen",
              null,
              "extraFees",
              List.of());
      Map<String,Object> draft =
          view(
              "expectedVersion",
              0,
              "previousStatementId",
              null,
              "reusedEvidenceIds",
              List.of(),
              "outbound",
              third,
              "return",
              own);
      String first =
          d3BQuoteTest.call("POST", base + "/save", draft, d3BQuoteTest.supplier, d3BQuoteTest.key(), 200)
              .path("resourceId")
              .asString();
      d3BQuoteTest.call(
          "POST",
          base + "/" + first + "/submit",
          view("expectedVersion", 1),
          d3BQuoteTest.supplier,
          d3BQuoteTest.key(),
          409);
      d3BQuoteTest.call("POST", buyer + "/receipt/confirm", view("expectedVersion", 0), d3BQuoteTest.buyer, d3BQuoteTest.key(), 200);
      d3BQuoteTest.call(
          "POST",
          factory + "/return-shipment/start-pickup",
          view(
              "expectedVersion",
              0,
              "expectedArrivalAt",
              time(now(d3BQuoteTest.f.clock).plusHours(3)),
              "remark",
              null),
          d3BQuoteTest.supplier,
          d3BQuoteTest.key(),
          200);
      String item =
          d3BQuoteTest.f.db.queryForObject(
              "SELECT id FROM rental_order_item WHERE rental_order_id=?", String.class, order);
      d3BQuoteTest.call(
          "POST",
          factory + "/return-check/quantity",
          view("expectedVersion", 0, "itemId", item, "quantity", 2),
          d3BQuoteTest.supplier,
          d3BQuoteTest.key(),
          200);
      d3BQuoteTest.call(
          "POST",
          factory + "/return-check/confirm",
          view("expectedVersion", 1),
          d3BQuoteTest.supplier,
          d3BQuoteTest.key(),
          200);
      d3BQuoteTest.call(
          "POST",
          base + "/" + first + "/submit",
          view("expectedVersion", 1),
          d3BQuoteTest.supplier,
          d3BQuoteTest.key(),
          409);
      assertEquals("EXPENSE_EVIDENCE_REQUIRED", d3BQuoteTest.f.lastReason);
      Map<String,Object> pic =
          D4EvidenceHttpTest.part(
              D4EvidenceHttpTest.upload(d3BQuoteTest, factory, "transport_expense", d3BQuoteTest.supplier, 200),
              "transport_expense");
      pic.put("scene", "carrier_order");
      pic.put("statementId", first);
      pic.put("transportLegId", outbound);
      String originalBinding = d3BQuoteTest.key();
      d3BQuoteTest.call(
          "POST",
          factory + "/evidence",
          view("expectedVersion", 1, "materials", List.of(pic)),
          d3BQuoteTest.supplier,
          originalBinding,
          200);
      assertEquals(
          originalBinding,
          d3BQuoteTest.call(
                  "POST",
                  factory + "/evidence",
                  view("expectedVersion", 2, "materials", List.of(pic)),
                  d3BQuoteTest.supplier,
                  d3BQuoteTest.key(),
                  200)
              .path("requestId")
              .asString());
      String evidence =
          d3BQuoteTest.call("GET", factory + "/evidence?statementId=" + first, null, d3BQuoteTest.supplier, null, 200)
              .path("items")
              .get(0)
              .path("id")
              .asString();
      d3BQuoteTest.call(
          "POST",
          base + "/" + first + "/submit",
          view("expectedVersion", 2),
          d3BQuoteTest.supplier,
          d3BQuoteTest.key(),
          200);
      d3BQuoteTest.call(
          "POST",
          admin + first + "/review",
          view("expectedVersion", 3, "decision", "return", "reason", "请核对第三方发票金额"),
          null,
          d3BQuoteTest.key(),
          200);
      third.put("baseAmountFen", thirdParty);
      draft.put("expectedVersion", 4);
      draft.put("previousStatementId", first);
      draft.put("reusedEvidenceIds", List.of(evidence));
      String second =
          d3BQuoteTest.call("POST", base + "/save", draft, d3BQuoteTest.supplier, d3BQuoteTest.key(), 200)
              .path("resourceId")
              .asString();
      assertNotEquals(first, second);
      assertEquals(
          "1",
          d3BQuoteTest.call("GET", factory + "/evidence?statementId=" + second, null, d3BQuoteTest.supplier, null, 200)
              .path("total")
              .asString());
      d3BQuoteTest.call(
          "POST",
          base + "/" + second + "/submit",
          view("expectedVersion", 1),
          d3BQuoteTest.supplier,
          d3BQuoteTest.key(),
          200);
      Map<String,Object> approve = view("expectedVersion", 2, "decision", "approve", "reason", null);
      String key = d3BQuoteTest.key();
      d3BQuoteTest.call("POST", admin + second + "/review", approve, null, key, 200);
      d3BQuoteTest.call("POST", admin + second + "/review", approve, null, key, 200);
      JsonNode approved = d3BQuoteTest.call("GET", admin + second, null, null, null, 200);
      assertEquals(total, approved.path("approvedTotalFen").asInt());
      assertEquals(reimbursement, approved.path("onlineSettleFen").asInt());
      assertEquals(remainder, approved.path("refundableFen").asInt());
      assertEquals(total - reimbursement, approved.path("overBudgetFen").asInt());
      assertEquals(remainder > 0 ? 1 : 0, approved.path("refundIds").size());
      if (remainder > 0)
        assertEquals(
            remainder,
            d3BQuoteTest.f.db.queryForObject(
                "SELECT freight_refund_amount_fen FROM refund_order WHERE id=?",
                Integer.class,
                approved.path("refundIds").get(0).asString()));
      assertEquals(
          "2",
          d3BQuoteTest.call("GET", factory + "/transport-expenses", null, d3BQuoteTest.supplier, null, 200)
              .path("total")
              .asString());
      assertEquals(
          "1",
          d3BQuoteTest.call(
                  "GET",
                  "/api/admin/transport-expenses?status=approved&factoryId=" + d3BQuoteTest.factoryId,
                  null,
                  null,
                  null,
                  200)
              .path("total")
              .asString());
      SettlementGeneration generator =
          d3BQuoteTest.f.app.getBean(com.mujian.backend.settlement.service.SettlementGeneration.class);
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plus(java.time.Duration.ofDays(1));
      generator.generate(d3BQuoteTest.factoryId);
      assertEquals(
          remainder > 0 ? 0 : 1,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM factory_settlement_batch WHERE factory_id=?",
              Integer.class,
              d3BQuoteTest.factoryId));
      RefundWorkTransaction work = d3BQuoteTest.f.app.getBean(com.mujian.backend.payment.service.RefundWorkTransaction.class);
      for (String refund :
          d3BQuoteTest.f.db.queryForList(
              "SELECT id FROM refund_order WHERE rental_order_id=? ORDER BY id",
              String.class,
              order)) {
        PaymentGateway.RefundRequest refundRequest = work.claim(refund).request();
        PaymentGateway.RefundFact fact =
            new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.RefundFact(
                refundRequest.account().mchId(),
                refundRequest.subMchid(),
                refundRequest.outTradeNo(),
                refundRequest.transactionId(),
                refundRequest.refundNo(),
                d3BQuoteTest.key(),
                "SUCCESS",
                refundRequest.totalFen(),
                refundRequest.refundFen(),
                "CNY",
                d3BQuoteTest.f.clock.instant(),
                false);
        work.observe(refund, fact);
        work.observe(refund, fact);
      }
      assertEquals(
          "completed",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT order_status FROM rental_order WHERE id=?", String.class, order));
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plus(java.time.Duration.ofDays(1));
      generator.generate(d3BQuoteTest.factoryId);
      generator.generate(d3BQuoteTest.factoryId);
      assertEquals(
          1,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM factory_settlement_detail WHERE rental_order_id=? AND"
                  + " freight_reimbursement_amount_fen=?",
              Integer.class,
              order,
              reimbursement));
      assertEquals(
          "sharing",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT channel_suborder_status FROM payment_channel_suborder WHERE"
                  + " rental_order_id=?",
              String.class,
              order));
      assertThrows(
          org.springframework.dao.DataAccessException.class,
          () ->
              d3BQuoteTest.f.db.update(
                  "UPDATE factory_settlement_detail SET"
                      + " platform_commission_amount_fen=platform_commission_amount_fen+1 WHERE"
                      + " rental_order_id=?",
                  order));
      String execution =
          d3BQuoteTest.f.db.queryForObject(
              "SELECT id FROM wechat_profit_sharing_order WHERE rental_order_id=?",
              String.class,
              order);
      SharingWork sharing = d3BQuoteTest.f.app.getBean(com.mujian.backend.settlement.service.SharingWork.class);
      String batch =
          d3BQuoteTest.f.db.queryForObject(
              "SELECT settlement_batch_id FROM wechat_profit_sharing_order WHERE id=?",
              String.class,
              execution);
      String adminBatch = "/api/admin/settlements/" + batch,
          factoryBatch = "/api/me/factory-settlements/" + batch;
      d3BQuoteTest.f.adminCookie = "mj_admin_dev=" + d3BQuoteTest.f.sessions.create(d3BQuoteTest.f.admin, "admin").session().getId();
      d3BQuoteTest.f.csrf =
          d3BQuoteTest.f
              .call("GET", "/api/admin/auth/csrf", null, null, null, 200)
              .path("csrfToken")
              .asString();
      d3BQuoteTest.call("GET", "/api/me/factory-settlements", null, d3BQuoteTest.buyer, null, 403);
      assertEquals("SUPPLIER_ROLE_REQUIRED", d3BQuoteTest.f.lastReason);
      assertEquals(
          "0",
          d3BQuoteTest.call("GET", "/api/me/factory-settlements", null, d3BQuoteTest.supplier, null, 200)
              .path("total")
              .asString());
      d3BQuoteTest.call("GET", factoryBatch, null, d3BQuoteTest.supplier, null, 404);
      d3BQuoteTest.call("GET", factoryBatch + "/items", null, d3BQuoteTest.supplier, null, 404);
      d3BQuoteTest.call("GET", factoryBatch + "/executions", null, d3BQuoteTest.supplier, null, 404);
      if (thirdParty == 7500) {
        // 同号明确失败按 30 / 60 / 300 秒重试，失败源保持冻结，工厂不能查到到账。
        String originalNo = null;
        int[] waits = {30, 60, 300};
        for (int n = 0; n <= 3; n++) {
          SharingWork.Claim attempt = sharing.claim(execution);
          assertNotNull(attempt);
          assertFalse(attempt.query());
          if (originalNo == null) originalNo = attempt.request().outNo();
          assertEquals(originalNo, attempt.request().outNo());
          sharing.observe(
              attempt, new Observation(ProfitSharingOutcome.RETRYABLE, 0, null));
          if (n < 3) {
            assertNull(sharing.claim(execution));
            d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plusSeconds(waits[n]);
          }
        }
        assertNull(sharing.claim(execution));
        d3BQuoteTest.call("GET", factoryBatch, null, d3BQuoteTest.supplier, null, 404);
        JsonNode stalled = d3BQuoteTest.call("GET", adminBatch, null, null, null, 200);
        assertEquals("sharing_retry_exhausted", stalled.path("exceptionCode").asString());
        assertEquals("0", stalled.path("summary").get("actualUnlockFen").asString());
        assertEquals("processing", stalled.path("summary").path("status").asString());
        assertEquals(
            "failed",
            d3BQuoteTest.f.db.queryForObject(
                "SELECT payment_status FROM factory_settlement_payment WHERE settlement_batch_id=?",
                String.class,
                batch));
        return;
      }
      JsonNode before = d3BQuoteTest.call("GET", adminBatch, null, null, null, 200);
      assertEquals("processing", before.path("summary").path("status").asString());
      assertEquals(
          "1",
          d3BQuoteTest.call(
                  "GET",
                  "/api/admin/settlements?factoryId=" + d3BQuoteTest.factoryId + "&status=processing",
                  null,
                  null,
                  null,
                  200)
              .path("total")
              .asString());
      d3BQuoteTest.call("GET", "/api/admin/settlements?fromDate=2026-11-01", null, null, null, 400);
      SharingWork.Claim create = sharing.claim(execution);
      assertFalse(create.query());
      assertFalse(create.request().finish());
      assertNull(sharing.claim(execution));
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plusSeconds(30);
      SharingWork.Claim retry = sharing.claim(execution);
      assertTrue(retry.query());
      assertEquals(create.request().outNo(), retry.request().outNo());
      sharing.observe(
          retry,
          new Observation(
              ProfitSharingOutcome.SUCCESS, retry.request().amount(), "isolated-share"));
      sharing.observe(create, new Observation(ProfitSharingOutcome.UNKNOWN, 0, null));
      SharingWork.Claim finish = sharing.claim(execution);
      assertTrue(finish.request().finish());
      assertFalse(finish.query());
      assertNotEquals(create.request().outNo(), finish.request().outNo());
      if (thirdParty == 6800) {
        sharing.observe(finish, new Observation(ProfitSharingOutcome.RETRYABLE, 0, null));
        assertNull(sharing.claim(execution));
        d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plusSeconds(30);
        SharingWork.Claim repeated = sharing.claim(execution);
        assertTrue(repeated.request().finish());
        assertFalse(repeated.query());
        assertEquals(finish.request().outNo(), repeated.request().outNo());
        assertEquals("success", repeated.row().commission());
        finish = repeated;
      }
      sharing.observe(finish, new Observation(ProfitSharingOutcome.UNKNOWN, 0, null));
      assertEquals(
          "processing",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT settlement_status FROM factory_settlement_batch WHERE factory_id=?",
              String.class,
              d3BQuoteTest.factoryId));
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plusSeconds(30);
      SharingWork.Claim lookup = sharing.claim(execution);
      assertTrue(lookup.query());
      assertEquals(finish.request().outNo(), lookup.request().outNo());
      ProfitSharingGateway.Observation success =
          new Observation(
              ProfitSharingOutcome.SUCCESS, lookup.request().amount(), "isolated-finish");
      sharing.observe(lookup, success);
      sharing.observe(lookup, success);
      assertEquals(
          "settled",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT settlement_status FROM factory_settlement_batch WHERE factory_id=?",
              String.class,
              d3BQuoteTest.factoryId));
      assertEquals(
          lookup.request().amount(),
          d3BQuoteTest.f.db.queryForObject(
              "SELECT actual_paid_amount_fen FROM factory_settlement_payment WHERE factory_id=?",
              Integer.class,
              d3BQuoteTest.factoryId));
      d3BQuoteTest.call(
          "POST",
          adminBatch + "/acknowledgements",
          view("snapshotToken", before.path("snapshotToken").asString()),
          null,
          "ack-before",
          409);
      assertEquals("SETTLEMENT_SNAPSHOT_CHANGED", d3BQuoteTest.f.lastReason);
      JsonNode detail = d3BQuoteTest.call("GET", factoryBatch, null, d3BQuoteTest.supplier, null, 200);
      assertTrue(detail.path("snapshotToken").isNull());
      if (otherFactory != null) {
        d3BQuoteTest.call("GET", factoryBatch, null, otherFactory, null, 404);
        d3BQuoteTest.call("GET", factoryBatch + "/items", null, otherFactory, null, 404);
        d3BQuoteTest.call("GET", factoryBatch + "/executions", null, otherFactory, null, 404);
      }
      assertEquals(lookup.request().amount(), detail.path("summary").path("actualUnlockFen").asInt());
      D2ApplicationTest.Fault sqlCounts = d3BQuoteTest.f.app.getBean(D2ApplicationTest.Fault.class);
      int visibilityBefore = sqlCounts.settlementVisibilityReads.get();
      int sumsBefore = sqlCounts.settlementUnlockSums.get();
      d3BQuoteTest.call("GET", adminBatch + "/items", null, null, null, 200);
      d3BQuoteTest.call("GET", adminBatch + "/executions", null, null, null, 200);
      assertEquals(
          String.valueOf(reimbursement), detail.path("summary").path("freightFen").asString());
      assertEquals(
          reimbursement,
          d3BQuoteTest.call("GET", factoryBatch + "/items", null, d3BQuoteTest.supplier, null, 200)
              .path("items")
              .get(0)
              .path("freightSettleFen")
              .asInt());
      assertEquals(
          "success",
          d3BQuoteTest.call("GET", factoryBatch + "/executions", null, d3BQuoteTest.supplier, null, 200)
              .path("items")
              .get(0)
              .path("unfreezeStatus")
              .asString());
      assertEquals(4, sqlCounts.settlementVisibilityReads.get() - visibilityBefore);
      assertEquals(0, sqlCounts.settlementUnlockSums.get() - sumsBefore);
      JsonNode after = d3BQuoteTest.call("GET", adminBatch, null, null, null, 200);
      assertEquals(1, sqlCounts.settlementUnlockSums.get() - sumsBefore);
      assertEquals(lookup.request().amount(), after.path("summary").path("actualUnlockFen").asInt());
      Map<String,Object> ack = view("snapshotToken", after.path("snapshotToken").asString(), "remark", "已核对分佣和解冻");
      d3BQuoteTest.call(
          "POST",
          adminBatch + "/acknowledgements",
          view(
              "snapshotToken",
              after.path("snapshotToken").asString().replaceFirst("^[^.]+", "unknown_kid")),
          null,
          d3BQuoteTest.key(),
          409);
      assertEquals("SETTLEMENT_SNAPSHOT_CHANGED", d3BQuoteTest.f.lastReason);
      String audit =
          d3BQuoteTest.call("POST", adminBatch + "/acknowledgements", ack, null, "Ack-case_1", 200)
              .path("acknowledgementId")
              .asString();
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plusSeconds(601);
      assertEquals(
          audit,
          d3BQuoteTest.call("POST", adminBatch + "/acknowledgements", ack, null, "Ack-case_1", 200)
              .path("acknowledgementId")
              .asString());
      d3BQuoteTest.call("POST", adminBatch + "/acknowledgements", ack, null, "ack-case_1", 409);
      assertEquals("SETTLEMENT_SNAPSHOT_EXPIRED", d3BQuoteTest.f.lastReason);
      ack.put("remark", "变更");
      d3BQuoteTest.call("POST", adminBatch + "/acknowledgements", ack, null, "Ack-case_1", 409);
      assertEquals("IDEMPOTENCY_CONFLICT", d3BQuoteTest.f.lastReason);
      assertEquals(
          1,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM admin_operation_audit WHERE target_id=? AND"
                  + " operation_code='acknowledgeSettlement'",
              Integer.class,
              batch));
      assertEquals(
          "success",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT payment_status FROM factory_settlement_payment WHERE settlement_batch_id=?",
              String.class,
              batch));
      if (thirdParty == 1100 && "1".equals(System.getenv("MJ_D4_BROWSER_TEST"))) {
        Process browser =
            new ProcessBuilder(
                    System.getenv("MJ_D4_NODE"), "../mujian-frontend/tests/d4-admin-browser.cjs")
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        try (OutputStream input = browser.getOutputStream()) {
          String orderNo =
              d3BQuoteTest.f.db.queryForObject(
                  "SELECT order_no FROM rental_order WHERE id=?", String.class, order);
          input.write(
              d3BQuoteTest.f.json.writeValueAsBytes(
                  view(
                      "api",
                      d3BQuoteTest.f.base,
                      "cookie",
                      d3BQuoteTest.f.adminCookie,
                      "batch",
                      batch,
                      "factory",
                      d3BQuoteTest.factoryId,
                      "order",
                      orderNo,
                      "out",
                      java.nio.file.Path.of("target/d4-review").toAbsolutePath().toString())));
        }
        assertTrue(browser.waitFor(90, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(0, browser.exitValue());
      }
      assertNull(sharing.claim(execution));
    } finally {
      if (d3BQuoteTest.f.db != null && d3BQuoteTest.factoryId != null) {
        d3BQuoteTest.f.db.update(
            "DELETE a FROM admin_operation_audit a JOIN factory_settlement_batch b ON"
                + " b.id=a.target_id WHERE b.factory_id=? AND"
                + " a.operation_code='acknowledgeSettlement'",
            d3BQuoteTest.factoryId);
        d3BQuoteTest.f.db.update(
            "DELETE w FROM wechat_profit_sharing_order w JOIN factory_settlement_batch b ON"
                + " b.id=w.settlement_batch_id WHERE b.factory_id=?",
            d3BQuoteTest.factoryId);
        for (String t :
            List.of(
                "factory_settlement_payment",
                "platform_commission_record",
                "factory_settlement_detail",
                "factory_settlement_batch"))
          d3BQuoteTest.f.db.update("DELETE FROM " + t + " WHERE factory_id=?", d3BQuoteTest.factoryId);
        d3BQuoteTest.f.db.update(
            "DELETE e FROM order_evidence_record e JOIN rental_order o ON o.id=e.rental_order_id"
                + " WHERE o.factory_id=?",
            d3BQuoteTest.factoryId);
        d3BQuoteTest.f.db.update(
            "DELETE FROM order_transport_expense_statement WHERE factory_id=?", d3BQuoteTest.factoryId);
        d3BQuoteTest.f.db.update(
            "DELETE i FROM order_return_item_check i JOIN order_return_check c ON"
                + " c.id=i.return_check_id WHERE c.factory_id=?",
            d3BQuoteTest.factoryId);
        d3BQuoteTest.f.db.update("DELETE FROM order_return_check WHERE factory_id=?", d3BQuoteTest.factoryId);
        d3BQuoteTest.f.db.update(
            "DELETE s FROM order_return_shipment s JOIN rental_order o ON o.id=s.rental_order_id"
                + " WHERE o.factory_id=?",
            d3BQuoteTest.factoryId);
      }
      setup.cleanup(d3BQuoteTest);
    }
  }
}
