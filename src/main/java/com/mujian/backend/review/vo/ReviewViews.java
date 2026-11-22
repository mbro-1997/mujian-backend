package com.mujian.backend.review.vo;

import java.util.List;

public final class ReviewViews {
  public record Entry(boolean canReview, String reasonCode, String reviewId, List<String> tags) {}

  public record Review(
      String id,
      String businessType,
      String businessOrderId,
      String reviewerRole,
      int rating,
      List<String> tags,
      String content,
      String status,
      String createdAt,
      String rewardStatus,
      String userCouponId) {}
}
