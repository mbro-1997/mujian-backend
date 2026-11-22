package com.mujian.backend.factory.dto;

import java.util.List;

/** B接口白名单DTO；不作为持久化实体使用。 */
public final class FactoryDtos {
  /** 装配本业务所需依赖，复用现有公共能力。 */
  private FactoryDtos() {}

  public record Context(
      String userId,
      String nickname,
      String userStatus,
      String roleCode,
      String registrationStage,
      String accessMode,
      String nextAction) {}

  public record MaterialLocator(
      String sourceType,
      String sourceId,
      String section,
      String field,
      Integer imageIndex,
      Integer caseIndex) {}

  public record MaterialInput(String kind, String uploadId, MaterialLocator locator) {}

  public record CaseInput(String title, String description, List<MaterialInput> images) {}

  public record CaseRead(String title, String description, List<MaterialLocator> images) {}

  public record OnboardingFields(
      String factoryName,
      String unifiedSocialCreditCode,
      MaterialInput factoryAvatar,
      MaterialInput businessLicense,
      List<MaterialInput> factoryPhotos,
      String contactName,
      String factoryAddress,
      String mainBusiness,
      Integer workerCount,
      List<String> specialtyCategoryIds,
      List<CaseInput> cases,
      String remark) {}

  public record ChangeFields(
      String factoryName,
      String unifiedSocialCreditCode,
      MaterialInput factoryAvatar,
      MaterialInput businessLicense,
      List<MaterialInput> factoryPhotos,
      String contactName,
      String factoryAddress,
      String mainBusiness,
      Integer workerCount,
      List<String> specialtyCategoryIds,
      List<CaseInput> cases,
      String remark,
      String contactPhone) {}

  public record Option(String id, String code, String label, Boolean enabled, Boolean retained) {}

  public record Options(String optionGroup, List<Option> items) {}

  public record AcceptedRule(String documentId, String version, String contentHash) {}

  public record FactoryRule(
      String documentId,
      String code,
      String title,
      String version,
      String contentHash,
      String effectiveAt) {}

  public record FactoryRuleDocument(
      String documentId,
      String code,
      String title,
      String version,
      String contentHash,
      String effectiveAt,
      String contentType,
      String contentText,
      String status) {}

  public record FactoryRules(String scene, List<FactoryRule> rules) {}

  public record OnboardingSubmit(
      String previousApplicationId, List<AcceptedRule> acceptedRules, OnboardingFields materials) {}

  public record ChangeSubmit(
      String previousApplicationId,
      String baseProfileVersion,
      String phoneVerificationProof,
      ChangeFields proposed) {}

  public record OwnerFields(
      String factoryName,
      String unifiedSocialCreditCode,
      MaterialLocator factoryAvatar,
      MaterialLocator businessLicense,
      List<MaterialLocator> factoryPhotos,
      String contactName,
      String factoryAddress,
      String mainBusiness,
      Integer workerCount,
      List<String> specialtyCategoryIds,
      List<CaseRead> cases,
      String remark,
      List<String> specialtyCategoryNames,
      String contactPhone) {}

  public record MaskedFields(
      String factoryName,
      String unifiedSocialCreditCode,
      MaterialLocator factoryAvatar,
      MaterialLocator businessLicense,
      List<MaterialLocator> factoryPhotos,
      String factoryAddress,
      String mainBusiness,
      Integer workerCount,
      List<String> specialtyCategoryIds,
      List<CaseRead> cases,
      String remark,
      List<String> specialtyCategoryNames,
      String contactNameMasked,
      String contactPhoneMasked) {}

  public record OnboardingApplication(
      String id,
      String applicationNo,
      String auditStatus,
      Integer version,
      String submittedAt,
      String reviewedAt,
      String rejectReason,
      String previousApplicationId,
      OwnerFields materials,
      List<AcceptedRule> acceptedRules,
      String createdFactoryId) {}

  public record ChangeApplication(
      String id,
      String applicationNo,
      String auditStatus,
      Integer version,
      String submittedAt,
      String reviewedAt,
      String rejectReason,
      String previousApplicationId,
      String factoryId,
      String baseProfileVersion,
      OwnerFields before,
      OwnerFields proposed) {}

