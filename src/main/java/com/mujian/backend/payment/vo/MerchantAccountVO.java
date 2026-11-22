package com.mujian.backend.payment.vo;

/** 收款账户展示使用脱敏商户号，不通过人工按钮激活。 */
public record MerchantAccountVO(
    String id,
    String factoryId,
    int version,
    String accountStatus,
    String profitSharingStatus,
    String subjectName,
    String subMerchantMasked,
    String lastSyncedAt,
    boolean onboardingAvailable,
    String nextAction,
    String actionUrl,
    String reasonCode) {}
