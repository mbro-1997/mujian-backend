package com.mujian.backend.delivery.domain;

import java.time.*;

/** 正式order_return_shipment事实；原始时间与根版本独立保存。 */
public record OrderReturnShipmentDO(
    String id,
    String rentalOrderId,
    String returnTransportLegId,
    String buyerUserId,
    String operatorUserId,
    String operatorRole,
    String shipmentStatus,
    String returnMethod,
    String trackingNo,
    String carrierName,
    LocalDateTime sentAt,
    LocalDateTime expectedArrivalAt,
    LocalDateTime expectedCheckStartAt,
    Integer packagingPhotoCount,
    String remark,
    Integer version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
