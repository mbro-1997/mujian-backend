package com.mujian.backend.factory.projection;

import java.time.*;
import java.util.*;

/** 工厂资料及审核来源查询投影；不同来源只映射各自明确SELECT字段。 */
public class FactorySourceProjection {
  private String id;
  private String applicationNo;
  private String submitRequestId;
  private String requestHash;
  private String acceptedRuleDocumentIdsJson;
  private String previousApplicationId;
  private String applicantUserId;
  private String inviteCodeId;
  private String factoryName;
  private String factoryAvatarUrl;
  private String unifiedSocialCreditCode;
  private String businessLicenseUrl;
  private String factoryPhotoUrls;
  private String contactName;
  private String contactPhone;
  private String factoryAddress;
  private String mainBusiness;
  private Integer workerCount;
  private String specialtyCategoryIdsJson;
  private String specialtyCategoryNamesSnapshotJson;
  private String caseMaterialsJson;
  private String remark;
  private String auditStatus;
  private String pendingApplicantGuard;
  private String auditRemark;
  private String rejectReason;
  private String reviewedByAdminUserId;
  private LocalDateTime submittedAt;
  private LocalDateTime reviewedAt;
  private String createdFactoryId;
  private Integer version;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private String ownerUserId;
  private String profileVersion;
  private String factoryStatus;
  private String approvedAuditId;
  private String approvedByAdminUserId;
  private LocalDateTime approvedAt;
  private String factoryId;
  private String changePayloadJson;
  private String pendingFactoryGuard;
  private String applicantNickname;

  /** 读取id。 */
  public String getId() {
    return id;
  }

  /** 设置id。 */
  public void setId(String id) {
    this.id = id;
  }

  /** 读取applicationNo。 */
  public String getApplicationNo() {
    return applicationNo;
  }

  /** 设置applicationNo。 */
  public void setApplicationNo(String applicationNo) {
    this.applicationNo = applicationNo;
  }

  /** 读取submitRequestId。 */
  public String getSubmitRequestId() {
    return submitRequestId;
  }

  /** 设置submitRequestId。 */
  public void setSubmitRequestId(String submitRequestId) {
    this.submitRequestId = submitRequestId;
  }

  /** 读取requestHash。 */
  public String getRequestHash() {
    return requestHash;
  }

  /** 设置requestHash。 */
  public void setRequestHash(String requestHash) {
    this.requestHash = requestHash;
  }

  /** 读取acceptedRuleDocumentIdsJson。 */
  public String getAcceptedRuleDocumentIdsJson() {
    return acceptedRuleDocumentIdsJson;
  }

  /** 设置acceptedRuleDocumentIdsJson。 */
  public void setAcceptedRuleDocumentIdsJson(String acceptedRuleDocumentIdsJson) {
    this.acceptedRuleDocumentIdsJson = acceptedRuleDocumentIdsJson;
  }

  /** 读取previousApplicationId。 */
  public String getPreviousApplicationId() {
    return previousApplicationId;
  }

  /** 设置previousApplicationId。 */
  public void setPreviousApplicationId(String previousApplicationId) {
    this.previousApplicationId = previousApplicationId;
  }

  /** 读取applicantUserId。 */
  public String getApplicantUserId() {
    return applicantUserId;
  }

  /** 设置applicantUserId。 */
  public void setApplicantUserId(String applicantUserId) {
    this.applicantUserId = applicantUserId;
  }

  /** 读取inviteCodeId。 */
  public String getInviteCodeId() {
    return inviteCodeId;
  }

  /** 设置inviteCodeId。 */
  public void setInviteCodeId(String inviteCodeId) {
    this.inviteCodeId = inviteCodeId;
  }

  /** 读取factoryName。 */
  public String getFactoryName() {
    return factoryName;
  }

  /** 设置factoryName。 */
  public void setFactoryName(String factoryName) {
    this.factoryName = factoryName;
  }

  /** 读取factoryAvatarUrl。 */
  public String getFactoryAvatarUrl() {
    return factoryAvatarUrl;
  }

