package com.mujian.backend.payment.domain;

import java.time.LocalDateTime;

/** 渠道状态白名单快照，不持久银行卡验证及原始敏感资料。 */
public record MerchantStatusSnapshot(
    int schemaVersion,
    String applymentId,
    String requestNo,
    String subMchid,
    String state,
    String actionUrl,
    LocalDateTime checkedAt) {}
