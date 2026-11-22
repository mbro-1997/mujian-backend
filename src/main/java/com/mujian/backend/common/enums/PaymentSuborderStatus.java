package com.mujian.backend.common.enums;

/** 原支付来源状态或退款来源类型，沿正式持久编码。 */
public enum PaymentSuborderStatus {
  pending,
  paid_frozen,
  partially_refunded,
  fully_refunded,
  sharing,
  settled,
  closed
}