  /** 设置factoryAvatarUrl。 */
  public void setFactoryAvatarUrl(String factoryAvatarUrl) {
    this.factoryAvatarUrl = factoryAvatarUrl;
  }

  /** 读取unifiedSocialCreditCode。 */
  public String getUnifiedSocialCreditCode() {
    return unifiedSocialCreditCode;
  }

  /** 设置unifiedSocialCreditCode。 */
  public void setUnifiedSocialCreditCode(String unifiedSocialCreditCode) {
    this.unifiedSocialCreditCode = unifiedSocialCreditCode;
  }

  /** 读取businessLicenseUrl。 */
  public String getBusinessLicenseUrl() {
    return businessLicenseUrl;
  }

  /** 设置businessLicenseUrl。 */
  public void setBusinessLicenseUrl(String businessLicenseUrl) {
    this.businessLicenseUrl = businessLicenseUrl;
  }

  /** 读取factoryPhotoUrls。 */
  public String getFactoryPhotoUrls() {
    return factoryPhotoUrls;
  }

  /** 设置factoryPhotoUrls。 */
  public void setFactoryPhotoUrls(String factoryPhotoUrls) {
    this.factoryPhotoUrls = factoryPhotoUrls;
  }

  /** 读取contactName。 */
  public String getContactName() {
    return contactName;
  }

  /** 设置contactName。 */
  public void setContactName(String contactName) {
    this.contactName = contactName;
  }

  /** 读取contactPhone。 */
  public String getContactPhone() {
    return contactPhone;
  }

  /** 设置contactPhone。 */
  public void setContactPhone(String contactPhone) {
    this.contactPhone = contactPhone;
  }

  /** 读取factoryAddress。 */
  public String getFactoryAddress() {
    return factoryAddress;
  }

  /** 设置factoryAddress。 */
  public void setFactoryAddress(String factoryAddress) {
    this.factoryAddress = factoryAddress;
  }

  /** 读取mainBusiness。 */
  public String getMainBusiness() {
    return mainBusiness;
  }

  /** 设置mainBusiness。 */
  public void setMainBusiness(String mainBusiness) {
    this.mainBusiness = mainBusiness;
  }

  /** 读取workerCount。 */
  public Integer getWorkerCount() {
    return workerCount;
  }

  /** 设置workerCount。 */
  public void setWorkerCount(Integer workerCount) {
    this.workerCount = workerCount;
  }

  /** 读取specialtyCategoryIdsJson。 */
  public String getSpecialtyCategoryIdsJson() {
    return specialtyCategoryIdsJson;
  }

  /** 设置specialtyCategoryIdsJson。 */
  public void setSpecialtyCategoryIdsJson(String specialtyCategoryIdsJson) {
    this.specialtyCategoryIdsJson = specialtyCategoryIdsJson;
  }

  /** 读取specialtyCategoryNamesSnapshotJson。 */
  public String getSpecialtyCategoryNamesSnapshotJson() {
    return specialtyCategoryNamesSnapshotJson;
  }

  /** 设置specialtyCategoryNamesSnapshotJson。 */
  public void setSpecialtyCategoryNamesSnapshotJson(String specialtyCategoryNamesSnapshotJson) {
    this.specialtyCategoryNamesSnapshotJson = specialtyCategoryNamesSnapshotJson;
  }

  /** 读取caseMaterialsJson。 */
  public String getCaseMaterialsJson() {
    return caseMaterialsJson;
  }

  /** 设置caseMaterialsJson。 */
  public void setCaseMaterialsJson(String caseMaterialsJson) {
    this.caseMaterialsJson = caseMaterialsJson;
  }

  /** 读取remark。 */
  public String getRemark() {
    return remark;
  }

  /** 设置remark。 */
  public void setRemark(String remark) {
    this.remark = remark;
  }

  /** 读取auditStatus。 */
  public String getAuditStatus() {
    return auditStatus;
  }

  /** 设置auditStatus。 */
  public void setAuditStatus(String auditStatus) {
    this.auditStatus = auditStatus;
  }

  /** 读取pendingApplicantGuard。 */
  public String getPendingApplicantGuard() {
    return pendingApplicantGuard;
  }

