package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** prop_sku_stock：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropSkuStockDO extends BaseDO {

  /** SKU ID */
  private String skuId;

  /** 总数量 */
  private Integer totalQuantity;

  /** 可租库存基数 */
  private Integer rentableBaseQuantity;

  /** 当前可用数量 */
  private Integer availableQuantity;

  /** 已锁定数量 */
  private Integer lockedQuantity;

  /** 租赁中数量 */
  private Integer rentingQuantity;

  /** 丢失数量 */
  private Integer lostQuantity;

  /** 报废数量 */
  private Integer scrappedQuantity;

  /** 最近盘点时间 */
  private LocalDateTime lastCountedAt;

  /** 最近盘点人 */
  private String lastCountedBy;

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

  /** 读取总数量，供持久化映射使用。 */
  public Integer getTotalQuantity() {
    return totalQuantity;
  }

  /** 接收总数量，保持持久化映射的字段含义。 */
  public void setTotalQuantity(Integer value) {
    totalQuantity = value;
  }

  /** 读取可租库存基数，供持久化映射使用。 */
  public Integer getRentableBaseQuantity() {
    return rentableBaseQuantity;
  }

  /** 接收可租库存基数，保持持久化映射的字段含义。 */
  public void setRentableBaseQuantity(Integer value) {
    rentableBaseQuantity = value;
  }

  /** 读取当前可用数量，供持久化映射使用。 */
  public Integer getAvailableQuantity() {
    return availableQuantity;
  }

  /** 接收当前可用数量，保持持久化映射的字段含义。 */
  public void setAvailableQuantity(Integer value) {
    availableQuantity = value;
  }

  /** 读取已锁定数量，供持久化映射使用。 */
  public Integer getLockedQuantity() {
    return lockedQuantity;
  }

  /** 接收已锁定数量，保持持久化映射的字段含义。 */
  public void setLockedQuantity(Integer value) {
    lockedQuantity = value;
  }

  /** 读取租赁中数量，供持久化映射使用。 */
  public Integer getRentingQuantity() {
    return rentingQuantity;
  }

  /** 接收租赁中数量，保持持久化映射的字段含义。 */
  public void setRentingQuantity(Integer value) {
    rentingQuantity = value;
  }

  /** 读取丢失数量，供持久化映射使用。 */
  public Integer getLostQuantity() {
    return lostQuantity;
  }

  /** 接收丢失数量，保持持久化映射的字段含义。 */
  public void setLostQuantity(Integer value) {
    lostQuantity = value;
  }

  /** 读取报废数量，供持久化映射使用。 */
  public Integer getScrappedQuantity() {
    return scrappedQuantity;
  }

  /** 接收报废数量，保持持久化映射的字段含义。 */
  public void setScrappedQuantity(Integer value) {
    scrappedQuantity = value;
  }

  /** 读取最近盘点时间，供持久化映射使用。 */
  public LocalDateTime getLastCountedAt() {
    return lastCountedAt;
  }

  /** 接收最近盘点时间，保持持久化映射的字段含义。 */
  public void setLastCountedAt(LocalDateTime value) {
    lastCountedAt = value;
  }

  /** 读取最近盘点人，供持久化映射使用。 */
  public String getLastCountedBy() {
    return lastCountedBy;
  }

  /** 接收最近盘点人，保持持久化映射的字段含义。 */
  public void setLastCountedBy(String value) {
    lastCountedBy = value;
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
  public PropSkuStockDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置SKU ID并返回当前对象，供连续组装数据。 */
  public PropSkuStockDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置总数量并返回当前对象，供连续组装数据。 */
  public PropSkuStockDO totalQuantity(Integer value) {
    setTotalQuantity(value);
    return this;
  }

  /** 设置可租库存基数并返回当前对象，供连续组装数据。 */
  public PropSkuStockDO rentableBaseQuantity(Integer value) {
    setRentableBaseQuantity(value);
    return this;
  }

  /** 设置当前可用数量并返回当前对象，供连续组装数据。 */
  public PropSkuStockDO availableQuantity(Integer value) {
    setAvailableQuantity(value);
    return this;
  }

  /** 设置已锁定数量并返回当前对象，供连续组装数据。 */
  public PropSkuStockDO lockedQuantity(Integer value) {
    setLockedQuantity(value);
    return this;
  }

  /** 设置租赁中数量并返回当前对象，供连续组装数据。 */
  public PropSkuStockDO rentingQuantity(Integer value) {
    setRentingQuantity(value);
    return this;
  }

  /** 设置丢失数量并返回当前对象，供连续组装数据。 */
  public PropSkuStockDO lostQuantity(Integer value) {
    setLostQuantity(value);
    return this;
  }

  /** 设置报废数量并返回当前对象，供连续组装数据。 */
  public PropSkuStockDO scrappedQuantity(Integer value) {
    setScrappedQuantity(value);
    return this;
  }

  /** 设置最近盘点时间并返回当前对象，供连续组装数据。 */
  public PropSkuStockDO lastCountedAt(LocalDateTime value) {
    setLastCountedAt(value);
    return this;
  }

  /** 设置最近盘点人并返回当前对象，供连续组装数据。 */
  public PropSkuStockDO lastCountedBy(String value) {
    setLastCountedBy(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public PropSkuStockDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }

  /** 设置更新时间并返回当前对象，供连续组装数据。 */
  public PropSkuStockDO updatedAt(LocalDateTime value) {
    setUpdatedAt(value);
    return this;
  }
}
