package com.mujian.backend.order.domain;

import com.mujian.backend.order.policy.CancellationCalculation;
import com.mujian.backend.payment.dto.CashComponents;
import java.time.LocalDateTime;

/** 取消在根锁内的一次判定；原退款及在途科目不包含本次新建义务。 */
public record CancellationDeductionSnapshot(
    int schemaVersion,
    String ruleCode,
    String ruleVersion,
    int orderVersion,
    LocalDateTime decidedAt,
    LocalDateTime rentalStartAt,
    long secondsUntilStart,
    int originalRentFen,
    int cashRentFen,
    CancellationCalculation.Result amounts,
    CashComponents originalRefunded,
    CashComponents originalRefundInFlight) {}
