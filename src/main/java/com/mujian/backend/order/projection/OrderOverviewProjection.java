package com.mujian.backend.order.projection;

/** 工厂全范围计数，不受首页分页条数影响。 */
public record OrderOverviewProjection(
    long pendingAcceptCount,
    long acceptedCount,
    long refundExceptionCount,
    long allOrderCount,
    long pendingOutboundCount,
    long returnCheckingCount,long exceptionOrderCount) {}
