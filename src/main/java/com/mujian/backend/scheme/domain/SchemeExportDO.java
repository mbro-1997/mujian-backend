package com.mujian.backend.scheme.domain;

import java.time.LocalDateTime;

/** 仅承接对应持久化事实，不直接作为接口响应。 */
public record SchemeExportDO(
    String id,
    String schemeId,
    String exportStatus,
    int sourceContentVersion,
    String templateVersion,
    boolean hidePrice,
    boolean watermarkEnabled,
    String watermarkText,
    LocalDateTime fileExpireAt,
    String localFileKey,
    LocalDateTime processingDeadlineAt,
    String processingSchemeGuard,
    LocalDateTime recordExpireAt,
    LocalDateTime generatedAt,
    String errorMessage,
    String snapshotJson,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
