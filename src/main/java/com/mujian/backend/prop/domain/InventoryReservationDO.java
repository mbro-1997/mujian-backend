package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** inventory_reservation：正式结构化字段定义；不得直接作为HTTP响应。 */
public class InventoryReservationDO extends BaseDO {

  /** SKU ID */
  private String skuId;

  /** SN ID */
  private String snId;

  /** 占用数量 */
  private Integer quantity;

  /** 来源结算批次 ID */
  private String checkoutBatchId;

  /** 来源结算明细快照 ID */
  private String checkoutBatchItemSnapshotId;

  /** 租赁订单 ID */
  private String rentalOrderId;

  /** 租赁订单明细 ID */
  private String rentalOrderItemId;

  /** 通用来源类型 */
  private String sourceType;

  /** 通用来源 ID */
  private String sourceId;

  /** 租期开始日期 */
  private LocalDate rentalStartDate;

  /** 租期结束日期 */
  private LocalDate rentalEndDate;

  /** 实际占用开始日期 */
  private LocalDate occupyStartDate;

  /** 实际占用结束日期 */
  private LocalDate occupyEndDate;

  /** 占用状态 */
  private String reservationStatus;

  /** 过期时间 */
  private LocalDateTime expiresAt;

  /** 占用幂等键 */
  private String idempotencyKey;

  /** 乐观锁版本 */
  private Integer version;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;

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

  /** 读取占用数量，供持久化映射使用。 */
  public Integer getQuantity() {
    return quantity;
  }

  /** 接收占用数量，保持持久化映射的字段含义。 */
  public void setQuantity(Integer value) {
    quantity = value;
  }

  /** 读取来源结算批次 ID，供持久化映射使用。 */
  public String getCheckoutBatchId() {
    return checkoutBatchId;
  }

  /** 接收来源结算批次 ID，保持持久化映射的字段含义。 */
  public void setCheckoutBatchId(String value) {
    checkoutBatchId = value;
  }

  /** 读取来源结算明细快照 ID，供持久化映射使用。 */
  public String getCheckoutBatchItemSnapshotId() {
    return checkoutBatchItemSnapshotId;
  }

  /** 接收来源结算明细快照 ID，保持持久化映射的字段含义。 */
  public void setCheckoutBatchItemSnapshotId(String value) {
    checkoutBatchItemSnapshotId = value;
  }

  /** 读取租赁订单 ID，供持久化映射使用。 */
  public String getRentalOrderId() {
    return rentalOrderId;
  }

  /** 接收租赁订单 ID，保持持久化映射的字段含义。 */
  public void setRentalOrderId(String value) {
    rentalOrderId = value;
  }

  /** 读取租赁订单明细 ID，供持久化映射使用。 */
  public String getRentalOrderItemId() {
    return rentalOrderItemId;
  }

  /** 接收租赁订单明细 ID，保持持久化映射的字段含义。 */
  public void setRentalOrderItemId(String value) {
    rentalOrderItemId = value;
  }

  /** 读取通用来源类型，供持久化映射使用。 */
  public String getSourceType() {
    return sourceType;
  }

  /** 接收通用来源类型，保持持久化映射的字段含义。 */
  public void setSourceType(String value) {
    sourceType = value;
  }

  /** 读取通用来源 ID，供持久化映射使用。 */
  public String getSourceId() {
    return sourceId;
  }

  /** 接收通用来源 ID，保持持久化映射的字段含义。 */
  public void setSourceId(String value) {
    sourceId = value;
  }

  /** 读取租期开始日期，供持久化映射使用。 */
  public LocalDate getRentalStartDate() {
    return rentalStartDate;
  }

  /** 接收租期开始日期，保持持久化映射的字段含义。 */
  public void setRentalStartDate(LocalDate value) {
    rentalStartDate = value;
  }

  /** 读取租期结束日期，供持久化映射使用。 */
  public LocalDate getRentalEndDate() {
    return rentalEndDate;
  }

  /** 接收租期结束日期，保持持久化映射的字段含义。 */
  public void setRentalEndDate(LocalDate value) {
    rentalEndDate = value;
  }

