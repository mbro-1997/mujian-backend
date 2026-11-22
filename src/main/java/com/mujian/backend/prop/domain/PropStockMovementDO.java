package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** prop_stock_movement：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropStockMovementDO extends BaseDO {

  /** SKU ID */
  private String skuId;

  /** 流水类型 */
  private String movementType;

  /** 来源类型 */
  private String sourceType;

  /** 变动数量 */
  private Integer quantity;

  /** 变动前数量 */
  private Integer beforeQuantity;

  /** 变动后数量 */
  private Integer afterQuantity;

  /** 变动前库存快照 */
  private String beforeStockSnapshotJson;

  /** 变动后库存快照 */
  private String afterStockSnapshotJson;

  /** 关联库存调整审核 ID */
  private String relatedAuditId;

  /** 关联订单 ID */
  private String relatedOrderId;

  /** 关联库存占用 ID */
  private String relatedReservationId;

  /** 关联仲裁 ID */
  private String relatedArbitrationId;

  /** 操作人用户 ID */
  private String operatorUserId;

  /** 操作人角色 */
  private String operatorRole;

  /** 备注 */
  private String remark;

  /** 效果唯一键 */
  private String businessKey;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 读取SKU ID，供持久化映射使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收SKU ID，保持持久化映射的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取流水类型，供持久化映射使用。 */
  public String getMovementType() {
    return movementType;
  }

  /** 接收流水类型，保持持久化映射的字段含义。 */
  public void setMovementType(String value) {
    movementType = value;
  }

  /** 读取来源类型，供持久化映射使用。 */
  public String getSourceType() {
    return sourceType;
  }

  /** 接收来源类型，保持持久化映射的字段含义。 */
  public void setSourceType(String value) {
    sourceType = value;
  }

  /** 读取变动数量，供持久化映射使用。 */
  public Integer getQuantity() {
    return quantity;
  }

  /** 接收变动数量，保持持久化映射的字段含义。 */
  public void setQuantity(Integer value) {
    quantity = value;
  }

  /** 读取变动前数量，供持久化映射使用。 */
  public Integer getBeforeQuantity() {
    return beforeQuantity;
  }

  /** 接收变动前数量，保持持久化映射的字段含义。 */
  public void setBeforeQuantity(Integer value) {
    beforeQuantity = value;
  }

  /** 读取变动后数量，供持久化映射使用。 */
  public Integer getAfterQuantity() {
    return afterQuantity;
  }

  /** 接收变动后数量，保持持久化映射的字段含义。 */
  public void setAfterQuantity(Integer value) {
    afterQuantity = value;
  }

  /** 读取变动前库存快照，供持久化映射使用。 */
  public String getBeforeStockSnapshotJson() {
    return beforeStockSnapshotJson;
  }

  /** 接收变动前库存快照，保持持久化映射的字段含义。 */
  public void setBeforeStockSnapshotJson(String value) {
    beforeStockSnapshotJson = value;
  }

  /** 读取变动后库存快照，供持久化映射使用。 */
  public String getAfterStockSnapshotJson() {
    return afterStockSnapshotJson;
  }

  /** 接收变动后库存快照，保持持久化映射的字段含义。 */
  public void setAfterStockSnapshotJson(String value) {
    afterStockSnapshotJson = value;
  }

  /** 读取关联库存调整审核 ID，供持久化映射使用。 */
  public String getRelatedAuditId() {
    return relatedAuditId;
  }

  /** 接收关联库存调整审核 ID，保持持久化映射的字段含义。 */
  public void setRelatedAuditId(String value) {
    relatedAuditId = value;
  }

  /** 读取关联订单 ID，供持久化映射使用。 */
  public String getRelatedOrderId() {
    return relatedOrderId;
  }

  /** 接收关联订单 ID，保持持久化映射的字段含义。 */
  public void setRelatedOrderId(String value) {
    relatedOrderId = value;
  }

  /** 读取关联库存占用 ID，供持久化映射使用。 */
  public String getRelatedReservationId() {
    return relatedReservationId;
  }

  /** 接收关联库存占用 ID，保持持久化映射的字段含义。 */
  public void setRelatedReservationId(String value) {
    relatedReservationId = value;
  }

  /** 读取关联仲裁 ID，供持久化映射使用。 */
  public String getRelatedArbitrationId() {
    return relatedArbitrationId;
  }

  /** 接收关联仲裁 ID，保持持久化映射的字段含义。 */
  public void setRelatedArbitrationId(String value) {
    relatedArbitrationId = value;
  }

  /** 读取操作人用户 ID，供持久化映射使用。 */
  public String getOperatorUserId() {
    return operatorUserId;
  }

  /** 接收操作人用户 ID，保持持久化映射的字段含义。 */
  public void setOperatorUserId(String value) {
    operatorUserId = value;
  }

  /** 读取操作人角色，供持久化映射使用。 */
  public String getOperatorRole() {
    return operatorRole;
  }

  /** 接收操作人角色，保持持久化映射的字段含义。 */
  public void setOperatorRole(String value) {
    operatorRole = value;
  }

  /** 读取备注，供持久化映射使用。 */
  public String getRemark() {
    return remark;
  }

  /** 接收备注，保持持久化映射的字段含义。 */
  public void setRemark(String value) {
    remark = value;
  }

  /** 读取效果唯一键，供持久化映射使用。 */
  public String getBusinessKey() {
    return businessKey;
  }

  /** 接收效果唯一键，保持持久化映射的字段含义。 */
  public void setBusinessKey(String value) {
    businessKey = value;
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
  public PropStockMovementDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置SKU ID并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置流水类型并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO movementType(String value) {
    setMovementType(value);
    return this;
  }

  /** 设置来源类型并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO sourceType(String value) {
    setSourceType(value);
    return this;
  }

  /** 设置变动数量并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO quantity(Integer value) {
    setQuantity(value);
    return this;
  }

  /** 设置变动前数量并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO beforeQuantity(Integer value) {
    setBeforeQuantity(value);
    return this;
  }

  /** 设置变动后数量并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO afterQuantity(Integer value) {
    setAfterQuantity(value);
    return this;
  }

  /** 设置变动前库存快照并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO beforeStockSnapshotJson(String value) {
    setBeforeStockSnapshotJson(value);
    return this;
  }

  /** 设置变动后库存快照并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO afterStockSnapshotJson(String value) {
    setAfterStockSnapshotJson(value);
    return this;
  }

  /** 设置关联库存调整审核 ID并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO relatedAuditId(String value) {
    setRelatedAuditId(value);
    return this;
  }

  /** 设置关联订单 ID并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO relatedOrderId(String value) {
    setRelatedOrderId(value);
    return this;
  }

  /** 设置关联库存占用 ID并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO relatedReservationId(String value) {
    setRelatedReservationId(value);
    return this;
  }

  /** 设置关联仲裁 ID并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO relatedArbitrationId(String value) {
    setRelatedArbitrationId(value);
    return this;
  }

  /** 设置操作人用户 ID并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO operatorUserId(String value) {
    setOperatorUserId(value);
    return this;
  }

  /** 设置操作人角色并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO operatorRole(String value) {
    setOperatorRole(value);
    return this;
  }

  /** 设置备注并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO remark(String value) {
    setRemark(value);
    return this;
  }

  /** 设置效果唯一键并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO businessKey(String value) {
    setBusinessKey(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public PropStockMovementDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }
}
