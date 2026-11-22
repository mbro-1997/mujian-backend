package com.mujian.backend.common.enums;

/** D4正式契约固定编码，不由运营字典修改。 */
public enum ReturnCheckStatus {
  pending,
  warning,
  no_issue,
  claim_submitted,
  auto_closed,
  admin_closed
}
