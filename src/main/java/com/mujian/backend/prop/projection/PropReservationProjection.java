package com.mujian.backend.prop.projection;

public class PropReservationProjection
    extends com.mujian.backend.prop.domain.InventoryReservationDO {
  private String skuCode;

  private String propName;

  private String snCode;

  /** 读取SKU 编码，供查询结果映射使用。 */
  public String getSkuCode() {
    return skuCode;
  }

  /** 接收SKU 编码，保持查询结果映射的字段含义。 */
  public void setSkuCode(String value) {
    skuCode = value;
  }

  /** 读取道具名称，供查询结果映射使用。 */
  public String getPropName() {
    return propName;
  }

  /** 接收道具名称，保持查询结果映射的字段含义。 */
  public void setPropName(String value) {
    propName = value;
  }

  /** 读取SN 编号，供查询结果映射使用。 */
  public String getSnCode() {
    return snCode;
  }

  /** 接收SN 编号，保持查询结果映射的字段含义。 */
  public void setSnCode(String value) {
    snCode = value;
  }
}
