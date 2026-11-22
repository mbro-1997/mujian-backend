package com.mujian.backend.coupon.dto;

import java.time.*;
import java.util.List;

public final class OperationsInput {
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
      OffsetDateTime validStartAt,
      OffsetDateTime validEndAt,
      String snapshotToken) {
    /** 将起始时间转换为数据库查询边界。 */
    public LocalDateTime startLocal() {
      return validStartAt == null
          ? null
          : validStartAt
              .atZoneSameInstant(com.mujian.backend.common.support.D2Values.ZONE)
              .toLocalDateTime();
    }

    /** 将结束时间转换为数据库查询边界。 */
    public LocalDateTime endLocal() {
      return validEndAt == null
          ? null
          : validEndAt
              .atZoneSameInstant(com.mujian.backend.common.support.D2Values.ZONE)
              .toLocalDateTime();
    }
  }

  public record Activity(
      String templateId,
      String condition,
      OffsetDateTime startsAt,
      OffsetDateTime endsAt,
      String reason,
      Integer version) {
    /** 将起始时间转换为数据库查询边界。 */
    public LocalDateTime startLocal() {
      return startsAt
          .atZoneSameInstant(com.mujian.backend.common.support.D2Values.ZONE)
          .toLocalDateTime();
    }

    /** 将结束时间转换为数据库查询边界。 */
    public LocalDateTime endLocal() {
      return endsAt
          .atZoneSameInstant(com.mujian.backend.common.support.D2Values.ZONE)
          .toLocalDateTime();
    }
  }

  public record Rule(String eventType, String templateId, String snapshotToken) {}

  public record Snapshot(String snapshotToken) {}

  public record Version(int version) {}
}
