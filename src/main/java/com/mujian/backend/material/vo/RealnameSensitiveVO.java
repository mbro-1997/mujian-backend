package com.mujian.backend.material.vo;

import java.time.*;
import java.util.*;

/** 材料接口明确响应；授权范围和私有签名期限不变。 */
public class RealnameSensitiveVO {
  private String applicationId;
  private String fullName;
  private String fullIdCard;
  private String frontUrl;
  private String backUrl;
  private String expiresAt;
  private String auditId;

  /** 读取applicationId。 */
  public String getApplicationId() {
    return applicationId;
  }

  /** 设置applicationId。 */
  public void setApplicationId(String applicationId) {
    this.applicationId = applicationId;
  }

  /** 读取fullName。 */
  public String getFullName() {
    return fullName;
  }

  /** 设置fullName。 */
  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  /** 读取fullIdCard。 */
  public String getFullIdCard() {
    return fullIdCard;
  }

  /** 设置fullIdCard。 */
  public void setFullIdCard(String fullIdCard) {
    this.fullIdCard = fullIdCard;
  }

  /** 读取frontUrl。 */
  public String getFrontUrl() {
    return frontUrl;
  }

  /** 设置frontUrl。 */
  public void setFrontUrl(String frontUrl) {
    this.frontUrl = frontUrl;
  }

  /** 读取backUrl。 */
  public String getBackUrl() {
    return backUrl;
  }

  /** 设置backUrl。 */
  public void setBackUrl(String backUrl) {
    this.backUrl = backUrl;
  }

  /** 读取expiresAt。 */
  public String getExpiresAt() {
    return expiresAt;
  }

  /** 设置expiresAt。 */
  public void setExpiresAt(String expiresAt) {
    this.expiresAt = expiresAt;
  }

  /** 读取auditId。 */
  public String getAuditId() {
    return auditId;
  }

  /** 设置auditId。 */
  public void setAuditId(String auditId) {
    this.auditId = auditId;
  }

  /** 组装applicationId。 */
  public RealnameSensitiveVO applicationId(String applicationId) {
    this.applicationId = applicationId;
    return this;
  }

  /** 组装fullName。 */
  public RealnameSensitiveVO fullName(String fullName) {
    this.fullName = fullName;
    return this;
  }

  /** 组装fullIdCard。 */
  public RealnameSensitiveVO fullIdCard(String fullIdCard) {
    this.fullIdCard = fullIdCard;
    return this;
  }

  /** 组装frontUrl。 */
  public RealnameSensitiveVO frontUrl(String frontUrl) {
    this.frontUrl = frontUrl;
    return this;
  }

  /** 组装backUrl。 */
  public RealnameSensitiveVO backUrl(String backUrl) {
    this.backUrl = backUrl;
    return this;
  }

  /** 组装expiresAt。 */
  public RealnameSensitiveVO expiresAt(String expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  /** 组装auditId。 */
  public RealnameSensitiveVO auditId(String auditId) {
    this.auditId = auditId;
    return this;
  }
}
