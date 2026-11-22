package com.mujian.backend.coupon.projection;

import java.time.LocalDateTime;

/** 使用历史状态来自本次usage，不能被再次用券状态覆盖。 */
public record CouponUseProjection(
    String id,
    String couponId,
    String couponName,
    String batchId,
    String paymentId,
    int discountFen,
    String status,
    LocalDateTime usedAt,
    LocalDateTime returnedAt) {}
