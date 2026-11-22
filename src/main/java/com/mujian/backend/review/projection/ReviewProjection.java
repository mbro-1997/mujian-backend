package com.mujian.backend.review.projection;

import java.time.LocalDateTime;

public record ReviewProjection(
    String id,
    String businessType,
    String businessOrderId,
    String reviewerRole,
    int rating,
    String tagJson,
    String content,
    String status,
    LocalDateTime createdAt,
    String rewardStatus,
    String userCouponId) {
  public record Order(String buyerUserId, String factoryId, String orderStatus) {}

  public record Actor(String userId, String factoryId, String role) {}
}
