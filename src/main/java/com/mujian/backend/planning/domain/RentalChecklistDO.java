package com.mujian.backend.planning.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 仅承接对应持久化事实，不直接作为接口响应。 */
public record RentalChecklistDO(
    String id,
    String ownerUserId,
    String checklistName,
    LocalDate rentalStartDate,
    LocalDate rentalEndDate,
    int rentalDays,
    String checklistStatus,
    Integer activeSlotNo,
    String currentOwnerGuard,
    String currentCheckoutBatchId,
    String sourceType,
    String sourceSchemeId,
    LocalDateTime activatedAt,
    LocalDateTime checkoutProcessingAt,
    LocalDateTime convertedOrderAt,
    int version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
