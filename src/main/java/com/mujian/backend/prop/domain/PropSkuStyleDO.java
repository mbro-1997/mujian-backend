package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** prop_sku_style：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropSkuStyleDO extends BaseDO {

  /** SKU ID */
  private String skuId;

  /** 风格编码 */
  private String styleCode;

  /** 排序 */
  private Integer sortOrder;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 读取SKU ID，供持久化映射使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收SKU ID，保持持久化映射的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取风格编码，供持久化映射使用。 */
  public String getStyleCode() {
    return styleCode;
  }

  /** 接收风格编码，保持持久化映射的字段含义。 */
  public void setStyleCode(String value) {
    styleCode = value;
  }

  /** 读取排序，供持久化映射使用。 */
  public Integer getSortOrder() {
    return sortOrder;
  }

  /** 接收排序，保持持久化映射的字段含义。 */
  public void setSortOrder(Integer value) {
    sortOrder = value;
  }

  /** 读取创建时间，供持久化映射使用。 */
  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /** 接收创建时间，保持持久化映射的字段含义。 */
  public void setCreatedAt(LocalDateTime value) {
    createdAt = value;
  }

  /** 设置记录标识并返回当前对象，供连续组装数据。 */
  public PropSkuStyleDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置SKU ID并返回当前对象，供连续组装数据。 */
  public PropSkuStyleDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置风格编码并返回当前对象，供连续组装数据。 */
  public PropSkuStyleDO styleCode(String value) {
    setStyleCode(value);
    return this;
  }

  /** 设置排序并返回当前对象，供连续组装数据。 */
  public PropSkuStyleDO sortOrder(Integer value) {
    setSortOrder(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public PropSkuStyleDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }
}
