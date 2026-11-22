package com.mujian.backend.planning.domain;

import java.time.LocalDateTime;

/** 仅承接对应持久化事实，不直接作为接口响应。 */
public record RentalChecklistItemDO(
    String id,
    String checklistId,
    String skuId,
    String factoryId,
    int quantity,
    String remark,
    int sortOrder,
    Integer maxRentableQuantitySnapshot,
    String unavailableReason,
    String skuSnapshotJson,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
