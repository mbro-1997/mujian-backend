package com.mujian.backend.casework.domain;

import java.time.*;
import java.util.*;

/** admin_case持久化字段，不直接作为接口响应。 */
public class AdminCaseDO {
  private String id;
  private String caseNo;
  private String activeCaseKey;
  private String caseType;
  private String caseStatus;
  private String priority;
  private String title;
  private String summary;
  private String rentalOrderId;
  private String buyerUserId;
  private String factoryId;
  private LocalDateTime deadlineAt;
  private String resolutionCode;
  private String resolutionSummary;
  private String closureChecklistJson;
  private LocalDateTime closedAt;
  private String closedByAdminUserId;
  private Integer version;
  private String createdByType;
  private String createdByAdminUserId;
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

  /** 读取caseNo。 */
  public String getCaseNo() {
    return caseNo;
  }

  /** 设置caseNo。 */
  public void setCaseNo(String caseNo) {
    this.caseNo = caseNo;
  }

  /** 读取activeCaseKey。 */
  public String getActiveCaseKey() {
    return activeCaseKey;
  }

  /** 设置activeCaseKey。 */
  public void setActiveCaseKey(String activeCaseKey) {
    this.activeCaseKey = activeCaseKey;
  }

  /** 读取caseType。 */
  public String getCaseType() {
    return caseType;
  }

  /** 设置caseType。 */
  public void setCaseType(String caseType) {
    this.caseType = caseType;
  }

  /** 读取caseStatus。 */
  public String getCaseStatus() {
    return caseStatus;
  }

  /** 设置caseStatus。 */
  public void setCaseStatus(String caseStatus) {
    this.caseStatus = caseStatus;
  }

  /** 读取priority。 */
  public String getPriority() {
    return priority;
  }

  /** 设置priority。 */
  public void setPriority(String priority) {
    this.priority = priority;
  }

  /** 读取title。 */
  public String getTitle() {
    return title;
  }

  /** 设置title。 */
  public void setTitle(String title) {
    this.title = title;
  }

  /** 读取summary。 */
  public String getSummary() {
    return summary;
  }

  /** 设置summary。 */
  public void setSummary(String summary) {
    this.summary = summary;
  }

  /** 读取rentalOrderId。 */
  public String getRentalOrderId() {
    return rentalOrderId;
  }

  /** 设置rentalOrderId。 */
  public void setRentalOrderId(String rentalOrderId) {
    this.rentalOrderId = rentalOrderId;
  }

  /** 读取buyerUserId。 */
  public String getBuyerUserId() {
    return buyerUserId;
  }

  /** 设置buyerUserId。 */
  public void setBuyerUserId(String buyerUserId) {
    this.buyerUserId = buyerUserId;
  }

  /** 读取factoryId。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 设置factoryId。 */
  public void setFactoryId(String factoryId) {
    this.factoryId = factoryId;
  }

  /** 读取deadlineAt。 */
  public LocalDateTime getDeadlineAt() {
    return deadlineAt;
  }

  /** 设置deadlineAt。 */
  public void setDeadlineAt(LocalDateTime deadlineAt) {
    this.deadlineAt = deadlineAt;
  }

  /** 读取resolutionCode。 */
  public String getResolutionCode() {
    return resolutionCode;
  }

  /** 设置resolutionCode。 */
  public void setResolutionCode(String resolutionCode) {
    this.resolutionCode = resolutionCode;
  }

  /** 读取resolutionSummary。 */
  public String getResolutionSummary() {
    return resolutionSummary;
  }

  /** 设置resolutionSummary。 */
  public void setResolutionSummary(String resolutionSummary) {
    this.resolutionSummary = resolutionSummary;
  }

  /** 读取closureChecklistJson。 */
  public String getClosureChecklistJson() {
    return closureChecklistJson;
  }

  /** 设置closureChecklistJson。 */
  public void setClosureChecklistJson(String closureChecklistJson) {
    this.closureChecklistJson = closureChecklistJson;
  }

  /** 读取closedAt。 */
  public LocalDateTime getClosedAt() {
    return closedAt;
  }

  /** 设置closedAt。 */
  public void setClosedAt(LocalDateTime closedAt) {
    this.closedAt = closedAt;
  }

  /** 读取closedByAdminUserId。 */
  public String getClosedByAdminUserId() {
    return closedByAdminUserId;
  }

