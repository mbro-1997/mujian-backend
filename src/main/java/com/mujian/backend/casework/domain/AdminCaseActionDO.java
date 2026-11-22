package com.mujian.backend.casework.domain;

import java.time.*;
import java.util.*;

/** admin_case_action持久化字段，不直接作为接口响应。 */
public class AdminCaseActionDO {
  private String id;
  private String actionNo;
  private String adminCaseId;
  private String parentActionId;
  private Integer stepNo;
  private String actionType;
  private String actionStatus;
  private String targetModule;
  private String targetType;
  private String targetId;
  private String requestPayloadJson;
  private Long plannedRentRefundAmountFen;
  private Long plannedDepositRefundAmountFen;
  private Long plannedFreightRefundAmountFen;
  private String resultPayloadJson;
  private String failureCode;
  private String failureMessage;
  private String idempotencyKey;
  private String operatorAdminUserId;
  private String operationReason;
  private String confirmationSnapshotJson;
  private LocalDateTime operatorConfirmedAt;
  private LocalDateTime executedAt;
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

  /** 读取actionNo。 */
  public String getActionNo() {
    return actionNo;
  }

  /** 设置actionNo。 */
  public void setActionNo(String actionNo) {
    this.actionNo = actionNo;
  }

  /** 读取adminCaseId。 */
  public String getAdminCaseId() {
    return adminCaseId;
  }

  /** 设置adminCaseId。 */
  public void setAdminCaseId(String adminCaseId) {
    this.adminCaseId = adminCaseId;
  }

  /** 读取parentActionId。 */
  public String getParentActionId() {
    return parentActionId;
  }

  /** 设置parentActionId。 */
  public void setParentActionId(String parentActionId) {
    this.parentActionId = parentActionId;
  }

  /** 读取stepNo。 */
  public Integer getStepNo() {
    return stepNo;
  }

  /** 设置stepNo。 */
  public void setStepNo(Integer stepNo) {
    this.stepNo = stepNo;
  }

  /** 读取actionType。 */
  public String getActionType() {
    return actionType;
  }

  /** 设置actionType。 */
  public void setActionType(String actionType) {
    this.actionType = actionType;
  }

  /** 读取actionStatus。 */
  public String getActionStatus() {
    return actionStatus;
  }

