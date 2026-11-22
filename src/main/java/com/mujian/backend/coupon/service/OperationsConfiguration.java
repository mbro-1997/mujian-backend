package com.mujian.backend.coupon.service;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.admin.service.CommandAuditService;
import com.mujian.backend.admin.service.CommandAuditService.Command;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.coupon.dto.OperationsInput;
import com.mujian.backend.coupon.mapper.*;
import com.mujian.backend.coupon.vo.OperationsViews.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Service
public class OperationsConfiguration {
  private final OperationsWriteMapper operationsWriteMapper;
  private final OperationsReader operationsReader;
  private final CommandAuditService commandAuditService;
  private final DistributionMapper distributionMapper;
  private final com.mujian.backend.admin.mapper.AdministrationMapper administrationMapper;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  /** 复用统一审计事务与固定版本读模型。 */
  public OperationsConfiguration(
      OperationsWriteMapper operationsWriteMapper,
      OperationsReader operationsReader,
      CommandAuditService commandAuditService,
      DistributionMapper distributionMapper,
      com.mujian.backend.admin.mapper.AdministrationMapper administrationMapper,
      Clock clock,
      ObjectMapper objectMapper) {
    this.operationsWriteMapper = operationsWriteMapper;
    this.operationsReader = operationsReader;
    this.commandAuditService = commandAuditService;
    this.distributionMapper = distributionMapper;
    this.administrationMapper = administrationMapper;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  /** 新版本沿同一模板编码递增，保存只修改草稿。 */
  public Template template(Command command, String id, OperationsInput.Template operationsInputTemplate) {
    return commandAuditService.execute(
        command,
        "admin:coupon-template:manage",
        id == null ? "createTemplate" : "editTemplate",
        "保存券模板",
        "12",
        "coupon_template",
        id,
        operationsInputTemplate,
        Template.class,
        Template::id,
        before -> {
          LocalDateTime at = now(clock);
          if (operationsInputTemplate.validStartAt() != null && !operationsInputTemplate.validStartAt().isBefore(operationsInputTemplate.validEndAt()))
            throw ApiFailure.invalid();
          if (!operationsInputTemplate.factoryIds().isEmpty()
              && operationsWriteMapper.countFactories(operationsInputTemplate.factoryIds())
                  != operationsInputTemplate.factoryIds().size()) throw ApiFailure.invalid();
          String target = id;
          if (id == null) {
            administrationMapper.lockRole(
                com.mujian.backend.common.support.SystemIdentity.PLATFORM_ADMIN_ROLE_ID);
            target = Ids.next();
            operationsWriteMapper.insertTemplate(
                target,
                command.actor(),
                operationsInputTemplate,
                operationsWriteMapper.nextTemplateVersion(operationsInputTemplate.templateCode()),
                at);
          } else {
            Template old = operationsReader.readTemplate(id, true);
            before.accept(old);
            if (!old.snapshotToken().equals(operationsInputTemplate.snapshotToken()))
              throw conflict(D2Codes.CODE_VERSION_CONFLICT);
            if (!"draft".equals(old.status()) || !old.templateCode().equals(operationsInputTemplate.templateCode()))
              throw conflict(BusinessFailureReason.STATE_NOT_EDITABLE.name());
            operationsWriteMapper.updateTemplate(id, operationsInputTemplate, at);
            operationsWriteMapper.removeScope(id);
          }
          List<OperationsWriteMapper.Scope> scopes = new ArrayList<>();
          for (String factory : operationsInputTemplate.factoryIds())
            scopes.add(new OperationsWriteMapper.Scope(Ids.next(), factory));
          if (!scopes.isEmpty()) operationsWriteMapper.insertScope(target, scopes, at);
          return operationsReader.readTemplate(target, false);
        });
  }

  /** 发布、暂停、恢复及结束沿原版本执行。 */
  public Template templateState(
      Command command, String id, String action, OperationsInput.Snapshot operationsInputSnapshot) {
    return commandAuditService.execute(
        command,
        "admin:coupon-template:manage",
        action + "Template",
        "变更券模板状态",
        "12",
        "coupon_template",
        id,
        operationsInputSnapshot,
        Template.class,
        Template::id,
        before -> {
          Template old = operationsReader.readTemplate(id, true);
          before.accept(old);
          if (!old.snapshotToken().equals(operationsInputSnapshot.snapshotToken()))
            throw conflict(D2Codes.CODE_VERSION_CONFLICT);
          String target =
              switch (action) {
                case "publish" -> "draft".equals(old.status()) ? "active" : null;
                case "pause" -> "active".equals(old.status()) ? "paused" : null;
                case "resume" -> "paused".equals(old.status()) ? "active" : null;
                case "end" -> Set.of("paused", "active").contains(old.status()) ? "ended" : null;
                default -> null;
              };
          if (target == null) throw conflict(BusinessFailureReason.STATE_NOT_EDITABLE.name());
          if ("active".equals(target)
              && old.validEndAt() != null
              && !clock.instant().isBefore(OffsetDateTime.parse(old.validEndAt()).toInstant()))
            throw conflict(BusinessFailureReason.TEMPLATE_NOT_ISSUABLE.name());
          operationsWriteMapper.setTemplateStatus(id, target, now(clock));
          return operationsReader.readTemplate(id, false);
        });
  }

  /** 创建或编辑条件活动草稿，窗口始终采用业务时区及毫秒。 */
  public Activity activity(Command command, String id, OperationsInput.Activity activity) {
    return commandAuditService.execute(
        command,
        "admin:coupon-activity:manage",
        id == null ? "createActivity" : "editActivity",
        "保存领券活动",
        "12",
        "coupon_distribution",
        id,
        activity,
        Activity.class,
        Activity::id,
        before -> {
          if (!activity.startsAt().isBefore(activity.endsAt())) throw ApiFailure.invalid();
          String target = id;
          if (id != null) {
            Activity old = operationsReader.readActivity(id, true);
            before.accept(old);
            if (old.version() != activity.version()) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
            if (!"draft".equals(old.status())) throw conflict(BusinessFailureReason.STATE_NOT_EDITABLE.name());
          }
          Template fixed = operationsReader.readTemplate(activity.templateId(), true);
          if (!"active".equals(fixed.status())) throw conflict(BusinessFailureReason.TEMPLATE_NOT_ISSUABLE.name());
          if (id == null) {
            target = Ids.next();
            ObjectNode config = objectMapper.createObjectNode();
            config.put("schemaVersion", 1);
            config.put("mode", "conditional_claim");
            config.put("templateId", activity.templateId());
            config.put("condition", activity.condition());
            config.put(
                "startsAt",
                time(
                    activity
                        .startsAt()
                        .atZoneSameInstant(com.mujian.backend.common.support.D2Values.ZONE)
                        .toLocalDateTime()));
            config.put(
                "endsAt",
                time(
                    activity
                        .endsAt()
                        .atZoneSameInstant(com.mujian.backend.common.support.D2Values.ZONE)
                        .toLocalDateTime()));
            config.put("reason", activity.reason());
            String digest;
            try {
              digest =
                  HexFormat.of()
                      .formatHex(
                          MessageDigest.getInstance("SHA-256")
                              .digest(objectMapper.writeValueAsBytes(config)));
            } catch (NoSuchAlgorithmException error) {
              throw new IllegalStateException(error);
            }
            operationsWriteMapper.insertActivity(
                target, command.actor(), command.key(), digest, activity, now(clock));
          } else if (operationsWriteMapper.updateActivity(id, activity) != 1)
            throw conflict(D2Codes.CODE_VERSION_CONFLICT);
          return operationsReader.readActivity(target, false);
        });
  }

  /** 发布与取消均使用活动版本；发布前重新核对原模板。 */
  public Activity activityState(
      Command command, String id, String action, OperationsInput.Version version) {
    return commandAuditService.execute(
        command,
        "admin:coupon-activity:manage",
        action + "Activity",
        "变更领券活动",
        "12",
        "coupon_distribution",
        id,
        version,
        Activity.class,
        Activity::id,
        before -> {
          Activity old = operationsReader.readActivity(id, true);
          before.accept(old);
          if (old.version() != version.version()) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
          boolean publish = "publish".equals(action);
          if (!(publish
              ? "draft".equals(old.status())
              : Set.of("draft", "active").contains(old.status())))
            throw conflict(BusinessFailureReason.STATE_NOT_EDITABLE.name());
          if (publish) {
            if (!distributionMapper.lockTemplate(old.templateId()).issuable(now(clock)))
              throw conflict(BusinessFailureReason.TEMPLATE_NOT_ISSUABLE.name());
            if (!clock.instant().isBefore(OffsetDateTime.parse(old.endsAt()).toInstant()))
              throw conflict(BusinessFailureReason.STATE_NOT_EDITABLE.name());
          }
          if (operationsWriteMapper.setActivityStatus(
                  id, publish ? "active" : "cancelled", version.version(), now(clock))
              != 1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
          return operationsReader.readActivity(id, false);
        });
  }

  /** 事件只引用固定金额模板，启用前可调整草稿。 */
  public Rule rule(Command command, String id, OperationsInput.Rule operationsInputRule) {
    return commandAuditService.execute(
        command,
        "admin:coupon-reward:manage",
        id == null ? "createRewardRule" : "editRewardRule",
        "保存首次奖励规则",
        "12",
        "coupon_reward_rule",
        id,
        operationsInputRule,
        Rule.class,
        Rule::id,
        before -> {
          administrationMapper.lockRole(
              com.mujian.backend.common.support.SystemIdentity.PLATFORM_ADMIN_ROLE_ID);
          String target = id;
          if (id != null) {
            Rule old = operationsReader.readRule(id, true);
            before.accept(old);
            if (!old.snapshotToken().equals(operationsInputRule.snapshotToken()))
              throw conflict(D2Codes.CODE_VERSION_CONFLICT);
            if (!old.eventType().equals(operationsInputRule.eventType()) || !"draft".equals(old.status()))
              throw conflict(BusinessFailureReason.STATE_NOT_EDITABLE.name());
          }
          Template template = operationsReader.readTemplate(operationsInputRule.templateId(), false);
          if (!"fixed_amount".equals(template.couponType()))
            throw conflict(BusinessFailureReason.TEMPLATE_NOT_ISSUABLE.name());
          if (id == null) {
            target = Ids.next();
            operationsWriteMapper.insertRule(
                target,
                command.actor(),
                operationsInputRule,
                operationsWriteMapper.nextRuleVersion(operationsInputRule.eventType()),
                now(clock));
          } else operationsWriteMapper.updateRule(id, operationsInputRule.templateId());
          return operationsReader.readRule(target, false);
        });
  }

  /** 启用新版本与结束旧版本在同一事务中进行。 */
  public Rule ruleState(Command command, String id, String action, OperationsInput.Snapshot operationsInputSnapshot) {
    return commandAuditService.execute(
        command,
        "admin:coupon-reward:manage",
        action + "RewardRule",
        "变更首次奖励规则",
        "12",
        "coupon_reward_rule",
        id,
        operationsInputSnapshot,
        Rule.class,
        Rule::id,
        before -> {
          administrationMapper.lockRole(
              com.mujian.backend.common.support.SystemIdentity.PLATFORM_ADMIN_ROLE_ID);
          Rule old = operationsReader.readRule(id, true);
          before.accept(old);
          if (!old.snapshotToken().equals(operationsInputSnapshot.snapshotToken()))
            throw conflict(D2Codes.CODE_VERSION_CONFLICT);
          boolean activate = "activate".equals(action);
          if (!(activate ? "draft" : "active").equals(old.status()))
            throw conflict(BusinessFailureReason.STATE_NOT_EDITABLE.name());
          if (activate) {
            com.mujian.backend.coupon.projection.DistributionRows.Template template =
                distributionMapper.lockTemplate(old.templateId());
            if (!template.issuable(now(clock)) || !"fixed_amount".equals(template.couponType()))
              throw conflict(BusinessFailureReason.TEMPLATE_NOT_ISSUABLE.name());
            operationsWriteMapper.endActiveRule(old.eventType(), now(clock));
          }
          operationsWriteMapper.setRuleStatus(id, activate ? "active" : "ended", now(clock));
          return operationsReader.readRule(id, false);
        });
  }
}
