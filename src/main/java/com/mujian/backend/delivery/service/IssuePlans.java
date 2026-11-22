package com.mujian.backend.delivery.service;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.enums.FulfillmentReason;

import com.mujian.backend.casework.dto.CaseContact;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.payment.domain.ChannelSuborderDO;
import com.mujian.backend.prop.domain.InventoryReservationDO;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.AdminCaseActionDO;
import com.mujian.backend.casework.mapper.AdminCaseActionMapper;
import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.common.support.SystemIdentity;
import com.mujian.backend.delivery.dto.IssuePlan;
import com.mujian.backend.delivery.mapper.IssuePlanMapper;
import com.mujian.backend.delivery.mapper.IssueReviewMapper;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.TradingOperations;
import com.mujian.backend.order.service.TradingRoots;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.payment.dto.CashComponents;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import com.mujian.backend.payment.mapper.RefundMapper;
import com.mujian.backend.payment.service.RefundObligations;
import com.mujian.backend.prop.mapper.InventoryReservationMapper;
import com.mujian.backend.prop.service.TradingReservations;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 一个父动作对应一个确认版本，执行结果均由该版本的实际退款和实物来源读取。 */
@Service
public class IssuePlans {
  private final OverdueFees overdueFees;
  private final IssuePlanMapper issuePlanMapper;
  private final IssueReviewMapper issueReviewMapper;
  private final DisputeGuard guard;
  private final AdminCaseActionMapper adminCaseActionMapper;
  private final TradingOperations tradingOperations;
  private final TradingRoots tradingRoots;
  private final FulfillmentAccess fulfillmentAccess;
  private final TradingReservations tradingReservations;
  private final InventoryReservationMapper inventoryReservationMapper;
  private final RefundMapper refundMapper;
  private final PaymentOrderMapper paymentOrderMapper;
  private final RefundObligations obligations;
  private final ReceiptTimerRecovery timers;
  private final ObjectMapper json;
  private final Clock clock;
  private final com.mujian.backend.order.service.OrderExtensions extensions;

  public IssuePlans(IssuePlanMapper issuePlanMapper,IssueReviewMapper issueReviewMapper,AdminCaseActionMapper adminCaseActionMapper,TradingOperations tradingOperations,
      TradingRoots tradingRoots,FulfillmentAccess fulfillmentAccess,TradingReservations tradingReservations,InventoryReservationMapper inventoryReservationMapper,
      RefundMapper refundMapper,PaymentOrderMapper paymentOrderMapper,RefundObligations obligations,ReceiptTimerRecovery timers,ObjectMapper json,Clock clock,DisputeGuard guard,com.mujian.backend.order.service.OrderExtensions extensions,OverdueFees overdueFees) {
    this.overdueFees=overdueFees;
    this.guard=guard;this.extensions=extensions;
    this.issuePlanMapper=issuePlanMapper; this.issueReviewMapper=issueReviewMapper; this.adminCaseActionMapper=adminCaseActionMapper; this.tradingOperations=tradingOperations; this.tradingRoots=tradingRoots; this.fulfillmentAccess=fulfillmentAccess;
    this.tradingReservations=tradingReservations; this.inventoryReservationMapper=inventoryReservationMapper; this.refundMapper=refundMapper; this.paymentOrderMapper=paymentOrderMapper;
    this.obligations=obligations; this.timers=timers; this.json=json; this.clock=clock;
  }

  /** 登记待处理的问题及其来源。 */
  private IssueReviewMapper.Row issue(String id) {
    List<IssueReviewMapper.Row> rows=issueReviewMapper.selectRows(new IssueReviewMapper.Filter(id,null,null,null),new PageQuery(1,1));
    if (rows.isEmpty()) throw missing();
    return rows.get(0);
  }

