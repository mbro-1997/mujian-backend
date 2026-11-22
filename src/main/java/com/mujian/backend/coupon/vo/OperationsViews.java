package com.mujian.backend.coupon.vo;

import java.util.List;

public final class OperationsViews {
  public record Template(
      String templateCode,
      String templateName,
      String couponType,
      Integer faceAmountFen,
      Integer discountRateBps,
      int thresholdRentAmountFen,
      String applicableScopeType,
      List<String> factoryIds,
      String validityType,
      Integer validDays,
      String validStartAt,
      String validEndAt,
      String id,
      int templateVersion,
      String status,
      String issuedCount,
      String snapshotToken) {}

  public record TemplateFilter(String status, String keyword) {}

  public record TemplateRow(
      String templateCode,
      String templateName,
      String couponType,
      Integer faceAmountFen,
      Integer discountRateBps,
      int thresholdRentAmountFen,
      String applicableScopeType,
      String validityType,
      Integer validDays,
      String validStartAt,
      String validEndAt,
      String id,
      int templateVersion,
      String status,
      String issuedCount) {}

  public record Activity(
      String id,
      String templateId,
      String condition,
      String startsAt,
      String endsAt,
      String reason,
      String status,
      int version,
      String issuedQuantity) {}

  public record ActivityFilter(String status) {}

  public record Rule(
      String id,
      String eventType,
      String templateId,
      int ruleVersion,
      String status,
      String snapshotToken) {}

  public record RuleFilter(String eventType) {}

  public record Reward(
      String id,
      String userId,
      String eventType,
      String sourceId,
      String ruleId,
      String status,
      String reasonCode,
      String userCouponId,
      String occurredAt) {}

  public record RewardFilter(String eventType, String userId, String status) {}

  public record Distribution(
      String id,
      String templateId,
      String mode,
      String requestId,
      String targetCount,
      String issuedQuantity,
      String completedAt,
      String reason,
      String createdByAdminUserId) {}

  public record DistributionFilter(String requestId, String templateId) {}

  public record Issued(
      String id,
      String userId,
      String templateId,
      String couponStatus,
      String issueSourceType,
      String issueSourceId,
      String validFrom,
      String validUntil,
      String createdAt) {}

  public record IssuedDetail(
      @com.fasterxml.jackson.annotation.JsonUnwrapped Issued coupon, List<String> usageIds) {}

  public record IssuedFilter(
      String userId, String issueSourceType, String issueSourceId, String templateId) {}
}
