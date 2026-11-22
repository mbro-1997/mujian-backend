package com.mujian.backend.d2a;

import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mujian.backend.dispute.service.ArbitrationRecovery;
import com.mujian.backend.common.support.Ids;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
/** 履约中裁决后，继续租赁及归还时都按原现金分摊核对。 */
class D5ArbitrationDepositHttpTest {
  /** 验证持续租用在实物归还前保留剩余资金。 */
  @Test void continuingRentalKeepsRemainderUntilPhysicalReturn() throws Exception {
    D4RecoveryHttpTest setup=new D4RecoveryHttpTest();D3BQuoteTest d3BQuoteTest=setup.fixture();d3BQuoteTest.extraSources=new Class<?>[0];
    try{
      d3BQuoteTest.start();d3BQuoteTest.f.db.update("UPDATE prop_sku SET deposit_amount_fen=25000 WHERE id=?",d3BQuoteTest.sku);
      String order=setup.dispatch(d3BQuoteTest),buyer="/api/me/rental-orders/"+order,factory="/api/me/factory-orders/"+order;
      String item=d3BQuoteTest.f.db.queryForObject("SELECT id FROM rental_order_item WHERE rental_order_id=?",String.class,order);
      Map<String,Object> evidence=view("evidenceIds",List.of(),"uploadReceiptIds",List.of());
      String issue=d3BQuoteTest.call("POST",buyer+"/issues",view("expectedVersion",0,"scene","receipt","effect","continue","primaryIssueType","damaged","summary","收货时外壳破损，继续使用",
        "items",List.of(view("itemId",item,"quantity",1,"issueType","damaged","description","外壳破损")),"evidence",evidence),d3BQuoteTest.buyer,d3BQuoteTest.key(),200).path("resourceId").asString();
      JsonNode detail=d3BQuoteTest.call("GET",buyer,null,d3BQuoteTest.buyer,null,200);
      String id=d3BQuoteTest.call("POST","/api/admin/rental-orders/"+order+"/arbitrations",view("expectedVersion",detail.path("version").asInt(),"sourceType","receipt_problem",
          "sourceId",issue,"summary","双方协商未果，继续租赁","evidence",evidence),null,d3BQuoteTest.key(),200).path("resourceId").asString();
      String path="/api/admin/arbitrations/"+id;
      Map<String,Object> v=view("expectedVersion",0,"responsibility","buyer","lossFen",10000,"rentDeductionFen",0,"refunds",view("rentFen",0,"depositFen",0,"freightFen",0),
          "stockOutcomes",List.of(),"summary","承担一百元损失，余下四百元随原合同保留");
      JsonNode draft=d3BQuoteTest.call("POST",path+"/decision-draft",v,null,d3BQuoteTest.key(),200);
      if(System.getenv("MJ_D5_NODE")!=null)D5ClaimStockHttpTest.pages(d3BQuoteTest,order,id,"ongoingDraft");
      assertEquals(50000,draft.path("depositBalance").path("paidFen").asInt());
      confirmAsTwoReviewers(d3BQuoteTest,path,view("expectedVersion",draft.path("version").asInt(),"decisionId",draft.path("decision").path("id").asString()));
      d3BQuoteTest.f.app.getBean(ArbitrationRecovery.class).next(id);
      assertEquals("closed",d3BQuoteTest.call("GET",path,null,null,null,200).path("status").asString());
      assertEquals(10000,d3BQuoteTest.f.db.queryForObject("SELECT deposit_deducted_amount_fen FROM rental_order WHERE id=?",Integer.class,order));
      assertEquals(0,d3BQuoteTest.f.db.queryForObject("SELECT COUNT(*) FROM refund_order WHERE rental_order_id=?",Integer.class,order));
      assertEquals(2,d3BQuoteTest.f.db.queryForObject("SELECT SUM(quantity) FROM inventory_reservation WHERE rental_order_id=? AND reservation_status='renting'",Integer.class,order));
      int version=d3BQuoteTest.call("GET",buyer+"/fulfillment",null,d3BQuoteTest.buyer,null,200).path("receipt").path("version").asInt();
      d3BQuoteTest.call("POST",buyer+"/receipt/confirm",view("expectedVersion",version),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
      d3BQuoteTest.call("POST","/api/admin/rental-orders/"+order+"/return-check/arrival",view("expectedVersion",0,"reason","实物已到厂"),null,d3BQuoteTest.key(),200);
      version=d3BQuoteTest.call("GET",factory+"/fulfillment",null,d3BQuoteTest.supplier,null,200).path("returnCheck").path("version").asInt();
      d3BQuoteTest.call("POST",factory+"/return-check/quantity",view("expectedVersion",version,"itemId",item,"quantity",2),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
      d3BQuoteTest.call("POST",factory+"/return-check/confirm",view("expectedVersion",version+1),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
      assertEquals(40000,d3BQuoteTest.f.db.queryForObject("SELECT deposit_refund_amount_fen FROM refund_order WHERE rental_order_id=?",Integer.class,order));
    }finally{d3BQuoteTest.stop();}
  }
  /** 以两名审核人竞争同一决定。 */
  private void confirmAsTwoReviewers(D3BQuoteTest d3BQuoteTest,String path,Object command) throws Exception {
    String firstCookie=d3BQuoteTest.f.adminCookie,firstCsrf=d3BQuoteTest.f.csrf;
    String name="d5."+Ids.next();
    JdbcTemplate db=d3BQuoteTest.f.db;
    d3BQuoteTest.f.app.getBean(com.mujian.backend.application.service.admin.AdminInitializationService.class)
      .initialize(name,UUID.randomUUID().toString());
    Map<String,Object> account=db.queryForMap("SELECT id,user_id FROM admin_account WHERE username=?",name);
    // D1 首期仅有 platform_admin。在独立库临时关闭决定按钮，可单独验证读权限。
    String button=db.queryForObject("SELECT id FROM admin_menu WHERE permission_code='admin:arbitration:decide' AND enabled=1",String.class);
    db.update("UPDATE admin_menu SET enabled=0 WHERE id=?",button);
    String secondCookie="mj_admin_dev="+d3BQuoteTest.f.sessions.create((String)account.get("user_id"),"admin").session().getId();
    String secondCsrf;
    try {
      d3BQuoteTest.f.adminCookie=secondCookie;
      secondCsrf=d3BQuoteTest.f.csrf=d3BQuoteTest.f.call("GET","/api/admin/auth/csrf",null,null,null,200).path("csrfToken").asString();
      assertTrue(d3BQuoteTest.call("GET",path,null,null,null,200).path("availableActions").isEmpty());
      d3BQuoteTest.call("POST",path+"/decision-confirm",command,null,d3BQuoteTest.key(),403);
    } finally {
      d3BQuoteTest.f.adminCookie=firstCookie;d3BQuoteTest.f.csrf=firstCsrf;
      db.update("UPDATE admin_menu SET enabled=1 WHERE id=?",button);
    }
    CountDownLatch gate=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
    try {
      Future<Integer> first=pool.submit(()->post(d3BQuoteTest,path,command,firstCookie,firstCsrf,gate));
      Future<Integer> second=pool.submit(()->post(d3BQuoteTest,path,command,secondCookie,secondCsrf,gate));
      gate.countDown();
      assertEquals(Set.of(200,409),new HashSet<>(List.of(first.get(),second.get())));
    } finally {pool.shutdownNow();}
  }
  /** 以测试身份提交请求。 */
  private int post(D3BQuoteTest d3BQuoteTest,String path,Object body,String cookie,String csrf,CountDownLatch gate) throws Exception {
    HttpRequest request=HttpRequest.newBuilder(URI.create(d3BQuoteTest.f.base+path+"/decision-confirm"))
      .header("Cookie",cookie).header("X-CSRF-Token",csrf).header("Content-Type","application/json")
      .header("Idempotency-Key",d3BQuoteTest.key()).POST(HttpRequest.BodyPublishers.ofString(d3BQuoteTest.f.json.writeValueAsString(body))).build();
    gate.await();return d3BQuoteTest.f.http.send(request,HttpResponse.BodyHandlers.discarding()).statusCode();
  }
}
