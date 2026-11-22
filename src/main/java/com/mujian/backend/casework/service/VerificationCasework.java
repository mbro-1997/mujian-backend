package com.mujian.backend.casework.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.domain.AdminCaseActionDO;
import com.mujian.backend.casework.domain.AdminCaseDO;
import com.mujian.backend.casework.domain.AdminCaseRelationDO;
import com.mujian.backend.casework.domain.AdminOperationAuditDO;
import com.mujian.backend.casework.dto.CloseCaseDTO;
import com.mujian.backend.casework.mapper.*;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.*;
import com.mujian.backend.verification.domain.VerificationApplicationDO;
import com.mujian.backend.verification.dto.VerificationReviewRequestDTO;
import com.mujian.backend.verification.vo.VerificationReviewResultVO;
import java.time.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class VerificationCasework {
  private final AdminCaseMapper adminCaseMapper;
  private final AdminCaseRelationMapper adminCaseRelationMapper;
  private final AdminCaseActionMapper adminCaseActionMapper;
  private final AdminOperationAuditMapper adminOperationAuditMapper;
  private final ObjectMapper objectMapper;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public VerificationCasework(
      AdminCaseMapper adminCaseMapper,
      AdminCaseRelationMapper adminCaseRelationMapper,
      AdminCaseActionMapper adminCaseActionMapper,
      AdminOperationAuditMapper adminOperationAuditMapper,
      ObjectMapper objectMapper) {
    this.adminCaseMapper = adminCaseMapper;
    this.adminCaseRelationMapper = adminCaseRelationMapper;
    this.adminCaseActionMapper = adminCaseActionMapper;
    this.adminOperationAuditMapper = adminOperationAuditMapper;
    this.objectMapper = objectMapper;
  }

  /** 将认证来源映射为既定工单类型。 */
  private static String type(String domain) {
    return switch (domain) {
      case "realname" -> "realname_review";
      case "student" -> "student_verification_review";
      default -> throw new IllegalArgumentException();
    };
  }

  /** 创建业务记录，保留原幂等与事务边界。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void create(String domain, String user, String application, LocalDateTime now) {
    String c = Ids.next();
    adminCaseMapper.insertAdminCase(
        new AdminCaseDO()
            .id(c)
            .caseNo("CASE" + c)
            .activeCaseKey(domain + ":" + application)
            .caseType(type(domain))
            .caseStatus(CODE_PENDING)
            .priority("P2")
            .title(domain.equals("realname") ? "实名认证审核" : "学生认证审核")
            .buyerUserId(user)
            .createdByType(FulfillmentRole.system.name())
            .createdByAdminUserId(SystemIdentity.SYSTEM_USER_ID)
            .createdAt(now)
            .updatedAt(now));
    adminCaseRelationMapper.insertAdminCaseRelation(
        new AdminCaseRelationDO()
            .id(Ids.next())
            .adminCaseId(c)
            .relationType("user_" + domain + "_verification")
            .relationId(application)
            .relationRole("source")
            .isPrimary(1)
            .createdAt(now));
  }

  /** 取得认证申请关联的审核工单。 */
  public AdminCaseDO associated(
      String domain, VerificationApplicationDO verificationApplicationDO, boolean lock) {
    List<AdminCaseDO> rows =
        adminCaseMapper.selectAdminCasesBySource(
            "user_" + domain + "_verification", verificationApplicationDO.getId(), lock);
    if (rows.size() != 1
        || !verificationApplicationDO.getUserId().equals(rows.get(0).getBuyerUserId())
        || !type(domain).equals(rows.get(0).getCaseType()))
      throw new ApiFailure(
          500, CODE_DATA_INTEGRITY_ERROR, domain.toUpperCase() + "_CASE_INCONSISTENT");
    return rows.get(0);
  }

  /** 核对重试材料与原申请一致后返回原结果。 */
  public <T extends VerificationReviewResultVO> T replay(
      String key,
      String actor,
      String source,
      VerificationReviewRequestDTO verificationReviewRequestDTO,
      Class<T> resultType) {
    AdminCaseActionDO adminCaseActionDO =
        com.mujian.backend.infrastructure.persistence.MappingInputs.first(
            adminCaseActionMapper.selectAdminCaseActionsByIdempotencyKey(key));
    if (adminCaseActionDO == null) return null;
    if (!actor.equals(adminCaseActionDO.getOperatorAdminUserId())
        || !source.equals(adminCaseActionDO.getTargetId())
        || !objectMapper
            .readTree(adminCaseActionDO.getRequestPayloadJson())
            .equals(objectMapper.valueToTree(verificationReviewRequestDTO)))
      throw conflict(CODE_IDEMPOTENCY_KEY_REUSED);
    return objectMapper.readValue(adminCaseActionDO.getResultPayloadJson(), resultType);
  }

  /** 原子完成审核动作及工单结果快照。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void finish(
      String domain,
      VerificationApplicationDO verificationApplicationDO,
      AdminCaseDO adminCaseDO,
      String actor,
      String key,
      VerificationReviewRequestDTO verificationReviewRequestDTO,
      VerificationReviewResultVO verificationReviewResultVO,
      String trace,
      LocalDateTime now) {
    String action = verificationReviewResultVO.getActionId(),
        status = verificationReviewResultVO.getStatus(),
        remark = verificationReviewRequestDTO.getRemark(),
        target = "user_" + domain + "_verification";
    String decision = verificationReviewRequestDTO.getDecision();
    adminCaseActionMapper.insertAdminCaseAction(
        new AdminCaseActionDO()
            .id(action)
            .actionNo("ACT" + action)
            .adminCaseId(adminCaseDO.getId())
            .actionType(domain + "_" + decision)
            .actionStatus("succeeded")
            .targetModule("01")
            .targetType(target)
            .targetId(verificationApplicationDO.getId())
            .requestPayloadJson(objectMapper.writeValueAsString(verificationReviewRequestDTO))
            .resultPayloadJson(objectMapper.writeValueAsString(verificationReviewResultVO))
            .idempotencyKey(key)
            .operatorAdminUserId(actor)
            .operationReason(remark)
            .operatorConfirmedAt(now)
            .executedAt(now)
            .createdAt(now)
            .updatedAt(now));
    Map<String, Object> checklist =
        view(
            "schemaVersion",
            1,
            "sourceType",
            target,
            "sourceId",
            verificationApplicationDO.getId(),
            "sourceStatus",
            status,
            "actionId",
            action);
    if (adminCaseMapper.closeAdminCaseByVersion(
            new CloseCaseDTO()
                .id(adminCaseDO.getId())
                .version(adminCaseDO.getVersion())
                .status(status)
                .summary(remark)
                .checklist(objectMapper.writeValueAsString(checklist))
                .now(now)
                .actor(actor))
        != 1) throw conflict(CODE_VERSION_CONFLICT);
    audit(
        domain,
        verificationApplicationDO.getId(),
        actor,
        domain + "_" + decision,
        "admin:" + domain + ":review",
        trace,
        adminCaseDO.getId(),
        action,
        key,
        remark,
        view(
            "schemaVersion",
            1,
            "status",
            verificationApplicationDO.getVerificationStatus(),
            "version",
            verificationApplicationDO.getVersion(),
            "applicationId",
            verificationApplicationDO.getId()),
        view(
            "schemaVersion",
            1,
            "status",
            status,
            "version",
            verificationReviewResultVO.getVersion(),
            "applicationId",
            verificationApplicationDO.getId()),
        now);
  }

  /** 记录敏感资料查看审计。 */
  @Transactional
  public String audit(
      String domain,
      String application,
      String actor,
      String operation,
      String permission,
      String trace,
      String caseId,
      String action,
      String key,
      String reason,
      Object before,
      Object after,
      LocalDateTime now) {
    String id = Ids.next();
    adminOperationAuditMapper.insertAdminOperationAudit(
        new AdminOperationAuditDO()
            .id(id)
            .auditNo("AUD" + id)
            .adminUserId(actor)
            .adminCaseId(caseId)
            .caseActionId(action)
            .permissionCode(permission)
            .operationCode(operation)
            .operationName(operation)
            .targetModule("01")
            .targetType("user_" + domain + "_verification")
            .targetId(application)
            .requestId(trace)
            .idempotencyKey(key)
            .operationReason(reason)
            .beforeSnapshotJson(before == null ? null : objectMapper.writeValueAsString(before))
            .afterSnapshotJson(after == null ? null : objectMapper.writeValueAsString(after))
            .resultStatus("success")
            .createdAt(now));
    return id;
  }

  /** 读取最近成功审核动作的备注。 */
  public String remark(String source) {
    return adminCaseActionMapper.selectLatestSucceededActionRemark(source);
  }
}
