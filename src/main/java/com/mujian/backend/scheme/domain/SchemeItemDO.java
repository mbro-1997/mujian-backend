package com.mujian.backend.scheme.domain;

import java.time.LocalDateTime;

/** 仅承接对应持久化事实，不直接作为接口响应。 */
public record SchemeItemDO(
    String id,
    String schemeId,
    String skuId,
    String factoryId,
    int quantity,
    String remark,
    int sortOrder,
    String skuSnapshotJson,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
