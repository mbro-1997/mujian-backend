package com.mujian.backend.custom.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.*;
import java.util.List;

public final class CustomForms {
  private CustomForms() {}

  public record EvidenceRef(String materialId, String name, String type) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record RequirementItem(
      String id,
      String sourceSkuId,
      String title,
      Integer quantity,
      String dimension,
      String material,
      String style,
      String acceptanceNotes,
      List<EvidenceRef> attachments) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Draft(
      Integer expectedVersion,
      String factoryId,
      String title,
      String usageScene,
      LocalDate expectedCompletionDate,
      String deliveryCity,
      String deliveryMode,
      String remark,
      List<Integer> budgetRangeFen,
      List<RequirementItem> items,
      List<EvidenceRef> attachments) {}

  public record QuotedItem(
      String itemId,
      String title,
      Integer quantity,
      Integer unitPriceFen,
      String dimension,
      String material,
      String style,
      String acceptanceStandard) {}

  public record QuoteTerms(
      List<QuotedItem> items,
      String deliveryMode,
      Integer freightFen,
      Integer productionDays,
      LocalDate promisedDate,
      String revisionTerms,
      String includedAndExcluded,
      String afterSaleTerms,
      String remark) {}

  public record QuoteInput(int expectedVersion, QuoteTerms terms) {}

  public record QuoteAction(int expectedVersion, String quoteId) {}

  public record AcceptQuote(int expectedVersion, String quoteId, String addressId) {}

  public record Action(int expectedVersion) {}

  public record NoteAction(int expectedVersion, String reason) {}

  public record DeliveryInput(
      int expectedVersion,
      String remark,
      List<String> materialIds,
      List<String> orderItemIds,
      OffsetDateTime occurredAt) {}

  public record DeliveryAction(int expectedVersion, String deliveryId) {}

  public record ExtensionInput(
      int expectedVersion, LocalDate requestedDate, String reason, List<String> evidenceIds) {}

  public record Issue(
      int expectedVersion,
      String deliveryId,
      String type,
      String problem,
      List<String> evidenceIds,
      String requestedSolution) {}

  public record StatementInput(int expectedVersion, String summary, List<String> evidenceIds) {}

  public record Resolution(
      int expectedVersion,
      String resolutionType,
      Integer goodsRefundFen,
      Integer freightRefundFen,
      String reason) {}

  public record ExtensionDecision(int expectedVersion, String decision, String note) {}
}
