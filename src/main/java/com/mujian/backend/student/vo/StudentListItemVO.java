package com.mujian.backend.student.vo;

import java.time.*;
import java.util.*;

/** 明确接口字段，保持现有JSON属性与空值契约。 */
public class StudentListItemVO {
  private String id;
  private String applicationNo;
  private String userId;
  private String nickname;
  private String status;
  private String submittedAt;
  private String schoolName;
  private String expireAt;

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

  /** 读取status。 */
  public String getStatus() {
    return status;
  }

  /** 设置status。 */
  public void setStatus(String status) {
    this.status = status;
  }

  /** 读取submittedAt。 */
  public String getSubmittedAt() {
    return submittedAt;
  }

  /** 设置submittedAt。 */
  public void setSubmittedAt(String submittedAt) {
    this.submittedAt = submittedAt;
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
  public String getExpireAt() {
    return expireAt;
  }

  /** 设置expireAt。 */
  public void setExpireAt(String expireAt) {
    this.expireAt = expireAt;
  }

  /** 组装id。 */
  public StudentListItemVO id(String id) {
    this.id = id;
    return this;
  }

  /** 组装applicationNo。 */
  public StudentListItemVO applicationNo(String applicationNo) {
    this.applicationNo = applicationNo;
    return this;
  }

  /** 组装userId。 */
  public StudentListItemVO userId(String userId) {
    this.userId = userId;
    return this;
  }

  /** 组装nickname。 */
  public StudentListItemVO nickname(String nickname) {
    this.nickname = nickname;
    return this;
  }

  /** 组装status。 */
  public StudentListItemVO status(String status) {
    this.status = status;
    return this;
  }

  /** 组装submittedAt。 */
  public StudentListItemVO submittedAt(String submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

  /** 组装schoolName。 */
  public StudentListItemVO schoolName(String schoolName) {
    this.schoolName = schoolName;
    return this;
  }

  /** 组装expireAt。 */
  public StudentListItemVO expireAt(String expireAt) {
    this.expireAt = expireAt;
    return this;
  }
}
