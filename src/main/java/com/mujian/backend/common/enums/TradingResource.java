package com.mujian.backend.common.enums;

/** D3-B固定业务编码；接口及数据库保持原字符串，不运行时改写。 */
public enum TradingResource {
  order_outbound_record,
  order_receipt_check,
  order_issue_report,
  order_return_shipment,
  order_return_check,
  order_transport_expense_statement,
  checkout_batch,
  rental_order,
  transport_vehicle_tier,
  transport_pricing_rule,
  factory_wechat_merchant_account
}
