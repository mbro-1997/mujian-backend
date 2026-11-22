package com.mujian.backend.order.domain;

import java.time.LocalDateTime;

/** 持久命令回执，资金事实仍由支付及退款表负责。 */
public record TradingOperationDO(
    String id,
    String actorUserId,
    String requestId,
    String operationCode,
    String requestHash,
    String resourceType,
    String resourceId,
    String resultJson,
    LocalDateTime createdAt) {}
