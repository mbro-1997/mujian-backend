package com.mujian.backend.material.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.infrastructure.storage.*;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore.Stored;
import com.mujian.backend.material.mapper.MaterialReferenceMapper;
import com.mujian.backend.material.service.VerificationMaterials.Receipt;
import java.time.*;
import java.util.*;
import java.util.HashSet;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;

/** Both submitters use this same owner lock and the immutable, non-renewable receipt deadline. */
@Service
public class VerificationMaterialCleanup {
  private final PrivateMaterialStore privateMaterialStore;
  private final LoginMapper loginMapper;
  private final MaterialReferenceMapper materialReferenceMapper;
  private final Clock clock;
  private final Environment environment;
  private final TransactionTemplate transactionTemplate;
  private final MaterialCleanupAudit materialCleanupAudit;
  private static final org.slf4j.Logger LOG =
      org.slf4j.LoggerFactory.getLogger(VerificationMaterialCleanup.class);

  public record Report(
      String runId,
      boolean reportOnly,
      int scanned,
      int candidates,
      int retained,
      int skipped,
      int deleted,
      int unknown,
      boolean completed) {}

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public VerificationMaterialCleanup(
      PrivateMaterialStore privateMaterialStore,
      LoginMapper loginMapper,
      MaterialReferenceMapper materialReferenceMapper,
      Clock clock,
      Environment environment,
      PlatformTransactionManager platformTransactionManager,
      MaterialCleanupAudit materialCleanupAudit) {
    this.materialCleanupAudit = materialCleanupAudit;
    this.privateMaterialStore = privateMaterialStore;
    this.loginMapper = loginMapper;
    this.materialReferenceMapper = materialReferenceMapper;
    this.clock = clock;
    this.environment = environment;
    transactionTemplate = new TransactionTemplate(platformTransactionManager);
    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    transactionTemplate.setTimeout(5);
  }

  /** 执行既有后台补偿任务并保持失败隔离。 */
  @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Shanghai")
  public void scheduled() {
    Report report =
        scan(!environment.getProperty("mj.verification.cleanup.enabled", Boolean.class, false));
    LOG.info("event=verification_cleanup report={}", report);
  }

  /** 扫描到期未引用材料，审计后按版本删除并保留失败重试。 */
  public Report scan(boolean reportOnly) {
    String run = Ids.next(), marker = null;
    int scanned = 0, candidate = 0, retained = 0, skipped = 0, deleted = 0, unknown = 0;
    boolean completed = true;
    HashSet<String> markers = new HashSet<String>();
    do {
      PrivateMaterialStore.Page page;
      try {
        page = privateMaterialStore.list(marker);
      } catch (RuntimeException e) {
        completed = false;
        unknown++;
        com.mujian.backend.common.api.FailureDiagnostics.background(
            "material_cleanup_list", run, e);
        safeUnknown(run, null, null, null, "pagination_failed");
        break;
      }
      for (Stored stored : page.objects()) {
        scanned++;
        String purpose = null, version = stored.versionId(), phase = "inspect";
        try {
          if (stored.lastModified() == null
              || clock.instant().isBefore(stored.lastModified().plus(Duration.ofHours(48)))) {
            materialCleanupAudit.record(
                run,
                stored.key(),
                null,
                version,
                "skipped",
                stored.lastModified() == null ? "missing_age" : "too_young");
            skipped++;
            continue;
          }
          Stored storedObject = privateMaterialStore.head(stored.key());
          version = storedObject.versionId();
          purpose = storedObject.metadata().get("purpose");
          Receipt receipt = trusted(storedObject);
          if (receipt == null) {
            materialCleanupAudit.record(
                run, stored.key(), purpose, version, "skipped", "untrusted_metadata");
            skipped++;
            continue;
          }
          candidate++;
          String result =
              transactionTemplate.execute(
                  status -> {
                    if (loginMapper.selectWritablePrimaryFlag() != 1) return "primary_unconfirmed";
                    if (loginMapper.selectUserByIdForUpdate(receipt.owner()) == null)
                      return "owner_absent";
                    if (!eligibleTime(storedObject, receipt)) return "too_young";
                    return materialReferenceMapper.countMaterialReferencesByOwnerAndKey(
                                receipt.owner(), receipt.objectKey())
                            > 0
                        ? "referenced"
                        : "candidate";
                  });
          if ("referenced".equals(result)) {
            materialCleanupAudit.record(
                run, storedObject.key(), purpose, version, "retained", result);
            retained++;
            continue;
          }
          if (!"candidate".equals(result)) {
            materialCleanupAudit.record(
                run,
                storedObject.key(),
                purpose,
                version,
                "unknown",
                result == null ? "check_incomplete" : result);
            unknown++;
            continue;
          }
          if (reportOnly || !privateMaterialStore.deletionPolicyVerified()) {
            materialCleanupAudit.record(
                run,
                storedObject.key(),
                purpose,
                version,
                "skipped",
                reportOnly ? "report_only_candidate" : "policy_denied");
            skipped++;
            continue;
          }
          // Persist intent BEFORE the non-atomic cloud operation. Failure here prohibits deletion.
          phase = "delete_intent_audit";
          materialCleanupAudit.record(
              run, storedObject.key(), purpose, version, "delete_intent", "unreferenced_expired");
          phase = "delete_response_unknown";
          privateMaterialStore.delete(storedObject.key(), storedObject.versionId());
          phase = "delete_outcome_audit";
          materialCleanupAudit.record(
              run, storedObject.key(), purpose, version, "deleted", "delete_acknowledged");
          deleted++;
        } catch (RuntimeException e) {
          unknown++;
          com.mujian.backend.common.api.FailureDiagnostics.background(
              "material_cleanup_" + phase, run, e);
          safeUnknown(run, stored.key(), purpose, version, phase);
        }
      }
      String next = page.nextMarker();
      if (next != null && (!markers.add(next) || next.equals(marker))) {
        completed = false;
        unknown++;
        safeUnknown(run, null, null, null, "pagination_marker_repeated");
        break;
      }
      marker = next;
    } while (marker != null);
    return new Report(
        run, reportOnly, scanned, candidate, retained, skipped, deleted, unknown, completed);
  }

