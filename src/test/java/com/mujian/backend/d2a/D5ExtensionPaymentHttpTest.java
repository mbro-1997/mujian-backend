package com.mujian.backend.d2a;

import tools.jackson.databind.JsonNode;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mujian.backend.infrastructure.payment.wechat.SignedWechatHttpFixture;
import com.mujian.backend.order.service.ExtensionPayment;
import com.mujian.backend.order.service.OrderExtensions;
import com.mujian.backend.payment.service.PaymentExecution;
import com.mujian.backend.payment.service.RefundExecution;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.net.*;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class D5ExtensionPaymentHttpTest extends SignedWechatHttpFixture {
  /** 验证截点付款迟到与提醒后取消。 */
  @Test void cutoffPaymentReceivedLateAndBuyerCancelsAfterWarning() throws Exception {exercise("cancel");}
  /** 验证超过截点的付款原路退回。 */
  @Test void paymentOutsideCutoffIsRefunded() throws Exception {exercise("late");}
  /** 验证批准与买家取消的竞争。 */
  @Test void approvalRacesBuyerCancellation() throws Exception {exercise("race");}
  /** 验证拒绝与取消竞争只退一次款。 */
  @Test void rejectionRacesCancellationRefundsOnlyOnce() throws Exception {exercise("rejectRace");}
  /** 验证截止瞬间仅允许自动批准。 */
  @Test void exactDeadlineOnlyAllowsAutomaticApproval() throws Exception {exercise("auto");}

  /** 通过工厂二级列表批准已付款延期。 */
  @Test void factoryApprovesPaidExtensionFromSecondaryList() throws Exception {exercise("factory");}
  /** 按指定分支执行完整测试流程。 */
  private void exercise(String scenario) throws Exception {
    D4RecoveryHttpTest setup=new D4RecoveryHttpTest();
    D3BQuoteTest d3BQuoteTest=setup.fixture(); d3BQuoteTest.extraSources=new Class<?>[0];
    try {
      d3BQuoteTest.start();
      PaymentExecution payment=d3BQuoteTest.f.app.getBean(PaymentExecution.class);
      ExtensionPayment command=d3BQuoteTest.f.app.getBean(ExtensionPayment.class);
      RefundExecution refunds=d3BQuoteTest.f.app.getBean(RefundExecution.class);
      ReflectionTestUtils.setField(command,"gateway",gateway);
      ReflectionTestUtils.setField(payment,"paymentGateway",gateway);
      ReflectionTestUtils.setField(refunds,"paymentGateway",gateway);
      D2ApplicationTest.User other=d3BQuoteTest.fixture.supplier();
      String order=setup.dispatch(d3BQuoteTest,d3BQuoteTest.paidOrder(account.mchId()));
      String base="/api/me/rental-orders/"+order;
      d3BQuoteTest.call("POST",base+"/receipt/confirm",view("expectedVersion",0),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
      JsonNode quote=d3BQuoteTest.call("GET",base+"/extension-quote?extraDays=2",null,d3BQuoteTest.buyer,null,200);
      Instant cutoff=OffsetDateTime.parse(quote.path("latestPaidAt").asString()).toInstant();
      d3BQuoteTest.f.clock.at=cutoff.minusSeconds(90);
      Map<String,Object> body=view("expectedVersion",quote.path("orderVersion").asInt(),"extraDays",2,"remark","需多使用两天");
      reply.set("{\"prepay_id\":\"isolated-extension-prepay\"}");
      String key=d3BQuoteTest.key();
      JsonNode result=d3BQuoteTest.call("POST",base+"/extensions",body,d3BQuoteTest.buyer,key,200);
      assertEquals("pay",result.path("nextAction").asString());
      String id=result.path("resourceId").asString(),pid=result.path("payment").path("id").asString();
      String own="/api/me/extensions/"+id, factory="/api/me/factory-extensions/"+id;
      assertEquals(id,d3BQuoteTest.call("POST",base+"/extensions",body,d3BQuoteTest.buyer,key,200).path("resourceId").asString());
      if(scenario.equals("cancel")){
        d3BQuoteTest.f.db.update("UPDATE sys_user SET user_status='disabled' WHERE id=?",d3BQuoteTest.buyer.id());
        try{
          d3BQuoteTest.call("POST",base+"/extensions",body,d3BQuoteTest.buyer,key,403);
          d3BQuoteTest.call("POST",own+"/pay",view("expectedVersion",0),d3BQuoteTest.buyer,d3BQuoteTest.key(),403);
        }finally{d3BQuoteTest.f.db.update("UPDATE sys_user SET user_status='active' WHERE id=?",d3BQuoteTest.buyer.id());}
      }
      assertEquals("pending_payment",d3BQuoteTest.call("GET",own,null,d3BQuoteTest.buyer,null,200).path("extensionStatus").asString());
      d3BQuoteTest.call("GET",factory,null,d3BQuoteTest.supplier,null,404);
      Map<String,Object> sub=d3BQuoteTest.f.db.queryForMap("SELECT id,sub_mchid_snapshot,channel_sub_order_no,channel_suborder_amount_fen FROM payment_channel_suborder WHERE payment_order_id=?",pid);
      Instant paid="late".equals(scenario)?cutoff.plusMillis(1):cutoff;
      Map<String,Object> fact=view("combine_appid","test-app","combine_mchid",account.mchId(),"combine_out_trade_no",pid,
          "sub_orders",List.of(view("out_trade_no",sub.get("channel_sub_order_no"),"mchid",account.mchId(),"sub_mchid",sub.get("sub_mchid_snapshot"),
          "transaction_id","wx-"+id,"trade_state","SUCCESS","success_time",paid.toString(),"amount",view("total_amount",sub.get("channel_suborder_amount_fen"),"currency","CNY"))));
      reply.set(json.writeValueAsString(fact));
      d3BQuoteTest.f.clock.at=cutoff.plusSeconds(10);
      payment.execute(pid);
      JsonNode after=d3BQuoteTest.call("GET",own,null,d3BQuoteTest.buyer,null,200);
      String oldDue=quote.path("originalDueDate").asString(),newDue=quote.path("requestedDueDate").asString();
      String state=after.path("extensionStatus").asString();
      if(scenario.equals("late"))assertEquals("cancelled",state);
      else {
        assertEquals("pending_factory",state);
        d3BQuoteTest.call("GET",factory,null,other,null,404);
        d3BQuoteTest.call("POST",factory+"/approve",view("expectedVersion",1),other,d3BQuoteTest.key(),404);
        OrderExtensions lifecycle=d3BQuoteTest.f.app.getBean(OrderExtensions.class);
        d3BQuoteTest.f.clock.at=OffsetDateTime.parse(after.path("factoryWarningAt").asString()).toInstant();
        lifecycle.recover(id);
        after=d3BQuoteTest.call("GET",own,null,d3BQuoteTest.buyer,null,200);
        assertEquals("factory_warning",after.path("extensionStatus").asString());
        Map<String,Object> decision=view("expectedVersion",after.path("version").asInt());
        if(scenario.equals("cancel")) {
          if(System.getenv("MJ_D5_NODE")!=null)D5ClaimStockHttpTest.pages(d3BQuoteTest,order,id,"buyerCancel");
          else {key=d3BQuoteTest.key();d3BQuoteTest.call("POST",own+"/cancel",decision,d3BQuoteTest.buyer,key,200);d3BQuoteTest.call("POST",own+"/cancel",decision,d3BQuoteTest.buyer,key,200);}
          d3BQuoteTest.call("POST",factory+"/approve",decision,d3BQuoteTest.supplier,d3BQuoteTest.key(),409);
        } else if(scenario.equals("factory")){
          if(System.getenv("MJ_D5_NODE")!=null)D5ClaimStockHttpTest.pages(d3BQuoteTest,order,id,"factoryApprove");
          else d3BQuoteTest.call("POST",factory+"/approve",decision,d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
        } else if(Set.of("race","rejectRace").contains(scenario)) {
          CountDownLatch gate=new CountDownLatch(1);
          ExecutorService threads=Executors.newFixedThreadPool(2);
          try {
            Future<Integer> a=threads.submit(()->post(d3BQuoteTest,own+"/cancel",decision,d3BQuoteTest.buyer,gate));
            boolean reject=scenario.equals("rejectRace");
            Future<Integer> b=threads.submit(()->post(d3BQuoteTest,factory+(reject?"/reject":"/approve"),reject?view("expectedVersion",decision.get("expectedVersion"),"reason","下笔订单已预约"):decision,d3BQuoteTest.supplier,gate));
            gate.countDown();assertEquals(Set.of(200,409),Set.of(a.get(),b.get()));
          } finally {threads.shutdownNow();}
        } else {
          d3BQuoteTest.f.clock.at=OffsetDateTime.parse(after.path("factoryDeadlineAt").asString()).toInstant();
          d3BQuoteTest.call("POST",own+"/cancel",decision,d3BQuoteTest.buyer,d3BQuoteTest.key(),409);
          d3BQuoteTest.call("POST",factory+"/approve",decision,d3BQuoteTest.supplier,d3BQuoteTest.key(),409);
          lifecycle.recover(id);lifecycle.recover(id);
        }
        state=d3BQuoteTest.call("GET",own,null,d3BQuoteTest.buyer,null,200).path("extensionStatus").asString();
      }
      boolean approved="approved".equals(state);
      if(Set.of("auto","factory").contains(scenario))assertTrue(approved);
      assertEquals(approved?newDue:oldDue,d3BQuoteTest.call("GET",base,null,d3BQuoteTest.buyer,null,200).path("disputes").path("currentDueDate").asString());
      assertEquals(0,d3BQuoteTest.f.db.queryForObject("SELECT COUNT(*) FROM inventory_reservation WHERE source_id=? AND reservation_status IN ('held','paid_locked')",Integer.class,id));
      assertEquals(1,d3BQuoteTest.f.db.queryForObject("SELECT COUNT(*) FROM payment_allocation WHERE payment_order_id=?",Integer.class,pid));
      assertEquals(approved?0:1,d3BQuoteTest.f.db.queryForObject("SELECT COUNT(*) FROM refund_order WHERE payment_order_id=?",Integer.class,pid));
      if ("factory".equals(scenario)) {
        d3BQuoteTest.f.clock.at=java.time.LocalDate.parse(oldDue).plusDays(1).atTime(12,0).atZone(ZONE).toInstant();
        d3BQuoteTest.buyer=new D2ApplicationTest.User(d3BQuoteTest.buyer.id(),d3BQuoteTest.f.sessions.create(d3BQuoteTest.buyer.id(),"miniapp").session().getId());
        d3BQuoteTest.call("POST",base+"/return-shipment/send",view("expectedVersion",0,"sentAt",time(now(d3BQuoteTest.f.clock)),
            "trackingNo","YT-EXT-12","carrierName",null,"remark","生效延期内实际交运","handoverMethod","carrier"),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
        tools.jackson.databind.JsonNode fee=d3BQuoteTest.call("GET",base,null,d3BQuoteTest.buyer,null,200).path("disputes").path("overdueFee");
        assertEquals(newDue,fee.path("dueDate").asString());assertEquals("effective",fee.path("status").asString());
        assertEquals(0,fee.path("days").asInt());assertEquals(0,fee.path("rentFen").asInt());
      }
      if(!approved) {
        String refund=d3BQuoteTest.f.db.queryForObject("SELECT id FROM refund_order WHERE payment_order_id=?",String.class,pid);
        reply.set(json.writeValueAsString(view("refund_id","wx-refund-"+id,"out_refund_no",refund,"out_trade_no",sub.get("channel_sub_order_no"),
          "transaction_id","wx-"+id,"status","SUCCESS","success_time",time(now(d3BQuoteTest.f.clock)),
          "amount",view("refund",sub.get("channel_suborder_amount_fen"),"total",sub.get("channel_suborder_amount_fen"),"currency","CNY"))));
        refunds.execute(refund);refunds.execute(refund);
        assertEquals("success",d3BQuoteTest.call("GET",own,null,d3BQuoteTest.buyer,null,200).path("refund").path("status").asString());
      }
    } finally {setup.cleanup(d3BQuoteTest);}
  }
  /** 以测试身份提交请求。 */
  private int post(D3BQuoteTest d3BQuoteTest,String path,Object body,D2ApplicationTest.User user,CountDownLatch gate) throws Exception {
    gate.await();
    HttpRequest request=HttpRequest.newBuilder(URI.create(d3BQuoteTest.f.base+path)).header("Authorization","Bearer "+user.token()).header("Content-Type","application/json")
       .header("Idempotency-Key",d3BQuoteTest.key()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
    return d3BQuoteTest.f.http.send(request,HttpResponse.BodyHandlers.ofString()).statusCode();
  }
}
