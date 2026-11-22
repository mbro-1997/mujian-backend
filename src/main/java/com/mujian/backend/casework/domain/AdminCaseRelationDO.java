package com.mujian.backend.casework.domain;

import java.time.*;
import java.util.*;

/** admin_case_relation持久化字段，不直接作为接口响应。 */
public class AdminCaseRelationDO {
  private String id;
  private String adminCaseId;
  private String relationType;
  private String relationId;
  private String relationRole;
  private Integer isPrimary;
  private String snapshotJson;
  private LocalDateTime createdAt;

  /** 读取id。 */
  public String getId() {
    return id;
  }

  /** 设置id。 */
  public void setId(String id) {
    this.id = id;
  }

  /** 读取adminCaseId。 */
  public String getAdminCaseId() {
    return adminCaseId;
  }

  /** 设置adminCaseId。 */
  public void setAdminCaseId(String adminCaseId) {
    this.adminCaseId = adminCaseId;
  }

  /** 读取relationType。 */
  public String getRelationType() {
    return relationType;
  }

  /** 设置relationType。 */
  public void setRelationType(String relationType) {
    this.relationType = relationType;
  }

  /** 读取relationId。 */
  public String getRelationId() {
    return relationId;
  }

  /** 设置relationId。 */
  public void setRelationId(String relationId) {
    this.relationId = relationId;
  }

  /** 读取relationRole。 */
  public String getRelationRole() {
    return relationRole;
  }

  /** 设置relationRole。 */
  public void setRelationRole(String relationRole) {
    this.relationRole = relationRole;
  }

  /** 读取isPrimary。 */
  public Integer getIsPrimary() {
    return isPrimary;
  }

  /** 设置isPrimary。 */
  public void setIsPrimary(Integer isPrimary) {
    this.isPrimary = isPrimary;
  }

  /** 读取snapshotJson。 */
  public String getSnapshotJson() {
    return snapshotJson;
  }

  /** 设置snapshotJson。 */
  public void setSnapshotJson(String snapshotJson) {
    this.snapshotJson = snapshotJson;
  }

  /** 读取createdAt。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 设置createdAt。 */
  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  /** 组装id。 */
  public AdminCaseRelationDO id(String id) {
    this.id = id;
    return this;
  }

  /** 组装adminCaseId。 */
  public AdminCaseRelationDO adminCaseId(String adminCaseId) {
    this.adminCaseId = adminCaseId;
    return this;
  }

  /** 组装relationType。 */
  public AdminCaseRelationDO relationType(String relationType) {
    this.relationType = relationType;
    return this;
  }

  /** 组装relationId。 */
  public AdminCaseRelationDO relationId(String relationId) {
    this.relationId = relationId;
    return this;
  }

  /** 组装relationRole。 */
  public AdminCaseRelationDO relationRole(String relationRole) {
    this.relationRole = relationRole;
    return this;
  }

  /** 组装isPrimary。 */
  public AdminCaseRelationDO isPrimary(Integer isPrimary) {
    this.isPrimary = isPrimary;
    return this;
  }

  /** 组装snapshotJson。 */
  public AdminCaseRelationDO snapshotJson(String snapshotJson) {
    this.snapshotJson = snapshotJson;
    return this;
  }

  /** 组装createdAt。 */
  public AdminCaseRelationDO createdAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }
}
