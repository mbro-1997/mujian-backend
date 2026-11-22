package com.mujian.backend.common.enums;

/** 履约页面可用动作，权限与状态由服务端共同派生。 */
public enum FulfillmentAction {
  save,
  scan,
  set_quantity,
  confirm,
  append_evidence,
  contact_support,
  send,
  start_pickup,
  confirm_pickup_handover,
  assess_overdue_fee,
  confirm_no_issue,
  admin_confirm_arrival,
  admin_close,
  view_refunds
}
