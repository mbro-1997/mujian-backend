package com.mujian.backend.common.enums;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PropRentableStatus {
  RENTABLE("rentable"),
  UNAVAILABLE("unavailable");
  private final String code;

  /** 装配本组件依赖，复用既有业务和基础设施能力。 */
  PropRentableStatus(String code) {
    this.code = code;
  }

  /** 返回契约约定的稳定状态编码。 */
  @JsonValue
  public String code() {
    return code;
  }
}
