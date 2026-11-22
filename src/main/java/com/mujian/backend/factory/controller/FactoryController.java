package com.mujian.backend.factory.controller;

import static com.mujian.backend.common.support.D2Codes.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.dto.FactoryListQueryDTO;
import com.mujian.backend.factory.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import java.util.*;
import java.util.HashSet;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FactoryController extends com.mujian.backend.common.base.BaseController {
  private final FactoryCatalog factoryCatalog;
  private final FactoryInvitationService factoryInvitationService;
  private final FactoryReadService factoryReadService;
  private final FactorySubmissionService factorySubmissionService;
  private final FactoryReviewService factoryReviewService;
  private final FactoryMaterials factoryMaterials;
  private final FactoryPhoneVerification factoryPhoneVerification;
  private final FactoryListService factoryListService;
  private final FactorySensitiveService factorySensitiveService;
  private final SessionService sessionService;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactoryController(
      FactoryCatalog factoryCatalog,
      FactoryInvitationService factoryInvitationService,
      FactoryReadService factoryReadService,
      FactorySubmissionService factorySubmissionService,
      FactoryReviewService factoryReviewService,
      FactoryMaterials factoryMaterials,
      FactoryPhoneVerification factoryPhoneVerification,
      FactoryListService factoryListService,
      FactorySensitiveService factorySensitiveService,
      SessionService sessionService) {
    this.factoryCatalog = factoryCatalog;
    this.factoryInvitationService = factoryInvitationService;
    this.factoryReadService = factoryReadService;
    this.factorySubmissionService = factorySubmissionService;
    this.factoryReviewService = factoryReviewService;
    this.factoryMaterials = factoryMaterials;
    this.factoryPhoneVerification = factoryPhoneVerification;
    this.factoryListService = factoryListService;
    this.factorySensitiveService = factorySensitiveService;
    this.sessionService = sessionService;
  }

  /** 从当前会话取得操作者，拒绝未授权访问。 */
  private String actor(HttpServletRequest httpServletRequest) {
    return sessionService.user(sessionService.from(httpServletRequest));
  }

  /** 取得本次请求的审计追踪编号。 */
  private String trace(HttpServletRequest httpServletRequest) {
    return (String) httpServletRequest.getAttribute("traceId");
  }

  /** 返回当前资料可选的字典项并保留已有选择。 */
  @GetMapping("/api/me/factory-options")
  public ApiResponse<Options> options(HttpServletRequest httpServletRequest) {
    return success(httpServletRequest, factoryCatalog.options(actor(httpServletRequest)));
  }

  /** 返回当前生效的业务规则版本。 */
  @GetMapping("/api/me/factory-rules")
  public ApiResponse<FactoryRules> rules(HttpServletRequest httpServletRequest) {
    return success(httpServletRequest, factoryCatalog.rules(actor(httpServletRequest)));
  }

  /** 按文档编号返回当前有效规则内容。 */
  @GetMapping("/api/me/factory-rules/{id}")
  public ApiResponse<FactoryRuleDocument> rule(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(httpServletRequest, factoryCatalog.document(actor(httpServletRequest), id));
  }

  /** 按固定锁序认领邀请码并更新账号准入阶段。 */
  @PostMapping("/api/me/factory-invitations/claim")
  public ApiResponse<ClaimResult> claim(
      HttpServletRequest httpServletRequest, @RequestBody ClaimRequest claimRequest) {
    return success(
        httpServletRequest,
        factoryInvitationService.claim(actor(httpServletRequest), claimRequest));
  }

  /** 返回当前入驻状态及允许的下一步操作。 */
  @GetMapping("/api/me/factory-onboarding/current")
  public ApiResponse<OnboardingCurrent> currentOnboarding(HttpServletRequest httpServletRequest) {
    return success(
        httpServletRequest, factoryReadService.currentOnboarding(actor(httpServletRequest)));
  }

  /** 按本人请求编号恢复入驻申请。 */
  @GetMapping("/api/me/factory-onboarding/by-request/{key}")
  public ApiResponse<OnboardingApplication> recoverOnboarding(
      HttpServletRequest httpServletRequest, @PathVariable String key) {
    return success(
        httpServletRequest, factoryReadService.recoverOnboarding(actor(httpServletRequest), key));
  }

  /** 核对本人归属并返回入驻申请。 */
  @GetMapping("/api/me/factory-onboarding/{id}")
  public ApiResponse<OnboardingApplication> ownOnboarding(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(
        httpServletRequest, factoryReadService.ownOnboarding(actor(httpServletRequest), id));
  }

  /** 提交本人入驻材料并返回申请状态。 */
  @PostMapping("/api/me/factory-onboarding")
  public ApiResponse<OnboardingSubmitResult> submitOnboarding(
      HttpServletRequest httpServletRequest,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody OnboardingSubmit onboardingSubmit) {
    return success(
        httpServletRequest,
        factorySubmissionService.onboarding(actor(httpServletRequest), key, onboardingSubmit));
  }

  /** 返回当前资料变更状态及允许的下一步操作。 */
  @GetMapping("/api/me/factory-profile-changes/current")
  public ApiResponse<ChangeCurrent> currentChange(HttpServletRequest httpServletRequest) {
    return success(httpServletRequest, factoryReadService.currentChange(actor(httpServletRequest)));
  }

  /** 按本人请求编号恢复资料变更申请。 */
  @GetMapping("/api/me/factory-profile-changes/by-request/{key}")
  public ApiResponse<ChangeApplication> recoverChange(
      HttpServletRequest httpServletRequest, @PathVariable String key) {
    return success(
        httpServletRequest, factoryReadService.recoverChange(actor(httpServletRequest), key));
  }

  /** 核对本人归属并返回资料变更申请。 */
  @GetMapping("/api/me/factory-profile-changes/{id}")
  public ApiResponse<ChangeApplication> ownChange(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(httpServletRequest, factoryReadService.ownChange(actor(httpServletRequest), id));
  }

  /** 提交本人资料变更并保留审批前正式资料。 */
  @PostMapping("/api/me/factory-profile-changes")
  public ApiResponse<ChangeSubmitResult> submitChange(
      HttpServletRequest httpServletRequest,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody ChangeSubmit changeSubmit) {
    return success(
        httpServletRequest,
        factorySubmissionService.change(actor(httpServletRequest), key, changeSubmit));
  }

  /** 取得当前账号关联的工厂资料并核对访问条件。 */
  @GetMapping("/api/me/factory-profile")
  public ApiResponse<FactoryProfile> profile(HttpServletRequest httpServletRequest) {
    return success(httpServletRequest, factoryReadService.profile(actor(httpServletRequest)));
  }

  /** 核对本人材料归属后签发短时链接。 */
  @PostMapping("/api/me/factory-materials/view")
  public ApiResponse<MaterialView> materialFalse(
      HttpServletRequest httpServletRequest, @RequestBody MaterialLocator materialLocator) {
    return success(
        httpServletRequest,
        factoryMaterials.view(
            actor(httpServletRequest), materialLocator, false, trace(httpServletRequest)));
  }

  /** 核对管理端敏感权限并审计材料查看。 */
  @PostMapping("/api/admin/factory-materials/view")
  public ApiResponse<MaterialView> materialTrue(
      HttpServletRequest httpServletRequest, @RequestBody MaterialLocator materialLocator) {
    return success(
        httpServletRequest,
        factoryMaterials.view(
            actor(httpServletRequest), materialLocator, true, trace(httpServletRequest)));
  }

  /** 返回更换联系电话的能力状态。 */
  @GetMapping("/api/me/factory-phone/capability")
  public ApiResponse<PhoneCapability> phoneCapability(HttpServletRequest httpServletRequest) {
    return success(
        httpServletRequest, factoryPhoneVerification.capability(actor(httpServletRequest)));
  }

  /** 调用既定短信能力门禁，未配置时拒绝发送。 */
  @PostMapping("/api/me/factory-phone/sms")
  public ApiResponse<PhoneSendResult> sms(
      HttpServletRequest httpServletRequest,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody PhoneSend phoneSend) {
    factoryPhoneVerification.command(actor(httpServletRequest));
    throw new IllegalStateException();
  }

  /** 调用既定验证码能力门禁，未配置时拒绝验证。 */
  @PostMapping("/api/me/factory-phone/verify")
  public ApiResponse<PhoneProof> verify(
      HttpServletRequest httpServletRequest,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody PhoneVerify phoneVerify) {
    factoryPhoneVerification.command(actor(httpServletRequest));
    throw new IllegalStateException();
  }

  /** 按筛选条件分页返回邀请码列表。 */
  @GetMapping("/api/admin/factory-invitations")
  public ApiResponse<InvitationPage> invites(
      HttpServletRequest httpServletRequest,
      @ModelAttribute FactoryListQueryDTO factoryListQueryDTO) {
    return success(
        httpServletRequest,
        factoryListService.invites(actor(httpServletRequest), factoryListQueryDTO));
  }

  /** 按管理员权限创建邀请码并返回短期明文。 */
  @PostMapping("/api/admin/factory-invitations")
  public ApiResponse<InvitationResult> createInvite(
      HttpServletRequest httpServletRequest,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody InvitationCreate invitationCreate) {
    return success(
        httpServletRequest,
        factoryInvitationService.create(
            actor(httpServletRequest), key, invitationCreate, trace(httpServletRequest)));
  }

  /** 按管理员请求编号恢复邀请码结果。 */
  @GetMapping("/api/admin/factory-invitations/by-request/{key}")
  public ApiResponse<InvitationResult> recoverInvite(
      HttpServletRequest httpServletRequest, @PathVariable String key) {
    return success(
        httpServletRequest, factoryInvitationService.recover(actor(httpServletRequest), key));
  }

  /** 核对邀请码使用情况后作废并记录审计。 */
  @PostMapping("/api/admin/factory-invitations/{id}/revoke")
  public ApiResponse<Invitation> revoke(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody RevokeRequest revokeRequest) {
    return success(
        httpServletRequest,
        factoryInvitationService.revoke(
            actor(httpServletRequest), id, key, revokeRequest, trace(httpServletRequest)));
  }

  /** 按筛选条件分页查询入驻审核队列。 */
  @GetMapping("/api/admin/factory-onboarding")
  public ApiResponse<QueuePage> listOnboarding(
      HttpServletRequest httpServletRequest,
      @ModelAttribute FactoryListQueryDTO factoryListQueryDTO) {
    return success(
        httpServletRequest,
        factoryListService.queue(
            actor(httpServletRequest), FactorySource.ONBOARDING, factoryListQueryDTO));
  }

  /** 检查管理权限后返回入驻审核详情。 */
  @GetMapping("/api/admin/factory-onboarding/{id}")
  public ApiResponse<AdminOnboarding> adminOnboarding(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(
        httpServletRequest, factoryReadService.adminOnboarding(actor(httpServletRequest), id));
  }

  /** 核对敏感权限并审计入驻材料查看。 */
  @PostMapping("/api/admin/factory-onboarding/{id}/sensitive-view")
  public ApiResponse<SensitiveResult> sensitiveOnboarding(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(
        httpServletRequest,
        factorySensitiveService.view(
            actor(httpServletRequest), FactorySource.ONBOARDING, id, trace(httpServletRequest)));
  }

  /** 按申请和工单版本批准工厂入驻。 */
  @PostMapping("/api/admin/factory-onboarding/{id}/approve")
  public ApiResponse<ReviewResult> approveOnboarding(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Approve approve) {
    return success(
        httpServletRequest,
        factoryReviewService.review(
            actor(httpServletRequest),
            FactorySource.ONBOARDING,
            id,
            key,
            true,
            new FactoryReviewService.Command(
                approve.expectedVersion(), null, false, approve.remark()),
            trace(httpServletRequest)));
  }

  /** 按申请和工单版本驳回工厂入驻。 */
  @PostMapping("/api/admin/factory-onboarding/{id}/reject")
  public ApiResponse<ReviewResult> rejectOnboarding(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody Reject reject) {
    return success(
        httpServletRequest,
        factoryReviewService.review(
            actor(httpServletRequest),
            FactorySource.ONBOARDING,
            id,
            key,
            false,
            new FactoryReviewService.Command(
                reject.expectedVersion(), null, false, reject.reason()),
            trace(httpServletRequest)));
  }

  /** 按筛选条件分页查询资料变更审核队列。 */
  @GetMapping("/api/admin/factory-profile-changes")
  public ApiResponse<QueuePage> listChange(
      HttpServletRequest httpServletRequest,
      @ModelAttribute FactoryListQueryDTO factoryListQueryDTO) {
    return success(
        httpServletRequest,
        factoryListService.queue(
            actor(httpServletRequest), FactorySource.CHANGE, factoryListQueryDTO));
  }

  /** 检查管理权限后返回资料变更审核详情。 */
  @GetMapping("/api/admin/factory-profile-changes/{id}")
  public ApiResponse<AdminChange> adminChange(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(
        httpServletRequest, factoryReadService.adminChange(actor(httpServletRequest), id));
  }

  /** 核对敏感权限并审计变更材料查看。 */
  @PostMapping("/api/admin/factory-profile-changes/{id}/sensitive-view")
  public ApiResponse<ChangeSensitiveResult> sensitiveChange(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(
        httpServletRequest,
        factorySensitiveService.change(actor(httpServletRequest), id, trace(httpServletRequest)));
  }

  /** 按基准版本批准资料变更并更新正式资料。 */
  @PostMapping("/api/admin/factory-profile-changes/{id}/approve")
  public ApiResponse<ReviewResult> approveChange(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody ChangeApprove changeApprove) {
    return success(
        httpServletRequest,
        factoryReviewService.review(
            actor(httpServletRequest),
            FactorySource.CHANGE,
            id,
            key,
            true,
            new FactoryReviewService.Command(
                changeApprove.expectedVersion(),
                changeApprove.baseProfileVersion(),
                changeApprove.sameLegalEntityConfirmed(),
                changeApprove.remark()),
            trace(httpServletRequest)));
  }

  /** 驳回资料变更并保留原正式资料。 */
  @PostMapping("/api/admin/factory-profile-changes/{id}/reject")
  public ApiResponse<ReviewResult> rejectChange(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody ChangeReject changeReject) {
    return success(
        httpServletRequest,
        factoryReviewService.review(
            actor(httpServletRequest),
            FactorySource.CHANGE,
            id,
            key,
            false,
            new FactoryReviewService.Command(
                changeReject.expectedVersion(), null, false, changeReject.reason()),
            trace(httpServletRequest)));
  }

  /** 按权限和筛选条件分页返回工厂目录。 */
  @GetMapping("/api/admin/factories")
  public ApiResponse<FactoryPage> factories(
      HttpServletRequest httpServletRequest,
      @ModelAttribute FactoryListQueryDTO factoryListQueryDTO) {
    return success(
        httpServletRequest,
        factoryListService.factories(actor(httpServletRequest), factoryListQueryDTO));
  }

  /** 核对管理权限后读取指定工厂详情。 */
  @GetMapping("/api/admin/factories/{id}")
  public ApiResponse<AdminFactory> factory(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(
        httpServletRequest, factoryReadService.adminProfile(actor(httpServletRequest), id));
  }

  /** 核对敏感权限并审计正式工厂材料查看。 */
  @PostMapping("/api/admin/factories/{id}/sensitive-view")
  public ApiResponse<SensitiveResult> sensitiveFactory(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(
        httpServletRequest,
        factorySensitiveService.view(
            actor(httpServletRequest), FactorySource.PROFILE, id, trace(httpServletRequest)));
  }

  /** 按请求编号恢复审核决定，避免重复处理。 */
  @GetMapping("/api/admin/factory-review-actions/by-request/{key}")
  public ApiResponse<ReviewRecovery> reviewRecovery(
      HttpServletRequest httpServletRequest, @PathVariable String key) {
    return success(
        httpServletRequest, factoryReviewService.recover(actor(httpServletRequest), key));
  }

  /** 校验用途、身份和文件后复用私有材料上传。 */
  @PostMapping(value = "/api/me/factory-materials", consumes = "multipart/form-data")
  public ApiResponse<UploadResult> upload(
      HttpServletRequest httpServletRequest,
      @RequestParam("file") MultipartFile multipartFile,
      @RequestParam String purpose)
      throws Exception {
    if (httpServletRequest.getQueryString() != null) throw ApiFailure.invalid();
    HashSet<String> names = new HashSet<String>();
    for (Part part : httpServletRequest.getParts())
      if (!names.add(part.getName())) throw ApiFailure.invalid();
    if (!names.equals(Set.of("file", "purpose"))) throw ApiFailure.invalid();
    if (multipartFile.getSize() > 5242880)
      throw new ApiFailure(400, CODE_INVALID_ARGUMENT, CODE_MATERIAL_TOO_LARGE);
    return success(
        httpServletRequest,
        factoryMaterials.upload(actor(httpServletRequest), purpose, multipartFile.getBytes()));
  }
}
