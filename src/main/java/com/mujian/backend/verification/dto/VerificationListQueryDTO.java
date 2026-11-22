package com.mujian.backend.verification.dto;

import java.time.*;
import java.util.*;

/** 认证审核列表的明确HTTP筛选参数。 */
public class VerificationListQueryDTO {
  private String page;
  private String pageSize;
  private String status;
  private String keyword;
  private String submittedFrom;
  private String submittedTo;

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

  /** 读取status。 */
  public String getStatus() {
    return status;
  }

  /** 设置status。 */
  public void setStatus(String status) {
    this.status = status;
  }

  /** 读取keyword。 */
  public String getKeyword() {
    return keyword;
  }

  /** 设置keyword。 */
  public void setKeyword(String keyword) {
    this.keyword = keyword;
  }

  /** 读取submittedFrom。 */
  public String getSubmittedFrom() {
    return submittedFrom;
  }

  /** 设置submittedFrom。 */
  public void setSubmittedFrom(String submittedFrom) {
    this.submittedFrom = submittedFrom;
  }

  /** 读取submittedTo。 */
  public String getSubmittedTo() {
    return submittedTo;
  }

  /** 设置submittedTo。 */
  public void setSubmittedTo(String submittedTo) {
    this.submittedTo = submittedTo;
  }

  /** 组装page。 */
  public VerificationListQueryDTO page(String page) {
    this.page = page;
    return this;
  }

  /** 组装pageSize。 */
  public VerificationListQueryDTO pageSize(String pageSize) {
    this.pageSize = pageSize;
    return this;
  }

  /** 组装status。 */
  public VerificationListQueryDTO status(String status) {
    this.status = status;
    return this;
  }

  /** 组装keyword。 */
  public VerificationListQueryDTO keyword(String keyword) {
    this.keyword = keyword;
    return this;
  }

  /** 组装submittedFrom。 */
  public VerificationListQueryDTO submittedFrom(String submittedFrom) {
    this.submittedFrom = submittedFrom;
    return this;
  }

  /** 组装submittedTo。 */
  public VerificationListQueryDTO submittedTo(String submittedTo) {
    this.submittedTo = submittedTo;
    return this;
  }
}
