package com.mujian.backend.student.vo;

import java.time.*;
import java.util.*;

/** 明确接口字段，保持现有JSON属性与空值契约。 */
public class StudentApplicationVO {
  private String id;
  private String applicationNo;
  private String status;
  private String schoolName;
  private int materialCount;
  private String submittedAt;
  private String reviewedAt;
  private String expireAt;
  private boolean isCurrentlyValid;
  private String rejectReason;

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

  /** 读取schoolName。 */
  public String getSchoolName() {
    return schoolName;
  }

  /** 设置schoolName。 */
  public void setSchoolName(String schoolName) {
    this.schoolName = schoolName;
  }

  /** 读取materialCount。 */
  public int getMaterialCount() {
    return materialCount;
  }

  /** 设置materialCount。 */
  public void setMaterialCount(int materialCount) {
    this.materialCount = materialCount;
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

  /** 读取expireAt。 */
  public String getExpireAt() {
    return expireAt;
  }

  /** 设置expireAt。 */
  public void setExpireAt(String expireAt) {
    this.expireAt = expireAt;
  }

  /** 读取isCurrentlyValid。 */
  public boolean getIsCurrentlyValid() {
    return isCurrentlyValid;
  }

  /** 设置isCurrentlyValid。 */
  public void setIsCurrentlyValid(boolean isCurrentlyValid) {
    this.isCurrentlyValid = isCurrentlyValid;
  }

  /** 读取rejectReason。 */
  public String getRejectReason() {
    return rejectReason;
  }

  /** 设置rejectReason。 */
  public void setRejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
  }

  /** 组装id。 */
  public StudentApplicationVO id(String id) {
    this.id = id;
    return this;
  }

  /** 组装applicationNo。 */
  public StudentApplicationVO applicationNo(String applicationNo) {
    this.applicationNo = applicationNo;
    return this;
  }

  /** 组装status。 */
  public StudentApplicationVO status(String status) {
    this.status = status;
    return this;
  }

  /** 组装schoolName。 */
  public StudentApplicationVO schoolName(String schoolName) {
    this.schoolName = schoolName;
    return this;
  }

  /** 组装materialCount。 */
  public StudentApplicationVO materialCount(int materialCount) {
    this.materialCount = materialCount;
    return this;
  }

  /** 组装submittedAt。 */
  public StudentApplicationVO submittedAt(String submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

  /** 组装reviewedAt。 */
  public StudentApplicationVO reviewedAt(String reviewedAt) {
    this.reviewedAt = reviewedAt;
    return this;
  }

  /** 组装expireAt。 */
  public StudentApplicationVO expireAt(String expireAt) {
    this.expireAt = expireAt;
    return this;
  }

  /** 组装isCurrentlyValid。 */
  public StudentApplicationVO isCurrentlyValid(boolean isCurrentlyValid) {
    this.isCurrentlyValid = isCurrentlyValid;
    return this;
  }

  /** 组装rejectReason。 */
  public StudentApplicationVO rejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
    return this;
  }
}
