package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 SnLog，不是数据库实体。 */
public class SnLogVO {
  private String id;

  private String snId;

  private String fromStatus;

  private String toStatus;

  private String sourceType;

  private String operatorUserId;

  private String operatorRole;

  private String relatedAdjustmentId;

  private String relatedOrderId;

  private String relatedArbitrationId;

  private String reason;

  private String createdAt;

  /** 读取记录标识，供接口结果展示使用。 */
  public String getId() {
    return id;
  }

  /** 接收记录标识，保持接口结果展示的字段含义。 */
  public void setId(String value) {
    id = value;
  }

  /** 读取SN ID，供接口结果展示使用。 */
  public String getSnId() {
    return snId;
  }

  /** 接收SN ID，保持接口结果展示的字段含义。 */
  public void setSnId(String value) {
    snId = value;
  }

  /** 读取变更前状态，供接口结果展示使用。 */
  public String getFromStatus() {
    return fromStatus;
  }

  /** 接收变更前状态，保持接口结果展示的字段含义。 */
  public void setFromStatus(String value) {
    fromStatus = value;
  }

  /** 读取变更后状态，供接口结果展示使用。 */
  public String getToStatus() {
    return toStatus;
  }

  /** 接收变更后状态，保持接口结果展示的字段含义。 */
  public void setToStatus(String value) {
    toStatus = value;
  }

  /** 读取业务来源类型，供接口结果展示使用。 */
  public String getSourceType() {
    return sourceType;
  }

  /** 接收业务来源类型，保持接口结果展示的字段含义。 */
  public void setSourceType(String value) {
    sourceType = value;
  }

  /** 读取操作人用户 ID，供接口结果展示使用。 */
  public String getOperatorUserId() {
    return operatorUserId;
  }

  /** 接收操作人用户 ID，保持接口结果展示的字段含义。 */
  public void setOperatorUserId(String value) {
    operatorUserId = value;
  }

  /** 读取操作人角色，供接口结果展示使用。 */
  public String getOperatorRole() {
    return operatorRole;
  }

  /** 接收操作人角色，保持接口结果展示的字段含义。 */
  public void setOperatorRole(String value) {
    operatorRole = value;
  }

  /** 读取relatedAdjustmentId，供接口结果展示使用。 */
  public String getRelatedAdjustmentId() {
    return relatedAdjustmentId;
  }

  /** 接收relatedAdjustmentId，保持接口结果展示的字段含义。 */
  public void setRelatedAdjustmentId(String value) {
    relatedAdjustmentId = value;
  }

  /** 读取关联订单 ID，供接口结果展示使用。 */
  public String getRelatedOrderId() {
    return relatedOrderId;
  }

  /** 接收关联订单 ID，保持接口结果展示的字段含义。 */
  public void setRelatedOrderId(String value) {
    relatedOrderId = value;
  }

  /** 读取关联仲裁 ID，供接口结果展示使用。 */
  public String getRelatedArbitrationId() {
    return relatedArbitrationId;
  }

  /** 接收关联仲裁 ID，保持接口结果展示的字段含义。 */
  public void setRelatedArbitrationId(String value) {
    relatedArbitrationId = value;
  }

  /** 读取业务操作原因，供接口结果展示使用。 */
  public String getReason() {
    return reason;
  }

  /** 接收业务操作原因，保持接口结果展示的字段含义。 */
  public void setReason(String value) {
    reason = value;
  }

  /** 读取创建时间，供接口结果展示使用。 */
  public String getCreatedAt() {
    return createdAt;
  }

  /** 接收创建时间，保持接口结果展示的字段含义。 */
  public void setCreatedAt(String value) {
    createdAt = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public SnLogVO id(String value) {
    setId(value);
    return this;
  }

  /** 设置SN ID并返回当前对象，供连续组装数据。 */
  public SnLogVO snId(String value) {
    setSnId(value);
    return this;
  }

  /** 设置变更前状态并返回当前对象，供连续组装数据。 */
  public SnLogVO fromStatus(String value) {
    setFromStatus(value);
    return this;
  }

  /** 设置变更后状态并返回当前对象，供连续组装数据。 */
  public SnLogVO toStatus(String value) {
    setToStatus(value);
    return this;
  }

  /** 设置业务来源类型并返回当前对象，供连续组装数据。 */
  public SnLogVO sourceType(String value) {
    setSourceType(value);
    return this;
  }

  /** 设置操作人用户 ID并返回当前对象，供连续组装数据。 */
  public SnLogVO operatorUserId(String value) {
    setOperatorUserId(value);
    return this;
  }

  /** 设置操作人角色并返回当前对象，供连续组装数据。 */
  public SnLogVO operatorRole(String value) {
    setOperatorRole(value);
    return this;
  }

  /** 设置relatedAdjustmentId并返回当前对象，供连续组装数据。 */
  public SnLogVO relatedAdjustmentId(String value) {
    setRelatedAdjustmentId(value);
    return this;
  }

  /** 设置关联订单 ID并返回当前对象，供连续组装数据。 */
  public SnLogVO relatedOrderId(String value) {
    setRelatedOrderId(value);
    return this;
  }

  /** 设置关联仲裁 ID并返回当前对象，供连续组装数据。 */
  public SnLogVO relatedArbitrationId(String value) {
    setRelatedArbitrationId(value);
    return this;
  }

  /** 设置业务操作原因并返回当前对象，供连续组装数据。 */
  public SnLogVO reason(String value) {
    setReason(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public SnLogVO createdAt(String value) {
    setCreatedAt(value);
    return this;
  }
}
