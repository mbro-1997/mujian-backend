package com.mujian.backend.realname.vo;

import java.time.*;
import java.util.*;

/** 明确接口字段，保持现有JSON属性与空值契约。 */
public class RealnameDetailVO {
  private RealnameApplicationVO application;
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

  /** 读取application。 */
  public RealnameApplicationVO getApplication() {
    return application;
  }

  /** 设置application。 */
  public void setApplication(RealnameApplicationVO realnameApplicationVO) {
    this.application = realnameApplicationVO;
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

  /** 组装application。 */
  public RealnameDetailVO application(RealnameApplicationVO realnameApplicationVO) {
    this.application = realnameApplicationVO;
    return this;
  }

  /** 组装userId。 */
  public RealnameDetailVO userId(String userId) {
    this.userId = userId;
    return this;
  }

  /** 组装nickname。 */
  public RealnameDetailVO nickname(String nickname) {
    this.nickname = nickname;
    return this;
  }

  /** 组装phoneMasked。 */
  public RealnameDetailVO phoneMasked(String phoneMasked) {
    this.phoneMasked = phoneMasked;
    return this;
  }

  /** 组装caseId。 */
  public RealnameDetailVO caseId(String caseId) {
    this.caseId = caseId;
    return this;
  }

  /** 组装caseVersion。 */
  public RealnameDetailVO caseVersion(int caseVersion) {
    this.caseVersion = caseVersion;
    return this;
  }

  /** 组装version。 */
  public RealnameDetailVO version(int version) {
    this.version = version;
    return this;
  }

  /** 组装reviewedByAdminUserId。 */
  public RealnameDetailVO reviewedByAdminUserId(String reviewedByAdminUserId) {
    this.reviewedByAdminUserId = reviewedByAdminUserId;
    return this;
  }

  /** 组装auditRemark。 */
  public RealnameDetailVO auditRemark(String auditRemark) {
    this.auditRemark = auditRemark;
    return this;
  }

  /** 组装canViewSensitive。 */
  public RealnameDetailVO canViewSensitive(boolean canViewSensitive) {
    this.canViewSensitive = canViewSensitive;
    return this;
  }

  /** 组装canReview。 */
  public RealnameDetailVO canReview(boolean canReview) {
    this.canReview = canReview;
    return this;
  }
}
