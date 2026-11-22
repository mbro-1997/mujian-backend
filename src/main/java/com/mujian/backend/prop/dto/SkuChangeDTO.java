package com.mujian.backend.prop.dto;

import java.util.List;

/** 道具已批准资料的完整更新申请，不接受库存和免审选项。 */
public record SkuChangeDTO(
    Integer expectedSkuVersion, String previousApplicationId, Fields fields, String applyRemark) {
  public record Fields(
      String propName,
      String sizeDesc,
      String lengthCm,
      String widthCm,
      String heightCm,
      String materialDesc,
      String fireResistantCode,
      String weightKg,
      Integer dailyRentPriceFen,
      Integer depositAmountFen,
      List<ImageInputDTO> images,
      String remark,
      Integer packagingFactorBps) {}
}
