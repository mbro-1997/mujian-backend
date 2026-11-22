package com.mujian.backend.account.vo;

import java.time.*;
import java.util.*;

/** 可阅读的规则正文及版本信息。 */
public class RuleDocumentVO {
  private String documentId;
  private String code;
  private String title;
  private String version;
  private String contentHash;
  private String effectiveAt;
  private String contentType;
  private String contentText;
  private String status;

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

  /** 读取contentType。 */
  public String getContentType() {
    return contentType;
  }

  /** 设置contentType。 */
  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  /** 读取contentText。 */
  public String getContentText() {
    return contentText;
  }

  /** 设置contentText。 */
  public void setContentText(String contentText) {
    this.contentText = contentText;
  }

  /** 读取status。 */
  public String getStatus() {
    return status;
  }

  /** 设置status。 */
  public void setStatus(String status) {
    this.status = status;
  }

  /** 组装documentId。 */
  public RuleDocumentVO documentId(String documentId) {
    this.documentId = documentId;
    return this;
  }

  /** 组装code。 */
  public RuleDocumentVO code(String code) {
    this.code = code;
    return this;
  }

  /** 组装title。 */
  public RuleDocumentVO title(String title) {
    this.title = title;
    return this;
  }

  /** 组装version。 */
  public RuleDocumentVO version(String version) {
    this.version = version;
    return this;
  }

  /** 组装contentHash。 */
  public RuleDocumentVO contentHash(String contentHash) {
    this.contentHash = contentHash;
    return this;
  }

  /** 组装effectiveAt。 */
  public RuleDocumentVO effectiveAt(String effectiveAt) {
    this.effectiveAt = effectiveAt;
    return this;
  }

  /** 组装contentType。 */
  public RuleDocumentVO contentType(String contentType) {
    this.contentType = contentType;
    return this;
  }

  /** 组装contentText。 */
  public RuleDocumentVO contentText(String contentText) {
    this.contentText = contentText;
    return this;
  }

  /** 组装status。 */
  public RuleDocumentVO status(String status) {
    this.status = status;
    return this;
  }
}
