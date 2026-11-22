package com.mujian.backend.student.projection;

import java.time.*;
import java.util.*;

/** 审核列表只读取本页展示所需列，详情与敏感查看另走完整申请。 */
public class StudentListProjection {
  private String id;
  private String userId;
  private String verificationStatus;
  private LocalDateTime submittedAt;
  private String nickname;
  private String applicationNo;
  private String schoolName;
  private LocalDateTime expireAt;

  /** 读取id。 */
  public String getId() {
    return id;
  }

  /** 设置id。 */
  public void setId(String id) {
    this.id = id;
  }

  /** 读取userId。 */
  public String getUserId() {
    return userId;
  }

  /** 设置userId。 */
  public void setUserId(String userId) {
    this.userId = userId;
  }

  /** 读取verificationStatus。 */
  public String getVerificationStatus() {
    return verificationStatus;
  }

  /** 设置verificationStatus。 */
  public void setVerificationStatus(String verificationStatus) {
    this.verificationStatus = verificationStatus;
  }

  /** 读取submittedAt。 */
  public LocalDateTime getSubmittedAt() {
    return submittedAt;
  }

  /** 设置submittedAt。 */
  public void setSubmittedAt(LocalDateTime submittedAt) {
    this.submittedAt = submittedAt;
  }

  /** 读取nickname。 */
  public String getNickname() {
    return nickname;
  }

  /** 设置nickname。 */
  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  /** 读取applicationNo。 */
  public String getApplicationNo() {
    return applicationNo;
  }

  /** 设置applicationNo。 */
  public void setApplicationNo(String applicationNo) {
    this.applicationNo = applicationNo;
  }

  /** 读取schoolName。 */
  public String getSchoolName() {
    return schoolName;
  }

  /** 设置schoolName。 */
  public void setSchoolName(String schoolName) {
    this.schoolName = schoolName;
  }

  /** 读取expireAt。 */
  public LocalDateTime getExpireAt() {
    return expireAt;
  }

  /** 设置expireAt。 */
  public void setExpireAt(LocalDateTime expireAt) {
    this.expireAt = expireAt;
  }

  /** 组装id。 */
  public StudentListProjection id(String id) {
    this.id = id;
    return this;
  }

  /** 组装userId。 */
  public StudentListProjection userId(String userId) {
    this.userId = userId;
    return this;
  }

  /** 组装verificationStatus。 */
  public StudentListProjection verificationStatus(String verificationStatus) {
    this.verificationStatus = verificationStatus;
    return this;
  }

  /** 组装submittedAt。 */
  public StudentListProjection submittedAt(LocalDateTime submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

  /** 组装nickname。 */
  public StudentListProjection nickname(String nickname) {
    this.nickname = nickname;
    return this;
  }

  /** 组装applicationNo。 */
  public StudentListProjection applicationNo(String applicationNo) {
    this.applicationNo = applicationNo;
    return this;
  }

  /** 组装schoolName。 */
  public StudentListProjection schoolName(String schoolName) {
    this.schoolName = schoolName;
    return this;
  }

  /** 组装expireAt。 */
  public StudentListProjection expireAt(LocalDateTime expireAt) {
    this.expireAt = expireAt;
    return this;
  }
}
