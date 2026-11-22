package com.mujian.backend.dispute.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.common.enums.OrderParticipantRole;

import com.mujian.backend.delivery.domain.OrderReturnCheckDO;
import com.mujian.backend.material.service.VerificationMaterials;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.prop.domain.InventoryReservationDO;

import static com.mujian.backend.common.support.D2Values.*;
import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.mapper.*;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.delivery.dto.IssueInput;
import com.mujian.backend.delivery.mapper.*;
import com.mujian.backend.delivery.policy.FulfillmentProtection;
import com.mujian.backend.delivery.service.*;
import com.mujian.backend.dispute.dto.ClaimInput;
import com.mujian.backend.dispute.mapper.ClaimMapper;
import com.mujian.backend.dispute.mapper.ClaimMapper.Row;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.prop.service.TradingReservations;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class ClaimService {
  public record Detail(String id,String orderId,String orderNo,String factoryId,int version,String createdAt,String returnCheckId,String caseId,String status,
      String problemType,String summary,Integer claimedFen,Integer approvedLossFen,Integer availableDepositFen,String responseDeadlineAt,String reviewRemark,
      String appealReason,String arbitrationId,List<String> evidenceIds,List<String> availableActions,List<IssueInput.Item> items,String factoryName) {}
  private final Arbitrations arbitrations;
  private final ClaimMapper claimMapper;
  private final FulfillmentAccess fulfillmentAccess;
  private final FactoryAccess factoryAccess;
  private final TradingAccess tradingAccess;
  private final VerificationAccess verificationAccess;
  private final TradingOperations tradingOperations;
  private final TradingRoots tradingRoots;
  private final ReturnCheckMapper returnCheckMapper;
  private final OrderIssueMapper orderIssueMapper;
  private final RentalOrderMapper rentalOrderMapper;
  private final FulfillmentProtection fulfillmentProtection;
  private final OrderEvidence evidence;
  private final AdminCaseMapper adminCaseMapper;
  private final AdminCaseRelationMapper adminCaseRelationMapper;
  private final AdminCaseActionMapper adminCaseActionMapper;
  private final DisputeFunds disputeFunds;
  private final TradingReservations tradingReservations;
  private final ObjectMapper json;
  private final Clock clock;
  public ClaimService(ClaimMapper claimMapper,FulfillmentAccess fulfillmentAccess,FactoryAccess factoryAccess,TradingAccess tradingAccess,VerificationAccess verificationAccess,TradingOperations tradingOperations,
      TradingRoots tradingRoots,ReturnCheckMapper returnCheckMapper,OrderIssueMapper orderIssueMapper,RentalOrderMapper rentalOrderMapper,FulfillmentProtection fulfillmentProtection,OrderEvidence evidence,
      AdminCaseMapper adminCaseMapper,AdminCaseRelationMapper adminCaseRelationMapper,AdminCaseActionMapper adminCaseActionMapper,DisputeFunds disputeFunds,TradingReservations tradingReservations,ObjectMapper json,Clock clock,Arbitrations arbitrations){
    this.arbitrations=arbitrations;
    this.claimMapper=claimMapper;this.fulfillmentAccess=fulfillmentAccess;this.factoryAccess=factoryAccess;this.tradingAccess=tradingAccess;this.verificationAccess=verificationAccess;this.tradingOperations=tradingOperations;this.tradingRoots=tradingRoots;
    this.returnCheckMapper=returnCheckMapper;this.orderIssueMapper=orderIssueMapper;this.rentalOrderMapper=rentalOrderMapper;this.fulfillmentProtection=fulfillmentProtection;this.evidence=evidence;this.adminCaseMapper=adminCaseMapper;this.adminCaseRelationMapper=adminCaseRelationMapper;this.adminCaseActionMapper=adminCaseActionMapper;
    this.disputeFunds=disputeFunds;this.tradingReservations=tradingReservations;this.json=json;this.clock=clock;
  }
  /** 组合当前身份可见范围及查询条件。 */
  private ClaimMapper.Filter filter(String actor,OrderParticipantRole role,String id,String order,String status) {
    String buyer=null,factory=null;
    switch(role){
      case buyer->{tradingAccess.buyer(actor);buyer=actor;}
      case factory->{if(!D2Codes.CODE_SUPPLIER.equals(factoryAccess.context(actor,false).roleCode()))throw ApiFailure.denied(TradingReason.SUPPLIER_ROLE_REQUIRED.name());
        factory=factoryAccess.profile(actor,false,false).getId();}
      case admin->{ /* 管理员读写权限统一从本次请求的同一快照取得。 */ }
    }
    return new ClaimMapper.Filter(id,order,buyer,factory,role==OrderParticipantRole.admin,status);
  }
  /** 按当前身份和筛选条件返回分页摘要。 */
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public PageResult<Detail> page(String actor,OrderParticipantRole role,String order,String status,PageQuery page){
    boolean review=canReview(actor,role);
    ClaimMapper.Filter claimFilter=filter(actor,role,null,order,status);
    return PageResult.of(claimMapper.selectOrderClaimPageByQuery(claimFilter,page).stream().map(r->detail(r,role,review)).toList(),claimMapper.selectOrderClaimCountByQuery(claimFilter),page);
  }
  /** 校验访问范围后返回业务详情。 */
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public Detail read(String actor,OrderParticipantRole role,String id){
    boolean review=canReview(actor,role);
    List<ClaimMapper.Row> list=claimMapper.selectOrderClaimPageByQuery(filter(actor,role,id,null,null),new PageQuery(1,1));
    if(list.isEmpty())throw missing();return detail(list.get(0),role,review);
  }
  /** 从存储记录读取证据引用。 */
  private List<String> refs(Row r){return r.evidence()==null?List.of():Arrays.asList(json.readValue(r.evidence(),String[].class));}
  /** 按本请求已核实的权限判断审核按钮。 */
  private boolean canReview(String actor, OrderParticipantRole role) {
    return role == OrderParticipantRole.admin && verificationAccess.readPermissions(actor, "admin:claim:read").contains("admin:claim:review");
  }
  /** 组装当前身份有权查看的明细与操作。 */
  private Detail detail(Row r,OrderParticipantRole role,boolean canReview){
    List<String> available=new ArrayList<>();
    if(role==OrderParticipantRole.factory){
      if("draft".equals(r.status()))available.addAll(List.of("edit","submit"));
      if(Set.of("draft","submitted","admin_reviewing","supplement_required").contains(r.status()))available.add("withdraw");
      if("supplement_required".equals(r.status()))available.add("supplement");
    }
    if(canReview && Set.of("submitted","admin_reviewing").contains(r.status()))available.add("review");
    if(role==OrderParticipantRole.buyer && "waiting_user_response".equals(r.status()) && now(clock).isBefore(r.responseDeadlineAt()))available.addAll(List.of("confirm","appeal"));
    return new Detail(r.id(),r.orderId(),r.orderNo(),r.factoryId(),r.version(),time(r.createdAt()),r.returnCheckId(),r.caseId(),r.status(),r.problemType(),
        r.summary(),r.claimedFen(),r.approvedLossFen(),r.availableDepositFen(),time(r.responseDeadlineAt()),r.reviewRemark(),r.appealReason(),r.arbitrationId(),
        refs(r),available,r.items()==null?List.of():Arrays.asList(json.readValue(r.items(),IssueInput.Item[].class)),r.factoryName());
  }

  /** 核对操作者范围并按业务根加锁。 */
  private Row lock(TradingCommand tradingCommand,String id,OrderParticipantRole role){
    tradingOperations.authorize(tradingCommand.actor(),tradingCommand.operation()); Row claimRow=claimMapper.selectClaimById(id);if(claimRow==null)throw missing();
    RentalOrderDO rentalOrderDO=fulfillmentAccess.order(tradingCommand.actor(),claimRow.orderId(),role==OrderParticipantRole.factory,role==OrderParticipantRole.admin?"admin:claim:review":null);
    if(role==OrderParticipantRole.buyer && claimRow.responseDeadlineAt()==null || role==OrderParticipantRole.admin && claimRow.caseId()==null)throw missing();
    // 锁前读取只定位合并付款根，锁后重新读取索赔状态用于本次命令。
    tradingRoots.lock(rentalOrderDO.checkoutBatchId());
    return claimMapper.selectClaimById(id);
  }
  /** 拒绝基于旧版本的写操作。 */
  private void version(Row r,int v){if(r.version()!=v)throw conflict(D2Codes.CODE_VERSION_CONFLICT);}
  /** 校验当前业务状态。 */
  private void state(Row r,String... allowed){if(!Arrays.asList(allowed).contains(r.status()))throw conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name());}

  /** 保存待提交的内容与版本。 */
  public TradingReceipt draft(TradingCommand tradingCommand,String target,ClaimInput claimInput,boolean create){
    String id=target,order;
    if(create){
      RentalOrderDO rentalOrderDO=fulfillmentAccess.order(tradingCommand.actor(),target,true,null);tradingRoots.lock(rentalOrderDO.checkoutBatchId());order=rentalOrderDO.id();
    }else{ClaimMapper.Row claimRow=lock(tradingCommand,id,OrderParticipantRole.factory);order=claimRow.orderId();}
    // 锁内优先返回原命令的回执，避免再次推进状态或占用资金。
    TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);if(tradingReceipt!=null)return tradingReceipt;
    OrderReturnCheckDO check=returnCheckMapper.lockCheck(order);if(check==null || check.closedAt()!=null)throw conflict(FulfillmentReason.RETURN_ALREADY_CLOSED.name());
    if(check.claimCreated()!=0)throw conflict(FulfillmentReason.RETURN_ALREADY_CLOSED.name());
    if(create){
      if(check.version()!=claimInput.expectedVersion())throw conflict(D2Codes.CODE_VERSION_CONFLICT);
      id=Ids.next();claimMapper.insertOrderClaimDraft(id,order,check.id(),tradingCommand.actor(),tradingCommand.actor()+":"+tradingCommand.requestId(),now(clock));
    }else{ClaimMapper.Row existingClaim=claimMapper.selectClaimById(id);state(existingClaim,"draft");version(existingClaim,claimInput.expectedVersion());}
    if(claimInput.items()!=null){
      List<OrderIssueMapper.ItemSource> allowed=orderIssueMapper.selectOrderItems(order);
      Set<String> seen=new HashSet<>();
      for(IssueInput.Item item:claimInput.items())if(!seen.add(item.itemId()) || allowed.stream().noneMatch(v->v.id().equals(item.itemId()) && v.quantity()>=item.quantity()))throw ApiFailure.invalid();
    }
    ClaimMapper.Row draftClaim=claimMapper.selectClaimById(id);
    List<String> ids=claimInput.evidence()==null?refs(draftClaim):attachments(tradingCommand,draftClaim,claimInput.evidence(),FulfillmentRole.factory,EvidenceNode.claim);
    claimMapper.updateOrderClaim(id,claimInput,json.writeValueAsString(claimInput.items()==null?List.of():claimInput.items()),json.writeValueAsString(ids),now(clock),!create);
    return tradingOperations.record(tradingCommand,"order_claim",id,claimMapper.selectClaimById(id).version());
  }

  /** 验证材料归属后绑定到当前申请。 */
  private List<String> attachments(TradingCommand cmd,Row row,IssueInput.Evidence input,FulfillmentRole role,EvidenceNode node){
    LinkedHashSet<String> selected=new LinkedHashSet<String>();
    for(String id:input.evidenceIds()){
      if(!claimMapper.usableEvidence(id,row.orderId(),cmd.actor()))throw missing();
      selected.add(id);
    }
    List<VerificationMaterials.VerifiedObject> verified=evidence.verifyUploads(cmd.actor(),row.orderId(),node,role,input.uploadReceiptIds());
    List<String> uploaded=evidence.bindVerifiedUploads(cmd.actor(),row.orderId(),node,role,verified);
    if(node==EvidenceNode.claim && !uploaded.isEmpty())claimMapper.bindUploads(row.id(),row.orderId(),uploaded);
    selected.addAll(uploaded);return List.copyOf(selected);
  }

  /** 校验来源与版本并提交申请。 */
  public TradingReceipt submit(TradingCommand tradingCommand,String id,int version){
    ClaimMapper.Row claimRow=lock(tradingCommand,id,OrderParticipantRole.factory);
    // 锁内优先返回原命令的回执，避免再次推进状态或占用资金。
    TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);if(tradingReceipt!=null)return tradingReceipt;
    state(claimRow,"draft");version(claimRow,version);LocalDateTime at=now(clock);
    OrderReturnCheckDO check=returnCheckMapper.lockCheck(claimRow.orderId());
    if(check==null || check.closedAt()!=null)throw conflict(FulfillmentReason.RETURN_ALREADY_CLOSED.name());
    if(check.claimCreated()!=0 || !fulfillmentProtection.writable(fulfillmentProtection.facts(claimRow.orderId())))throw conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name());
    if(check.autoCloseAt()==null || !at.isBefore(check.autoCloseAt()))throw conflict(BusinessFailureReason.D5_DEADLINE_REACHED.name());
    if(claimRow.problemType()==null || claimRow.summary()==null || claimRow.claimedFen()==null || refs(claimRow).isEmpty() || claimRow.items()==null || json.readTree(claimRow.items()).isEmpty())throw ApiFailure.invalid();
    List<InventoryReservationDO> held=tradingReservations.lockExisting(rentalOrderMapper.selectOrderById(claimRow.orderId()).checkoutBatchId());
    if(claimMapper.updateOrderReturnCheckFreeze(check.id(),at)!=1)throw conflict(BusinessFailureReason.CLAIM_STATE_CHANGED.name());
    claimMapper.freezeInventory(claimRow.orderId(),at);claimMapper.freezeDeposit(claimRow.orderId(),at);
    tradingReservations.refreshCurrent(held.stream().map(x->x.getSkuId()).toList());
    String c=Ids.next();
    adminCaseMapper.insertAdminCase(new AdminCaseDO().id(c).caseNo("CLA"+c).activeCaseKey("claim:"+id).caseType("claim_review").caseStatus("pending").priority("P2")
      .title("工厂归还索赔").rentalOrderId(claimRow.orderId()).buyerUserId(claimRow.buyerId()).factoryId(claimRow.factoryId()).createdByType(FulfillmentRole.system.name())
      .createdByAdminUserId(SystemIdentity.SYSTEM_USER_ID).version(0).createdAt(at).updatedAt(at));
    relation(c,"order_claim",id,true,at);
    for(String ref:refs(claimRow))relation(c,"order_evidence_record",ref,false,at);
    claimMapper.updateOrderClaimState(id,"admin_reviewing","pending",null,null,null,null,at);
    return tradingOperations.record(tradingCommand,"order_claim",id,version+1);
  }
  /** 登记案件与来源记录之间的关系。 */
  private void relation(String c,String type,String id,boolean primary,LocalDateTime at){
    adminCaseRelationMapper.insertAdminCaseRelation(new AdminCaseRelationDO().id(Ids.next()).adminCaseId(c).relationType(type).relationId(id)
        .relationRole(primary?"source":"evidence_context").isPrimary(primary?1:0).createdAt(at));
  }
  /** 记录本次处理动作及操作者。 */
  private void action(Row r,TradingCommand cmd,String reason,LocalDateTime at){
    if(r.caseId()==null)return;
    String id=Ids.next();
    adminCaseActionMapper.insertAdminCaseAction(new AdminCaseActionDO().id(id).actionNo("CLA"+id).adminCaseId(r.caseId()).actionType(cmd.operation().name()).actionStatus("succeeded")
      .targetModule("06").targetType("order_claim").targetId(r.id()).operatorAdminUserId(cmd.operation()==TradingOperation.reviewClaim?cmd.actor():SystemIdentity.SYSTEM_USER_ID)
      .requestPayloadJson(json.writeValueAsString(view("actor",cmd.actor(),"version",r.version(),"reason",reason))).operationReason(reason)
      .idempotencyKey("claim-action:"+cmd.actor()+":"+cmd.requestId()).executedAt(at).createdAt(at).updatedAt(at));
  }
  /** 核实当前状态后撤回申请。 */
  public TradingReceipt withdraw(TradingCommand tradingCommand,String id,int version,String reason){
    ClaimMapper.Row claimRow=lock(tradingCommand,id,OrderParticipantRole.factory);
    // 锁内优先返回原命令的回执，避免再次推进状态或占用资金。
    TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);if(tradingReceipt!=null)return tradingReceipt;
    state(claimRow,"draft","submitted","admin_reviewing","supplement_required");version(claimRow,version);
    LocalDateTime at=now(clock);claimMapper.updateOrderClaimState(id,"withdrawn",null,null,null,null,null,at);
    if(claimRow.caseId()!=null){action(claimRow,tradingCommand,reason,at);claimMapper.caseState(claimRow.caseId(),"closed",SystemIdentity.SYSTEM_USER_ID,at);resume(claimRow);}
    return tradingOperations.recordReason(tradingCommand,"order_claim",id,version+1,reason);
  }
  /** 恢复因本次申请暂停的后续处理。 */
  private void resume(Row r){claimMapper.releaseClaim(r.returnCheckId(),now(clock));restore(r.orderId());}
  /** 恢复被冻结的资源及计时。 */
  public void restore(String order){
    tradingRoots.lock(rentalOrderMapper.selectOrderById(order).checkoutBatchId());
    if(claimMapper.updateOrderReturnCheckResume(order,now(clock))){claimMapper.thawInventory(order,now(clock));claimMapper.thawDeposit(order,now(clock));}
  }
  /** 按审核决定登记状态及原因。 */
  public TradingReceipt review(TradingCommand tradingCommand,String id,ClaimInput.Review review){
    ClaimMapper.Row claimRow=lock(tradingCommand,id,OrderParticipantRole.admin);
    // 锁内优先返回原命令的回执，避免再次推进状态或占用资金。
    TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);if(tradingReceipt!=null)return tradingReceipt;state(claimRow,"submitted","admin_reviewing");version(claimRow,review.expectedVersion());
    LocalDateTime at=now(clock);
    switch(review.decision()){
      case "approve"->{claimMapper.updateOrderClaimState(id,"waiting_user_response","approved",review.remark(),review.lossFen(),disputeFunds.deposit(claimRow.orderId()),tradingCommand.actor(),at);
        claimMapper.caseState(claimRow.caseId(),"waiting_user",tradingCommand.actor(),at);}
      case "reject"->{claimMapper.updateOrderClaimState(id,"rejected","rejected",review.remark(),null,null,tradingCommand.actor(),at);claimMapper.caseState(claimRow.caseId(),"closed",tradingCommand.actor(),at);resume(claimRow);}
      case "supplement"->{claimMapper.updateOrderClaimState(id,"supplement_required","need_more_evidence",review.remark(),null,null,tradingCommand.actor(),at);claimMapper.caseState(claimRow.caseId(),"waiting_factory",tradingCommand.actor(),at);}
      default->throw ApiFailure.invalid();
    }
    action(claimRow,tradingCommand,review.remark(),at);return tradingOperations.recordReason(tradingCommand,"order_claim",id,claimRow.version()+1,review.remark());
  }
  /** 登记本次补充材料或说明。 */
  public TradingReceipt supplement(TradingCommand tradingCommand,String id,IssueInput.Supplement supplement){
    ClaimMapper.Row claimRow=lock(tradingCommand,id,OrderParticipantRole.factory);
    // 锁内优先返回原命令的回执，避免再次推进状态或占用资金。
    TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);if(tradingReceipt!=null)return tradingReceipt;state(claimRow,"supplement_required");version(claimRow,supplement.expectedVersion());
    LocalDateTime at=now(clock);LinkedHashSet<String> all=new LinkedHashSet<>(refs(claimRow));
    for(String ref:attachments(tradingCommand,claimRow,supplement.evidence(),FulfillmentRole.factory,EvidenceNode.claim))if(all.add(ref))relation(claimRow.caseId(),"order_evidence_record",ref,false,at);
    claimMapper.updateOrderClaimEvidence(id,json.writeValueAsString(all),at);claimMapper.updateOrderClaimState(id,"admin_reviewing","pending",null,null,null,null,at);
    action(claimRow,tradingCommand,supplement.summary(),at);claimMapper.caseState(claimRow.caseId(),"pending",null,at);
    return tradingOperations.recordReason(tradingCommand,"order_claim",id,claimRow.version()+1,supplement.summary());
  }
  /** 记录申请相关方对审核结果的答复。 */
  public TradingReceipt respond(TradingCommand tradingCommand,String id,int version,IssueInput.Supplement appeal){
    ClaimMapper.Row claimRow=lock(tradingCommand,id,OrderParticipantRole.buyer);
    // 锁内优先返回原命令的回执，避免再次推进状态或占用资金。
    TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);if(tradingReceipt!=null)return tradingReceipt;state(claimRow,"waiting_user_response");version(claimRow,version);
    LocalDateTime at=now(clock);if(!at.isBefore(claimRow.responseDeadlineAt()))throw conflict(BusinessFailureReason.D5_DEADLINE_REACHED.name());
    if(appeal!=null){
      List<String> ids=attachments(tradingCommand,claimRow,appeal.evidence(),FulfillmentRole.buyer,EvidenceNode.arbitration);
      String arb=Ids.next();claimMapper.insertClaimUserResponse(Ids.next(),claimRow,"appeal",appeal.summary(),at);claimMapper.insertArbitrationCaseAppeal(arb,claimRow,tradingCommand.actor(),appeal.summary(),at);
      arbitrations.openCase(arb,claimRow.orderId(),"order_claim",id,SystemIdentity.SYSTEM_USER_ID,at);
      for(String ref:refs(claimRow))claimMapper.arbitrationEvidence(Ids.next(),arb,ref,FulfillmentRole.factory.name(),at);
      for(String ref:ids)if(!refs(claimRow).contains(ref))claimMapper.arbitrationEvidence(Ids.next(),arb,ref,FulfillmentRole.buyer.name(),at);
      claimMapper.updateOrderClaimState(id,"user_appealed",null,null,null,null,null,at);
    }else effective(claimRow,false,at);
    return tradingOperations.record(tradingCommand,"order_claim",id,claimMapper.selectClaimById(id).version());
  }
  /** 执行已生效的资金及库存处理。 */
  private void effective(Row r,boolean timeout,LocalDateTime at){
    claimMapper.insertClaimUserResponse(Ids.next(),r,timeout?"timeout_auto_confirm":"confirm",null,at);
    // 同一订单锁内先登记已生效的损失，再按剩余可退押金生成退款义务。
    disputeFunds.effective(r.orderId(),"claim",r.id(),r.approvedLossFen(),0,r.summary(),at);
    disputeFunds.refund(r.orderId(),"order_claim",r.id(),new com.mujian.backend.payment.dto.CashComponents(0,disputeFunds.deposit(r.orderId()),0),"按普通索赔核定结果退还剩余押金",SystemIdentity.SYSTEM_USER_ID);
    claimMapper.updateOrderClaimState(r.id(),"executing",null,null,null,null,null,at);
  }
  /** 在业务锁内处理到期仍未答复的索赔。 */
  public void expire(String id){
    ClaimMapper.Row claimRow=claimMapper.selectClaimById(id);if(claimRow==null)return;tradingRoots.lock(rentalOrderMapper.selectOrderById(claimRow.orderId()).checkoutBatchId());
    claimRow=claimMapper.selectClaimById(id);LocalDateTime at=now(clock);
    if("waiting_user_response".equals(claimRow.status()) && !at.isBefore(claimRow.responseDeadlineAt()))effective(claimRow,true,at);
  }
}
