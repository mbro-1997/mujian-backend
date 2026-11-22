package com.mujian.backend.scheme.domain;

import java.time.LocalDateTime;

/** 仅承接对应持久化事实，不直接作为接口响应。 */
public record SchemeShareDO(
    String id,
    String schemeId,
    String snapshotId,
    String requestId,
    String shareToken,
    String shareStatus,
    boolean hidePrice,
    LocalDateTime expiresAt,
    int viewCount,
    String createdBy,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
