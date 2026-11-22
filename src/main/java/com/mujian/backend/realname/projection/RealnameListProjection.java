package com.mujian.backend.realname.projection;

import java.time.*;
import java.util.*;

/** 审核列表只读取本页展示所需列，详情与敏感查看另走完整申请。 */
public class RealnameListProjection {
  private String id;
  private String userId;
  private String verificationStatus;
  private LocalDateTime submittedAt;
  private String nickname;
  private byte[] realNameCiphertext;
  private String encryptionKeyVersion;

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

  /** 读取realNameCiphertext。 */
  public byte[] getRealNameCiphertext() {
    return realNameCiphertext;
  }

  /** 设置realNameCiphertext。 */
  public void setRealNameCiphertext(byte[] realNameCiphertext) {
    this.realNameCiphertext = realNameCiphertext;
  }

  /** 读取encryptionKeyVersion。 */
  public String getEncryptionKeyVersion() {
    return encryptionKeyVersion;
  }

  /** 设置encryptionKeyVersion。 */
  public void setEncryptionKeyVersion(String encryptionKeyVersion) {
    this.encryptionKeyVersion = encryptionKeyVersion;
  }

  /** 组装id。 */
  public RealnameListProjection id(String id) {
    this.id = id;
    return this;
  }

  /** 组装userId。 */
  public RealnameListProjection userId(String userId) {
    this.userId = userId;
    return this;
  }

  /** 组装verificationStatus。 */
  public RealnameListProjection verificationStatus(String verificationStatus) {
    this.verificationStatus = verificationStatus;
    return this;
  }

  /** 组装submittedAt。 */
  public RealnameListProjection submittedAt(LocalDateTime submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

  /** 组装nickname。 */
  public RealnameListProjection nickname(String nickname) {
    this.nickname = nickname;
    return this;
  }

  /** 组装realNameCiphertext。 */
  public RealnameListProjection realNameCiphertext(byte[] realNameCiphertext) {
    this.realNameCiphertext = realNameCiphertext;
    return this;
  }

  /** 组装encryptionKeyVersion。 */
  public RealnameListProjection encryptionKeyVersion(String encryptionKeyVersion) {
    this.encryptionKeyVersion = encryptionKeyVersion;
    return this;
  }
}
