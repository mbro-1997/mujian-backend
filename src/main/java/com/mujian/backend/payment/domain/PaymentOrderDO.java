package com.mujian.backend.payment.domain;

import java.time.LocalDateTime;

/** 原合单支付及持久恢复信息；未知支付保留同号查询与关单。 */
public record PaymentOrderDO(
    String id,
    String paymentNo,
    String requestIdempotencyKey,
    String checkoutBatchId,
    String buyerUserId,
    String channelAppId,
    String channelMchId,
    String channelNotifyUrl,
    String channelPrepayId,
    String channelCombineOutTradeNo,
    String payerOpenid,
    String channelTradeState,
    String paymentStatus,
    int payableAmountFen,
    int paidAmountFen,
    int refundedAmountFen,
    LocalDateTime expireAt,
    LocalDateTime paidAt,
    LocalDateTime closedAt,
    int version,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime nextAttemptAt,
    LocalDateTime lastCheckedAt,
    int attemptCount,
    String lastErrorCode,
    LocalDateTime closeRequestedAt,
    String closeReason) {
  /** 首租沿原批次恢复，独立收款沿支付ID恢复。 */
  public String fundingKey() { return checkoutBatchId==null ? id : checkoutBatchId; }
}
