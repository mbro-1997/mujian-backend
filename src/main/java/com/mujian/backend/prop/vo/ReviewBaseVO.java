package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 ReviewBase，不是数据库实体。 */
public abstract class ReviewBaseVO {
  private String id;

  private String skuId;

  private String factoryId;

  private String factoryName;

  private String applicantUserId;

  private String applicantName;

  private String auditStatus;

  private Integer version;

  private String applyRemark;

  private String auditRemark;

  private String rejectReason;

  private String submittedAt;

  private String reviewedAt;

  private String reviewerId;

  private String caseId;

  private Boolean canCancel;

  private Boolean canApprove;

  private Boolean canReject;

  private String approvalBlockReason;

  private String sourceType;

  private String assetType;

  private String skuCode;

  private String propName;

  private String currentSkuStatus;

  private Integer currentSkuVersion;

  /** 读取记录标识，供接口结果展示使用。 */
  public String getId() {
    return id;
  }

  /** 接收记录标识，保持接口结果展示的字段含义。 */
  public void setId(String value) {
    id = value;
  }

  /** 读取道具标识，供接口结果展示使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具标识，保持接口结果展示的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取工厂标识，供接口结果展示使用。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 接收工厂标识，保持接口结果展示的字段含义。 */
  public void setFactoryId(String value) {
    factoryId = value;
  }

  /** 读取factoryName，供接口结果展示使用。 */
  public String getFactoryName() {
    return factoryName;
  }

  /** 接收factoryName，保持接口结果展示的字段含义。 */
  public void setFactoryName(String value) {
    factoryName = value;
  }

  /** 读取申请人用户 ID，供接口结果展示使用。 */
  public String getApplicantUserId() {
    return applicantUserId;
  }

  /** 接收申请人用户 ID，保持接口结果展示的字段含义。 */
  public void setApplicantUserId(String value) {
    applicantUserId = value;
  }

  /** 读取applicantName，供接口结果展示使用。 */
  public String getApplicantName() {
    return applicantName;
  }

  /** 接收applicantName，保持接口结果展示的字段含义。 */
  public void setApplicantName(String value) {
    applicantName = value;
  }

  /** 读取审核状态，供接口结果展示使用。 */
  public String getAuditStatus() {
    return auditStatus;
  }

  /** 接收审核状态，保持接口结果展示的字段含义。 */
  public void setAuditStatus(String value) {
    auditStatus = value;
  }

  /** 读取乐观锁版本，供接口结果展示使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持接口结果展示的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取申请说明，供接口结果展示使用。 */
  public String getApplyRemark() {
    return applyRemark;
  }

  /** 接收申请说明，保持接口结果展示的字段含义。 */
  public void setApplyRemark(String value) {
    applyRemark = value;
  }

  /** 读取审核备注，供接口结果展示使用。 */
  public String getAuditRemark() {
    return auditRemark;
  }

  /** 接收审核备注，保持接口结果展示的字段含义。 */
  public void setAuditRemark(String value) {
    auditRemark = value;
  }

  /** 读取驳回原因，供接口结果展示使用。 */
  public String getRejectReason() {
    return rejectReason;
  }

  /** 接收驳回原因，保持接口结果展示的字段含义。 */
  public void setRejectReason(String value) {
    rejectReason = value;
  }

  /** 读取提交时间，供接口结果展示使用。 */
  public String getSubmittedAt() {
    return submittedAt;
  }

  /** 接收提交时间，保持接口结果展示的字段含义。 */
  public void setSubmittedAt(String value) {
    submittedAt = value;
  }

  /** 读取审核时间，供接口结果展示使用。 */
  public String getReviewedAt() {
    return reviewedAt;
  }

  /** 接收审核时间，保持接口结果展示的字段含义。 */
  public void setReviewedAt(String value) {
    reviewedAt = value;
  }

  /** 读取审核管理员 ID，供接口结果展示使用。 */
  public String getReviewerId() {
    return reviewerId;
  }

  /** 接收审核管理员 ID，保持接口结果展示的字段含义。 */
  public void setReviewerId(String value) {
    reviewerId = value;
  }

  /** 读取caseId，供接口结果展示使用。 */
  public String getCaseId() {
    return caseId;
  }

  /** 接收caseId，保持接口结果展示的字段含义。 */
  public void setCaseId(String value) {
    caseId = value;
  }

  /** 读取是否允许撤回，供接口结果展示使用。 */
  public Boolean getCanCancel() {
    return canCancel;
  }

  /** 接收是否允许撤回，保持接口结果展示的字段含义。 */
  public void setCanCancel(Boolean value) {
    canCancel = value;
  }

  /** 读取是否允许批准，供接口结果展示使用。 */
  public Boolean getCanApprove() {
    return canApprove;
  }

  /** 接收是否允许批准，保持接口结果展示的字段含义。 */
  public void setCanApprove(Boolean value) {
    canApprove = value;
  }