  /** 设置pendingApplicantGuard。 */
  public void setPendingApplicantGuard(String pendingApplicantGuard) {
    this.pendingApplicantGuard = pendingApplicantGuard;
  }

  /** 读取auditRemark。 */
  public String getAuditRemark() {
    return auditRemark;
  }

  /** 设置auditRemark。 */
  public void setAuditRemark(String auditRemark) {
    this.auditRemark = auditRemark;
  }

  /** 读取rejectReason。 */
  public String getRejectReason() {
    return rejectReason;
  }

  /** 设置rejectReason。 */
  public void setRejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
  }

  /** 读取reviewedByAdminUserId。 */
  public String getReviewedByAdminUserId() {
    return reviewedByAdminUserId;
  }

  /** 设置reviewedByAdminUserId。 */
  public void setReviewedByAdminUserId(String reviewedByAdminUserId) {
    this.reviewedByAdminUserId = reviewedByAdminUserId;
  }

  /** 读取submittedAt。 */
  public LocalDateTime getSubmittedAt() {
    return submittedAt;
  }

  /** 设置submittedAt。 */
  public void setSubmittedAt(LocalDateTime submittedAt) {
    this.submittedAt = submittedAt;
  }

  /** 读取reviewedAt。 */
  public LocalDateTime getReviewedAt() {
    return reviewedAt;
  }

  /** 设置reviewedAt。 */
  public void setReviewedAt(LocalDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
  }

  /** 读取createdFactoryId。 */
  public String getCreatedFactoryId() {
    return createdFactoryId;
  }

  /** 设置createdFactoryId。 */
  public void setCreatedFactoryId(String createdFactoryId) {
    this.createdFactoryId = createdFactoryId;
  }

  /** 读取version。 */
  public Integer getVersion() {
    return version;
  }

  /** 设置version。 */
  public void setVersion(Integer version) {
    this.version = version;
  }

  /** 读取createdAt。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 设置createdAt。 */
  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /** 读取updatedAt。 */
  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  /** 设置updatedAt。 */
  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  /** 读取ownerUserId。 */
  public String getOwnerUserId() {
    return ownerUserId;
  }

  /** 设置ownerUserId。 */
  public void setOwnerUserId(String ownerUserId) {
    this.ownerUserId = ownerUserId;
  }

  /** 读取profileVersion。 */
  public String getProfileVersion() {
    return profileVersion;
  }

  /** 设置profileVersion。 */
  public void setProfileVersion(String profileVersion) {
    this.profileVersion = profileVersion;
  }

  /** 读取factoryStatus。 */
  public String getFactoryStatus() {
    return factoryStatus;
  }

  /** 设置factoryStatus。 */
  public void setFactoryStatus(String factoryStatus) {
    this.factoryStatus = factoryStatus;
  }

  /** 读取approvedAuditId。 */
  public String getApprovedAuditId() {
    return approvedAuditId;
  }

  /** 设置approvedAuditId。 */
  public void setApprovedAuditId(String approvedAuditId) {
    this.approvedAuditId = approvedAuditId;
  }

  /** 读取approvedByAdminUserId。 */
  public String getApprovedByAdminUserId() {
    return approvedByAdminUserId;
  }

  /** 设置approvedByAdminUserId。 */
  public void setApprovedByAdminUserId(String approvedByAdminUserId) {
    this.approvedByAdminUserId = approvedByAdminUserId;
  }

  /** 读取approvedAt。 */
  public LocalDateTime getApprovedAt() {
    return approvedAt;
  }

  /** 设置approvedAt。 */
  public void setApprovedAt(LocalDateTime approvedAt) {
    this.approvedAt = approvedAt;
  }

  /** 读取factoryId。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 设置factoryId。 */
  public void setFactoryId(String factoryId) {
    this.factoryId = factoryId;
  }

  /** 读取changePayloadJson。 */
  public String getChangePayloadJson() {
    return changePayloadJson;
  }

  /** 设置changePayloadJson。 */
  public void setChangePayloadJson(String changePayloadJson) {
    this.changePayloadJson = changePayloadJson;
  }

  /** 读取pendingFactoryGuard。 */
  public String getPendingFactoryGuard() {
    return pendingFactoryGuard;
  }

  /** 设置pendingFactoryGuard。 */
  public void setPendingFactoryGuard(String pendingFactoryGuard) {
    this.pendingFactoryGuard = pendingFactoryGuard;
  }

  /** 读取applicantNickname。 */
  public String getApplicantNickname() {
    return applicantNickname;
  }

  /** 设置applicantNickname。 */
  public void setApplicantNickname(String applicantNickname) {
    this.applicantNickname = applicantNickname;
  }

  /** 组装id。 */
  public FactorySourceProjection id(String id) {
    this.id = id;
    return this;
  }

  /** 组装applicationNo。 */
  public FactorySourceProjection applicationNo(String applicationNo) {
    this.applicationNo = applicationNo;
    return this;
  }

  /** 组装submitRequestId。 */
  public FactorySourceProjection submitRequestId(String submitRequestId) {
    this.submitRequestId = submitRequestId;
    return this;
  }

  /** 组装requestHash。 */
  public FactorySourceProjection requestHash(String requestHash) {
    this.requestHash = requestHash;
    return this;
  }

  /** 组装acceptedRuleDocumentIdsJson。 */
  public FactorySourceProjection acceptedRuleDocumentIdsJson(String acceptedRuleDocumentIdsJson) {
    this.acceptedRuleDocumentIdsJson = acceptedRuleDocumentIdsJson;
    return this;
  }

  /** 组装previousApplicationId。 */
  public FactorySourceProjection previousApplicationId(String previousApplicationId) {
    this.previousApplicationId = previousApplicationId;
    return this;
  }

  /** 组装applicantUserId。 */
  public FactorySourceProjection applicantUserId(String applicantUserId) {
    this.applicantUserId = applicantUserId;
    return this;
  }

  /** 组装inviteCodeId。 */
  public FactorySourceProjection inviteCodeId(String inviteCodeId) {
    this.inviteCodeId = inviteCodeId;
    return this;
  }

  /** 组装factoryName。 */
  public FactorySourceProjection factoryName(String factoryName) {
    this.factoryName = factoryName;
    return this;
  }

  /** 组装factoryAvatarUrl。 */
  public FactorySourceProjection factoryAvatarUrl(String factoryAvatarUrl) {
    this.factoryAvatarUrl = factoryAvatarUrl;
    return this;
  }

  /** 组装unifiedSocialCreditCode。 */
  public FactorySourceProjection unifiedSocialCreditCode(String unifiedSocialCreditCode) {
    this.unifiedSocialCreditCode = unifiedSocialCreditCode;
    return this;
  }

  /** 组装businessLicenseUrl。 */
  public FactorySourceProjection businessLicenseUrl(String businessLicenseUrl) {
    this.businessLicenseUrl = businessLicenseUrl;
    return this;
  }

  /** 组装factoryPhotoUrls。 */
  public FactorySourceProjection factoryPhotoUrls(String factoryPhotoUrls) {
    this.factoryPhotoUrls = factoryPhotoUrls;
    return this;
  }

  /** 组装contactName。 */
  public FactorySourceProjection contactName(String contactName) {
    this.contactName = contactName;
    return this;
  }

  /** 组装contactPhone。 */
  public FactorySourceProjection contactPhone(String contactPhone) {
    this.contactPhone = contactPhone;
    return this;
  }

  /** 组装factoryAddress。 */
  public FactorySourceProjection factoryAddress(String factoryAddress) {
    this.factoryAddress = factoryAddress;
    return this;
  }

  /** 组装mainBusiness。 */
  public FactorySourceProjection mainBusiness(String mainBusiness) {
    this.mainBusiness = mainBusiness;
    return this;
  }

  /** 组装workerCount。 */
  public FactorySourceProjection workerCount(Integer workerCount) {
    this.workerCount = workerCount;
    return this;
  }

  /** 组装specialtyCategoryIdsJson。 */
  public FactorySourceProjection specialtyCategoryIdsJson(String specialtyCategoryIdsJson) {
    this.specialtyCategoryIdsJson = specialtyCategoryIdsJson;
    return this;
  }

  /** 组装specialtyCategoryNamesSnapshotJson。 */
  public FactorySourceProjection specialtyCategoryNamesSnapshotJson(
      String specialtyCategoryNamesSnapshotJson) {
    this.specialtyCategoryNamesSnapshotJson = specialtyCategoryNamesSnapshotJson;
    return this;
  }

  /** 组装caseMaterialsJson。 */
  public FactorySourceProjection caseMaterialsJson(String caseMaterialsJson) {
    this.caseMaterialsJson = caseMaterialsJson;
    return this;
  }

  /** 组装remark。 */
  public FactorySourceProjection remark(String remark) {
    this.remark = remark;
    return this;
  }

  /** 组装auditStatus。 */
  public FactorySourceProjection auditStatus(String auditStatus) {
    this.auditStatus = auditStatus;
    return this;
  }

  /** 组装pendingApplicantGuard。 */
  public FactorySourceProjection pendingApplicantGuard(String pendingApplicantGuard) {
    this.pendingApplicantGuard = pendingApplicantGuard;
    return this;
  }

  /** 组装auditRemark。 */
  public FactorySourceProjection auditRemark(String auditRemark) {
    this.auditRemark = auditRemark;
    return this;
  }

  /** 组装rejectReason。 */
  public FactorySourceProjection rejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
    return this;
  }

  /** 组装reviewedByAdminUserId。 */
  public FactorySourceProjection reviewedByAdminUserId(String reviewedByAdminUserId) {
    this.reviewedByAdminUserId = reviewedByAdminUserId;
    return this;
  }

  /** 组装submittedAt。 */
  public FactorySourceProjection submittedAt(LocalDateTime submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

  /** 组装reviewedAt。 */
  public FactorySourceProjection reviewedAt(LocalDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
    return this;
  }

  /** 组装createdFactoryId。 */
  public FactorySourceProjection createdFactoryId(String createdFactoryId) {
    this.createdFactoryId = createdFactoryId;
    return this;
  }

  /** 组装version。 */
  public FactorySourceProjection version(Integer version) {
    this.version = version;
    return this;
  }

  /** 组装createdAt。 */
  public FactorySourceProjection createdAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /** 组装updatedAt。 */
  public FactorySourceProjection updatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  /** 组装ownerUserId。 */
  public FactorySourceProjection ownerUserId(String ownerUserId) {
    this.ownerUserId = ownerUserId;
    return this;
  }

  /** 组装profileVersion。 */
  public FactorySourceProjection profileVersion(String profileVersion) {
    this.profileVersion = profileVersion;
    return this;
  }

  /** 组装factoryStatus。 */
  public FactorySourceProjection factoryStatus(String factoryStatus) {
    this.factoryStatus = factoryStatus;
    return this;
  }

  /** 组装approvedAuditId。 */
  public FactorySourceProjection approvedAuditId(String approvedAuditId) {
    this.approvedAuditId = approvedAuditId;
    return this;
  }

  /** 组装approvedByAdminUserId。 */
  public FactorySourceProjection approvedByAdminUserId(String approvedByAdminUserId) {
    this.approvedByAdminUserId = approvedByAdminUserId;
    return this;
  }

  /** 组装approvedAt。 */
  public FactorySourceProjection approvedAt(LocalDateTime approvedAt) {
    this.approvedAt = approvedAt;
    return this;
  }

  /** 组装factoryId。 */
  public FactorySourceProjection factoryId(String factoryId) {
    this.factoryId = factoryId;
    return this;
  }

  /** 组装changePayloadJson。 */
  public FactorySourceProjection changePayloadJson(String changePayloadJson) {
    this.changePayloadJson = changePayloadJson;
    return this;
  }

  /** 组装pendingFactoryGuard。 */
  public FactorySourceProjection pendingFactoryGuard(String pendingFactoryGuard) {
    this.pendingFactoryGuard = pendingFactoryGuard;
    return this;
  }

  /** 组装applicantNickname。 */
  public FactorySourceProjection applicantNickname(String applicantNickname) {
    this.applicantNickname = applicantNickname;
    return this;
  }
}
