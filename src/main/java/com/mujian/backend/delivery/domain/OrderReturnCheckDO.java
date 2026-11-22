package com.mujian.backend.delivery.domain;

import java.time.*;

/** 正式order_return_check事实；原始时间与根版本独立保存。 */
public record OrderReturnCheckDO(
    String id,
    String rentalOrderId,
    String factoryId,
    String returnShipmentId,
    String checkStatus,
    String checkStartSource,
    LocalDateTime checkStartAt,
    LocalDateTime warningAt,
    LocalDateTime autoCloseAt,
    LocalDateTime checkedAt,
    String operatorUserId,
    String problemSummary,
    Integer claimCreated,
    String closeReasonCode,
    String closeReason,
    String closedByUserId,
    LocalDateTime closedAt,
    Integer version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
