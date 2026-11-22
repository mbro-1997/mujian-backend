package com.mujian.backend.d2a;

import com.mujian.backend.dispute.service.ClaimStock;
import java.util.Map;
import tools.jackson.databind.JsonNode;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.OrderExtensions;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

/** 同厂两笔真实订单处于相邻档期，延期只使用原合同的资产或数量。 */
class D5ExtensionStockHttpTest {
  /** 验证已分配实例在首个占用日限制延期。 */
  @Test void assignedSnLimitsExtensionAtFirstOccupiedDay() throws Exception {overlap(true);}
  /** 检查数量类延期超过剩余库存的边界。 */
  @Test void quantityExtensionCanExceedRemainingPool() throws Exception {overlap(false);}
  /** 构造交叠区间检查占用边界。 */
  private void overlap(boolean asset) throws Exception {
    D4RecoveryHttpTest setup=new D4RecoveryHttpTest();D3BQuoteTest d3BQuoteTest=setup.fixture();d3BQuoteTest.extraSources=new Class<?>[0];
    try {
      d3BQuoteTest.start();
      d3BQuoteTest.f.db.update("UPDATE prop_sku_stock SET total_quantity=2,rentable_base_quantity=2,available_quantity=2 WHERE sku_id=?",d3BQuoteTest.sku);
      String sku=d3BQuoteTest.sku;
      if(asset){
        JsonNode made=d3BQuoteTest.fixture.create(d3BQuoteTest.supplier,"A_ASSET",1);sku=made.path("skuId").asString();
        d3BQuoteTest.fixture.approve("sku_audit",made.path("targetId").asString(),0);
        d3BQuoteTest.f.db.update("UPDATE prop_sn_instance SET instance_status='idle' WHERE sku_id=?",sku);
        d3BQuoteTest.call("POST","/api/me/checklists/"+d3BQuoteTest.checklist+"/items",view("expectedVersion",1,"skuId",sku,"quantity",1,"remark",null),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
        d3BQuoteTest.input.put("expectedVersion",2);
      }
      String order=d3BQuoteTest.paidOrder();
      String day=now(d3BQuoteTest.f.clock).toLocalDate().plusDays(12).toString();
      String next=d3BQuoteTest.call("POST","/api/me/checklists",view("sourceType","manual","startDate",day,"endDate",day),d3BQuoteTest.buyer,d3BQuoteTest.key(),200).path("resourceId").asString();
      d3BQuoteTest.call("POST","/api/me/checklists/"+next+"/items",view("expectedVersion",0,"skuId",sku,"quantity",asset?1:2,"remark",null),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
      d3BQuoteTest.input.put("checklistId",next);d3BQuoteTest.input.put("expectedVersion",1);d3BQuoteTest.coupon=null;
      String later=d3BQuoteTest.paidOrder(),buyer="/api/me/rental-orders/"+order,base="/api/me/factory-orders/"+order;
      if(asset){
        d3BQuoteTest.call("POST",base+"/accept",view("expectedVersion",0),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
        String code=d3BQuoteTest.f.db.queryForObject("SELECT qr_code_id FROM prop_qr_code WHERE target_type='SN' AND sku_id=?",String.class,sku);
        d3BQuoteTest.call("POST",base+"/outbound/scans",view("expectedVersion",0,"code",code),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
        String item=d3BQuoteTest.f.db.queryForObject("SELECT id FROM rental_order_item WHERE rental_order_id=? AND sku_id=?",String.class,order,d3BQuoteTest.sku);
        d3BQuoteTest.call("POST",base+"/outbound/quantity",view("expectedVersion",1,"itemId",item,"quantity",2),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
        ArrayList<Object> photos=new ArrayList<>();for(int i=0;i<3;i++)photos.add(D4EvidenceHttpTest.part(D4EvidenceHttpTest.upload(d3BQuoteTest,base,"outbound",d3BQuoteTest.supplier,200),"outbound"));
        d3BQuoteTest.call("POST",base+"/evidence",view("expectedVersion",2,"materials",photos),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
        d3BQuoteTest.call("POST",base+"/outbound/confirm",view("expectedVersion",3),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
      }else setup.dispatch(d3BQuoteTest,order);
      d3BQuoteTest.call("POST",buyer+"/receipt/confirm",view("expectedVersion",0),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
      int gap=d3BQuoteTest.f.db.queryForObject("SELECT DATEDIFF(b.occupy_start_date,a.occupy_end_date)-1 FROM inventory_reservation a JOIN inventory_reservation b ON a.sku_id=b.sku_id AND a.sn_id <=> b.sn_id WHERE a.rental_order_id=? AND b.rental_order_id=?",Integer.class,order,later);
      assertTrue(gap>0);String path=buyer+"/extension-quote?extraDays=";
      JsonNode quote=d3BQuoteTest.call("GET",path+gap,null,d3BQuoteTest.buyer,null,200);
      if(asset){
        assertEquals(gap,quote.path("maxExtraDays").asInt());
        d3BQuoteTest.call("GET",path+(gap+1),null,d3BQuoteTest.buyer,null,409);assertEquals("EXTENSION_DAYS_EXCEEDED",d3BQuoteTest.f.lastReason);
        verifyRepairOfAssignedInstance(d3BQuoteTest,order,sku);
      }else{
        JsonNode ok=d3BQuoteTest.call("GET",path+(gap+1),null,d3BQuoteTest.buyer,null,200);
        TradingCommand tradingCommand=new TradingCommand(d3BQuoteTest.buyer.id(),d3BQuoteTest.key(),TradingOperation.submitExtension,"d".repeat(64));
        d3BQuoteTest.f.app.getBean(OrderExtensions.class).submit(tradingCommand,order,new OrderExtensions.Days(ok.path("orderVersion").asInt(),gap+1,null));
        assertEquals(4,d3BQuoteTest.f.db.queryForObject("SELECT SUM(quantity) FROM inventory_reservation WHERE sku_id=? AND reservation_status IN ('held','paid_locked') AND occupy_start_date <= (SELECT MIN(occupy_start_date) FROM inventory_reservation WHERE rental_order_id=?) AND occupy_end_date >= (SELECT MIN(occupy_start_date) FROM inventory_reservation WHERE rental_order_id=?)",Integer.class,sku,later,later));
        assertEquals(2,d3BQuoteTest.f.db.queryForObject("SELECT rentable_base_quantity FROM prop_sku_stock WHERE sku_id=?",Integer.class,sku));
      }
    }finally{d3BQuoteTest.stop();}
  }
  /** 核对维修对已分配实例的影响。 */
  private void verifyRepairOfAssignedInstance(D3BQuoteTest d3BQuoteTest,String order,String sku) throws Exception {
    String base="/api/me/factory-orders/"+order;
    d3BQuoteTest.call("POST","/api/admin/rental-orders/"+order+"/return-check/arrival",view("expectedVersion",0,"reason","到厂核对维修件"),null,d3BQuoteTest.key(),200);
    Map<String,Object> line=d3BQuoteTest.f.db.queryForMap("SELECT r.id,r.sn_id,r.rental_order_item_id FROM inventory_reservation r WHERE r.rental_order_id=? AND r.sku_id=?",order,sku);
    String photo=D4EvidenceHttpTest.upload(d3BQuoteTest,base,"claim",d3BQuoteTest.supplier,200);
    int checkVersion=d3BQuoteTest.call("GET",base+"/fulfillment",null,d3BQuoteTest.supplier,null,200).path("returnCheck").path("version").asInt();
    String claim=d3BQuoteTest.call("POST",base+"/claims",view("expectedVersion",checkVersion,"problemType","damaged","claimedFen",1,"summary","到厂后确认此件需送修",
      "items",java.util.List.of(view("itemId",line.get("rental_order_item_id"),"quantity",1,"issueType","damaged","description","需送修")),
      "evidence",view("evidenceIds",java.util.List.of(),"uploadReceiptIds",java.util.List.of(photo))),d3BQuoteTest.supplier,d3BQuoteTest.key(),200).path("resourceId").asString();
    d3BQuoteTest.call("POST","/api/me/factory-claims/"+claim+"/submit",view("expectedVersion",0),d3BQuoteTest.supplier,d3BQuoteTest.key(),200);
    Map<String,Object> record=view("expectedVersion",1,"claimId",claim,"reservationId",line.get("id"),"itemId",line.get("rental_order_item_id"),"snId",line.get("sn_id"),"quantity",1,"outcome","repair","replacesId",null,"remark","已交维修");
    String key=d3BQuoteTest.key();d3BQuoteTest.call("POST",base+"/return-check/claim-stock",record,d3BQuoteTest.supplier,key,200);
    d3BQuoteTest.call("POST",base+"/return-check/claim-stock",record,d3BQuoteTest.supplier,key,200);
    d3BQuoteTest.call("POST","/api/admin/claims/"+claim+"/review",view("expectedVersion",2,"decision","approve","lossFen",1,"remark","核定实际损失"),null,d3BQuoteTest.key(),200);
    d3BQuoteTest.call("POST","/api/me/claims/"+claim+"/confirm",view("expectedVersion",3),d3BQuoteTest.buyer,d3BQuoteTest.key(),200);
    ClaimStock exec=d3BQuoteTest.f.app.getBean(com.mujian.backend.dispute.service.ClaimStock.class);
    exec.applyNext(claim);exec.applyNext(claim);
    assertEquals("repairing",d3BQuoteTest.f.db.queryForObject("SELECT instance_status FROM prop_sn_instance WHERE id=? AND current_order_id IS NULL",String.class,line.get("sn_id")));
    assertEquals("completed",d3BQuoteTest.f.db.queryForObject("SELECT reservation_status FROM inventory_reservation WHERE id=?",String.class,line.get("id")));
    assertEquals(2,d3BQuoteTest.f.db.queryForObject("SELECT SUM(quantity) FROM inventory_reservation WHERE rental_order_id=? AND reservation_status='disputed_holding'",Integer.class,order));
  }
}
