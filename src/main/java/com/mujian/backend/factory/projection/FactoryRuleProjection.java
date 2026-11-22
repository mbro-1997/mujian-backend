package com.mujian.backend.factory.projection;

import java.time.*;
import java.util.*;

/** 工厂履约规则文档的查询字段。 */
public class FactoryRuleProjection {
  private String id;
  private String ruleCode;
  private String ruleTitle;
  private String ruleVersion;
  private String contentHash;
  private LocalDateTime effectiveAt;
  private String contentText;
  private String ruleStatus;

  /** 读取id。 */
  public String getId() {
    return id;
  }

  /** 设置id。 */
  public void setId(String id) {
    this.id = id;
  }

  /** 读取ruleCode。 */
  public String getRuleCode() {
    return ruleCode;
  }

  /** 设置ruleCode。 */
  public void setRuleCode(String ruleCode) {
    this.ruleCode = ruleCode;
  }

  /** 读取ruleTitle。 */
  public String getRuleTitle() {
    return ruleTitle;
  }

  /** 设置ruleTitle。 */
  public void setRuleTitle(String ruleTitle) {
    this.ruleTitle = ruleTitle;
  }

  /** 读取ruleVersion。 */
  public String getRuleVersion() {
    return ruleVersion;
  }

  /** 设置ruleVersion。 */
  public void setRuleVersion(String ruleVersion) {
    this.ruleVersion = ruleVersion;
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
  public LocalDateTime getEffectiveAt() {
    return effectiveAt;
  }

  /** 设置effectiveAt。 */
  public void setEffectiveAt(LocalDateTime effectiveAt) {
    this.effectiveAt = effectiveAt;
  }

  /** 读取contentText。 */
  public String getContentText() {
    return contentText;
  }

  /** 设置contentText。 */
  public void setContentText(String contentText) {
    this.contentText = contentText;
  }

  /** 读取ruleStatus。 */
  public String getRuleStatus() {
    return ruleStatus;
  }

  /** 设置ruleStatus。 */
  public void setRuleStatus(String ruleStatus) {
    this.ruleStatus = ruleStatus;
  }

  /** 组装id。 */
  public FactoryRuleProjection id(String id) {
    this.id = id;
    return this;
  }

  /** 组装ruleCode。 */
  public FactoryRuleProjection ruleCode(String ruleCode) {
    this.ruleCode = ruleCode;
    return this;
  }

  /** 组装ruleTitle。 */
  public FactoryRuleProjection ruleTitle(String ruleTitle) {
    this.ruleTitle = ruleTitle;
    return this;
  }

  /** 组装ruleVersion。 */
  public FactoryRuleProjection ruleVersion(String ruleVersion) {
    this.ruleVersion = ruleVersion;
    return this;
  }

  /** 组装contentHash。 */
  public FactoryRuleProjection contentHash(String contentHash) {
    this.contentHash = contentHash;
    return this;
  }

  /** 组装effectiveAt。 */
  public FactoryRuleProjection effectiveAt(LocalDateTime effectiveAt) {
    this.effectiveAt = effectiveAt;
    return this;
  }

  /** 组装contentText。 */
  public FactoryRuleProjection contentText(String contentText) {
    this.contentText = contentText;
    return this;
  }

  /** 组装ruleStatus。 */
  public FactoryRuleProjection ruleStatus(String ruleStatus) {
    this.ruleStatus = ruleStatus;
    return this;
  }
}
