package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** prop_sn_adjustment_item：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropSnAdjustmentItemDO extends BaseDO {

  /** 实物调整审核 ID */
  private String adjustmentAuditId;

  /** SKU ID */
  private String skuId;

  /** SN ID */
  private String snId;

  /** SN 编号快照 */
  private String snCodeSnapshot;

  /** 调整前实物状态 */
  private String beforeInstanceStatus;

  /** 目标实物状态 */
  private String targetInstanceStatus;

  /** 明细说明 */
  private String itemRemark;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 读取实物调整审核 ID，供持久化映射使用。 */
  public String getAdjustmentAuditId() {
    return adjustmentAuditId;
  }

  /** 接收实物调整审核 ID，保持持久化映射的字段含义。 */
  public void setAdjustmentAuditId(String value) {
    adjustmentAuditId = value;
  }

  /** 读取SKU ID，供持久化映射使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收SKU ID，保持持久化映射的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取SN ID，供持久化映射使用。 */
  public String getSnId() {
    return snId;
  }

  /** 接收SN ID，保持持久化映射的字段含义。 */
  public void setSnId(String value) {
    snId = value;
  }

  /** 读取SN 编号快照，供持久化映射使用。 */
  public String getSnCodeSnapshot() {
    return snCodeSnapshot;
  }

  /** 接收SN 编号快照，保持持久化映射的字段含义。 */
  public void setSnCodeSnapshot(String value) {
    snCodeSnapshot = value;
  }

  /** 读取调整前实物状态，供持久化映射使用。 */
  public String getBeforeInstanceStatus() {
    return beforeInstanceStatus;
  }

  /** 接收调整前实物状态，保持持久化映射的字段含义。 */
  public void setBeforeInstanceStatus(String value) {
    beforeInstanceStatus = value;
  }

  /** 读取目标实物状态，供持久化映射使用。 */
  public String getTargetInstanceStatus() {
    return targetInstanceStatus;
  }

  /** 接收目标实物状态，保持持久化映射的字段含义。 */
  public void setTargetInstanceStatus(String value) {
    targetInstanceStatus = value;
  }

  /** 读取明细说明，供持久化映射使用。 */
  public String getItemRemark() {
    return itemRemark;
  }

  /** 接收明细说明，保持持久化映射的字段含义。 */
  public void setItemRemark(String value) {
    itemRemark = value;
  }

  /** 读取创建时间，供持久化映射使用。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 接收创建时间，保持持久化映射的字段含义。 */
  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentItemDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置实物调整审核 ID并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentItemDO adjustmentAuditId(String value) {
    setAdjustmentAuditId(value);
    return this;
  }

  /** 设置SKU ID并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentItemDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置SN ID并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentItemDO snId(String value) {
    setSnId(value);
    return this;
  }

  /** 设置SN 编号快照并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentItemDO snCodeSnapshot(String value) {
    setSnCodeSnapshot(value);
    return this;
  }

  /** 设置调整前实物状态并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentItemDO beforeInstanceStatus(String value) {
    setBeforeInstanceStatus(value);
    return this;
  }

  /** 设置目标实物状态并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentItemDO targetInstanceStatus(String value) {
    setTargetInstanceStatus(value);
    return this;
  }

  /** 设置明细说明并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentItemDO itemRemark(String value) {
    setItemRemark(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public PropSnAdjustmentItemDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }
}
