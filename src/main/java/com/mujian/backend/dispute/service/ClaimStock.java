package com.mujian.backend.dispute.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.order.domain.RentalOrderDO;

import static com.mujian.backend.common.support.D2Values.*;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.*;
import com.mujian.backend.delivery.service.*;
import com.mujian.backend.delivery.mapper.ReturnCheckMapper;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.payment.mapper.NormalFundsMapper;
import com.mujian.backend.dispute.mapper.*;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.prop.domain.*;
import com.mujian.backend.prop.mapper.*;
import com.mujian.backend.prop.service.*;
import com.mujian.backend.prop.utils.PropJson;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class ClaimStock {
  public record Input(String claimId,int expectedVersion,String reservationId,String itemId,String snId,int quantity,
                      String outcome,String replacesId,String remark) {}
  public record Result(String id,String reservationId,String itemId,String snId,int quantity,String outcome,String status,String remark,String createdAt,String appliedAt) {}
  public record State(String claimId,String orderId,int version,boolean fundingEffective,boolean canRecord,List<ClaimStockMapper.Line> lines,List<Result> records) {}
  private static final Set<String> WRITABLE=Set.of("submitted","admin_reviewing","supplement_required","waiting_user_response","executing");
  private final ClaimMapper claimMapper;
  private final ClaimStockMapper claimStockMapper;
  private final FulfillmentAccess fulfillmentAccess;
  private final RentalOrderMapper rentalOrderMapper;
  private final TradingRoots tradingRoots;
  private final TradingOperations tradingOperations;
  private final TradingReservations tradingReservations;
  private final InventoryReservationMapper inventoryReservationMapper;
  private final PropInventory inventory;
  private final PropSnInstanceMapper propSnInstanceMapper;
  private final PropInstanceStatusLogMapper propInstanceStatusLogMapper;
  private final PropStockMovementMapper propStockMovementMapper;
  private final PropJson json;
  private final ReturnCheckMapper returnCheckMapper;
  private final FulfillmentEvents fulfillmentEvents;
  private final NormalFundsMapper normalFundsMapper;
  private final Clock clock;
  public ClaimStock(ClaimMapper claimMapper,ClaimStockMapper claimStockMapper,FulfillmentAccess fulfillmentAccess,RentalOrderMapper rentalOrderMapper,
      TradingRoots tradingRoots,TradingOperations tradingOperations,TradingReservations tradingReservations,InventoryReservationMapper inventoryReservationMapper,
      PropInventory inventory,PropSnInstanceMapper propSnInstanceMapper,PropInstanceStatusLogMapper propInstanceStatusLogMapper,PropStockMovementMapper propStockMovementMapper,PropJson json,Clock clock,ReturnCheckMapper returnCheckMapper,FulfillmentEvents fulfillmentEvents,NormalFundsMapper normalFundsMapper){
    this.returnCheckMapper=returnCheckMapper;this.fulfillmentEvents=fulfillmentEvents;this.normalFundsMapper=normalFundsMapper;
    this.claimMapper=claimMapper;this.claimStockMapper=claimStockMapper;this.fulfillmentAccess=fulfillmentAccess;this.rentalOrderMapper=rentalOrderMapper;this.tradingRoots=tradingRoots;this.tradingOperations=tradingOperations;this.tradingReservations=tradingReservations;
    this.inventoryReservationMapper=inventoryReservationMapper;this.inventory=inventory;this.propSnInstanceMapper=propSnInstanceMapper;this.propInstanceStatusLogMapper=propInstanceStatusLogMapper;this.propStockMovementMapper=propStockMovementMapper;this.json=json;this.clock=clock;
  }
  /** 定位本次命令的来源记录。 */
  private ClaimMapper.Row source(String order,String claim){
    ClaimMapper.Row claimRow=claimMapper.selectClaimById(id(claim));
    if(claimRow==null || !claimRow.orderId().equals(order))throw missing();
    return claimRow;
  }
  /** 校验访问范围后返回业务详情。 */
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public State read(String actor,String order,String claim){
    fulfillmentAccess.order(actor,order,true,null);
    ClaimMapper.Row claimRow=source(order,claim);
    return new State(claim,order,claimRow.version(),Set.of("executing","closed").contains(claimRow.status()),
      WRITABLE.contains(claimRow.status()) && (claimRow.arbitrationId()==null || claimStockMapper.arbitrationClosed(claimRow.arbitrationId())),claimStockMapper.selectOrderClaimLines(claim),claimStockMapper.selectOrderClaimStockResultFacts(claim).stream().map(f->new Result(f.id(),f.reservationId(),
      f.itemId(),f.snId(),f.quantity(),f.outcome(),f.status(),f.remark(),time(f.createdAt()),time(f.appliedAt()))).toList());
  }
  /** 按核验结果保存本次业务事实。 */
  public TradingReceipt record(TradingCommand tradingCommand,String order,Input claimStockInput){
    tradingOperations.authorize(tradingCommand.actor(),tradingCommand.operation());
    tradingRoots.lock(fulfillmentAccess.order(tradingCommand.actor(),order,true,null).checkoutBatchId());
    ClaimMapper.Row claimRow=source(order,claimStockInput.claimId());TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);if(tradingReceipt!=null)return tradingReceipt;
    if(!WRITABLE.contains(claimRow.status()) || claimRow.arbitrationId()!=null && !claimStockMapper.arbitrationClosed(claimRow.arbitrationId()))throw conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name());
    if(claimRow.version()!=claimStockInput.expectedVersion())throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if(claimStockInput.quantity()<1 || claimStockInput.remark()==null || claimStockInput.remark().isBlank() || claimStockInput.remark().length()>500 ||
        !Set.of("intact","repair","lost","scrap").contains(claimStockInput.outcome()))throw ApiFailure.invalid();
    ClaimStockMapper.Line line=claimStockMapper.selectOrderClaimLines(claimRow.id()).stream().filter(l->l.reservationId().equals(claimStockInput.reservationId())).findFirst().orElseThrow(()->conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name()));
    if(!line.itemId().equals(claimStockInput.itemId()) || !Objects.equals(line.snId(),claimStockInput.snId()) || line.snId()!=null && claimStockInput.quantity()!=1)throw ApiFailure.invalid();
    if(claimStockInput.replacesId()==null){
      if(claimStockInput.quantity()>line.unrecordedQuantity())throw conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name());
    }else{
      ClaimStockMapper.Fact claimStockFact=claimStockMapper.selectOrderClaimStockResultFacts(claimRow.id()).stream().filter(f->f.id().equals(claimStockInput.replacesId())).findFirst().orElseThrow(()->missing());
      if(!claimStockFact.reservationId().equals(line.reservationId()) || claimStockFact.quantity()!=claimStockInput.quantity() || "repair".equals(claimStockInput.outcome()) ||
          claimStockMapper.supersedeClaimStockResult(claimStockFact.id(),claimRow.id())!=1)throw conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name());
    }
    LocalDateTime at=now(clock);
    claimStockMapper.insertClaimStockResult(new ClaimStockMapper.Fact(Ids.next(),claimRow.id(),line.reservationId(),line.itemId(),line.skuId(),line.snId(),claimStockInput.quantity(),claimStockInput.outcome(),
        "recorded",tradingCommand.actor(),claimStockInput.remark().strip(),FulfillmentRole.factory.name(),null,at,null),claimStockInput.replacesId());
    if(claimStockMapper.advanceClaimVersion(claimRow.id(),claimRow.version(),at)!=1)throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    return tradingOperations.record(tradingCommand,"order_claim",claimRow.id(),claimRow.version()+1);
  }
  /** 资金回应先提交。每次只领取一条未执行实物结果，流水、占用和完成标记同事务。 */
  public boolean applyNext(String claim){
    ClaimMapper.Row first=claimMapper.selectClaimById(claim);if(first==null)return false;
    RentalOrderDO order=rentalOrderMapper.selectOrderById(first.orderId());
    tradingRoots.lock(order.checkoutBatchId());ClaimMapper.Row claimRow=claimMapper.selectClaimById(claim);
    if(!"executing".equals(claimRow.status()) || claimRow.arbitrationId()!=null && !claimStockMapper.arbitrationClosed(claimRow.arbitrationId()))return false;
    List<ClaimStockMapper.Fact> facts=claimStockMapper.selectOrderClaimStockResultFacts(claim);
    ClaimStockMapper.Fact fact=facts.stream().filter(f->"recorded".equals(f.status())).findFirst().orElse(null);
    if(fact==null){
      if(claimStockMapper.selectClaimStockCloseable(claim) && claimStockMapper.selectOrderClaimLines(claim).isEmpty()){
        LocalDateTime at=now(clock);String actor=facts.isEmpty()?SystemIdentity.SYSTEM_USER_ID:facts.get(facts.size()-1).actor();
        if(claimStockMapper.closeCheck(claimRow.orderId(),actor,at)==1){
          returnCheckMapper.finishReturnLeg(order.id(),at);
          fulfillmentEvents.append("claim:"+claim+":stock",order.id(),null,"order_return_check",claimRow.returnCheckId(),FulfillmentEventType.return_check_closed,at,
              actor,actor.equals(SystemIdentity.SYSTEM_USER_ID)?FulfillmentRole.system:FulfillmentRole.factory,"索赔道具均已逐项核验处置");
        }
        if(claimStockMapper.fundsComplete(order.id())){
          claimMapper.updateOrderClaimState(claim,"closed",null,null,null,null,null,at);
          claimMapper.caseState(claimRow.caseId(),"closed",SystemIdentity.SYSTEM_USER_ID,at);
          fulfillmentEvents.append("return:"+claimRow.returnCheckId()+":complete",order.id(),null,"order_return_check",claimRow.returnCheckId(),FulfillmentEventType.return_close_completed,at,
              SystemIdentity.SYSTEM_USER_ID,FulfillmentRole.system,"索赔实物已收尾，押金责任已执行");
          normalFundsMapper.settleNoPool(order.id(),at);normalFundsMapper.tryComplete(order.id(),at);
        }
      }
      return false;
    }
    tradingReservations.lockExisting(order.checkoutBatchId());
    ClaimStockMapper.Line line=claimStockMapper.selectOrderClaimLines(claim).stream().filter(l->l.reservationId().equals(fact.reservationId())).findFirst().orElseThrow(()->conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name()));
    if(line.heldQuantity()<fact.quantity())throw conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name());
    if(line.snId()==null && "repair".equals(fact.outcome())){claimStockMapper.repairPending(fact.id());return true;}
    String release=release(order.id(),line.heldQuantity(),"disputed_holding",fact,null);
    claimStockMapper.updateOrderClaimStockResultApplied(fact.id(),release,now(clock));return true;
  }
  /** 按已生效裁决处理相关库存。 */
  @Transactional(propagation=Propagation.MANDATORY)
  public void adjudicate(ArbitrationMapper.Row row,ArbitrationMapper.Reservation line,String step,String actor,String outcome,int quantity){
    RentalOrderDO order=rentalOrderMapper.selectOrderById(row.orderId());tradingReservations.lockExisting(order.checkoutBatchId());
    if(line.quantity()<quantity || "paid_locked".equals(line.status()))throw conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name());
    ClaimStockMapper.Fact fact=new ClaimStockMapper.Fact(step,row.claimId(),line.id(),line.itemId(),line.skuId(),line.snId(),quantity,outcome,"recorded",actor,"执行仲裁实物处置","arbitration",null,now(clock),null);
    release(row.orderId(),line.quantity(),line.status(),fact,row.id());
    if(row.claimId()!=null)claimStockMapper.replaceDisplaced(row.claimId(),line.id(),line.quantity()-quantity);
  }
  /** 释放对应来源的库存占用。 */
  private String release(String order,int held,String beforeState,ClaimStockMapper.Fact fact,String arbitration){
    LocalDateTime at=now(clock);boolean adjudicated=arbitration!=null;
    PropSkuStockDO before=fact.snId()==null?inventory.stock(fact.skuId(),true):null;
    String release=fact.reservationId();
    if(held>fact.quantity()){
      release=Ids.next();
      if(claimStockMapper.insertInventoryReservationSplit(release,fact,at)!=1 || claimStockMapper.updateInventoryReservationReduce(fact,at)!=1)throw conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name());
    }else if(inventoryReservationMapper.transitionFulfillment(release,order,beforeState,false,at)!=1)throw conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name());
    if(fact.snId()!=null)applyInstance(order,fact,at,arbitration);
    else{
      boolean loss=!"intact".equals(fact.outcome());
      if(loss && claimStockMapper.updatePropSkuStockLoss(fact.skuId(),fact.quantity(),"lost".equals(fact.outcome()),"scrap".equals(fact.outcome()),at)!=1)throw conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name());
      tradingReservations.refreshCurrent(List.of(fact.skuId()));
      PropSkuStockDO after=inventory.stock(fact.skuId(),false);
      propStockMovementMapper.insertPropStockMovement(new PropStockMovementDO().id(Ids.next()).skuId(fact.skuId()).movementType(loss?(adjudicated?"arbitration_decrease":"abnormal_decrease"):"return")
          .sourceType(adjudicated?"arbitration":"order_fulfillment").relatedArbitrationId(arbitration).quantity(loss?-fact.quantity():fact.quantity())
          .beforeQuantity(loss?before.getTotalQuantity():before.getRentingQuantity()).afterQuantity(loss?after.getTotalQuantity():after.getRentingQuantity())
          .beforeStockSnapshotJson(json.write(inventory.stockSnapshot(before))).afterStockSnapshotJson(json.write(inventory.stockSnapshot(after)))
          .relatedOrderId(order).relatedReservationId(release).operatorUserId(fact.actor()).operatorRole(adjudicated?FulfillmentRole.admin.name():FulfillmentRole.factory.name()).remark(fact.remark())
          .businessKey("claim-stock:"+fact.id()).createdAt(at));
    }
    return release;
  }
  /** 对指定实例执行已批准的库存处理。 */
  private void applyInstance(String order,ClaimStockMapper.Fact f,LocalDateTime at,String arbitration){
    PropSnInstanceDO propSnInstanceDO=inventory.sn(f.snId(),true);
    if(propSnInstanceDO.getCurrentOrderId()!=null && !order.equals(propSnInstanceDO.getCurrentOrderId()))throw conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name());
    String from=propSnInstanceDO.getInstanceStatus();
    String to=switch(f.outcome()){case "intact"->"renting".equals(from)?"idle":from;case "repair"->"repairing";case "lost"->"lost";case "scrap"->"scrapped";default->throw ApiFailure.invalid();};
    if(Set.of("lost","scrapped","retired").contains(from) && !from.equals(to))throw conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name());
    if(propSnInstanceMapper.transitionFulfillment(propSnInstanceDO.getId(),propSnInstanceDO.getVersion(),to,null,f.actor(),at)!=1)throw conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name());
    propInstanceStatusLogMapper.insertPropInstanceStatusLog(new PropInstanceStatusLogDO().id(Ids.next()).propInstanceId(propSnInstanceDO.getId()).skuId(propSnInstanceDO.getSkuId()).snCode(propSnInstanceDO.getSnCode())
      .qrCodeId(propSnInstanceDO.getQrCodeId()).fromStatus(from).toStatus(to).sourceType(arbitration==null?"order_fulfillment":"arbitration").relatedArbitrationId(arbitration).reason(f.remark()).operatorUserId(f.actor()).operatorRole(arbitration==null?FulfillmentRole.factory.name():FulfillmentRole.admin.name())
      .relatedOrderId(order).businessKey("claim-stock:"+f.id()).createdAt(at));
  }
}
