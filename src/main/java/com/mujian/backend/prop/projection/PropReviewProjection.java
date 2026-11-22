package com.mujian.backend.prop.projection;

/** 审核业务的四种固定来源投影；具体分支在PropReviewConverter显式映射。 */
public class PropReviewProjection {
  private String id;

  private String skuId;

  private String applicantUserId;

  private String actionType;

  private String beforeSkuStatus;

  private String targetSkuStatus;

  private String beforeSnapshotJson;

  private String proposedSnapshotJson;

  private Integer initialSnQuantity;

  private Integer initialStockQuantity;

  private String auditStatus;

  private String pendingSkuGuard;

  private String applyRemark;

  private String auditRemark;

  private String rejectReason;

  private String reviewerId;

  private java.time.LocalDateTime submittedAt;

  private java.time.LocalDateTime reviewedAt;

  private Integer version;

  private java.time.LocalDateTime createdAt;

  private String requestId;

  private String requestHash;

  private String previousAuditId;

  private Integer baseSkuVersion;

  private java.time.LocalDateTime updatedAt;

  private String factoryId;

  private String adjustmentType;

  private String increaseScene;

  private Integer requestedQuantity;

  private String relatedSkuAuditId;

  private String assetType;

  private Integer quantityDelta;

  private String abnormalResult;

  private Integer beforeTotalQuantity;

  private Integer beforeAvailableQuantity;

  private String reason;

  private String relatedMovementId;

  private String exceptionNo;

  private String snId;

  private String originalQrCodeId;

  private String reportedByUserId;

  private String exceptionType;

  private String description;

  private String evidenceUrlsJson;

  private String exceptionStatus;

  private String activeOriginalQrGuard;

  private String resolutionType;

  private String replacementQrCodeId;

  private String resolvedByAdminUserId;

  private String resolutionReason;

  private java.time.LocalDateTime resolvedAt;

  /** 读取记录标识，供查询结果映射使用。 */
  public String getId() {
    return id;
  }

  /** 接收记录标识，保持查询结果映射的字段含义。 */
  public void setId(String value) {
    id = value;
  }

  /** 读取道具标识，供查询结果映射使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具标识，保持查询结果映射的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取申请人用户 ID，供查询结果映射使用。 */
  public String getApplicantUserId() {
    return applicantUserId;
  }

  /** 接收申请人用户 ID，保持查询结果映射的字段含义。 */
  public void setApplicantUserId(String value) {
    applicantUserId = value;
  }

  /** 读取申请动作，供查询结果映射使用。 */
  public String getActionType() {
    return actionType;
  }

  /** 接收申请动作，保持查询结果映射的字段含义。 */
  public void setActionType(String value) {
    actionType = value;
  }

  /** 读取申请前 SKU 状态，供查询结果映射使用。 */
  public String getBeforeSkuStatus() {
    return beforeSkuStatus;
  }

  /** 接收申请前 SKU 状态，保持查询结果映射的字段含义。 */
  public void setBeforeSkuStatus(String value) {
    beforeSkuStatus = value;
  }

  /** 读取审核通过后的目标 SKU 状态，供查询结果映射使用。 */
  public String getTargetSkuStatus() {
    return targetSkuStatus;
  }

  /** 接收审核通过后的目标 SKU 状态，保持查询结果映射的字段含义。 */
  public void setTargetSkuStatus(String value) {
    targetSkuStatus = value;
  }

  /** 读取提交前 SKU 快照，供查询结果映射使用。 */
  public String getBeforeSnapshotJson() {
    return beforeSnapshotJson;
  }

  /** 接收提交前 SKU 快照，保持查询结果映射的字段含义。 */
  public void setBeforeSnapshotJson(String value) {
    beforeSnapshotJson = value;
  }

  /** 读取拟生效 SKU 快照，供查询结果映射使用。 */
  public String getProposedSnapshotJson() {
    return proposedSnapshotJson;
  }

  /** 接收拟生效 SKU 快照，保持查询结果映射的字段含义。 */
  public void setProposedSnapshotJson(String value) {
    proposedSnapshotJson = value;
  }

