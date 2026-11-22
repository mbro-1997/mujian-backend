package com.mujian.backend.factory.projection;

import java.time.*;
import java.util.*;

/** 邀请码记录及列表已提交标记。 */
public class FactoryInviteProjection {
  private String id;
  private String codeHash;
  private String createRequestId;
  private String requestHash;
  private String codeSuffix;
  private String inviteStatus;
  private LocalDateTime expireAt;
  private String createdByAdminUserId;
  private String lockedByUserId;
  private LocalDateTime lockedAt;
  private String usedByUserId;
  private LocalDateTime usedAt;
  private String revokedByAdminUserId;
  private LocalDateTime revokedAt;
  private String remark;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private Integer submitted;

  /** 读取id。 */
  public String getId() {
    return id;
  }

  /** 设置id。 */
  public void setId(String id) {
    this.id = id;
  }

  /** 读取codeHash。 */
  public String getCodeHash() {
    return codeHash;
  }

  /** 设置codeHash。 */
  public void setCodeHash(String codeHash) {
    this.codeHash = codeHash;
  }

  /** 读取createRequestId。 */
  public String getCreateRequestId() {
    return createRequestId;
  }

  /** 设置createRequestId。 */
  public void setCreateRequestId(String createRequestId) {
    this.createRequestId = createRequestId;
  }

  /** 读取requestHash。 */
  public String getRequestHash() {
    return requestHash;
  }

  /** 设置requestHash。 */
  public void setRequestHash(String requestHash) {
    this.requestHash = requestHash;
  }

  /** 读取codeSuffix。 */
  public String getCodeSuffix() {
    return codeSuffix;
  }

  /** 设置codeSuffix。 */
  public void setCodeSuffix(String codeSuffix) {
    this.codeSuffix = codeSuffix;
  }

  /** 读取inviteStatus。 */
  public String getInviteStatus() {
    return inviteStatus;
  }

  /** 设置inviteStatus。 */
  public void setInviteStatus(String inviteStatus) {
    this.inviteStatus = inviteStatus;
  }

  /** 读取expireAt。 */
  public LocalDateTime getExpireAt() {
    return expireAt;
  }

  /** 设置expireAt。 */
  public void setExpireAt(LocalDateTime expireAt) {
    this.expireAt = expireAt;
  }

  /** 读取createdByAdminUserId。 */
  public String getCreatedByAdminUserId() {
    return createdByAdminUserId;
  }

  /** 设置createdByAdminUserId。 */
  public void setCreatedByAdminUserId(String createdByAdminUserId) {
    this.createdByAdminUserId = createdByAdminUserId;
  }

  /** 读取lockedByUserId。 */
  public String getLockedByUserId() {
    return lockedByUserId;
  }

  /** 设置lockedByUserId。 */
  public void setLockedByUserId(String lockedByUserId) {
    this.lockedByUserId = lockedByUserId;
  }

  /** 读取lockedAt。 */
  public LocalDateTime getLockedAt() {
    return lockedAt;
  }

  /** 设置lockedAt。 */
  public void setLockedAt(LocalDateTime lockedAt) {
    this.lockedAt = lockedAt;
  }

  /** 读取usedByUserId。 */
  public String getUsedByUserId() {
    return usedByUserId;
  }

  /** 设置usedByUserId。 */
  public void setUsedByUserId(String usedByUserId) {
    this.usedByUserId = usedByUserId;
  }

  /** 读取usedAt。 */
  public LocalDateTime getUsedAt() {
    return usedAt;
  }

  /** 设置usedAt。 */
  public void setUsedAt(LocalDateTime usedAt) {
    this.usedAt = usedAt;
  }

  /** 读取revokedByAdminUserId。 */
  public String getRevokedByAdminUserId() {
    return revokedByAdminUserId;
  }

  /** 设置revokedByAdminUserId。 */
  public void setRevokedByAdminUserId(String revokedByAdminUserId) {
    this.revokedByAdminUserId = revokedByAdminUserId;
  }

  /** 读取revokedAt。 */
  public LocalDateTime getRevokedAt() {
    return revokedAt;
  }

  /** 设置revokedAt。 */
  public void setRevokedAt(LocalDateTime revokedAt) {
    this.revokedAt = revokedAt;
  }

  /** 读取remark。 */
  public String getRemark() {
    return remark;
  }

  /** 设置remark。 */
  public void setRemark(String remark) {
    this.remark = remark;
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

  /** 读取submitted。 */
  public Integer getSubmitted() {
    return submitted;
  }

  /** 设置submitted。 */
  public void setSubmitted(Integer submitted) {
    this.submitted = submitted;
  }

  /** 组装id。 */
  public FactoryInviteProjection id(String id) {
    this.id = id;
    return this;
  }

  /** 组装codeHash。 */
  public FactoryInviteProjection codeHash(String codeHash) {
    this.codeHash = codeHash;
    return this;
  }

  /** 组装createRequestId。 */
  public FactoryInviteProjection createRequestId(String createRequestId) {
    this.createRequestId = createRequestId;
    return this;
  }

  /** 组装requestHash。 */
  public FactoryInviteProjection requestHash(String requestHash) {
    this.requestHash = requestHash;
    return this;
  }

  /** 组装codeSuffix。 */
  public FactoryInviteProjection codeSuffix(String codeSuffix) {
    this.codeSuffix = codeSuffix;
    return this;
  }

  /** 组装inviteStatus。 */
  public FactoryInviteProjection inviteStatus(String inviteStatus) {
    this.inviteStatus = inviteStatus;
    return this;
  }

  /** 组装expireAt。 */
  public FactoryInviteProjection expireAt(LocalDateTime expireAt) {
    this.expireAt = expireAt;
    return this;
  }

  /** 组装createdByAdminUserId。 */
  public FactoryInviteProjection createdByAdminUserId(String createdByAdminUserId) {
    this.createdByAdminUserId = createdByAdminUserId;
    return this;
  }

  /** 组装lockedByUserId。 */
  public FactoryInviteProjection lockedByUserId(String lockedByUserId) {
    this.lockedByUserId = lockedByUserId;
    return this;
  }

  /** 组装lockedAt。 */
  public FactoryInviteProjection lockedAt(LocalDateTime lockedAt) {
    this.lockedAt = lockedAt;
    return this;
  }

  /** 组装usedByUserId。 */
  public FactoryInviteProjection usedByUserId(String usedByUserId) {
    this.usedByUserId = usedByUserId;
    return this;
  }

  /** 组装usedAt。 */
  public FactoryInviteProjection usedAt(LocalDateTime usedAt) {
    this.usedAt = usedAt;
    return this;
  }

  /** 组装revokedByAdminUserId。 */
  public FactoryInviteProjection revokedByAdminUserId(String revokedByAdminUserId) {
    this.revokedByAdminUserId = revokedByAdminUserId;
    return this;
  }

  /** 组装revokedAt。 */
  public FactoryInviteProjection revokedAt(LocalDateTime revokedAt) {
    this.revokedAt = revokedAt;
    return this;
  }

  /** 组装remark。 */
  public FactoryInviteProjection remark(String remark) {
    this.remark = remark;
    return this;
  }

  /** 组装createdAt。 */
  public FactoryInviteProjection createdAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /** 组装updatedAt。 */
  public FactoryInviteProjection updatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }

  /** 组装submitted。 */
  public FactoryInviteProjection submitted(Integer submitted) {
    this.submitted = submitted;
    return this;
  }
}
