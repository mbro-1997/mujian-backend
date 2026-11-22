package com.mujian.backend.factory.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.infrastructure.persistence.MappingInputs.first;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.casework.mapper.*;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.FactoryInvitationStatus;
import com.mujian.backend.common.enums.FactoryReasonCode;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.dto.FactoryDtos.Invitation;
import com.mujian.backend.factory.dto.FactoryDtos.InvitationCreate;
import com.mujian.backend.factory.mapper.FactoryInviteCodeMapper;
import com.mujian.backend.factory.mapper.FactoryOnboardingAuditMapper;
import com.mujian.backend.factory.projection.*;
import com.mujian.backend.factory.service.*;
import com.mujian.backend.web.response.account.ContextResponse;
import java.security.SecureRandom;
import java.time.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class FactoryInvitationServiceImpl implements FactoryInvitationService {
  private final FactoryAccess factoryAccess;
  private final FactoryOnboardingAuditMapper factoryOnboardingAuditMapper;
  private final FactoryInviteCodeMapper factoryInviteCodeMapper;
  private final LoginMapper loginMapper;
  private final AdminOperationAuditMapper adminOperationAuditMapper;
  private final FactoryRequests factoryRequests;
  private final FactoryAudit factoryAudit;
  private final StringRedisTemplate stringRedisTemplate;
  private final Clock clock;
  private final ObjectMapper objectMapper;
  private final String prefix;
  private static final SecureRandom RANDOM = new SecureRandom();
  private static final String ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactoryInvitationServiceImpl(
      FactoryAccess factoryAccess,
      FactoryOnboardingAuditMapper factoryOnboardingAuditMapper,
      FactoryInviteCodeMapper factoryInviteCodeMapper,
      LoginMapper loginMapper,
      AdminOperationAuditMapper adminOperationAuditMapper,
      FactoryRequests factoryRequests,
      FactoryAudit factoryAudit,
      StringRedisTemplate stringRedisTemplate,
      Clock clock,
      ObjectMapper objectMapper,
      @Value("${mj.environment:production}") String env) {
    this.factoryAccess = factoryAccess;
    this.factoryOnboardingAuditMapper = factoryOnboardingAuditMapper;
    this.factoryInviteCodeMapper = factoryInviteCodeMapper;
    this.loginMapper = loginMapper;
    this.adminOperationAuditMapper = adminOperationAuditMapper;
    this.factoryRequests = factoryRequests;
    this.factoryAudit = factoryAudit;
    this.stringRedisTemplate = stringRedisTemplate;
    this.clock = clock;
    this.objectMapper = objectMapper;
    prefix = "mujian:" + env + ":factory-invite:";
  }

  /** 按有效期和申请使用情况计算邀请码展示状态。 */
  public String status(FactoryInviteProjection factoryInviteProjection, boolean submitted) {
    return !submitted
            && Set.of(FactoryInvitationStatus.UNUSED.code(), FactoryInvitationStatus.LOCKED.code())
                .contains(factoryInviteProjection.getInviteStatus())
            && !now(clock).isBefore(factoryInviteProjection.getExpireAt())
        ? FactoryInvitationStatus.EXPIRED.code()
        : factoryInviteProjection.getInviteStatus();
  }

  /** 组装邀请码响应，按操作权限控制可作废标志。 */
  public Invitation invitation(
      FactoryInviteProjection factoryInviteProjection, boolean submitted, boolean manage) {
    return new Invitation(
        factoryInviteProjection.getId(),
        factoryInviteProjection.getCodeSuffix(),
        status(factoryInviteProjection, submitted),
        time(factoryInviteProjection.getExpireAt()),
        factoryInviteProjection.getLockedByUserId(),
        submitted,
        manage
            && !submitted
            && Set.of(FactoryInvitationStatus.UNUSED.code(), FactoryInvitationStatus.LOCKED.code())
                .contains(factoryInviteProjection.getInviteStatus()),
        time(factoryInviteProjection.getCreatedAt()),
        factoryInviteProjection.getRemark());
  }

  /** 读取邀请码短期明文并组装创建或恢复结果。 */
  private InvitationResult result(FactoryInviteProjection factoryInviteProjection, String actor) {
    String raw;
    try {
      raw = stringRedisTemplate.opsForValue().get(prefix + factoryInviteProjection.getId());
    } catch (Exception e) {
      throw ApiFailure.redis().causedBy(e);
    }
    return new InvitationResult(
        invitation(
            factoryInviteProjection,
            factoryOnboardingAuditMapper.hasFactoryOnboardingByInvitation(
                factoryInviteProjection.getId()),
            factoryAccess.has(actor, "factory-invite", "manage")),
        raw == null ? "unavailable" : "available",
        raw,
        time(factoryInviteProjection.getCreatedAt().plusMinutes(10)));
  }

  /** 创建业务记录，保留原幂等与事务边界。 */
  @Transactional
  public InvitationResult create(
      String actor, String key, InvitationCreate invitationCreate, String trace) {
    factoryAccess.admin(actor, "factory-invite", "manage");
    key = id(key);
    InvitationCreate invitationCreateInput =
        new InvitationCreate(
            invitationCreate.expireAt(), FactoryRequests.optional(invitationCreate.remark(), 255));
    String hash = factoryRequests.hash("createFactoryInvitation", actor, invitationCreateInput);
    // 同一管理员串行，先恢复已提交原记录，再分配新随机码。Redis从不独立证明DB提交。
    factoryAccess.lock(actor);
    FactoryInviteProjection factoryInviteProjectionOld =
        first(factoryInviteCodeMapper.selectFactoryInvitationByRequest(actor, key));
    if (factoryInviteProjectionOld != null) {
      if (!hash.equals(factoryInviteProjectionOld.getRequestHash()))
        throw conflict(CODE_IDEMPOTENCY_KEY_REUSED);
      return result(factoryInviteProjectionOld, actor);
    }
    LocalDateTime now = now(clock);
    LocalDateTime until =
        invitationCreate.expireAt() == null
            ? now.plusDays(7)
            : OffsetDateTime.parse(invitationCreate.expireAt())
                .atZoneSameInstant(ZONE)
                .toLocalDateTime();
    if (!until.isAfter(now)) throw ApiFailure.invalid();
    String id = Ids.next();
    StringBuilder code = new StringBuilder();
    for (int i = 0; i < 20; i++) code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
    try {
      stringRedisTemplate.opsForValue().set(prefix + id, code.toString(), Duration.ofMinutes(10));
    } catch (Exception e) {
      throw ApiFailure.redis().causedBy(e);
    }
    factoryInviteCodeMapper.insertFactoryInvitation(
        new FactoryInviteCodeDO()
            .id(id)
            .codeHash(FactoryRequests.sha(code.toString()))
            .createRequestId(key)
            .requestHash(hash)
            .codeSuffix(code.substring(16))
            .inviteStatus(FactoryInvitationStatus.UNUSED.code())
            .expireAt(until)
            .createdByAdminUserId(actor)
            .remark(invitationCreateInput.remark())
            .createdAt(now)
            .updatedAt(now));
    factoryAudit.record(
        actor,
        "factory_invite_code",
        id,
        "factory_invitation_create",
        "admin:factory-invite:manage",
        trace,
        null,
        null,
        null,
        invitationCreateInput.remark(),
        null,
        view("invitationId", id, "requestHash", hash));
    return result(factoryInviteCodeMapper.selectFactoryInvitationById(id, false), actor);
  }

  /** 按原请求编号恢复已提交结果并核对访问权限。 */
  public InvitationResult recover(String actor, String key) {
    factoryAccess.admin(actor, "factory-invite", "manage");
    FactoryInviteProjection factoryInviteProjection =
        first(factoryInviteCodeMapper.selectFactoryInvitationForRecovery(actor, id(key)));
    if (factoryInviteProjection == null) throw missing();
    return result(factoryInviteProjection, actor);
  }

  /** 按固定锁序认领邀请码并更新账号准入阶段。 */
  @Transactional
  public ClaimResult claim(String owner, ClaimRequest claimRequest) {
    factoryAccess.lock(owner);
    factoryAccess.entrant(owner, true);
    String code = claimRequest.inviteCode().replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    if (code.length() != 20 || code.chars().anyMatch(c -> ALPHABET.indexOf(c) < 0))
      throw conflict(FactoryReasonCode.INVITATION_INVALID.name());
    FactoryInviteProjection factoryInviteProjection =
        first(
            factoryInviteCodeMapper.selectFactoryInvitationByHashForUpdate(
                FactoryRequests.sha(code)));
    if (factoryInviteProjection == null)
      throw conflict(FactoryReasonCode.INVITATION_INVALID.name());
    if (factoryInviteProjection.getLockedByUserId() != null
        && !owner.equals(factoryInviteProjection.getLockedByUserId()))
      throw conflict(FactoryReasonCode.INVITATION_ALREADY_CLAIMED.name());
    if (factoryOnboardingAuditMapper.hasFactoryOnboardingByInvitation(
        factoryInviteProjection.getId()))
      throw conflict(FactoryReasonCode.INVITATION_HAS_APPLICATION.name());
    if (FactoryInvitationStatus.REVOKED.code().equals(factoryInviteProjection.getInviteStatus()))
      throw conflict(FactoryReasonCode.INVITATION_UNAVAILABLE.name());
    if (!now(clock).isBefore(factoryInviteProjection.getExpireAt()))
      throw conflict(FactoryReasonCode.INVITATION_EXPIRED.name());
    if (!Set.of(FactoryInvitationStatus.UNUSED.code(), FactoryInvitationStatus.LOCKED.code())
        .contains(factoryInviteProjection.getInviteStatus()))
      throw conflict(FactoryReasonCode.INVITATION_UNAVAILABLE.name());
    FactoryInviteProjection factoryInviteProjectionCurrent =
        factoryInviteCodeMapper.selectLatestFactoryInvitationByOwner(owner);
    if (factoryInviteProjectionCurrent != null
        && !factoryInviteProjectionCurrent.getId().equals(factoryInviteProjection.getId())
        && (factoryOnboardingAuditMapper.hasFactoryOnboardingByInvitation(
                factoryInviteProjectionCurrent.getId())
            || FactoryInvitationStatus.LOCKED
                .code()
                .equals(status(factoryInviteProjectionCurrent, false))))
      throw conflict(FactoryReasonCode.INVITATION_UNAVAILABLE.name());
    if (!owner.equals(factoryInviteProjection.getLockedByUserId()))
      factoryInviteCodeMapper.claimFactoryInvitation(
          new FactoryInviteCodeDO()
              .lockedByUserId(owner)
              .lockedAt(now(clock))
              .inviteStatus(FactoryInvitationStatus.LOCKED.code())
              .updatedAt(now(clock))
              .id(factoryInviteProjection.getId()));
    loginMapper.markFactoryInvitationLocked(now(clock), owner);
    ContextResponse contextResponse = factoryAccess.context(owner, false);
    return new ClaimResult(
        factoryInviteProjection.getId(),
        time(factoryInviteProjection.getExpireAt()),
        new Context(
            contextResponse.userId(),
            contextResponse.nickname(),
            contextResponse.userStatus(),
            contextResponse.roleCode(),
            contextResponse.registrationStage(),
            contextResponse.accessMode(),
            contextResponse.nextAction()));
  }

  /** 核对邀请码使用情况后作废并记录审计。 */
  @Transactional
  public Invitation revoke(
      String actor, String target, String key, RevokeRequest revokeRequest, String trace) {
    factoryAccess.admin(actor, "factory-invite", "manage");
    target = id(target);
    key = id(key);
    String reason = text(revokeRequest.reason(), 255),
        hash = factoryRequests.hash("revokeFactoryInvitation", target, new RevokeRequest(reason)),
        auditKey = "factory-invite-revoke:" + actor + ":" + key;
    FactoryInviteProjection factoryInviteProjectionHint =
        factoryInviteCodeMapper.selectFactoryInvitationById(target, false);
    if (factoryInviteProjectionHint == null) throw missing();
    factoryAccess.lock(actor, factoryInviteProjectionHint.getLockedByUserId());
    FactoryInviteProjection factoryInviteProjection =
        factoryInviteCodeMapper.selectFactoryInvitationById(target, true);
    if (!Objects.equals(
        factoryInviteProjectionHint.getLockedByUserId(),
        factoryInviteProjection.getLockedByUserId())) throw conflict(CODE_VERSION_CONFLICT);
    List<String> old =
        adminOperationAuditMapper.selectInvitationRevocationAuditByRequest(actor, auditKey);
    if (!old.isEmpty()) {
      JsonNode jsonNode = objectMapper.readTree(old.get(0));
      if (!hash.equals(jsonNode.path("requestHash").asString()))
        throw conflict(CODE_IDEMPOTENCY_KEY_REUSED);
      return objectMapper.treeToValue(jsonNode.get("result"), Invitation.class);
    }
    if (factoryOnboardingAuditMapper.hasFactoryOnboardingByInvitation(target))
      throw conflict(FactoryReasonCode.INVITATION_HAS_APPLICATION.name());
    if (!Set.of(FactoryInvitationStatus.UNUSED.code(), FactoryInvitationStatus.LOCKED.code())
        .contains(factoryInviteProjection.getInviteStatus()))
      throw conflict(FactoryReasonCode.INVITATION_UNAVAILABLE.name());
    factoryInviteCodeMapper.revokeFactoryInvitation(
        new FactoryInviteCodeDO()
            .inviteStatus(FactoryInvitationStatus.REVOKED.code())
            .revokedByAdminUserId(actor)
            .revokedAt(now(clock))
            .updatedAt(now(clock))
            .id(target));
    Invitation invitation =
        invitation(factoryInviteCodeMapper.selectFactoryInvitationById(target, false), false, true);
    factoryAudit.record(
        actor,
        "factory_invite_code",
        target,
        "factory_invitation_revoke",
        "admin:factory-invite:manage",
        trace,
        auditKey,
        null,
        null,
        reason,
        null,
        view("requestHash", hash, "result", invitation));
    return invitation;
  }
}
