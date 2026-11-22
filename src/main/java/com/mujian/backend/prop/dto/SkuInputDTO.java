package com.mujian.backend.prop.dto;

import java.util.List;

public record SkuInputDTO(
    String propName,
    String typeCode,
    List<String> styleCodes,
    String sizeDesc,
    String lengthCm,
    String widthCm,
    String heightCm,
    String materialDesc,
    String fireResistantCode,
    String weightKg,
    String transportSuggestion,
    Integer dailyRentPriceFen,
    Integer depositAmountFen,
    List<ImageInputDTO> images,
    String remark,
    Integer packagingFactorBps) {}
