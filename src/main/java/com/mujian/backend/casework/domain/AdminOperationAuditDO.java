package com.mujian.backend.casework.domain;

import java.time.*;
import java.util.*;

/** admin_operation_audit持久化字段，不直接作为接口响应。 */
public class AdminOperationAuditDO {
  private String id;
  private String auditNo;
  private String adminUserId;
  private String adminCaseId;
  private String caseActionId;
  private String permissionCode;
  private String operationCode;
  private String operationName;
  private String targetModule;
  private String targetType;
  private String targetId;
  private String requestId;
  private String idempotencyKey;
  private String operationReason;
  private String beforeSnapshotJson;
  private String afterSnapshotJson;
  private String resultStatus;
  private String resultMessage;
  private String clientIp;
  private String userAgent;
  private LocalDateTime createdAt;

  /** 读取id。 */
  public String getId() {
    return id;
  }

  /** 设置id。 */
  public void setId(String id) {
    this.id = id;
  }

  /** 读取auditNo。 */
  public String getAuditNo() {
    return auditNo;
  }

  /** 设置auditNo。 */
  public void setAuditNo(String auditNo) {
    this.auditNo = auditNo;
  }

  /** 读取adminUserId。 */
  public String getAdminUserId() {
    return adminUserId;
  }

  /** 设置adminUserId。 */
  public void setAdminUserId(String adminUserId) {
    this.adminUserId = adminUserId;
  }

  /** 读取adminCaseId。 */
  public String getAdminCaseId() {
    return adminCaseId;
  }

  /** 设置adminCaseId。 */
  public void setAdminCaseId(String adminCaseId) {
    this.adminCaseId = adminCaseId;
  }

  /** 读取caseActionId。 */
  public String getCaseActionId() {
    return caseActionId;
  }

  /** 设置caseActionId。 */
  public void setCaseActionId(String caseActionId) {
    this.caseActionId = caseActionId;
  }

  /** 读取permissionCode。 */
  public String getPermissionCode() {
    return permissionCode;
  }

  /** 设置permissionCode。 */
  public void setPermissionCode(String permissionCode) {
    this.permissionCode = permissionCode;
  }

  /** 读取operationCode。 */
  public String getOperationCode() {
    return operationCode;
  }

  /** 设置operationCode。 */
  public void setOperationCode(String operationCode) {
    this.operationCode = operationCode;
  }

  /** 读取operationName。 */
  public String getOperationName() {
    return operationName;
  }

  /** 设置operationName。 */
  public void setOperationName(String operationName) {
    this.operationName = operationName;
  }

  /** 读取targetModule。 */
  public String getTargetModule() {
    return targetModule;
  }

  /** 设置targetModule。 */
  public void setTargetModule(String targetModule) {
    this.targetModule = targetModule;
  }

  /** 读取targetType。 */
  public String getTargetType() {
    return targetType;
  }

  /** 设置targetType。 */
  public void setTargetType(String targetType) {
    this.targetType = targetType;
  }

  /** 读取targetId。 */
  public String getTargetId() {
    return targetId;
  }

  /** 设置targetId。 */
  public void setTargetId(String targetId) {
    this.targetId = targetId;
  }

  /** 读取requestId。 */
  public String getRequestId() {
    return requestId;
  }

  /** 设置requestId。 */
  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  /** 读取idempotencyKey。 */
  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  /** 设置idempotencyKey。 */
  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  /** 读取operationReason。 */
  public String getOperationReason() {
    return operationReason;
  }

  /** 设置operationReason。 */
  public void setOperationReason(String operationReason) {
    this.operationReason = operationReason;
  }

  /** 读取beforeSnapshotJson。 */
  public String getBeforeSnapshotJson() {
    return beforeSnapshotJson;
  }

  /** 设置beforeSnapshotJson。 */
  public void setBeforeSnapshotJson(String beforeSnapshotJson) {
    this.beforeSnapshotJson = beforeSnapshotJson;
  }

  /** 读取afterSnapshotJson。 */
  public String getAfterSnapshotJson() {
    return afterSnapshotJson;
  }

  /** 设置afterSnapshotJson。 */
  public void setAfterSnapshotJson(String afterSnapshotJson) {
    this.afterSnapshotJson = afterSnapshotJson;
  }

