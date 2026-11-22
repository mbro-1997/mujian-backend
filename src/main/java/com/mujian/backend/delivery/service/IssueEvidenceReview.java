package com.mujian.backend.delivery.service;

import com.mujian.backend.order.domain.RentalOrderDO;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.AdminCaseRelationDO;
import com.mujian.backend.casework.mapper.AdminCaseRelationMapper;
import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.delivery.dto.IssueInput;
import com.mujian.backend.delivery.mapper.IssueReviewMapper;
import com.mujian.backend.delivery.mapper.OrderIssueMapper;
import com.mujian.backend.material.service.VerificationMaterials;
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
public class IssueEvidenceReview {
  private final IssueReviewMapper issueReviewMapper;
  private final OrderIssueMapper orderIssueMapper;
  private final AdminCaseRelationMapper adminCaseRelationMapper;
  private final OrderEvidence orderEvidence;
  private final TradingOperations tradingOperations;
  private final TradingRoots tradingRoots;
  private final FulfillmentAccess fulfillmentAccess;
  private final Clock clock;
  private final TransactionTemplate transaction;

  /** 复用收货问题来源、订单锁及 08 事项，同一事务写入状态和回执。 */
  public IssueEvidenceReview(IssueReviewMapper issueReviewMapper, OrderIssueMapper orderIssueMapper, AdminCaseRelationMapper adminCaseRelationMapper,
      OrderEvidence orderEvidence, TradingOperations tradingOperations, TradingRoots tradingRoots, FulfillmentAccess fulfillmentAccess,
      Clock clock, PlatformTransactionManager manager) {
    this.issueReviewMapper = issueReviewMapper;
    this.orderIssueMapper = orderIssueMapper;
    this.adminCaseRelationMapper = adminCaseRelationMapper;
    this.orderEvidence = orderEvidence;
    this.tradingOperations = tradingOperations;
    this.tradingRoots = tradingRoots;
    this.fulfillmentAccess = fulfillmentAccess;
    this.clock = clock;
    transaction = new TransactionTemplate(manager);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /** 补证要求始终保留原原因与原动作身份。 */
  public TradingReceipt request(TradingCommand command, String issueId, IssueInput.Reason reason) {
    IssueReviewMapper.Row original = source(command, issueId, true);
    return transaction.execute(tx -> {
      lock(command, original, true);
      TradingReceipt tradingReceipt = tradingOperations.replay(command);
      if (tradingReceipt != null) return tradingReceipt;
      IssueReviewMapper.Row issue = source(command,issueId,true);
      check(issue,reason.expectedVersion(),Set.of("submitted","processing"));
      LocalDateTime at = now(clock);
      issueReviewMapper.insertEvidenceRequest(Ids.next(),issue,command.actor(),reason.reason(),command.requestId(),at);
      advance(issue,"waiting_evidence","waiting_user",at);
      return tradingOperations.recordReason(command,"order_issue_report",issueId,issue.version()+1,reason.reason());
    });
  }

  /** 在 COS 预检之后根据原始会话及业务来源复核，补证文本和图片均保留来源。 */
  public TradingReceipt supplement(TradingCommand command,String issueId,IssueInput.Supplement supplement) {
    IssueReviewMapper.Row original = source(command,issueId,false);
    TradingReceipt tradingReceipt = tradingOperations.replay(command);
    if (tradingReceipt != null) return tradingReceipt;
    EvidenceNode node = original.scene().evidenceNode();
    List<VerificationMaterials.VerifiedObject> uploads = orderEvidence.verifyUploads(command.actor(),
        original.orderId(),node,FulfillmentRole.buyer,supplement.evidence().uploadReceiptIds());
    return transaction.execute(tx -> {
      lock(command,original,false);
      TradingReceipt tradingReceiptPrior = tradingOperations.replay(command);
      if (tradingReceiptPrior != null) return tradingReceiptPrior;
      IssueReviewMapper.Row issue = source(command,issueId,false);
      check(issue,supplement.expectedVersion(),Set.of("waiting_evidence"));
      List<String> reused = supplement.evidence().evidenceIds();
      if (!reused.isEmpty() && orderIssueMapper.selectOwnedEvidenceIds(issue.orderId(),command.actor(),node.name(),reused).size()!=reused.size())
        throw conflict(FulfillmentReason.MATERIAL_REFERENCE_INVALID.name());
      LocalDateTime at = now(clock);
      Set<String> evidenceIds = new LinkedHashSet<>(reused);
      String textId = Ids.next();
      issueReviewMapper.insertSupplementText(textId,issue,command.actor(),supplement.summary(),at);
      evidenceIds.add(textId);
      for (String id : orderEvidence.bindVerifiedUploads(command.actor(),issue.orderId(),node,FulfillmentRole.buyer,uploads)) {
        if (orderIssueMapper.bindIssueEvidence(id,issueId,issue.orderId())!=1)
          throw conflict(FulfillmentReason.MATERIAL_REFERENCE_INVALID.name());
        evidenceIds.add(id);
      }
      for (IssueReviewMapper.Evidence existing : issueReviewMapper.selectEvidence(List.of(issueId)))
        evidenceIds.remove(existing.evidenceId());
      for (String id : evidenceIds) {
        adminCaseRelationMapper.insertAdminCaseRelation(new AdminCaseRelationDO().id(Ids.next()).adminCaseId(issue.caseId())
            .relationType("order_evidence_record").relationId(id).relationRole("evidence_context").isPrimary(0).createdAt(at));
      }
      advance(issue,"processing","pending",at);
      return tradingOperations.record(command,"order_issue_report",issueId,issue.version()+1);
    });
  }

  /** 核对操作者范围并按业务根加锁。 */
  private void lock(TradingCommand command, IssueReviewMapper.Row issue, boolean admin) {
    RentalOrderDO order = fulfillmentAccess.order(command.actor(), issue.orderId(), false, admin ? command.operation().permission() : null);
    tradingRoots.lock(order.checkoutBatchId());
  }

  /** 定位本次命令的来源记录。 */
  private IssueReviewMapper.Row source(TradingCommand command,String id,boolean admin) {
    tradingOperations.authorize(command.actor(),command.operation());
    List<IssueReviewMapper.Row> rows = issueReviewMapper.selectRows(new IssueReviewMapper.Filter(id,null,admin ? null : command.actor(),null),new PageQuery(1,1));
    if (rows.isEmpty()) throw missing();
    return rows.get(0);
  }

  /** 核对当前状态是否允许继续。 */
  private static void check(IssueReviewMapper.Row issue,int expectedVersion,Set<String> allowed) {
    if (issue.version()!=expectedVersion) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if (!allowed.contains(issue.status())) throw conflict(FulfillmentReason.ISSUE_STATE_CHANGED.name());
  }

  /** 按已核验的版本推进处理状态。 */
  private void advance(IssueReviewMapper.Row issue,String status,String caseStatus,LocalDateTime at) {
    if (issueReviewMapper.updateStatus(issue.id(),issue.version(),status,at)!=1
        || issueReviewMapper.changeCaseStatus(issue,caseStatus,at)!=1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
  }
}
