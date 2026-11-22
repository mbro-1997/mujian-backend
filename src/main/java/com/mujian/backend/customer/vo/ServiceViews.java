package com.mujian.backend.customer.vo;

import java.time.LocalDateTime;

public final class ServiceViews {
  public record Source(String sourceType, String sourceId) {}

  public record SourceInput(Source source) {}

  public record ServiceSource(
      Source source,
      String title,
      String status,
      String contextId,
      Progress progress,
      String returnPagePath) {}

  public record Progress(String caseNo, String status) {}

  public record Context(
      String id,
      Source source,
      String title,
      String statusSnapshot,
      String summary,
      String returnPagePath,
      String contextStatus,
      String expireAt,
      Progress progress) {}

  public record ContextCard(Context context, String contextToken) {}

  public record SourceRow(
      String sourceType,
      String sourceId,
      String module,
      String title,
      String status,
      String path,
      LocalDateTime createdAt) {}

  public record ContextRow(
      String id,
      String userId,
      String activeRole,
      String sourceType,
      String sourceId,
      String title,
      String statusSnapshot,
      String summary,
      String returnPagePath,
      String contextStatus,
      LocalDateTime expireAt,
      String tokenHash,
      String adminCaseId) {}
}
