package com.mujian.backend.realname.domain;

import java.time.*;
import java.util.*;

/** user_realname_verification持久化对象，不直接返回敏感原始字段。 */
public class RealnameVerificationDO
    extends com.mujian.backend.verification.domain.VerificationApplicationDO {
  private byte[] realNameCiphertext;
  private byte[] idCardCiphertext;
  private String encryptionKeyVersion;
  private String idCardFrontObjectKey;
  private String idCardBackObjectKey;
  private String submitRequestId;
  private LocalDateTime submittedAt;
  private String materialConsentVersion;
  private LocalDateTime materialConsentAt;
  private String reviewedByAdminUserId;
  private LocalDateTime reviewedAt;
  private String auditRemark;
  private String provider;
  private String providerRequestId;
  private String rejectReason;
  private LocalDateTime verifiedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 读取realNameCiphertext。 */
  public byte[] getRealNameCiphertext() {
    return realNameCiphertext;
  }

  /** 设置realNameCiphertext。 */
  public void setRealNameCiphertext(byte[] realNameCiphertext) {
    this.realNameCiphertext = realNameCiphertext;
  }

  /** 读取idCardCiphertext。 */
  public byte[] getIdCardCiphertext() {
    return idCardCiphertext;
  }

  /** 设置idCardCiphertext。 */
  public void setIdCardCiphertext(byte[] idCardCiphertext) {
    this.idCardCiphertext = idCardCiphertext;
  }

  /** 读取encryptionKeyVersion。 */
  public String getEncryptionKeyVersion() {
    return encryptionKeyVersion;
  }

  /** 设置encryptionKeyVersion。 */
  public void setEncryptionKeyVersion(String encryptionKeyVersion) {
    this.encryptionKeyVersion = encryptionKeyVersion;
  }

  /** 读取idCardFrontObjectKey。 */
  public String getIdCardFrontObjectKey() {
    return idCardFrontObjectKey;
  }

  /** 设置idCardFrontObjectKey。 */
  public void setIdCardFrontObjectKey(String idCardFrontObjectKey) {
    this.idCardFrontObjectKey = idCardFrontObjectKey;
  }

  /** 读取idCardBackObjectKey。 */
  public String getIdCardBackObjectKey() {
    return idCardBackObjectKey;
  }

  /** 设置idCardBackObjectKey。 */
  public void setIdCardBackObjectKey(String idCardBackObjectKey) {
    this.idCardBackObjectKey = idCardBackObjectKey;
  }

  /** 读取submitRequestId。 */
  public String getSubmitRequestId() {
    return submitRequestId;
  }

  /** 设置submitRequestId。 */
  public void setSubmitRequestId(String submitRequestId) {
    this.submitRequestId = submitRequestId;
  }

  /** 读取submittedAt。 */
  public LocalDateTime getSubmittedAt() {
    return submittedAt;
  }

  /** 设置submittedAt。 */
  public void setSubmittedAt(LocalDateTime submittedAt) {
    this.submittedAt = submittedAt;
  }

  /** 读取materialConsentVersion。 */
  public String getMaterialConsentVersion() {
    return materialConsentVersion;
  }

  /** 设置materialConsentVersion。 */
  public void setMaterialConsentVersion(String materialConsentVersion) {
    this.materialConsentVersion = materialConsentVersion;
  }

  /** 读取materialConsentAt。 */
  public LocalDateTime getMaterialConsentAt() {
    return materialConsentAt;
  }

  /** 设置materialConsentAt。 */
  public void setMaterialConsentAt(LocalDateTime materialConsentAt) {
    this.materialConsentAt = materialConsentAt;
  }

  /** 读取reviewedByAdminUserId。 */
  public String getReviewedByAdminUserId() {
    return reviewedByAdminUserId;
  }

  /** 设置reviewedByAdminUserId。 */
  public void setReviewedByAdminUserId(String reviewedByAdminUserId) {
    this.reviewedByAdminUserId = reviewedByAdminUserId;
  }

  /** 读取reviewedAt。 */
  public LocalDateTime getReviewedAt() {
    return reviewedAt;
  }

  /** 设置reviewedAt。 */
  public void setReviewedAt(LocalDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
  }

  /** 读取auditRemark。 */
  public String getAuditRemark() {
    return auditRemark;
  }

  /** 设置auditRemark。 */
  public void setAuditRemark(String auditRemark) {
    this.auditRemark = auditRemark;
  }

  /** 读取provider。 */
  public String getProvider() {
    return provider;
  }

  /** 设置provider。 */
  public void setProvider(String provider) {
    this.provider = provider;
  }

  /** 读取providerRequestId。 */
  public String getProviderRequestId() {
    return providerRequestId;
  }

  /** 设置providerRequestId。 */
  public void setProviderRequestId(String providerRequestId) {
    this.providerRequestId = providerRequestId;
  }

  /** 读取rejectReason。 */
  public String getRejectReason() {
    return rejectReason;
  }

  /** 设置rejectReason。 */
  public void setRejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
  }

  /** 读取verifiedAt。 */
  public LocalDateTime getVerifiedAt() {
    return verifiedAt;
  }

  /** 设置verifiedAt。 */
  public void setVerifiedAt(LocalDateTime verifiedAt) {
    this.verifiedAt = verifiedAt;
  }

  /** 读取createdAt。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 设置createdAt。 */
  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /** 读取updatedAt。 */
  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  /** 设置updatedAt。 */
  public void setUpdatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
  }

  /** 组装realNameCiphertext。 */
  public RealnameVerificationDO realNameCiphertext(byte[] realNameCiphertext) {
    this.realNameCiphertext = realNameCiphertext;
    return this;
  }

  /** 组装idCardCiphertext。 */
  public RealnameVerificationDO idCardCiphertext(byte[] idCardCiphertext) {
    this.idCardCiphertext = idCardCiphertext;
    return this;
  }

  /** 组装encryptionKeyVersion。 */
  public RealnameVerificationDO encryptionKeyVersion(String encryptionKeyVersion) {
    this.encryptionKeyVersion = encryptionKeyVersion;
    return this;
  }

  /** 组装idCardFrontObjectKey。 */
  public RealnameVerificationDO idCardFrontObjectKey(String idCardFrontObjectKey) {
    this.idCardFrontObjectKey = idCardFrontObjectKey;
    return this;
  }

  /** 组装idCardBackObjectKey。 */
  public RealnameVerificationDO idCardBackObjectKey(String idCardBackObjectKey) {
    this.idCardBackObjectKey = idCardBackObjectKey;
    return this;
  }

  /** 组装submitRequestId。 */
  public RealnameVerificationDO submitRequestId(String submitRequestId) {
    this.submitRequestId = submitRequestId;
    return this;
  }

  /** 组装submittedAt。 */
  public RealnameVerificationDO submittedAt(LocalDateTime submittedAt) {
    this.submittedAt = submittedAt;
    return this;
  }

  /** 组装materialConsentVersion。 */
  public RealnameVerificationDO materialConsentVersion(String materialConsentVersion) {
    this.materialConsentVersion = materialConsentVersion;
    return this;
  }

  /** 组装materialConsentAt。 */
  public RealnameVerificationDO materialConsentAt(LocalDateTime materialConsentAt) {
    this.materialConsentAt = materialConsentAt;
    return this;
  }

  /** 组装reviewedByAdminUserId。 */
  public RealnameVerificationDO reviewedByAdminUserId(String reviewedByAdminUserId) {
    this.reviewedByAdminUserId = reviewedByAdminUserId;
    return this;
  }

  /** 组装reviewedAt。 */
  public RealnameVerificationDO reviewedAt(LocalDateTime reviewedAt) {
    this.reviewedAt = reviewedAt;
    return this;
  }

  /** 组装auditRemark。 */
  public RealnameVerificationDO auditRemark(String auditRemark) {
    this.auditRemark = auditRemark;
    return this;
  }

  /** 组装provider。 */
  public RealnameVerificationDO provider(String provider) {
    this.provider = provider;
    return this;
  }

  /** 组装providerRequestId。 */
  public RealnameVerificationDO providerRequestId(String providerRequestId) {
    this.providerRequestId = providerRequestId;
    return this;
  }

  /** 组装rejectReason。 */
  public RealnameVerificationDO rejectReason(String rejectReason) {
    this.rejectReason = rejectReason;
    return this;
  }

  /** 组装verifiedAt。 */
  public RealnameVerificationDO verifiedAt(LocalDateTime verifiedAt) {
    this.verifiedAt = verifiedAt;
    return this;
  }

  /** 组装createdAt。 */
  public RealnameVerificationDO createdAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /** 组装updatedAt。 */
  public RealnameVerificationDO updatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }
}
