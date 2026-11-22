package com.mujian.backend.order.vo;

/** 响应结果与持久资源快照分离，重放类型由入口派生。 */
public record TradingReceipt(
    String requestId,
    String operation,
    String resourceType,
    String resourceId,
    int resourceVersion,
    String outcome,
    String appliedAt) {}
