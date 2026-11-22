package com.mujian.backend.common.enums;

/** 邀请码生命周期，独立于账号和审核状态。 */
public enum FactoryInvitationStatus {
  UNUSED,
  LOCKED,
  USED,
  REVOKED,
  EXPIRED;

  /** 返回既有持久化及接口编码。 */
  public String code() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
