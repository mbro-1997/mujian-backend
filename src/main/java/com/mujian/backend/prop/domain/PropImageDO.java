package com.mujian.backend.prop.domain;

import com.mujian.backend.common.base.BaseDO;
import java.time.*;

/** prop_image：正式结构化字段定义；不得直接作为HTTP响应。 */
public class PropImageDO extends BaseDO {

  /** SKU ID */
  private String skuId;

  /** 图片 URL */
  private String imageUrl;

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

  /** 读取图片 URL，供持久化映射使用。 */
  public String getImageUrl() {
    return imageUrl;
  }

  /** 接收图片 URL，保持持久化映射的字段含义。 */
  public void setImageUrl(String value) {
    imageUrl = value;
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
  public PropImageDO id(String value) {
    setId(value);
    return this;
  }

  /** 设置SKU ID并返回当前对象，供连续组装数据。 */
  public PropImageDO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置图片 URL并返回当前对象，供连续组装数据。 */
  public PropImageDO imageUrl(String value) {
    setImageUrl(value);
    return this;
  }

  /** 设置排序并返回当前对象，供连续组装数据。 */
  public PropImageDO sortOrder(Integer value) {
    setSortOrder(value);
    return this;
  }

  /** 设置创建时间并返回当前对象，供连续组装数据。 */
  public PropImageDO createdAt(LocalDateTime value) {
    setCreatedAt(value);
    return this;
  }
}
