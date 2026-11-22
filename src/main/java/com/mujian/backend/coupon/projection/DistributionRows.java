package com.mujian.backend.coupon.projection;

import java.time.LocalDateTime;

public final class DistributionRows {
  public record Template(
      String id,
      String templateName,
      String couponType,
      Integer faceAmountFen,
      Integer discountRateBps,
      int thresholdRentAmountFen,
      String costBearer,
      String applicableScopeType,
      String validityType,
      Integer validDays,
      LocalDateTime validStartAt,
      LocalDateTime validEndAt,
      String templateStatus) {
    /** 固定结束瞬间不再发券。 */
    public boolean issuable(LocalDateTime at) {
      return "active".equals(templateStatus) && (validEndAt == null || at.isBefore(validEndAt));
    }
  }

  public record Activity(
      String id,
      String templateId,
      String conditionCode,
      String status,
      LocalDateTime startsAt,
      LocalDateTime endsAt) {}

  public record OfferRow(
      String activityId,
      String templateId,
      String templateName,
      String couponType,
      Integer faceAmountFen,
      Integer discountRateBps,
      int thresholdRentAmountFen,
      String applicableScopeType,
      String conditionCode,
      LocalDateTime endsAt,
      String validityType,
      Integer validDays,
      LocalDateTime validStartAt,
      LocalDateTime validEndAt) {}

  public record Scope(String templateId, String factoryId, String factoryName) {}

  public record CouponWrite(String id, String userId, String requestId, String logId) {}
}
