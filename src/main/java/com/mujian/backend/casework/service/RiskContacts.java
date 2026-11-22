package com.mujian.backend.casework.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.AdminOperationAuditDO;
import com.mujian.backend.casework.dto.CaseContact;
import com.mujian.backend.casework.mapper.AdminCaseNoteMapper;
import com.mujian.backend.casework.mapper.AdminOperationAuditMapper;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.FulfillmentReason;
import com.mujian.backend.common.enums.OverdueReviewBand;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.delivery.mapper.MonitoringMapper;
import com.mujian.backend.delivery.vo.MonitoringViews.Risk;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.dto.TradingCommand;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class RiskContacts {
  public record OverdueReview(String currentDueDate, String assessedAt, boolean atResolution,
      OverdueReviewBand band, int depositPaidFen, int ceilingPercent, int ceilingFen) {}
  public record Detail(Risk risk, List<CaseContact.Row> contacts, List<CaseContact.Reference> references,
      OverdueReview overdueReview) {}

  /** 生成逾期复核所需的联系信息。 */
  private OverdueReview overdueReview(String riskId) {
    MonitoringMapper.RiskOrder source = monitoringMapper.selectRiskOrder(riskId);
    LocalDateTime assessed = source.resolvedAt() == null ? now(clock) : source.resolvedAt();
    OverdueReviewBand overdueReviewBand = OverdueReviewBand.at(source.currentDueDate().plusDays(1).atStartOfDay(), assessed);
    int percent = overdueReviewBand.ceilingPercent();
    int ceiling = Math.toIntExact((long) source.paidDepositFen() * percent / 100);
    return new OverdueReview(source.currentDueDate().toString(), time(assessed), source.resolvedAt() != null,
        overdueReviewBand, source.paidDepositFen(), percent, ceiling);
  }

  private final MonitoringMapper monitoringMapper;
  private final AdminCaseNoteMapper adminCaseNoteMapper;
  private final AdminOperationAuditMapper adminOperationAuditMapper;
  private final VerificationAccess verificationAccess;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 复用风险读取权限、管理事项和操作审计。 */
  public RiskContacts(MonitoringMapper monitoringMapper, AdminCaseNoteMapper adminCaseNoteMapper,
      AdminOperationAuditMapper adminOperationAuditMapper, VerificationAccess verificationAccess,
      ObjectMapper objectMapper, Clock clock) {
    this.monitoringMapper = monitoringMapper;
    this.adminCaseNoteMapper = adminCaseNoteMapper;
    this.adminOperationAuditMapper = adminOperationAuditMapper;
    this.verificationAccess = verificationAccess;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 同一快照回读来源、事项版本及实际沟通记录。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Detail detail(String actor, String riskId) {
    verificationAccess.admin(actor,"admin:fulfillment:risk:read");
    Risk risk = monitoringMapper.selectRiskById(id(riskId));
    if (risk == null) throw missing();
    if (risk.caseId() == null) return new Detail(risk,List.of(),List.of(),overdueReview(risk.id()));
    List<CaseContact.Row> contacts = new ArrayList<>();
    for (AdminCaseNoteMapper.Note note : adminCaseNoteMapper.selectNotesByCaseId(risk.caseId())) {
      List<CaseContact.Reference> references = note.snapshot() == null ? List.of()
          : objectMapper.readValue(note.snapshot(),CaseContact.Snapshot.class).references();
      contacts.add(new CaseContact.Row(note.id(),note.channel(),note.summary(),time(note.at()),references));
    }
    return new Detail(risk,contacts,adminCaseNoteMapper.selectReferencesByCaseId(risk.caseId()),overdueReview(risk.id()));
  }

  /** 在事项锁内先重放成功回执，再核版本及来源，提交与审计共同生效。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public CaseContact.Result append(TradingCommand command, String caseId, CaseContact.Input caseContactInput, String traceId) {
    TradingOperation operation = TradingOperation.case_contact_record;
    if (command.operation()!=operation) throw ApiFailure.invalid();
    verificationAccess.admin(command.actor(),operation.permission());
    AdminCaseNoteMapper.CaseState state = adminCaseNoteMapper.lockContactCaseById(id(caseId));
    if (state == null) throw missing();
    String prior = adminCaseNoteMapper.selectSnapshotByRequest(caseId,command.actor(),command.requestId());
    if (prior != null) {
      CaseContact.Snapshot snapshot = objectMapper.readValue(prior,CaseContact.Snapshot.class);
      if (!command.requestHash().equals(snapshot.requestHash())) throw conflict(FulfillmentReason.IDEMPOTENCY_CONFLICT.name());
      return snapshot.result();
    }
    if ("closed".equals(state.status()) || "cancelled".equals(state.status())) throw conflict(FulfillmentReason.CASE_CLOSED.name());
    if (caseContactInput.caseVersion()!=state.version()) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    List<CaseContact.Reference> references = caseContactInput.references()==null ? List.of() : caseContactInput.references();
    if (!references.isEmpty() && !adminCaseNoteMapper.selectReferencesByCaseId(caseId).containsAll(references)) throw missing();
    LocalDateTime at = now(clock);
    String noteId = Ids.next();
    adminCaseNoteMapper.insertContactNote(noteId,caseId,caseContactInput,command.actor(),at);
    if (adminCaseNoteMapper.incrementCaseVersion(caseId,state.version(),at)!=1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    CaseContact.Result result = new CaseContact.Result(noteId,caseId,state.version()+1,time(at));
    String auditId = Ids.next();
    adminOperationAuditMapper.insertAdminOperationAudit(new AdminOperationAuditDO().id(auditId)
        .auditNo("AUD"+auditId).adminUserId(command.actor()).adminCaseId(caseId)
        .permissionCode(operation.permission()).operationCode(operation.name()).operationName("追加事项联系记录")
        .targetModule("08").targetType("admin_case").targetId(caseId).requestId(traceId)
        .idempotencyKey(command.requestId()).resultStatus("success").createdAt(at)
        .afterSnapshotJson(objectMapper.writeValueAsString(new CaseContact.Snapshot(command.requestHash(),result,references))));
    return result;
  }
}
