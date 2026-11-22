package com.mujian.backend.d2a;

import com.mujian.backend.delivery.service.DisputeGuard;
import com.mujian.backend.delivery.service.ReturnClosure;
import com.mujian.backend.dispute.service.ArbitrationRecovery;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.payment.mapper.PaymentNotificationMapper;
import com.mujian.backend.payment.service.PaymentExecution;
import com.mujian.backend.payment.service.PaymentNotificationRecovery;
import com.mujian.backend.payment.service.RefundExecution;
import com.mujian.backend.payment.service.RefundWorkTransaction;
import com.mujian.backend.settlement.job.SettlementRecovery;
import com.mujian.backend.settlement.mapper.SharingMapper;
import com.mujian.backend.settlement.service.SettlementGeneration;
import java.io.OutputStream;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;
import com.mujian.backend.delivery.service.FulfillmentRecovery;
import com.mujian.backend.dispute.service.ClaimService;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class D5ClaimStockHttpTest extends com.mujian.backend.infrastructure.payment.wechat.SignedWechatHttpFixture {
  /** 确认索赔后按核定数量恢复库存。 */
  @Test void confirmAndResumePartialStock() throws Exception {exercise("confirm");}
  /** 验证到期自动处置与申诉竞争。 */
  @Test void deadlineRacesAppeal() throws Exception {exercise("timeout");}
  /** 验证期限内申诉保全资金与库存。 */
  @Test void appealBeforeDeadlineProtectsCashAndStock() throws Exception {exercise("appeal");}
  /** 验证第七十小时撤回后恢复剩余两小时。 */
  @Test void seventiethHourWithdrawalRestoresTwoHours() throws Exception {exercise("withdraw");}
  /** 验证第七十小时驳回后恢复剩余两小时。 */
  @Test void seventiethHourRejectionRestoresTwoHours() throws Exception {exercise("reject");}
  /** 验证截止时提交不能抢过自动或人工关闭。 */
  @Test void submissionAtDeadlineCannotBeatAutomaticAndAdministrativeClosure() throws Exception {exercise("cutoff");}
  /** 验证缺来源的历史索赔标记限制后续操作。 */
  @Test void legacyClaimMarkerWithoutSourceFailsClosed() throws Exception {
    D4RecoveryHttpTest setup=new D4RecoveryHttpTest();D3BQuoteTest d3BQuoteTest=setup.fixture();d3BQuoteTest.extraSources=new Class<?>[0];
    try {
      d3BQuoteTest.start();String order=setup.dispatch(d3BQuoteTest);
      d3BQuoteTest.call("POST","/api/me/rental-orders/"+order+"/receipt/confirm",view("expectedVersion",0),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
      d3BQuoteTest.call("POST","/api/admin/rental-orders/"+order+"/return-check/arrival",view("expectedVersion",0,"reason","到厂"),null,d3BQuoteTest.key(),200);
      DisputeGuard guard=d3BQuoteTest.f.app.getBean(com.mujian.backend.delivery.service.DisputeGuard.class);
      assertTrue(guard.inspect(order).ready());
      d3BQuoteTest.f.db.update("UPDATE order_return_check SET claim_created=1 WHERE rental_order_id=?",order);
      assertFalse(guard.inspect(order).ready());assertTrue(guard.blockedOrders(null,d3BQuoteTest.factoryId).orElseThrow().contains(order));
      JsonNode ctx=d3BQuoteTest.call("GET","/api/me/factory-orders/"+order+"/fulfillment",null,d3BQuoteTest.supplier,null,200);
      assertFalse(ctx.path("availableActions").toString().contains("confirm_return"));
      d3BQuoteTest.f.clock.at=d3BQuoteTest.f.clock.at.plusSeconds(73*3600);renewAdmin(d3BQuoteTest);
      d3BQuoteTest.call("POST","/api/admin/rental-orders/"+order+"/return-check/close",view("expectedVersion",0,"reason","复核旧单"),null,d3BQuoteTest.key(),503);
      d3BQuoteTest.f.app.getBean(com.mujian.backend.delivery.service.ReturnClosure.class).recover(order);
      assertEquals(0,amount(d3BQuoteTest,"SELECT COUNT(*) FROM order_return_check WHERE rental_order_id=? AND closed_at IS NOT NULL",order));
    }finally{d3BQuoteTest.stop();}
  }
  /** 按指定分支执行完整测试流程。 */
  private void exercise(String scenario) throws Exception {
    D4RecoveryHttpTest setup=new D4RecoveryHttpTest();D3BQuoteTest d3BQuoteTest=setup.fixture();d3BQuoteTest.extraSources=new Class<?>[0];
    try{
      d3BQuoteTest.start();D2ApplicationTest.User other=d3BQuoteTest.fixture.supplier();
      boolean full="confirm".equals(scenario);
      int count=full?2:3,loss=full?80000:2300,deposit=full?50000:1500,owed=loss-deposit;
      d3BQuoteTest.f.db.update("UPDATE rental_checklist_item SET quantity=? WHERE checklist_id=? AND sku_id=?",count,d3BQuoteTest.checklist,d3BQuoteTest.sku);
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET deposit_amount_fen=? WHERE id=?",deposit/count,d3BQuoteTest.sku);
      String order=setup.dispatch(d3BQuoteTest,d3BQuoteTest.paidOrder(account.mchId())),base="/api/me/factory-orders/"+order,buyer="/api/me/rental-orders/"+order;
      String secondDebt=full?anotherDebt(d3BQuoteTest,setup):null;
      d3BQuoteTest.call("POST",buyer+"/receipt/confirm",view("expectedVersion",0),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
      if(scenario.equals("appeal"))priorCash(d3BQuoteTest,order);
      d3BQuoteTest.call("POST","/api/admin/rental-orders/"+order+"/return-check/arrival",view("expectedVersion",0,"reason","实物已到厂"),null,d3BQuoteTest.key(),200);
      String item=d3BQuoteTest.f.db.queryForObject("SELECT id FROM rental_order_item WHERE rental_order_id=?",String.class,order);
      String proof=D4EvidenceHttpTest.upload(d3BQuoteTest,base,"claim",d3BQuoteTest.supplier,200);
      int checkVersion=amount(d3BQuoteTest,"SELECT version FROM order_return_check WHERE rental_order_id=?",order);
      Map<String,Object> body=view("expectedVersion",checkVersion,"problemType","damaged","claimedFen",loss,"summary","本单实物已到厂，其中一件外壳破损",
        "items",List.of(view("itemId",item,"issueType","damaged","quantity",1,"description","外壳破损")),
        "evidence",view("evidenceIds",List.of(),"uploadReceiptIds",List.of(proof)));
      String claim=d3BQuoteTest.call("POST",base+"/claims",body,d3BQuoteTest.supplier,d3BQuoteTest.key(),200).path("resourceId").asString();
      String own="/api/me/claims/"+claim,factory="/api/me/factory-claims/"+claim,admin="/api/admin/claims/"+claim,stock=base+"/return-check/claim-stock",query=stock+"?claimId="+claim;
      String proofId=d3BQuoteTest.f.db.queryForObject("SELECT id FROM order_evidence_record WHERE upload_receipt_id=?",String.class,proof);
      String privateView="/evidence/"+proofId+"/view";
      if(full){
        d3BQuoteTest.call("POST",base+privateView,Map.of(),d3BQuoteTest.supplier,null,200);
        d3BQuoteTest.call("POST",base+privateView,Map.of(),other,null,404);
        d3BQuoteTest.call("POST",buyer+privateView,Map.of(),d3BQuoteTest.buyer,null,404);
      }
      if(Set.of("withdraw","reject","cutoff").contains(scenario)){
        D2ApplicationTest.MutableClock clock=d3BQuoteTest.f.clock;
        Instant closeAt=d3BQuoteTest.f.db.queryForObject("SELECT auto_close_at FROM order_return_check WHERE rental_order_id=?",java.time.LocalDateTime.class,order)
            .atZone(java.time.ZoneId.of("Asia/Shanghai")).toInstant();
        clock.at="cutoff".equals(scenario)?closeAt:closeAt.minusSeconds(7200);
        renewAdmin(d3BQuoteTest);
        ReturnClosure closure=d3BQuoteTest.f.app.getBean(com.mujian.backend.delivery.service.ReturnClosure.class);
        if("cutoff".equals(scenario)){
          CountDownLatch gate=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(3);
          TradingCommand tradingCommand=new com.mujian.backend.order.dto.TradingCommand(d3BQuoteTest.f.admin,d3BQuoteTest.key(),com.mujian.backend.common.enums.TradingOperation.adminCloseReturn,"d".repeat(64));
          try{
            Future<Boolean> automatic=pool.submit(()->{gate.await();closure.recover(order);return true;});
            Future<Boolean> manual=pool.submit(()->{gate.await();try{closure.confirm(tradingCommand,order,checkVersion,"库房现场复核");}catch(com.mujian.backend.common.api.ApiFailure e){assertEquals("RETURN_ALREADY_CLOSED",e.reason);}return true;});
            Future<JsonNode> submit=pool.submit(()->{gate.await();return d3BQuoteTest.call("POST",factory+"/submit",view("expectedVersion",0),d3BQuoteTest.supplier,d3BQuoteTest.key(),409);});
            gate.countDown();automatic.get();manual.get();submit.get();
          }finally{pool.shutdownNow();}
          assertEquals(1,amount(d3BQuoteTest,"SELECT COUNT(*) FROM order_return_check WHERE rental_order_id=? AND closed_at IS NOT NULL",order));
          assertEquals(0,amount(d3BQuoteTest,"SELECT COUNT(*) FROM order_compensation_receivable WHERE rental_order_id=?",order));return;
        }
        d3BQuoteTest.call("POST",factory+"/submit",view("expectedVersion",0),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
        assertEquals(7200000,amount(d3BQuoteTest,"SELECT remaining_close_ms FROM order_return_check WHERE rental_order_id=?",order));
        clock.at=closeAt.plusSeconds(86400);renewAdmin(d3BQuoteTest);closure.recover(order);
        JsonNode ctx=d3BQuoteTest.call("GET",base+"/fulfillment",null,d3BQuoteTest.supplier,null,200);
        d3BQuoteTest.call("POST","/api/admin/rental-orders/"+order+"/return-check/close",view("expectedVersion",ctx.path("returnCheck").path("version").asInt(),"reason","复查现场"),null,d3BQuoteTest.key(),409);
        if("withdraw".equals(scenario))d3BQuoteTest.call("POST",factory+"/withdraw",view("expectedVersion",1),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
        else d3BQuoteTest.call("POST",admin+"/review",view("expectedVersion",1,"decision","reject","remark","证据不足"),null,d3BQuoteTest.key(),200);
        LocalDateTime restored=d3BQuoteTest.f.db.queryForObject("SELECT auto_close_at FROM order_return_check WHERE rental_order_id=?",java.time.LocalDateTime.class,order);
        assertEquals(now(clock).plusHours(2),restored);clock.at=clock.at.plusSeconds(7199);closure.recover(order);
        assertEquals(0,amount(d3BQuoteTest,"SELECT COUNT(*) FROM order_return_check WHERE rental_order_id=? AND closed_at IS NOT NULL",order));
        clock.at=clock.at.plusSeconds(1);closure.recover(order);closure.recover(order);
        assertEquals(1,amount(d3BQuoteTest,"SELECT COUNT(*) FROM order_return_check WHERE rental_order_id=? AND check_status='auto_closed'",order));return;
      }
      d3BQuoteTest.call("POST",factory+"/submit",view("expectedVersion",0),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
      d3BQuoteTest.call("GET",query,null,other,null,404);
      JsonNode state=d3BQuoteTest.call("GET",query,null,d3BQuoteTest.supplier,null,200);
      JsonNode line=state.path("lines").get(0);assertEquals(count,line.path("heldQuantity").asInt());
      Map<String,Object> record=view("expectedVersion",state.path("version").asInt(),"claimId",claim,"reservationId",line.path("reservationId").asString(),"itemId",item,
        "snId",null,"quantity",count-1,"outcome","intact","replacesId",null,"remark","逐件清点，登记到厂完好数量");
      String key=d3BQuoteTest.key();d3BQuoteTest.call("POST",stock,record,other,d3BQuoteTest.key(),404);
      JsonNode receipt=d3BQuoteTest.call("POST",stock,record,d3BQuoteTest.supplier,key,200);
      assertEquals(receipt.path("resourceVersion"),d3BQuoteTest.call("POST",stock,record,d3BQuoteTest.supplier,key,200).path("resourceVersion"));
      int version=d3BQuoteTest.call("GET",admin,null,null,null,200).path("version").asInt();
      if(full)d3BQuoteTest.call("POST","/api/admin/rental-orders/"+order+privateView,Map.of(),null,null,200);
      boolean browser=full && System.getenv("MJ_D5_NODE")!=null;
      if(browser)pages(d3BQuoteTest,order,claim,"claimReview");else d3BQuoteTest.call("POST",admin+"/review",view("expectedVersion",version,"decision","approve","lossFen",loss,"remark","按证据核定"),null,d3BQuoteTest.key(),200);
      if(full)assertNotNull(d3BQuoteTest.call("POST",buyer+privateView,Map.of(),d3BQuoteTest.buyer,null,200).path("expiresAt").asString());
      JsonNode review=d3BQuoteTest.call("GET",own,null,d3BQuoteTest.buyer,null,200);version=review.path("version").asInt();
      assertEquals(0,amount(d3BQuoteTest,"SELECT COUNT(*) FROM order_compensation_receivable WHERE rental_order_id=?",order));
      D2ApplicationTest.MutableClock clock=d3BQuoteTest.f.clock;Instant deadline=OffsetDateTime.parse(review.path("responseDeadlineAt").asString()).toInstant();
      FulfillmentRecovery job=d3BQuoteTest.f.app.getBean(FulfillmentRecovery.class);ClaimService claimService=d3BQuoteTest.f.app.getBean(ClaimService.class);
      Map<String,Object> appeal=view("expectedVersion",version,"summary","损失核定有误，请平台核对","evidence",view("evidenceIds",List.of(),"uploadReceiptIds",List.of()));
      if(scenario.equals("appeal")){
        clock.at=deadline.minusMillis(1);if(System.getenv("MJ_D5_NODE")!=null)pages(d3BQuoteTest,order,claim,"buyerAppeal");else d3BQuoteTest.call("POST",own+"/appeal",appeal,d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
        clock.at=deadline;job.recoverReturns();
        assertEquals("user_appealed",d3BQuoteTest.call("GET",own,null,d3BQuoteTest.buyer,null,200).path("status").asString());
        assertEquals(1,amount(d3BQuoteTest,"SELECT COUNT(*) FROM order_deduction WHERE rental_order_id=?",order));
        assertEquals(3,amount(d3BQuoteTest,"SELECT SUM(quantity) FROM inventory_reservation WHERE rental_order_id=? AND reservation_status='disputed_holding'",order));
        String arb=d3BQuoteTest.call("GET",own,null,d3BQuoteTest.buyer,null,200).path("arbitrationId").asString();
        String path="/api/admin/arbitrations/"+arb;
        d3BQuoteTest.f.adminCookie="mj_admin_dev="+d3BQuoteTest.f.sessions.create(d3BQuoteTest.f.admin,"admin").session().getId();
        d3BQuoteTest.f.csrf=d3BQuoteTest.f.call("GET","/api/admin/auth/csrf",null,null,null,200).path("csrfToken").asString();
        d3BQuoteTest.call("GET","/api/me/factory-arbitrations/"+arb,null,other,null,404);
        Map<String,Object> draft=view("expectedVersion",0,"lossFen",1000,"rentDeductionFen",0,"responsibility","buyer","summary","按实际维修损失裁决",
            "stockOutcomes",List.of(view("itemId",item,"snId",null,"quantity",1,"result","scrap")),
            "refunds",view("rentFen",0,"depositFen",0,"freightFen",0),"refundBasis","扣款后剩余押金原路退还");
        JsonNode saved=d3BQuoteTest.call("POST",path+"/decision-draft",draft,null,d3BQuoteTest.key(),200);
        assertEquals(100,saved.path("depositBalance").path("deductedFen").asInt());
        assertEquals(50,saved.path("depositBalance").path("refundedFen").asInt());
        assertEquals(100,saved.path("depositBalance").path("reservedFen").asInt());
        if(System.getenv("MJ_D5_NODE")!=null)pages(d3BQuoteTest,order,arb,"returnDraft");
        Map<String,Object> confirm=view("expectedVersion",saved.path("version").asInt(),"decisionId",saved.path("decision").path("id").asString());
        d3BQuoteTest.call("POST",path+"/decision-confirm",confirm,null,d3BQuoteTest.key(),409);
        assertEquals("SOURCE_ACTION_REQUIRED",d3BQuoteTest.f.lastReason);
        assertEquals(1,amount(d3BQuoteTest,"SELECT COUNT(*) FROM order_deduction WHERE rental_order_id=?",order));
        draft.put("expectedVersion",saved.path("version").asInt());draft.put("refunds",view("rentFen",0,"depositFen",250,"freightFen",0));
        saved=d3BQuoteTest.call("POST",path+"/decision-draft",draft,null,d3BQuoteTest.key(),200);
        confirm.put("expectedVersion",saved.path("version").asInt());confirm.put("decisionId",saved.path("decision").path("id").asString());
        key=d3BQuoteTest.key();d3BQuoteTest.call("POST",path+"/decision-confirm",confirm,null,key,200);d3BQuoteTest.call("POST",path+"/decision-confirm",confirm,null,key,200);
        JsonNode decision=d3BQuoteTest.call("GET",path,null,null,null,200);
        String refund=decision.path("refundIds").get(0).asString();
        assertEquals(350,decision.path("depositBalance").path("reservedFen").asInt());
        assertEquals(1100,decision.path("depositBalance").path("deductedFen").asInt());
        job.recoverReturns();assertEquals("executing",d3BQuoteTest.call("GET",path,null,null,null,200).path("status").asString());
        Map<String,Object> source=d3BQuoteTest.f.db.queryForMap("SELECT r.channel_out_trade_no,r.channel_transaction_id,s.paid_amount_fen FROM refund_order r JOIN payment_channel_suborder s ON s.id=r.payment_channel_suborder_id WHERE r.id=?",refund);
        RefundWorkTransaction refundWorkTransaction=d3BQuoteTest.f.app.getBean(com.mujian.backend.payment.service.RefundWorkTransaction.class);
        RefundExecution executor=new com.mujian.backend.payment.service.RefundExecution(refundWorkTransaction,gateway,d3BQuoteTest.f.app.getBean(com.mujian.backend.payment.service.UnformedBatchRefunds.class));
        Map<String,Object> channel=view("refund_id","arb-refund-"+refund,"out_refund_no",refund,"out_trade_no",source.get("channel_out_trade_no"),"transaction_id",source.get("channel_transaction_id"),
           "status","SUCCESS","success_time",time(now(clock)),"amount",view("refund",250,"total",source.get("paid_amount_fen"),"currency","CNY"));
        reply.set(json.writeValueAsString(channel));executor.execute(refund);
        ArbitrationRecovery recovery=d3BQuoteTest.f.app.getBean(com.mujian.backend.dispute.service.ArbitrationRecovery.class);
        assertTrue(recovery.next(arb));
        D2ApplicationTest.Fault fault=d3BQuoteTest.f.app.getBean(D2ApplicationTest.Fault.class);
        fault.table="prop_stock_movement";
        assertThrows(RuntimeException.class,()->recovery.next(arb));
        fault.table=null;recovery.failed(arb);
        assertEquals(300,d3BQuoteTest.call("GET",path,null,null,null,200).path("depositBalance").path("refundedFen").asInt());
        assertEquals("execution_failed",d3BQuoteTest.call("GET",path,null,null,null,200).path("status").asString());
        job.recoverReturns();job.recoverReturns();
        assertEquals("closed",d3BQuoteTest.call("GET",path,null,null,null,200).path("status").asString());
        assertEquals("executing",d3BQuoteTest.call("GET",own,null,d3BQuoteTest.buyer,null,200).path("status").asString());
        String historical=d3BQuoteTest.f.db.queryForObject("SELECT id FROM refund_order WHERE rental_order_id=? AND deposit_refund_amount_fen=100",String.class,order);
        channel.put("refund_id","wx-"+historical);channel.put("out_refund_no",historical);
        channel.put("amount",view("total",source.get("paid_amount_fen"),"refund",100,"currency","CNY"));reply.set(json.writeValueAsString(channel));
        executor.execute(historical);job.recoverReturns();
        assertEquals(400,d3BQuoteTest.call("GET",path,null,null,null,200).path("depositBalance").path("refundedFen").asInt());
        assertEquals("closed",d3BQuoteTest.call("GET",own,null,d3BQuoteTest.buyer,null,200).path("status").asString());
        assertEquals(2,amount(d3BQuoteTest,"SELECT COUNT(*) FROM order_deduction WHERE rental_order_id=?",order));
        assertEquals(1,amount(d3BQuoteTest,"SELECT COUNT(*) FROM prop_stock_movement WHERE related_arbitration_id=?",arb));
        return;
      }
      if(scenario.equals("timeout")){
        clock.at=deadline;CountDownLatch gate=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
          Future<Boolean> left=pool.submit(()->{gate.await();claimService.expire(claim);return true;});
          Future<JsonNode> right=pool.submit(()->{gate.await();return d3BQuoteTest.call("POST",own+"/appeal",appeal,d3BQuoteTest.buyer,d3BQuoteTest.key(),409);});
          gate.countDown();left.get();right.get();
        }finally{pool.shutdownNow();}
      }else{
        if(browser)pages(d3BQuoteTest,order,claim,"claimConfirm");else {Map<String,Object> confirm=view("expectedVersion",version);key=d3BQuoteTest.key();
        d3BQuoteTest.call("POST",own+"/confirm",confirm,d3BQuoteTest.buyer,key,200);d3BQuoteTest.call("POST",own+"/confirm",confirm,d3BQuoteTest.buyer,key,200);}
      }
      claimService.expire(claim);job.recoverReturns();job.recoverReturns();
      assertEquals(1,amount(d3BQuoteTest,"SELECT COUNT(*) FROM claim_user_response WHERE claim_id=?",claim));
      assertEquals(deposit,amount(d3BQuoteTest,"SELECT SUM(amount_fen) FROM order_deduction WHERE rental_order_id=?",order));
      assertEquals(owed,amount(d3BQuoteTest,"SELECT receivable_amount_fen FROM order_compensation_receivable WHERE rental_order_id=?",order));
      state=d3BQuoteTest.call("GET",query,null,d3BQuoteTest.supplier,null,200);
      assertEquals(1,state.path("lines").get(0).path("heldQuantity").asInt());
      assertEquals("applied",state.path("records").get(0).path("status").asString());
      JsonNode work=d3BQuoteTest.call("GET","/api/me/factory-orders?view=factory_pending",null,d3BQuoteTest.supplier,null,200);
      assertTrue(work.path("items").toString().contains(order));
      assertTrue(d3BQuoteTest.call("GET",base,null,d3BQuoteTest.supplier,null,200).path("availableActions").toString().contains("return_check"));
      int available=amount(d3BQuoteTest,"SELECT available_quantity FROM prop_sku_stock WHERE sku_id=?",d3BQuoteTest.sku);
      record.put("expectedVersion",state.path("version").asInt());record.put("quantity",1);record.put("outcome","repair");record.put("remark","破损件送修");
      d3BQuoteTest.call("POST",stock,record,d3BQuoteTest.supplier,d3BQuoteTest.key(),200);job.recoverReturns();
      state=d3BQuoteTest.call("GET",query,null,d3BQuoteTest.supplier,null,200);JsonNode repair=state.path("records").valueStream().filter(r->"repair_pending".equals(r.path("status").asString())).findFirst().orElseThrow();
      assertEquals(1,state.path("lines").get(0).path("heldQuantity").asInt());
      record.put("expectedVersion",state.path("version").asInt());record.put("outcome","scrap");record.put("replacesId",repair.path("id").asString());record.put("remark","检测后确认不可修复，已报废");
      key=d3BQuoteTest.key();d3BQuoteTest.call("POST",stock,record,d3BQuoteTest.supplier,key,200);d3BQuoteTest.call("POST",stock,record,d3BQuoteTest.supplier,key,200);job.recoverReturns();job.recoverReturns();
      assertTrue(d3BQuoteTest.call("GET",query,null,d3BQuoteTest.supplier,null,200).path("lines").isEmpty());
      assertEquals(available,amount(d3BQuoteTest,"SELECT available_quantity FROM prop_sku_stock WHERE sku_id=?",d3BQuoteTest.sku));
      assertEquals(1,amount(d3BQuoteTest,"SELECT scrapped_quantity FROM prop_sku_stock WHERE sku_id=?",d3BQuoteTest.sku));
      assertEquals(1,amount(d3BQuoteTest,"SELECT COUNT(*) FROM order_return_check WHERE rental_order_id=? AND closed_at IS NOT NULL",order));
      if("confirm".equals(scenario)){
        SettlementGeneration settlement=d3BQuoteTest.f.app.getBean(com.mujian.backend.settlement.service.SettlementGeneration.class);
        clock.at=clock.at.plusSeconds(2*86400);
        settlement.generate(d3BQuoteTest.factoryId);
        String original=d3BQuoteTest.f.db.queryForObject("SELECT source_allocation_snapshot_json FROM factory_settlement_detail WHERE rental_order_id=?",String.class,order);
        String debt=d3BQuoteTest.f.db.queryForObject("SELECT id FROM order_compensation_receivable WHERE rental_order_id=?",String.class,order);
        JsonNode cash=d3BQuoteTest.call("GET","/api/me/compensation-receivables/"+debt,null,d3BQuoteTest.buyer,null,200);
        assertEquals(owed,cash.path("outstandingFen").asInt());
        d3BQuoteTest.call("GET","/api/me/factory-compensation-receivables/"+debt,null,other,null,404);
        assertTrue(d3BQuoteTest.call("GET","/api/me/factory-compensation-receivables/"+debt,null,d3BQuoteTest.supplier,null,200).path("availableActions").isEmpty());
        PaymentExecution exec=d3BQuoteTest.f.app.getBean(com.mujian.backend.payment.service.PaymentExecution.class);
        org.springframework.test.util.ReflectionTestUtils.setField(exec,"paymentGateway",gateway);
        org.springframework.test.util.ReflectionTestUtils.setField(d3BQuoteTest.f.app.getBean(com.mujian.backend.dispute.controller.ReceivableController.class),"paymentGateway",gateway);
        org.springframework.test.util.ReflectionTestUtils.setField(d3BQuoteTest.f.app.getBean(com.mujian.backend.order.service.ExtensionPayment.class),"paymentGateway",gateway);
        String payPath="/api/me/compensation-receivables/"+debt+"/pay";
        key=d3BQuoteTest.key();reply.set("{\"prepay_id\":\"isolated-compensation-prepay\"}");
        Map<String,Object> intent=view("expectedVersion",cash.path("version").asInt());JsonNode pay=d3BQuoteTest.call("POST",payPath,intent,d3BQuoteTest.buyer,key,200);
        assertEquals("pay",pay.path("nextAction").asString());
        String closedId=pay.path("payment").path("id").asString();
        Map<String,Object> closedSub=d3BQuoteTest.f.db.queryForMap("SELECT channel_sub_order_no,sub_mchid_snapshot FROM payment_channel_suborder WHERE payment_order_id=?",closedId);
        reply.set(json.writeValueAsString(view("combine_appid","test-app","combine_mchid",account.mchId(),"combine_out_trade_no",closedId,
          "sub_orders",List.of(view("out_trade_no",closedSub.get("channel_sub_order_no"),"mchid",account.mchId(),"sub_mchid",closedSub.get("sub_mchid_snapshot"),
          "trade_state","CLOSED","amount",view("total_amount",owed,"currency","CNY"))))));
        clock.at=clock.at.plusSeconds(31);exec.execute(closedId);
        JsonNode pending=d3BQuoteTest.call("GET","/api/me/compensation-receivables/"+debt,null,d3BQuoteTest.buyer,null,200);
        assertEquals("pending",pending.path("status").asString());assertEquals(owed,pending.path("outstandingFen").asInt());
        reply.set("{\"prepay_id\":\"isolated-supplement-second-attempt\"}");
        JsonNode next=d3BQuoteTest.call("POST",payPath,view("expectedVersion",pending.path("version").asInt()),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
        String pid=next.path("payment").path("id").asString();assertNotEquals(closedId,pid);
        JsonNode originalLaunch=d3BQuoteTest.call("POST",payPath,intent,d3BQuoteTest.buyer,key,200);
        assertEquals(closedId,originalLaunch.path("payment").path("id").asString());assertTrue(originalLaunch.path("parameters").isNull());
        d3BQuoteTest.f.adminCookie="mj_admin_dev="+d3BQuoteTest.f.sessions.create(d3BQuoteTest.f.admin,"admin").session().getId();
        d3BQuoteTest.f.csrf=d3BQuoteTest.f.call("GET","/api/admin/auth/csrf",null,null,null,200).path("csrfToken").asString();
        if(browser)pages(d3BQuoteTest,order,debt,"debtPay");
        JsonNode trace=d3BQuoteTest.call("GET","/api/admin/payments/"+pid,null,null,null,200);
        assertTrue(trace.path("batch").isNull());assertEquals(debt,trace.path("receivableId").asString());
        assertEquals("compensation_supplement",trace.path("scene").asString());
        Map<String,Object> sub=d3BQuoteTest.f.db.queryForMap("SELECT id,sub_mchid_snapshot,channel_sub_order_no FROM payment_channel_suborder WHERE payment_order_id=?",pid);
        Map<String,Object> fact=view("combine_appid","test-app","combine_mchid",account.mchId(),"combine_out_trade_no",pid,"sub_orders",List.of(view(
           "out_trade_no",sub.get("channel_sub_order_no"),"mchid",account.mchId(),"sub_mchid",sub.get("sub_mchid_snapshot"),"transaction_id","wx-"+pid,
           "trade_state","SUCCESS","success_time",clock.instant().toString(),"amount",view("total_amount",owed,"payer_amount",owed,"currency","CNY","payer_currency","CNY"))));
        LinkedHashMap<String,Object> unknown=new LinkedHashMap<String,Object>(fact);unknown.put("combine_out_trade_no",com.mujian.backend.common.support.Ids.next());
        assertEquals(400,d3BQuoteTest.notifyPayment(d3BQuoteTest.key(),unknown,true));
        String event=com.mujian.backend.common.support.Ids.next();
        assertEquals(204,d3BQuoteTest.notifyPayment(event,fact,true));assertEquals(204,d3BQuoteTest.notifyPayment(event,fact,true));
        PaymentNotificationMapper inbox=d3BQuoteTest.f.app.getBean(com.mujian.backend.payment.mapper.PaymentNotificationMapper.class);
        PaymentNotificationRecovery worker=d3BQuoteTest.f.app.getBean(com.mujian.backend.payment.service.PaymentNotificationRecovery.class);
        worker.recover(inbox.selectNotificationByEvent(event));worker.recover(inbox.selectNotificationByEvent(event));
        assertEquals("processed",inbox.selectNotificationByEvent(event).processStatus());
        reply.set(json.writeValueAsString(fact));clock.at=clock.at.plusSeconds(31);exec.execute(pid);exec.execute(pid);
        cash=d3BQuoteTest.call("GET","/api/me/compensation-receivables/"+debt,null,d3BQuoteTest.buyer,null,200);
        assertEquals(0,cash.path("outstandingFen").asInt());assertEquals(owed,cash.path("paidFen").asInt());
        assertEquals(owed,amount(d3BQuoteTest,"SELECT compensation_allocated_amount_fen FROM payment_allocation WHERE payment_order_id=?",pid));
        clock.at=clock.at.plusSeconds(2*86400);
        CountDownLatch start=new CountDownLatch(1);ExecutorService pool=Executors.newFixedThreadPool(2);
        try{
          Callable<Void> generate=()->{start.await();settlement.generate(d3BQuoteTest.factoryId);return null;};
          Future<Void> first=pool.submit(generate);Future<Void> second=pool.submit(generate);start.countDown();first.get();second.get();
        }finally{pool.shutdownNow();}
        assertEquals(2,amount(d3BQuoteTest,"SELECT COUNT(*) FROM factory_settlement_detail WHERE rental_order_id=?",order));
        assertEquals(original,d3BQuoteTest.f.db.queryForObject("SELECT source_allocation_snapshot_json FROM factory_settlement_detail WHERE rental_order_id=? AND excess_compensation_paid_amount_fen=0",String.class,order));
        assertEquals(1,amount(d3BQuoteTest,"SELECT COUNT(*) FROM factory_settlement_detail WHERE rental_order_id=? AND excess_compensation_paid_amount_fen=30000 AND platform_commission_amount_fen=0 AND factory_settle_amount_fen=30000",order));
        assertEquals(1,amount(d3BQuoteTest,"SELECT COUNT(*) FROM wechat_profit_sharing_order WHERE payment_order_id=? AND execution_type='finish_only'",pid));
        assertEquals(100,d3BQuoteTest.call("GET","/api/me/compensation-receivables/"+secondDebt,null,d3BQuoteTest.buyer,null,200).path("outstandingFen").asInt());
        assertTrue(d3BQuoteTest.f.app.getBean(com.mujian.backend.order.mapper.CheckoutMapper.class).existsOutstandingCompensation(d3BQuoteTest.buyer.id()));
        d3BQuoteTest.call("POST","/api/me/checkout-quotes",d3BQuoteTest.input,d3BQuoteTest.buyer,null,409);assertEquals("COMPENSATION_OUTSTANDING",d3BQuoteTest.f.lastReason);
        verifyFinishOnlyRecovery(d3BQuoteTest,pid,owed);
        JsonNode otherCash=d3BQuoteTest.call("GET","/api/me/compensation-receivables/"+secondDebt,null,d3BQuoteTest.buyer,null,200);
        reply.set("{\"prepay_id\":\"isolated-second-debt\"}");
        String otherPid=d3BQuoteTest.call("POST","/api/me/compensation-receivables/"+secondDebt+"/pay",view("expectedVersion",otherCash.path("version").asInt()),d3BQuoteTest.buyer,d3BQuoteTest.key(),200)
          .path("payment").path("id").asString();
        Map<String,Object> otherSub=d3BQuoteTest.f.db.queryForMap("SELECT channel_sub_order_no,sub_mchid_snapshot FROM payment_channel_suborder WHERE payment_order_id=?",otherPid);
        Map<String,Object> last=view("combine_appid","test-app","combine_mchid",account.mchId(),"combine_out_trade_no",otherPid,"sub_orders",List.of(view(
          "out_trade_no",otherSub.get("channel_sub_order_no"),"mchid",account.mchId(),"sub_mchid",otherSub.get("sub_mchid_snapshot"),"transaction_id","wx-"+otherPid,
          "trade_state","SUCCESS","success_time",clock.instant().toString(),"amount",view("total_amount",100,"currency","CNY"))));
        reply.set(json.writeValueAsString(last));clock.at=clock.at.plusSeconds(31);exec.execute(otherPid);
        assertEquals("paid",d3BQuoteTest.call("GET","/api/me/compensation-receivables/"+secondDebt,null,d3BQuoteTest.buyer,null,200).path("status").asString());
        d3BQuoteTest.f.app.getBean(com.mujian.backend.order.service.TradingAccess.class).newTrade(d3BQuoteTest.buyer.id());
      }
    }finally{d3BQuoteTest.stop();}
  }
  /** 检查仅待完成阶段的恢复。 */
  private void verifyFinishOnlyRecovery(D3BQuoteTest d3BQuoteTest,String payment,int owed) throws Exception {
    JdbcTemplate db=d3BQuoteTest.f.db;
    String id=db.queryForObject("SELECT id FROM wechat_profit_sharing_order WHERE payment_order_id=?",String.class,payment);
    SharingMapper sharingMapper=d3BQuoteTest.f.app.getBean(com.mujian.backend.settlement.mapper.SharingMapper.class);
    SharingMapper.State source=sharingMapper.selectWechatProfitSharingOrderState(id);
    SettlementRecovery recovery=d3BQuoteTest.f.app.getBean(com.mujian.backend.settlement.job.SettlementRecovery.class);
    org.springframework.test.util.ReflectionTestUtils.setField(recovery,"profitSharingGateway",gateway);
    Map<String,Object> response=view("sub_mchid",source.merchant(),"transaction_id",source.transactionId(),"out_order_no",source.finishNo(),"order_id","wx-finish-"+id);
    // 首次渠道结果未知。恢复必须查询同一个解冻号，不得发起分佣或第二份解冻。
    int before=requests.size();
    org.springframework.test.util.ReflectionTestUtils.setField(this,"replyStatus",503);
    try {reply.set("{}");recovery.run(id);} finally {org.springframework.test.util.ReflectionTestUtils.setField(this,"replyStatus",200);}
    assertEquals("processing",sharingMapper.selectWechatProfitSharingOrderState(id).unfreeze());
    d3BQuoteTest.f.clock.at=d3BQuoteTest.f.clock.at.plusSeconds(60);
    response.put("status","FINISHED");response.put("finish_amount",owed);reply.set(json.writeValueAsString(response));
    recovery.run(id);recovery.run(id);
    assertEquals("success",sharingMapper.selectWechatProfitSharingOrderState(id).unfreeze());
    assertEquals(List.of("POST /v3/ecommerce/profitsharing/finish-order","GET /v3/ecommerce/profitsharing/orders?sub_mchid="+source.merchant()
        +"&transaction_id="+source.transactionId()+"&out_order_no="+source.finishNo()),requests.subList(before,requests.size()));
    assertEquals(owed,db.queryForObject("SELECT actual_unlock_amount_fen FROM wechat_profit_sharing_order WHERE id=? AND actual_commission_amount_fen=0",Integer.class,id));
  }
  /** 续建当前用例的管理员会话。 */
  static void renewAdmin(D3BQuoteTest d3BQuoteTest) throws Exception {
    d3BQuoteTest.f.adminCookie="mj_admin_dev="+d3BQuoteTest.f.sessions.create(d3BQuoteTest.f.admin,"admin").session().getId();
    d3BQuoteTest.f.csrf=d3BQuoteTest.f.call("GET","/api/admin/auth/csrf",null,null,null,200).path("csrfToken").asString();
  }
  /** 读取待核对的分页面。 */
  static void pages(D3BQuoteTest d3BQuoteTest,String order,String id,String mode) throws Exception {
    Process script=new ProcessBuilder(System.getenv("MJ_D5_NODE"),"../mujian-frontend/tests/d5-issue-pages-http.cjs").inheritIO()
      .redirectInput(ProcessBuilder.Redirect.PIPE).start();
    try{
      try(OutputStream stdin=script.getOutputStream()){stdin.write(d3BQuoteTest.f.json.writeValueAsBytes(view("base",d3BQuoteTest.f.base,"cookie",d3BQuoteTest.f.adminCookie,"order",order,"issue",id,
        "mode",mode,"token",d3BQuoteTest.buyer.token(),"supplier",d3BQuoteTest.supplier.token(),"out",java.nio.file.Path.of("target/d5").toAbsolutePath().toString())));}
      assertTrue(script.waitFor(60,TimeUnit.SECONDS));assertEquals(0,script.exitValue());
    }finally{if(script.isAlive())script.destroyForcibly();}
  }
  /** 通过先前履约中已裁决的扣款和两个协商退款构造不同历史去向。 */
  private void priorCash(D3BQuoteTest d3BQuoteTest,String order) throws Exception {
    String buyer="/api/me/rental-orders/"+order, item=d3BQuoteTest.f.db.queryForObject("SELECT id FROM rental_order_item WHERE rental_order_id=?",String.class,order);
    Map<String,Object> empty=view("evidenceIds",List.of(),"uploadReceiptIds",List.of());
    for(int amount:new int[]{0,50,100}){
      int version=d3BQuoteTest.call("GET",buyer,null,d3BQuoteTest.buyer,null,200).path("version").asInt();
      String issue=d3BQuoteTest.call("POST",buyer+"/issues",view("expectedVersion",version,"scene","in_use","effect","continue","primaryIssueType","damaged","summary","履约中的历史处理",
          "items",List.of(view("itemId",item,"issueType","damaged","quantity",1,"description","核对实物")),"evidence",empty),d3BQuoteTest.buyer,d3BQuoteTest.key(),200).path("resourceId").asString();
      if(amount==0){
        version=d3BQuoteTest.call("GET",buyer,null,d3BQuoteTest.buyer,null,200).path("version").asInt();
        String arb=d3BQuoteTest.call("POST","/api/admin/rental-orders/"+order+"/arbitrations",view("expectedVersion",version,"sourceType","receipt_problem","sourceId",issue,"summary","实际损失已知","evidence",empty),null,d3BQuoteTest.key(),200).path("resourceId").asString();
        String path="/api/admin/arbitrations/"+arb;
        JsonNode draft=d3BQuoteTest.call("POST",path+"/decision-draft",view("expectedVersion",0,"responsibility","buyer","lossFen",100,"rentDeductionFen",0,
          "refunds",view("rentFen",0,"depositFen",0,"freightFen",0),"stockOutcomes",List.of(),"summary","承担一元损失，继续履约"),null,d3BQuoteTest.key(),200);
        d3BQuoteTest.call("POST",path+"/decision-confirm",view("expectedVersion",draft.path("version").asInt(),"decisionId",draft.path("decision").path("id").asString()),null,d3BQuoteTest.key(),200);
        d3BQuoteTest.f.app.getBean(com.mujian.backend.dispute.service.ArbitrationRecovery.class).next(arb);
      }else{
        String path="/api/admin/issues/"+issue;
        d3BQuoteTest.call("POST",path+"/resolution",view("expectedVersion",0,"direction","continue","solutionSummary","协商退还部分押金","agreement",view("summary","双方已确认",
          "confirmedAt",time(now(d3BQuoteTest.f.clock)),"evidenceIds",List.of()),"references",List.of(),"serviceRefund",view("components",view("rentFen",0,"depositFen",amount,"freightFen",0),"basis","协商降低押金")),null,d3BQuoteTest.key(),200);
        if(amount==50){
          String id=d3BQuoteTest.call("GET",path,null,null,null,200).path("plan").path("refunds").get(0).path("refundId").asString();
          Map<String,Object> sub=d3BQuoteTest.f.db.queryForMap("SELECT r.channel_out_trade_no,r.channel_transaction_id,s.paid_amount_fen FROM refund_order r JOIN payment_channel_suborder s ON s.id=r.payment_channel_suborder_id WHERE r.id=?",id);
          reply.set(json.writeValueAsString(view("refund_id","wx-"+id,"out_refund_no",id,"out_trade_no",sub.get("channel_out_trade_no"),"transaction_id",sub.get("channel_transaction_id"),
             "status","SUCCESS","success_time",time(now(d3BQuoteTest.f.clock)),"amount",view("total",sub.get("paid_amount_fen"),"refund",amount,"currency","CNY"))));
          new com.mujian.backend.payment.service.RefundExecution(d3BQuoteTest.f.app.getBean(com.mujian.backend.payment.service.RefundWorkTransaction.class),gateway,
            d3BQuoteTest.f.app.getBean(com.mujian.backend.payment.service.UnformedBatchRefunds.class)).execute(id);
        }
      }
    }
  }
  /** 同一买家的另一笔真实旧单，在第一笔补缴完成后继续限制新的付款。 */
  private String anotherDebt(D3BQuoteTest d3BQuoteTest,D4RecoveryHttpTest setup) throws Exception {
    String start=now(d3BQuoteTest.f.clock).toLocalDate().plusDays(10).toString();
    String list=d3BQuoteTest.call("POST","/api/me/checklists",view("sourceType","manual","startDate",start,"endDate",start),d3BQuoteTest.buyer,d3BQuoteTest.key(),200).path("resourceId").asString();
    d3BQuoteTest.call("POST","/api/me/checklists/"+list+"/items",view("expectedVersion",0,"skuId",d3BQuoteTest.sku,"quantity",1,"remark",null),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
    d3BQuoteTest.input.put("checklistId",list);d3BQuoteTest.coupon=null;
    String order=setup.dispatch(d3BQuoteTest,d3BQuoteTest.paidOrder(account.mchId())),buyer="/api/me/rental-orders/"+order,factory="/api/me/factory-orders/"+order;
    d3BQuoteTest.call("POST",buyer+"/receipt/confirm",view("expectedVersion",0),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
    d3BQuoteTest.call("POST","/api/admin/rental-orders/"+order+"/return-check/arrival",view("expectedVersion",0,"reason","第二笔测试订单已到厂"),null,d3BQuoteTest.key(),200);
    int version=amount(d3BQuoteTest,"SELECT version FROM order_return_check WHERE rental_order_id=?",order);
    String proof=D4EvidenceHttpTest.upload(d3BQuoteTest,factory,"claim",d3BQuoteTest.supplier,200);
    String item=d3BQuoteTest.f.db.queryForObject("SELECT id FROM rental_order_item WHERE rental_order_id=?",String.class,order);
    String claim=d3BQuoteTest.call("POST",factory+"/claims",view("expectedVersion",version,"problemType","damaged","claimedFen",25100,"summary","第二笔赔付款",
      "items",List.of(view("itemId",item,"issueType","damaged","quantity",1,"description","破损")),"evidence",view("evidenceIds",List.of(),"uploadReceiptIds",List.of(proof))),d3BQuoteTest.supplier,d3BQuoteTest.key(),200).path("resourceId").asString();
    d3BQuoteTest.call("POST","/api/me/factory-claims/"+claim+"/submit",view("expectedVersion",0),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
    d3BQuoteTest.call("POST","/api/admin/claims/"+claim+"/review",view("expectedVersion",1,"decision","approve","lossFen",25100,"remark","核对损失"),null,d3BQuoteTest.key(),200);
    d3BQuoteTest.call("POST","/api/me/claims/"+claim+"/confirm",view("expectedVersion",2),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
    return d3BQuoteTest.f.db.queryForObject("SELECT id FROM order_compensation_receivable WHERE rental_order_id=?",String.class,order);
  }
  /** 提取用于断言的金额。 */
  private int amount(D3BQuoteTest d3BQuoteTest,String sql,String id){return d3BQuoteTest.f.db.queryForObject(sql,Integer.class,id);}
}
