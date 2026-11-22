package com.mujian.backend.order.vo;

/** 取消确认金额由原订单与当前可控时钟共同确定。 */
public record CancellationQuoteVO(
    String orderId,
    int orderVersion,
    String confirmToken,
    int deductionFen,
    int rentRefundFen,
    int depositRefundFen,
    int freightRefundFen,
    int totalRefundFen,
    int rateBps,
    String expiresAt) {}
