package com.mujian.backend.factory.domain;

import java.time.*;
import java.util.*;

/** factory_onboarding_audit明确持久化字段；由业务方法控制允许写入的列。 */
public class FactoryOnboardingAuditDO extends FactoryFieldsDO {
  private String id;
  private String applicationNo;
  private String submitRequestId;
  private String requestHash;
  private String acceptedRuleDocumentIdsJson;
  private String previousApplicationId;
  private String applicantUserId;
  private String inviteCodeId;
  private String auditStatus;
  private String auditRemark;
  private String rejectReason;
  private String reviewedByAdminUserId;
  private LocalDateTime submittedAt;
  private LocalDateTime reviewedAt;
  private String createdFactoryId;
  private Integer version;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

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

  /** 读取auditStatus。 */
  public String getAuditStatus() {
    return auditStatus;
  }

  /** 设置auditStatus。 */
  public void setAuditStatus(String auditStatus) {
    this.auditStatus = auditStatus;
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

  /** 组装id。 */
  public FactoryOnboardingAuditDO id(String id) {
    this.id = id;
    return this;
  }

  /** 组装applicationNo。 */
  public FactoryOnboardingAuditDO applicationNo(String applicationNo) {
    this.applicationNo = applicationNo;
    return this;
  }

  /** 组装submitRequestId。 */
  public FactoryOnboardingAuditDO submitRequestId(String submitRequestId) {
    this.submitRequestId = submitRequestId;
    return this;
  }

  /** 组装requestHash。 */
  public FactoryOnboardingAuditDO requestHash(String requestHash) {
    this.requestHash = requestHash;
    return this;
  }

  /** 组装acceptedRuleDocumentIdsJson。 */
  public FactoryOnboardingAuditDO acceptedRuleDocumentIdsJson(String acceptedRuleDocumentIdsJson) {
    this.acceptedRuleDocumentIdsJson = acceptedRuleDocumentIdsJson;
    return this;
  }

  /** 组装previousApplicationId。 */
  public FactoryOnboardingAuditDO previousApplicationId(String previousApplicationId) {
    this.previousApplicationId = previousApplicationId;
    return this;
  }

  /** 组装applicantUserId。 */
  public FactoryOnboardingAuditDO applicantUserId(String applicantUserId) {
    this.applicantUserId = applicantUserId;
    return this;
  }

  /** 组装inviteCodeId。 */
  public FactoryOnboardingAuditDO inviteCodeId(String inviteCodeId) {
    this.inviteCodeId = inviteCodeId;
    return this;
  }

  /** 组装auditStatus。 */
  public FactoryOnboardingAuditDO auditStatus(String auditStatus) {
    this.auditStatus = auditStatus;
    return this;
  }

  /** 组装auditRemark。 */
  public FactoryOnboardingAuditDO auditRemark(String auditRemark) {
    this.auditRemark = auditRemark;
    return this;
  }

  /** 组装rejectReason。 */
  public FactoryOnboardingAuditDO rejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
    return this;
  }

  /** 组装reviewedByAdminUserId。 */
  public FactoryOnboardingAuditDO reviewedByAdminUserId(String reviewedByAdminUserId) {
    this.reviewedByAdminUserId = reviewedByAdminUserId;
    return this;
  }

  /** 组装submittedAt。 */
  public FactoryOnboardingAuditDO submittedAt(LocalDateTime submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

  /** 组装reviewedAt。 */
  public FactoryOnboardingAuditDO reviewedAt(LocalDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
    return this;
  }

  /** 组装createdFactoryId。 */
  public FactoryOnboardingAuditDO createdFactoryId(String createdFactoryId) {
    this.createdFactoryId = createdFactoryId;
    return this;
  }

  /** 组装version。 */
  public FactoryOnboardingAuditDO version(Integer version) {
    this.version = version;
    return this;
  }

  /** 组装createdAt。 */
  public FactoryOnboardingAuditDO createdAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /** 组装updatedAt。 */
  public FactoryOnboardingAuditDO updatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }
}
