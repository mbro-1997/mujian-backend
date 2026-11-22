package com.mujian.backend.prop.dto;

import java.time.*;

public class QrExceptionQueryDTO extends PropPageFilterDTO {
  private String factoryId;

  private String exceptionType;

  private String factoryKeyword;

  private String keyword;

  private String status;

  private LocalDateTime today;

  private LocalDateTime tomorrow;

  /** 读取工厂标识，供请求绑定或查询条件传递使用。 */
  public String getFactoryId() {
    return factoryId;
  }

  /** 接收工厂标识，保持请求绑定或查询条件传递的字段含义。 */
  public void setFactoryId(String value) {
    factoryId = value;
  }

  /** 读取异常类型，供请求绑定或查询条件传递使用。 */
  public String getExceptionType() {
    return exceptionType;
  }

  /** 接收异常类型，保持请求绑定或查询条件传递的字段含义。 */
  public void setExceptionType(String value) {
    exceptionType = value;
  }

  /** 读取工厂关键词条件，供请求绑定或查询条件传递使用。 */
  public String getFactoryKeyword() {
    return factoryKeyword;
  }

  /** 接收工厂关键词条件，保持请求绑定或查询条件传递的字段含义。 */
  public void setFactoryKeyword(String value) {
    factoryKeyword = value;
  }

  /** 读取关键词查询条件，供请求绑定或查询条件传递使用。 */
  public String getKeyword() {
    return keyword;
  }

  /** 接收关键词查询条件，保持请求绑定或查询条件传递的字段含义。 */
  public void setKeyword(String value) {
    keyword = value;
  }

  /** 读取状态筛选条件，供请求绑定或查询条件传递使用。 */
  public String getStatus() {
    return status;
  }

  /** 接收状态筛选条件，保持请求绑定或查询条件传递的字段含义。 */
  public void setStatus(String value) {
    status = value;
  }

  /** 读取当天统计开始边界，供请求绑定或查询条件传递使用。 */
  public LocalDateTime getToday() {
    return today;
  }

  /** 接收当天统计开始边界，保持请求绑定或查询条件传递的字段含义。 */
  public void setToday(LocalDateTime value) {
    today = value;
  }

  /** 读取次日统计开始边界，供请求绑定或查询条件传递使用。 */
  public LocalDateTime getTomorrow() {
    return tomorrow;
  }

  /** 接收次日统计开始边界，保持请求绑定或查询条件传递的字段含义。 */
  public void setTomorrow(LocalDateTime value) {
    tomorrow = value;
  }

  /** 设置工厂标识并返回当前对象，供连续组装数据。 */
  public QrExceptionQueryDTO factoryId(String value) {
    setFactoryId(value);
    return this;
  }

  /** 设置异常类型并返回当前对象，供连续组装数据。 */
  public QrExceptionQueryDTO exceptionType(String value) {
    setExceptionType(value);
    return this;
  }

  /** 设置工厂关键词条件并返回当前对象，供连续组装数据。 */
  public QrExceptionQueryDTO factoryKeyword(String value) {
    setFactoryKeyword(value);
    return this;
  }

  /** 设置关键词查询条件并返回当前对象，供连续组装数据。 */
  public QrExceptionQueryDTO keyword(String value) {
    setKeyword(value);
    return this;
  }

  /** 设置状态筛选条件并返回当前对象，供连续组装数据。 */
  public QrExceptionQueryDTO status(String value) {
    setStatus(value);
    return this;
  }

  /** 设置当天统计开始边界并返回当前对象，供连续组装数据。 */
  public QrExceptionQueryDTO today(LocalDateTime value) {
    setToday(value);
    return this;
  }

  /** 设置次日统计开始边界并返回当前对象，供连续组装数据。 */
  public QrExceptionQueryDTO tomorrow(LocalDateTime value) {
    setTomorrow(value);
    return this;
  }
}
