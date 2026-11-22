package com.mujian.backend.verification.vo;

import java.time.*;
import java.util.*;

/** 审核动作结果，与历史幂等回放字段一致。 */
public class VerificationReviewResultVO {
  private String actionId;
  private String applicationId;
  private String status;
  private int version;
  private String caseId;
  private int caseVersion;
  private String reviewedAt;
  private String reviewedByAdminUserId;

  /** 读取actionId。 */
  public String getActionId() {
    return actionId;
  }

  /** 设置actionId。 */
  public void setActionId(String actionId) {
    this.actionId = actionId;
  }

  /** 读取applicationId。 */
  public String getApplicationId() {
    return applicationId;
  }

  /** 设置applicationId。 */
  public void setApplicationId(String applicationId) {
    this.applicationId = applicationId;
  }

  /** 读取status。 */
  public String getStatus() {
    return status;
  }

  /** 设置status。 */
  public void setStatus(String status) {
    this.status = status;
  }

  /** 读取version。 */
  public int getVersion() {
    return version;
  }

  /** 设置version。 */
  public void setVersion(int version) {
    this.version = version;
  }

  /** 读取caseId。 */
  public String getCaseId() {
    return caseId;
  }

  /** 设置caseId。 */
  public void setCaseId(String caseId) {
    this.caseId = caseId;
  }

  /** 读取caseVersion。 */
  public int getCaseVersion() {
    return caseVersion;
  }

  /** 设置caseVersion。 */
  public void setCaseVersion(int caseVersion) {
    this.caseVersion = caseVersion;
  }

  /** 读取reviewedAt。 */
  public String getReviewedAt() {
    return reviewedAt;
  }

  /** 设置reviewedAt。 */
  public void setReviewedAt(String reviewedAt) {
    this.reviewedAt = reviewedAt;
  }

  /** 读取reviewedByAdminUserId。 */
  public String getReviewedByAdminUserId() {
    return reviewedByAdminUserId;
  }

  /** 设置reviewedByAdminUserId。 */
  public void setReviewedByAdminUserId(String reviewedByAdminUserId) {
    this.reviewedByAdminUserId = reviewedByAdminUserId;
  }

  /** 组装actionId。 */
  public VerificationReviewResultVO actionId(String actionId) {
    this.actionId = actionId;
    return this;
  }

  /** 组装applicationId。 */
  public VerificationReviewResultVO applicationId(String applicationId) {
    this.applicationId = applicationId;
    return this;
  }

  /** 组装status。 */
  public VerificationReviewResultVO status(String status) {
    this.status = status;
    return this;
  }

  /** 组装version。 */
  public VerificationReviewResultVO version(int version) {
    this.version = version;
    return this;
  }

  /** 组装caseId。 */
  public VerificationReviewResultVO caseId(String caseId) {
    this.caseId = caseId;
    return this;
  }

  /** 组装caseVersion。 */
  public VerificationReviewResultVO caseVersion(int caseVersion) {
    this.caseVersion = caseVersion;
    return this;
  }

  /** 组装reviewedAt。 */
  public VerificationReviewResultVO reviewedAt(String reviewedAt) {
    this.reviewedAt = reviewedAt;
    return this;
  }

  /** 组装reviewedByAdminUserId。 */
  public VerificationReviewResultVO reviewedByAdminUserId(String reviewedByAdminUserId) {
    this.reviewedByAdminUserId = reviewedByAdminUserId;
    return this;
  }
}
