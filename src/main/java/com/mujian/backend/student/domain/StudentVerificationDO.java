package com.mujian.backend.student.domain;

import java.time.*;
import java.util.*;

/** user_student_verification持久化对象，不直接返回敏感原始字段。 */
public class StudentVerificationDO
    extends com.mujian.backend.verification.domain.VerificationApplicationDO {
  private String applicationNo;
  private String submitRequestId;
  private String schoolName;
  private String studentCardUrls;
  private String idCardLast4;
  private String rejectReason;
  private String reviewedByAdminUserId;
  private LocalDateTime submittedAt;
  private String materialConsentVersion;
  private LocalDateTime materialConsentAt;
  private LocalDateTime reviewedAt;
  private LocalDateTime expireAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

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

  /** 读取schoolName。 */
  public String getSchoolName() {
    return schoolName;
  }

  /** 设置schoolName。 */
  public void setSchoolName(String schoolName) {
    this.schoolName = schoolName;
  }

  /** 读取studentCardUrls。 */
  public String getStudentCardUrls() {
    return studentCardUrls;
  }

  /** 设置studentCardUrls。 */
  public void setStudentCardUrls(String studentCardUrls) {
    this.studentCardUrls = studentCardUrls;
  }

  /** 读取idCardLast4。 */
  public String getIdCardLast4() {
    return idCardLast4;
  }

  /** 设置idCardLast4。 */
  public void setIdCardLast4(String idCardLast4) {
    this.idCardLast4 = idCardLast4;
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

  /** 读取materialConsentVersion。 */
  public String getMaterialConsentVersion() {
    return materialConsentVersion;
  }

  /** 设置materialConsentVersion。 */
  public void setMaterialConsentVersion(String materialConsentVersion) {
    this.materialConsentVersion = materialConsentVersion;
  }

  /** 读取materialConsentAt。 */
  public LocalDateTime getMaterialConsentAt() {
    return materialConsentAt;
  }

  /** 设置materialConsentAt。 */
  public void setMaterialConsentAt(LocalDateTime materialConsentAt) {
    this.materialConsentAt = materialConsentAt;
  }

  /** 读取reviewedAt。 */
  public LocalDateTime getReviewedAt() {
    return reviewedAt;
  }

  /** 设置reviewedAt。 */
  public void setReviewedAt(LocalDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
  }

  /** 读取expireAt。 */
  public LocalDateTime getExpireAt() {
    return expireAt;
  }

  /** 设置expireAt。 */
  public void setExpireAt(LocalDateTime expireAt) {
    this.expireAt = expireAt;
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

  /** 组装applicationNo。 */
  public StudentVerificationDO applicationNo(String applicationNo) {
    this.applicationNo = applicationNo;
    return this;
  }

  /** 组装submitRequestId。 */
  public StudentVerificationDO submitRequestId(String submitRequestId) {
    this.submitRequestId = submitRequestId;
    return this;
  }

  /** 组装schoolName。 */
  public StudentVerificationDO schoolName(String schoolName) {
    this.schoolName = schoolName;
    return this;
  }

  /** 组装studentCardUrls。 */
  public StudentVerificationDO studentCardUrls(String studentCardUrls) {
    this.studentCardUrls = studentCardUrls;
    return this;
  }

  /** 组装idCardLast4。 */
  public StudentVerificationDO idCardLast4(String idCardLast4) {
    this.idCardLast4 = idCardLast4;
    return this;
  }

  /** 组装rejectReason。 */
  public StudentVerificationDO rejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
    return this;
  }

  /** 组装reviewedByAdminUserId。 */
  public StudentVerificationDO reviewedByAdminUserId(String reviewedByAdminUserId) {
    this.reviewedByAdminUserId = reviewedByAdminUserId;
    return this;
  }

  /** 组装submittedAt。 */
  public StudentVerificationDO submittedAt(LocalDateTime submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

  /** 组装materialConsentVersion。 */
  public StudentVerificationDO materialConsentVersion(String materialConsentVersion) {
    this.materialConsentVersion = materialConsentVersion;
    return this;
  }

  /** 组装materialConsentAt。 */
  public StudentVerificationDO materialConsentAt(LocalDateTime materialConsentAt) {
    this.materialConsentAt = materialConsentAt;
    return this;
  }

  /** 组装reviewedAt。 */
  public StudentVerificationDO reviewedAt(LocalDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
    return this;
  }

  /** 组装expireAt。 */
  public StudentVerificationDO expireAt(LocalDateTime expireAt) {
    this.expireAt = expireAt;
    return this;
  }

  /** 组装createdAt。 */
  public StudentVerificationDO createdAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /** 组装updatedAt。 */
  public StudentVerificationDO updatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }
}
