package com.mujian.backend.payment.domain;

import java.time.LocalDateTime;

/** 原退款义务和渠道执行恢复信息；金额保留不随失败或重试删除。 */
public record RefundOrderDO(
    String id,
    String refundNo,
    String requestIdempotencyKey,
    String bizOrderType,
    String bizOrderId,
    String rentalOrderId,
    String paymentOrderId,
    String paymentChannelSuborderId,
    String refundType,
    String refundStatus,
    String channelTransactionId,
    String channelOutTradeNo,
    String channelOutRefundNo,
    String channelRefundNo,
    int customGoodsRefundAmountFen,
    int rentRefundAmountFen,
    int depositRefundAmountFen,
    int freightRefundAmountFen,
    int refundAmountFen,
    String refundReason,
    String requestedBy,
    LocalDateTime requestedAt,
    LocalDateTime successAt,
    LocalDateTime failedAt,
    String failReason,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime nextAttemptAt,
    LocalDateTime lastCheckedAt,
    int attemptCount,
    String lastErrorCode,
    int version) {}
