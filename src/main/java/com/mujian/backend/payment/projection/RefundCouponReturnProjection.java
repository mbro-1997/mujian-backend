package com.mujian.backend.payment.projection;

import java.time.LocalDateTime;

/** 原支付的整券返回事实，用于获权退款列表一次性装配。 */
public record RefundCouponReturnProjection(
    String paymentId,
    String usageStatus,
    String userCouponId,
    LocalDateTime returnedAt,
    LocalDateTime validUntil) {}
