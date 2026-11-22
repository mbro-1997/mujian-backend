package com.mujian.backend.common.enums;

/** D3-B固定业务编码；接口及数据库保持原字符串，不运行时改写。 */
public enum TradingRefundType {
  user_cancel_before_accept,
  user_cancel_rule,
  factory_manual_reject,
  factory_timeout_reject,
  unformed_batch_refund,
  deposit_release,
  freight_pool_release
}
