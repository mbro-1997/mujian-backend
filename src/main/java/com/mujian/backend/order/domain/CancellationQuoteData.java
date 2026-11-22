package com.mujian.backend.order.domain;

import com.mujian.backend.order.policy.CancellationCalculation;
import java.time.LocalDateTime;

/** 用途隔离的取消确认内容，绑定原单、版本、规则版本及现金科目。 */
public record CancellationQuoteData(
    int schemaVersion,
    String buyerId,
    String orderId,
    int orderVersion,
    String ruleCode,
    String ruleVersion,
    CancellationCalculation.Result amounts,
    LocalDateTime issuedAt,
    LocalDateTime expiresAt) {}
