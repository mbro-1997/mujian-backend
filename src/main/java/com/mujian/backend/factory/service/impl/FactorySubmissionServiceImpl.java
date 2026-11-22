package com.mujian.backend.factory.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.common.enums.FactoryInvitationStatus;
import com.mujian.backend.common.enums.FactoryReasonCode;
import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.domain.FactoryChangeSnapshot;
import com.mujian.backend.factory.domain.FactorySnapshot;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.dto.FactoryDtos.ChangeSubmit;
import com.mujian.backend.factory.dto.FactoryDtos.ChangeSubmitResult;
import com.mujian.backend.factory.dto.FactoryDtos.OnboardingSubmit;
import com.mujian.backend.factory.dto.FactoryDtos.OnboardingSubmitResult;
import com.mujian.backend.factory.mapper.FactoryInviteCodeMapper;
import com.mujian.backend.factory.mapper.FactoryOnboardingAuditMapper;
import com.mujian.backend.factory.mapper.FactoryProfileChangeAuditMapper;
import com.mujian.backend.factory.mapper.FactoryReviewMapper;
import com.mujian.backend.factory.projection.*;
import com.mujian.backend.factory.service.*;
import java.time.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

@Service
public class FactorySubmissionServiceImpl implements FactorySubmissionService {
  private final FactoryReviewMapper factoryReviewMapper;
  private final FactoryInviteCodeMapper factoryInviteCodeMapper;
  private final FactoryOnboardingAuditMapper factoryOnboardingAuditMapper;
  private final LoginMapper loginMapper;
  private final FactoryProfileChangeAuditMapper factoryProfileChangeAuditMapper;
  private final FactoryAccess factoryAccess;
  private final FactoryRequests factoryRequests;
  private final FactoryReadService factoryReadService;
  private final FactorySnapshotBuilder factorySnapshotBuilder;
  private final FactorySnapshotData factorySnapshotData;
  private final FactoryCatalog factoryCatalog;
  private final FactoryCasework factoryCasework;
  private final FactoryPhoneVerification factoryPhoneVerification;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactorySubmissionServiceImpl(
      FactoryReviewMapper factoryReviewMapper,
      FactoryInviteCodeMapper factoryInviteCodeMapper,
      FactoryOnboardingAuditMapper factoryOnboardingAuditMapper,
      LoginMapper loginMapper,
      FactoryProfileChangeAuditMapper factoryProfileChangeAuditMapper,
      FactoryAccess factoryAccess,
      FactoryRequests factoryRequests,
      FactoryReadService factoryReadService,
      FactorySnapshotBuilder factorySnapshotBuilder,
      FactorySnapshotData factorySnapshotData,
      FactoryCatalog factoryCatalog,
      FactoryCasework factoryCasework,
      FactoryPhoneVerification factoryPhoneVerification,
      PlatformTransactionManager platformTransactionManager,
      ObjectMapper objectMapper,
      Clock clock) {
    this.factoryReviewMapper = factoryReviewMapper;
    this.factoryInviteCodeMapper = factoryInviteCodeMapper;
    this.factoryOnboardingAuditMapper = factoryOnboardingAuditMapper;
    this.loginMapper = loginMapper;
    this.factoryProfileChangeAuditMapper = factoryProfileChangeAuditMapper;
    this.factoryAccess = factoryAccess;
    this.factoryRequests = factoryRequests;
    this.factoryReadService = factoryReadService;
    this.factorySnapshotBuilder = factorySnapshotBuilder;
    this.factorySnapshotData = factorySnapshotData;
    this.factoryCatalog = factoryCatalog;
    this.factoryCasework = factoryCasework;
    this.factoryPhoneVerification = factoryPhoneVerification;
    transactionTemplate = new TransactionTemplate(platformTransactionManager);
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 核对同一请求编号是否仍对应原请求内容。 */
  private void same(FactorySourceProjection factorySourceProjection, String hash) {
    if (!factorySourceProjection.getRequestHash().equals(hash))
      throw conflict(CODE_IDEMPOTENCY_KEY_REUSED);
  }

  /** 检查上次驳回申请与本次重提的关联。 */
  private void previous(FactorySourceProjection factorySourceProjectionCurrent, String previous) {
    if (factorySourceProjectionCurrent == null) {
      if (previous != null) throw missing();
    } else if (!CODE_REJECTED.equals(factorySourceProjectionCurrent.getAuditStatus())
        || !factorySourceProjectionCurrent.getId().equals(previous))
      throw conflict(FactoryReasonCode.FACTORY_ALREADY_REVIEWED.name());
  }

  /** 按用户锁核对幂等、资格和材料，保存入驻申请并衔接工单。 */
  public OnboardingSubmitResult onboarding(
      String owner, String key, OnboardingSubmit onboardingSubmit) {
    id(key);
    OnboardingSubmit onboardingSubmitInput = factoryRequests.onboarding(onboardingSubmit);
    String hash = factoryRequests.hash("submitFactoryOnboarding", owner, onboardingSubmitInput);
    OnboardingSubmitResult onboardingSubmitResult =
        transactionTemplate.execute(
            status -> {
              // 原请求优先于当前身份、规则、回执时效；已成功请求始终恢复原单。
              factoryAccess.lock(owner);
              factoryAccess.context(owner, true);
              FactorySourceProjection factorySourceProjectionOld =
                  factoryReviewMapper.selectFactoryAuditByRequest(
                      FactorySource.ONBOARDING, owner, key);
              if (factorySourceProjectionOld != null) {
                same(factorySourceProjectionOld, hash);
                return new OnboardingSubmitResult(
                    CODE_REPLAYED, factoryReadService.onboarding(factorySourceProjectionOld));
              }
              factoryAccess.entrant(owner, true);
              FactorySourceProjection factorySourceProjection =
                  factoryReviewMapper.selectCurrentFactoryAuditByOwner(
                      FactorySource.ONBOARDING, owner);
              if (factorySourceProjection != null
                  && CODE_PENDING.equals(factorySourceProjection.getAuditStatus()))
                return new OnboardingSubmitResult(
                    CODE_EXISTING_PENDING, factoryReadService.onboarding(factorySourceProjection));
              previous(factorySourceProjection, onboardingSubmitInput.previousApplicationId());
              FactoryInviteProjection factoryInviteProjection =
                  factorySourceProjection == null
                      ? factoryInviteCodeMapper.selectLatestFactoryInvitationByOwner(owner)
                      : factoryInviteCodeMapper.selectFactoryInvitationById(
                          factorySourceProjection.getInviteCodeId(), false);
              if (factoryInviteProjection == null)
                throw conflict(FactoryReasonCode.INVITATION_UNAVAILABLE.name());
              factoryInviteProjection =
                  factoryInviteCodeMapper.selectFactoryInvitationById(
                      factoryInviteProjection.getId(), true);
              if (!owner.equals(factoryInviteProjection.getLockedByUserId()))
                throw conflict(FactoryReasonCode.INVITATION_UNAVAILABLE.name());
              if (factorySourceProjection == null) {
                if (!FactoryInvitationStatus.LOCKED
                    .code()
                    .equals(factoryInviteProjection.getInviteStatus()))
                  throw conflict(FactoryReasonCode.INVITATION_UNAVAILABLE.name());
                if (!now(clock).isBefore(factoryInviteProjection.getExpireAt()))
                  throw conflict(FactoryReasonCode.INVITATION_EXPIRED.name());
              } else if (!FactoryInvitationStatus.USED
                  .code()
                  .equals(factoryInviteProjection.getInviteStatus()))
                throw conflict(FactoryReasonCode.INVITATION_UNAVAILABLE.name());
              FactorySnapshot factorySnapshot =
                  factorySnapshotBuilder.build(
                      owner,
                      onboardingSubmitInput.materials(),
                      factoryAccess.phone(owner),
                      factorySourceProjection == null
                          ? null
                          : factorySnapshotData.read(factorySourceProjection));
              List<String> ruleIds =
                  factoryCatalog.accept(owner, onboardingSubmitInput.acceptedRules());
              String id = Ids.next();
              LocalDateTime at = now(clock);
              FactoryOnboardingAuditDO factoryOnboardingAuditDO =
                  new FactoryOnboardingAuditDO()
                      .id(id)
                      .applicationNo("FO" + id)
                      .submitRequestId(key)
                      .requestHash(hash)
                      .acceptedRuleDocumentIdsJson(objectMapper.writeValueAsString(ruleIds))
                      .previousApplicationId(onboardingSubmitInput.previousApplicationId())
                      .applicantUserId(owner)
                      .inviteCodeId(factoryInviteProjection.getId())
                      .auditStatus(CODE_PENDING)
                      .version(0)
                      .submittedAt(at)
                      .createdAt(at)
                      .updatedAt(at);
              factorySnapshotData.fill(factoryOnboardingAuditDO, factorySnapshot);
              factoryOnboardingAuditMapper.insertFactoryOnboarding(factoryOnboardingAuditDO);
              loginMapper.markFactoryPending(now(clock), owner);
              return new OnboardingSubmitResult(
                  "submitted",
                  factoryReadService.onboarding(
                      factoryReviewMapper.selectFactorySourceById(
                          FactorySource.ONBOARDING, id, false)));
            });
    factoryCasework.reconcile(FactorySource.ONBOARDING, onboardingSubmitResult.application().id());
    return onboardingSubmitResult;
  }

  /** 核对资料基准版本后冻结变更快照，保存待审申请。 */
  public ChangeSubmitResult change(String owner, String key, ChangeSubmit changeSubmit) {
    id(key);
    ChangeSubmit changeSubmitInput = factoryRequests.change(changeSubmit);
    String hash = factoryRequests.hash("submitFactoryChange", owner, changeSubmitInput);
    ChangeSubmitResult changeSubmitResult =
        transactionTemplate.execute(
            status -> {
              factoryAccess.lock(owner);
              FactorySourceProjection factorySourceProjectionProfile =
                  factoryAccess.profile(owner, true, true);
              FactorySourceProjection factorySourceProjection =
                  factoryReviewMapper.selectFactoryAuditByRequest(
                      FactorySource.CHANGE, factorySourceProjectionProfile.getId(), key);
              if (factorySourceProjection != null) {
                same(factorySourceProjection, hash);
                return new ChangeSubmitResult(
                    CODE_REPLAYED, factoryReadService.change(factorySourceProjection));
              }
              FactorySourceProjection factorySourceProjectionCurrent =
                  factoryReviewMapper.selectCurrentFactoryAuditByOwner(
                      FactorySource.CHANGE, factorySourceProjectionProfile.getId());
              if (factorySourceProjectionCurrent != null
                  && CODE_PENDING.equals(factorySourceProjectionCurrent.getAuditStatus()))
                return new ChangeSubmitResult(
                    CODE_EXISTING_PENDING,
                    factoryReadService.change(factorySourceProjectionCurrent));
              if (changeSubmitInput.previousApplicationId() != null)
                previous(factorySourceProjectionCurrent, changeSubmitInput.previousApplicationId());
              else if (factorySourceProjectionCurrent != null
                  && CODE_REJECTED.equals(factorySourceProjectionCurrent.getAuditStatus()))
                throw conflict(FactoryReasonCode.FACTORY_ALREADY_REVIEWED.name());
              if (!factorySourceProjectionProfile
                  .getProfileVersion()
                  .equals(changeSubmitInput.baseProfileVersion()))
                throw conflict(FactoryReasonCode.FACTORY_PROFILE_BASE_CHANGED.name());
              FactorySnapshot factorySnapshotBefore =
                  factorySnapshotData.read(factorySourceProjectionProfile);
              factoryPhoneVerification.unchanged(
                  factorySnapshotBefore.contact_phone(),
                  changeSubmitInput.proposed().contactPhone(),
                  changeSubmitInput.phoneVerificationProof());
              FactorySnapshot factorySnapshotProposed =
                  factorySnapshotBuilder.build(
                      owner, changeSubmitInput.proposed(), factorySnapshotBefore);
              if (factorySnapshotData
                  .changed(factorySnapshotBefore, factorySnapshotProposed)
                  .isEmpty()) throw conflict(FactoryReasonCode.FACTORY_PROFILE_UNCHANGED.name());
              FactoryChangeSnapshot factoryChangeSnapshot =
                  new FactoryChangeSnapshot(
                      1,
                      factorySnapshotBefore,
                      factorySnapshotProposed,
                      changeSubmitInput.baseProfileVersion(),
                      changeSubmitInput.previousApplicationId(),
                      null);
              String id = Ids.next();
              LocalDateTime at = now(clock);
              factoryProfileChangeAuditMapper.insertFactoryProfileChange(
                  new FactoryProfileChangeAuditDO()
                      .id(id)
                      .applicationNo("FC" + id)
                      .submitRequestId(key)
                      .requestHash(hash)
                      .factoryId(factorySourceProjectionProfile.getId())
                      .applicantUserId(owner)
                      .changePayloadJson(objectMapper.writeValueAsString(factoryChangeSnapshot))
                      .auditStatus(CODE_PENDING)
                      .version(0)
                      .submittedAt(at)
                      .createdAt(at)
                      .updatedAt(at));
              return new ChangeSubmitResult(
                  "submitted",
                  factoryReadService.change(
                      factoryReviewMapper.selectFactorySourceById(
                          FactorySource.CHANGE, id, false)));
            });
    factoryCasework.reconcile(FactorySource.CHANGE, changeSubmitResult.application().id());
    return changeSubmitResult;
  }
}
