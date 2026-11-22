package com.mujian.backend.planning.dto;

import java.util.List;

/** 清单及跨模块共享命令的明确输入；HTTP形状由统一机器契约校验。 */
public final class PlanningDtos {
  private PlanningDtos() {}

  public record ExpectedVersion(Integer expectedVersion) {}

  public record ItemSet(Integer expectedVersion, String skuId, int quantity, String remark) {}

  public record CreateChecklist(
      String sourceType,
      String schemeId,
      Integer expectedSchemeVersion,
      String startDate,
      String endDate) {}

  public record ChecklistEntry(String itemId, int quantity, String remark) {}

  public record SaveChecklist(Integer expectedVersion, List<ChecklistEntry> items) {}

  public record ChecklistItemUpdate(Integer expectedVersion, int quantity, String remark) {}
}
