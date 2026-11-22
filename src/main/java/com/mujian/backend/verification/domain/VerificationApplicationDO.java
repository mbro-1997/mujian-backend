package com.mujian.backend.verification.domain;

import java.time.*;
import java.util.*;

/** 实名与学生审核共有且同义的申请标识、归属、状态及版本。 */
public class VerificationApplicationDO {
  private String id;
  private String userId;
  private String verificationStatus;
  private Integer version;

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

  /** 读取version。 */
  public Integer getVersion() {
    return version;
  }

  /** 设置version。 */
  public void setVersion(Integer version) {
    this.version = version;
  }

  /** 组装id。 */
  public VerificationApplicationDO id(String id) {
    this.id = id;
    return this;
  }

  /** 组装userId。 */
  public VerificationApplicationDO userId(String userId) {
    this.userId = userId;
    return this;
  }

  /** 组装verificationStatus。 */
  public VerificationApplicationDO verificationStatus(String verificationStatus) {
    this.verificationStatus = verificationStatus;
    return this;
  }

  /** 组装version。 */
  public VerificationApplicationDO version(Integer version) {
    this.version = version;
    return this;
  }
}
