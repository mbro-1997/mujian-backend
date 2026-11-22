package com.mujian.backend.realname.vo;

import java.time.*;
import java.util.*;

/** 明确接口字段，保持现有JSON属性与空值契约。 */
public class RealnameCurrentVO {
  private RealnameApplicationVO application;
  private boolean canSubmit;
  private String nextAction;
  private com.mujian.backend.material.vo.MaterialConsentVO consent;
  private com.mujian.backend.material.vo.MaterialPolicyVO uploadPolicy;
  private String phoneMasked;

  /** 读取application。 */
  public RealnameApplicationVO getApplication() {
    return application;
  }

  /** 设置application。 */
  public void setApplication(RealnameApplicationVO realnameApplicationVO) {
    this.application = realnameApplicationVO;
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

  /** 读取phoneMasked。 */
  public String getPhoneMasked() {
    return phoneMasked;
  }

  /** 设置phoneMasked。 */
  public void setPhoneMasked(String phoneMasked) {
    this.phoneMasked = phoneMasked;
  }

  /** 组装application。 */
  public RealnameCurrentVO application(RealnameApplicationVO realnameApplicationVO) {
    this.application = realnameApplicationVO;
    return this;
  }

  /** 组装canSubmit。 */
  public RealnameCurrentVO canSubmit(boolean canSubmit) {
    this.canSubmit = canSubmit;
    return this;
  }

  /** 组装nextAction。 */
  public RealnameCurrentVO nextAction(String nextAction) {
    this.nextAction = nextAction;
    return this;
  }

  /** 组装consent。 */
  public RealnameCurrentVO consent(com.mujian.backend.material.vo.MaterialConsentVO materialConsentVO) {
    this.consent = materialConsentVO;
    return this;
  }

  /** 组装uploadPolicy。 */
  public RealnameCurrentVO uploadPolicy(
      com.mujian.backend.material.vo.MaterialPolicyVO materialPolicyVO) {
    this.uploadPolicy = materialPolicyVO;
    return this;
  }

  /** 组装phoneMasked。 */
  public RealnameCurrentVO phoneMasked(String phoneMasked) {
    this.phoneMasked = phoneMasked;
    return this;
  }
}
