package com.mujian.backend.d2a;

import com.mujian.backend.payment.service.RefundExecution;
import java.net.http.HttpRequest;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.payment.service.RefundWorkTransaction;
import java.util.List;
import org.junit.jupiter.api.Test;

class D5IssuePlanHttpTest extends com.mujian.backend.infrastructure.payment.wechat.SignedWechatHttpFixture {
  private final D4RecoveryHttpTest setup=new D4RecoveryHttpTest();

  /** 核对出车前的未计费状态。 */
  @Test void zeroBeforeDispatch() throws Exception { exercise(false,false); }
  /** 检查发货后需实物归还并单独退款。 */
  @Test void dispatchedNeedsPhysicalReturnAndSeparateRefund() throws Exception { exercise(true,false); }

  /** 验证首次退款尝试与修订方案竞争。 */
  @Test void firstRefundAttemptRacesRevisedPlan() throws Exception { exercise(true,true); }

  /** 按指定分支执行完整测试流程。 */
  private void exercise(boolean dispatch,boolean race) throws Exception {
    D3BQuoteTest d3BQuoteTest=setup.fixture();
    d3BQuoteTest.extraSources=new Class<?>[0];
    try {
      d3BQuoteTest.start();
      if(race)d3BQuoteTest.f.db.update("UPDATE prop_sku SET deposit_amount_fen=100 WHERE id=?",d3BQuoteTest.sku);
      String order=d3BQuoteTest.paidOrder(account.mchId()), factory="/api/me/factory-orders/"+order;
      if (dispatch) setup.dispatch(d3BQuoteTest,order);
      else d3BQuoteTest.call("POST",factory+"/accept",view("expectedVersion",0),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
      String item=d3BQuoteTest.f.db.queryForObject("SELECT id FROM rental_order_item WHERE rental_order_id=?",String.class,order);
      Map<String,Object> report=view("expectedVersion",0,"scene","receipt","effect","paused","primaryIssueType","damaged","summary","双方核对外壳破损",
          "items",List.of(view("itemId",item,"quantity",1,"issueType","damaged","description","外壳裂纹")),
          "evidence",view("evidenceIds",List.of(),"uploadReceiptIds",List.of()));
      String issue=d3BQuoteTest.call("POST","/api/me/rental-orders/"+order+"/issues",report,d3BQuoteTest.buyer,d3BQuoteTest.key(),200).path("resourceId").asString();
      String admin="/api/admin/issues/"+issue;
      Map<String,Object> body=view("expectedVersion",0,"direction","terminate","solutionSummary","双方同意终止并核算退款",
          "agreement",view("summary","双方确认方案","confirmedAt",time(now(d3BQuoteTest.f.clock)),"evidenceIds",List.of()),
          "references",List.of(view("relationType","rental_order","relationId",order)),
          "recovery",dispatch?"按原回程交回工厂":"尚未出库","freightResponsibility","工厂承担",
          "refundComponents",view("rentFen",dispatch?1:0,"depositFen",0,"freightFen",0),
          "depositReason","双方书面确认扣除押金","refundBasis","协商结果","goodsLocation",dispatch?"买家处":"工厂仓库");
      String key=d3BQuoteTest.key();
      JsonNode result=d3BQuoteTest.call("POST",admin+"/resolution",body,null,key,200);
      assertEquals(result.path("resourceId"),d3BQuoteTest.call("POST",admin+"/resolution",body,null,key,200).path("resourceId"));
      JsonNode plan=d3BQuoteTest.call("GET",admin,null,null,null,200).path("plan");
      assertEquals("terminate",plan.path("direction").asString());
      if (!dispatch) {
        status(d3BQuoteTest,order,"terminated","none");
        assertEquals(0,count(d3BQuoteTest,"refund_order",order));
        assertEquals(0,count(d3BQuoteTest,"order_return_check",order));
        assertEquals("succeeded",plan.path("executionStatus").asString());
      } else {
        status(d3BQuoteTest,order,"termination_pending","processing");
        assertEquals(1,plan.path("refunds").size());
        String refund=plan.path("refunds").get(0).path("refundId").asString();
        body.put("expectedVersion",1);
        body.put("solutionSummary","协商修订回收安排");
        d3BQuoteTest.call("POST",admin+"/resolution",body,null,d3BQuoteTest.key(),200);
        JsonNode revised=d3BQuoteTest.call("GET",admin,null,null,null,200).path("plan");
        String original=refund;
        refund=revised.path("refunds").get(0).path("refundId").asString();
        assertNotEquals(original,refund);
        assertEquals("cancelled",d3BQuoteTest.f.db.queryForObject("SELECT refund_status FROM refund_order WHERE id=?",String.class,original));
        RefundWorkTransaction worker=d3BQuoteTest.f.app.getBean(RefundWorkTransaction.class);
        assertNull(worker.claim(original));
        assertNull(worker.claim(refund));
        d3BQuoteTest.call("POST",factory+"/return-check/quantity",view("expectedVersion",0,"itemId",item,"quantity",2),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
        status(d3BQuoteTest,order,"return_checking","processing");
        d3BQuoteTest.f.clock.at=d3BQuoteTest.f.clock.at.plus(java.time.Duration.ofHours(73));
        ConfigurableApplicationContext ctx=d3BQuoteTest.f.app;
        ctx.getBean(com.mujian.backend.delivery.service.ReturnClosure.class).recover(order);
        assertNull(worker.claim(refund));
        assertEquals(0,d3BQuoteTest.f.db.queryForObject("SELECT COUNT(*) FROM order_return_check WHERE rental_order_id=? AND closed_at IS NOT NULL",Integer.class,order));
        // 向后移动业务时钟后换取同一夹具身份的新会话。
        d3BQuoteTest.f.adminCookie="mj_admin_dev="+d3BQuoteTest.f.sessions.create(d3BQuoteTest.f.admin,"admin").session().getId();
        d3BQuoteTest.f.csrf=d3BQuoteTest.f.call("GET","/api/admin/auth/csrf",null,null,null,200).path("csrfToken").asString();
        d3BQuoteTest.call("POST",factory+"/return-check/confirm",view("expectedVersion",1),d3BQuoteTest.supplier,d3BQuoteTest.key(),409);
        String close=factory+"/return-check/confirm-termination";
        d3BQuoteTest.call("POST",close,view("expectedVersion",1),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
        status(d3BQuoteTest,order,"terminated","processing");
        if(race){
          // 抢占第一次渠道提交与修订方案都要锁同一个订单根。
          String racingRefund=refund;
          body.put("expectedVersion",2);body.put("solutionSummary","入库后双方再次核对方案");
          HttpRequest request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(d3BQuoteTest.f.base+admin+"/resolution"))
            .header("Cookie",d3BQuoteTest.f.adminCookie).header("X-CSRF-Token",d3BQuoteTest.f.csrf).header("Content-Type","application/json").header("Idempotency-Key",d3BQuoteTest.key())
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(d3BQuoteTest.f.json.writeValueAsString(body))).build();
          CountDownLatch start=new java.util.concurrent.CountDownLatch(1);ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
          try{
            Future<RefundWorkTransaction.Ticket> claim=pool.submit(()->{start.await();return worker.claim(racingRefund);});
            Future<Integer> amend=pool.submit(()->{start.await();return d3BQuoteTest.f.http.send(request,java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode();});
            start.countDown();RefundWorkTransaction.Ticket ticket=claim.get();int code=amend.get();
            assertEquals(ticket==null?200:409,code);
            JsonNode actualPlan=d3BQuoteTest.call("GET",admin,null,null,null,200).path("plan");
            String actual=actualPlan.path("refunds").get(0).path("refundId").asString();
            if(ticket==null){assertNotEquals(racingRefund,actual);assertNotNull(worker.claim(actual));}else assertEquals(racingRefund,actual);
            assertEquals(1,d3BQuoteTest.f.db.queryForObject("SELECT COUNT(*) FROM refund_order WHERE rental_order_id=? AND refund_status!='cancelled'",Integer.class,order));
            assertEquals(1,d3BQuoteTest.f.db.queryForObject("SELECT COUNT(*) FROM order_deduction WHERE rental_order_id=? AND deduction_status='confirmed'",Integer.class,order));
          }finally{pool.shutdownNow();}return;
        }
        Map<String,Object> txSource=d3BQuoteTest.f.db.queryForMap("SELECT r.channel_out_trade_no,r.channel_transaction_id,s.paid_amount_fen FROM refund_order r JOIN payment_channel_suborder s ON s.id=r.payment_channel_suborder_id WHERE r.id=?",refund);
        Map<String,Object> channel=view("refund_id","isolated-refund","out_refund_no",refund,"out_trade_no",txSource.get("channel_out_trade_no"),
            "transaction_id",txSource.get("channel_transaction_id"),"status","PROCESSING","amount",view("refund",1,"total",txSource.get("paid_amount_fen"),"currency","CNY"));
        RefundExecution execution=new com.mujian.backend.payment.service.RefundExecution(worker,gateway,d3BQuoteTest.f.app.getBean(com.mujian.backend.payment.service.UnformedBatchRefunds.class));
        reply.set(json.writeValueAsString(channel));
        execution.execute(refund);
        assertEquals(1,requests.size());
        plan=d3BQuoteTest.call("GET",admin,null,null,null,200).path("plan");
        assertTrue(plan.path("locked").asBoolean());
        assertEquals("processing",plan.path("refunds").get(0).path("status").asString());
        assertEquals("executing",plan.path("executionStatus").asString());
        assertEquals(0,d3BQuoteTest.f.db.queryForObject("SELECT total_refunded_amount_fen FROM rental_order WHERE id=?",Integer.class,order));
        assertEquals(0,d3BQuoteTest.f.db.queryForObject("SELECT COUNT(*) FROM inventory_reservation WHERE rental_order_id=? AND reservation_status='renting'",Integer.class,order));
        d3BQuoteTest.f.clock.at=d3BQuoteTest.f.clock.at.plusSeconds(31);
        channel.put("status","ABNORMAL");
        reply.set(json.writeValueAsString(channel));
        execution.execute(refund);
        status(d3BQuoteTest,order,"terminated","failed");
        body.put("expectedVersion",2);
        d3BQuoteTest.call("POST",admin+"/resolution",body,null,d3BQuoteTest.key(),409);
        d3BQuoteTest.f.clock.at=d3BQuoteTest.f.clock.at.plusSeconds(301);
        execution=new com.mujian.backend.payment.service.RefundExecution(worker,gateway,d3BQuoteTest.f.app.getBean(com.mujian.backend.payment.service.UnformedBatchRefunds.class));
        channel.put("status","SUCCESS");
        channel.put("success_time",time(now(d3BQuoteTest.f.clock)));
        reply.set(json.writeValueAsString(channel));
        execution.execute(refund);
        execution.execute(refund);
        assertEquals(3,requests.size());
        assertTrue(requests.get(1).contains("/out-refund-no/"+refund));
        status(d3BQuoteTest,order,"terminated","partial_refunded");
        assertEquals("succeeded",d3BQuoteTest.call("GET",admin,null,null,null,200).path("plan").path("executionStatus").asString());
        assertEquals(1,d3BQuoteTest.f.db.queryForObject("SELECT total_refunded_amount_fen FROM rental_order WHERE id=?",Integer.class,order));
      }
    } finally {
      if (d3BQuoteTest.f.db!=null && d3BQuoteTest.factoryId!=null) {
        JdbcTemplate db=d3BQuoteTest.f.db;
        String scope=" JOIN admin_case c ON c.id=x.admin_case_id WHERE c.factory_id=? AND c.case_type='receipt_issue'";
        db.update("DELETE x FROM admin_case_action x"+scope+" AND x.parent_action_id IS NOT NULL",d3BQuoteTest.factoryId);
        for (String table:List.of("admin_operation_audit","admin_case_relation","admin_case_action")) db.update("DELETE x FROM "+table+" x"+scope,d3BQuoteTest.factoryId);
        db.update("DELETE FROM admin_case WHERE factory_id=? AND case_type='receipt_issue'",d3BQuoteTest.factoryId);
        for (String table:List.of("order_deduction","order_issue_item","order_issue_report"))
          db.update("DELETE x FROM "+table+" x JOIN rental_order o ON o.id=x.rental_order_id WHERE o.factory_id=?",d3BQuoteTest.factoryId);
      }
      setup.cleanup(d3BQuoteTest);
    }
  }

  /** 核对指定条件的记录数量。 */
  private static int count(D3BQuoteTest d3BQuoteTest,String table,String order) {
    return d3BQuoteTest.f.db.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE rental_order_id=?",Integer.class,order);
  }

  /** 读取当前业务状态供断言。 */
  private static void status(D3BQuoteTest d3BQuoteTest,String order,String status,String refund) throws Exception {
    JsonNode detail=d3BQuoteTest.call("GET","/api/me/rental-orders/"+order,null,d3BQuoteTest.buyer,null,200);
    JsonNode rows=d3BQuoteTest.call("GET","/api/me/rental-orders",null,d3BQuoteTest.buyer,null,200).path("items");
    JsonNode row=rows.valueStream().filter(r->order.equals(r.path("id").asString())).findFirst().orElseThrow();
    for (JsonNode source:List.of(row,detail)) {
      assertEquals(status,source.path("displayStatus").asString());
      assertEquals(refund,source.path("refundStatus").asString());
    }
  }
}
