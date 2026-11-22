package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** prop_stock_adjustment_audit：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropStockAdjustmentAuditDO extends BaseDO {

  /** SKU ID */
  private String skuId;

  /** 资产类型 */
  private String assetType;

  /** 调整类型 */
  private String adjustmentType;

  /** 变化数量 */
  private Integer quantityDelta;

  /** 异常扣减结果 */
  private String abnormalResult;

  /** 调整前总数量 */
  private Integer beforeTotalQuantity;

  /** 调整前可用数量 */
  private Integer beforeAvailableQuantity;

  /** 申请原因 */
  private String reason;

  /** 审核状态 */
  private String auditStatus;

  /** 申请人用户 ID */
  private String applicantUserId;

  /** 审核管理员 ID */
  private String reviewerId;

  /** 审核备注 */
  private String auditRemark;

  /** 驳回原因 */
  private String rejectReason;

  /** 关联库存流水 ID */
  private String relatedMovementId;

  /** 来源 SKU 审核 ID */
  private String relatedSkuAuditId;

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

  /** 读取资产类型，供持久化映射使用。 */
  public String getAssetType() {
    return assetType;
  }

  /** 接收资产类型，保持持久化映射的字段含义。 */
  public void setAssetType(String value) {
    assetType = value;
  }

  /** 读取调整类型，供持久化映射使用。 */
  public String getAdjustmentType() {
    return adjustmentType;
  }

  /** 接收调整类型，保持持久化映射的字段含义。 */
  public void setAdjustmentType(String value) {
    adjustmentType = value;
  }

  /** 读取变化数量，供持久化映射使用。 */
  public Integer getQuantityDelta() {
    return quantityDelta;
  }

  /** 接收变化数量，保持持久化映射的字段含义。 */
  public void setQuantityDelta(Integer value) {
    quantityDelta = value;
  }

  /** 读取异常扣减结果，供持久化映射使用。 */
  public String getAbnormalResult() {
    return abnormalResult;
  }

  /** 接收异常扣减结果，保持持久化映射的字段含义。 */
  public void setAbnormalResult(String value) {
    abnormalResult = value;
  }

  /** 读取调整前总数量，供持久化映射使用。 */
  public Integer getBeforeTotalQuantity() {
    return beforeTotalQuantity;
  }

  /** 接收调整前总数量，保持持久化映射的字段含义。 */
  public void setBeforeTotalQuantity(Integer value) {
    beforeTotalQuantity = value;
  }

  /** 读取调整前可用数量，供持久化映射使用。 */
  public Integer getBeforeAvailableQuantity() {
    return beforeAvailableQuantity;
  }

  /** 接收调整前可用数量，保持持久化映射的字段含义。 */
  public void setBeforeAvailableQuantity(Integer value) {
    beforeAvailableQuantity = value;
  }

  /** 读取申请原因，供持久化映射使用。 */
  public String getReason() {
    return reason;
  }

  /** 接收申请原因，保持持久化映射的字段含义。 */
  public void setReason(String value) {
    reason = value;
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

  /** 读取关联库存流水 ID，供持久化映射使用。 */
  public String getRelatedMovementId() {
    return relatedMovementId;
  }

  /** 接收关联库存流水 ID，保持持久化映射的字段含义。 */
  public void setRelatedMovementId(String value) {
    relatedMovementId = value;
  }

  /** 读取来源 SKU 审核 ID，供持久化映射使用。 */
  public String getRelatedSkuAuditId() {
    return relatedSkuAuditId;
  }

  /** 接收来源 SKU 审核 ID，保持持久化映射的字段含义。 */
  public void setRelatedSkuAuditId(String value) {
    relatedSkuAuditId = value;
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

  /** 读取更新时间，供持久化映射使用。 */
  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  /** 接收更新时间，保持持久化映射的字段含义。 */
  public void setUpdatedAt(LocalDateTime value) {
    updatedAt = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置SKU ID并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置资产类型并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO assetType(String value) {
    setAssetType(value);
    return this;
  }

  /** 设置调整类型并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO adjustmentType(String value) {
    setAdjustmentType(value);
    return this;
  }

  /** 设置变化数量并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO quantityDelta(Integer value) {
    setQuantityDelta(value);
    return this;
  }

  /** 设置异常扣减结果并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO abnormalResult(String value) {
    setAbnormalResult(value);
    return this;
  }

  /** 设置调整前总数量并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO beforeTotalQuantity(Integer value) {
    setBeforeTotalQuantity(value);
    return this;
  }

  /** 设置调整前可用数量并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO beforeAvailableQuantity(Integer value) {
    setBeforeAvailableQuantity(value);
    return this;
  }

  /** 设置申请原因并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO reason(String value) {
    setReason(value);
    return this;
  }

  /** 设置审核状态并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO auditStatus(String value) {
    setAuditStatus(value);
    return this;
  }

  /** 设置申请人用户 ID并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO applicantUserId(String value) {
    setApplicantUserId(value);
    return this;
  }

  /** 设置审核管理员 ID并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO reviewerId(String value) {
    setReviewerId(value);
    return this;
  }

  /** 设置审核备注并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO auditRemark(String value) {
    setAuditRemark(value);
    return this;
  }

  /** 设置驳回原因并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO rejectReason(String value) {
    setRejectReason(value);
    return this;
  }

  /** 设置关联库存流水 ID并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO relatedMovementId(String value) {
    setRelatedMovementId(value);
    return this;
  }

  /** 设置来源 SKU 审核 ID并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO relatedSkuAuditId(String value) {
    setRelatedSkuAuditId(value);
    return this;
  }

  /** 设置提交时间并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO submittedAt(LocalDateTime value) {
    setSubmittedAt(value);
    return this;
  }

  /** 设置审核时间并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO reviewedAt(LocalDateTime value) {
    setReviewedAt(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }

  /** 设置提交请求编号并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO requestId(String value) {
    setRequestId(value);
    return this;
  }

  /** 设置提交摘要并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO requestHash(String value) {
    setRequestHash(value);
    return this;
  }

  /** 设置更新时间并返回当前对象，供连续组装数据。 */
  public PropStockAdjustmentAuditDO updatedAt(LocalDateTime value) {
    setUpdatedAt(value);
    return this;
  }
}
