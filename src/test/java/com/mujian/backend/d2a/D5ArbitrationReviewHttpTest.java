package com.mujian.backend.d2a;

import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.order.service.TradingAccess;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** 总工复核只使用独立订单、可回收身份及原 HTTP 入口。 */
class D5ArbitrationReviewHttpTest {
  private static final String ADMIN = "/api/admin/arbitrations";

  /** 防止无效草稿或历史裁决向买家扣款。 */
  @Test void blockedDraftAndPersistedLegacyDecisionCannotChargeBuyer() throws Exception {
    D3BQuoteTest d3BQuoteTest = new D4RecoveryHttpTest().fixture();
    d3BQuoteTest.extraSources = new Class<?>[0];
    try {
      d3BQuoteTest.start();
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET deposit_amount_fen=25000 WHERE id=?", d3BQuoteTest.sku);
      String order = new D4RecoveryHttpTest().dispatch(d3BQuoteTest), id = open(d3BQuoteTest, order);
      String path = ADMIN + "/" + id;
      prepareNextChecklist(d3BQuoteTest);
      for (String party : List.of("factory", "platform", "unknown")) {
        d3BQuoteTest.call("POST", path + "/decision-draft", draft(0, party, 80000, 0, 0), null, d3BQuoteTest.key(), 400);
        d3BQuoteTest.call("POST", path + "/decision-draft", draft(0, party, 0, 1, 0), null, d3BQuoteTest.key(), 400);
        assertTrue(get(d3BQuoteTest,path).path("decision").isNull());
        noBuyerCharge(d3BQuoteTest,order);
      }
      JsonNode saved = d3BQuoteTest.call("POST",path+"/decision-draft",draft(0,"shared",80000,0,0),null,d3BQuoteTest.key(),200);
      String decision = saved.path("decision").path("id").asString();
      Map<String,Object> confirm = view("expectedVersion",1,"decisionId",decision);
      // 重放旧版本可保存的不一致数据，再从原确认入口直接使用该草稿。
      for (String party : List.of("factory","platform","unknown")) {
        d3BQuoteTest.f.db.update("UPDATE arbitration_decision SET responsibility_party=? WHERE id=?",party,decision);
        d3BQuoteTest.call("POST",path+"/decision-confirm",confirm,null,d3BQuoteTest.key(),409);
        assertEquals("SOURCE_ACTION_REQUIRED",d3BQuoteTest.f.lastReason);
        assertEquals("draft",get(d3BQuoteTest,path).path("decision").path("status").asString());
        assertEquals(0,count(d3BQuoteTest,"SELECT COUNT(*) FROM admin_case_action WHERE target_id=? AND action_type='execute_arbitration'",decision));
        noBuyerCharge(d3BQuoteTest,order);
      }
      if(System.getenv("MJ_D5_NODE")!=null)D5ClaimStockHttpTest.pages(d3BQuoteTest,order,id,"invalidDecision");
      String nextBatch="/api/me/checkout-batches/"+d3BQuoteTest.submitQuoted(d3BQuoteTest.buyer,d3BQuoteTest.input);
      assertTrue(d3BQuoteTest.call("GET",nextBatch,null,d3BQuoteTest.buyer,null,200).path("canContinuePay").asBoolean());
      JsonNode corrected=d3BQuoteTest.call("POST",path+"/decision-draft",draft(1,"shared",70234,0,0),null,d3BQuoteTest.key(),200);
      d3BQuoteTest.call("POST",path+"/decision-confirm",view("expectedVersion",2,"decisionId",corrected.path("decision").path("id").asString()),null,d3BQuoteTest.key(),200);
      assertEquals(50000,count(d3BQuoteTest,"SELECT deposit_deducted_amount_fen FROM rental_order WHERE id=?",order));
      assertFalse(d3BQuoteTest.call("GET",nextBatch,null,d3BQuoteTest.buyer,null,200).path("canContinuePay").asBoolean());
      assertEquals(20234,count(d3BQuoteTest,"SELECT receivable_amount_fen FROM order_compensation_receivable WHERE rental_order_id=?",order));
      d3BQuoteTest.call("POST","/api/me/checkout-quotes",d3BQuoteTest.input,d3BQuoteTest.buyer,null,409);
      assertEquals("COMPENSATION_OUTSTANDING",d3BQuoteTest.f.lastReason);
    } finally { d3BQuoteTest.stop(); }
  }

