package com.mujian.backend.delivery.domain;

import java.time.LocalDateTime;

/** 发布后的价格规则不可覆盖，版本与业务规则版本分开。 */
public record PriceRuleDO(
    String id,
    String ruleVersion,
    String cityCode,
    String vehicleTierId,
    int distanceMinMeter,
    Integer distanceMaxMeter,
    int guideMinAmountFen,
    int guideMaxAmountFen,
    int ownVehicleSettleAmountFen,
    Integer extraKmAmountFen,
    String allowedExtraFeeJson,
    String ruleStatus,
    LocalDateTime effectiveAt,
    LocalDateTime expireAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    int version,
    int includedDistanceMeter) {}
