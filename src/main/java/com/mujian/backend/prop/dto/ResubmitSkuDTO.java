package com.mujian.backend.prop.dto;

public record ResubmitSkuDTO(
    String previousApplicationId,
    Integer expectedSkuVersion,
    SkuInputDTO fields,
    Integer initialQuantity,
    String applyRemark) {}
