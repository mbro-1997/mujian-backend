package com.mujian.backend.prop.dto;

public record CreateSkuDTO(
    String assetType, SkuInputDTO fields, Integer initialQuantity, String applyRemark) {}