  public record OnboardingCurrent(
      OnboardingApplication application,
      String invitationId,
      String invitationStatus,
      Boolean canSubmit,
      String nextAction) {}

  public record ChangeCurrent(
      ChangeApplication application, Boolean canSubmit, String nextAction) {}

  public record OnboardingSubmitResult(String outcome, OnboardingApplication application) {}

  public record ChangeSubmitResult(String outcome, ChangeApplication application) {}

  public record PhoneCapability(Boolean available, String reasonCode, String captchaAppId) {}

  public record FactoryProfile(
      String id,
      String ownerUserId,
      String factoryStatus,
      String profileVersion,
      Boolean canEdit,
      PhoneCapability phoneChange,
      OwnerFields materials) {}

  public record ClaimRequest(String inviteCode) {}

  public record ClaimResult(String invitationId, String expireAt, Context context) {}

  public record InvitationCreate(String expireAt, String remark) {}

  public record Invitation(
      String id,
      String suffix,
      String inviteStatus,
      String expireAt,
      String lockedByUserId,
      Boolean hasSubmittedApplication,
      Boolean canRevoke,
      String createdAt,
      String remark) {}

  public record InvitationResult(
      Invitation invitation, String codeAvailability, String inviteCode, String recoverUntil) {}

  public record RevokeRequest(String reason) {}

  public record UploadRequest(String file, String purpose) {}

  public record UploadResult(String uploadId, String expiresAt) {}

  public record MaterialView(String url, String expiresAt) {}

  public record PhoneSend(String phone, String ticket, String randstr) {}

  public record PhoneSendResult(
      String challengeId, String deliveryStatus, String expiresAt, Integer retryAfterSeconds) {}

  public record PhoneVerify(String challengeId, String code) {}

  public record PhoneProof(String proof, String phone, String expiresAt) {}

  public record Approve(Integer expectedVersion, String remark) {}

  public record ChangeApprove(
      Integer expectedVersion,
      String baseProfileVersion,
      Boolean sameLegalEntityConfirmed,
      String remark) {}

  public record Reject(Integer expectedVersion, String reason) {}

  public record ChangeReject(Integer expectedVersion, String reason) {}

  public record ReviewResult(
      String actionId,
      String applicationId,
      String auditStatus,
      Integer version,
      String factoryId,
      String profileVersion,
      String reviewedAt) {}

  public record ReviewRecovery(String sourceType, ReviewResult result) {}

  public record AdminOnboarding(
      String id,
      String applicationNo,
      String auditStatus,
      Integer version,
      String submittedAt,
      String reviewedAt,
      String rejectReason,
      String previousApplicationId,
      String applicantUserId,
      String inviteCodeId,
      MaskedFields materials,
      List<AcceptedRule> acceptedRules,
      String createdFactoryId,
      String caseId,
      Boolean canReview) {}

  public record AdminChange(
      String id,
      String applicationNo,
      String auditStatus,
      Integer version,
      String submittedAt,
      String reviewedAt,
      String rejectReason,
      String previousApplicationId,
      String factoryId,
      String applicantUserId,
      String baseProfileVersion,
      String currentProfileVersion,
      MaskedFields before,
      MaskedFields proposed,
      MaskedFields current,
      List<String> changedFields,
      String caseId,
      Boolean canReview) {}

  public record AdminFactory(
      String id,
      String ownerUserId,
      String factoryStatus,
      String profileVersion,
      MaskedFields materials,
      String approvedAt) {}

  public record SensitiveResult(String contactName, String contactPhone, String auditId) {}

  public record ChangeSensitiveResult(
      SensitiveResult before, SensitiveResult proposed, SensitiveResult current) {}

  public record QueueItem(
      String id,
      String applicationNo,
      String factoryId,
      String factoryName,
      String applicantUserId,
      String applicantNickname,
      String creditCodeMasked,
      String submittedAt,
      String auditStatus,
      List<String> changedFields) {}

  public record InvitationPage(
      List<Invitation> items, Integer page, Integer pageSize, String total) {}

  public record QueuePage(List<QueueItem> items, Integer page, Integer pageSize, String total) {}

  public record FactoryPage(
      List<AdminFactory> items, Integer page, Integer pageSize, String total) {}
}
