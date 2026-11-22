package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 ReviewPendingCounts，不是数据库实体。 */
public class ReviewPendingCountsVO {
  private String total;

  private String sku;

  private String inventory;

  private String skuStatus;

  /** 读取匹配记录总数，供接口结果展示使用。 */
  public String getTotal() {
    return total;
  }

  /** 接收匹配记录总数，保持接口结果展示的字段含义。 */
  public void setTotal(String value) {
    total = value;
  }

  /** 读取道具筛选标识，供接口结果展示使用。 */
  public String getSku() {
    return sku;
  }

  /** 接收道具筛选标识，保持接口结果展示的字段含义。 */
  public void setSku(String value) {
    sku = value;
  }

  /** 读取库存申请数量，供接口结果展示使用。 */
  public String getInventory() {
    return inventory;
  }

  /** 接收库存申请数量，保持接口结果展示的字段含义。 */
  public void setInventory(String value) {
    inventory = value;
  }

  /** 读取道具上下架状态，供接口结果展示使用。 */
  public String getSkuStatus() {
    return skuStatus;
  }

  /** 接收道具上下架状态，保持接口结果展示的字段含义。 */
  public void setSkuStatus(String value) {
    skuStatus = value;
  }

  /** 设置匹配记录总数并返回当前对象，供连续组装数据。 */
  public ReviewPendingCountsVO total(String value) {
    setTotal(value);
    return this;
  }

  /** 设置道具筛选标识并返回当前对象，供连续组装数据。 */
  public ReviewPendingCountsVO sku(String value) {
    setSku(value);
    return this;
  }

  /** 设置库存申请数量并返回当前对象，供连续组装数据。 */
  public ReviewPendingCountsVO inventory(String value) {
    setInventory(value);
    return this;
  }

  /** 设置道具上下架状态并返回当前对象，供连续组装数据。 */
  public ReviewPendingCountsVO skuStatus(String value) {
    setSkuStatus(value);
    return this;
  }
}