  /** 读取A 类初始实物件数，供查询结果映射使用。 */
  public Integer getInitialSnQuantity() {
    return initialSnQuantity;
  }

  /** 接收A 类初始实物件数，保持查询结果映射的字段含义。 */
  public void setInitialSnQuantity(Integer value) {
    initialSnQuantity = value;
  }

  /** 读取B 类初始库存数量，供查询结果映射使用。 */
  public Integer getInitialStockQuantity() {
    return initialStockQuantity;
  }

  /** 接收B 类初始库存数量，保持查询结果映射的字段含义。 */
  public void setInitialStockQuantity(Integer value) {
    initialStockQuantity = value;
  }

  /** 读取审核状态，供查询结果映射使用。 */
  public String getAuditStatus() {
    return auditStatus;
  }

  /** 接收审核状态，保持查询结果映射的字段含义。 */
  public void setAuditStatus(String value) {
    auditStatus = value;
  }

  /** 读取待审核 SKU 唯一守卫，供查询结果映射使用。 */
  public String getPendingSkuGuard() {
    return pendingSkuGuard;
  }

  /** 接收待审核 SKU 唯一守卫，保持查询结果映射的字段含义。 */
  public void setPendingSkuGuard(String value) {
    pendingSkuGuard = value;
  }

  /** 读取申请说明，供查询结果映射使用。 */
  public String getApplyRemark() {
    return applyRemark;
  }

  /** 接收申请说明，保持查询结果映射的字段含义。 */
  public void setApplyRemark(String value) {
    applyRemark = value;
  }

  /** 读取审核备注，供查询结果映射使用。 */
  public String getAuditRemark() {
    return auditRemark;
  }

  /** 接收审核备注，保持查询结果映射的字段含义。 */
  public void setAuditRemark(String value) {
    auditRemark = value;
  }

  /** 读取驳回原因，供查询结果映射使用。 */
  public String getRejectReason() {
    return rejectReason;
  }

  /** 接收驳回原因，保持查询结果映射的字段含义。 */
  public void setRejectReason(String value) {
    rejectReason = value;
  }

  /** 读取审核管理员 ID，供查询结果映射使用。 */
  public String getReviewerId() {
    return reviewerId;
  }

  /** 接收审核管理员 ID，保持查询结果映射的字段含义。 */
  public void setReviewerId(String value) {
    reviewerId = value;
  }

  /** 读取提交时间，供查询结果映射使用。 */
  public java.time.LocalDateTime getSubmittedAt() {
    return submittedAt;
  }

  /** 接收提交时间，保持查询结果映射的字段含义。 */
  public void setSubmittedAt(java.time.LocalDateTime value) {
    submittedAt = value;
  }

  /** 读取审核时间，供查询结果映射使用。 */
  public java.time.LocalDateTime getReviewedAt() {
    return reviewedAt;
  }

  /** 接收审核时间，保持查询结果映射的字段含义。 */
  public void setReviewedAt(java.time.LocalDateTime value) {
    reviewedAt = value;
  }

  /** 读取乐观锁版本，供查询结果映射使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持查询结果映射的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取创建时间，供查询结果映射使用。 */
  public java.time.LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 接收创建时间，保持查询结果映射的字段含义。 */
  public void setCreatedAt(java.time.LocalDateTime value) {
    createdAt = value;
  }

  /** 读取请求防重编号，供查询结果映射使用。 */
  public String getRequestId() {
    return requestId;
  }

  /** 接收请求防重编号，保持查询结果映射的字段含义。 */
  public void setRequestId(String value) {
    requestId = value;
  }

  /** 读取登记摘要，供查询结果映射使用。 */
  public String getRequestHash() {
    return requestHash;
  }

  /** 接收登记摘要，保持查询结果映射的字段含义。 */
  public void setRequestHash(String value) {
    requestHash = value;
  }

  /** 读取前次审核记录标识，供查询结果映射使用。 */
  public String getPreviousAuditId() {
    return previousAuditId;
  }

  /** 接收前次审核记录标识，保持查询结果映射的字段含义。 */
  public void setPreviousAuditId(String value) {
    previousAuditId = value;
  }