  /** 读取resultStatus。 */
  public String getResultStatus() {
    return resultStatus;
  }

  /** 设置resultStatus。 */
  public void setResultStatus(String resultStatus) {
    this.resultStatus = resultStatus;
  }

  /** 读取resultMessage。 */
  public String getResultMessage() {
    return resultMessage;
  }

  /** 设置resultMessage。 */
  public void setResultMessage(String resultMessage) {
    this.resultMessage = resultMessage;
  }

  /** 读取clientIp。 */
  public String getClientIp() {
    return clientIp;
  }

  /** 设置clientIp。 */
  public void setClientIp(String clientIp) {
    this.clientIp = clientIp;
  }

  /** 读取userAgent。 */
  public String getUserAgent() {
    return userAgent;
  }

  /** 设置userAgent。 */
  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
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
  public AdminOperationAuditDO id(String id) {
    this.id = id;
    return this;
  }

  /** 组装auditNo。 */
  public AdminOperationAuditDO auditNo(String auditNo) {
    this.auditNo = auditNo;
    return this;
  }

  /** 组装adminUserId。 */
  public AdminOperationAuditDO adminUserId(String adminUserId) {
    this.adminUserId = adminUserId;
    return this;
  }

  /** 组装adminCaseId。 */
  public AdminOperationAuditDO adminCaseId(String adminCaseId) {
    this.adminCaseId = adminCaseId;
    return this;
  }

  /** 组装caseActionId。 */
  public AdminOperationAuditDO caseActionId(String caseActionId) {
    this.caseActionId = caseActionId;
    return this;
  }

  /** 组装permissionCode。 */
  public AdminOperationAuditDO permissionCode(String permissionCode) {
    this.permissionCode = permissionCode;
    return this;
  }

  /** 组装operationCode。 */
  public AdminOperationAuditDO operationCode(String operationCode) {
    this.operationCode = operationCode;
    return this;
  }

  /** 组装operationName。 */
  public AdminOperationAuditDO operationName(String operationName) {
    this.operationName = operationName;
    return this;
  }

  /** 组装targetModule。 */
  public AdminOperationAuditDO targetModule(String targetModule) {
    this.targetModule = targetModule;
    return this;
  }

  /** 组装targetType。 */
  public AdminOperationAuditDO targetType(String targetType) {
    this.targetType = targetType;
    return this;
  }

  /** 组装targetId。 */
  public AdminOperationAuditDO targetId(String targetId) {
    this.targetId = targetId;
    return this;
  }

  /** 组装requestId。 */
  public AdminOperationAuditDO requestId(String requestId) {
    this.requestId = requestId;
    return this;
  }

  /** 组装idempotencyKey。 */
  public AdminOperationAuditDO idempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
    return this;
  }

  /** 组装operationReason。 */
  public AdminOperationAuditDO operationReason(String operationReason) {
    this.operationReason = operationReason;
    return this;
  }

  /** 组装beforeSnapshotJson。 */
  public AdminOperationAuditDO beforeSnapshotJson(String beforeSnapshotJson) {
    this.beforeSnapshotJson = beforeSnapshotJson;
    return this;
  }

  /** 组装afterSnapshotJson。 */
  public AdminOperationAuditDO afterSnapshotJson(String afterSnapshotJson) {
    this.afterSnapshotJson = afterSnapshotJson;
    return this;
  }

  /** 组装resultStatus。 */
  public AdminOperationAuditDO resultStatus(String resultStatus) {
    this.resultStatus = resultStatus;
    return this;
  }

  /** 组装resultMessage。 */
  public AdminOperationAuditDO resultMessage(String resultMessage) {
    this.resultMessage = resultMessage;
    return this;
  }

  /** 组装clientIp。 */
  public AdminOperationAuditDO clientIp(String clientIp) {
    this.clientIp = clientIp;
    return this;
  }

  /** 组装userAgent。 */
  public AdminOperationAuditDO userAgent(String userAgent) {
    this.userAgent = userAgent;
    return this;
  }

  /** 组装createdAt。 */
  public AdminOperationAuditDO createdAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }
}
