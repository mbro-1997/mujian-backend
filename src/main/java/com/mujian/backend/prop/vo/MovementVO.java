package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 Movement，不是数据库实体。 */
public class MovementVO {
  private String id;

  private String skuId;

  private String movementType;

  private String sourceType;

  private Integer quantity;

  private StockSnapshotVO before;

  private StockSnapshotVO after;

  private String relatedAuditId;

  private String relatedOrderId;

  private String relatedReservationId;

  private String relatedArbitrationId;

  private String operatorUserId;

  private String operatorRole;

  private String remark;

  private String createdAt;

  private Integer beforeQuantity;

  private Integer afterQuantity;

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

  /** 读取流水类型，供接口结果展示使用。 */
  public String getMovementType() {
    return movementType;
  }

  /** 接收流水类型，保持接口结果展示的字段含义。 */
  public void setMovementType(String value) {
    movementType = value;
  }

  /** 读取业务来源类型，供接口结果展示使用。 */
  public String getSourceType() {
    return sourceType;
  }

  /** 接收业务来源类型，保持接口结果展示的字段含义。 */
  public void setSourceType(String value) {
    sourceType = value;
  }

  /** 读取本次申请数量，供接口结果展示使用。 */
  public Integer getQuantity() {
    return quantity;
  }

  /** 接收本次申请数量，保持接口结果展示的字段含义。 */
  public void setQuantity(Integer value) {
    quantity = value;
  }

  /** 读取变更前快照，供接口结果展示使用。 */
  public StockSnapshotVO getBefore() {
    return before;
  }

  /** 接收变更前快照，保持接口结果展示的字段含义。 */
  public void setBefore(StockSnapshotVO stockSnapshotVO) {
    before = stockSnapshotVO;
  }

  /** 读取变更后快照，供接口结果展示使用。 */
  public StockSnapshotVO getAfter() {
    return after;
  }

  /** 接收变更后快照，保持接口结果展示的字段含义。 */
  public void setAfter(StockSnapshotVO stockSnapshotVO) {
    after = stockSnapshotVO;
  }

  /** 读取关联库存调整审核 ID，供接口结果展示使用。 */
  public String getRelatedAuditId() {
    return relatedAuditId;
  }

  /** 接收关联库存调整审核 ID，保持接口结果展示的字段含义。 */
  public void setRelatedAuditId(String value) {
    relatedAuditId = value;
  }

  /** 读取关联订单 ID，供接口结果展示使用。 */
  public String getRelatedOrderId() {
    return relatedOrderId;
  }

  /** 接收关联订单 ID，保持接口结果展示的字段含义。 */
  public void setRelatedOrderId(String value) {
    relatedOrderId = value;
  }

  /** 读取关联库存占用 ID，供接口结果展示使用。 */
  public String getRelatedReservationId() {
    return relatedReservationId;
  }

  /** 接收关联库存占用 ID，保持接口结果展示的字段含义。 */
  public void setRelatedReservationId(String value) {
    relatedReservationId = value;
  }

  /** 读取关联仲裁 ID，供接口结果展示使用。 */
  public String getRelatedArbitrationId() {
    return relatedArbitrationId;
  }

  /** 接收关联仲裁 ID，保持接口结果展示的字段含义。 */
  public void setRelatedArbitrationId(String value) {
    relatedArbitrationId = value;
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

  /** 读取备注，供接口结果展示使用。 */
  public String getRemark() {
    return remark;
  }

  /** 接收备注，保持接口结果展示的字段含义。 */
  public void setRemark(String value) {
    remark = value;
  }

  /** 读取创建时间，供接口结果展示使用。 */
  public String getCreatedAt() {
    return createdAt;
  }

  /** 接收创建时间，保持接口结果展示的字段含义。 */
  public void setCreatedAt(String value) {
    createdAt = value;
  }

  /** 读取变动前数量，供接口结果展示使用。 */
  public Integer getBeforeQuantity() {
    return beforeQuantity;
  }

  /** 接收变动前数量，保持接口结果展示的字段含义。 */
  public void setBeforeQuantity(Integer value) {
    beforeQuantity = value;
  }

  /** 读取变动后数量，供接口结果展示使用。 */
  public Integer getAfterQuantity() {
    return afterQuantity;
  }

  /** 接收变动后数量，保持接口结果展示的字段含义。 */
  public void setAfterQuantity(Integer value) {
    afterQuantity = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public MovementVO id(String value) {
    setId(value);
    return this;
  }

  /** 设置道具标识并返回当前对象，供连续组装数据。 */
  public MovementVO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置流水类型并返回当前对象，供连续组装数据。 */
  public MovementVO movementType(String value) {
    setMovementType(value);
    return this;
  }

  /** 设置业务来源类型并返回当前对象，供连续组装数据。 */
  public MovementVO sourceType(String value) {
    setSourceType(value);
    return this;
  }

  /** 设置本次申请数量并返回当前对象，供连续组装数据。 */
  public MovementVO quantity(Integer value) {
    setQuantity(value);
    return this;
  }

  /** 设置变更前快照并返回当前对象，供连续组装数据。 */
  public MovementVO before(StockSnapshotVO stockSnapshotVO) {
    setBefore(stockSnapshotVO);
    return this;
  }

  /** 设置变更后快照并返回当前对象，供连续组装数据。 */
  public MovementVO after(StockSnapshotVO stockSnapshotVO) {
    setAfter(stockSnapshotVO);
    return this;
  }

  /** 设置关联库存调整审核 ID并返回当前对象，供连续组装数据。 */
  public MovementVO relatedAuditId(String value) {
    setRelatedAuditId(value);
    return this;
  }

  /** 设置关联订单 ID并返回当前对象，供连续组装数据。 */
  public MovementVO relatedOrderId(String value) {
    setRelatedOrderId(value);
    return this;
  }

  /** 设置关联库存占用 ID并返回当前对象，供连续组装数据。 */
  public MovementVO relatedReservationId(String value) {
    setRelatedReservationId(value);
    return this;
  }

  /** 设置关联仲裁 ID并返回当前对象，供连续组装数据。 */
  public MovementVO relatedArbitrationId(String value) {
    setRelatedArbitrationId(value);
    return this;
  }

  /** 设置操作人用户 ID并返回当前对象，供连续组装数据。 */
  public MovementVO operatorUserId(String value) {
    setOperatorUserId(value);
    return this;
  }

  /** 设置操作人角色并返回当前对象，供连续组装数据。 */
  public MovementVO operatorRole(String value) {
    setOperatorRole(value);
    return this;
  }

  /** 设置备注并返回当前对象，供连续组装数据。 */
  public MovementVO remark(String value) {
    setRemark(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public MovementVO createdAt(String value) {
    setCreatedAt(value);
    return this;
  }

  /** 设置变动前数量并返回当前对象，供连续组装数据。 */
  public MovementVO beforeQuantity(Integer value) {
    setBeforeQuantity(value);
    return this;
  }

  /** 设置变动后数量并返回当前对象，供连续组装数据。 */
  public MovementVO afterQuantity(Integer value) {
    setAfterQuantity(value);
    return this;
  }
}
