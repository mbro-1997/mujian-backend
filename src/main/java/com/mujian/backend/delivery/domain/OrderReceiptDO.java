package com.mujian.backend.delivery.domain;

import java.time.*;

/** 正式order_receipt_check事实；原始时间与根版本独立保存。 */
public record OrderReceiptDO(
    String id,
    String rentalOrderId,
    String outboundTransportLegId,
    String buyerUserId,
    String checkStatus,
    LocalDateTime startAt,
    LocalDateTime deadlineAt,
    LocalDateTime confirmedAt,
    Integer version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
