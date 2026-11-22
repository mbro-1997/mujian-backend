package com.mujian.backend.prop.dto;

import java.util.List;

public record EditOptionsDTO(
    Integer expectedSkuVersion,
    String typeCode,
    List<String> styleCodes,
    String transportSuggestion) {}
