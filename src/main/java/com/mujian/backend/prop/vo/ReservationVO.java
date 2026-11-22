package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 Reservation，不是数据库实体。 */
public class ReservationVO {
  private String id;

  private String skuId;

  private String snId;

  private Integer quantity;

  private String rentalStartDate;

  private String rentalEndDate;

  private String occupyStartDate;

  private String occupyEndDate;

  private String reservationStatus;

  private String rentalOrderId;

  private String sourceType;

  private String sourceId;

  private String expiresAt;

  private String skuCode;

  private String propName;

  private String snCode;

  private String updatedAt;

  private String checkoutBatchId;

  private String checkoutBatchItemSnapshotId;

  private String rentalOrderItemId;

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

  /** 读取SN ID，供接口结果展示使用。 */
  public String getSnId() {
    return snId;
  }

  /** 接收SN ID，保持接口结果展示的字段含义。 */
  public void setSnId(String value) {
    snId = value;
  }

  /** 读取本次申请数量，供接口结果展示使用。 */
  public Integer getQuantity() {
    return quantity;
  }

  /** 接收本次申请数量，保持接口结果展示的字段含义。 */
  public void setQuantity(Integer value) {
    quantity = value;
  }

  /** 读取租期开始日期，供接口结果展示使用。 */
  public String getRentalStartDate() {
    return rentalStartDate;
  }

  /** 接收租期开始日期，保持接口结果展示的字段含义。 */
  public void setRentalStartDate(String value) {
    rentalStartDate = value;
  }

  /** 读取租期结束日期，供接口结果展示使用。 */
  public String getRentalEndDate() {
    return rentalEndDate;
  }

  /** 接收租期结束日期，保持接口结果展示的字段含义。 */
  public void setRentalEndDate(String value) {
    rentalEndDate = value;
  }

  /** 读取实际占用开始日期，供接口结果展示使用。 */
  public String getOccupyStartDate() {
    return occupyStartDate;
  }

  /** 接收实际占用开始日期，保持接口结果展示的字段含义。 */
  public void setOccupyStartDate(String value) {
    occupyStartDate = value;
  }

  /** 读取实际占用结束日期，供接口结果展示使用。 */
  public String getOccupyEndDate() {
    return occupyEndDate;
  }

  /** 接收实际占用结束日期，保持接口结果展示的字段含义。 */
  public void setOccupyEndDate(String value) {
    occupyEndDate = value;
  }

  /** 读取占用状态，供接口结果展示使用。 */
  public String getReservationStatus() {
    return reservationStatus;
  }

  /** 接收占用状态，保持接口结果展示的字段含义。 */
  public void setReservationStatus(String value) {
    reservationStatus = value;
  }

  /** 读取租赁订单 ID，供接口结果展示使用。 */
  public String getRentalOrderId() {
    return rentalOrderId;
  }

  /** 接收租赁订单 ID，保持接口结果展示的字段含义。 */
  public void setRentalOrderId(String value) {
    rentalOrderId = value;
  }

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

  /** 读取凭证或链接失效时间，供接口结果展示使用。 */
  public String getExpiresAt() {
    return expiresAt;
  }

