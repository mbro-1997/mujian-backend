package com.mujian.backend.coupon.projection;

import java.time.LocalDateTime;

/** 用户券与不可变模板版本联合读取；不向接口暴露持有人或内部发行键。 */
public record CouponProjection(
    String id,
    String userCouponNo,
    String templateId,
    String userId,
    String couponStatus,
    String couponTypeSnapshot,
    Integer faceAmountFenSnapshot,
    Integer discountRateBpsSnapshot,
    int thresholdRentAmountFenSnapshot,
    String costBearerSnapshot,
    LocalDateTime validFrom,
    LocalDateTime validUntil,
    LocalDateTime lockedUntil,
    LocalDateTime usedAt,
    LocalDateTime returnedAt,
    int version,
    String templateName,
    String scopeType,
    int templateVersion) {}
