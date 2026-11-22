package com.mujian.backend.dispute.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.enums.OrderParticipantRole;

import com.mujian.backend.material.service.VerificationMaterials;
import com.mujian.backend.order.domain.RentalOrderDO;

import static com.mujian.backend.common.support.D2Values.*;
import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.mapper.*;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.delivery.dto.IssueInput;
import com.mujian.backend.delivery.mapper.*;
import com.mujian.backend.delivery.service.*;
import com.mujian.backend.dispute.dto.ArbitrationInput;
import com.mujian.backend.dispute.mapper.*;
import com.mujian.backend.dispute.mapper.ArbitrationMapper.Row;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.payment.dto.CashComponents;
import java.time.*;
import java.util.*;
import static java.util.stream.Collectors.*;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class Arbitrations {
  public record Decision(String id,String status,String responsibility,String summary,int lossFen,int depositDeductionFen,int receivableFen,int rentDeductionFen,
      CashComponents refunds,List<ArbitrationInput.Outcome> stockOutcomes,String confirmedAt,List<ArbitrationMapper.Ref> executionReferences,String refundBasis){}
  public record Detail(String id,String orderId,String orderNo,String factoryId,int version,String createdAt,String caseNo,String status,String decisionStage,
      String claimId,String issueId,String appealReason,List<String> evidenceIds,Decision decision,String executionStatus,String failureReason,List<String> refundIds,
      String receivableId,List<String> availableActions,String requestedEvidence,String requestedParty,String factoryName,ArbitrationMapper.SourceBalance depositBalance,List<ArbitrationMapper.Reservation> stockOptions,List<IssuePlanMapper.Refund> refunds){}
  private final ArbitrationMapper arbitrationMapper;
  private final ClaimMapper claimMapper;
  private final FulfillmentAccess fulfillmentAccess;
  private final FactoryAccess factoryAccess;
  private final VerificationAccess verificationAccess;
  private final TradingAccess tradingAccess;
  private final TradingOperations tradingOperations;
  private final TradingRoots tradingRoots;
  private final RentalOrderMapper rentalOrderMapper;
  private final AdminCaseMapper adminCaseMapper;
  private final AdminCaseRelationMapper adminCaseRelationMapper;
  private final AdminCaseActionMapper adminCaseActionMapper;
  private final OrderEvidence evidence;
  private final DisputeFunds disputeFunds;
  private final IssuePlanMapper issuePlanMapper;
  private final Clock clock;
  private final ObjectMapper json;
  public Arbitrations(ArbitrationMapper arbitrationMapper,ClaimMapper claimMapper,FulfillmentAccess fulfillmentAccess,FactoryAccess factoryAccess,VerificationAccess verificationAccess,TradingAccess tradingAccess,
    TradingOperations tradingOperations,TradingRoots tradingRoots,RentalOrderMapper rentalOrderMapper,AdminCaseMapper adminCaseMapper,AdminCaseRelationMapper adminCaseRelationMapper,AdminCaseActionMapper adminCaseActionMapper,OrderEvidence evidence,
    DisputeFunds disputeFunds,IssuePlanMapper issuePlanMapper,Clock clock,ObjectMapper json){
    this.arbitrationMapper=arbitrationMapper;this.claimMapper=claimMapper;this.fulfillmentAccess=fulfillmentAccess;this.factoryAccess=factoryAccess;this.verificationAccess=verificationAccess;this.tradingAccess=tradingAccess;this.tradingOperations=tradingOperations;this.tradingRoots=tradingRoots;
    this.rentalOrderMapper=rentalOrderMapper;this.adminCaseMapper=adminCaseMapper;this.adminCaseRelationMapper=adminCaseRelationMapper;this.adminCaseActionMapper=adminCaseActionMapper;this.evidence=evidence;this.disputeFunds=disputeFunds;this.issuePlanMapper=issuePlanMapper;this.clock=clock;this.json=json;
  }
  /** 确定调用者有权读取的数据范围。 */
  private ClaimMapper.Filter scope(String actor,OrderParticipantRole role,String order,String stage){
    String buyer=null,factory=null;
    switch(role){case buyer->{tradingAccess.buyer(actor);buyer=actor;}case admin->{ /* 管理员读写权限统一从本次请求的同一快照取得。 */ }
      case factory->{if(!D2Codes.CODE_SUPPLIER.equals(factoryAccess.context(actor,false).roleCode()))throw ApiFailure.denied(TradingReason.SUPPLIER_ROLE_REQUIRED.name());factory=factoryAccess.profile(actor,false,false).getId();}}
    return new ClaimMapper.Filter(null,order,buyer,factory,role==OrderParticipantRole.admin,stage);
  }
  /** 核验是否拥有裁决操作权限。 */
  private boolean canDecide(String actor, OrderParticipantRole role) {
    return role == OrderParticipantRole.admin && verificationAccess.readPermissions(actor, "admin:arbitration:read").contains("admin:arbitration:decide");
  }

  /** 读取案件关联的裁决内容。 */
  private Decision decision(ArbitrationMapper.Decision d, List<ArbitrationMapper.Ref> refs) {
    return new Decision(d.id(), d.status(), d.responsibility(), d.summary(), d.loss(), d.deposit(), d.excess(), d.rent(),
        json.readValue(d.refunds(), CashComponents.class), List.of(json.readValue(d.stock(), ArbitrationInput.Outcome[].class)),
        time(d.confirmedAt()), refs, d.refundBasis());
  }

  /** 关联裁决、证据及执行结果。 */
  private List<Detail> details(List<Row> rows, OrderParticipantRole role, boolean canDecide) {
    if (rows.isEmpty()) return List.of();
    List<String> ids = rows.stream().map(Row::id).toList();
    List<String> orders = rows.stream().map(Row::orderId).distinct().toList();
    Map<String,ArbitrationMapper.Decision> decisions = arbitrationMapper.selectArbitrationDecisions(ids).stream().collect(toMap(ArbitrationMapper.Decision::caseId, Function.identity()));
    Map<String,List<String>> evidence = arbitrationMapper.evidenceForCases(ids, role.name()).stream().collect(groupingBy(
        ArbitrationMapper.EvidenceLink::caseId, mapping(ArbitrationMapper.EvidenceLink::evidenceId, toList())));
    Map<String,ArbitrationMapper.SourceBalance> balances = arbitrationMapper.selectArbitrationBalances(orders).stream().collect(toMap(ArbitrationMapper.SourceBalance::orderId, Function.identity()));
    List<String> confirmed = decisions.values().stream().filter(d -> "confirmed".equals(d.status())).map(ArbitrationMapper.Decision::id).toList();
    Map<String,List<ArbitrationMapper.ExecutionLink>> links = confirmed.isEmpty() ? Map.of() :
        arbitrationMapper.executionLinks(confirmed).stream().collect(groupingBy(ArbitrationMapper.ExecutionLink::decisionId));
    List<String> editableOrders = rows.stream().filter(r -> !confirmed(decisions.get(r.id()))).map(Row::orderId).distinct().toList();
    Map<String,List<ArbitrationMapper.Reservation>> stock = role != OrderParticipantRole.admin || editableOrders.isEmpty() ? Map.of() :
        arbitrationMapper.stockForOrders(editableOrders).stream().collect(groupingBy(ArbitrationMapper.Reservation::orderId));

    ArrayList<Arbitrations.Detail> result = new ArrayList<Detail>(rows.size());
    for (ArbitrationMapper.Row arbitrationRow : rows) {
      ArbitrationMapper.Decision arbitrationDecision = decisions.get(arbitrationRow.id());
      boolean decided = confirmed(arbitrationDecision);
      String stage = "cancelled".equals(arbitrationRow.status()) ? "historical" : decided ? "decided" : "awaiting_decision";
      boolean waiting = "waiting_evidence".equals(arbitrationRow.status());
      List<String> available = new ArrayList<>();
      if ("awaiting_decision".equals(stage)) {
        if (canDecide) {
          available.addAll(List.of("supplement","request_evidence","draft_decision"));
          if (arbitrationDecision != null && "decision_draft".equals(arbitrationRow.status())) available.add("confirm_decision");
        } else if (waiting && role.name().equals(arbitrationRow.party())) available.add("supplement");
      }
      List<ArbitrationMapper.ExecutionLink> steps = decided ? links.getOrDefault(arbitrationDecision.id(), List.of()) : List.<ArbitrationMapper.ExecutionLink>of();
      List<ArbitrationMapper.Ref> refs = steps.stream().map(v -> new ArbitrationMapper.Ref(v.relationType(), v.relationId())).toList();
      List<IssuePlanMapper.Refund> refunds = steps.stream().filter(v -> v.refundStatus() != null)
          .map(v -> new IssuePlanMapper.Refund(v.relationId(), v.refundStatus(), v.refundFen())).toList();
      Arbitrations.Decision shown = arbitrationDecision != null && (decided || role == OrderParticipantRole.admin) ? decision(arbitrationDecision, refs) : null;
      result.add(new Detail(arbitrationRow.id(), arbitrationRow.orderId(), arbitrationRow.orderNo(), arbitrationRow.factoryId(), arbitrationRow.version(), time(arbitrationRow.createdAt()), arbitrationRow.caseNo(), arbitrationRow.status(), stage,
          arbitrationRow.claimId(), arbitrationRow.issueId(), arbitrationRow.appealReason(), evidence.getOrDefault(arbitrationRow.id(), List.of()), shown, decided ? arbitrationRow.status() : null,
          decided ? arbitrationDecision.failureReason() : null, refs.stream().filter(v -> "refund_order".equals(v.relationType())).map(ArbitrationMapper.Ref::relationId).toList(),
          decided ? arbitrationDecision.receivable() : null, available, waiting ? arbitrationRow.request() : null, waiting ? arbitrationRow.party() : null, arbitrationRow.factoryName(), balances.get(arbitrationRow.orderId()),
          decided ? List.of() : stock.getOrDefault(arbitrationRow.orderId(), List.of()), refunds));
    }
    return result;
  }

  /** 读取已确认的裁决版本。 */
  private static boolean confirmed(ArbitrationMapper.Decision decision) {
    return decision != null && "confirmed".equals(decision.status());
  }

  /** 按当前身份和筛选条件返回分页摘要。 */
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public PageResult<Detail> page(String actor,OrderParticipantRole role,String order,String stage,PageQuery pageQuery){
    boolean decide=canDecide(actor,role);
    ClaimMapper.Filter claimFilter=scope(actor,role,order,stage);
    return PageResult.of(details(arbitrationMapper.selectArbitrationPageByQuery(claimFilter,pageQuery),role,decide),arbitrationMapper.selectArbitrationCaseCountByQuery(claimFilter),pageQuery);
  }
  /** 校验访问范围后返回业务详情。 */
  @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
  public Detail read(String actor,OrderParticipantRole role,String id){
    boolean decide=canDecide(actor,role);
    ClaimMapper.Filter claimFilter=scope(actor,role,null,null);ArbitrationMapper.Row arbitrationRow=arbitrationMapper.selectArbitrationById(id);
    if(arbitrationRow==null || claimFilter.buyerId()!=null && !claimFilter.buyerId().equals(arbitrationRow.buyerId()) || claimFilter.factoryId()!=null && !claimFilter.factoryId().equals(arbitrationRow.factoryId()))throw missing();
    return details(List.of(arbitrationRow),role,decide).get(0);
  }
  /** 核对操作者范围并按业务根加锁。 */
  private Row lock(TradingCommand tradingCommand,String id,OrderParticipantRole role){
    tradingOperations.authorize(tradingCommand.actor(),tradingCommand.operation());ArbitrationMapper.Row arbitrationRow=arbitrationMapper.selectArbitrationById(id);if(arbitrationRow==null)throw missing();
    tradingRoots.lock(fulfillmentAccess.order(tradingCommand.actor(),arbitrationRow.orderId(),role==OrderParticipantRole.factory,role==OrderParticipantRole.admin?"admin:arbitration:decide":null).checkoutBatchId());
    return arbitrationMapper.selectArbitrationById(id);
  }
  /** 校验当前案件是否允许编辑。 */
  private void editable(Row r,int v){
    ArbitrationMapper.Decision arbitrationDecision=arbitrationMapper.selectArbitrationDecision(r.id());
    if(arbitrationDecision!=null && "confirmed".equals(arbitrationDecision.status()) || "cancelled".equals(r.status()))throw conflict(BusinessFailureReason.DECISION_ALREADY_CONFIRMED.name());
    if(r.version()!=v)throw conflict(D2Codes.CODE_VERSION_CONFLICT);
  }
  /** 为来源业务建立关联案件。 */
  @Transactional(propagation=Propagation.MANDATORY)
  public String openCase(String arbId,String order,String type,String source,String actor,LocalDateTime at){
    RentalOrderDO rentalOrderDO=rentalOrderMapper.selectOrderById(order);
    String id=Ids.next();adminCaseMapper.insertAdminCase(new AdminCaseDO().id(id).caseNo("ARB"+id).activeCaseKey("arbitration:"+arbId).caseType("arbitration").caseStatus("pending")
      .priority("P2").title("订单仲裁").rentalOrderId(order).buyerUserId(rentalOrderDO.buyerUserId()).factoryId(rentalOrderDO.factoryId()).createdByType(SystemIdentity.SYSTEM_USER_ID.equals(actor) ? FulfillmentRole.system.name() : FulfillmentRole.admin.name())
      .createdByAdminUserId(actor).createdAt(at).updatedAt(at).version(0));
    adminCaseRelationMapper.insertAdminCaseRelation(new AdminCaseRelationDO().id(Ids.next()).adminCaseId(id).relationType("arbitration_case").relationId(arbId).relationRole("source").isPrimary(1).createdAt(at));
    adminCaseRelationMapper.insertAdminCaseRelation(new AdminCaseRelationDO().id(Ids.next()).adminCaseId(id).relationType(type).relationId(source).relationRole("source").isPrimary(0).createdAt(at));
    return id;
  }
  /** 创建与业务来源关联的处理记录。 */
  public TradingReceipt open(TradingCommand tradingCommand,String order,ArbitrationInput.Open open){
    RentalOrderDO rentalOrderDO=fulfillmentAccess.order(tradingCommand.actor(),order,false,"admin:arbitration:decide");tradingRoots.lock(rentalOrderDO.checkoutBatchId());TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);if(tradingReceipt!=null)return tradingReceipt;
    rentalOrderDO=rentalOrderMapper.selectOrderById(order);if(rentalOrderDO.version()!=open.expectedVersion())throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if(!Set.of("accepted","fulfilling","return_checking").contains(rentalOrderDO.orderStatus()) || issuePlanMapper.selectActive(order,null,true)!=null || issuePlanMapper.hasActiveClaimOrArbitration(order) || ("receipt_problem".equals(open.sourceType()) && issuePlanMapper.selectActive(order,open.sourceId(),false)!=null) || !arbitrationMapper.validSource(order,open.sourceType(),open.sourceId()))
      throw conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name());
    String id=Ids.next();LocalDateTime at=now(clock);arbitrationMapper.insertArbitrationCase(id,order,tradingCommand.actor(),open,at);openCase(id,order,open.sourceType(),open.sourceId(),tradingCommand.actor(),at);
    if("receipt_problem".equals(open.sourceType())){arbitrationMapper.freezeReceipt(order,at);arbitrationMapper.updateOrderIssueReportTransfer(order,open.sourceId(),at);}
    attach(tradingCommand,arbitrationMapper.selectArbitrationById(id),OrderParticipantRole.admin,open.evidence(),at);
    return tradingOperations.record(tradingCommand,"arbitration_case",id,0);
  }
  /** 将已校验的材料关联到业务记录。 */
  private void attach(TradingCommand cmd,Row r,OrderParticipantRole role,IssueInput.Evidence input,LocalDateTime at){
    LinkedHashSet<String> ids=new LinkedHashSet<String>();
    for(String id:input.evidenceIds()){
      if(role==OrderParticipantRole.admin?!arbitrationMapper.caseEvidence(r.id(),id):!claimMapper.usableEvidence(id,r.orderId(),cmd.actor()))throw missing();
      ids.add(id);
    }
    FulfillmentRole party=FulfillmentRole.valueOf(role.name());
    List<VerificationMaterials.VerifiedObject> files=evidence.verifyUploads(cmd.actor(),r.orderId(),EvidenceNode.arbitration,party,input.uploadReceiptIds());
    ids.addAll(evidence.bindVerifiedUploads(cmd.actor(),r.orderId(),EvidenceNode.arbitration,party,files));
    for(String id:ids)if(!arbitrationMapper.selectArbitrationLinked(r.id(),id))claimMapper.arbitrationEvidence(Ids.next(),r.id(),id,role.name(),at);
  }
  /** 记录本次处理动作及操作者。 */
  private String action(Row r,String parent,Integer step,String kind,String state,String module,String type,String target,String actor,String reason,Object payload,LocalDateTime at){
    String id=Ids.next();
    adminCaseActionMapper.insertAdminCaseAction(new AdminCaseActionDO().id(id).actionNo("ARB"+id).adminCaseId(r.caseId()).parentActionId(parent).stepNo(step).actionType(kind)
      .actionStatus(state).targetModule(module).targetType(type).targetId(target).operatorAdminUserId(actor).operationReason(reason)
      .requestPayloadJson(json.writeValueAsString(payload)).operatorConfirmedAt("execute_arbitration".equals(kind)?at:null)
      .executedAt("succeeded".equals(state)?at:null).idempotencyKey(parent==null?"arb-action:"+id:"arb-step:"+parent+":"+step).createdAt(at).updatedAt(at));
    return id;
  }
  /** 登记本次补充材料或说明。 */
  public TradingReceipt supplement(TradingCommand tradingCommand,String id,OrderParticipantRole role,IssueInput.Supplement supplement){
    Row arbitrationRow=lock(tradingCommand,id,role);TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);if(tradingReceipt!=null)return tradingReceipt;editable(arbitrationRow,supplement.expectedVersion());
    if(role!=OrderParticipantRole.admin && !("waiting_evidence".equals(arbitrationRow.status()) && role.name().equals(arbitrationRow.party())))throw conflict(D2Codes.CODE_MATERIAL_REFERENCE_INVALID);
    LocalDateTime at=now(clock);attach(tradingCommand,arbitrationRow,role,supplement.evidence(),at);
    action(arbitrationRow,null,null,"arbitration_supplement","succeeded","06","arbitration_case",id,role==OrderParticipantRole.admin?tradingCommand.actor():SystemIdentity.SYSTEM_USER_ID,supplement.summary(),view("actor",tradingCommand.actor(),"role",role.name()),at);
    arbitrationMapper.retireDraft(id,at);arbitrationMapper.updateArbitrationCaseStage(id,"evidence_review",at);return tradingOperations.recordReason(tradingCommand,"arbitration_case",id,arbitrationRow.version()+1,supplement.summary());
  }
  /** 要求责任方补充指定资料。 */
  public TradingReceipt request(TradingCommand tradingCommand,String id,ArbitrationInput.Request arbitrationInputRequest){
    Row arbitrationRow=lock(tradingCommand,id,OrderParticipantRole.admin);TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);if(tradingReceipt!=null)return tradingReceipt;editable(arbitrationRow,arbitrationInputRequest.expectedVersion());
    LocalDateTime at=now(clock);action(arbitrationRow,null,null,"requestArbitrationEvidence","succeeded","06","arbitration_case",id,tradingCommand.actor(),arbitrationInputRequest.reason(),arbitrationInputRequest,at);
    arbitrationMapper.retireDraft(id,at);arbitrationMapper.updateArbitrationCaseStage(id,"waiting_evidence",at);return tradingOperations.recordReason(tradingCommand,"arbitration_case",id,arbitrationRow.version()+1,arbitrationInputRequest.reason());
  }
  /** 核对裁决涉及的库存处理数量。 */
  private void checkStock(Row r,List<ArbitrationInput.Outcome> outcomes){
    List<ArbitrationMapper.Reservation> lines=arbitrationMapper.selectArbitrationReservations(r.orderId());Map<String,Integer> used=new HashMap<>();
    for(ArbitrationInput.Outcome arbitrationInputOutcome:outcomes){
      ArbitrationMapper.Reservation line=lines.stream().filter(l->l.itemId().equals(arbitrationInputOutcome.itemId()) && Objects.equals(arbitrationInputOutcome.snId(),l.snId())).findFirst().orElseThrow(()->conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name()));
      if("paid_locked".equals(line.status()) && !"no_change".equals(arbitrationInputOutcome.result()) || arbitrationInputOutcome.quantity()<=0 || used.merge(line.id(),arbitrationInputOutcome.quantity(),Math::addExact)>line.quantity() || arbitrationInputOutcome.snId()!=null && ("buyout".equals(arbitrationInputOutcome.result()) || arbitrationInputOutcome.quantity()!=1)
          || arbitrationInputOutcome.snId()==null && "repair".equals(arbitrationInputOutcome.result()))throw conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name());
    }
  }
  /** 核对裁决涉及的资金来源和可用金额。 */
  private int checkFunds(Row r,String responsibility,int loss,int rent,CashComponents cash,boolean terminal){
    if ((loss > 0 || rent > 0) && !("buyer".equals(responsibility) || "shared".equals(responsibility))) {
      throw conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name());
    }
    ArbitrationMapper.SourceBalance sourceBalance=arbitrationMapper.selectArbitrationBalance(r.orderId());int available=sourceBalance.paidFen()-sourceBalance.deductedFen()-sourceBalance.refundedFen()-sourceBalance.reservedFen();
    int deduction=Math.min(loss,Math.max(0,available));
    CashComponents limits=IssuePlans.limits(issuePlanMapper.selectRefundFundingSourcesByOrder(r.orderId(),null),issuePlanMapper.selectOrderDeductionOffsets(r.orderId(),null));
    if(available<0 || (long)deduction+cash.depositFen()>available || (long)rent+cash.rentFen()>limits.rentFen() || cash.freightFen()>limits.freightFen())
      throw conflict(BusinessFailureReason.REFUND_LIMIT_CHANGED.name());
    if(terminal && r.claimId()!=null && deduction+cash.depositFen()!=available)throw conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name());
    return deduction;
  }
  /** 保存待提交的内容与版本。 */
  public Detail draft(TradingCommand tradingCommand,String id,ArbitrationInput arbitrationInput){
    ArbitrationMapper.Row arbitrationRow=lock(tradingCommand,id,OrderParticipantRole.admin);TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);if(tradingReceipt!=null)return details(List.of(arbitrationRow),OrderParticipantRole.admin,true).get(0);
    editable(arbitrationRow,arbitrationInput.expectedVersion());checkStock(arbitrationRow,arbitrationInput.stockOutcomes());int deposit=checkFunds(arbitrationRow,arbitrationInput.responsibility(),arbitrationInput.lossFen(),arbitrationInput.rentDeductionFen(),arbitrationInput.refunds(),false);
    if(arbitrationInput.refunds().totalFen()>0 && (arbitrationInput.refundBasis()==null || arbitrationInput.refundBasis().isBlank()))throw conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name());
    LocalDateTime at=now(clock);arbitrationMapper.retireDraft(id,at);String d=Ids.next();arbitrationMapper.insertArbitrationDecisionDraft(d,arbitrationRow,arbitrationInput,deposit,json.writeValueAsString(arbitrationInput.stockOutcomes()),json.writeValueAsString(arbitrationInput.refunds()),tradingCommand.actor(),at);
    action(arbitrationRow,null,null,"arbitration_draft","succeeded","06","arbitration_decision",d,tradingCommand.actor(),arbitrationInput.summary(),arbitrationInput,at);arbitrationMapper.updateArbitrationCaseStage(id,"decision_draft",at);
    tradingOperations.record(tradingCommand,"arbitration_case",id,arbitrationRow.version()+1);return details(List.of(arbitrationMapper.selectArbitrationById(id)),OrderParticipantRole.admin,true).get(0);
  }
  /** 核验当前版本后确认处理结果。 */
  public TradingReceipt confirm(TradingCommand tradingCommand,String id,ArbitrationInput.Confirm confirm){
    ArbitrationMapper.Row arbitrationRow=lock(tradingCommand,id,OrderParticipantRole.admin);TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);if(tradingReceipt!=null)return tradingReceipt;
    editable(arbitrationRow,confirm.expectedVersion());ArbitrationMapper.Decision stored=arbitrationMapper.selectArbitrationDecision(id);
    if(stored==null || !stored.id().equals(confirm.decisionId()) || !"decision_draft".equals(arbitrationRow.status()))throw conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name());
    Arbitrations.Decision arbitrationsDecision=decision(stored,arbitrationMapper.selectAdminCaseActionRefs(stored.id()));checkStock(arbitrationRow,arbitrationsDecision.stockOutcomes());
    if(checkFunds(arbitrationRow,arbitrationsDecision.responsibility(),arbitrationsDecision.lossFen(),arbitrationsDecision.rentDeductionFen(),arbitrationsDecision.refunds(),true)!=arbitrationsDecision.depositDeductionFen())throw conflict(BusinessFailureReason.REFUND_LIMIT_CHANGED.name());
    LocalDateTime at=now(clock);if(arbitrationMapper.confirmArbitrationDecision(arbitrationsDecision.id(),tradingCommand.actor()+":"+tradingCommand.requestId(),tradingCommand.actor(),at)!=1)throw conflict(BusinessFailureReason.DECISION_ALREADY_CONFIRMED.name());
    String parent=action(arbitrationRow,null,null,"execute_arbitration","executing","06","arbitration_decision",arbitrationsDecision.id(),tradingCommand.actor(),arbitrationsDecision.summary(),arbitrationsDecision,at);
    String debt=disputeFunds.effective(arbitrationRow.orderId(),"arbitration",arbitrationsDecision.id(),arbitrationsDecision.lossFen(),arbitrationsDecision.rentDeductionFen(),arbitrationsDecision.summary(),at);
    List<String> refunds=disputeFunds.refund(arbitrationRow.orderId(),"arbitration_decision",arbitrationsDecision.id(),arbitrationsDecision.refunds(),arbitrationsDecision.refundBasis()==null?"按生效裁决退款":arbitrationsDecision.refundBasis(),tradingCommand.actor());
    int step=0;
    for(String deduction:arbitrationMapper.selectDeductionIdsByDecision(arbitrationsDecision.id()))action(arbitrationRow,parent,++step,"arbitration_deduction","succeeded","04","order_deduction",deduction,tradingCommand.actor(),arbitrationsDecision.summary(),arbitrationsDecision.id(),at);
    if(debt!=null){arbitrationMapper.linkReceivable(arbitrationsDecision.id(),debt);action(arbitrationRow,parent,++step,"arbitration_receivable","succeeded","04","order_compensation_receivable",debt,tradingCommand.actor(),arbitrationsDecision.summary(),arbitrationsDecision.id(),at);}
    for(String refund:refunds)action(arbitrationRow,parent,++step,"arbitration_refund","executing","04","refund_order",refund,tradingCommand.actor(),arbitrationsDecision.summary(),arbitrationsDecision.id(),at);
    for(ArbitrationInput.Outcome out:arbitrationsDecision.stockOutcomes())action(arbitrationRow,parent,++step,"arbitration_stock","executing","02","rental_order_item",out.itemId(),tradingCommand.actor(),arbitrationsDecision.summary(),out,at);
    arbitrationMapper.updateArbitrationCaseStage(id,"executing",at);return tradingOperations.record(tradingCommand,"arbitration_case",id,arbitrationRow.version()+1);
  }
}
