package com.mujian.backend.prop.projection;

public class PropReviewListProjection {
  private String id;

  private String sourceType;

  private String category;

  private String skuId;

  private String skuCode;

  private String propName;

  private String factoryId;

  private String factoryName;

  private String assetType;

  private String actionType;

  private String auditStatus;

  private String applicantName;

  private String applyRemark;

  private String relatedSkuAuditId;

  private Integer version;

  private Integer initialQuantity;

  private Integer quantity;

  private Integer quantityDelta;

  private java.time.LocalDateTime submittedAt;

  private java.time.LocalDateTime reviewedAt;

  /** 读取记录标识，供查询结果映射使用。 */
  public String getId() {
    return id;
  }

  /** 接收记录标识，保持查询结果映射的字段含义。 */
  public void setId(String value) {
    id = value;
  }

  /** 读取业务来源类型，供查询结果映射使用。 */
  public String getSourceType() {
    return sourceType;
  }

  /** 接收业务来源类型，保持查询结果映射的字段含义。 */
  public void setSourceType(String value) {
    sourceType = value;
  }

  /** 读取申请分类，供查询结果映射使用。 */
  public String getCategory() {
    return category;
  }

  /** 接收申请分类，保持查询结果映射的字段含义。 */
  public void setCategory(String value) {
    category = value;
  }

  /** 读取道具标识，供查询结果映射使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具标识，保持查询结果映射的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取SKU 编码，供查询结果映射使用。 */
  public String getSkuCode() {
    return skuCode;
  }

  /** 接收SKU 编码，保持查询结果映射的字段含义。 */
  public void setSkuCode(String value) {
    skuCode = value;
  }

  /** 读取道具名称，供查询结果映射使用。 */
  public String getPropName() {
    return propName;
  }

  /** 接收道具名称，保持查询结果映射的字段含义。 */
  public void setPropName(String value) {
    propName = value;
  }

  /** 读取工厂标识，供查询结果映射使用。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 接收工厂标识，保持查询结果映射的字段含义。 */
  public void setFactoryId(String value) {
    factoryId = value;
  }

  /** 读取factoryName，供查询结果映射使用。 */
  public String getFactoryName() {
    return factoryName;
  }

  /** 接收factoryName，保持查询结果映射的字段含义。 */
  public void setFactoryName(String value) {
    factoryName = value;
  }

  /** 读取资产类型，供查询结果映射使用。 */
  public String getAssetType() {
    return assetType;
  }

  /** 接收资产类型，保持查询结果映射的字段含义。 */
  public void setAssetType(String value) {
    assetType = value;
  }

  /** 读取申请动作，供查询结果映射使用。 */
  public String getActionType() {
    return actionType;
  }

  /** 接收申请动作，保持查询结果映射的字段含义。 */
  public void setActionType(String value) {
    actionType = value;
  }

  /** 读取审核状态，供查询结果映射使用。 */
  public String getAuditStatus() {
    return auditStatus;
  }

  /** 接收审核状态，保持查询结果映射的字段含义。 */
  public void setAuditStatus(String value) {
    auditStatus = value;
  }

  /** 读取applicantName，供查询结果映射使用。 */
  public String getApplicantName() {
    return applicantName;
  }

  /** 接收applicantName，保持查询结果映射的字段含义。 */
  public void setApplicantName(String value) {
    applicantName = value;
  }

  /** 读取申请说明，供查询结果映射使用。 */
  public String getApplyRemark() {
    return applyRemark;
  }

  /** 接收申请说明，保持查询结果映射的字段含义。 */
  public void setApplyRemark(String value) {
    applyRemark = value;
  }

  /** 读取关联道具审核记录标识，供查询结果映射使用。 */
  public String getRelatedSkuAuditId() {
    return relatedSkuAuditId;
  }

  /** 接收关联道具审核记录标识，保持查询结果映射的字段含义。 */
  public void setRelatedSkuAuditId(String value) {
    relatedSkuAuditId = value;
  }

  /** 读取乐观锁版本，供查询结果映射使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持查询结果映射的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取首次入库数量，供查询结果映射使用。 */
  public Integer getInitialQuantity() {
    return initialQuantity;
  }

  /** 接收首次入库数量，保持查询结果映射的字段含义。 */
  public void setInitialQuantity(Integer value) {
    initialQuantity = value;
  }

  /** 读取本次申请数量，供查询结果映射使用。 */
  public Integer getQuantity() {
    return quantity;
  }

  /** 接收本次申请数量，保持查询结果映射的字段含义。 */
  public void setQuantity(Integer value) {
    quantity = value;
  }

  /** 读取变化数量，供查询结果映射使用。 */
  public Integer getQuantityDelta() {
    return quantityDelta;
  }

  /** 接收变化数量，保持查询结果映射的字段含义。 */
  public void setQuantityDelta(Integer value) {
    quantityDelta = value;
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
}
