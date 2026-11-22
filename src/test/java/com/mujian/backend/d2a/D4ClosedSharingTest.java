package com.mujian.backend.d2a;

import tools.jackson.databind.JsonNode;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.infrastructure.payment.wechat.SignedWechatHttpFixture;
import com.mujian.backend.settlement.job.SettlementRecovery;
import com.mujian.backend.settlement.mapper.SharingMapper;
import com.mujian.backend.settlement.service.SettlementGeneration;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.node.ObjectNode;

class D4ClosedSharingTest extends SignedWechatHttpFixture {
  /** 进程重扫及原号恢复均以已验签的关闭终态为准。 */
  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void closedRecipientStopsCommissionAndUnlockAcrossRecovery(boolean previousCandidate)
      throws Exception {
    D4RecoveryHttpTest setup = new D4RecoveryHttpTest();
    D3BQuoteTest d3BQuoteTest = setup.fixture();
    try {
      d3BQuoteTest.start();
      String order = setup.dispatch(d3BQuoteTest, d3BQuoteTest.paidOrder(account.mchId()));
      String buyer = "/api/me/rental-orders/" + order, factory = "/api/me/factory-orders/" + order;
      d3BQuoteTest.call("POST", buyer + "/receipt/confirm", view("expectedVersion", 0), d3BQuoteTest.buyer, d3BQuoteTest.key(), 200);
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
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plus(Duration.ofDays(2));
      d3BQuoteTest.f.app.getBean(SettlementGeneration.class).generate(d3BQuoteTest.factoryId);
      String id =
          d3BQuoteTest.f.db.queryForObject(
              "SELECT id FROM wechat_profit_sharing_order WHERE rental_order_id=?",
              String.class,
              order);
      SharingMapper sharingMapper = d3BQuoteTest.f.app.getBean(SharingMapper.class);
      SharingMapper.State original = sharingMapper.selectWechatProfitSharingOrderState(id);
      SettlementRecovery recovery = d3BQuoteTest.f.app.getBean(SettlementRecovery.class);
      ReflectionTestUtils.setField(recovery, "profitSharingGateway", gateway);
      ObjectNode response =
          json.createObjectNode()
              .put("sub_mchid", original.merchant())
              .put("transaction_id", original.transactionId())
              .put("out_order_no", original.shareNo())
              .put("order_id", "fixture-verified-channel-no");
      reply.set(response.toString());
      recovery.scan();
      assertEquals(List.of("POST /v3/ecommerce/profitsharing/orders"), requests);
      response
          .put("status", "FINISHED")
          .putArray("receivers")
          .addObject()
          .put("type", "MERCHANT_ID")
          .put("receiver_account", account.mchId())
          .put("amount", original.fee())
          .put("result", "CLOSED")
          .put("fail_reason", "NO_RELATION");
      reply.set(response.toString());
      if (previousCandidate) {
        // 保留旧候选的真实快照结构，先前缺失的原因只允许由同一渠道单的签名查询补充。
        assertEquals(
            1,
            d3BQuoteTest.f.db.update(
                "UPDATE wechat_profit_sharing_order SET commission_status='pending',"
                    + " commission_retry_count=1,exception_code='sharing_retry_pending',channel_result_snapshot_json=JSON_OBJECT('outcome','RETRYABLE','actual',0,'channelNo',channel_sharing_no),next_attempt_at=?"
                    + " WHERE id=? AND sharing_status='processing'",
                now(d3BQuoteTest.f.clock).plusSeconds(30),
                id));
      }
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plusSeconds(previousCandidate ? 30 : 10);
      recovery.scan();
      String get =
          "GET /v3/ecommerce/profitsharing/orders?sub_mchid="
              + original.merchant()
              + "&transaction_id="
              + original.transactionId()
              + "&out_order_no="
              + original.shareNo();
      List<String> expected = List.of("POST /v3/ecommerce/profitsharing/orders", get);
      assertEquals(expected, requests);
      SharingMapper.State closed = sharingMapper.selectWechatProfitSharingOrderState(id);
      assertEquals("failed", closed.status());
      assertEquals("failed", closed.commission());
      assertEquals("blocked", closed.unfreeze());
      assertEquals("NO_RELATION", closed.exceptionCode());
      assertNull(closed.due());
      assertNull(closed.lease());
      assertEquals(previousCandidate ? 1 : 0, closed.commissionRetries());
      assertEquals(original.shareNo(), closed.shareNo());
      assertEquals(original.finishNo(), closed.finishNo());
      for (int seconds : List.of(30, 60, 300, 86400)) {
        d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plusSeconds(seconds);
        recovery.scan();
        recovery.run(id);
        assertEquals(expected, requests);
        assertEquals(closed, sharingMapper.selectWechatProfitSharingOrderState(id));
      }
      d3BQuoteTest.f.adminCookie = "mj_admin_dev=" + d3BQuoteTest.f.sessions.create(d3BQuoteTest.f.admin, "admin").session().getId();
      String batch = "/api/admin/settlements/" + closed.batchId();
      assertEquals(
          "NO_RELATION",
          d3BQuoteTest.call("GET", batch, null, null, null, 200).path("exceptionCode").asString());
      JsonNode trace = d3BQuoteTest.call("GET", batch + "/executions", null, null, null, 200).path("items").get(0);
      assertEquals("NO_RELATION", trace.path("exceptionCode").asString());
      assertEquals("fixture-verified-channel-no", trace.path("channelSharingNo").asString());
      assertTrue(trace.path("channelFinishNo").isNull());
      assertEquals(0, trace.path("actualUnlockFen").asInt());
      assertEquals(
          "sharing",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT channel_suborder_status FROM payment_channel_suborder WHERE id=?",
              String.class,
              closed.suborderId()));
    } finally {
      if (d3BQuoteTest.factoryId != null) {
        d3BQuoteTest.f.db.update(
            "DELETE FROM wechat_profit_sharing_order WHERE rental_order_id IN (SELECT id FROM"
                + " rental_order WHERE factory_id=?)",
            d3BQuoteTest.factoryId);
        for (String table :
            List.of(
                "factory_settlement_payment",
                "platform_commission_record",
                "factory_settlement_detail",
                "factory_settlement_batch"))
          d3BQuoteTest.f.db.update("DELETE FROM " + table + " WHERE factory_id=?", d3BQuoteTest.factoryId);
      }
      setup.cleanup(d3BQuoteTest);
    }
  }
}
