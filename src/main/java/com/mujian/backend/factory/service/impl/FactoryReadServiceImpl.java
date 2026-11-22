package com.mujian.backend.factory.service.impl;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.mapper.*;
import com.mujian.backend.common.enums.FactoryInvitationStatus;
import com.mujian.backend.common.enums.FactoryNextAction;
import com.mujian.backend.common.enums.FactoryReasonCode;
import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.domain.FactoryChangeSnapshot;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.mapper.FactoryInviteCodeMapper;
import com.mujian.backend.factory.mapper.FactoryReviewMapper;
import com.mujian.backend.factory.projection.*;
import com.mujian.backend.factory.service.*;
import com.mujian.backend.web.response.account.ContextResponse;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class FactoryReadServiceImpl implements FactoryReadService {
  private final FactoryReviewMapper factoryReviewMapper;
  private final FactoryInviteCodeMapper factoryInviteCodeMapper;
  private final FactoryAccess factoryAccess;
  private final FactorySnapshotData factorySnapshotData;
  private final FactoryCatalog factoryCatalog;
  private final FactoryInvitationService factoryInvitationService;
  private final FactoryPhoneVerification factoryPhoneVerification;
  private final AdminCaseMapper adminCaseMapper;
  private final ObjectMapper objectMapper;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactoryReadServiceImpl(
      FactoryReviewMapper factoryReviewMapper,
      FactoryInviteCodeMapper factoryInviteCodeMapper,
      FactoryAccess factoryAccess,
      FactorySnapshotData factorySnapshotData,
      FactoryCatalog factoryCatalog,
      FactoryInvitationService factoryInvitationService,
      FactoryPhoneVerification factoryPhoneVerification,
      AdminCaseMapper adminCaseMapper,
      ObjectMapper objectMapper) {
    this.factoryReviewMapper = factoryReviewMapper;
    this.factoryInviteCodeMapper = factoryInviteCodeMapper;
    this.factoryAccess = factoryAccess;
    this.factorySnapshotData = factorySnapshotData;
    this.factoryCatalog = factoryCatalog;
    this.factoryInvitationService = factoryInvitationService;
    this.factoryPhoneVerification = factoryPhoneVerification;
    this.adminCaseMapper = adminCaseMapper;
    this.objectMapper = objectMapper;
  }

  /** 读取申请冻结的规则编号，批量还原已接受规则。 */
  private List<AcceptedRule> rules(FactorySourceProjection factorySourceProjection) {
    String[] ids =
        objectMapper.readValue(
            factorySourceProjection.getAcceptedRuleDocumentIdsJson(), String[].class);
    return factoryCatalog.accepted(Arrays.asList(ids));
  }

  /** 将入驻记录组装为既有申请响应。 */
  public OnboardingApplication onboarding(FactorySourceProjection factorySourceProjection) {
    return new OnboardingApplication(
        factorySourceProjection.getId(),
        factorySourceProjection.getApplicationNo(),
        factorySourceProjection.getAuditStatus(),
        factorySourceProjection.getVersion(),
        time(factorySourceProjection.getSubmittedAt()),
        time(factorySourceProjection.getReviewedAt()),
        factorySourceProjection.getRejectReason(),
        factorySourceProjection.getPreviousApplicationId(),
        factorySnapshotData.owner(
            factorySnapshotData.read(factorySourceProjection),
            FactorySource.ONBOARDING,
            factorySourceProjection.getId(),
            "source"),
        rules(factorySourceProjection),
        factorySourceProjection.getCreatedFactoryId());
  }

  /** 读取或规范化工厂资料变更，保留冻结快照结构。 */
  public ChangeApplication change(FactorySourceProjection factorySourceProjection) {
    FactoryChangeSnapshot factoryChangeSnapshot =
        factorySnapshotData.change(factorySourceProjection);
    return new ChangeApplication(
        factorySourceProjection.getId(),
        factorySourceProjection.getApplicationNo(),
        factorySourceProjection.getAuditStatus(),
        factorySourceProjection.getVersion(),
        time(factorySourceProjection.getSubmittedAt()),
        time(factorySourceProjection.getReviewedAt()),
        factorySourceProjection.getRejectReason(),
        factoryChangeSnapshot.previous_application_id(),
        factorySourceProjection.getFactoryId(),
        factoryChangeSnapshot.base_profile_version(),
        factorySnapshotData.owner(
            factoryChangeSnapshot.before(),
            FactorySource.CHANGE,
            factorySourceProjection.getId(),
            "before"),
        factorySnapshotData.owner(
            factoryChangeSnapshot.proposed(),
            FactorySource.CHANGE,
            factorySourceProjection.getId(),
            "proposed"));
  }

  /** 核对本人归属并返回入驻申请。 */
  public OnboardingApplication ownOnboarding(String user, String id) {
    return onboarding(factoryAccess.owned(user, FactorySource.ONBOARDING, id));
  }

  /** 核对本人归属并返回资料变更申请。 */
  public ChangeApplication ownChange(String user, String id) {
    return change(factoryAccess.owned(user, FactorySource.CHANGE, id));
  }

  /** 按本人请求编号恢复入驻申请。 */
  public OnboardingApplication recoverOnboarding(String user, String key) {
    factoryAccess.context(user, false);
    FactorySourceProjection factorySourceProjection =
        factoryReviewMapper.selectFactoryAuditByRequest(FactorySource.ONBOARDING, user, id(key));
    if (factorySourceProjection == null) throw missing();
    return onboarding(factorySourceProjection);
  }

  /** 按本人请求编号恢复资料变更申请。 */
  public ChangeApplication recoverChange(String user, String key) {
    FactorySourceProjection factorySourceProjection = factoryAccess.profile(user, false, false);
    FactorySourceProjection factorySourceProjectionApplication =
        factoryReviewMapper.selectFactoryAuditByRequest(
            FactorySource.CHANGE, factorySourceProjection.getId(), id(key));
    if (factorySourceProjectionApplication == null
        || !user.equals(factorySourceProjectionApplication.getApplicantUserId())) throw missing();
    return change(factorySourceProjectionApplication);
  }

  /** 返回当前入驻状态及允许的下一步操作。 */
  public OnboardingCurrent currentOnboarding(String user) {
    ContextResponse contextResponse = factoryAccess.context(user, false);
    if (CODE_DEMANDER.equals(contextResponse.roleCode()))
      throw com.mujian.backend.common.api.ApiFailure.denied(
          FactoryReasonCode.FACTORY_ROLE_NOT_ELIGIBLE.name());
    FactorySourceProjection factorySourceProjection =
        factoryReviewMapper.selectCurrentFactoryAuditByOwner(FactorySource.ONBOARDING, user);
    FactoryInviteProjection factoryInviteProjection =
        factorySourceProjection == null
            ? factoryInviteCodeMapper.selectLatestFactoryInvitationByOwner(user)
            : factoryInviteCodeMapper.selectFactoryInvitationById(
                factorySourceProjection.getInviteCodeId(), false);
    boolean active = CODE_ACTIVE.equals(contextResponse.userStatus());
    String next =
        !active
            ? CODE_ENTER_RESTRICTED_HOME
            : factorySourceProjection != null
                ? switch (factorySourceProjection.getAuditStatus()) {
                  case CODE_PENDING -> CODE_WAIT_FACTORY_REVIEW;
                  case CODE_REJECTED -> CODE_REVISE_FACTORY_APPLICATION;
                  case CODE_APPROVED -> CODE_ENTER_FACTORY_HOME;
                  default -> FactoryNextAction.ENTER_FACTORY_INVITE.name();
                }
                : factoryInviteProjection != null
                        && FactoryInvitationStatus.LOCKED
                            .code()
                            .equals(factoryInvitationService.status(factoryInviteProjection, false))
                    ? CODE_COMPLETE_FACTORY_PROFILE
                    : FactoryNextAction.ENTER_FACTORY_INVITE.name();
    return new OnboardingCurrent(
        factorySourceProjection == null ? null : onboarding(factorySourceProjection),
        factoryInviteProjection == null ? null : factoryInviteProjection.getId(),
        factoryInviteProjection == null
            ? null
            : factoryInvitationService.status(
                factoryInviteProjection, factorySourceProjection != null),
        Set.of(CODE_REVISE_FACTORY_APPLICATION, CODE_COMPLETE_FACTORY_PROFILE).contains(next),
        next);
  }

  /** 返回当前资料变更状态及允许的下一步操作。 */
  public ChangeCurrent currentChange(String user) {
    FactorySourceProjection factorySourceProjection = factoryAccess.profile(user, false, false);
    ContextResponse contextResponse = factoryAccess.context(user, false);
    FactorySourceProjection factorySourceProjectionApplication =
        factoryReviewMapper.selectCurrentFactoryAuditByOwner(
            FactorySource.CHANGE, factorySourceProjection.getId());
    String next =
        !CODE_ACTIVE.equals(contextResponse.userStatus())
            ? CODE_ENTER_RESTRICTED_HOME
            : factorySourceProjectionApplication == null
                ? FactoryNextAction.EDIT_FACTORY_PROFILE.name()
                : switch (factorySourceProjectionApplication.getAuditStatus()) {
                  case CODE_PENDING -> FactoryNextAction.WAIT_FACTORY_PROFILE_REVIEW.name();
                  case CODE_REJECTED -> FactoryNextAction.REVISE_FACTORY_PROFILE.name();
                  default -> FactoryNextAction.VIEW_FACTORY_PROFILE.name();
                };
    return new ChangeCurrent(
        factorySourceProjectionApplication == null
            ? null
            : change(factorySourceProjectionApplication),
        Set.of(
                FactoryNextAction.EDIT_FACTORY_PROFILE.name(),
                FactoryNextAction.REVISE_FACTORY_PROFILE.name(),
                FactoryNextAction.VIEW_FACTORY_PROFILE.name())
            .contains(next),
        next);
  }

  /** 取得当前账号关联的工厂资料并核对访问条件。 */
  public FactoryProfile profile(String user) {
    FactorySourceProjection factorySourceProjection = factoryAccess.profile(user, false, false);
    return new FactoryProfile(
        factorySourceProjection.getId(),
        user,
        factorySourceProjection.getFactoryStatus(),
        factorySourceProjection.getProfileVersion(),
        CODE_ACTIVE.equals(factoryAccess.context(user, false).userStatus()),
        factoryPhoneVerification.capability(user),
        factorySnapshotData.owner(
            factorySnapshotData.read(factorySourceProjection),
            FactorySource.PROFILE,
            factorySourceProjection.getId(),
            "source"));
  }

  /** 取得业务申请关联的审核工单编号。 */
  private String caseId(FactorySource factorySource, String id) {
    List<AdminCaseDO> rows =
        adminCaseMapper.selectAdminCasesBySource(factorySource.table, id, false);
    return rows.isEmpty() ? null : rows.get(0).getId();
  }

  /** 检查管理权限后返回入驻审核详情。 */
  public AdminOnboarding adminOnboarding(String actor, String id) {
    java.util.List<String> permissions = factoryAccess.readPermissions(actor, FactorySource.ONBOARDING.permission);
    FactorySourceProjection factorySourceProjection =
        factoryReviewMapper.selectFactorySourceById(FactorySource.ONBOARDING, id(id), false);
    if (factorySourceProjection == null) throw missing();
    return new AdminOnboarding(
        factorySourceProjection.getId(),
        factorySourceProjection.getApplicationNo(),
        factorySourceProjection.getAuditStatus(),
        factorySourceProjection.getVersion(),
        time(factorySourceProjection.getSubmittedAt()),
        time(factorySourceProjection.getReviewedAt()),
        factorySourceProjection.getRejectReason(),
        factorySourceProjection.getPreviousApplicationId(),
        factorySourceProjection.getApplicantUserId(),
        factorySourceProjection.getInviteCodeId(),
        factorySnapshotData.masked(
            factorySnapshotData.read(factorySourceProjection),
            FactorySource.ONBOARDING,
            factorySourceProjection.getId(),
            "source"),
        rules(factorySourceProjection),
        factorySourceProjection.getCreatedFactoryId(),
        caseId(FactorySource.ONBOARDING, factorySourceProjection.getId()),
        CODE_PENDING.equals(factorySourceProjection.getAuditStatus())
            && permissions.contains("admin:" + FactorySource.ONBOARDING.permission + ":review"));
  }

  /** 检查管理权限后返回资料变更审核详情。 */
  public AdminChange adminChange(String actor, String id) {
    java.util.List<String> permissions = factoryAccess.readPermissions(actor, FactorySource.CHANGE.permission);
    FactorySourceProjection factorySourceProjection =
        factoryReviewMapper.selectFactorySourceById(FactorySource.CHANGE, id(id), false);
    if (factorySourceProjection == null) throw missing();
    FactoryChangeSnapshot factoryChangeSnapshot =
        factorySnapshotData.change(factorySourceProjection);
    FactorySourceProjection factorySourceProjectionCurrent =
        factoryReviewMapper.selectFactorySourceById(
            FactorySource.PROFILE, factorySourceProjection.getFactoryId(), false);
    if (factorySourceProjectionCurrent == null)
      throw new com.mujian.backend.common.api.ApiFailure(
          500, CODE_DATA_INTEGRITY_ERROR, FactoryReasonCode.FACTORY_DATA_INCONSISTENT.name());
    return new AdminChange(
        factorySourceProjection.getId(),
        factorySourceProjection.getApplicationNo(),
        factorySourceProjection.getAuditStatus(),
        factorySourceProjection.getVersion(),
        time(factorySourceProjection.getSubmittedAt()),
        time(factorySourceProjection.getReviewedAt()),
        factorySourceProjection.getRejectReason(),
        factoryChangeSnapshot.previous_application_id(),
        factorySourceProjection.getFactoryId(),
        factorySourceProjection.getApplicantUserId(),
        factoryChangeSnapshot.base_profile_version(),
        factorySourceProjectionCurrent.getProfileVersion(),
        factorySnapshotData.masked(
            factoryChangeSnapshot.before(), FactorySource.CHANGE, id, "before"),
        factorySnapshotData.masked(
            factoryChangeSnapshot.proposed(), FactorySource.CHANGE, id, "proposed"),
        factorySnapshotData.masked(
            factorySnapshotData.read(factorySourceProjectionCurrent),
            FactorySource.PROFILE,
            factorySourceProjectionCurrent.getId(),
            "source"),
        factorySnapshotData.changed(
            factoryChangeSnapshot.before(), factoryChangeSnapshot.proposed()),
        caseId(FactorySource.CHANGE, id),
        CODE_PENDING.equals(factorySourceProjection.getAuditStatus())
            && permissions.contains("admin:" + FactorySource.CHANGE.permission + ":review"));
  }

  /** 组装管理员工厂详情，保持材料权限边界。 */
  public AdminFactory adminProfile(String actor, String id) {
    factoryAccess.readPermissions(actor, "factory");
    FactorySourceProjection factorySourceProjection =
        factoryReviewMapper.selectFactorySourceById(FactorySource.PROFILE, id(id), false);
    if (factorySourceProjection == null) throw missing();
    return adminProfile(factorySourceProjection);
  }

  /** 组装管理员工厂详情，保持材料权限边界。 */
  public AdminFactory adminProfile(FactorySourceProjection factorySourceProjection) {
    return new AdminFactory(
        factorySourceProjection.getId(),
        factorySourceProjection.getOwnerUserId(),
        factorySourceProjection.getFactoryStatus(),
        factorySourceProjection.getProfileVersion(),
        factorySnapshotData.masked(
            factorySnapshotData.read(factorySourceProjection),
            FactorySource.PROFILE,
            factorySourceProjection.getId(),
            "source"),
        time(factorySourceProjection.getApprovedAt()));
  }
}
