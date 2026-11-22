package com.mujian.backend.payment.domain;

import java.time.LocalDateTime;

/** 仅持久已验签加密原通知和来源，不保存解密载荷。 */
public record PaymentNotificationDO(
    String id,
    String notifyType,
    String channelEventId,
    String paymentOrderId,
    String refundOrderId,
    String channelOutTradeNo,
    String channelOutRefundNo,
    String eventType,
    String processStatus,
    String rawNotifyJson,
    LocalDateTime receivedAt,
    LocalDateTime processedAt,
    String processMessage,
    LocalDateTime createdAt) {}
