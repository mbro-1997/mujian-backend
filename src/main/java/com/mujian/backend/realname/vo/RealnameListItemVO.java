package com.mujian.backend.realname.vo;

import java.time.*;
import java.util.*;

/** 明确接口字段，保持现有JSON属性与空值契约。 */
public class RealnameListItemVO {
  private String id;
  private String applicationNo;
  private String userId;
  private String nickname;
  private String status;
  private String submittedAt;
  private String realNameMasked;

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

  /** 读取realNameMasked。 */
  public String getRealNameMasked() {
    return realNameMasked;
  }

  /** 设置realNameMasked。 */
  public void setRealNameMasked(String realNameMasked) {
    this.realNameMasked = realNameMasked;
  }

  /** 组装id。 */
  public RealnameListItemVO id(String id) {
    this.id = id;
    return this;
  }

  /** 组装applicationNo。 */
  public RealnameListItemVO applicationNo(String applicationNo) {
    this.applicationNo = applicationNo;
    return this;
  }

  /** 组装userId。 */
  public RealnameListItemVO userId(String userId) {
    this.userId = userId;
    return this;
  }

  /** 组装nickname。 */
  public RealnameListItemVO nickname(String nickname) {
    this.nickname = nickname;
    return this;
  }

  /** 组装status。 */
  public RealnameListItemVO status(String status) {
    this.status = status;
    return this;
  }

  /** 组装submittedAt。 */
  public RealnameListItemVO submittedAt(String submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

  /** 组装realNameMasked。 */
  public RealnameListItemVO realNameMasked(String realNameMasked) {
    this.realNameMasked = realNameMasked;
    return this;
  }
}
