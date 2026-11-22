package com.mujian.backend.factory.dto;

import java.time.*;
import java.util.*;

/** 工厂列表HTTP查询参数，不携带服务端权限或分页派生字段。 */
public class FactoryListQueryDTO {
  private String page;
  private String pageSize;
  private String keyword;
  private String status;
  private String from;
  private String to;

  /** 读取page。 */
  public String getPage() {
    return page;
  }

  /** 设置page。 */
  public void setPage(String page) {
    this.page = page;
  }

  /** 读取pageSize。 */
  public String getPageSize() {
    return pageSize;
  }

  /** 设置pageSize。 */
  public void setPageSize(String pageSize) {
    this.pageSize = pageSize;
  }

  /** 读取keyword。 */
  public String getKeyword() {
    return keyword;
  }

  /** 设置keyword。 */
  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }

  /** 读取status。 */
  public String getStatus() {
    return status;
  }

  /** 设置status。 */
  public void setStatus(String status) {
    this.status = status;
  }

  /** 读取from。 */
  public String getFrom() {
    return from;
  }

  /** 设置from。 */
  public void setFrom(String from) {
    this.from = from;
  }

  /** 读取to。 */
  public String getTo() {
    return to;
  }

  /** 设置to。 */
  public void setTo(String to) {
    this.to = to;
  }

  /** 组装page。 */
  public FactoryListQueryDTO page(String page) {
    this.page = page;
    return this;
  }

  /** 组装pageSize。 */
  public FactoryListQueryDTO pageSize(String pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  /** 组装keyword。 */
  public FactoryListQueryDTO keyword(String keyword) {
    this.keyword = keyword;
    return this;
  }

  /** 组装status。 */
  public FactoryListQueryDTO status(String status) {
    this.status = status;
    return this;
  }

  /** 组装from。 */
  public FactoryListQueryDTO from(String from) {
    this.from = from;
    return this;
  }

  /** 组装to。 */
  public FactoryListQueryDTO to(String to) {
    this.to = to;
    return this;
  }
}
