package com.mujian.backend.prop.projection;

import java.math.BigDecimal;

/** 当前日实际数量投影，跨午夜仍以热占用为准。 */
public record CurrentStockQuantityProjection(
    BigDecimal available, BigDecimal locked, BigDecimal renting) {}
