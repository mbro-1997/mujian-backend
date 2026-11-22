package com.mujian.backend.payment.projection;

import java.time.LocalDateTime;

/** 工厂与可空收款账户联合投影，不使用空行伪造新开户事实。 */
public record MerchantSourceProjection(
    String id,
    String factoryId,
    String ownerId,
    String factoryName,
    String applymentId,
    String subMchid,
    String subjectName,
    String accountStatus,
    String profitSharingStatus,
    String channelStatusSnapshotJson,
    LocalDateTime lastSyncedAt,
    int version,
    String onboardingRequestNo) {}
