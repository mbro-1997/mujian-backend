package com.mujian.backend.account.vo;

import java.time.*;
import java.util.*;

/** 规则文档摘要，保持注册确认信息。 */
public class RuleSummaryVO {
  private String documentId;
  private String code;
  private String title;
  private String version;
  private String contentHash;
  private String effectiveAt;

  /** 读取documentId。 */
  public String getDocumentId() {
    return documentId;
  }

  /** 设置documentId。 */
  public void setDocumentId(String documentId) {
    this.documentId = documentId;
  }

  /** 读取code。 */
  public String getCode() {
    return code;
  }

  /** 设置code。 */
  public void setCode(String code) {
    this.code = code;
  }

  /** 读取title。 */
  public String getTitle() {
    return title;
  }

  /** 设置title。 */
  public void setTitle(String title) {
    this.title = title;
  }

  /** 读取version。 */
  public String getVersion() {
    return version;
  }

  /** 设置version。 */
  public void setVersion(String version) {
    this.version = version;
  }

  /** 读取contentHash。 */
  public String getContentHash() {
    return contentHash;
  }

  /** 设置contentHash。 */
  public void setContentHash(String contentHash) {
    this.contentHash = contentHash;
  }

  /** 读取effectiveAt。 */
  public String getEffectiveAt() {
    return effectiveAt;
  }

  /** 设置effectiveAt。 */
  public void setEffectiveAt(String effectiveAt) {
    this.effectiveAt = effectiveAt;
  }

  /** 组装documentId。 */
  public RuleSummaryVO documentId(String documentId) {
    this.documentId = documentId;
    return this;
  }

  /** 组装code。 */
  public RuleSummaryVO code(String code) {
    this.code = code;
    return this;
  }

  /** 组装title。 */
  public RuleSummaryVO title(String title) {
    this.title = title;
    return this;
  }

  /** 组装version。 */
  public RuleSummaryVO version(String version) {
    this.version = version;
    return this;
  }

  /** 组装contentHash。 */
  public RuleSummaryVO contentHash(String contentHash) {
    this.contentHash = contentHash;
    return this;
  }

  /** 组装effectiveAt。 */
  public RuleSummaryVO effectiveAt(String effectiveAt) {
    this.effectiveAt = effectiveAt;
    return this;
  }
}
