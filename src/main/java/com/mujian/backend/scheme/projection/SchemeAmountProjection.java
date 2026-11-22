package com.mujian.backend.scheme.projection;

import java.math.BigDecimal;

/** 方案未变化明细的宽整数金额合计，不返回SKU快照。 */
public record SchemeAmountProjection(BigDecimal dailyRentFen, BigDecimal depositFen) {}
