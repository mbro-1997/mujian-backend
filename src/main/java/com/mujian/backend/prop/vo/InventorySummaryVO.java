package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 InventorySummary，不是数据库实体。 */
public class InventorySummaryVO {
  private Integer totalQuantity;

  private Integer availableQuantity;

  /** 读取总数量，供接口结果展示使用。 */
  public Integer getTotalQuantity() {
    return totalQuantity;
  }

  /** 接收总数量，保持接口结果展示的字段含义。 */
  public void setTotalQuantity(Integer value) {
    totalQuantity = value;
  }

  /** 读取当前可用数量，供接口结果展示使用。 */
  public Integer getAvailableQuantity() {
    return availableQuantity;
  }

  /** 接收当前可用数量，保持接口结果展示的字段含义。 */
  public void setAvailableQuantity(Integer value) {
    availableQuantity = value;
  }

  /** 设置总数量并返回当前对象，供连续组装数据。 */
  public InventorySummaryVO totalQuantity(Integer value) {
    setTotalQuantity(value);
    return this;
  }

  /** 设置当前可用数量并返回当前对象，供连续组装数据。 */
  public InventorySummaryVO availableQuantity(Integer value) {
    setAvailableQuantity(value);
    return this;
  }
}
