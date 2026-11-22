package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 AdjustmentItem，不是数据库实体。 */
public class AdjustmentItemVO {
  private String snId;

  private String snCodeSnapshot;

  private String beforeStatus;

  private String targetStatus;

  private String remark;

  private String currentStatus;

  private String currentStatusRemark;

  private String currentStatusChangedAt;

  /** 读取SN ID，供接口结果展示使用。 */
  public String getSnId() {
    return snId;
  }

  /** 接收SN ID，保持接口结果展示的字段含义。 */
  public void setSnId(String value) {
    snId = value;
  }

  /** 读取SN 编号快照，供接口结果展示使用。 */
  public String getSnCodeSnapshot() {
    return snCodeSnapshot;
  }

  /** 接收SN 编号快照，保持接口结果展示的字段含义。 */
  public void setSnCodeSnapshot(String value) {
    snCodeSnapshot = value;
  }

  /** 读取beforeStatus，供接口结果展示使用。 */
  public String getBeforeStatus() {
    return beforeStatus;
  }

  /** 接收beforeStatus，保持接口结果展示的字段含义。 */
  public void setBeforeStatus(String value) {
    beforeStatus = value;
  }

  /** 读取targetStatus，供接口结果展示使用。 */
  public String getTargetStatus() {
    return targetStatus;
  }

  /** 接收targetStatus，保持接口结果展示的字段含义。 */
  public void setTargetStatus(String value) {
    targetStatus = value;
  }

  /** 读取备注，供接口结果展示使用。 */
  public String getRemark() {
    return remark;
  }

  /** 接收备注，保持接口结果展示的字段含义。 */
  public void setRemark(String value) {
    remark = value;
  }

  /** 读取currentStatus，供接口结果展示使用。 */
  public String getCurrentStatus() {
    return currentStatus;
  }

  /** 接收currentStatus，保持接口结果展示的字段含义。 */
  public void setCurrentStatus(String value) {
    currentStatus = value;
  }

  /** 读取currentStatusRemark，供接口结果展示使用。 */
  public String getCurrentStatusRemark() {
    return currentStatusRemark;
  }

  /** 接收currentStatusRemark，保持接口结果展示的字段含义。 */
  public void setCurrentStatusRemark(String value) {
    currentStatusRemark = value;
  }

  /** 读取currentStatusChangedAt，供接口结果展示使用。 */
  public String getCurrentStatusChangedAt() {
    return currentStatusChangedAt;
  }

  /** 接收currentStatusChangedAt，保持接口结果展示的字段含义。 */
  public void setCurrentStatusChangedAt(String value) {
    currentStatusChangedAt = value;
  }

  /** 设置SN ID并返回当前对象，供连续组装数据。 */
  public AdjustmentItemVO snId(String value) {
    setSnId(value);
    return this;
  }

  /** 设置SN 编号快照并返回当前对象，供连续组装数据。 */
  public AdjustmentItemVO snCodeSnapshot(String value) {
    setSnCodeSnapshot(value);
    return this;
  }

  /** 设置beforeStatus并返回当前对象，供连续组装数据。 */
  public AdjustmentItemVO beforeStatus(String value) {
    setBeforeStatus(value);
    return this;
  }

  /** 设置targetStatus并返回当前对象，供连续组装数据。 */
  public AdjustmentItemVO targetStatus(String value) {
    setTargetStatus(value);
    return this;
  }

  /** 设置备注并返回当前对象，供连续组装数据。 */
  public AdjustmentItemVO remark(String value) {
    setRemark(value);
    return this;
  }

  /** 设置currentStatus并返回当前对象，供连续组装数据。 */
  public AdjustmentItemVO currentStatus(String value) {
    setCurrentStatus(value);
    return this;
  }

  /** 设置currentStatusRemark并返回当前对象，供连续组装数据。 */
  public AdjustmentItemVO currentStatusRemark(String value) {
    setCurrentStatusRemark(value);
    return this;
  }

  /** 设置currentStatusChangedAt并返回当前对象，供连续组装数据。 */
  public AdjustmentItemVO currentStatusChangedAt(String value) {
    setCurrentStatusChangedAt(value);
    return this;
  }
}
