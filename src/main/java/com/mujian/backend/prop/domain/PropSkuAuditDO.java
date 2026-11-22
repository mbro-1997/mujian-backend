package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** prop_sku_audit：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropSkuAuditDO extends BaseDO {

  /** SKU ID */
  private String skuId;

  /** 申请人用户 ID */
  private String applicantUserId;

  /** 申请动作 */
  private String actionType;

  /** 申请前 SKU 状态 */
  private String beforeSkuStatus;

  /** 审核通过后的目标 SKU 状态 */
  private String targetSkuStatus;

  /** 提交前 SKU 快照 */
  private String beforeSnapshotJson;

  /** 拟生效 SKU 快照 */
  private String proposedSnapshotJson;

  /** A 类初始实物件数 */
  private Integer initialSnQuantity;

  /** B 类初始库存数量 */
  private Integer initialStockQuantity;

  /** 审核状态 */
  private String auditStatus;

  /** 待审核 SKU 唯一守卫 */
  private String pendingSkuGuard;

  /** 申请说明 */
  private String applyRemark;

  /** 审核备注 */
  private String auditRemark;

  /** 驳回原因 */
  private String rejectReason;

  /** 审核管理员 ID */
  private String reviewerId;

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

  /** 上一驳回或撤回申请 */
  private String previousAuditId;

  /** 提交时SKU资料版本 */
  private Integer baseSkuVersion;

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

  /** 读取申请人用户 ID，供持久化映射使用。 */
  public String getApplicantUserId() {
    return applicantUserId;
  }

  /** 接收申请人用户 ID，保持持久化映射的字段含义。 */
  public void setApplicantUserId(String value) {
    applicantUserId = value;
  }

  /** 读取申请动作，供持久化映射使用。 */
  public String getActionType() {
    return actionType;
  }

  /** 接收申请动作，保持持久化映射的字段含义。 */
  public void setActionType(String value) {
    actionType = value;
  }

  /** 读取申请前 SKU 状态，供持久化映射使用。 */
  public String getBeforeSkuStatus() {
    return beforeSkuStatus;
  }

  /** 接收申请前 SKU 状态，保持持久化映射的字段含义。 */
  public void setBeforeSkuStatus(String value) {
    beforeSkuStatus = value;
  }

  /** 读取审核通过后的目标 SKU 状态，供持久化映射使用。 */
  public String getTargetSkuStatus() {
    return targetSkuStatus;
  }

  /** 接收审核通过后的目标 SKU 状态，保持持久化映射的字段含义。 */
  public void setTargetSkuStatus(String value) {
    targetSkuStatus = value;
  }

  /** 读取提交前 SKU 快照，供持久化映射使用。 */
  public String getBeforeSnapshotJson() {
    return beforeSnapshotJson;
  }

  /** 接收提交前 SKU 快照，保持持久化映射的字段含义。 */
  public void setBeforeSnapshotJson(String value) {
    beforeSnapshotJson = value;
  }

  /** 读取拟生效 SKU 快照，供持久化映射使用。 */
  public String getProposedSnapshotJson() {
    return proposedSnapshotJson;
  }

  /** 接收拟生效 SKU 快照，保持持久化映射的字段含义。 */
  public void setProposedSnapshotJson(String value) {
    proposedSnapshotJson = value;
  }

  /** 读取A 类初始实物件数，供持久化映射使用。 */
  public Integer getInitialSnQuantity() {
    return initialSnQuantity;
  }

  /** 接收A 类初始实物件数，保持持久化映射的字段含义。 */
  public void setInitialSnQuantity(Integer value) {
    initialSnQuantity = value;
  }

  /** 读取B 类初始库存数量，供持久化映射使用。 */
  public Integer getInitialStockQuantity() {
    return initialStockQuantity;
  }

  /** 接收B 类初始库存数量，保持持久化映射的字段含义。 */
  public void setInitialStockQuantity(Integer value) {
    initialStockQuantity = value;
  }

  /** 读取审核状态，供持久化映射使用。 */
  public String getAuditStatus() {
    return auditStatus;
  }

  /** 接收审核状态，保持持久化映射的字段含义。 */
  public void setAuditStatus(String value) {
    auditStatus = value;
  }

  /** 读取待审核 SKU 唯一守卫，供持久化映射使用。 */
  public String getPendingSkuGuard() {
    return pendingSkuGuard;
  }

  /** 接收待审核 SKU 唯一守卫，保持持久化映射的字段含义。 */
  public void setPendingSkuGuard(String value) {
    pendingSkuGuard = value;
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

  /** 读取审核管理员 ID，供持久化映射使用。 */
  public String getReviewerId() {
    return reviewerId;
  }

  /** 接收审核管理员 ID，保持持久化映射的字段含义。 */
  public void setReviewerId(String value) {
    reviewerId = value;
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

  /** 读取上一驳回或撤回申请，供持久化映射使用。 */
  public String getPreviousAuditId() {
    return previousAuditId;
  }

  /** 接收上一驳回或撤回申请，保持持久化映射的字段含义。 */
  public void setPreviousAuditId(String value) {
    previousAuditId = value;
  }

  /** 读取提交时SKU资料版本，供持久化映射使用。 */
  public Integer getBaseSkuVersion() {
    return baseSkuVersion;
  }

  /** 接收提交时SKU资料版本，保持持久化映射的字段含义。 */
  public void setBaseSkuVersion(Integer value) {
    baseSkuVersion = value;
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
  public PropSkuAuditDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置SKU ID并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置申请人用户 ID并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO applicantUserId(String value) {
    setApplicantUserId(value);
    return this;
  }

  /** 设置申请动作并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO actionType(String value) {
    setActionType(value);
    return this;
  }

  /** 设置申请前 SKU 状态并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO beforeSkuStatus(String value) {
    setBeforeSkuStatus(value);
    return this;
  }

  /** 设置审核通过后的目标 SKU 状态并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO targetSkuStatus(String value) {
    setTargetSkuStatus(value);
    return this;
  }

  /** 设置提交前 SKU 快照并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO beforeSnapshotJson(String value) {
    setBeforeSnapshotJson(value);
    return this;
  }

  /** 设置拟生效 SKU 快照并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO proposedSnapshotJson(String value) {
    setProposedSnapshotJson(value);
    return this;
  }

  /** 设置A 类初始实物件数并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO initialSnQuantity(Integer value) {
    setInitialSnQuantity(value);
    return this;
  }

  /** 设置B 类初始库存数量并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO initialStockQuantity(Integer value) {
    setInitialStockQuantity(value);
    return this;
  }

  /** 设置审核状态并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO auditStatus(String value) {
    setAuditStatus(value);
    return this;
  }

  /** 设置待审核 SKU 唯一守卫并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO pendingSkuGuard(String value) {
    setPendingSkuGuard(value);
    return this;
  }

  /** 设置申请说明并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO applyRemark(String value) {
    setApplyRemark(value);
    return this;
  }

  /** 设置审核备注并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO auditRemark(String value) {
    setAuditRemark(value);
    return this;
  }

  /** 设置驳回原因并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO rejectReason(String value) {
    setRejectReason(value);
    return this;
  }

  /** 设置审核管理员 ID并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO reviewerId(String value) {
    setReviewerId(value);
    return this;
  }

  /** 设置提交时间并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO submittedAt(LocalDateTime value) {
    setSubmittedAt(value);
    return this;
  }

  /** 设置审核时间并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO reviewedAt(LocalDateTime value) {
    setReviewedAt(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }

  /** 设置提交请求编号并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO requestId(String value) {
    setRequestId(value);
    return this;
  }

  /** 设置提交摘要并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO requestHash(String value) {
    setRequestHash(value);
    return this;
  }

  /** 设置上一驳回或撤回申请并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO previousAuditId(String value) {
    setPreviousAuditId(value);
    return this;
  }

  /** 设置提交时SKU资料版本并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO baseSkuVersion(Integer value) {
    setBaseSkuVersion(value);
    return this;
  }

  /** 设置更新时间并返回当前对象，供连续组装数据。 */
  public PropSkuAuditDO updatedAt(LocalDateTime value) {
    setUpdatedAt(value);
    return this;
  }
}
