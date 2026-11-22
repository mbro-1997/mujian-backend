package com.mujian.backend.payment.projection;

/** 退款通知核验所需的原支付来源，查询不包含银行卡等渠道敏感资料。 */
public record RefundSourceProjection(
    String id,
    String paymentOrderId,
    String paymentChannelSuborderId,
    String channelOutRefundNo,
    String channelOutTradeNo,
    String channelTransactionId,
    int refundAmountFen,
    String channelAppId,
    String channelMchId,
    String subMchidSnapshot,
    int channelSuborderAmountFen) {}
