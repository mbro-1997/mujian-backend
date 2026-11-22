package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 ReviewRow，不是数据库实体。 */
public class ReviewRowVO {
  private String id;

  private String sourceType;

  private String skuId;

  private String propName;

  private String factoryId;

  private String factoryName;

  private String assetType;

  private String actionType;

  private String auditStatus;

  private String submittedAt;

  private String applicantName;

  private Integer version;

  private String skuCode;

  private String applyRemark;

  private String reviewedAt;

  private Integer initialQuantity;

  private Integer quantity;

  private Integer quantityDelta;

  private String relatedSkuAuditId;

  /** 读取记录标识，供接口结果展示使用。 */
  public String getId() {
    return id;
  }

  /** 接收记录标识，保持接口结果展示的字段含义。 */
  public void setId(String value) {
    id = value;
  }

  /** 读取业务来源类型，供接口结果展示使用。 */
  public String getSourceType() {
    return sourceType;
  }

  /** 接收业务来源类型，保持接口结果展示的字段含义。 */
  public void setSourceType(String value) {
    sourceType = value;
  }

  /** 读取道具标识，供接口结果展示使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具标识，保持接口结果展示的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取道具名称，供接口结果展示使用。 */
  public String getPropName() {
    return propName;
  }

  /** 接收道具名称，保持接口结果展示的字段含义。 */
  public void setPropName(String value) {
    propName = value;
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

  /** 读取资产类型，供接口结果展示使用。 */
  public String getAssetType() {
    return assetType;
  }

  /** 接收资产类型，保持接口结果展示的字段含义。 */
  public void setAssetType(String value) {
    assetType = value;
  }

  /** 读取申请动作，供接口结果展示使用。 */
  public String getActionType() {
    return actionType;
  }

  /** 接收申请动作，保持接口结果展示的字段含义。 */
  public void setActionType(String value) {
    actionType = value;
  }

  /** 读取审核状态，供接口结果展示使用。 */
  public String getAuditStatus() {
    return auditStatus;
  }

  /** 接收审核状态，保持接口结果展示的字段含义。 */
  public void setAuditStatus(String value) {
    auditStatus = value;
  }

  /** 读取提交时间，供接口结果展示使用。 */
  public String getSubmittedAt() {
    return submittedAt;
  }

  /** 接收提交时间，保持接口结果展示的字段含义。 */
  public void setSubmittedAt(String value) {
    submittedAt = value;
  }

  /** 读取applicantName，供接口结果展示使用。 */
  public String getApplicantName() {
    return applicantName;
  }

  /** 接收applicantName，保持接口结果展示的字段含义。 */
  public void setApplicantName(String value) {
    applicantName = value;
  }

  /** 读取乐观锁版本，供接口结果展示使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持接口结果展示的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取SKU 编码，供接口结果展示使用。 */
  public String getSkuCode() {
    return skuCode;
  }

  /** 接收SKU 编码，保持接口结果展示的字段含义。 */
  public void setSkuCode(String value) {
    skuCode = value;
  }

  /** 读取申请说明，供接口结果展示使用。 */
  public String getApplyRemark() {
    return applyRemark;
  }

  /** 接收申请说明，保持接口结果展示的字段含义。 */
  public void setApplyRemark(String value) {
    applyRemark = value;
  }

  /** 读取审核时间，供接口结果展示使用。 */
  public String getReviewedAt() {
    return reviewedAt;
  }

  /** 接收审核时间，保持接口结果展示的字段含义。 */
  public void setReviewedAt(String value) {
    reviewedAt = value;
  }

  /** 读取首次入库数量，供接口结果展示使用。 */
  public Integer getInitialQuantity() {
    return initialQuantity;
  }

  /** 接收首次入库数量，保持接口结果展示的字段含义。 */
  public void setInitialQuantity(Integer value) {
    initialQuantity = value;
  }

  /** 读取本次申请数量，供接口结果展示使用。 */
  public Integer getQuantity() {
    return quantity;
  }

  /** 接收本次申请数量，保持接口结果展示的字段含义。 */
  public void setQuantity(Integer value) {
    quantity = value;
  }

  /** 读取变化数量，供接口结果展示使用。 */
  public Integer getQuantityDelta() {
    return quantityDelta;
  }

  /** 接收变化数量，保持接口结果展示的字段含义。 */
  public void setQuantityDelta(Integer value) {
    quantityDelta = value;
  }

  /** 读取关联道具审核记录标识，供接口结果展示使用。 */
  public String getRelatedSkuAuditId() {
    return relatedSkuAuditId;
  }

  /** 接收关联道具审核记录标识，保持接口结果展示的字段含义。 */
  public void setRelatedSkuAuditId(String value) {
    relatedSkuAuditId = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public ReviewRowVO id(String value) {
    setId(value);
    return this;
  }

  /** 设置业务来源类型并返回当前对象，供连续组装数据。 */
  public ReviewRowVO sourceType(String value) {
    setSourceType(value);
    return this;
  }

  /** 设置道具标识并返回当前对象，供连续组装数据。 */
  public ReviewRowVO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置道具名称并返回当前对象，供连续组装数据。 */
  public ReviewRowVO propName(String value) {
    setPropName(value);
    return this;
  }

  /** 设置工厂标识并返回当前对象，供连续组装数据。 */
  public ReviewRowVO factoryId(String value) {
    setFactoryId(value);
    return this;
  }

  /** 设置factoryName并返回当前对象，供连续组装数据。 */
  public ReviewRowVO factoryName(String value) {
    setFactoryName(value);
    return this;
  }

  /** 设置资产类型并返回当前对象，供连续组装数据。 */
  public ReviewRowVO assetType(String value) {
    setAssetType(value);
    return this;
  }

  /** 设置申请动作并返回当前对象，供连续组装数据。 */
  public ReviewRowVO actionType(String value) {
    setActionType(value);
    return this;
  }

  /** 设置审核状态并返回当前对象，供连续组装数据。 */
  public ReviewRowVO auditStatus(String value) {
    setAuditStatus(value);
    return this;
  }

  /** 设置提交时间并返回当前对象，供连续组装数据。 */
  public ReviewRowVO submittedAt(String value) {
    setSubmittedAt(value);
    return this;
  }

  /** 设置applicantName并返回当前对象，供连续组装数据。 */
  public ReviewRowVO applicantName(String value) {
    setApplicantName(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public ReviewRowVO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置SKU 编码并返回当前对象，供连续组装数据。 */
  public ReviewRowVO skuCode(String value) {
    setSkuCode(value);
    return this;
  }

  /** 设置申请说明并返回当前对象，供连续组装数据。 */
  public ReviewRowVO applyRemark(String value) {
    setApplyRemark(value);
    return this;
  }

  /** 设置审核时间并返回当前对象，供连续组装数据。 */
  public ReviewRowVO reviewedAt(String value) {
    setReviewedAt(value);
    return this;
  }

  /** 设置首次入库数量并返回当前对象，供连续组装数据。 */
  public ReviewRowVO initialQuantity(Integer value) {
    setInitialQuantity(value);
    return this;
  }

  /** 设置本次申请数量并返回当前对象，供连续组装数据。 */
  public ReviewRowVO quantity(Integer value) {
    setQuantity(value);
    return this;
  }

  /** 设置变化数量并返回当前对象，供连续组装数据。 */
  public ReviewRowVO quantityDelta(Integer value) {
    setQuantityDelta(value);
    return this;
  }

  /** 设置关联道具审核记录标识并返回当前对象，供连续组装数据。 */
  public ReviewRowVO relatedSkuAuditId(String value) {
    setRelatedSkuAuditId(value);
    return this;
  }
}