  /** 设置closedByAdminUserId。 */
  public void setClosedByAdminUserId(String closedByAdminUserId) {
    this.closedByAdminUserId = closedByAdminUserId;
  }

  /** 读取version。 */
  public Integer getVersion() {
    return version;
  }

  /** 设置version。 */
  public void setVersion(Integer version) {
    this.version = version;
  }

  /** 读取createdByType。 */
  public String getCreatedByType() {
    return createdByType;
  }

  /** 设置createdByType。 */
  public void setCreatedByType(String createdByType) {
    this.createdByType = createdByType;
  }

  /** 读取createdByAdminUserId。 */
  public String getCreatedByAdminUserId() {
    return createdByAdminUserId;
  }

  /** 设置createdByAdminUserId。 */
  public void setCreatedByAdminUserId(String createdByAdminUserId) {
    this.createdByAdminUserId = createdByAdminUserId;
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
  public AdminCaseDO id(String id) {
    this.id = id;
    return this;
  }

  /** 组装caseNo。 */
  public AdminCaseDO caseNo(String caseNo) {
    this.caseNo = caseNo;
    return this;
  }

  /** 组装activeCaseKey。 */
  public AdminCaseDO activeCaseKey(String activeCaseKey) {
    this.activeCaseKey = activeCaseKey;
    return this;
  }

  /** 组装caseType。 */
  public AdminCaseDO caseType(String caseType) {
    this.caseType = caseType;
    return this;
  }

  /** 组装caseStatus。 */
  public AdminCaseDO caseStatus(String caseStatus) {
    this.caseStatus = caseStatus;
    return this;
  }

  /** 组装priority。 */
  public AdminCaseDO priority(String priority) {
    this.priority = priority;
    return this;
  }

  /** 组装title。 */
  public AdminCaseDO title(String title) {
    this.title = title;
    return this;
  }

  /** 组装summary。 */
  public AdminCaseDO summary(String summary) {
    this.summary = summary;
    return this;
  }

  /** 组装rentalOrderId。 */
  public AdminCaseDO rentalOrderId(String rentalOrderId) {
    this.rentalOrderId = rentalOrderId;
    return this;
  }

  /** 组装buyerUserId。 */
  public AdminCaseDO buyerUserId(String buyerUserId) {
    this.buyerUserId = buyerUserId;
    return this;
  }

  /** 组装factoryId。 */
  public AdminCaseDO factoryId(String factoryId) {
    this.factoryId = factoryId;
    return this;
  }

  /** 组装deadlineAt。 */
  public AdminCaseDO deadlineAt(LocalDateTime deadlineAt) {
    this.deadlineAt = deadlineAt;
    return this;
  }

  /** 组装resolutionCode。 */
  public AdminCaseDO resolutionCode(String resolutionCode) {
    this.resolutionCode = resolutionCode;
    return this;
  }

  /** 组装resolutionSummary。 */
  public AdminCaseDO resolutionSummary(String resolutionSummary) {
    this.resolutionSummary = resolutionSummary;
    return this;
  }

  /** 组装closureChecklistJson。 */
  public AdminCaseDO closureChecklistJson(String closureChecklistJson) {
    this.closureChecklistJson = closureChecklistJson;
    return this;
  }

  /** 组装closedAt。 */
  public AdminCaseDO closedAt(LocalDateTime closedAt) {
    this.closedAt = closedAt;
    return this;
  }

  /** 组装closedByAdminUserId。 */
  public AdminCaseDO closedByAdminUserId(String closedByAdminUserId) {
    this.closedByAdminUserId = closedByAdminUserId;
    return this;
  }

  /** 组装version。 */
  public AdminCaseDO version(Integer version) {
    this.version = version;
    return this;
  }

  /** 组装createdByType。 */
  public AdminCaseDO createdByType(String createdByType) {
    this.createdByType = createdByType;
    return this;
  }

  /** 组装createdByAdminUserId。 */
  public AdminCaseDO createdByAdminUserId(String createdByAdminUserId) {
    this.createdByAdminUserId = createdByAdminUserId;
    return this;
  }

  /** 组装createdAt。 */
  public AdminCaseDO createdAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /** 组装updatedAt。 */
  public AdminCaseDO updatedAt(LocalDateTime updatedAt) {
    this.updatedAt = updatedAt;
    return this;
  }
}
