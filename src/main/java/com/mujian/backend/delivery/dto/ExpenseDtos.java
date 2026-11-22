package com.mujian.backend.delivery.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.common.enums.*;
import java.util.List;

public final class ExpenseDtos {
  /** 仅承载本领域的结构定义或纯计算。 */
  private ExpenseDtos() {}

  public record ExtraFee(
      String type, int amountFen, String description, List<String> evidenceIds) {}

  public record Leg(
      TransportExecutionSource source,
      String carrierName,
      String carrierOrderNo,
      Integer baseAmountFen,
      List<ExtraFee> extraFees) {}

  public record Save(
      int expectedVersion,
      String previousStatementId,
      List<String> reusedEvidenceIds,
      Leg outbound,
      @JsonProperty("return") Leg returnLeg) {}

  public record Review(int expectedVersion, String decision, String reason) {}

  public record Query(
      String orderId,
      TransportExpenseStatus status,
      String factoryId,
      String keyword,
      Boolean overBudget,
      PageQuery page) {}
}
