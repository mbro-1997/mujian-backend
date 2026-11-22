package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 ReviewResult，不是数据库实体。 */
public class ReviewResultVO {
  private String sourceType;

  private String sourceId;

  private String skuId;

  private String decision;

  private String status;

  private Integer version;

  private String reviewedAt;

  /** 读取业务来源类型，供接口结果展示使用。 */
  public String getSourceType() {
    return sourceType;
  }

  /** 接收业务来源类型，保持接口结果展示的字段含义。 */
  public void setSourceType(String value) {
    sourceType = value;
  }

  /** 读取业务来源标识，供接口结果展示使用。 */
  public String getSourceId() {
    return sourceId;
  }

  /** 接收业务来源标识，保持接口结果展示的字段含义。 */
  public void setSourceId(String value) {
    sourceId = value;
  }

  /** 读取道具标识，供接口结果展示使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具标识，保持接口结果展示的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取decision，供接口结果展示使用。 */
  public String getDecision() {
    return decision;
  }

  /** 接收decision，保持接口结果展示的字段含义。 */
  public void setDecision(String value) {
    decision = value;
  }

  /** 读取状态筛选条件，供接口结果展示使用。 */
  public String getStatus() {
    return status;
  }

  /** 接收状态筛选条件，保持接口结果展示的字段含义。 */
  public void setStatus(String value) {
    status = value;
  }

  /** 读取乐观锁版本，供接口结果展示使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持接口结果展示的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取审核时间，供接口结果展示使用。 */
  public String getReviewedAt() {
    return reviewedAt;
  }

  /** 接收审核时间，保持接口结果展示的字段含义。 */
  public void setReviewedAt(String value) {
    reviewedAt = value;
  }

  /** 设置业务来源类型并返回当前对象，供连续组装数据。 */
  public ReviewResultVO sourceType(String value) {
    setSourceType(value);
    return this;
  }

  /** 设置业务来源标识并返回当前对象，供连续组装数据。 */
  public ReviewResultVO sourceId(String value) {
    setSourceId(value);
    return this;
  }

  /** 设置道具标识并返回当前对象，供连续组装数据。 */
  public ReviewResultVO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置decision并返回当前对象，供连续组装数据。 */
  public ReviewResultVO decision(String value) {
    setDecision(value);
    return this;
  }

  /** 设置状态筛选条件并返回当前对象，供连续组装数据。 */
  public ReviewResultVO status(String value) {
    setStatus(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public ReviewResultVO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置审核时间并返回当前对象，供连续组装数据。 */
  public ReviewResultVO reviewedAt(String value) {
    setReviewedAt(value);
    return this;
  }
}
