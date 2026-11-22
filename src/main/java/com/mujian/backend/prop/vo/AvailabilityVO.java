package com.mujian.backend.prop.vo;

/** 来自D2-C接口契约 Availability，不是数据库实体。 */
public class AvailabilityVO {
  private String startDate;

  private String endDate;

  private Integer maxRentableQuantity;

  private String rentableStatus;

  private String reasonCode;

  /** 读取查询开始日期，供接口结果展示使用。 */
  public String getStartDate() {
    return startDate;
  }

  /** 接收查询开始日期，保持接口结果展示的字段含义。 */
  public void setStartDate(String value) {
    startDate = value;
  }

  /** 读取查询结束日期，供接口结果展示使用。 */
  public String getEndDate() {
    return endDate;
  }

  /** 接收查询结束日期，保持接口结果展示的字段含义。 */
  public void setEndDate(String value) {
    endDate = value;
  }

  /** 读取maxRentableQuantity，供接口结果展示使用。 */
  public Integer getMaxRentableQuantity() {
    return maxRentableQuantity;
  }

  /** 接收maxRentableQuantity，保持接口结果展示的字段含义。 */
  public void setMaxRentableQuantity(Integer value) {
    maxRentableQuantity = value;
  }

  /** 读取rentableStatus，供接口结果展示使用。 */
  public String getRentableStatus() {
    return rentableStatus;
  }

  /** 接收rentableStatus，保持接口结果展示的字段含义。 */
  public void setRentableStatus(String value) {
    rentableStatus = value;
  }

  /** 读取reasonCode，供接口结果展示使用。 */
  public String getReasonCode() {
    return reasonCode;
  }

  /** 接收reasonCode，保持接口结果展示的字段含义。 */
  public void setReasonCode(String value) {
    reasonCode = value;
  }

  /** 设置查询开始日期并返回当前对象，供连续组装数据。 */
  public AvailabilityVO startDate(String value) {
    setStartDate(value);
    return this;
  }

  /** 设置查询结束日期并返回当前对象，供连续组装数据。 */
  public AvailabilityVO endDate(String value) {
    setEndDate(value);
    return this;
  }

  /** 设置maxRentableQuantity并返回当前对象，供连续组装数据。 */
  public AvailabilityVO maxRentableQuantity(Integer value) {
    setMaxRentableQuantity(value);
    return this;
  }

  /** 设置rentableStatus并返回当前对象，供连续组装数据。 */
  public AvailabilityVO rentableStatus(String value) {
    setRentableStatus(value);
    return this;
  }

  /** 设置reasonCode并返回当前对象，供连续组装数据。 */
  public AvailabilityVO reasonCode(String value) {
    setReasonCode(value);
    return this;
  }
}
