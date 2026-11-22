package com.mujian.backend.order.domain;

import java.time.*;

/** 下单时完整不可变选品快照，支持成单与后续历史读取。 */
public record BatchItemSnapshotDO(
    String id,
    String checkoutBatchId,
    String sourceChecklistItemId,
    String sourceChecklistId,
    String factoryId,
    String factoryNameSnapshot,
    String skuId,
    String propNameSnapshot,
    String imageUrlSnapshot,
    String assetTypeSnapshot,
    String typeLabelSnapshot,
    String styleLabelsSnapshot,
    int quantity,
    LocalDate rentalStartDate,
    LocalDate rentalEndDate,
    int rentalDays,
    int dailyRentPriceFen,
    int rentAmountFen,
    int couponDiscountAmountFen,
    int rentCashPayableAmountFen,
    int depositUnitAmountFen,
    int depositOriginalAmountFen,
    int depositWaivedAmountFen,
    int depositPayableAmountFen,
    String checkoutBatchTransportSnapshotId,
    String transportRequirementSnapshot,
    String itemSnapshotJson,
    LocalDateTime createdAt) {}