  /** 读取是否允许驳回，供接口结果展示使用。 */
  public Boolean getCanReject() {
    return canReject;
  }

  /** 接收是否允许驳回，保持接口结果展示的字段含义。 */
  public void setCanReject(Boolean value) {
    canReject = value;
  }

  /** 读取当前无法批准的原因，供接口结果展示使用。 */
  public String getApprovalBlockReason() {
    return approvalBlockReason;
  }

  /** 接收当前无法批准的原因，保持接口结果展示的字段含义。 */
  public void setApprovalBlockReason(String value) {
    approvalBlockReason = value;
  }

  /** 读取业务来源类型，供接口结果展示使用。 */
  public String getSourceType() {
    return sourceType;
  }

  /** 接收业务来源类型，保持接口结果展示的字段含义。 */
  public void setSourceType(String value) {
    sourceType = value;
  }

  /** 读取资产类型，供接口结果展示使用。 */
  public String getAssetType() {
    return assetType;
  }

  /** 接收资产类型，保持接口结果展示的字段含义。 */
  public void setAssetType(String value) {
    assetType = value;
  }

  /** 读取SKU 编码，供接口结果展示使用。 */
  public String getSkuCode() {
    return skuCode;
  }

  /** 接收SKU 编码，保持接口结果展示的字段含义。 */
  public void setSkuCode(String value) {
    skuCode = value;
  }

  /** 读取道具名称，供接口结果展示使用。 */
  public String getPropName() {
    return propName;
  }

  /** 接收道具名称，保持接口结果展示的字段含义。 */
  public void setPropName(String value) {
    propName = value;
  }

  /** 读取currentSkuStatus，供接口结果展示使用。 */
  public String getCurrentSkuStatus() {
    return currentSkuStatus;
  }

  /** 接收currentSkuStatus，保持接口结果展示的字段含义。 */
  public void setCurrentSkuStatus(String value) {
    currentSkuStatus = value;
  }

  /** 读取currentSkuVersion，供接口结果展示使用。 */
  public Integer getCurrentSkuVersion() {
    return currentSkuVersion;
  }

  /** 接收currentSkuVersion，保持接口结果展示的字段含义。 */
  public void setCurrentSkuVersion(Integer value) {
    currentSkuVersion = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO id(String value) {
    setId(value);
    return this;
  }

  /** 设置道具标识并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置工厂标识并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO factoryId(String value) {
    setFactoryId(value);
    return this;
  }

  /** 设置factoryName并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO factoryName(String value) {
    setFactoryName(value);
    return this;
  }

  /** 设置申请人用户 ID并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO applicantUserId(String value) {
    setApplicantUserId(value);
    return this;
  }

  /** 设置applicantName并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO applicantName(String value) {
    setApplicantName(value);
    return this;
  }

  /** 设置审核状态并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO auditStatus(String value) {
    setAuditStatus(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置申请说明并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO applyRemark(String value) {
    setApplyRemark(value);
    return this;
  }

  /** 设置审核备注并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO auditRemark(String value) {
    setAuditRemark(value);
    return this;
  }

  /** 设置驳回原因并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO rejectReason(String value) {
    setRejectReason(value);
    return this;
  }

  /** 设置提交时间并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO submittedAt(String value) {
    setSubmittedAt(value);
    return this;
  }

  /** 设置审核时间并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO reviewedAt(String value) {
    setReviewedAt(value);
    return this;
  }

  /** 设置审核管理员 ID并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO reviewerId(String value) {
    setReviewerId(value);
    return this;
  }

  /** 设置caseId并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO caseId(String value) {
    setCaseId(value);
    return this;
  }

  /** 设置是否允许撤回并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO canCancel(Boolean value) {
    setCanCancel(value);
    return this;
  }

  /** 设置是否允许批准并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO canApprove(Boolean value) {
    setCanApprove(value);
    return this;
  }

  /** 设置是否允许驳回并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO canReject(Boolean value) {
    setCanReject(value);
    return this;
  }

  /** 设置当前无法批准的原因并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO approvalBlockReason(String value) {
    setApprovalBlockReason(value);
    return this;
  }

  /** 设置业务来源类型并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO sourceType(String value) {
    setSourceType(value);
    return this;
  }

  /** 设置资产类型并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO assetType(String value) {
    setAssetType(value);
    return this;
  }

  /** 设置SKU 编码并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO skuCode(String value) {
    setSkuCode(value);
    return this;
  }

  /** 设置道具名称并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO propName(String value) {
    setPropName(value);
    return this;
  }

  /** 设置currentSkuStatus并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO currentSkuStatus(String value) {
    setCurrentSkuStatus(value);
    return this;
  }

  /** 设置currentSkuVersion并返回当前对象，供连续组装数据。 */
  public ReviewBaseVO currentSkuVersion(Integer value) {
    setCurrentSkuVersion(value);
    return this;
  }
}
