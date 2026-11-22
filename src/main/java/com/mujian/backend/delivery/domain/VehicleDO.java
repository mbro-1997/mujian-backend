package com.mujian.backend.delivery.domain;

import java.time.LocalDateTime;

/** 车型持久事实，容量按BIGINT承接。 */
public record VehicleDO(
    String id,
    String tierCode,
    String tierName,
    int cargoLengthMm,
    int cargoWidthMm,
    int cargoHeightMm,
    long effectiveVolumeCm3,
    long safePayloadGram,
    int sortNo,
    String tierStatus,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    int version) {}
