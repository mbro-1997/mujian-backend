package com.mujian.backend.common.enums;

/** 已验签关单响应，仅供恢复继续查证，不直接代表本地可释放。 */
public enum PaymentCloseResult {
  accepted,
  already_closed,
  not_found
}
