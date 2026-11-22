package com.mujian.backend.scheme.vo;

import com.mujian.backend.planning.vo.PlanningViews.*;
import java.util.List;

/** 方案及分享/PDF专属响应，保持既有JSON字段与序列化。 */
public final class SchemeViews {
  /** 仅作为相关record的命名空间，不创建包装实例。 */
  private SchemeViews() {}

  public record SchemeItem(
      String id,
      PricedDisplay display,
      int quantity,
      String remark,
      int sortOrder,
      int dailySubtotalFen,
      int depositSubtotalFen) {}

  public record SchemeSummary(
      String id,
      String name,
      String description,
      String status,
      int version,
      int contentVersion,
      int itemCount,
      String quantityTotal,
      int estimatedDailyRentFen,
      int estimatedDepositFen,
      String createdAt,
      String updatedAt,
      String archivedAt) {}

  public record ShareConfig(
      String id,
      String token,
      String status,
      boolean hidePrice,
      String expiresAt,
      String viewCount) {}

  public record ShareIssued(Receipt receipt, ShareConfig share) {}

  public record SchemeActions(
      boolean canEdit,
      boolean canArchive,
      boolean canShare,
      boolean canExport,
      boolean canCreateChecklist) {}

  public record SchemeDetail(
      String id,
      String name,
      String description,
      String status,
      int version,
      int contentVersion,
      int itemCount,
      String quantityTotal,
      int estimatedDailyRentFen,
      int estimatedDepositFen,
      String createdAt,
      String updatedAt,
      String archivedAt,
      List<SchemeItem> items,
      ShareConfig share,
      @com.fasterxml.jackson.annotation.JsonProperty("actions") SchemeActions schemeActions) {}

  public sealed interface SharedScheme permits HiddenScheme, PricedScheme {}

  public record HiddenItem(HiddenDisplay display, int quantity, String remark, int sortOrder) {}

  public record PricedItem(
      PricedDisplay display,
      int quantity,
      String remark,
      int sortOrder,
      int dailySubtotalFen,
      int depositSubtotalFen) {}

  public record HiddenScheme(
      String name,
      String description,
      String status,
      int contentVersion,
      String quantityTotal,
      String disclaimer,
      boolean hidePrice,
      List<HiddenItem> items)
      implements SharedScheme {}

  public record PricedScheme(
      String name,
      String description,
      String status,
      int contentVersion,
      String quantityTotal,
      String disclaimer,
      boolean hidePrice,
      List<PricedItem> items,
      int estimatedDailyRentFen,
      int estimatedDepositFen)
      implements SharedScheme {}

  public record ExportStatus(
      String id,
      String schemeId,
      String status,
      int sourceContentVersion,
      boolean hidePrice,
      String templateVersion,
      String createdAt,
      String generatedAt,
      String fileExpiresAt,
      boolean canDownload,
      String failureReason) {}
}
