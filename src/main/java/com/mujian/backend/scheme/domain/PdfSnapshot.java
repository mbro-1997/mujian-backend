package com.mujian.backend.scheme.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mujian.backend.planning.vo.PlanningViews.Prices;
import java.util.List;

/** 固定导出快照；隐藏输出在存储阶段就不存在prices字段。 */
public record PdfSnapshot(
    String name,
    String description,
    int contentVersion,
    String templateVersion,
    boolean hidePrice,
    String watermark,
    String generatedForTime,
    String exportedBy,
    List<Item> items) {
  public record Item(
      String name,
      String factoryName,
      String typeLabel,
      List<String> styleLabels,
      String sizeDesc,
      String materialDesc,
      List<String> imageKeys,
      int quantity,
      String remark,
      @JsonInclude(JsonInclude.Include.NON_NULL) Prices prices) {}
}