  /** 记录无法确认的清理结果，不冒称删除成功。 */
  private void safeUnknown(String run, String key, String purpose, String version, String reason) {
    try {
      materialCleanupAudit.record(run, key, purpose, version, "unknown", reason);
    } catch (RuntimeException e) {
      // MaterialCleanupAudit already emitted a sanitized structured fallback. A failed durable
      // outcome is UNKNOWN even if the object disappeared; reconcile the intent before closure.
    }
  }

  /** 判断对象是否达到既定未提交清理时间。 */
  private boolean eligibleTime(
      PrivateMaterialStore.Stored stored, VerificationMaterials.Receipt receipt) {
    return !clock.instant().isBefore(stored.lastModified().plus(Duration.ofHours(48)))
        && !clock.instant().isBefore(receipt.acceptUntil());
  }

  /** 校验对象元数据及合法归属，失败时禁止删除。 */
  private VerificationMaterials.Receipt trusted(PrivateMaterialStore.Stored stored) {
    try {
      Map<String, String> m = stored.metadata();
      String owner = m.get("owner"),
          purpose = m.get("purpose"),
          id = m.get("upload-id"),
          d = VerificationMaterials.domain(purpose),
          version = m.get("consent-version");
      Instant at = Instant.parse(m.get("accepted-at")),
          until = Instant.parse(m.get("accept-until"));
      VerificationMaterials.EvidenceScope scope = null;
      if ("custom_material".equals(purpose)) {
        String resource=com.mujian.backend.common.support.D2Values.id(m.get("order-id")), node=m.get("evidence-node"), role=m.get("operator-role");
        if (!(FulfillmentRole.buyer.name().equals(role) && java.util.Set.of("requirement","after_sale").contains(node)
            || FulfillmentRole.factory.name().equals(role) && java.util.Set.of("extension","delivery").contains(node)))return null;
        scope=new VerificationMaterials.EvidenceScope(resource,node,role);
      }
      if ("order_evidence".equals(purpose)) {
        String orderId = com.mujian.backend.common.support.D2Values.id(m.get("order-id"));
        com.mujian.backend.common.enums.EvidenceNode node =
            com.mujian.backend.common.enums.EvidenceNode.valueOf(m.get("evidence-node"));
        com.mujian.backend.common.enums.FulfillmentRole role =
            com.mujian.backend.common.enums.FulfillmentRole.valueOf(m.get("operator-role"));
        if (!node.permits(role)) return null;
        scope = new VerificationMaterials.EvidenceScope(orderId, node.name(), role.name());
      }
      if (!VerificationMaterials.key(owner, d, id).equals(stored.key())
          || !until.equals(at.plus(Duration.ofHours(24)))
          || !version.equals(d + "-material-v1")
          || stored.lastModified().isBefore(at.minusSeconds(1))
          || stored.lastModified().isAfter(until)
          || !eligibleTime(
              stored,
              new VerificationMaterials.Receipt(
                  owner, purpose, id, stored.key(), version, at, until, scope))) return null;
      return new VerificationMaterials.Receipt(
          owner, purpose, id, stored.key(), version, at, until, scope);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
