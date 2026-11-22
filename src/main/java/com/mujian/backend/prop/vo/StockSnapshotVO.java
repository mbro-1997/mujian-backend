package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 StockSnapshot，不是数据库实体。 */
public class StockSnapshotVO {
  private Integer totalQuantity;

  private Integer rentableBaseQuantity;

  private Integer availableQuantity;

  private Integer lockedQuantity;

  private Integer rentingQuantity;

  private Integer lostQuantity;

  private Integer scrappedQuantity;

  private String lastCountedAt;

  private String lastCountedBy;

  /** 读取总数量，供接口结果展示使用。 */
  public Integer getTotalQuantity() {
    return totalQuantity;
  }

  /** 接收总数量，保持接口结果展示的字段含义。 */
  public void setTotalQuantity(Integer value) {
    totalQuantity = value;
  }

  /** 读取可租库存基数，供接口结果展示使用。 */
  public Integer getRentableBaseQuantity() {
    return rentableBaseQuantity;
  }

  /** 接收可租库存基数，保持接口结果展示的字段含义。 */
  public void setRentableBaseQuantity(Integer value) {
    rentableBaseQuantity = value;
  }

  /** 读取当前可用数量，供接口结果展示使用。 */
  public Integer getAvailableQuantity() {
    return availableQuantity;
  }

  /** 接收当前可用数量，保持接口结果展示的字段含义。 */
  public void setAvailableQuantity(Integer value) {
    availableQuantity = value;
  }

  /** 读取已锁定数量，供接口结果展示使用。 */
  public Integer getLockedQuantity() {
    return lockedQuantity;
  }

  /** 接收已锁定数量，保持接口结果展示的字段含义。 */
  public void setLockedQuantity(Integer value) {
    lockedQuantity = value;
  }

  /** 读取租赁中数量，供接口结果展示使用。 */
  public Integer getRentingQuantity() {
    return rentingQuantity;
  }

  /** 接收租赁中数量，保持接口结果展示的字段含义。 */
  public void setRentingQuantity(Integer value) {
    rentingQuantity = value;
  }

  /** 读取丢失数量，供接口结果展示使用。 */
  public Integer getLostQuantity() {
    return lostQuantity;
  }

  /** 接收丢失数量，保持接口结果展示的字段含义。 */
  public void setLostQuantity(Integer value) {
    lostQuantity = value;
  }

  /** 读取报废数量，供接口结果展示使用。 */
  public Integer getScrappedQuantity() {
    return scrappedQuantity;
  }

  /** 接收报废数量，保持接口结果展示的字段含义。 */
  public void setScrappedQuantity(Integer value) {
    scrappedQuantity = value;
  }

  /** 读取最近盘点时间，供接口结果展示使用。 */
  public String getLastCountedAt() {
    return lastCountedAt;
  }

  /** 接收最近盘点时间，保持接口结果展示的字段含义。 */
  public void setLastCountedAt(String value) {
    lastCountedAt = value;
  }

  /** 读取最近盘点人，供接口结果展示使用。 */
  public String getLastCountedBy() {
    return lastCountedBy;
  }

  /** 接收最近盘点人，保持接口结果展示的字段含义。 */
  public void setLastCountedBy(String value) {
    lastCountedBy = value;
  }

  /** 设置总数量并返回当前对象，供连续组装数据。 */
  public StockSnapshotVO totalQuantity(Integer value) {
    setTotalQuantity(value);
    return this;
  }

  /** 设置可租库存基数并返回当前对象，供连续组装数据。 */
  public StockSnapshotVO rentableBaseQuantity(Integer value) {
    setRentableBaseQuantity(value);
    return this;
  }

  /** 设置当前可用数量并返回当前对象，供连续组装数据。 */
  public StockSnapshotVO availableQuantity(Integer value) {
    setAvailableQuantity(value);
    return this;
  }

  /** 设置已锁定数量并返回当前对象，供连续组装数据。 */
  public StockSnapshotVO lockedQuantity(Integer value) {
    setLockedQuantity(value);
    return this;
  }

  /** 设置租赁中数量并返回当前对象，供连续组装数据。 */
  public StockSnapshotVO rentingQuantity(Integer value) {
    setRentingQuantity(value);
    return this;
  }

  /** 设置丢失数量并返回当前对象，供连续组装数据。 */
  public StockSnapshotVO lostQuantity(Integer value) {
    setLostQuantity(value);
    return this;
  }

  /** 设置报废数量并返回当前对象，供连续组装数据。 */
  public StockSnapshotVO scrappedQuantity(Integer value) {
    setScrappedQuantity(value);
    return this;
  }

  /** 设置最近盘点时间并返回当前对象，供连续组装数据。 */
  public StockSnapshotVO lastCountedAt(String value) {
    setLastCountedAt(value);
    return this;
  }

  /** 设置最近盘点人并返回当前对象，供连续组装数据。 */
  public StockSnapshotVO lastCountedBy(String value) {
    setLastCountedBy(value);
    return this;
  }
}
