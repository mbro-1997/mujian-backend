package com.mujian.backend.delivery.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mujian.backend.delivery.dto.ExpenseDtos.ExtraFee;
import java.util.List;

public final class ExpenseViews {
  /** 仅承载本领域的结构定义或纯计算。 */
  private ExpenseViews() {}

  public record Leg(
      String transportLegId,
      String chargeMode,
      String source,
      String carrierName,
      String carrierOrderNo,
      Integer frozenOwnVehicleAmountFen,
      int baseAmountFen,
      List<ExtraFee> extraFees,
      int totalAmountFen,
      int approvedAmountFen) {}

  public record Expense(
      String id,
      String orderId,
      String orderNo,
      String factoryId,
      String factoryName,
      int statementVersion,
      int version,
      String status,
      Leg outbound,
      @JsonProperty("return") Leg returnLeg,
      int declaredTotalFen,
      int approvedTotalFen,
      int prepaidPoolFen,
      int onlineSettleFen,
      int overBudgetFen,
      int refundableFen,
      int evidenceCount,
      String submittedAt,
      String reviewedAt,
      String reviewReason,
      List<String> refundIds,
      List<String> availableActions) {}

  public record Context(
      String orderId,
      boolean required,
      Expense latest,
      Leg outbound,
      @JsonProperty("return") Leg returnLeg) {}
}
