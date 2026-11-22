package com.mujian.backend.coupon.service;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.enums.FulfillmentReason;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.coupon.dto.IssueCouponsInput;
import com.mujian.backend.coupon.mapper.*;
import com.mujian.backend.coupon.mapper.ManualIssueMapper.*;
import com.mujian.backend.coupon.projection.DistributionRows.*;
import com.mujian.backend.material.service.VerificationAccess;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class ManualIssueService {
  private final DistributionMapper distributionMapper;
  private final ManualIssueMapper manualIssueMapper;
  private final VerificationAccess verificationAccess;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;

  /** 使用独立提交边界，只有数据库回滚返回后才能确认失败。 */
  public ManualIssueService(
      DistributionMapper distributionMapper,
      ManualIssueMapper manualIssueMapper,
      VerificationAccess verificationAccess,
      ObjectMapper objectMapper,
      Clock clock,
      PlatformTransactionManager manager) {
    this.distributionMapper = distributionMapper;
    this.manualIssueMapper = manualIssueMapper;
    this.verificationAccess = verificationAccess;
    this.objectMapper = objectMapper;
    this.clock = clock;
    transactionTemplate = new TransactionTemplate(manager);
    transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /** 无序指定名单变为去重的 ASCII 顺序，摘要不含运行时目标集。 */
  public Receipt issue(String actor, String key, String trace, IssueCouponsInput issueCouponsInput) {
    verificationAccess.admin(actor, "admin:coupon:issue");
    if (key == null || key.isEmpty() || key.length() > 128 || issueCouponsInput.quantityPerUser() < 1)
      throw ApiFailure.invalid();
    String reason = text(issueCouponsInput.reason(), 500);
    List<String> targets = null;
    if ("selected_users".equals(issueCouponsInput.mode())) {
      if (issueCouponsInput.userIds() == null || issueCouponsInput.userIds().isEmpty() || issueCouponsInput.audienceFilter() != null)
        throw ApiFailure.invalid();
      TreeSet<String> unique = new TreeSet<>();
      for (String target : issueCouponsInput.userIds()) {
        if (target == null || !target.matches("[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}"))
          throw new ApiFailure(400, D2Codes.CODE_INVALID_ARGUMENT, BusinessFailureReason.TARGET_USER_INVALID.name());
        unique.add(target);
      }
      targets = List.copyOf(unique);
    } else if (!"all_users".equals(issueCouponsInput.mode())
        || issueCouponsInput.userIds() != null
        || !new IssueCouponsInput.Audience(D2Codes.CODE_DEMANDER, "active").equals(issueCouponsInput.audienceFilter()))
      throw ApiFailure.invalid();
    IssueCouponsInput normalized =
        new IssueCouponsInput(
            issueCouponsInput.mode(),
            id(issueCouponsInput.templateId()),
            issueCouponsInput.quantityPerUser(),
            reason,
            targets,
            issueCouponsInput.audienceFilter());
    ObjectNode intent = objectMapper.createObjectNode();
    intent.put("schemaVersion", 1);
    intent.put("mode", issueCouponsInput.mode());
    intent.put("templateId", issueCouponsInput.templateId());
    intent.put("quantityPerUser", issueCouponsInput.quantityPerUser());
    intent.set("userIds", objectMapper.valueToTree(targets));
    intent.set("audienceFilter", objectMapper.valueToTree(issueCouponsInput.audienceFilter()));
    intent.put("reason", reason);
    String digest;
    try {
      String canonical = objectMapper.writeValueAsString(intent);
      // Jackson 使用大写控制符转义；专项契约固定小写十六进制。
      StringBuilder bytes = new StringBuilder();
      for (int i = 0; i < canonical.length(); i++) {
        char c = canonical.charAt(i);
        bytes.append(c);
        if (c == '\\') {
          char escaped = canonical.charAt(++i);
          bytes.append(escaped);
          if (escaped == 'u') {
            bytes.append(canonical.substring(i + 1, i + 5).toLowerCase(Locale.ROOT));
            i += 4;
          }
        }
      }
      digest =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(bytes.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    ObjectNode config = objectMapper.createObjectNode();
    config.put("schema_version", 1);
    config.put("mode", issueCouponsInput.mode());
    config.put("template_id", issueCouponsInput.templateId());
    config.put("quantity_per_user", issueCouponsInput.quantityPerUser());
    config.set("user_ids", objectMapper.valueToTree(targets));
    if (issueCouponsInput.audienceFilter() == null) config.putNull("audience_filter");
    else {
      ObjectNode audience = config.putObject("audience_filter");
      audience.put("business_role", D2Codes.CODE_DEMANDER);
      audience.put("account_status", "active");
    }
    config.put("reason", reason);
    try {
      return transactionTemplate.execute(
          status -> {
            verificationAccess.admin(actor, "admin:coupon:issue");
            Batch previous = manualIssueMapper.selectByKey(key);
            if (previous != null) return replay(previous, actor, digest);
            String batch = Ids.next();
            if (manualIssueMapper.insertBatch(
                    batch,
                    actor,
                    key,
                    digest,
                    normalized,
                    objectMapper.writeValueAsString(config),
                    now(clock))
                != 1) throw conflict(BusinessFailureReason.TEMPLATE_NOT_ISSUABLE.name());
            Template template = distributionMapper.lockTemplate(issueCouponsInput.templateId());
            LocalDateTime at = now(clock);
            if (template == null || !template.issuable(at)) throw conflict(BusinessFailureReason.TEMPLATE_NOT_ISSUABLE.name());
            List<String> users = manualIssueMapper.selectTargetUsers(normalized.userIds());
            if (normalized.userIds() != null && users.size() != normalized.userIds().size())
              throw new ApiFailure(400, D2Codes.CODE_INVALID_ARGUMENT, BusinessFailureReason.TARGET_USER_INVALID.name());
            if (users.isEmpty()) throw conflict(BusinessFailureReason.NO_ELIGIBLE_USERS.name());
            long quantity = Math.multiplyExact((long) users.size(), normalized.quantityPerUser());
            List<CouponWrite> chunk = new ArrayList<>(256);
            for (String user : users)
              for (long ordinal = 1; ordinal <= normalized.quantityPerUser(); ordinal++) {
                chunk.add(
                    new CouponWrite(
                        Ids.next(),
                        user,
                        "distribution:" + batch + ":user:" + user + ":ordinal:" + ordinal,
                        Ids.next()));
                if (chunk.size() == 256) {
                  writeChunk(chunk, template, batch, actor, reason, at);
                  chunk.clear();
                }
              }
            if (!chunk.isEmpty()) writeChunk(chunk, template, batch, actor, reason, at);
            manualIssueMapper.insertIssueAudit(Ids.next(), actor, batch, key, trace, reason, at);
            distributionMapper.incrementTemplate(template.id(), quantity, at);
            manualIssueMapper.completeBatch(batch, users.size(), quantity, at);
            return new Receipt(
                batch,
                "completed",
                Integer.toString(users.size()),
                Long.toString(quantity),
                time(at));
          });
    } catch (DuplicateKeyException collision) {
      verificationAccess.admin(actor, "admin:coupon:issue");
      Batch winner = manualIssueMapper.selectByKey(key);
      if (winner != null) return replay(winner, actor, digest);
      throw conflict(BusinessFailureReason.ISSUE_IN_PROGRESS.name());
    } catch (PessimisticLockingFailureException uncertain) {
      throw conflict(BusinessFailureReason.ISSUE_IN_PROGRESS.name());
    } catch (DataIntegrityViolationException rolledBack) {
      throw new ApiFailure(500, D2Codes.CODE_INTERNAL_ERROR, BusinessFailureReason.ISSUE_ROLLED_BACK.name());
    }
  }

  /** 券和流水仅分 SQL，不分事务。 */
  private void writeChunk(
      List<CouponWrite> coupons,
      Template template,
      String batch,
      String actor,
      String reason,
      LocalDateTime at) {
    distributionMapper.insertCoupons(coupons, template, "manual_operation", batch, at);
    distributionMapper.insertIssuedLogs(coupons, FulfillmentRole.admin.name(), actor, "distribution", batch, reason, at);
  }

  /** 所有者及摘要均匹配才回显原回执。 */
  private Receipt replay(Batch batch, String actor, String digest) {
    if (!actor.equals(batch.owner())) throw ApiFailure.denied(BusinessFailureReason.REQUEST_OWNER_MISMATCH.name());
    if (!digest.equals(batch.digest()) || "conditional_claim".equals(batch.mode()))
      throw new ApiFailure(409, FulfillmentReason.IDEMPOTENCY_CONFLICT.name(), BusinessFailureReason.ISSUE_CONTENT_MISMATCH.name());
    return new Receipt(
        batch.id(),
        "completed",
        Long.toString(batch.targets()),
        Long.toString(batch.quantity()),
        time(batch.completedAt()));
  }
}
