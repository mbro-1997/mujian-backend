package com.mujian.backend.common.enums;

/** 原支付来源状态或退款来源类型，沿正式持久编码。 */
public enum RefundObligationSource {
  rental_order,
  checkout_batch,
  return_check,
  expense_statement
}
