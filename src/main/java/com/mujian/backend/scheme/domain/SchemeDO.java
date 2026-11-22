package com.mujian.backend.scheme.domain;

import java.time.LocalDateTime;

/** 仅承接对应持久化事实，不直接作为接口响应。 */
public record SchemeDO(
    String id,
    String ownerUserId,
    String schemeName,
    String description,
    String schemeStatus,
    Integer activeSlotNo,
    int contentVersion,
    int version,
    boolean shareEnabled,
    LocalDateTime archivedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
