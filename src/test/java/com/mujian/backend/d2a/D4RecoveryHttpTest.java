package com.mujian.backend.d2a;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.delivery.service.*;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** 真实订单锁、菜单权限、事件重放与可控时钟均运行在D4专用库。 */
class D4RecoveryHttpTest {
  /** 创建本场景需要的测试数据。 */
  D3BQuoteTest fixture() {
    D3BQuoteTest d3BQuoteTest = new D3BQuoteTest();
    d3BQuoteTest.extraSources = new Class<?>[] {D4OutboundHttpTest.Ready.class};
    d3BQuoteTest.extraArguments =
        new String[] {
          "--mj.fulfillment.enabled=true", "--mj.fulfillment.recovery-delay-ms=3600000"
        };
    return d3BQuoteTest;
  }

  /** 正式提交仍使用真实文件上传/绑定及逐项检查。 */
  String dispatch(D3BQuoteTest d3BQuoteTest) throws Exception {
    return dispatch(d3BQuoteTest,d3BQuoteTest.paidOrder());
  }

  /** 按实际已付订单继续出库，保留其原始支付商户。 */
  String dispatch(D3BQuoteTest d3BQuoteTest,String order) throws Exception {
    String path = "/api/me/factory-orders/" + order;
    d3BQuoteTest.call("POST", path + "/accept", view("expectedVersion", 0), d3BQuoteTest.supplier, d3BQuoteTest.key(), 200);
    ArrayList<Object> parts = new java.util.ArrayList<>();
    for (int index = 0; index < 3; index++)
      parts.add(
          D4EvidenceHttpTest.part(
              D4EvidenceHttpTest.upload(d3BQuoteTest, path, "outbound", d3BQuoteTest.supplier, 200), "outbound"));
    d3BQuoteTest.call(
        "POST",
        path + "/evidence",
        view("expectedVersion", 0, "materials", parts),
        d3BQuoteTest.supplier,
        d3BQuoteTest.key(),
        200);
    String item =
        d3BQuoteTest.f.db.queryForObject(
            "SELECT id FROM rental_order_item WHERE rental_order_id=?", String.class, order);
    d3BQuoteTest.call(
        "POST",
        path + "/outbound/quantity",
        view("expectedVersion", 1, "itemId", item, "quantity", d3BQuoteTest.f.db.queryForObject("SELECT quantity FROM rental_order_item WHERE id=?",Integer.class,item)),
        d3BQuoteTest.supplier,
        d3BQuoteTest.key(),
        200);
    d3BQuoteTest.call(
        "POST", path + "/outbound/confirm", view("expectedVersion", 2), d3BQuoteTest.supplier, d3BQuoteTest.key(), 200);
    return order;
  }