  /** 读取提交时SKU资料版本，供查询结果映射使用。 */
  public Integer getBaseSkuVersion() {
    return baseSkuVersion;
  }

  /** 接收提交时SKU资料版本，保持查询结果映射的字段含义。 */
  public void setBaseSkuVersion(Integer value) {
    baseSkuVersion = value;
  }

  /** 读取更新时间，供查询结果映射使用。 */
  public java.time.LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  /** 接收更新时间，保持查询结果映射的字段含义。 */
  public void setUpdatedAt(java.time.LocalDateTime value) {
    updatedAt = value;
  }

  /** 读取工厂标识，供查询结果映射使用。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 接收工厂标识，保持查询结果映射的字段含义。 */
  public void setFactoryId(String value) {
    factoryId = value;
  }

  /** 读取调整类型，供查询结果映射使用。 */
  public String getAdjustmentType() {
    return adjustmentType;
  }

  /** 接收调整类型，保持查询结果映射的字段含义。 */
  public void setAdjustmentType(String value) {
    adjustmentType = value;
  }

  /** 读取增加场景，供查询结果映射使用。 */
  public String getIncreaseScene() {
    return increaseScene;
  }

  /** 接收增加场景，保持查询结果映射的字段含义。 */
  public void setIncreaseScene(String value) {
    increaseScene = value;
  }

  /** 读取申请涉及数量，供查询结果映射使用。 */
  public Integer getRequestedQuantity() {
    return requestedQuantity;
  }

  /** 接收申请涉及数量，保持查询结果映射的字段含义。 */
  public void setRequestedQuantity(Integer value) {
    requestedQuantity = value;
  }

  /** 读取关联道具审核记录标识，供查询结果映射使用。 */
  public String getRelatedSkuAuditId() {
    return relatedSkuAuditId;
  }

  /** 接收关联道具审核记录标识，保持查询结果映射的字段含义。 */
  public void setRelatedSkuAuditId(String value) {
    relatedSkuAuditId = value;
  }

  /** 读取资产类型，供查询结果映射使用。 */
  public String getAssetType() {
    return assetType;
  }

  /** 接收资产类型，保持查询结果映射的字段含义。 */
  public void setAssetType(String value) {
    assetType = value;
  }

  /** 读取变化数量，供查询结果映射使用。 */
  public Integer getQuantityDelta() {
    return quantityDelta;
  }

  /** 接收变化数量，保持查询结果映射的字段含义。 */
  public void setQuantityDelta(Integer value) {
    quantityDelta = value;
  }

  /** 读取异常扣减结果，供查询结果映射使用。 */
  public String getAbnormalResult() {
    return abnormalResult;
  }

  /** 接收异常扣减结果，保持查询结果映射的字段含义。 */
  public void setAbnormalResult(String value) {
    abnormalResult = value;
  }

  /** 读取调整前总数量，供查询结果映射使用。 */
  public Integer getBeforeTotalQuantity() {
    return beforeTotalQuantity;
  }

  /** 接收调整前总数量，保持查询结果映射的字段含义。 */
  public void setBeforeTotalQuantity(Integer value) {
    beforeTotalQuantity = value;
  }

  /** 读取调整前可用数量，供查询结果映射使用。 */
  public Integer getBeforeAvailableQuantity() {
    return beforeAvailableQuantity;
  }

  /** 接收调整前可用数量，保持查询结果映射的字段含义。 */
  public void setBeforeAvailableQuantity(Integer value) {
    beforeAvailableQuantity = value;
  }

  /** 读取业务操作原因，供查询结果映射使用。 */
  public String getReason() {
    return reason;
  }

  /** 接收业务操作原因，保持查询结果映射的字段含义。 */
  public void setReason(String value) {
    reason = value;
  }

  /** 读取关联库存流水 ID，供查询结果映射使用。 */
  public String getRelatedMovementId() {
    return relatedMovementId;
  }

  /** 接收关联库存流水 ID，保持查询结果映射的字段含义。 */
  public void setRelatedMovementId(String value) {
    relatedMovementId = value;
  }

