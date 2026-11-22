package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** prop_sn_adjustment_audit：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropSnAdjustmentAuditDO extends BaseDO {

  /** SKU ID */
  private String skuId;

  /** 工厂 ID */
  private String factoryId;

  /** 调整类型 */
  private String adjustmentType;

  /** 增加场景 */
  private String increaseScene;

  /** 变化件数 */
  private Integer requestedQuantity;

  /** 审核状态 */
  private String auditStatus;

  /** 申请人用户 ID */
  private String applicantUserId;

  /** 审核管理员 ID */
  private String reviewerId;

  /** 申请说明 */
  private String applyRemark;

  /** 审核备注 */
  private String auditRemark;

  /** 驳回原因 */
  private String rejectReason;

  /** 提交时间 */
  private LocalDateTime submittedAt;

  /** 审核时间 */
  private LocalDateTime reviewedAt;

  /** 乐观锁版本 */
  private Integer version;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 提交请求编号 */
  private String requestId;

  /** 提交摘要 */
  private String requestHash;

  /** 首次来源SKU审核 */
  private String relatedSkuAuditId;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 读取SKU ID，供持久化映射使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收SKU ID，保持持久化映射的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取工厂 ID，供持久化映射使用。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 接收工厂 ID，保持持久化映射的字段含义。 */
  public void setFactoryId(String value) {
    factoryId = value;
  }

  /** 读取调整类型，供持久化映射使用。 */
  public String getAdjustmentType() {
    return adjustmentType;
  }

  /** 接收调整类型，保持持久化映射的字段含义。 */
  public void setAdjustmentType(String value) {
    adjustmentType = value;
  }

  /** 读取增加场景，供持久化映射使用。 */
  public String getIncreaseScene() {
    return increaseScene;
  }

  /** 接收增加场景，保持持久化映射的字段含义。 */
  public void setIncreaseScene(String value) {
    increaseScene = value;
  }

  /** 读取变化件数，供持久化映射使用。 */
  public Integer getRequestedQuantity() {
    return requestedQuantity;
  }

  /** 接收变化件数，保持持久化映射的字段含义。 */
  public void setRequestedQuantity(Integer value) {
    requestedQuantity = value;
  }

  /** 读取审核状态，供持久化映射使用。 */
  public String getAuditStatus() {
    return auditStatus;
  }

  /** 接收审核状态，保持持久化映射的字段含义。 */
  public void setAuditStatus(String value) {
    auditStatus = value;
  }

  /** 读取申请人用户 ID，供持久化映射使用。 */
  public String getApplicantUserId() {
    return applicantUserId;
  }

  /** 接收申请人用户 ID，保持持久化映射的字段含义。 */
  public void setApplicantUserId(String value) {
    applicantUserId = value;
  }

  /** 读取审核管理员 ID，供持久化映射使用。 */
  public String getReviewerId() {
    return reviewerId;
  }

  /** 接收审核管理员 ID，保持持久化映射的字段含义。 */
  public void setReviewerId(String value) {
    reviewerId = value;
  }

  /** 读取申请说明，供持久化映射使用。 */
  public String getApplyRemark() {
    return applyRemark;
  }

  /** 接收申请说明，保持持久化映射的字段含义。 */
  public void setApplyRemark(String value) {
    applyRemark = value;
  }

  /** 读取审核备注，供持久化映射使用。 */
  public String getAuditRemark() {
    return auditRemark;
  }

  /** 接收审核备注，保持持久化映射的字段含义。 */
  public void setAuditRemark(String value) {
    auditRemark = value;
  }

  /** 读取驳回原因，供持久化映射使用。 */
  public String getRejectReason() {
    return rejectReason;
  }

  /** 接收驳回原因，保持持久化映射的字段含义。 */
  public void setRejectReason(String value) {
    rejectReason = value;
  }

  /** 读取提交时间，供持久化映射使用。 */
  public LocalDateTime getSubmittedAt() {
    return submittedAt;
  }

  /** 接收提交时间，保持持久化映射的字段含义。 */
  public void setSubmittedAt(LocalDateTime value) {
    submittedAt = value;
  }

  /** 读取审核时间，供持久化映射使用。 */
  public LocalDateTime getReviewedAt() {
    return reviewedAt;
  }

  /** 接收审核时间，保持持久化映射的字段含义。 */
  public void setReviewedAt(LocalDateTime value) {
    reviewedAt = value;
  }

  /** 读取乐观锁版本，供持久化映射使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持持久化映射的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取创建时间，供持久化映射使用。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 接收创建时间，保持持久化映射的字段含义。 */
  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  /** 读取提交请求编号，供持久化映射使用。 */
  public String getRequestId() {
    return requestId;
  }

  /** 接收提交请求编号，保持持久化映射的字段含义。 */
  public void setRequestId(String value) {
    requestId = value;
  }

  /** 读取提交摘要，供持久化映射使用。 */
  public String getRequestHash() {
    return requestHash;
  }

  /** 接收提交摘要，保持持久化映射的字段含义。 */
  public void setRequestHash(String value) {
    requestHash = value;
  }

  /** 读取首次来源SKU审核，供持久化映射使用。 */
  public String getRelatedSkuAuditId() {
    return relatedSkuAuditId;
  }

  /** 接收首次来源SKU审核，保持持久化映射的字段含义。 */
  public void setRelatedSkuAuditId(String value) {
    relatedSkuAuditId = value;
  }

  /** 读取更新时间，供持久化映射使用。 */
  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  /** 接收更新时间，保持持久化映射的字段含义。 */
  public void setUpdatedAt(LocalDateTime value) {
    updatedAt = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置SKU ID并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置工厂 ID并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO factoryId(String value) {
    setFactoryId(value);
    return this;
  }

  /** 设置调整类型并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO adjustmentType(String value) {
    setAdjustmentType(value);
    return this;
  }

  /** 设置增加场景并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO increaseScene(String value) {
    setIncreaseScene(value);
    return this;
  }

  /** 设置变化件数并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO requestedQuantity(Integer value) {
    setRequestedQuantity(value);
    return this;
  }

  /** 设置审核状态并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO auditStatus(String value) {
    setAuditStatus(value);
    return this;
  }

  /** 设置申请人用户 ID并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO applicantUserId(String value) {
    setApplicantUserId(value);
    return this;
  }

  /** 设置审核管理员 ID并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO reviewerId(String value) {
    setReviewerId(value);
    return this;
  }

  /** 设置申请说明并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO applyRemark(String value) {
    setApplyRemark(value);
    return this;
  }

  /** 设置审核备注并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO auditRemark(String value) {
    setAuditRemark(value);
    return this;
  }

  /** 设置驳回原因并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO rejectReason(String value) {
    setRejectReason(value);
    return this;
  }

  /** 设置提交时间并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO submittedAt(LocalDateTime value) {
    setSubmittedAt(value);
    return this;
  }

  /** 设置审核时间并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO reviewedAt(LocalDateTime value) {
    setReviewedAt(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }

  /** 设置提交请求编号并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO requestId(String value) {
    setRequestId(value);
    return this;
  }

  /** 设置提交摘要并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO requestHash(String value) {
    setRequestHash(value);
    return this;
  }

  /** 设置首次来源SKU审核并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO relatedSkuAuditId(String value) {
    setRelatedSkuAuditId(value);
    return this;
  }

  /** 设置更新时间并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentAuditDO updatedAt(LocalDateTime value) {
    setUpdatedAt(value);
    return this;
  }
}
