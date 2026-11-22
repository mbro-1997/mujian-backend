package com.mujian.backend.prop.dto;

import java.time.*;

/** 固定写入命令；每个命令的NULL表示实际写入NULL，不是跳过字段。 */
public final class PropWriteDTO {
  /** 构造当前数据对象，保留约定字段的类型和空值。 */
  private PropWriteDTO() {}

  public record RetryQrImage(
      String id,
      String imageGenerationStatus,
      Integer imageAttempts,
      LocalDateTime imageLeaseUntil,
      LocalDateTime imageNextRetryAt,
      String imageErrorCode,
      Integer version,
      LocalDateTime updatedAt) {}

  public record ConfirmQrLabel(
      String id,
      String usedByFactoryUserId,
      LocalDateTime usedAt,
      Integer version,
      LocalDateTime updatedAt) {}

  public record BindSnQr(String id, String qrCodeId, Integer version, LocalDateTime updatedAt) {}

  public record RevokeQr(
      String id,
      String status,
      String revokedByAdminUserId,
      LocalDateTime revokedAt,
      Integer version,
      LocalDateTime imageLeaseUntil,
      LocalDateTime updatedAt) {}

  public record ChangeSkuStatus(
      String id, String skuStatus, Integer version, LocalDateTime updatedAt) {}

  public record TransitionSn(
      String id,
      String instanceStatus,
      String statusRemark,
      LocalDateTime statusChangedAt,
      String statusChangedBy,
      Integer version,
      LocalDateTime updatedAt) {}

  public record LinkStockMovement(String id, String relatedMovementId) {}

  public record UpdateSkuOptions(
      String id,
      String typeCode,
      String transportSuggestion,
      Integer version,
      LocalDateTime updatedAt) {}

  public record CancelReview(
      String id,
      String auditStatus,
      String reviewerId,
      LocalDateTime reviewedAt,
      String auditRemark,
      Integer version,
      LocalDateTime updatedAt) {}

  public record ResolveQrException(
      String id,
      String exceptionStatus,
      String resolvedByAdminUserId,
      String resolutionType,
      String resolutionReason,
      LocalDateTime resolvedAt,
      Integer version,
      LocalDateTime updatedAt) {}

  public record DecideReview(
      String id,
      String auditStatus,
      String reviewerId,
      String auditRemark,
      String rejectReason,
      LocalDateTime reviewedAt,
      Integer version,
      LocalDateTime updatedAt) {}
}