  /** 读取异常编号，供查询结果映射使用。 */
  public String getExceptionNo() {
    return exceptionNo;
  }

  /** 接收异常编号，保持查询结果映射的字段含义。 */
  public void setExceptionNo(String value) {
    exceptionNo = value;
  }

  /** 读取SN ID，供查询结果映射使用。 */
  public String getSnId() {
    return snId;
  }

  /** 接收SN ID，保持查询结果映射的字段含义。 */
  public void setSnId(String value) {
    snId = value;
  }

  /** 读取原二维码记录 ID，供查询结果映射使用。 */
  public String getOriginalQrCodeId() {
    return originalQrCodeId;
  }

  /** 接收原二维码记录 ID，保持查询结果映射的字段含义。 */
  public void setOriginalQrCodeId(String value) {
    originalQrCodeId = value;
  }

  /** 读取报告人用户 ID，供查询结果映射使用。 */
  public String getReportedByUserId() {
    return reportedByUserId;
  }

  /** 接收报告人用户 ID，保持查询结果映射的字段含义。 */
  public void setReportedByUserId(String value) {
    reportedByUserId = value;
  }

  /** 读取异常类型，供查询结果映射使用。 */
  public String getExceptionType() {
    return exceptionType;
  }

  /** 接收异常类型，保持查询结果映射的字段含义。 */
  public void setExceptionType(String value) {
    exceptionType = value;
  }

  /** 读取异常说明，供查询结果映射使用。 */
  public String getDescription() {
    return description;
  }

  /** 接收异常说明，保持查询结果映射的字段含义。 */
  public void setDescription(String value) {
    description = value;
  }

  /** 读取证据对象键，供查询结果映射使用。 */
  public String getEvidenceUrlsJson() {
    return evidenceUrlsJson;
  }

  /** 接收证据对象键，保持查询结果映射的字段含义。 */
  public void setEvidenceUrlsJson(String value) {
    evidenceUrlsJson = value;
  }

  /** 读取异常状态，供查询结果映射使用。 */
  public String getExceptionStatus() {
    return exceptionStatus;
  }

  /** 接收异常状态，保持查询结果映射的字段含义。 */
  public void setExceptionStatus(String value) {
    exceptionStatus = value;
  }

  /** 读取活动异常原码守卫，供查询结果映射使用。 */
  public String getActiveOriginalQrGuard() {
    return activeOriginalQrGuard;
  }

  /** 接收活动异常原码守卫，保持查询结果映射的字段含义。 */
  public void setActiveOriginalQrGuard(String value) {
    activeOriginalQrGuard = value;
  }

  /** 读取处理类型，供查询结果映射使用。 */
  public String getResolutionType() {
    return resolutionType;
  }

  /** 接收处理类型，保持查询结果映射的字段含义。 */
  public void setResolutionType(String value) {
    resolutionType = value;
  }

  /** 读取补发二维码 ID，供查询结果映射使用。 */
  public String getReplacementQrCodeId() {
    return replacementQrCodeId;
  }

  /** 接收补发二维码 ID，保持查询结果映射的字段含义。 */
  public void setReplacementQrCodeId(String value) {
    replacementQrCodeId = value;
  }

  /** 读取处理管理员用户 ID，供查询结果映射使用。 */
  public String getResolvedByAdminUserId() {
    return resolvedByAdminUserId;
  }

  /** 接收处理管理员用户 ID，保持查询结果映射的字段含义。 */
  public void setResolvedByAdminUserId(String value) {
    resolvedByAdminUserId = value;
  }

  /** 读取处理理由，供查询结果映射使用。 */
  public String getResolutionReason() {
    return resolutionReason;
  }

  /** 接收处理理由，保持查询结果映射的字段含义。 */
  public void setResolutionReason(String value) {
    resolutionReason = value;
  }

  /** 读取处理时间，供查询结果映射使用。 */
  public java.time.LocalDateTime getResolvedAt() {
    return resolvedAt;
  }

  /** 接收处理时间，保持查询结果映射的字段含义。 */
  public void setResolvedAt(java.time.LocalDateTime value) {
    resolvedAt = value;
  }
}
