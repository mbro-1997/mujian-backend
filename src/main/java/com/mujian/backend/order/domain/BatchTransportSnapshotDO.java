package com.mujian.backend.order.domain;

import java.time.LocalDateTime;

/** 每厂原运输事实，不再依赖当前工厂资料或地址簿。 */
public record BatchTransportSnapshotDO(
    String id,
    String checkoutBatchId,
    String factoryId,
    String outboundServiceMode,
    String returnServiceMode,
    String factoryAddressSnapshot,
    String receiverAddressSnapshot,
    String outboundVehicleTierId,
    String returnVehicleTierId,
    Integer outboundDistanceMeter,
    Integer returnDistanceMeter,
    int outboundGuideMinAmountFen,
    int outboundGuideMaxAmountFen,
    int returnGuideMinAmountFen,
    int returnGuideMaxAmountFen,
    int outboundPrepaidAmountFen,
    int returnPrepaidAmountFen,
    int freightPrepaidAmountFen,
    String routePricingSnapshotJson,
    String transportRequirementSnapshot,
    String buyerRemarkSnapshot,
    LocalDateTime createdAt) {}