  /** 按同一有效版本替换尚未向渠道提交的义务；任一步失败整笔回滚。 */
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public TradingReceipt confirm(TradingCommand tradingCommand,String issueId,IssuePlan issuePlan) {
    tradingOperations.authorize(tradingCommand.actor(),tradingCommand.operation());
    IssueReviewMapper.Row initial=issue(issueId);
    RentalOrderDO order=fulfillmentAccess.order(tradingCommand.actor(),initial.orderId(),false,tradingCommand.operation().permission());
    tradingRoots.lock(order.checkoutBatchId());
    TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);
    if (tradingReceipt!=null) return tradingReceipt;
    IssueReviewMapper.Row issueReviewRow=issue(issueId);
    if (issueReviewRow.version()!=issuePlan.expectedVersion()) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    LocalDateTime at=now(clock);
    String actionId=Ids.next(),orderId=issueReviewRow.orderId();
    DisputeGuard.Facts facts=guard.inspect(orderId);
    if(!facts.ready() || facts.claimActive())throw conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name());
    IssuePlanMapper.Action previous=issuePlanMapper.selectActive(orderId,issueId,false);
    IssuePlanMapper.Action termination=issuePlanMapper.selectActive(orderId,null,true);
    if (previous!=null && previous.locked()) throw conflict(BusinessFailureReason.TERMINATION_PLAN_LOCKED.name());
    if (!Set.of("submitted","processing","waiting_evidence").contains(issueReviewRow.status())
        || termination!=null && !issueId.equals(termination.issueId())) throw conflict(FulfillmentReason.ISSUE_STATE_CHANGED.name());
    if (previous!=null && (!issuePlan.terminates() || termination==null)) throw conflict(FulfillmentReason.ISSUE_STATE_CHANGED.name());
    if (issuePlan.terminates() || issuePlan.plannedCash().depositFen()>0) overdueFees.requireResolved(orderId);
    if (issuePlan.terminates()) extensions.endForRecovery(orderId,at);
    for (CaseContact.Reference ref:issuePlan.references())
      if (!issuePlanMapper.referenceExists(issueReviewRow.caseId(),orderId,ref.relationType(),ref.relationId())) throw missing();
    for (String id:issuePlan.agreement().evidenceIds())
      if (!issuePlanMapper.referenceExists(issueReviewRow.caseId(),orderId,"order_evidence_record",id)) throw missing();
    List<InventoryReservationDO> held=tradingReservations.lockExisting(order.checkoutBatchId());
    if (previous!=null) {
      List<IssuePlanMapper.Refund> oldRefunds=issuePlanMapper.selectPlannedRefundsByAction(previous.id());
      if (issuePlanMapper.cancelDraftRefunds(previous.id(),at)!=oldRefunds.size()) throw conflict(BusinessFailureReason.TERMINATION_PLAN_LOCKED.name());
      issuePlanMapper.cancelDeduction(previous.id(),at);
      issuePlanMapper.updateTotals(orderId,at);
      issuePlanMapper.cancelPlan(previous.id(),actionId,at);
    }
    List<IssuePlanMapper.Source> sources=issuePlanMapper.selectRefundFundingSourcesByOrder(orderId,null);
    CashComponents limit=limits(sources,issuePlanMapper.selectOrderDeductionOffsets(orderId,null)), cash=issuePlan.plannedCash();
    if (cash.rentFen()>limit.rentFen() || cash.depositFen()>limit.depositFen() || cash.freightFen()>limit.freightFen())
      throw conflict(BusinessFailureReason.REFUND_LIMIT_CHANGED.name());
    int retained=issuePlan.terminates()?limit.depositFen()-cash.depositFen():0;
    if (retained>0 && (issuePlan.depositReason()==null || issuePlan.depositReason().isBlank())) throw conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name());
    int version=previous==null ? 1 : previous.schemeVersion()+1;
    Map<String,Object> payload=view("schema_version",issuePlan.terminates()?2:1,"scheme_version",version,"fulfillment_decision",issuePlan.direction(),
        "solution_summary",issuePlan.solutionSummary(),"agreement",issuePlan.agreement(),"references",issuePlan.references(),"service_refund",issuePlan.serviceRefund());
    if (issuePlan.terminates()) payload.put("termination",view("scheme_version",version,"supersedes_action_id",previous==null?null:previous.id(),
        "return_plan",issuePlan.recovery(),"freight_plan",issuePlan.freightResponsibility(),"deposit_deduction_reason",issuePlan.depositReason(),
        "refund_basis",issuePlan.refundBasis(),"goods_location",issuePlan.goodsLocation(),"billing_cutoff_rule","actual_return_dispatch_day_inclusive"));
    AdminCaseActionDO action=new AdminCaseActionDO().id(actionId).actionNo("SOL"+actionId).adminCaseId(issueReviewRow.caseId())
        .actionType("resolve_order_issue").actionStatus("executing").targetModule("04").targetType("rental_order").targetId(orderId)
        .requestPayloadJson(json.writeValueAsString(payload)).resultPayloadJson(issuePlan.terminates()?"{\"termination\":{}}":"{}")
        .confirmationSnapshotJson(json.writeValueAsString(view("limits",limit,"sources",sources,"evidence_ids",issuePlan.agreement().evidenceIds())))
        .operatorAdminUserId(tradingCommand.actor()).operatorConfirmedAt(at).operationReason(issuePlan.solutionSummary())
        .idempotencyKey("issue-plan:"+tradingCommand.actor()+":"+tradingCommand.requestId()).createdAt(at).updatedAt(at);
    if (issuePlan.terminates()) action.plannedRentRefundAmountFen((long)cash.rentFen()).plannedDepositRefundAmountFen((long)cash.depositFen())
        .plannedFreightRefundAmountFen((long)cash.freightFen());
    adminCaseActionMapper.insertAdminCaseAction(action);
    if (retained>0) issuePlanMapper.reserveDeduction(Ids.next(),orderId,actionId,tradingCommand.actor(),retained,issuePlan.depositReason(),at);
    reserveRefunds(issueReviewRow,actionId,tradingCommand.actor(),issuePlan,sources,at);
    if (issuePlanMapper.resolveIssue(issueId,issueReviewRow.version(),issuePlan.terminates()?"cancel_refund":"continue_fulfillment",issuePlan.solutionSummary(),false,at)!=1)
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if (issueReviewMapper.changeCaseStatus(issueReviewRow,"pending",at)!=1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if (issuePlan.terminates()) issuePlanMapper.stopNormalClosure(orderId,at);
    if (issuePlan.terminates() && !issuePlanMapper.selectIssuePlanDispatched(orderId)) {
      inventoryReservationMapper.releaseOrder(orderId,at);
      tradingReservations.refreshCurrent(held.stream().filter(r->orderId.equals(r.getRentalOrderId())).map(r->r.getSkuId()).toList());
    }
    IssuePlanMapper.Action active=issuePlanMapper.selectActive(orderId,issueId,false);
    reconcileLocked(active,tradingCommand.actor(),at);
    if (!issuePlan.terminates()) timers.restore(orderId);
    refundMapper.refreshOrderRefunds(orderId,at);
    return tradingOperations.recordReason(tradingCommand,"admin_case_action",actionId,version,issuePlan.solutionSummary());
  }

  /** 在订单锁内按资金来源预留退款。 */
  private void reserveRefunds(IssueReviewMapper.Row row,String actionId,String actor,IssuePlan plan,
      List<IssuePlanMapper.Source> sources,LocalDateTime at) {
    CashComponents need=plan.plannedCash();
    int rent=need.rentFen(),deposit=need.depositFen(),freight=need.freightFen(),step=0;
    for (IssuePlanMapper.Source source:sources) {
      CashComponents part=new CashComponents(Math.min(rent,source.rent()),Math.min(deposit,source.deposit()),Math.min(freight,source.freight()));
      if (part.totalFen()==0) continue;
      ChannelSuborderDO channelSuborderDO=paymentOrderMapper.lockSuborderById(source.suborderId());
      String refund=obligations.reserve(actor,"rental_order",row.orderId(),"admin_case_action",actionId,"customer_service_refund",
          "订单异常协商退款",channelSuborderDO,new CashComponents(source.originalRent(),source.originalDeposit(),source.originalFreight()),part);
      String stepId=Ids.next();
      adminCaseActionMapper.insertAdminCaseAction(new AdminCaseActionDO().id(stepId).actionNo("REF"+stepId).adminCaseId(row.caseId())
          .parentActionId(actionId).stepNo(++step).actionType("issue_refund").actionStatus("executing").targetModule("04")
          .targetType("refund_order").targetId(refund).operatorAdminUserId(actor).idempotencyKey("issue-refund:"+actionId+":"+channelSuborderDO.id())
          .createdAt(at).updatedAt(at));
      rent-=part.rentFen(); deposit-=part.depositFen(); freight-=part.freightFen();
    }
    if (rent!=0 || deposit!=0 || freight!=0) throw conflict(BusinessFailureReason.REFUND_LIMIT_CHANGED.name());
  }

  /** 检查退款执行的前置事实。 */
  public boolean readyForRefund(IssuePlanMapper.Action action) {
    return !"terminate".equals(json.readTree(action.payload()).path("fulfillment_decision").asString())
        || !issuePlanMapper.selectIssuePlanDispatched(action.orderId()) || issuePlanMapper.physicallyReceived(action.orderId());
  }

  /** 读取各资金项目仍可处置的金额。 */
  public static CashComponents limits(List<IssuePlanMapper.Source> sources,IssuePlanMapper.Offsets offsets) {
    if (sources.isEmpty()) throw conflict(BusinessFailureReason.SOURCE_ACTION_REQUIRED.name());
    long rent=-offsets.rent(),deposit=-offsets.deposit(),freight=-offsets.freight();
    for (IssuePlanMapper.Source issuePlanSource:sources) {
      if (issuePlanSource.rent()<0 || issuePlanSource.deposit()<0 || issuePlanSource.freight()<0) throw conflict(BusinessFailureReason.REFUND_LIMIT_CHANGED.name());
      rent+=issuePlanSource.rent();deposit+=issuePlanSource.deposit();freight+=issuePlanSource.freight();
    }
    return new CashComponents(Math.toIntExact(Math.max(0,rent)),Math.toIntExact(Math.max(0,deposit)),Math.toIntExact(Math.max(0,freight)));
  }

  /** 调用方已锁订单根；方案确认和退款成功通知均重查实物与每个真实子退款。 */
  public void reconcileLocked(IssuePlanMapper.Action action,String actor,LocalDateTime at) {
    if (action==null || "succeeded".equals(action.status())) return;
    String order=action.orderId();
    boolean terminating="terminate".equals(json.readTree(action.payload()).path("fulfillment_decision").asString());
    if (terminating) {
      if (issuePlanMapper.selectIssuePlanDispatched(order) && !issuePlanMapper.physicallyReceived(order)) return;
      int closed=issuePlanMapper.terminateOrder(order,at);
      if(issuePlanMapper.confirmDeduction(action.id(),at)>0 || closed>0)issuePlanMapper.updateTotals(order,at);
    }
    if (issuePlanMapper.selectPlannedRefundsByAction(action.id()).stream().anyMatch(r->!"success".equals(r.status()))) return;

    if(terminating)issuePlanMapper.settleTerminationFreight(order,at);
    issuePlanMapper.finishSteps(action.id(),at);
    issuePlanMapper.finishPlan(action,actor,at);
    issuePlanMapper.closeIssue(action.issueId(),at);
    issuePlanMapper.closeCase(action.caseId(),SystemIdentity.SYSTEM_USER_ID,at);
  }
}