  /** 接收凭证或链接失效时间，保持接口结果展示的字段含义。 */
  public void setExpiresAt(String value) {
    expiresAt = value;
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

  /** 读取SN 编号，供接口结果展示使用。 */
  public String getSnCode() {
    return snCode;
  }

  /** 接收SN 编号，保持接口结果展示的字段含义。 */
  public void setSnCode(String value) {
    snCode = value;
  }

  /** 读取更新时间，供接口结果展示使用。 */
  public String getUpdatedAt() {
    return updatedAt;
  }

  /** 接收更新时间，保持接口结果展示的字段含义。 */
  public void setUpdatedAt(String value) {
    updatedAt = value;
  }

  /** 读取来源结算批次 ID，供接口结果展示使用。 */
  public String getCheckoutBatchId() {
    return checkoutBatchId;
  }

  /** 接收来源结算批次 ID，保持接口结果展示的字段含义。 */
  public void setCheckoutBatchId(String value) {
    checkoutBatchId = value;
  }

  /** 读取来源结算明细快照 ID，供接口结果展示使用。 */
  public String getCheckoutBatchItemSnapshotId() {
    return checkoutBatchItemSnapshotId;
  }

  /** 接收来源结算明细快照 ID，保持接口结果展示的字段含义。 */
  public void setCheckoutBatchItemSnapshotId(String value) {
    checkoutBatchItemSnapshotId = value;
  }

  /** 读取租赁订单明细 ID，供接口结果展示使用。 */
  public String getRentalOrderItemId() {
    return rentalOrderItemId;
  }

  /** 接收租赁订单明细 ID，保持接口结果展示的字段含义。 */
  public void setRentalOrderItemId(String value) {
    rentalOrderItemId = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public ReservationVO id(String value) {
    setId(value);
    return this;
  }

  /** 设置道具标识并返回当前对象，供连续组装数据。 */
  public ReservationVO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置SN ID并返回当前对象，供连续组装数据。 */
  public ReservationVO snId(String value) {
    setSnId(value);
    return this;
  }

  /** 设置本次申请数量并返回当前对象，供连续组装数据。 */
  public ReservationVO quantity(Integer value) {
    setQuantity(value);
    return this;
  }

  /** 设置租期开始日期并返回当前对象，供连续组装数据。 */
  public ReservationVO rentalStartDate(String value) {
    setRentalStartDate(value);
    return this;
  }

  /** 设置租期结束日期并返回当前对象，供连续组装数据。 */
  public ReservationVO rentalEndDate(String value) {
    setRentalEndDate(value);
    return this;
  }

  /** 设置实际占用开始日期并返回当前对象，供连续组装数据。 */
  public ReservationVO occupyStartDate(String value) {
    setOccupyStartDate(value);
    return this;
  }

  /** 设置实际占用结束日期并返回当前对象，供连续组装数据。 */
  public ReservationVO occupyEndDate(String value) {
    setOccupyEndDate(value);
    return this;
  }

  /** 设置占用状态并返回当前对象，供连续组装数据。 */
  public ReservationVO reservationStatus(String value) {
    setReservationStatus(value);
    return this;
  }

  /** 设置租赁订单 ID并返回当前对象，供连续组装数据。 */
  public ReservationVO rentalOrderId(String value) {
    setRentalOrderId(value);
    return this;
  }

  /** 设置业务来源类型并返回当前对象，供连续组装数据。 */
  public ReservationVO sourceType(String value) {
    setSourceType(value);
    return this;
  }

  /** 设置业务来源标识并返回当前对象，供连续组装数据。 */
  public ReservationVO sourceId(String value) {
    setSourceId(value);
    return this;
  }

  /** 设置凭证或链接失效时间并返回当前对象，供连续组装数据。 */
  public ReservationVO expiresAt(String value) {
    setExpiresAt(value);
    return this;
  }

  /** 设置SKU 编码并返回当前对象，供连续组装数据。 */
  public ReservationVO skuCode(String value) {
    setSkuCode(value);
    return this;
  }

  /** 设置道具名称并返回当前对象，供连续组装数据。 */
  public ReservationVO propName(String value) {
    setPropName(value);
    return this;
  }

  /** 设置SN 编号并返回当前对象，供连续组装数据。 */
  public ReservationVO snCode(String value) {
    setSnCode(value);
    return this;
  }

  /** 设置更新时间并返回当前对象，供连续组装数据。 */
  public ReservationVO updatedAt(String value) {
    setUpdatedAt(value);
    return this;
  }

  /** 设置来源结算批次 ID并返回当前对象，供连续组装数据。 */
  public ReservationVO checkoutBatchId(String value) {
    setCheckoutBatchId(value);
    return this;
  }

  /** 设置来源结算明细快照 ID并返回当前对象，供连续组装数据。 */
  public ReservationVO checkoutBatchItemSnapshotId(String value) {
    setCheckoutBatchItemSnapshotId(value);
    return this;
  }

  /** 设置租赁订单明细 ID并返回当前对象，供连续组装数据。 */
  public ReservationVO rentalOrderItemId(String value) {
    setRentalOrderItemId(value);
    return this;
  }
}
