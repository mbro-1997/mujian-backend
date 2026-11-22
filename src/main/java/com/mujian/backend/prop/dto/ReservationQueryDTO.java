package com.mujian.backend.prop.dto;

import java.time.*;

public class ReservationQueryDTO extends PropPageFilterDTO {
  private String skuId;

  private String snId;

  private String status;

  private LocalDate start;

  private LocalDate end;

  private String keyword;

  private String startDate;
  private String endDate;

  /** 读取道具标识，供请求绑定或查询条件传递使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收道具标识，保持请求绑定或查询条件传递的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取SN ID，供请求绑定或查询条件传递使用。 */
  public String getSnId() {
    return snId;
  }

  /** 接收SN ID，保持请求绑定或查询条件传递的字段含义。 */
  public void setSnId(String value) {
    snId = value;
  }

  /** 读取状态筛选条件，供请求绑定或查询条件传递使用。 */
  public String getStatus() {
    return status;
  }

  /** 接收状态筛选条件，保持请求绑定或查询条件传递的字段含义。 */
  public void setStatus(String value) {
    status = value;
  }

  /** 读取查询起始时间，供请求绑定或查询条件传递使用。 */
  public LocalDate getStart() {
    return start;
  }

  /** 接收查询起始时间，保持请求绑定或查询条件传递的字段含义。 */
  public void setStart(LocalDate value) {
    start = value;
  }

  /** 读取查询结束边界，供请求绑定或查询条件传递使用。 */
  public LocalDate getEnd() {
    return end;
  }

  /** 接收查询结束边界，保持请求绑定或查询条件传递的字段含义。 */
  public void setEnd(LocalDate value) {
    end = value;
  }

  /** 读取关键词查询条件，供请求绑定或查询条件传递使用。 */
  public String getKeyword() {
    return keyword;
  }

  /** 接收关键词查询条件，保持请求绑定或查询条件传递的字段含义。 */
  public void setKeyword(String value) {
    keyword = value;
  }

  /** 设置道具标识并返回当前对象，供连续组装数据。 */
  public ReservationQueryDTO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置SN ID并返回当前对象，供连续组装数据。 */
  public ReservationQueryDTO snId(String value) {
    setSnId(value);
    return this;
  }

  /** 设置状态筛选条件并返回当前对象，供连续组装数据。 */
  public ReservationQueryDTO status(String value) {
    setStatus(value);
    return this;
  }

  /** 设置查询起始时间并返回当前对象，供连续组装数据。 */
  public ReservationQueryDTO start(LocalDate value) {
    setStart(value);
    return this;
  }

  /** 设置查询结束边界并返回当前对象，供连续组装数据。 */
  public ReservationQueryDTO end(LocalDate value) {
    setEnd(value);
    return this;
  }

  /** 设置关键词查询条件并返回当前对象，供连续组装数据。 */
  public ReservationQueryDTO keyword(String value) {
    setKeyword(value);
    return this;
  }

  /** 读取startDate查询条件。 */
  public String getStartDate() {
    return startDate;
  }

  /** 设置startDate查询条件。 */
  public void setStartDate(String value) {
    startDate = value;
  }

  /** 读取endDate查询条件。 */
  public String getEndDate() {
    return endDate;
  }

  /** 设置endDate查询条件。 */
  public void setEndDate(String value) {
    endDate = value;
  }
}
