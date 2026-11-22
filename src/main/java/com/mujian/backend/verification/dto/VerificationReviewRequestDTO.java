package com.mujian.backend.verification.dto;

import java.time.*;
import java.util.*;

/** 审核幂等快照，属性名与旧JSON完全一致；仅学生批准包含validYears。 */
public class VerificationReviewRequestDTO {
  private int schemaVersion;
  private String applicationId;
  private String caseId;
  private String decision;
  private int expectedVersion;
  private int expectedCaseVersion;
  private String remark;

  @com.fasterxml.jackson.annotation.JsonInclude(
      com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
  private Integer validYears;

  /** 读取schemaVersion。 */
  public int getSchemaVersion() {
    return schemaVersion;
  }

  /** 设置schemaVersion。 */
  public void setSchemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  /** 读取applicationId。 */
  public String getApplicationId() {
    return applicationId;
  }

  /** 设置applicationId。 */
  public void setApplicationId(String applicationId) {
    this.applicationId = applicationId;
  }

  /** 读取caseId。 */
  public String getCaseId() {
    return caseId;
  }

  /** 设置caseId。 */
  public void setCaseId(String caseId) {
    this.caseId = caseId;
  }

  /** 读取decision。 */
  public String getDecision() {
    return decision;
  }

  /** 设置decision。 */
  public void setDecision(String decision) {
    this.decision = decision;
  }

  /** 读取expectedVersion。 */
  public int getExpectedVersion() {
    return expectedVersion;
  }

  /** 设置expectedVersion。 */
  public void setExpectedVersion(int expectedVersion) {
    this.expectedVersion = expectedVersion;
  }

  /** 读取expectedCaseVersion。 */
  public int getExpectedCaseVersion() {
    return expectedCaseVersion;
  }

  /** 设置expectedCaseVersion。 */
  public void setExpectedCaseVersion(int expectedCaseVersion) {
    this.expectedCaseVersion = expectedCaseVersion;
  }

  /** 读取remark。 */
  public String getRemark() {
    return remark;
  }

  /** 设置remark。 */
  public void setRemark(String remark) {
    this.remark = remark;
  }

  /** 读取validYears。 */
  public Integer getValidYears() {
    return validYears;
  }

  /** 设置validYears。 */
  public void setValidYears(Integer validYears) {
    this.validYears = validYears;
  }

  /** 组装schemaVersion。 */
  public VerificationReviewRequestDTO schemaVersion(int schemaVersion) {
    this.schemaVersion = schemaVersion;
    return this;
  }

  /** 组装applicationId。 */
  public VerificationReviewRequestDTO applicationId(String applicationId) {
    this.applicationId = applicationId;
    return this;
  }

  /** 组装caseId。 */
  public VerificationReviewRequestDTO caseId(String caseId) {
    this.caseId = caseId;
    return this;
  }

  /** 组装decision。 */
  public VerificationReviewRequestDTO decision(String decision) {
    this.decision = decision;
    return this;
  }

  /** 组装expectedVersion。 */
  public VerificationReviewRequestDTO expectedVersion(int expectedVersion) {
    this.expectedVersion = expectedVersion;
    return this;
  }

  /** 组装expectedCaseVersion。 */
  public VerificationReviewRequestDTO expectedCaseVersion(int expectedCaseVersion) {
    this.expectedCaseVersion = expectedCaseVersion;
    return this;
  }

  /** 组装remark。 */
  public VerificationReviewRequestDTO remark(String remark) {
    this.remark = remark;
    return this;
  }

  /** 组装validYears。 */
  public VerificationReviewRequestDTO validYears(Integer validYears) {
    this.validYears = validYears;
    return this;
  }
}
