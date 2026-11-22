package com.mujian.backend.order.projection;

/** 当前工厂与收款账户事实，原收款商户只在服务端冻结。 */
public record CheckoutFactoryProjection(
    String id,
    String ownerUserId,
    String factoryName,
    String factoryAddress,
    long profileVersion,
    String factoryStatus,
    String ownerStatus,
    String merchantId,
    String subMchid,
    String accountStatus,
    String profitSharingStatus,
    Integer merchantVersion) {}
