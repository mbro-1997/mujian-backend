package com.mujian.backend.factory.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.domain.AdminCaseActionDO;
import com.mujian.backend.casework.dto.CloseCaseDTO;
import com.mujian.backend.casework.mapper.*;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.FactoryInvitationStatus;
import com.mujian.backend.common.enums.FactoryReasonCode;
import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.domain.FactoryChangeSnapshot;
import com.mujian.backend.factory.domain.FactorySnapshot;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.dto.FactoryDtos.ReviewResult;
import com.mujian.backend.factory.mapper.FactoryInviteCodeMapper;
import com.mujian.backend.factory.mapper.FactoryOnboardingAuditMapper;
import com.mujian.backend.factory.mapper.FactoryProfileChangeAuditMapper;
import com.mujian.backend.factory.mapper.FactoryProfileMapper;
import com.mujian.backend.factory.mapper.FactoryReviewMapper;
import com.mujian.backend.factory.projection.*;
import com.mujian.backend.factory.service.*;
import java.time.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class FactoryReviewServiceImpl implements FactoryReviewService {

  private final FactoryReviewMapper factoryReviewMapper;
  private final FactoryInviteCodeMapper factoryInviteCodeMapper;
  private final FactoryProfileMapper factoryProfileMapper;
  private final LoginMapper loginMapper;
  private final FactoryOnboardingAuditMapper factoryOnboardingAuditMapper;
  private final FactoryProfileChangeAuditMapper factoryProfileChangeAuditMapper;
  private final FactoryAccess factoryAccess;
  private final FactoryRequests factoryRequests;
  private final FactorySnapshotData factorySnapshotData;
  private final FactoryCasework factoryCasework;
  private final AdminCaseActionMapper adminCaseActionMapper;
  private final AdminCaseMapper adminCaseMapper;
  private final FactoryAudit factoryAudit;
  private final FactoryPhoneVerification factoryPhoneVerification;
  private final TransactionTemplate transactionTemplate;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactoryReviewServiceImpl(
      FactoryReviewMapper factoryReviewMapper,
      FactoryInviteCodeMapper factoryInviteCodeMapper,
      FactoryProfileMapper factoryProfileMapper,
      LoginMapper loginMapper,
      FactoryOnboardingAuditMapper factoryOnboardingAuditMapper,
      FactoryProfileChangeAuditMapper factoryProfileChangeAuditMapper,
      FactoryAccess factoryAccess,
      FactoryRequests factoryRequests,
      FactorySnapshotData factorySnapshotData,
      FactoryCasework factoryCasework,
      AdminCaseActionMapper adminCaseActionMapper,
      AdminCaseMapper adminCaseMapper,
      FactoryAudit factoryAudit,
      FactoryPhoneVerification factoryPhoneVerification,
      PlatformTransactionManager platformTransactionManager,
      ObjectMapper objectMapper,
      Clock clock) {
    this.factoryReviewMapper = factoryReviewMapper;
    this.factoryInviteCodeMapper = factoryInviteCodeMapper;
    this.factoryProfileMapper = factoryProfileMapper;
    this.loginMapper = loginMapper;
    this.factoryOnboardingAuditMapper = factoryOnboardingAuditMapper;
    this.factoryProfileChangeAuditMapper = factoryProfileChangeAuditMapper;
    this.factoryAccess = factoryAccess;
    this.factoryRequests = factoryRequests;
    this.factorySnapshotData = factorySnapshotData;
    this.factoryCasework = factoryCasework;
    this.adminCaseActionMapper = adminCaseActionMapper;
    this.adminCaseMapper = adminCaseMapper;
    this.factoryAudit = factoryAudit;
    this.factoryPhoneVerification = factoryPhoneVerification;
    transactionTemplate = new TransactionTemplate(platformTransactionManager);
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 按原请求编号恢复已提交结果并核对访问权限。 */
  public ReviewRecovery recover(String actor, String key) {
    AdminCaseActionDO adminCaseActionDO =
        com.mujian.backend.infrastructure.persistence.MappingInputs.first(
            adminCaseActionMapper.selectAdminCaseActionsByIdempotencyKey(
                "factory-review:" + actor + ":" + id(key)));
    if (adminCaseActionDO == null) throw missing();
    JsonNode jsonNode = objectMapper.readTree(adminCaseActionDO.getRequestPayloadJson());
    FactorySource factorySource = FactorySource.from(jsonNode.path("sourceType").asString());
    factoryAccess.admin(actor, factorySource.permission, "review");
    return new ReviewRecovery(
        factorySource.table,
        objectMapper.readValue(adminCaseActionDO.getResultPayloadJson(), ReviewResult.class));
  }

  /** 按幂等和固定锁序审核申请，原子保存结果及审计。 */
  public ReviewResult review(
      String actor,
      FactorySource factorySource,
      String id,
      String key,
      boolean approve,
      Command commandRaw,
      String trace) {
    factoryAccess.admin(actor, factorySource.permission, "review");
    id(id);
    id(key);
    int max = factorySource == FactorySource.CHANGE ? 255 : 500;
    FactoryReviewService.Command command =
        new Command(
            commandRaw.expectedVersion(),
            commandRaw.baseProfileVersion() == null
                ? null
                : FactoryRequests.version(commandRaw.baseProfileVersion()),
            commandRaw.sameLegalEntityConfirmed(),
            approve
                ? FactoryRequests.optional(commandRaw.remark(), max)
                : text(commandRaw.remark(), max));
    String
        hash =
            factoryRequests.hash(
                (approve ? "approve" : "reject") + factorySource.table, id, command),
        actionKey = "factory-review:" + actor + ":" + key;
    try {
      return transactionTemplate.execute(
          status -> {
            FactorySourceProjection factorySourceProjectionInitial =
                factoryReviewMapper.selectFactorySourceById(factorySource, id, false);
            if (factorySourceProjectionInitial == null) throw missing();
            String owner = factorySourceProjectionInitial.getApplicantUserId();
            factoryAccess.lock(actor, owner);
            FactorySourceProjection factorySourceProjectionProfile =
                factorySource == FactorySource.CHANGE
                    ? factoryReviewMapper.selectFactorySourceById(
                        FactorySource.PROFILE, factorySourceProjectionInitial.getFactoryId(), true)
                    : null;
            if (factorySource == FactorySource.ONBOARDING)
              factoryInviteCodeMapper.selectFactoryInvitationById(
                  factorySourceProjectionInitial.getInviteCodeId(), true);
            FactorySourceProjection factorySourceProjectionLocked =
                factoryReviewMapper.selectFactorySourceById(factorySource, id, true);
            // 跨目标/决定复用同一审核键必须拒绝；成功原键优先于终态和新版基线。
            AdminCaseActionDO adminCaseActionDOPrevious =
                com.mujian.backend.infrastructure.persistence.MappingInputs.first(
                    adminCaseActionMapper.selectAdminCaseActionsByIdempotencyKey(actionKey));
            if (adminCaseActionDOPrevious != null) {
              JsonNode jsonNodeRequest =
                  objectMapper.readTree(adminCaseActionDOPrevious.getRequestPayloadJson());
              if (!hash.equals(jsonNodeRequest.path("request_hash").asString()))
                throw conflict(CODE_IDEMPOTENCY_KEY_REUSED);
              return objectMapper.readValue(
                  adminCaseActionDOPrevious.getResultPayloadJson(), ReviewResult.class);
            }
            if (!CODE_PENDING.equals(factorySourceProjectionLocked.getAuditStatus()))
              throw conflict(FactoryReasonCode.FACTORY_ALREADY_REVIEWED.name());
            if (command.expectedVersion() != factorySourceProjectionLocked.getVersion())
              throw conflict(CODE_VERSION_CONFLICT);
            factoryAccess.context(owner, true);
            LocalDateTime now = now(clock);
            String
                factoryId =
                    factorySource == FactorySource.CHANGE
                        ? factorySourceProjectionLocked.getFactoryId()
                        : null,
                profileVersion =
                    factorySourceProjectionProfile == null
                        ? null
                        : factorySourceProjectionProfile.getProfileVersion();
            AdminCaseDO adminCaseDO =
                factoryCasework.ensure(factorySource, factorySourceProjectionLocked);
            if (factorySource == FactorySource.ONBOARDING) {
              FactoryInviteProjection factoryInviteProjection =
                  factoryInviteCodeMapper.selectFactoryInvitationById(
                      factorySourceProjectionLocked.getInviteCodeId(), false);
              if (!owner.equals(factoryInviteProjection.getLockedByUserId())
                  || !Set.of(
                          FactoryInvitationStatus.LOCKED.code(),
                          FactoryInvitationStatus.USED.code())
                      .contains(factoryInviteProjection.getInviteStatus()))
                throw new ApiFailure(
                    500,
                    CODE_DATA_INTEGRITY_ERROR,
                    FactoryReasonCode.FACTORY_DATA_INCONSISTENT.name());
              if (approve) {
                factoryAccess.entrant(owner, true);
                FactorySnapshot factorySnapshot =
                    factorySnapshotData.read(factorySourceProjectionLocked);
                factoryId = Ids.next();
                FactoryProfileDO factoryProfileDOCreated =
                    new FactoryProfileDO()
                        .id(factoryId)
                        .ownerUserId(owner)
                        .profileVersion(1L)
                        .factoryStatus(CODE_ACTIVE)
                        .approvedAuditId(id)
                        .approvedByAdminUserId(actor)
                        .approvedAt(now)
                        .createdAt(now)
                        .updatedAt(now);
                factorySnapshotData.fill(factoryProfileDOCreated, factorySnapshot);
                factoryProfileMapper.insertFactoryProfile(factoryProfileDOCreated);
                loginMapper.insertSupplierRole(Ids.next(), owner, now);
                profileVersion = "1";
              }
              loginMapper.updateRegistrationStage(
                  approve ? CODE_FACTORY_BOUND : CODE_FACTORY_REJECTED, now, owner);
              factoryInviteCodeMapper.useFactoryInvitation(
                  new FactoryInviteCodeDO()
                      .inviteStatus(FactoryInvitationStatus.USED.code())
                      .usedByUserId(owner)
                      .usedAt(now)
                      .updatedAt(now)
                      .id(factoryInviteProjection.getId()));
            } else if (approve) {
              if (factorySourceProjectionProfile == null
                  || !owner.equals(factorySourceProjectionProfile.getOwnerUserId()))
                throw new ApiFailure(
                    500,
                    CODE_DATA_INTEGRITY_ERROR,
                    FactoryReasonCode.FACTORY_DATA_INCONSISTENT.name());
              FactoryChangeSnapshot factoryChangeSnapshot =
                  factorySnapshotData.change(factorySourceProjectionLocked);
              if (!factoryChangeSnapshot
                      .base_profile_version()
                      .equals(factorySourceProjectionProfile.getProfileVersion())
                  || !factoryChangeSnapshot
                      .base_profile_version()
                      .equals(command.baseProfileVersion()))
                throw conflict(FactoryReasonCode.FACTORY_PROFILE_BASE_CHANGED.name());
              factoryPhoneVerification.unchanged(
                  factorySourceProjectionProfile.getContactPhone(),
                  factoryChangeSnapshot.proposed().contact_phone(),
                  factoryChangeSnapshot.phone_verification() == null ? null : "invalid");
              if (!factoryChangeSnapshot
                      .before()
                      .unified_social_credit_code()
                      .equals(factoryChangeSnapshot.proposed().unified_social_credit_code())
                  && (!command.sameLegalEntityConfirmed() || command.remark() == null))
                throw conflict(FactoryReasonCode.FACTORY_LEGAL_ENTITY_CONFIRMATION_REQUIRED.name());
              long next;
              try {
                next =
                    Math.addExact(
                        Long.parseLong(factorySourceProjectionProfile.getProfileVersion()), 1);
              } catch (ArithmeticException e) {
                throw conflict(FactoryReasonCode.FACTORY_PROFILE_BASE_CHANGED.name());
              }
              FactoryProfileDO factoryProfileDOUpdated =
                  new FactoryProfileDO().profileVersion(next).updatedAt(now);
              factorySnapshotData.fill(factoryProfileDOUpdated, factoryChangeSnapshot.proposed());
              factoryProfileMapper.updateFactoryProfileMaterials(
                  factoryProfileDOUpdated.id(factoryId));
              profileVersion = Long.toString(next);
            }
            if (factorySource == FactorySource.ONBOARDING) {
              factoryOnboardingAuditMapper.reviewFactoryOnboarding(
                  new FactoryOnboardingAuditDO()
                      .auditStatus(approve ? CODE_APPROVED : CODE_REJECTED)
                      .reviewedByAdminUserId(actor)
                      .reviewedAt(now)
                      .rejectReason(approve ? null : command.remark())
                      .version(factorySourceProjectionLocked.getVersion() + 1)
                      .updatedAt(now)
                      .id(id)
                      .createdFactoryId(factoryId)
                      .auditRemark(command.remark()));
            } else {
              factoryProfileChangeAuditMapper.reviewFactoryProfileChange(
                  new FactoryProfileChangeAuditDO()
                      .auditStatus(approve ? CODE_APPROVED : CODE_REJECTED)
                      .reviewedByAdminUserId(actor)
                      .reviewedAt(now)
                      .rejectReason(approve ? null : command.remark())
                      .version(factorySourceProjectionLocked.getVersion() + 1)
                      .updatedAt(now)
                      .id(id));
            }
            String action = Ids.next(),
                operation =
                    (approve ? "approve_" : "reject_")
                        + (factorySource == FactorySource.ONBOARDING
                            ? "factory_onboarding"
                            : "factory_profile_change");
            ReviewResult reviewResult =
                new ReviewResult(
                    action,
                    id,
                    approve ? CODE_APPROVED : CODE_REJECTED,
                    factorySourceProjectionLocked.getVersion() + 1,
                    factoryId,
                    profileVersion,
                    time(now));
            Map<String, Object> input =
                view(
                    "schema_version",
                    1,
                    "sourceType",
                    factorySource.table,
                    "sourceId",
                    id,
                    "decision",
                    approve ? "approve" : "reject",
                    "request_hash",
                    hash,
                    "expectedVersion",
                    command.expectedVersion(),
                    "baseProfileVersion",
                    command.baseProfileVersion(),
                    "sameLegalEntityConfirmed",
                    command.sameLegalEntityConfirmed());
            adminCaseActionMapper.insertAdminCaseAction(
                new AdminCaseActionDO()
                    .id(action)
                    .actionNo("ACT" + action)
                    .adminCaseId(adminCaseDO.getId())
                    .actionType(operation)
                    .actionStatus("succeeded")
                    .targetModule("01")
                    .targetType(factorySource.table)
                    .targetId(id)
                    .requestPayloadJson(objectMapper.writeValueAsString(input))
                    .resultPayloadJson(objectMapper.writeValueAsString(reviewResult))
                    .idempotencyKey(actionKey)
                    .operatorAdminUserId(actor)
                    .operationReason(command.remark())
                    .operatorConfirmedAt(now)
                    .executedAt(now)
                    .createdAt(now)
                    .updatedAt(now));
            if (adminCaseMapper.closeAdminCaseByVersion(
                    new CloseCaseDTO()
                        .id(adminCaseDO.getId())
                        .version(adminCaseDO.getVersion())
                        .status(reviewResult.auditStatus())
                        .summary(command.remark())
                        .checklist(
                            objectMapper.writeValueAsString(
                                view(
                                    "schemaVersion",
                                    1,
                                    "sourceType",
                                    factorySource.table,
                                    "sourceId",
                                    id,
                                    "sourceStatus",
                                    reviewResult.auditStatus(),
                                    "actionId",
                                    action)))
                        .now(now)
                        .actor(actor))
                != 1) throw conflict(CODE_VERSION_CONFLICT);
            if (factoryId != null)
              adminCaseMapper.updateAdminCaseFactory(factoryId, adminCaseDO.getId());
            factoryAudit.record(
                actor,
                factorySource.table,
                id,
                operation,
                "admin:" + factorySource.permission + ":review",
                trace,
                actionKey,
                adminCaseDO.getId(),
                action,
                command.remark(),
                view("status", CODE_PENDING, "version", factorySourceProjectionLocked.getVersion()),
                reviewResult);
            return reviewResult;
          });
    } catch (org.springframework.dao.DuplicateKeyException duplicateKeyException) {
      if (duplicateKeyException.getMessage() != null
          && duplicateKeyException.getMessage().contains("b4_credit"))
        throw conflict(FactoryReasonCode.FACTORY_CREDIT_CODE_IN_USE.name());
      throw duplicateKeyException;
    }
  }
}
