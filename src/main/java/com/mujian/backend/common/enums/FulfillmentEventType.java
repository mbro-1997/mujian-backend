package com.mujian.backend.common.enums;

/** 05履约事件编码。 */
public enum FulfillmentEventType {
  outbound_completed,
  issue_reported,
  receipt_confirmed,
  receipt_auto_confirmed,
  return_sent,
  pickup_started,
  return_check_started,
  return_check_warning,
  return_check_closed,
  return_close_completed,
  transport_expense_submitted,
  transport_expense_returned,
  transport_expense_approved,
  rental_overdue
}
