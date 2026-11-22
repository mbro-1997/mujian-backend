package com.mujian.backend.common.enums;

/** 收付通签名查询事实，仅在业务层完成金额核对后推进本地阶段。 */
public enum ProfitSharingOutcome {
  ACCEPTED,
  PENDING,
  SUCCESS,
  ABSENT,
  RETRYABLE,
  CLOSED,
  MISMATCH,
  UNKNOWN
}
