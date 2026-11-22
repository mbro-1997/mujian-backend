package com.mujian.backend.delivery.vo;

import static com.mujian.backend.common.support.D2Values.time;

import java.time.LocalDateTime;

public final class MonitoringViews {
  /** 仅承载本领域的结构定义或纯计算。 */
  private MonitoringViews() {}

  public record Warning(
      String checkId,
      String orderId,
      String orderNo,
      String factoryId,
      String factoryName,
      String status,
      String startAt,
      String warningAt,
      String autoCloseAt,
      boolean claimCreated,
      int depositFen) {
    public Warning(
        String checkId,
        String orderId,
        String orderNo,
        String factoryId,
        String factoryName,
        String status,
        LocalDateTime start,
        LocalDateTime warning,
        LocalDateTime close,
        boolean claimed,
        int deposit) {
      this(
          checkId,
          orderId,
          orderNo,
          factoryId,
          factoryName,
          status,
          time(start),
          time(warning),
          time(close),
          claimed,
          deposit);
    }
  }

  public record Risk(
      String id,
      String sourceOrderId,
      String affectedOrderId,
      String skuId,
      String snId,
      String status,
      String createdAt,
      String caseId,
      Integer caseVersion) {
    public Risk(
        String id,
        String sourceOrderId,
        String affectedOrderId,
        String skuId,
        String snId,
        String status,
        LocalDateTime at, String caseId, Integer caseVersion) {
      this(id, sourceOrderId, affectedOrderId, skuId, snId, status, time(at), caseId, caseVersion);
    }
  }
}