  /** 检查逾期事件只标记受影响的已付峰值并幂等。 */
  @Test
  void overdueEventOnlyFlagsAffectedPaidPeakAndReplaysOnce() throws Exception {
    D3BQuoteTest d3BQuoteTest = fixture();
    try {
      d3BQuoteTest.start();
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET deposit_amount_fen=503 WHERE id=?",d3BQuoteTest.sku);
      d3BQuoteTest.f.db.update(
          "UPDATE prop_sku_stock SET total_quantity=2,rentable_base_quantity=2,available_quantity=2"
              + " WHERE sku_id=?",
          d3BQuoteTest.sku);
      String source = dispatch(d3BQuoteTest);
      String end = now(d3BQuoteTest.f.clock).toLocalDate().plusDays(10).toString();
      String list =
          d3BQuoteTest.call(
                  "POST",
                  "/api/me/checklists",
                  view("sourceType", "manual", "startDate", end, "endDate", end),
                  d3BQuoteTest.buyer,
                  d3BQuoteTest.key(),
                  200)
              .path("resourceId")
              .asString();
      d3BQuoteTest.call(
          "POST",
          "/api/me/checklists/" + list + "/items",
          view("expectedVersion", 0, "skuId", d3BQuoteTest.sku, "quantity", 2, "remark", null),
          d3BQuoteTest.buyer,
          d3BQuoteTest.key(),
          200);
      d3BQuoteTest.input.put("checklistId", list);
      d3BQuoteTest.coupon = null;
      String affected = d3BQuoteTest.paidOrder();
      OverdueOrders worker = d3BQuoteTest.f.app.getBean(OverdueOrders.class);
      worker.transition(source);
      assertEquals(
          0,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM order_fulfillment_event WHERE rental_order_id=? AND"
                  + " event_type='rental_overdue'",
              Integer.class,
              source));
      Instant original = d3BQuoteTest.f.clock.at;
      d3BQuoteTest.f.clock.at = original.plus(Duration.ofDays(6));
      worker.transition(source);
      worker.transition(source);
      String event =
          d3BQuoteTest.f.db.queryForObject(
              "SELECT id FROM order_fulfillment_event WHERE rental_order_id=? AND"
                  + " event_type='rental_overdue'",
              String.class,
              source);
      assertEquals(
          "overdue_holding",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT reservation_status FROM inventory_reservation WHERE rental_order_id=?",
              String.class,
              source));
      worker.consume(event);
      worker.consume(event);
      d3BQuoteTest.f.clock.at = original;
      JsonNode result =
          d3BQuoteTest.call(
              "GET",
              "/api/admin/fulfillment-risks?sourceOrderId="
                  + source
                  + "&affectedOrderId="
                  + affected,
              null,
              null,
              null,
              200);
      assertEquals("1", result.path("total").asString());
      assertEquals(d3BQuoteTest.sku, result.path("items").get(0).path("skuId").asString());
      JsonNode risk = result.path("items").get(0);
      worker.reconcileRisk(risk.path("id").asString());
      String riskPath = "/api/admin/fulfillment-risks/" + risk.path("id").asString();
      JsonNode riskDetail = d3BQuoteTest.call("GET",riskPath,null,null,null,200);
      assertEquals(3,riskDetail.path("references").size());
      LocalDate dueDate=d3BQuoteTest.f.db.queryForObject("SELECT current_due_date FROM rental_order WHERE id=?",java.time.LocalDate.class,source);
      Instant due=dueDate.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
      long[] windows=new long[]{-1,0,86400000L,86400001L,172800000L,172800001L,259200000L,259200001L};
      List<String> bands=List.of("not_due","within_24h","within_24h","within_48h","within_48h","within_72h","within_72h","beyond_72h");
      int[] ceilings={0,0,0,10,10,30,30,100};
      for(int i=0;i<windows.length;i++){
        d3BQuoteTest.f.clock.at=due.plusMillis(windows[i]);D5ClaimStockHttpTest.renewAdmin(d3BQuoteTest);
        JsonNode review=d3BQuoteTest.call("GET",riskPath,null,null,null,200).path("overdueReview");
        assertEquals(bands.get(i),review.path("band").asString());
        assertEquals(ceilings[i],review.path("ceilingPercent").asInt());
        assertTrue(review.path("depositPaidFen").asInt()>0);
        assertEquals(review.path("depositPaidFen").asInt()*ceilings[i]/100,review.path("ceilingFen").asInt());
      }
      if(System.getenv("MJ_D5_NODE")!=null)D5ClaimStockHttpTest.pages(d3BQuoteTest,source,risk.path("id").asString(),"overdueReview");
      assertEquals(0,d3BQuoteTest.f.db.queryForObject("SELECT COUNT(*) FROM order_deduction WHERE rental_order_id=?",Integer.class,source));
      assertEquals(0,d3BQuoteTest.f.db.queryForObject("SELECT COUNT(*) FROM order_compensation_receivable WHERE rental_order_id=?",Integer.class,source));
      d3BQuoteTest.f.clock.at=original;D5ClaimStockHttpTest.renewAdmin(d3BQuoteTest);
      String contactPath = "/api/admin/cases/" + risk.path("caseId").asString() + "/contact-records";
      String key = d3BQuoteTest.key();
      java.util.Map<String,Object> contact = view("caseVersion",0,"contactChannel","phone","summary","已与双方确认到货安排",
          "references",List.of(view("relationType","rental_order","relationId",affected)));
      JsonNode note = d3BQuoteTest.call("POST",contactPath,contact,null,key,200);
      assertEquals(note,d3BQuoteTest.call("POST",contactPath,contact,null,key,200));
      d3BQuoteTest.call("POST",contactPath,contact,null,d3BQuoteTest.key(),409);
      assertEquals("VERSION_CONFLICT",d3BQuoteTest.f.lastReason);
      contact.put("caseVersion",1);
      d3BQuoteTest.call("POST",contactPath,contact,null,key,409);
      assertEquals("IDEMPOTENCY_CONFLICT",d3BQuoteTest.f.lastReason);
      contact.put("references",List.of(view("relationType","rental_order","relationId",d3BQuoteTest.factoryId)));
      d3BQuoteTest.call("POST",contactPath,contact,null,d3BQuoteTest.key(),404);
      riskDetail = d3BQuoteTest.call("GET",riskPath,null,null,null,200);
      assertEquals(1,riskDetail.path("contacts").size());
      assertEquals(note.path("noteId"),riskDetail.path("contacts").get(0).path("id"));
      assertEquals("pending",riskDetail.path("risk").path("status").asString());
      assertEquals(1,riskDetail.path("risk").path("caseVersion").asInt());

