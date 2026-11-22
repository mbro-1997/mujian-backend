package com.mujian.backend.material.service;

import static com.mujian.backend.common.support.D2Values.view;

import com.mujian.backend.casework.domain.AdminOperationAuditDO;
import com.mujian.backend.casework.mapper.*;
import com.mujian.backend.common.support.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Independently committed cleanup intent/outcomes; never stores raw object paths or version IDs.
 */
@Component
public class MaterialCleanupAudit {
  private final AdminOperationAuditMapper adminOperationAuditMapper;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(MaterialCleanupAudit.class);

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public MaterialCleanupAudit(
      AdminOperationAuditMapper adminOperationAuditMapper,
      ObjectMapper objectMapper,
      Clock clock,
      PlatformTransactionManager platformTransactionManager) {
    this.adminOperationAuditMapper = adminOperationAuditMapper;
    this.objectMapper = objectMapper;
    this.clock = clock;
    transactionTemplate = new TransactionTemplate(platformTransactionManager);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transactionTemplate.setTimeout(5);
  }

  /** 对审计定位信息计算脱敏摘要。 */
  public static String digest(String value) {
    if (value == null) return null;
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 持久化本次操作审计，保持原事务与脱敏规则。 */
  public void record(
      String run, String key, String purpose, String version, String result, String reason) {
    String safePurpose =
        Set.of("realname_front", "realname_back", "student_card")
                .contains(purpose == null ? "" : purpose)
            ? purpose
            : "unknown";
    Map<String, Object> data =
        view(
            "schemaVersion",
            1,
            "runId",
            run,
            "objectKeyHash",
            digest(key),
            "purpose",
            safePurpose,
            "versionIdHash",
            digest(version),
            "result",
            result,
            "reason",
            reason,
            "time",
            clock.instant().toString());
    String snapshot = objectMapper.writeValueAsString(data);
    try {
      transactionTemplate.executeWithoutResult(
          status -> {
            String id = Ids.next();
            adminOperationAuditMapper.insertAdminOperationAudit(
                new AdminOperationAuditDO()
                    .id(id)
                    .auditNo("AUD" + id)
                    .adminUserId(SystemIdentity.SYSTEM_USER_ID)
                    .adminCaseId(null)
                    .caseActionId(null)
                    .permissionCode(null)
                    .operationCode("verification_material_cleanup")
                    .operationName("认证材料清理")
                    .targetModule("01")
                    .targetType("verification_material")
                    .targetId(null)
                    .requestId(run)
                    .idempotencyKey(null)
                    .operationReason(reason)
                    .beforeSnapshotJson(null)
                    .afterSnapshotJson(snapshot)
                    .resultStatus(result.equals("unknown") ? "failed" : "success")
                    .createdAt(
                        LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai"))));
          });
      LOG.info("event=verification_cleanup_detail audit=persisted detail={}", snapshot);
    } catch (RuntimeException e) {
      // Do not log exception messages: SDK/JDBC failures can include credentials or raw keys.
      LOG.error("event=verification_cleanup_detail audit=failed detail={}", snapshot);
      throw e;
    }
  }
}
