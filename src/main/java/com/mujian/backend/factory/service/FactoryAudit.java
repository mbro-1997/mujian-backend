package com.mujian.backend.factory.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.AdminOperationAuditDO;
import com.mujian.backend.casework.mapper.*;
import com.mujian.backend.common.support.Ids;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
public class FactoryAudit {
  private final AdminOperationAuditMapper adminOperationAuditMapper;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactoryAudit(
      AdminOperationAuditMapper adminOperationAuditMapper, ObjectMapper objectMapper, Clock clock) {
    this.adminOperationAuditMapper = adminOperationAuditMapper;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 持久化本次操作审计，保持原事务与脱敏规则。 */
  @Transactional
  public String record(
      String actor,
      String targetType,
      String target,
      String operation,
      String permission,
      String trace,
      String key,
      String caseId,
      String action,
      String reason,
      Object before,
      Object after) {
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
            .targetType(targetType)
            .targetId(target)
            .requestId(trace)
            .idempotencyKey(key)
            .operationReason(reason)
            .beforeSnapshotJson(before == null ? null : objectMapper.writeValueAsString(before))
            .afterSnapshotJson(after == null ? null : objectMapper.writeValueAsString(after))
            .resultStatus("success")
            .createdAt(now(clock)));
    return id;
  }
}
