package com.mujian.backend.common.enums;

/** D3-B固定业务编码；接口及数据库保持原字符串，不运行时改写。 */
public enum MerchantAccountStatus {
  not_applied,
  reviewing,
  need_action,
  active,
  frozen,
  restricted
}
