package com.mujian.backend.common.enums;

/** D4普通照片节点及允许写入的实际角色。 */
public enum EvidenceNode {
  outbound,
  handover,
  receipt_check,
  in_use_report,
  return_sent,
  transport_expense,
  claim,
  arbitration,
  return_check;

  /** 归还执行方还须按订单原回程方式核对。 */
  public boolean permits(FulfillmentRole role) {
    return switch (this) {
      case outbound, handover, transport_expense, return_check -> role == FulfillmentRole.factory;
      case claim -> role==FulfillmentRole.factory;
      case arbitration -> role==FulfillmentRole.factory || role==FulfillmentRole.buyer;
      case receipt_check, in_use_report -> role == FulfillmentRole.buyer;
      case return_sent -> role == FulfillmentRole.buyer || role == FulfillmentRole.factory;
    };
  }
}
