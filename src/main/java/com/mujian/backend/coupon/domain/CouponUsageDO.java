package com.mujian.backend.coupon.domain;

import java.time.LocalDateTime;

/** 原券一次结算使用事实，用于全付核销和全原款返还。 */
public record CouponUsageDO(
    String id,
    String usageNo,
    String userCouponId,
    String userId,
    String checkoutBatchId,
    String paymentOrderId,
    int eligibleRentAmountFen,
    int discountAmountFen,
    String costBearerSnapshot,
    String usageStatus,
    LocalDateTime lockExpireAt,
    LocalDateTime usedAt,
    LocalDateTime releasedAt,
    LocalDateTime returnConfirmedAt,
    int version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
