package com.mujian.backend.common.enums;

/** 货物实际离开买家的三种方式。 */
public enum ReturnHandoverMethod {
  carrier,
  self_delivery,
  factory_pickup;

  /** 对照正式登记值验证归还交接方式。 */
  public static boolean supports(String method) {
    for (ReturnHandoverMethod value : values()) if (value.name().equals(method)) return true;
    return false;
  }
}
