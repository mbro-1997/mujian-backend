package com.mujian.backend.delivery.projection;

/** 订单冻结运输快照与真实资金/占用事实的只读摘要。 */
public record FulfillmentMetadata(
    String outboundLegId,
    String outboundMode,
    String outboundStatus,
    String returnLegId,
    String returnMode,
    String returnStatus,
    String transportRequirement,
    String depositStatus,
    boolean inventoryReleased) {}
