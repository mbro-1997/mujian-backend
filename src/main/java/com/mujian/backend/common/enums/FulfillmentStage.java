package com.mujian.backend.common.enums;

/** 只读展示阶段与尚未创建子根的上下文状态。 */
public enum FulfillmentStage {
  not_started,
  not_dispatched,
  pending_outbound,
  pending_receipt,
  receipt_checking,
  renting,
  overdue,
  return_in_transit,
  return_checking,
  return_closed,
  cancelled
}
