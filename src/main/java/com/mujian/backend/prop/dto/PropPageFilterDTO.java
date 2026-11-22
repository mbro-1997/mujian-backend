package com.mujian.backend.prop.dto;

import java.time.*;

public class PropPageFilterDTO {
  private long offset;

  private int limit;

  private String page;
  private String pageSize;

  /** 读取数据库分页偏移，供请求绑定或查询条件传递使用。 */
  public long getOffset() {
    return offset;
  }

  /** 接收数据库分页偏移，保持请求绑定或查询条件传递的字段含义。 */
  public void setOffset(long value) {
    offset = value;
  }

  /** 读取数据库单页上限，供请求绑定或查询条件传递使用。 */
  public int getLimit() {
    return limit;
  }

  /** 接收数据库单页上限，保持请求绑定或查询条件传递的字段含义。 */
  public void setLimit(int value) {
    limit = value;
  }

  /** 按long计算分页偏移，避免页码乘法溢出。 */
  public PropPageFilterDTO offset(long value) {
    setOffset(value);
    return this;
  }

  /** 设置数据库单页上限并返回当前对象，供连续组装数据。 */
  public PropPageFilterDTO limit(int value) {
    setLimit(value);
    return this;
  }

  /** 读取page查询条件。 */
  public String getPage() {
    return page;
  }

  /** 设置page查询条件。 */
  public void setPage(String value) {
    page = value;
  }

  /** 读取pageSize查询条件。 */
  public String getPageSize() {
    return pageSize;
  }

  /** 设置pageSize查询条件。 */
  public void setPageSize(String value) {
    pageSize = value;
  }
}
