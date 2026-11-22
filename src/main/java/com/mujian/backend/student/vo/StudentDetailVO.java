package com.mujian.backend.student.vo;

import java.time.*;
import java.util.*;

/** 明确接口字段，保持现有JSON属性与空值契约。 */
public class StudentDetailVO {
  private StudentApplicationVO application;
  private String userId;
  private String nickname;
  private String phoneMasked;
  private String caseId;
  private int caseVersion;
  private int version;
  private String reviewedByAdminUserId;
  private String auditRemark;
  private boolean canViewSensitive;
  private boolean canReview;
  private String realnameStatus;
  private String realNameMasked;
  private String idCardMasked;
  private boolean canViewRealnameSensitive;

  /** 读取application。 */
  public StudentApplicationVO getApplication() {
    return application;
  }

  /** 设置application。 */
  public void setApplication(StudentApplicationVO studentApplicationVO) {
    this.application = studentApplicationVO;
  }

  /** 读取userId。 */
  public String getUserId() {
    return userId;
  }

  /** 设置userId。 */
  public void setUserId(String userId) {
    this.userId = userId;
  }

  /** 读取nickname。 */
  public String getNickname() {
    return nickname;
  }

  /** 设置nickname。 */
  public void setNickname(String nickname) {
    this.nickname = nickname;
  }

  /** 读取phoneMasked。 */
  public String getPhoneMasked() {
    return phoneMasked;
  }

  /** 设置phoneMasked。 */
  public void setPhoneMasked(String phoneMasked) {
    this.phoneMasked = phoneMasked;
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

  /** 读取version。 */
  public int getVersion() {
    return version;
  }

  /** 设置version。 */
  public void setVersion(int version) {
    this.version = version;
  }

  /** 读取reviewedByAdminUserId。 */
  public String getReviewedByAdminUserId() {
    return reviewedByAdminUserId;
  }

  /** 设置reviewedByAdminUserId。 */
  public void setReviewedByAdminUserId(String reviewedByAdminUserId) {
    this.reviewedByAdminUserId = reviewedByAdminUserId;
  }

  /** 读取auditRemark。 */
  public String getAuditRemark() {
    return auditRemark;
  }

  /** 设置auditRemark。 */
  public void setAuditRemark(String auditRemark) {
    this.auditRemark = auditRemark;
  }

  /** 读取canViewSensitive。 */
  public boolean getCanViewSensitive() {
    return canViewSensitive;
  }

  /** 设置canViewSensitive。 */
  public void setCanViewSensitive(boolean canViewSensitive) {
    this.canViewSensitive = canViewSensitive;
  }

  /** 读取canReview。 */
  public boolean getCanReview() {
    return canReview;
  }

  /** 设置canReview。 */
  public void setCanReview(boolean canReview) {
    this.canReview = canReview;
  }

  /** 读取realnameStatus。 */
  public String getRealnameStatus() {
    return realnameStatus;
  }

  /** 设置realnameStatus。 */
  public void setRealnameStatus(String realnameStatus) {
    this.realnameStatus = realnameStatus;
  }

  /** 读取realNameMasked。 */
  public String getRealNameMasked() {
    return realNameMasked;
  }

  /** 设置realNameMasked。 */
  public void setRealNameMasked(String realNameMasked) {
    this.realNameMasked = realNameMasked;
  }

  /** 读取idCardMasked。 */
  public String getIdCardMasked() {
    return idCardMasked;
  }

  /** 设置idCardMasked。 */
  public void setIdCardMasked(String idCardMasked) {
    this.idCardMasked = idCardMasked;
  }

  /** 读取canViewRealnameSensitive。 */
  public boolean getCanViewRealnameSensitive() {
    return canViewRealnameSensitive;
  }

  /** 设置canViewRealnameSensitive。 */
  public void setCanViewRealnameSensitive(boolean canViewRealnameSensitive) {
    this.canViewRealnameSensitive = canViewRealnameSensitive;
  }

  /** 组装application。 */
  public StudentDetailVO application(StudentApplicationVO studentApplicationVO) {
    this.application = studentApplicationVO;
    return this;
  }

  /** 组装userId。 */
  public StudentDetailVO userId(String userId) {
    this.userId = userId;
    return this;
  }

  /** 组装nickname。 */
  public StudentDetailVO nickname(String nickname) {
    this.nickname = nickname;
    return this;
  }

  /** 组装phoneMasked。 */
  public StudentDetailVO phoneMasked(String phoneMasked) {
    this.phoneMasked = phoneMasked;
    return this;
  }

  /** 组装caseId。 */
  public StudentDetailVO caseId(String caseId) {
    this.caseId = caseId;
    return this;
  }

  /** 组装caseVersion。 */
  public StudentDetailVO caseVersion(int caseVersion) {
    this.caseVersion = caseVersion;
    return this;
  }

  /** 组装version。 */
  public StudentDetailVO version(int version) {
    this.version = version;
    return this;
  }

  /** 组装reviewedByAdminUserId。 */
  public StudentDetailVO reviewedByAdminUserId(String reviewedByAdminUserId) {
    this.reviewedByAdminUserId = reviewedByAdminUserId;
    return this;
  }

  /** 组装auditRemark。 */
  public StudentDetailVO auditRemark(String auditRemark) {
    this.auditRemark = auditRemark;
    return this;
  }

  /** 组装canViewSensitive。 */
  public StudentDetailVO canViewSensitive(boolean canViewSensitive) {
    this.canViewSensitive = canViewSensitive;
    return this;
  }

  /** 组装canReview。 */
  public StudentDetailVO canReview(boolean canReview) {
    this.canReview = canReview;
    return this;
  }

  /** 组装realnameStatus。 */
  public StudentDetailVO realnameStatus(String realnameStatus) {
    this.realnameStatus = realnameStatus;
    return this;
  }

  /** 组装realNameMasked。 */
  public StudentDetailVO realNameMasked(String realNameMasked) {
    this.realNameMasked = realNameMasked;
    return this;
  }

  /** 组装idCardMasked。 */
  public StudentDetailVO idCardMasked(String idCardMasked) {
    this.idCardMasked = idCardMasked;
    return this;
  }

  /** 组装canViewRealnameSensitive。 */
  public StudentDetailVO canViewRealnameSensitive(boolean canViewRealnameSensitive) {
    this.canViewRealnameSensitive = canViewRealnameSensitive;
    return this;
  }
}
