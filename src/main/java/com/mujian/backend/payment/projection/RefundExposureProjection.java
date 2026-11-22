package com.mujian.backend.payment.projection;

/** 已成功及仍保留的原退款科目汇总，用长整数检查叠加上限。 */
public record RefundExposureProjection(
    long rentFen, long depositFen, long freightFen, long totalFen) {}
