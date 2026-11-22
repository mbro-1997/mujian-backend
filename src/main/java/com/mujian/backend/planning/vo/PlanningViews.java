package com.mujian.backend.planning.vo;

import com.mujian.backend.prop.vo.PublicImageViewVO;
import java.util.List;

/** 清单、共享回执及快照展示对象；价格投影保留明确类型。 */
public final class PlanningViews {
  private PlanningViews() {}

  public record Prices(int dailyRentPriceFen, int depositAmountFen) {}

  public sealed interface Display permits HiddenDisplay, PricedDisplay {}

  public record HiddenDisplay(
      String skuId,
      String factoryId,
      String factoryName,
      String name,
      String assetType,
      String typeCode,
      String typeLabel,
      List<String> styleCodes,
      List<String> styleLabels,
      String sizeDesc,
      String lengthCm,
      String widthCm,
      String heightCm,
      String weightKg,
      String materialDesc,
      PublicImageViewVO image)
      implements Display {}

  public record PricedDisplay(
      String skuId,
      String factoryId,
      String factoryName,
      String name,
      String assetType,
      String typeCode,
      String typeLabel,
      List<String> styleCodes,
      List<String> styleLabels,
      String sizeDesc,
      String lengthCm,
      String widthCm,
      String heightCm,
      String weightKg,
      String materialDesc,
      PublicImageViewVO image,
      Prices prices)
      implements Display {}

  public record Receipt(
      String requestId,
      String operation,
      String resourceType,
      String resourceId,
      Integer resourceVersion,
      String outcome,
      String appliedAt) {}

  public record Availability(int maxRentableQuantity, boolean isRentable, String reasonCode) {}

  public record ChecklistItem(
      String id,
      PricedDisplay display,
      int quantity,
      String remark,
      int sortOrder,
      Availability availability,
      int rentEstimateFen,
      int depositEstimateFen) {}

  public record ChecklistSummary(
      String id,
      String name,
      String startDate,
      String endDate,
      int rentalDays,
      String status,
      int version,
      String sourceType,
      String sourceSchemeId,
      String currentCheckoutBatchId,
      int itemCount,
      String quantityTotal,
      int estimatedDailyRentFen,
      int estimatedDepositFen,
      String createdAt,
      String updatedAt) {}

  public record ChecklistGroup(
      String factoryId,
      String factoryName,
      List<ChecklistItem> items,
      int rentEstimateFen,
      int depositEstimateFen) {}

  public record Submission(boolean canSubmit, String reasonCode, String opensAt, String closesAt) {}

  public record ChecklistActions(
      boolean canEdit, boolean canActivate, boolean canContinueSelecting) {}

  public record ChecklistDetail(
      String id,
      String name,
      String startDate,
      String endDate,
      int rentalDays,
      String status,
      int version,
      String sourceType,
      String sourceSchemeId,
      String currentCheckoutBatchId,
      int itemCount,
      String quantityTotal,
      int estimatedDailyRentFen,
      int estimatedDepositFen,
      String createdAt,
      String updatedAt,
      List<ChecklistGroup> groups,
      int rentEstimateFen,
      int depositEstimateFen,
      Submission submission,
      @com.fasterxml.jackson.annotation.JsonProperty("actions")
          ChecklistActions checklistActions) {}

  public record Context(
      int editingSchemeCount,
      int schemeLimit,
      int activeChecklistCount,
      int checklistLimit,
      ChecklistSummary currentChecklist,
      boolean tradingEnabled) {}
}
