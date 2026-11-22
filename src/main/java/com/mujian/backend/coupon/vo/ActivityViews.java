package com.mujian.backend.coupon.vo;

import java.util.List;

public final class ActivityViews {
  public record Factory(String factoryId, String factoryName) {}

  public record Offer(
      String activityId,
      String templateId,
      String templateName,
      String couponType,
      Integer faceAmountFen,
      Integer discountRateBps,
      int thresholdRentAmountFen,
      String applicableScopeType,
      List<Factory> factories,
      String condition,
      String claimEndsAt,
      String validityType,
      Integer validDays,
      String validStartAt,
      String validEndAt) {}

  public record Claim(String userCouponId, String couponStatus, boolean alreadyClaimed) {}
}
