package com.mujian.backend.scheme.dto;

import java.util.List;

/** 方案及分享/PDF专属请求，保持既有JSON字段与序列化。 */
public final class SchemeDtos {
  /** 仅作为相关record的命名空间，不创建包装实例。 */
  private SchemeDtos() {}

  public record CreateScheme(String name, String description) {}

  public record SchemeEntry(String skuId, int quantity, String remark) {}

  public record SaveAction(
      com.mujian.backend.common.enums.SchemeSaveAction action,
      String skuId,
      String itemId,
      Integer quantity,
      String remark,
      Integer sortOrder,
      String name,
      String description) {}

  public record UpdateScheme(List<SaveAction> actions) {}

  public record ReorderScheme(Integer expectedVersion, List<String> itemIds) {}

  public record ShareSet(Integer expectedVersion, boolean hidePrice) {}

  public record ExportPdf(boolean hidePrice) {}
}