  /** 验证买方无新增扣款。 */
  private void noBuyerCharge(D3BQuoteTest d3BQuoteTest,String order) throws Exception {
    assertEquals(0,count(d3BQuoteTest,"SELECT COUNT(*) FROM order_deduction WHERE rental_order_id=?",order));
    assertEquals(0,count(d3BQuoteTest,"SELECT deposit_deducted_amount_fen FROM rental_order WHERE id=?",order));
    assertEquals(0,count(d3BQuoteTest,"SELECT COUNT(*) FROM order_compensation_receivable WHERE rental_order_id=?",order));
    d3BQuoteTest.f.app.getBean(TradingAccess.class).newTrade(d3BQuoteTest.buyer.id());
    d3BQuoteTest.call("POST","/api/me/checkout-quotes",d3BQuoteTest.input,d3BQuoteTest.buyer,null,200);
  }

  /** 准备后续用例所需的可编辑清单。 */
  private void prepareNextChecklist(D3BQuoteTest d3BQuoteTest) throws Exception {
    String date=now(d3BQuoteTest.f.clock).toLocalDate().plusDays(12).toString();
    String list=d3BQuoteTest.call("POST","/api/me/checklists",view("sourceType","manual","startDate",date,"endDate",date),d3BQuoteTest.buyer,d3BQuoteTest.key(),200).path("resourceId").asString();
    d3BQuoteTest.call("POST","/api/me/checklists/"+list+"/items",view("expectedVersion",0,"skuId",d3BQuoteTest.sku,"quantity",1,"remark",null),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
    d3BQuoteTest.input.put("checklistId",list);
    d3BQuoteTest.input.put("couponId",null);
    d3BQuoteTest.coupon=null;
  }

  /** 构造可继续提交的草稿。 */
  private static Map<String,Object> draft(int version,String party,int loss,int rent,int refund) {
    return view("expectedVersion",version,"responsibility",party,"lossFen",loss,"rentDeductionFen",rent,
      "refunds",view("rentFen",0,"depositFen",refund,"freightFen",0),"refundBasis","按原押金实收记录退还",
      "stockOutcomes",List.of(),"summary","按双方签认的维修清单核定，其中买家份额 "+loss+" 分");
  }

  /** 发起本场景的业务申请。 */
  private static String open(D3BQuoteTest d3BQuoteTest,String order) throws Exception {
    String buyer="/api/me/rental-orders/"+order;
    String item=d3BQuoteTest.f.db.queryForObject("SELECT id FROM rental_order_item WHERE rental_order_id=?",String.class,order);
    Map<String,Object> evidence=view("evidenceIds",List.of(),"uploadReceiptIds",List.of());
    String issue=d3BQuoteTest.call("POST",buyer+"/issues",view("expectedVersion",0,"scene","receipt","effect","continue","primaryIssueType","damaged",
        "summary","收货时发现损坏","items",List.of(view("itemId",item,"quantity",1,"issueType","damaged","description","损坏")),"evidence",evidence),d3BQuoteTest.buyer,d3BQuoteTest.key(),200).path("resourceId").asString();
    int version=d3BQuoteTest.call("GET",buyer,null,d3BQuoteTest.buyer,null,200).path("version").asInt();
    return d3BQuoteTest.call("POST","/api/admin/rental-orders/"+order+"/arbitrations",view("expectedVersion",version,"sourceType","receipt_problem",
        "sourceId",issue,"summary","请核实责任","evidence",evidence),null,d3BQuoteTest.key(),200).path("resourceId").asString();
  }

  /** 检查履约分页的金额、证据范围和 SQL 次数。 */
  @Test void stagesPagingMoneyAndEvidenceStayScopedWithBoundedSql() throws Exception {
    D4RecoveryHttpTest setup=new D4RecoveryHttpTest();
    D3BQuoteTest d3BQuoteTest=setup.fixture();
    d3BQuoteTest.extraSources=new Class<?>[0];
    try {
      d3BQuoteTest.start();
      D2ApplicationTest.User otherFactory=d3BQuoteTest.fixture.supplier();
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET deposit_amount_fen=25000 WHERE id=?", d3BQuoteTest.sku);
      String order=setup.dispatch(d3BQuoteTest), decided=open(d3BQuoteTest,order);
      JdbcTemplate db=d3BQuoteTest.f.db;
      // 为分页准备已存在的候审/撤销历史。资金、私有证据和裁决使用正式写入入口。
      for(int i=0;i<22;i++) {
        String id=Ids.next();
        db.update("""
          INSERT INTO arbitration_case(id,case_no,rental_order_id,case_type,case_status,version,opened_by_user_id,opened_role,summary,opened_at,created_at,updated_at)
          VALUES(?,?,?,'admin_intervention',?,0,?,'admin','隔离历史案件',NOW(3),NOW(3),NOW(3))
          """,id,"ARB"+id,order,i==21?"cancelled":"open",d3BQuoteTest.f.admin);
      }
      String buyerProof=attach(d3BQuoteTest,order,decided,"buyer",0);
      String factoryProof=attach(d3BQuoteTest,order,decided,"factory",2);
      independentRefund(d3BQuoteTest,decided,"factory",4,107);
      prepareNextChecklist(d3BQuoteTest);
      String second=setup.dispatch(d3BQuoteTest), failed=open(d3BQuoteTest,second);
      independentRefund(d3BQuoteTest,failed,"platform",0,53);
      db.update("UPDATE arbitration_case SET case_status='execution_failed' WHERE id=?",failed);
      db.update("""
        UPDATE admin_case_action p JOIN arbitration_decision d ON d.id=p.target_id
        SET p.action_status='failed',p.failure_message='渠道待恢复'
        WHERE p.action_type='execute_arbitration' AND d.arbitration_case_id=?
        """,failed);

      JsonNode all=get(d3BQuoteTest,ADMIN+"?orderId="+order+"&pageSize=100");
      assertEquals(22,all.path("total").asInt());
      assertEquals(21,all.path("items").valueStream().filter(r->"awaiting_decision".equals(r.path("decisionStage").asString())).count());
      JsonNode first=measured(d3BQuoteTest,ADMIN+"?orderId="+order+"&decisionStage=awaiting_decision");
      assertEquals(20,first.path("items").size());
      JsonNode last=get(d3BQuoteTest,ADMIN+"?orderId="+order+"&decisionStage=awaiting_decision&page=2&pageSize=20");
      assertEquals(1,last.path("items").size());
      assertTrue(first.path("items").valueStream().noneMatch(r->r.path("id").equals(last.path("items").get(0).path("id"))));
      String button=db.queryForObject("SELECT id FROM admin_menu WHERE permission_code='admin:arbitration:decide' AND enabled=1",String.class);
      db.update("UPDATE admin_menu SET enabled=0 WHERE id=?",button);
      try {
        JsonNode readOnly=measured(d3BQuoteTest,ADMIN+"?orderId="+order+"&decisionStage=awaiting_decision");
        for(JsonNode row:readOnly.path("items"))assertTrue(row.path("availableActions").isEmpty());
      } finally {db.update("UPDATE admin_menu SET enabled=1 WHERE id=?",button);}
      JsonNode history=get(d3BQuoteTest,ADMIN+"?orderId="+order+"&decisionStage=historical").path("items");
      assertEquals(1,history.size()); assertEquals("historical",history.get(0).path("decisionStage").asString());

      JsonNode done=measured(d3BQuoteTest,ADMIN+"?decisionStage=decided").path("items");
      for(String id:List.of(decided,failed)) {
        JsonNode r=done.valueStream().filter(v->v.path("id").asString().equals(id)).findFirst().orElseThrow();
        assertEquals(get(d3BQuoteTest,ADMIN+"/"+id),r);
        assertEquals("decided",r.path("decisionStage").asString());
        assertEquals(id.equals(failed)?"execution_failed":"executing",r.path("executionStatus").asString());
        if(id.equals(failed))assertEquals("渠道待恢复",r.path("failureReason").asString());
        int amount=id.equals(failed)?53:107;
        assertEquals(amount,r.path("refunds").get(0).path("amountFen").asInt());
        assertEquals(amount,r.path("depositBalance").path("reservedFen").asInt());
        assertEquals(r.path("refunds").get(0).path("refundId"),r.path("refundIds").get(0));
        assertEquals(id.equals(failed)?0:2,r.path("evidenceIds").size());
      }
      for(boolean factory:List.of(false,true)){
        D2ApplicationTest.User user=factory?d3BQuoteTest.supplier:d3BQuoteTest.buyer;
        String path=factory?"/api/me/factory-arbitrations":"/api/me/arbitrations";
        JsonNode items=d3BQuoteTest.call("GET",path+"?orderId="+order+"&decisionStage=decided",null,user,null,200).path("items");
        assertEquals(1,items.size());
        assertEquals(1,items.get(0).path("evidenceIds").size());
        assertEquals(factory?factoryProof:buyerProof,items.get(0).path("evidenceIds").get(0).asString());
        assertEquals(items.get(0),d3BQuoteTest.call("GET",path+"/"+decided,null,user,null,200));
        assertTrue(items.get(0).path("stockOptions").isEmpty());
      }
      assertTrue(d3BQuoteTest.call("GET","/api/me/factory-arbitrations?orderId="+order,null,otherFactory,null,200).path("items").isEmpty());
      assertTrue(d3BQuoteTest.call("GET","/api/me/arbitrations?orderId="+order,null,d3BQuoteTest.f.user(),null,200).path("items").isEmpty());
      d3BQuoteTest.call("GET","/api/me/factory-arbitrations/"+decided,null,otherFactory,null,404);
      if(System.getenv("MJ_D5_NODE")!=null){
        D5ClaimStockHttpTest.pages(d3BQuoteTest,order,decided,"arbitrationStages");
        D5ClaimStockHttpTest.pages(d3BQuoteTest,second,failed,"arbitrationFailure");
      }
    } finally {d3BQuoteTest.stop();}
  }

  /** 关联本场景所需凭证。 */
  private String attach(D3BQuoteTest d3BQuoteTest,String order,String id,String party,int version) throws Exception {
    String path=ADMIN+"/"+id;
    d3BQuoteTest.call("POST",path+"/request-evidence",view("expectedVersion",version,"party",party,"reason","请提供现场照片"),null,d3BQuoteTest.key(),200);
    boolean buyer=party.equals("buyer");
    D2ApplicationTest.User actor=buyer?d3BQuoteTest.buyer:d3BQuoteTest.supplier;
    String receipt=D4EvidenceHttpTest.upload(d3BQuoteTest,(buyer?"/api/me/rental-orders/":"/api/me/factory-orders/")+order,"arbitration",actor,200);
    String own=(buyer?"/api/me/arbitrations/":"/api/me/factory-arbitrations/")+id;
    d3BQuoteTest.call("POST",own+"/supplement",view("expectedVersion",version+1,"summary","已附现场照片","evidence",view("evidenceIds",List.of(),"uploadReceiptIds",List.of(receipt))),actor,d3BQuoteTest.key(),200);
    return d3BQuoteTest.call("GET",own,null,actor,null,200).path("evidenceIds").get(0).asString();
  }

  /** 构造独立退款来源。 */
  private void independentRefund(D3BQuoteTest d3BQuoteTest,String id,String party,int version,int fen) throws Exception {
    String path=ADMIN+"/"+id;
    JsonNode saved=d3BQuoteTest.call("POST",path+"/decision-draft",draft(version,party,0,0,fen),null,d3BQuoteTest.key(),200);
    d3BQuoteTest.call("POST",path+"/decision-confirm",view("expectedVersion",version+1,"decisionId",saved.path("decision").path("id").asString()),null,d3BQuoteTest.key(),200);
    JsonNode r=get(d3BQuoteTest,path);
    assertEquals(0,r.path("decision").path("depositDeductionFen").asInt());
    assertEquals(0,r.path("decision").path("receivableFen").asInt());
    assertEquals(0,count(d3BQuoteTest,"SELECT COUNT(*) FROM order_deduction WHERE rental_order_id=?",r.path("orderId").asString()));
    assertEquals(0,count(d3BQuoteTest,"SELECT COUNT(*) FROM order_compensation_receivable WHERE rental_order_id=?",r.path("orderId").asString()));
  }

  /** 采集被测操作的调用开销。 */
  private JsonNode measured(D3BQuoteTest d3BQuoteTest,String path) throws Exception {
    D2ApplicationTest.Fault fault=d3BQuoteTest.f.app.getBean(D2ApplicationTest.Fault.class);
    AtomicInteger counter=fault.sqlCalls;
    int permissionsBefore=fault.adminPermissionReads.get();
    int before=counter.get();
    JsonNode one=get(d3BQuoteTest,path+"&pageSize=1");
    int single=counter.get()-before;
    assertEquals(1,fault.adminPermissionReads.get()-permissionsBefore);
    permissionsBefore=fault.adminPermissionReads.get();
    before=counter.get();
    JsonNode more=get(d3BQuoteTest,path+"&pageSize=20");
    int multi=counter.get()-before;
    assertEquals(1,fault.adminPermissionReads.get()-permissionsBefore);
    assertEquals(1,one.path("items").size());
    assertTrue(more.path("items").size()>1);
    assertEquals(one.path("items").get(0),more.path("items").get(0));
    assertTrue(single>0); assertEquals(single,multi,"执行的 SQL 条数不应随行数增加");
    System.out.printf("R3 %s: 1 row / %d rows = %d / %d SQL%n",path.split("\\?")[0],more.path("items").size(),single,multi);
    return more;
  }

  /** 检查索赔审核权限只读一次且隔离可见范围。 */
  @Test void claimReviewPermissionsReadOnceAndStayPrivate() throws Exception {
    D4RecoveryHttpTest setup=new D4RecoveryHttpTest();D3BQuoteTest d3BQuoteTest=setup.fixture();d3BQuoteTest.extraSources=new Class<?>[0];
    try {
      d3BQuoteTest.start();
      D2ApplicationTest.User otherFactory=d3BQuoteTest.fixture.supplier();
      Map<String,Integer> claims=new HashMap<>();
      Map<String,String> orders=new HashMap<>();
      for(int amount:List.of(735,291)) {
        if(!claims.isEmpty())prepareNextChecklist(d3BQuoteTest);
        String order=setup.dispatch(d3BQuoteTest),factory="/api/me/factory-orders/"+order;
        d3BQuoteTest.call("POST","/api/me/rental-orders/"+order+"/receipt/confirm",view("expectedVersion",0),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
        d3BQuoteTest.call("POST","/api/admin/rental-orders/"+order+"/return-check/arrival",view("expectedVersion",0,"reason","道具到厂"),null,d3BQuoteTest.key(),200);
        String item=d3BQuoteTest.f.db.queryForObject("SELECT id FROM rental_order_item WHERE rental_order_id=?",String.class,order);
        int version=count(d3BQuoteTest,"SELECT version FROM order_return_check WHERE rental_order_id=?",order);
        String proof=D4EvidenceHttpTest.upload(d3BQuoteTest,factory,"claim",d3BQuoteTest.supplier,200);
        String claim=d3BQuoteTest.call("POST",factory+"/claims",view("expectedVersion",version,"problemType","damaged","claimedFen",amount,"summary","核对道具损失",
            "items",List.of(view("itemId",item,"issueType","damaged","quantity",1,"description","外壳破损")),
            "evidence",view("evidenceIds",List.of(),"uploadReceiptIds",List.of(proof))),d3BQuoteTest.supplier,d3BQuoteTest.key(),200).path("resourceId").asString();
        d3BQuoteTest.call("POST","/api/me/factory-claims/"+claim+"/submit",view("expectedVersion",0),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
        claims.put(claim,amount); orders.put(claim,order);
      }
      String path="/api/admin/claims?status=admin_reviewing";
      JsonNode list=measured(d3BQuoteTest,path);
      HashSet<String> evidenceIds=new HashSet<String>();
      for(JsonNode row:list.path("items")) {
        String id=row.path("id").asString();if(!claims.containsKey(id))continue;
        assertEquals(get(d3BQuoteTest,"/api/admin/claims/"+id),row);
        assertEquals(claims.get(id),row.path("claimedFen").asInt());
        assertEquals("review",row.path("availableActions").get(0).asString());
        assertTrue(evidenceIds.add(row.path("evidenceIds").get(0).asString()));
        assertTrue(d3BQuoteTest.call("GET","/api/me/factory-claims?orderId="+orders.get(id),null,otherFactory,null,200).path("items").isEmpty());
        assertTrue(d3BQuoteTest.call("GET","/api/me/claims?orderId="+orders.get(id),null,d3BQuoteTest.buyer,null,200).path("items").isEmpty());
      }
      assertEquals(2,evidenceIds.size());
      JdbcTemplate db=d3BQuoteTest.f.db;
      String permission=db.queryForObject("SELECT id FROM admin_menu WHERE permission_code='admin:claim:review' AND enabled=1",String.class);
      db.update("UPDATE admin_menu SET enabled=0 WHERE id=?",permission);
      try {
        for(JsonNode row:measured(d3BQuoteTest,path).path("items"))assertTrue(row.path("availableActions").isEmpty());
      } finally {db.update("UPDATE admin_menu SET enabled=1 WHERE id=?",permission);}
      String claim=claims.keySet().iterator().next();
      d3BQuoteTest.call("POST","/api/admin/claims/"+claim+"/review",view("expectedVersion",1,"decision","approve",
          "lossFen",claims.get(claim),"remark","按实际损失核定"),null,d3BQuoteTest.key(),200);
      d3BQuoteTest.call("POST","/api/me/claims/"+claim+"/confirm",view("expectedVersion",2),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
      assertEquals("executing",get(d3BQuoteTest,"/api/admin/claims/"+claim).path("status").asString());
      if(System.getenv("MJ_D5_NODE")!=null)D5ClaimStockHttpTest.pages(d3BQuoteTest,orders.get(claim),claim,"ordinaryClaimStatus");
    } finally {d3BQuoteTest.stop();}
  }

  /** 读取指定测试资源。 */
  private static JsonNode get(D3BQuoteTest d3BQuoteTest,String path) throws Exception {
    return d3BQuoteTest.call("GET",path,null,null,null,200);
  }
  /** 核对指定条件的记录数量。 */
  private static int count(D3BQuoteTest d3BQuoteTest,String sql,String id) {
    return d3BQuoteTest.f.db.queryForObject(sql,Integer.class,id);
  }
}
