package com.mujian.backend.prop.dto;

import java.util.List;

public record SnAdjustmentDTO(
    String adjustmentType,
    Integer quantity,
    String reason,
    List<String> snIds,
    String abnormalResult) {}
