package com.mujian.backend.prop.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PropStockSnapshotDTO {
  @JsonProperty("schema_version")
  private Integer schemaVersion;

  @JsonProperty("total_quantity")
  private Integer totalQuantity;

  @JsonProperty("rentable_base_quantity")
  private Integer rentableBaseQuantity;

  @JsonProperty("available_quantity")
  private Integer availableQuantity;

  @JsonProperty("locked_quantity")
  private Integer lockedQuantity;

  @JsonProperty("renting_quantity")
  private Integer rentingQuantity;

  @JsonProperty("lost_quantity")
  private Integer lostQuantity;

  @JsonProperty("scrapped_quantity")
  private Integer scrappedQuantity;

  @JsonProperty("last_counted_at")
  private String lastCountedAt;

  @JsonProperty("last_counted_by")
  private String lastCountedBy;

  /** 读取快照结构版本，供请求绑定或查询条件传递使用。 */
  public Integer getSchemaVersion() {
    return schemaVersion;
  }

  /** 接收快照结构版本，保持请求绑定或查询条件传递的字段含义。 */
  public void setSchemaVersion(Integer value) {
    schemaVersion = value;
  }

  /** 读取总数量，供请求绑定或查询条件传递使用。 */
  public Integer getTotalQuantity() {
    return totalQuantity;
  }

  /** 接收总数量，保持请求绑定或查询条件传递的字段含义。 */
  public void setTotalQuantity(Integer value) {
    totalQuantity = value;
  }

  /** 读取可租库存基数，供请求绑定或查询条件传递使用。 */
  public Integer getRentableBaseQuantity() {
    return rentableBaseQuantity;
  }

  /** 接收可租库存基数，保持请求绑定或查询条件传递的字段含义。 */
  public void setRentableBaseQuantity(Integer value) {
    rentableBaseQuantity = value;
  }

  /** 读取当前可用数量，供请求绑定或查询条件传递使用。 */
  public Integer getAvailableQuantity() {
    return availableQuantity;
  }

  /** 接收当前可用数量，保持请求绑定或查询条件传递的字段含义。 */
  public void setAvailableQuantity(Integer value) {
    availableQuantity = value;
  }

  /** 读取已锁定数量，供请求绑定或查询条件传递使用。 */
  public Integer getLockedQuantity() {
    return lockedQuantity;
  }

  /** 接收已锁定数量，保持请求绑定或查询条件传递的字段含义。 */
  public void setLockedQuantity(Integer value) {
    lockedQuantity = value;
  }

  /** 读取租赁中数量，供请求绑定或查询条件传递使用。 */
  public Integer getRentingQuantity() {
    return rentingQuantity;
  }

  /** 接收租赁中数量，保持请求绑定或查询条件传递的字段含义。 */
  public void setRentingQuantity(Integer value) {
    rentingQuantity = value;
  }

  /** 读取丢失数量，供请求绑定或查询条件传递使用。 */
  public Integer getLostQuantity() {
    return lostQuantity;
  }

  /** 接收丢失数量，保持请求绑定或查询条件传递的字段含义。 */
  public void setLostQuantity(Integer value) {
    lostQuantity = value;
  }

  /** 读取报废数量，供请求绑定或查询条件传递使用。 */
  public Integer getScrappedQuantity() {
    return scrappedQuantity;
  }

  /** 接收报废数量，保持请求绑定或查询条件传递的字段含义。 */
  public void setScrappedQuantity(Integer value) {
    scrappedQuantity = value;
  }

  /** 读取最近盘点时间，供请求绑定或查询条件传递使用。 */
  public String getLastCountedAt() {
    return lastCountedAt;
  }

  /** 接收最近盘点时间，保持请求绑定或查询条件传递的字段含义。 */
  public void setLastCountedAt(String value) {
    lastCountedAt = value;
  }

  /** 读取最近盘点人，供请求绑定或查询条件传递使用。 */
  public String getLastCountedBy() {
    return lastCountedBy;
  }

  /** 接收最近盘点人，保持请求绑定或查询条件传递的字段含义。 */
  public void setLastCountedBy(String value) {
    lastCountedBy = value;
  }
}
