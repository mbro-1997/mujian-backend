package com.mujian.backend.prop.projection;

public class PropQuantityProjection {
  private String skuId;

  private java.math.BigDecimal amount;

  /** 读取道具标识，供查询结果映射使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具标识，保持查询结果映射的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取统计数量，供查询结果映射使用。 */
  public java.math.BigDecimal getAmount() {
    return amount;
  }

  /** 接收统计数量，保持查询结果映射的字段含义。 */
  public void setAmount(java.math.BigDecimal value) {
    amount = value;
  }
}