      assertTrue(
          d3BQuoteTest.call("GET", "/api/me/rental-orders/" + affected, null, d3BQuoteTest.buyer, null, 200)
              .path("supplyWarning")
              .asBoolean());
      worker.reconcileRisk(risk.path("id").asString());
      assertEquals("pending",d3BQuoteTest.call("GET",riskPath,null,null,null,200).path("risk").path("status").asString());
      d3BQuoteTest.f.db.update("UPDATE prop_sku_stock SET total_quantity=4,rentable_base_quantity=4 WHERE sku_id=?",d3BQuoteTest.sku);
      worker.reconcileRisk(risk.path("id").asString());
      assertEquals("resolved",d3BQuoteTest.call("GET",riskPath,null,null,null,200).path("risk").path("status").asString());
      d3BQuoteTest.call("POST",contactPath,contact,null,d3BQuoteTest.key(),409);
      assertEquals("CASE_CLOSED",d3BQuoteTest.f.lastReason);
    } finally {
      cleanup(d3BQuoteTest);
    }
  }

  /** 检查提醒与自动关闭在门禁和超时重试中只生效一次。 */
  @Test
  void warningAndAutoClosePersistOnceAcrossGateAndTimeout() throws Exception {
    D3BQuoteTest d3BQuoteTest = fixture();
    try {
      d3BQuoteTest.start();
      String id = dispatch(d3BQuoteTest),
          buyer = "/api/me/rental-orders/" + id,
          admin = "/api/admin/rental-orders/" + id;
      d3BQuoteTest.call("POST", buyer + "/receipt/confirm", view("expectedVersion", 0), d3BQuoteTest.buyer, d3BQuoteTest.key(), 200);
      d3BQuoteTest.call(
          "POST",
          admin + "/return-check/arrival",
          view("expectedVersion", 0, "reason", "现场清点后确认实际到厂"),
          null,
          d3BQuoteTest.key(),
          200);
      FulfillmentRecovery job = d3BQuoteTest.f.app.getBean(FulfillmentRecovery.class);
      Instant start = d3BQuoteTest.f.clock.at;
      d3BQuoteTest.f.clock.at = start.plus(Duration.ofHours(12)).minusMillis(1);
      job.recoverReturns();
      assertEquals(
          "pending",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT check_status FROM order_return_check WHERE rental_order_id=?",
              String.class,
              id));
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plusMillis(1);
      job.recoverReturns();
      job.recoverReturns();
      assertEquals(
          "warning",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT check_status FROM order_return_check WHERE rental_order_id=?",
              String.class,
              id));
      d3BQuoteTest.f.clock.at = start;
      JsonNode queue =
          d3BQuoteTest.call(
              "GET",
              "/api/admin/return-checks?status=warning&factoryId=" + d3BQuoteTest.factoryId,
              null,
              null,
              null,
              200);
      assertEquals("1", queue.path("total").asString());
      assertEquals(id, queue.path("items").get(0).path("orderId").asString());
      d3BQuoteTest.call("GET", "/api/admin/return-checks?autoCloseFrom=2030-01-01", null, null, null, 400);
      d3BQuoteTest.f.clock.at = start.plus(Duration.ofHours(72));
      D4OutboundHttpTest.KnownFacts guard = d3BQuoteTest.f.app.getBean(D4OutboundHttpTest.KnownFacts.class);
      guard.ready = false;
      job.recoverReturns();
      assertEquals(
          "warning",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT check_status FROM order_return_check WHERE rental_order_id=?",
              String.class,
              id));
      guard.ready = true;
      job.recoverReturns();
      job.recoverReturns();
      assertEquals(
          "auto_closed",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT check_status FROM order_return_check WHERE rental_order_id=? AND checked_at"
                  + " IS NULL",
              String.class,
              id));
      assertEquals(
          5,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM admin_case_action a JOIN admin_case c ON c.id=a.admin_case_id"
                  + " WHERE c.rental_order_id=? AND c.case_type='return_check_timeout' AND"
                  + " c.case_status='closed'",
              Integer.class,
              id));
      assertEquals(
          0,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT SUM(i.checked_quantity) FROM order_return_item_check i JOIN"
                  + " order_return_check c ON c.id=i.return_check_id WHERE c.rental_order_id=?",
              Integer.class,
              id));
    } finally {
      cleanup(d3BQuoteTest);
    }
  }

  /** 管理员与到期恢复分别开启事务，在同一订单行锁上竞争，只允许一个处置结果。 */
  @Test
  void adminAndRecoveryCanCloseOnlyOnce() throws Exception {
    D3BQuoteTest d3BQuoteTest = fixture();
    java.util.concurrent.ExecutorService pool =
        java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      d3BQuoteTest.start();
      String order = dispatch(d3BQuoteTest);
      d3BQuoteTest.call(
          "POST",
          "/api/me/rental-orders/" + order + "/receipt/confirm",
          view("expectedVersion", 0),
          d3BQuoteTest.buyer,
          d3BQuoteTest.key(),
          200);
      d3BQuoteTest.call(
          "POST",
          "/api/admin/rental-orders/" + order + "/return-check/arrival",
          view("expectedVersion", 0, "reason", "入库现场到厂"),
          null,
          d3BQuoteTest.key(),
          200);
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plus(Duration.ofHours(72));
      ReturnClosure closure = d3BQuoteTest.f.app.getBean(ReturnClosure.class);
      com.mujian.backend.order.dto.TradingCommand tradingCommand =
          new com.mujian.backend.order.dto.TradingCommand(
              d3BQuoteTest.f.admin,
              d3BQuoteTest.key(),
              com.mujian.backend.common.enums.TradingOperation.adminCloseReturn,
              "a".repeat(64));
      int version =
          d3BQuoteTest.f.db.queryForObject(
              "SELECT version FROM order_return_check WHERE rental_order_id=?",
              Integer.class,
              order);
      java.util.concurrent.CyclicBarrier start = new java.util.concurrent.CyclicBarrier(2);
      java.util.concurrent.Future<Boolean> manual =
          pool.submit(
              () -> {
                start.await(10, java.util.concurrent.TimeUnit.SECONDS);
                try {
                  closure.confirm(tradingCommand, order, version, "库房现场核实后结束");
                  return true;
                } catch (com.mujian.backend.common.api.ApiFailure failure) {
                  assertEquals("RETURN_ALREADY_CLOSED", failure.reason);
                  return false;
                }
              });
      java.util.concurrent.Future<Boolean> automatic =
          pool.submit(
              () -> {
                start.await(10, java.util.concurrent.TimeUnit.SECONDS);
                closure.recover(order);
                return true;
              });
      boolean adminWon = manual.get(20, java.util.concurrent.TimeUnit.SECONDS);
      assertTrue(automatic.get(20, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(
          adminWon ? "admin_closed" : "auto_closed",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT check_status FROM order_return_check WHERE rental_order_id=? AND checked_at"
                  + " IS NULL",
              String.class,
              order));
      assertEquals(
          5,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM admin_case_action a JOIN admin_case c ON c.id=a.admin_case_id"
                  + " WHERE c.rental_order_id=? AND c.case_status='closed'",
              Integer.class,
              order));
      assertEquals(
          2,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM prop_stock_movement WHERE related_order_id=?",
              Integer.class,
              order));
    } finally {
      pool.shutdownNow();
      cleanup(d3BQuoteTest);
    }
  }

  /** 已落库关闭事实跨进程保留，定时任务查缺补齐，且不会重复退押或释放。 */
  @Test
  void durableClosureRepairsMissingInventoryRefundEventAndAuditStep() throws Exception {
    D3BQuoteTest d3BQuoteTest = fixture();
    try {
      d3BQuoteTest.start();
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET deposit_amount_fen=500 WHERE id=?", d3BQuoteTest.sku);
      String order = dispatch(d3BQuoteTest);
      d3BQuoteTest.call(
          "POST",
          "/api/me/rental-orders/" + order + "/receipt/confirm",
          view("expectedVersion", 0),
          d3BQuoteTest.buyer,
          d3BQuoteTest.key(),
          200);
      d3BQuoteTest.call(
          "POST",
          "/api/admin/rental-orders/" + order + "/return-check/arrival",
          view("expectedVersion", 0, "reason", "实际到厂"),
          null,
          d3BQuoteTest.key(),
          200);
      String check =
          d3BQuoteTest.f.db.queryForObject(
              "SELECT id FROM order_return_check WHERE rental_order_id=?", String.class, order);
      d3BQuoteTest.f.db.update(
          "UPDATE order_return_check SET"
              + " check_status='admin_closed',closed_at=?,closed_by_user_id=?,close_reason_code='admin_force_close',close_reason='现场确认后关闭',version=version+1"
              + " WHERE id=?",
          now(d3BQuoteTest.f.clock),
          d3BQuoteTest.f.admin,
          check);
      FulfillmentRecovery worker = d3BQuoteTest.f.app.getBean(FulfillmentRecovery.class);
      D4OutboundHttpTest.KnownFacts guard = d3BQuoteTest.f.app.getBean(D4OutboundHttpTest.KnownFacts.class);
      guard.ready = false;
      worker.recoverReturns();
      assertEquals(
          2,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT renting_quantity FROM prop_sku_stock WHERE sku_id=?", Integer.class, d3BQuoteTest.sku));
      guard.ready = true;
      worker.recoverReturns();
      String refund =
          d3BQuoteTest.f.db.queryForObject(
              "SELECT id FROM refund_order WHERE rental_order_id=? AND"
                  + " deposit_refund_amount_fen=1000",
              String.class,
              order);
      assertEquals(
          2,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM prop_stock_movement WHERE related_order_id=?",
              Integer.class,
              order));
      String completedId =
          d3BQuoteTest.f.db.queryForObject(
              "SELECT id FROM order_fulfillment_event WHERE rental_order_id=? AND"
                  + " event_type='return_close_completed'",
              String.class,
              order);
      assertEquals(
          1,
          d3BQuoteTest.f.db.update(
              "DELETE FROM order_fulfillment_event WHERE rental_order_id=? AND"
                  + " event_type='return_close_completed'",
              order));
      assertEquals(
          1,
          d3BQuoteTest.f.db.update(
              "DELETE a FROM admin_case_action a JOIN admin_case c ON c.id=a.admin_case_id WHERE"
                  + " c.rental_order_id=? AND a.step_no=4",
              order));
      worker.recoverReturns();
      worker.recoverReturns();
      assertEquals(
          completedId,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT id FROM order_fulfillment_event WHERE rental_order_id=? AND"
                  + " event_type='return_close_completed'",
              String.class,
              order));
      assertEquals(
          refund,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT id FROM refund_order WHERE rental_order_id=?", String.class, order));
      assertEquals(
          1,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM order_fulfillment_event WHERE rental_order_id=? AND"
                  + " event_type='return_close_completed'",
              Integer.class,
              order));
      assertEquals(
          5,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM admin_case_action a JOIN admin_case c ON c.id=a.admin_case_id"
                  + " WHERE c.rental_order_id=?",
              Integer.class,
              order));
      assertEquals(
          2,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM prop_stock_movement WHERE related_order_id=?",
              Integer.class,
              order));
    } finally {
      cleanup(d3BQuoteTest);
    }
  }

  /** 清理本用例创建的资源。 */
  void cleanup(D3BQuoteTest d3BQuoteTest) {
    if (d3BQuoteTest.f.db != null && d3BQuoteTest.factoryId != null) {
      d3BQuoteTest.f.db.update(
          "DELETE r FROM order_fulfillment_risk r JOIN rental_order o ON"
              + " o.id=r.source_rental_order_id WHERE o.factory_id=?",
          d3BQuoteTest.factoryId);
      d3BQuoteTest.f.db.update(
          "DELETE r FROM admin_case_action r JOIN admin_case c ON c.id=r.admin_case_id WHERE"
              + " c.factory_id=? AND c.case_type='return_check_timeout' AND r.parent_action_id IS"
              + " NOT NULL",
          d3BQuoteTest.factoryId);
      for (String table : List.of("admin_operation_audit","admin_case_note","admin_case_action", "admin_case_relation"))
        d3BQuoteTest.f.db.update(
            "DELETE r FROM "
                + table
                + " r JOIN admin_case c ON c.id=r.admin_case_id WHERE c.factory_id=? AND"
                + " c.case_type IN ('return_check_timeout','fulfillment_risk')",
            d3BQuoteTest.factoryId);
      d3BQuoteTest.f.db.update(
          "DELETE FROM admin_case WHERE factory_id=? AND case_type IN ('return_check_timeout','fulfillment_risk')",
          d3BQuoteTest.factoryId);
      d3BQuoteTest.f.db.update(
          "DELETE i FROM order_return_item_check i JOIN order_return_check c ON"
              + " c.id=i.return_check_id WHERE c.factory_id=?",
          d3BQuoteTest.factoryId);
      d3BQuoteTest.f.db.update("DELETE FROM order_return_check WHERE factory_id=?", d3BQuoteTest.factoryId);
    }
    new D4OutboundHttpTest().cleanup(d3BQuoteTest);
  }
}
