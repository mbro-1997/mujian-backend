package com.mujian.backend.payment.domain;

import java.time.LocalDateTime;

/** 每厂原渠道子单及单向收款事实。 */
public record ChannelSuborderDO(
    String id,
    String paymentOrderId,
    String factoryId,
    String factoryWechatAccountId,
    String subMchidSnapshot,
    String channelSubOrderNo,
    String channelSubTransactionId,
    int channelSuborderAmountFen,
    String channelSuborderStatus,
    String rentalOrderId,
    String channelDescription,
    boolean profitSharingFlag,
    String paymentStatus,
    String channelTradeState,
    int paidAmountFen,
    int refundedAmountFen,
    LocalDateTime paidAt,
    LocalDateTime closedAt,
    int version,
    LocalDateTime freezeExpireAt,
    String freezeAlertStatus,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
