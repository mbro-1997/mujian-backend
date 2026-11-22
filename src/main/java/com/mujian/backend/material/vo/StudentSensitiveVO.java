package com.mujian.backend.material.vo;

import java.time.*;
import java.util.*;

/** 材料接口明确响应；授权范围和私有签名期限不变。 */
public class StudentSensitiveVO {
  private String applicationId;
  private String phone;
  private List<SensitiveImageVO> images;
  private RealnameSensitiveAccessVO realnameView;
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

  /** 读取phone。 */
  public String getPhone() {
    return phone;
  }

  /** 设置phone。 */
  public void setPhone(String phone) {
    this.phone = phone;
  }

  /** 读取images。 */
  public List<SensitiveImageVO> getImages() {
    return images;
  }

  /** 设置images。 */
  public void setImages(List<SensitiveImageVO> images) {
    this.images = images;
  }

  /** 读取realnameView。 */
  public RealnameSensitiveAccessVO getRealnameView() {
    return realnameView;
  }

  /** 设置realnameView。 */
  public void setRealnameView(RealnameSensitiveAccessVO realnameSensitiveAccessVO) {
    this.realnameView = realnameSensitiveAccessVO;
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
  public StudentSensitiveVO applicationId(String applicationId) {
    this.applicationId = applicationId;
    return this;
  }

  /** 组装phone。 */
  public StudentSensitiveVO phone(String phone) {
    this.phone = phone;
    return this;
  }

  /** 组装images。 */
  public StudentSensitiveVO images(List<SensitiveImageVO> images) {
    this.images = images;
    return this;
  }

  /** 组装realnameView。 */
  public StudentSensitiveVO realnameView(RealnameSensitiveAccessVO realnameSensitiveAccessVO) {
    this.realnameView = realnameSensitiveAccessVO;
    return this;
  }

  /** 组装expiresAt。 */
  public StudentSensitiveVO expiresAt(String expiresAt) {
    this.expiresAt = expiresAt;
    return this;
  }

  /** 组装auditId。 */
  public StudentSensitiveVO auditId(String auditId) {
    this.auditId = auditId;
    return this;
  }
}
