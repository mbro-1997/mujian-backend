package com.mujian.backend.delivery.domain;

import java.time.*;

/** 正式order_outbound_record事实；原始时间与根版本独立保存。 */
public record OrderOutboundDO(
    String id,
    String rentalOrderId,
    String outboundTransportLegId,
    String factoryId,
    String outboundStatus,
    String outboundServiceMode,
    String transportRequirementSnapshot,
    Integer outboundPhotoCount,
    String operatorUserId,
    LocalDateTime outboundAt,
    LocalDateTime dispatchAt,
    String remark,
    Integer version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
