package com.mujian.backend.order.domain;

import java.time.*;

/** 提交批次的持久事实，金额不从当前SKU回算。 */
public record CheckoutBatchDO(
    String id,
    String batchNo,
    String sourceChecklistId,
    String buyerUserId,
    String batchStatus,
    String submitIdempotencyKey,
    String exceptionReason,
    String recoveryMode,
    LocalDate rentalStartDate,
    LocalDate rentalEndDate,
    int rentalDays,
    String transportNoticeSnapshot,
    String ruleSnapshotJson,
    int rentPayableAmountFen,
    int couponDiscountAmountFen,
    int depositPayableAmountFen,
    int freightPrepaidAmountFen,
    int payableAmountFen,
    LocalDateTime holdExpireAt,
    LocalDateTime submittedAt,
    LocalDateTime paidAt,
    LocalDateTime closedAt,
    LocalDateTime expiredAt,
    int version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String requestHash,
    LocalDateTime guardReleasedAt) {}
