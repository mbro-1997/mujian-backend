package com.mujian.backend.factory.domain;

import java.time.*;
import java.util.*;

/** factory_profile明确持久化字段；由业务方法控制允许写入的列。 */
public class FactoryProfileDO extends FactoryFieldsDO {
  private String id;
  private String ownerUserId;
  private Long profileVersion;
  private String factoryStatus;
  private String approvedAuditId;
  private String approvedByAdminUserId;
  private LocalDateTime approvedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 读取id。 */
  public String getId() {
    return id;
  }

  /** 设置id。 */
  public void setId(String id) {
    this.id = id;
  }

  /** 读取ownerUserId。 */
  public String getOwnerUserId() {
    return ownerUserId;
  }

  /** 设置ownerUserId。 */
  public void setOwnerUserId(String ownerUserId) {
    this.ownerUserId = ownerUserId;
  }

  /** 读取profileVersion。 */
  public Long getProfileVersion() {
    return profileVersion;
  }

  /** 设置profileVersion。 */
  public void setProfileVersion(Long profileVersion) {
    this.profileVersion = profileVersion;
  }

  /** 读取factoryStatus。 */
  public String getFactoryStatus() {
    return factoryStatus;
  }

  /** 设置factoryStatus。 */
  public void setFactoryStatus(String factoryStatus) {
    this.factoryStatus = factoryStatus;
  }

  /** 读取approvedAuditId。 */
  public String getApprovedAuditId() {
    return approvedAuditId;
  }

  /** 设置approvedAuditId。 */
  public void setApprovedAuditId(String approvedAuditId) {
    this.approvedAuditId = approvedAuditId;
  }

  /** 读取approvedByAdminUserId。 */
  public String getApprovedByAdminUserId() {
    return approvedByAdminUserId;
  }

  /** 设置approvedByAdminUserId。 */
  public void setApprovedByAdminUserId(String approvedByAdminUserId) {
    this.approvedByAdminUserId = approvedByAdminUserId;
  }

  /** 读取approvedAt。 */
  public LocalDateTime getApprovedAt() {
    return approvedAt;
  }

  /** 设置approvedAt。 */
  public void setApprovedAt(LocalDateTime approvedAt) {
    this.approvedAt = approvedAt;
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

  /** 组装id。 */
  public FactoryProfileDO id(String id) {
    this.id = id;
    return this;
  }

  /** 组装ownerUserId。 */
  public FactoryProfileDO ownerUserId(String ownerUserId) {
    this.ownerUserId = ownerUserId;
    return this;
  }

  /** 组装profileVersion。 */
  public FactoryProfileDO profileVersion(Long profileVersion) {
    this.profileVersion = profileVersion;
    return this;
  }

  /** 组装factoryStatus。 */
  public FactoryProfileDO factoryStatus(String factoryStatus) {
    this.factoryStatus = factoryStatus;
    return this;
  }

  /** 组装approvedAuditId。 */
  public FactoryProfileDO approvedAuditId(String approvedAuditId) {
    this.approvedAuditId = approvedAuditId;
    return this;
  }

  /** 组装approvedByAdminUserId。 */
  public FactoryProfileDO approvedByAdminUserId(String approvedByAdminUserId) {
    this.approvedByAdminUserId = approvedByAdminUserId;
    return this;
  }

  /** 组装approvedAt。 */
  public FactoryProfileDO approvedAt(LocalDateTime approvedAt) {
    this.approvedAt = approvedAt;
    return this;
  }

  /** 组装createdAt。 */
  public FactoryProfileDO createdAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /** 组装updatedAt。 */
  public FactoryProfileDO updatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }
}
