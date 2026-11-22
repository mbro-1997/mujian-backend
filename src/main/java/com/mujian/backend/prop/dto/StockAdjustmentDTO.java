package com.mujian.backend.prop.dto;

public record StockAdjustmentDTO(
    String adjustmentType, Integer quantity, String reason, String abnormalResult) {}
