package com.mujian.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 继续履约只留证，暂停阻断核验倒计时。 */
public enum IssueEffect {
  @JsonProperty("continue") CONTINUE("continue"), @JsonProperty("paused") PAUSED("paused");

  private final String code;

  IssueEffect(String code) { this.code=code; }

  /** 返回持久化使用的正式编码。 */
  public String getCode() { return code; }
}
