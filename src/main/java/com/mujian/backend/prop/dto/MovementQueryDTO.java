package com.mujian.backend.prop.dto;

import java.time.*;

public class MovementQueryDTO extends PropPageFilterDTO {
  private String skuId;

  private String sourceType;

  private String movementType;

  private LocalDateTime start;

  private LocalDateTime end;

  private String startDate;
  private String endDate;

  /** 读取台账查询目标标识，供请求绑定或查询条件传递使用。 */
  public String getSkuId() {
    return skuId;
  }

  /** 接收台账查询目标标识，保持请求绑定或查询条件传递的字段含义。 */
  public void setSkuId(String value) {
    skuId = value;
  }

  /** 读取业务来源类型，供请求绑定或查询条件传递使用。 */
  public String getSourceType() {
    return sourceType;
  }

  /** 接收业务来源类型，保持请求绑定或查询条件传递的字段含义。 */
  public void setSourceType(String value) {
    sourceType = value;
  }

  /** 读取流水类型，供请求绑定或查询条件传递使用。 */
  public String getMovementType() {
    return movementType;
  }

  /** 接收流水类型，保持请求绑定或查询条件传递的字段含义。 */
  public void setMovementType(String value) {
    movementType = value;
  }

  /** 读取查询起始时间，供请求绑定或查询条件传递使用。 */
  public LocalDateTime getStart() {
    return start;
  }

  /** 接收查询起始时间，保持请求绑定或查询条件传递的字段含义。 */
  public void setStart(LocalDateTime value) {
    start = value;
  }

  /** 读取查询结束边界，供请求绑定或查询条件传递使用。 */
  public LocalDateTime getEnd() {
    return end;
  }

  /** 接收查询结束边界，保持请求绑定或查询条件传递的字段含义。 */
  public void setEnd(LocalDateTime value) {
    end = value;
  }

  /** 设置台账查询目标标识并返回当前对象，供连续组装数据。 */
  public MovementQueryDTO skuId(String value) {
    setSkuId(value);
    return this;
  }

  /** 设置业务来源类型并返回当前对象，供连续组装数据。 */
  public MovementQueryDTO sourceType(String value) {
    setSourceType(value);
    return this;
  }

  /** 设置流水类型并返回当前对象，供连续组装数据。 */
  public MovementQueryDTO movementType(String value) {
    setMovementType(value);
    return this;
  }

  /** 设置查询起始时间并返回当前对象，供连续组装数据。 */
  public MovementQueryDTO start(LocalDateTime value) {
    setStart(value);
    return this;
  }

  /** 设置查询结束边界并返回当前对象，供连续组装数据。 */
  public MovementQueryDTO end(LocalDateTime value) {
    setEnd(value);
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
