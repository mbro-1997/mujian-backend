package com.mujian.backend.realname.vo;

import java.time.*;
import java.util.*;

/** 明确接口字段，保持现有JSON属性与空值契约。 */
public class RealnameApplicationVO {
  private String id;
  private String applicationNo;
  private String status;
  private String realNameMasked;
  private String idCardMasked;
  private String submittedAt;
  private String reviewedAt;
  private String verifiedAt;
  private String rejectReason;
  private String nextAction;

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

  /** 读取status。 */
  public String getStatus() {
    return status;
  }

  /** 设置status。 */
  public void setStatus(String status) {
    this.status = status;
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

  /** 读取submittedAt。 */
  public String getSubmittedAt() {
    return submittedAt;
  }

  /** 设置submittedAt。 */
  public void setSubmittedAt(String submittedAt) {
    this.submittedAt = submittedAt;
  }

  /** 读取reviewedAt。 */
  public String getReviewedAt() {
    return reviewedAt;
  }

  /** 设置reviewedAt。 */
  public void setReviewedAt(String reviewedAt) {
    this.reviewedAt = reviewedAt;
  }

  /** 读取verifiedAt。 */
  public String getVerifiedAt() {
    return verifiedAt;
  }

  /** 设置verifiedAt。 */
  public void setVerifiedAt(String verifiedAt) {
    this.verifiedAt = verifiedAt;
  }

  /** 读取rejectReason。 */
  public String getRejectReason() {
    return rejectReason;
  }

  /** 设置rejectReason。 */
  public void setRejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
  }

  /** 读取nextAction。 */
  public String getNextAction() {
    return nextAction;
  }

  /** 设置nextAction。 */
  public void setNextAction(String nextAction) {
    this.nextAction = nextAction;
  }

  /** 组装id。 */
  public RealnameApplicationVO id(String id) {
    this.id = id;
    return this;
  }

  /** 组装applicationNo。 */
  public RealnameApplicationVO applicationNo(String applicationNo) {
    this.applicationNo = applicationNo;
    return this;
  }

  /** 组装status。 */
  public RealnameApplicationVO status(String status) {
    this.status = status;
    return this;
  }

  /** 组装realNameMasked。 */
  public RealnameApplicationVO realNameMasked(String realNameMasked) {
    this.realNameMasked = realNameMasked;
    return this;
  }

  /** 组装idCardMasked。 */
  public RealnameApplicationVO idCardMasked(String idCardMasked) {
    this.idCardMasked = idCardMasked;
    return this;
  }

  /** 组装submittedAt。 */
  public RealnameApplicationVO submittedAt(String submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

  /** 组装reviewedAt。 */
  public RealnameApplicationVO reviewedAt(String reviewedAt) {
    this.reviewedAt = reviewedAt;
    return this;
  }

  /** 组装verifiedAt。 */
  public RealnameApplicationVO verifiedAt(String verifiedAt) {
    this.verifiedAt = verifiedAt;
    return this;
  }

  /** 组装rejectReason。 */
  public RealnameApplicationVO rejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
    return this;
  }

  /** 组装nextAction。 */
  public RealnameApplicationVO nextAction(String nextAction) {
    this.nextAction = nextAction;
    return this;
  }
}
