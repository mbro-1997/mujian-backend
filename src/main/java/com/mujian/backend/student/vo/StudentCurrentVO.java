package com.mujian.backend.student.vo;

import java.time.*;
import java.util.*;

/** 明确接口字段，保持现有JSON属性与空值契约。 */
public class StudentCurrentVO {
  private StudentApplicationVO application;
  private boolean canSubmit;
  private String nextAction;
  private com.mujian.backend.material.vo.MaterialConsentVO consent;
  private com.mujian.backend.material.vo.MaterialPolicyVO uploadPolicy;
  private String realnameStatus;

  /** 读取application。 */
  public StudentApplicationVO getApplication() {
    return application;
  }

  /** 设置application。 */
  public void setApplication(StudentApplicationVO studentApplicationVO) {
    this.application = studentApplicationVO;
  }

  /** 读取canSubmit。 */
  public boolean getCanSubmit() {
    return canSubmit;
  }

  /** 设置canSubmit。 */
  public void setCanSubmit(boolean canSubmit) {
    this.canSubmit = canSubmit;
  }

  /** 读取nextAction。 */
  public String getNextAction() {
    return nextAction;
  }

  /** 设置nextAction。 */
  public void setNextAction(String nextAction) {
    this.nextAction = nextAction;
  }

  /** 读取consent。 */
  public com.mujian.backend.material.vo.MaterialConsentVO getConsent() {
    return consent;
  }

  /** 设置consent。 */
  public void setConsent(com.mujian.backend.material.vo.MaterialConsentVO materialConsentVO) {
    this.consent = materialConsentVO;
  }

  /** 读取uploadPolicy。 */
  public com.mujian.backend.material.vo.MaterialPolicyVO getUploadPolicy() {
    return uploadPolicy;
  }

  /** 设置uploadPolicy。 */
  public void setUploadPolicy(com.mujian.backend.material.vo.MaterialPolicyVO materialPolicyVO) {
    this.uploadPolicy = materialPolicyVO;
  }

  /** 读取realnameStatus。 */
  public String getRealnameStatus() {
    return realnameStatus;
  }

  /** 设置realnameStatus。 */
  public void setRealnameStatus(String realnameStatus) {
    this.realnameStatus = realnameStatus;
  }

  /** 组装application。 */
  public StudentCurrentVO application(StudentApplicationVO studentApplicationVO) {
    this.application = studentApplicationVO;
    return this;
  }

  /** 组装canSubmit。 */
  public StudentCurrentVO canSubmit(boolean canSubmit) {
    this.canSubmit = canSubmit;
    return this;
  }

  /** 组装nextAction。 */
  public StudentCurrentVO nextAction(String nextAction) {
    this.nextAction = nextAction;
    return this;
  }

  /** 组装consent。 */
  public StudentCurrentVO consent(com.mujian.backend.material.vo.MaterialConsentVO materialConsentVO) {
    this.consent = materialConsentVO;
    return this;
  }

  /** 组装uploadPolicy。 */
  public StudentCurrentVO uploadPolicy(
      com.mujian.backend.material.vo.MaterialPolicyVO materialPolicyVO) {
    this.uploadPolicy = materialPolicyVO;
    return this;
  }

  /** 组装realnameStatus。 */
  public StudentCurrentVO realnameStatus(String realnameStatus) {
    this.realnameStatus = realnameStatus;
    return this;
  }
}
