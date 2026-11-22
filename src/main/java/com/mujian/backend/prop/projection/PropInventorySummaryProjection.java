package com.mujian.backend.prop.projection;

public class PropInventorySummaryProjection {
  private String id;

  private String factoryName;

  private String stockId;

  private Integer formed;

  private Integer totalQuantity;

  private Integer availableQuantity;

  private Integer snTotal;

  private Integer snAvailable;

  /** 读取记录标识，供查询结果映射使用。 */
  public String getId() {
    return id;
  }

  /** 接收记录标识，保持查询结果映射的字段含义。 */
  public void setId(String value) {
    id = value;
  }

  /** 读取factoryName，供查询结果映射使用。 */
  public String getFactoryName() {
    return factoryName;
  }

  /** 接收factoryName，保持查询结果映射的字段含义。 */
  public void setFactoryName(String value) {
    factoryName = value;
  }

  /** 读取库存台账标识，供查询结果映射使用。 */
  public String getStockId() {
    return stockId;
  }

  /** 接收库存台账标识，保持查询结果映射的字段含义。 */
  public void setStockId(String value) {
    stockId = value;
  }

  /** 读取是否已形成库存，供查询结果映射使用。 */
  public Integer getFormed() {
    return formed;
  }

  /** 接收是否已形成库存，保持查询结果映射的字段含义。 */
  public void setFormed(Integer value) {
    formed = value;
  }

  /** 读取总数量，供查询结果映射使用。 */
  public Integer getTotalQuantity() {
    return totalQuantity;
  }

  /** 接收总数量，保持查询结果映射的字段含义。 */
  public void setTotalQuantity(Integer value) {
    totalQuantity = value;
  }

  /** 读取当前可用数量，供查询结果映射使用。 */
  public Integer getAvailableQuantity() {
    return availableQuantity;
  }

  /** 接收当前可用数量，保持查询结果映射的字段含义。 */
  public void setAvailableQuantity(Integer value) {
    availableQuantity = value;
  }

  /** 读取实物资产总数，供查询结果映射使用。 */
  public Integer getSnTotal() {
    return snTotal;
  }

  /** 接收实物资产总数，保持查询结果映射的字段含义。 */
  public void setSnTotal(Integer value) {
    snTotal = value;
  }

  /** 读取可用实物资产数量，供查询结果映射使用。 */
  public Integer getSnAvailable() {
    return snAvailable;
  }

  /** 接收可用实物资产数量，保持查询结果映射的字段含义。 */
  public void setSnAvailable(Integer value) {
    snAvailable = value;
  }
}
