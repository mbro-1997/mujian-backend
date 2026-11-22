package com.mujian.backend.coupon.vo;

import java.util.List;

/** 用户券及消费记录，不承接运营发券入口。 */
public final class CouponViews {
  /** 禁止实例化此静态结构容器。 */
  private CouponViews() {}

  public record Coupon(
      String id,
      String couponNo,
      String name,
      String status,
      String type,
      Integer faceAmountFen,
      Integer discountRateBps,
      int thresholdRentFen,
      String scopeType,
      List<String> factoryIds,
      String validFrom,
      String validUntil,
      String returnedAt) {}

  public record CouponPage(
      List<Coupon> items,
      String total,
      int page,
      int pageSize,
      String availableCount,
      String unavailableCount) {}

  public record UseRecord(
      String usageId,
      String couponId,
      String couponName,
      String batchId,
      String paymentId,
      int discountFen,
      String status,
      String usedAt,
      String returnedAt) {}

  public record Choice(Coupon coupon, boolean usable, int savingFen, String reasonCode) {}

  public record Choices(List<Choice> items, String calculatedAt) {}

  public record Return(String status, String userCouponId, String returnedAt, String validUntil) {}
}
