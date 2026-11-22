package com.mujian.backend.delivery.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.AdminCaseDO;
import com.mujian.backend.casework.domain.AdminCaseRelationDO;
import com.mujian.backend.casework.mapper.AdminCaseMapper;
import com.mujian.backend.casework.mapper.AdminCaseRelationMapper;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.common.support.SystemIdentity;
import com.mujian.backend.delivery.domain.OrderReceiptDO;
import com.mujian.backend.delivery.domain.OrderReturnShipmentDO;
import com.mujian.backend.delivery.dto.IssueInput;
import com.mujian.backend.delivery.mapper.FulfillmentMapper;
import com.mujian.backend.delivery.mapper.OrderIssueMapper;
import com.mujian.backend.material.service.VerificationMaterials;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.TradingOperations;
import com.mujian.backend.order.service.TradingRoots;
import com.mujian.backend.order.vo.TradingReceipt;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderIssues {
  private final OrderIssueMapper orderIssueMapper;
  private final FulfillmentMapper fulfillmentMapper;
  private final FulfillmentAccess fulfillmentAccess;
  private final OrderEvidence orderEvidence;
  private final TradingOperations tradingOperations;
  private final TradingRoots tradingRoots;
  private final AdminCaseMapper adminCaseMapper;
  private final AdminCaseRelationMapper adminCaseRelationMapper;
  private final FulfillmentEvents fulfillmentEvents;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  /** 复用原单锁、附件凭证、管理事项和持久化回执。 */
  public OrderIssues(OrderIssueMapper orderIssueMapper,FulfillmentMapper fulfillmentMapper,FulfillmentAccess fulfillmentAccess,
      OrderEvidence orderEvidence,TradingOperations tradingOperations,TradingRoots tradingRoots,AdminCaseMapper adminCaseMapper,
      AdminCaseRelationMapper adminCaseRelationMapper,FulfillmentEvents fulfillmentEvents,PlatformTransactionManager transactionManager,Clock clock) {
    this.orderIssueMapper=orderIssueMapper;
    this.fulfillmentMapper=fulfillmentMapper;
    this.fulfillmentAccess=fulfillmentAccess;
    this.orderEvidence=orderEvidence;
    this.tradingOperations=tradingOperations;
    this.tradingRoots=tradingRoots;
    this.adminCaseMapper=adminCaseMapper;
    this.adminCaseRelationMapper=adminCaseRelationMapper;
    this.fulfillmentEvents=fulfillmentEvents;
    transactionTemplate=new TransactionTemplate(transactionManager);
    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.clock=clock;
  }

  /** 提交时复核本人、阶段、版本和证据；首次暂停才保存实际剩余时长。 */
  public TradingReceipt submit(TradingCommand command,String orderId,IssueInput issueInput) {
    RentalOrderDO rentalOrderDO=fulfillmentAccess.order(command.actor(),orderId,false,null);
    TradingReceipt tradingReceipt=tradingOperations.replay(command);
    if (tradingReceipt!=null) return tradingReceipt;
    EvidenceNode node=issueInput.scene().evidenceNode();
    List<VerificationMaterials.VerifiedObject> uploads;
    try {
      uploads=orderEvidence.verifyUploads(command.actor(),orderId,node,FulfillmentRole.buyer,issueInput.evidence().uploadReceiptIds());
    } catch (ApiFailure failure) {
      TradingReceipt tradingReceiptReplay=tradingOperations.replay(command);
      if (tradingReceiptReplay!=null) return tradingReceiptReplay;
      throw failure;
    }
    return transactionTemplate.execute(transaction -> {
      tradingRoots.lock(rentalOrderDO.checkoutBatchId());
      RentalOrderDO order=fulfillmentAccess.order(command.actor(),orderId,false,null);
      TradingReceipt replay=tradingOperations.replay(command);
      if (replay!=null) return replay;
      LocalDateTime at=now(clock);
      OrderReceiptDO receipt=fulfillmentMapper.selectOrderReceiptDOByOrder(orderId);
      OrderReturnShipmentDO shipment=fulfillmentMapper.selectOrderReturnShipmentDOByOrder(orderId);
      if (shipment!=null && shipment.sentAt()!=null || fulfillmentMapper.selectOrderReturnCheckDOByOrder(orderId)!=null)
        throw conflict(FulfillmentReason.ISSUE_STATE_CHANGED.name());
      boolean receiptIssue=issueInput.scene()==IssueScene.receipt;
      if (receiptIssue) {
        if (receipt==null && !TradingState.accepted.name().equals(order.orderStatus())
            || receipt!=null && !Set.of("pending","problem_reported").contains(receipt.checkStatus()))
          throw conflict(FulfillmentReason.ISSUE_STATE_CHANGED.name());
        if ((receipt==null ? 0 : receipt.version())!=issueInput.expectedVersion()) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
      } else {
        if (receipt==null || !Set.of("confirmed","auto_confirmed").contains(receipt.checkStatus())
            || !TradingState.fulfilling.name().equals(order.orderStatus())) throw conflict(FulfillmentReason.ISSUE_STATE_CHANGED.name());
        if (issueInput.expectedVersion()!=order.version()) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
      }
      Map<String,OrderIssueMapper.ItemSource> sources=new HashMap<>();
      for (OrderIssueMapper.ItemSource item:orderIssueMapper.selectOrderItems(orderId)) sources.put(item.id(),item);
      Set<IssueItemKey> distinct=new HashSet<>();
      for (IssueInput.Item item:issueInput.items()) {
        OrderIssueMapper.ItemSource source=sources.get(item.itemId());
        if (source==null) throw missing();
        if (item.quantity()>source.quantity() || !distinct.add(new IssueItemKey(item.itemId(),item.issueType()))) throw ApiFailure.invalid();
      }
      List<String> evidenceIds=new ArrayList<>(issueInput.evidence().evidenceIds());
      if (!evidenceIds.isEmpty() && orderIssueMapper.selectOwnedEvidenceIds(orderId,command.actor(),node.name(),evidenceIds).size()!=evidenceIds.size())
        throw conflict(FulfillmentReason.MATERIAL_REFERENCE_INVALID.name());
      String issueId=Ids.next();
      orderIssueMapper.insertIssue(issueId,orderId,receiptIssue && receipt!=null ? receipt.id() : null,command.actor(),command.requestId(),issueInput,at);
      for (IssueInput.Item item:issueInput.items()) orderIssueMapper.insertIssueItem(Ids.next(),issueId,orderId,sources.get(item.itemId()).skuId(),item,at);
      for (String id:orderEvidence.bindVerifiedUploads(command.actor(),orderId,node,FulfillmentRole.buyer,uploads)) {
        if (orderIssueMapper.bindIssueEvidence(id,issueId,orderId)!=1) throw conflict(FulfillmentReason.MATERIAL_REFERENCE_INVALID.name());
        evidenceIds.add(id);
      }
      if (receiptIssue && receipt!=null) {
        if (orderIssueMapper.reportReceiptIssue(receipt.id(),receipt.version(),issueInput.effect()==IssueEffect.PAUSED,at)!=1)
          throw conflict(D2Codes.CODE_VERSION_CONFLICT);
        orderIssueMapper.synchronizeReceiptDeadline(orderId,at);
      } else if (orderIssueMapper.advanceOrderVersion(orderId,order.version(),at)!=1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
      String caseId=Ids.next();
      adminCaseMapper.insertAdminCase(new AdminCaseDO().id(caseId).caseNo("ISS"+caseId).activeCaseKey("issue:"+issueId)
          .caseType(receiptIssue?"receipt_issue":"in_use_issue").caseStatus("pending").priority("P2").title(receiptIssue?"收货问题":"使用中异常")
          .buyerUserId(command.actor()).factoryId(order.factoryId()).rentalOrderId(orderId).version(0)
          .createdByType(FulfillmentRole.system.name()).createdByAdminUserId(SystemIdentity.SYSTEM_USER_ID).createdAt(at).updatedAt(at));
      adminCaseRelationMapper.insertAdminCaseRelation(new AdminCaseRelationDO().id(Ids.next()).adminCaseId(caseId)
          .relationType("order_issue_report").relationId(issueId).relationRole("source").isPrimary(1).createdAt(at));
      for (String id:evidenceIds) adminCaseRelationMapper.insertAdminCaseRelation(new AdminCaseRelationDO().id(Ids.next()).adminCaseId(caseId)
          .relationType("order_evidence_record").relationId(id).relationRole("evidence_context").isPrimary(0).createdAt(at));
      fulfillmentEvents.append("issue:"+issueId+":reported",orderId,null,TradingResource.order_issue_report.name(),issueId,
          FulfillmentEventType.issue_reported,at,command.actor(),FulfillmentRole.buyer,issueInput.effect()==IssueEffect.PAUSED?"已上报问题，暂停履约":"已上报问题，继续履约");
      return tradingOperations.record(command,TradingResource.order_issue_report.name(),issueId,0);
    });
  }

  private record IssueItemKey(String itemId,IssueType issueType) {}
}
