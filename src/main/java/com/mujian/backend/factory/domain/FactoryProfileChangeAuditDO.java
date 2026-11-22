package com.mujian.backend.factory.domain;

import java.time.*;
import java.util.*;

/** factory_profile_change_audit明确持久化字段；由业务方法控制允许写入的列。 */
public class FactoryProfileChangeAuditDO {
  private String id;
  private String applicationNo;
  private String submitRequestId;
  private String requestHash;
  private String factoryId;
  private String applicantUserId;
  private String changePayloadJson;
  private String auditStatus;
  private String rejectReason;
  private String reviewedByAdminUserId;
  private LocalDateTime submittedAt;
  private LocalDateTime reviewedAt;
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

  /** 读取factoryId。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 设置factoryId。 */
  public void setFactoryId(String factoryId) {
    this.factoryId = factoryId;
  }

  /** 读取applicantUserId。 */
  public String getApplicantUserId() {
    return applicantUserId;
  }

  /** 设置applicantUserId。 */
  public void setApplicantUserId(String applicantUserId) {
    this.applicantUserId = applicantUserId;
  }

  /** 读取changePayloadJson。 */
  public String getChangePayloadJson() {
    return changePayloadJson;
  }

  /** 设置changePayloadJson。 */
  public void setChangePayloadJson(String changePayloadJson) {
    this.changePayloadJson = changePayloadJson;
  }

  /** 读取auditStatus。 */
  public String getAuditStatus() {
    return auditStatus;
  }

  /** 设置auditStatus。 */
  public void setAuditStatus(String auditStatus) {
    this.auditStatus = auditStatus;
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
  public FactoryProfileChangeAuditDO id(String id) {
    this.id = id;
    return this;
  }

  /** 组装applicationNo。 */
  public FactoryProfileChangeAuditDO applicationNo(String applicationNo) {
    this.applicationNo = applicationNo;
    return this;
  }

  /** 组装submitRequestId。 */
  public FactoryProfileChangeAuditDO submitRequestId(String submitRequestId) {
    this.submitRequestId = submitRequestId;
    return this;
  }

  /** 组装requestHash。 */
  public FactoryProfileChangeAuditDO requestHash(String requestHash) {
    this.requestHash = requestHash;
    return this;
  }

  /** 组装factoryId。 */
  public FactoryProfileChangeAuditDO factoryId(String factoryId) {
    this.factoryId = factoryId;
    return this;
  }

  /** 组装applicantUserId。 */
  public FactoryProfileChangeAuditDO applicantUserId(String applicantUserId) {
    this.applicantUserId = applicantUserId;
    return this;
  }

  /** 组装changePayloadJson。 */
  public FactoryProfileChangeAuditDO changePayloadJson(String changePayloadJson) {
    this.changePayloadJson = changePayloadJson;
    return this;
  }

  /** 组装auditStatus。 */
  public FactoryProfileChangeAuditDO auditStatus(String auditStatus) {
    this.auditStatus = auditStatus;
    return this;
  }

  /** 组装rejectReason。 */
  public FactoryProfileChangeAuditDO rejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
    return this;
  }

  /** 组装reviewedByAdminUserId。 */
  public FactoryProfileChangeAuditDO reviewedByAdminUserId(String reviewedByAdminUserId) {
    this.reviewedByAdminUserId = reviewedByAdminUserId;
    return this;
  }

  /** 组装submittedAt。 */
  public FactoryProfileChangeAuditDO submittedAt(LocalDateTime submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

  /** 组装reviewedAt。 */
  public FactoryProfileChangeAuditDO reviewedAt(LocalDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
    return this;
  }

  /** 组装version。 */
  public FactoryProfileChangeAuditDO version(Integer version) {
    this.version = version;
    return this;
  }

  /** 组装createdAt。 */
  public FactoryProfileChangeAuditDO createdAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /** 组装updatedAt。 */
  public FactoryProfileChangeAuditDO updatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }
}