  /** 设置actionStatus。 */
  public void setActionStatus(String actionStatus) {
    this.actionStatus = actionStatus;
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

  /** 读取requestPayloadJson。 */
  public String getRequestPayloadJson() {
    return requestPayloadJson;
  }

  /** 设置requestPayloadJson。 */
  public void setRequestPayloadJson(String requestPayloadJson) {
    this.requestPayloadJson = requestPayloadJson;
  }

  /** 读取plannedRentRefundAmountFen。 */
  public Long getPlannedRentRefundAmountFen() {
    return plannedRentRefundAmountFen;
  }

  /** 设置plannedRentRefundAmountFen。 */
  public void setPlannedRentRefundAmountFen(Long plannedRentRefundAmountFen) {
    this.plannedRentRefundAmountFen = plannedRentRefundAmountFen;
  }

  /** 读取plannedDepositRefundAmountFen。 */
  public Long getPlannedDepositRefundAmountFen() {
    return plannedDepositRefundAmountFen;
  }

  /** 设置plannedDepositRefundAmountFen。 */
  public void setPlannedDepositRefundAmountFen(Long plannedDepositRefundAmountFen) {
    this.plannedDepositRefundAmountFen = plannedDepositRefundAmountFen;
  }

  /** 读取plannedFreightRefundAmountFen。 */
  public Long getPlannedFreightRefundAmountFen() {
    return plannedFreightRefundAmountFen;
  }

  /** 设置plannedFreightRefundAmountFen。 */
  public void setPlannedFreightRefundAmountFen(Long plannedFreightRefundAmountFen) {
    this.plannedFreightRefundAmountFen = plannedFreightRefundAmountFen;
  }

  /** 读取resultPayloadJson。 */
  public String getResultPayloadJson() {
    return resultPayloadJson;
  }

  /** 设置resultPayloadJson。 */
  public void setResultPayloadJson(String resultPayloadJson) {
    this.resultPayloadJson = resultPayloadJson;
  }

  /** 读取failureCode。 */
  public String getFailureCode() {
    return failureCode;
  }

  /** 设置failureCode。 */
  public void setFailureCode(String failureCode) {
    this.failureCode = failureCode;
  }

  /** 读取failureMessage。 */
  public String getFailureMessage() {
    return failureMessage;
  }

  /** 设置failureMessage。 */
  public void setFailureMessage(String failureMessage) {
    this.failureMessage = failureMessage;
  }

  /** 读取idempotencyKey。 */
  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  /** 设置idempotencyKey。 */
  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  /** 读取operatorAdminUserId。 */
  public String getOperatorAdminUserId() {
    return operatorAdminUserId;
  }

  /** 设置operatorAdminUserId。 */
  public void setOperatorAdminUserId(String operatorAdminUserId) {
    this.operatorAdminUserId = operatorAdminUserId;
  }

  /** 读取operationReason。 */
  public String getOperationReason() {
    return operationReason;
  }

  /** 设置operationReason。 */
  public void setOperationReason(String operationReason) {
    this.operationReason = operationReason;
  }

  /** 读取confirmationSnapshotJson。 */
  public String getConfirmationSnapshotJson() {
    return confirmationSnapshotJson;
  }

  /** 设置confirmationSnapshotJson。 */
  public void setConfirmationSnapshotJson(String confirmationSnapshotJson) {
    this.confirmationSnapshotJson = confirmationSnapshotJson;
  }

  /** 读取operatorConfirmedAt。 */
  public LocalDateTime getOperatorConfirmedAt() {
    return operatorConfirmedAt;
  }

  /** 设置operatorConfirmedAt。 */
  public void setOperatorConfirmedAt(LocalDateTime operatorConfirmedAt) {
    this.operatorConfirmedAt = operatorConfirmedAt;
  }

  /** 读取executedAt。 */
  public LocalDateTime getExecutedAt() {
    return executedAt;
  }

  /** 设置executedAt。 */
  public void setExecutedAt(LocalDateTime executedAt) {
    this.executedAt = executedAt;
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
  public AdminCaseActionDO id(String id) {
    this.id = id;
    return this;
  }

  /** 组装actionNo。 */
  public AdminCaseActionDO actionNo(String actionNo) {
    this.actionNo = actionNo;
    return this;
  }

  /** 组装adminCaseId。 */
  public AdminCaseActionDO adminCaseId(String adminCaseId) {
    this.adminCaseId = adminCaseId;
    return this;
  }

  /** 组装parentActionId。 */
  public AdminCaseActionDO parentActionId(String parentActionId) {
    this.parentActionId = parentActionId;
    return this;
  }

  /** 组装stepNo。 */
  public AdminCaseActionDO stepNo(Integer stepNo) {
    this.stepNo = stepNo;
    return this;
  }

  /** 组装actionType。 */
  public AdminCaseActionDO actionType(String actionType) {
    this.actionType = actionType;
    return this;
  }

  /** 组装actionStatus。 */
  public AdminCaseActionDO actionStatus(String actionStatus) {
    this.actionStatus = actionStatus;
    return this;
  }

  /** 组装targetModule。 */
  public AdminCaseActionDO targetModule(String targetModule) {
    this.targetModule = targetModule;
    return this;
  }

  /** 组装targetType。 */
  public AdminCaseActionDO targetType(String targetType) {
    this.targetType = targetType;
    return this;
  }

  /** 组装targetId。 */
  public AdminCaseActionDO targetId(String targetId) {
    this.targetId = targetId;
    return this;
  }

  /** 组装requestPayloadJson。 */
  public AdminCaseActionDO requestPayloadJson(String requestPayloadJson) {
    this.requestPayloadJson = requestPayloadJson;
    return this;
  }

  /** 组装plannedRentRefundAmountFen。 */
  public AdminCaseActionDO plannedRentRefundAmountFen(Long plannedRentRefundAmountFen) {
    this.plannedRentRefundAmountFen = plannedRentRefundAmountFen;
    return this;
  }

  /** 组装plannedDepositRefundAmountFen。 */
  public AdminCaseActionDO plannedDepositRefundAmountFen(Long plannedDepositRefundAmountFen) {
    this.plannedDepositRefundAmountFen = plannedDepositRefundAmountFen;
    return this;
  }

  /** 组装plannedFreightRefundAmountFen。 */
  public AdminCaseActionDO plannedFreightRefundAmountFen(Long plannedFreightRefundAmountFen) {
    this.plannedFreightRefundAmountFen = plannedFreightRefundAmountFen;
    return this;
  }

  /** 组装resultPayloadJson。 */
  public AdminCaseActionDO resultPayloadJson(String resultPayloadJson) {
    this.resultPayloadJson = resultPayloadJson;
    return this;
  }

  /** 组装failureCode。 */
  public AdminCaseActionDO failureCode(String failureCode) {
    this.failureCode = failureCode;
    return this;
  }

  /** 组装failureMessage。 */
  public AdminCaseActionDO failureMessage(String failureMessage) {
    this.failureMessage = failureMessage;
    return this;
  }

  /** 组装idempotencyKey。 */
  public AdminCaseActionDO idempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
    return this;
  }

  /** 组装operatorAdminUserId。 */
  public AdminCaseActionDO operatorAdminUserId(String operatorAdminUserId) {
    this.operatorAdminUserId = operatorAdminUserId;
    return this;
  }

  /** 组装operationReason。 */
  public AdminCaseActionDO operationReason(String operationReason) {
    this.operationReason = operationReason;
    return this;
  }

  /** 组装confirmationSnapshotJson。 */
  public AdminCaseActionDO confirmationSnapshotJson(String confirmationSnapshotJson) {
    this.confirmationSnapshotJson = confirmationSnapshotJson;
    return this;
  }

  /** 组装operatorConfirmedAt。 */
  public AdminCaseActionDO operatorConfirmedAt(LocalDateTime operatorConfirmedAt) {
    this.operatorConfirmedAt = operatorConfirmedAt;
    return this;
  }

  /** 组装executedAt。 */
  public AdminCaseActionDO executedAt(LocalDateTime executedAt) {
    this.executedAt = executedAt;
    return this;
  }

  /** 组装createdAt。 */
  public AdminCaseActionDO createdAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /** 组装updatedAt。 */
  public AdminCaseActionDO updatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }
}