  /** 读取实际占用开始日期，供持久化映射使用。 */
  public LocalDate getOccupyStartDate() {
    return occupyStartDate;
  }

  /** 接收实际占用开始日期，保持持久化映射的字段含义。 */
  public void setOccupyStartDate(LocalDate value) {
    occupyStartDate = value;
  }

  /** 读取实际占用结束日期，供持久化映射使用。 */
  public LocalDate getOccupyEndDate() {
    return occupyEndDate;
  }

  /** 接收实际占用结束日期，保持持久化映射的字段含义。 */
  public void setOccupyEndDate(LocalDate value) {
    occupyEndDate = value;
  }

  /** 读取占用状态，供持久化映射使用。 */
  public String getReservationStatus() {
    return reservationStatus;
  }

  /** 接收占用状态，保持持久化映射的字段含义。 */
  public void setReservationStatus(String value) {
    reservationStatus = value;
  }

  /** 读取过期时间，供持久化映射使用。 */
  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  /** 接收过期时间，保持持久化映射的字段含义。 */
  public void setExpiresAt(LocalDateTime value) {
    expiresAt = value;
  }

  /** 读取占用幂等键，供持久化映射使用。 */
  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  /** 接收占用幂等键，保持持久化映射的字段含义。 */
  public void setIdempotencyKey(String value) {
    idempotencyKey = value;
  }

  /** 读取乐观锁版本，供持久化映射使用。 */
  public Integer getVersion() {
    return version;
  }

  /** 接收乐观锁版本，保持持久化映射的字段含义。 */
  public void setVersion(Integer value) {
    version = value;
  }

  /** 读取创建时间，供持久化映射使用。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 接收创建时间，保持持久化映射的字段含义。 */
  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  /** 读取更新时间，供持久化映射使用。 */
  public LocalDateTime getUpdatedAt() {
    return updatedAt;
  }

  /** 接收更新时间，保持持久化映射的字段含义。 */
  public void setUpdatedAt(LocalDateTime value) {
    updatedAt = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置SKU ID并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置SN ID并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO snId(String value) {
    setSnId(value);
    return this;
  }

  /** 设置占用数量并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO quantity(Integer value) {
    setQuantity(value);
    return this;
  }

  /** 设置来源结算批次 ID并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO checkoutBatchId(String value) {
    setCheckoutBatchId(value);
    return this;
  }

  /** 设置来源结算明细快照 ID并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO checkoutBatchItemSnapshotId(String value) {
    setCheckoutBatchItemSnapshotId(value);
    return this;
  }

  /** 设置租赁订单 ID并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO rentalOrderId(String value) {
    setRentalOrderId(value);
    return this;
  }

  /** 设置租赁订单明细 ID并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO rentalOrderItemId(String value) {
    setRentalOrderItemId(value);
    return this;
  }

  /** 设置通用来源类型并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO sourceType(String value) {
    setSourceType(value);
    return this;
  }

  /** 设置通用来源 ID并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO sourceId(String value) {
    setSourceId(value);
    return this;
  }

  /** 设置租期开始日期并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO rentalStartDate(LocalDate value) {
    setRentalStartDate(value);
    return this;
  }

  /** 设置租期结束日期并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO rentalEndDate(LocalDate value) {
    setRentalEndDate(value);
    return this;
  }

  /** 设置实际占用开始日期并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO occupyStartDate(LocalDate value) {
    setOccupyStartDate(value);
    return this;
  }

  /** 设置实际占用结束日期并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO occupyEndDate(LocalDate value) {
    setOccupyEndDate(value);
    return this;
  }

  /** 设置占用状态并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO reservationStatus(String value) {
    setReservationStatus(value);
    return this;
  }

  /** 设置过期时间并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO expiresAt(LocalDateTime value) {
    setExpiresAt(value);
    return this;
  }

  /** 设置占用幂等键并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO idempotencyKey(String value) {
    setIdempotencyKey(value);
    return this;
  }

  /** 设置乐观锁版本并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO version(Integer value) {
    setVersion(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }

  /** 设置更新时间并返回当前对象，供连续组装数据。 */
  public InventoryReservationDO updatedAt(LocalDateTime value) {
    setUpdatedAt(value);
    return this;
  }
}
