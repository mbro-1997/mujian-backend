package com.mujian.backend.payment.vo;

import com.mujian.backend.order.vo.*;
import java.util.List;

/** 管理追溯展示原支付与各厂实收，不返回账户密钥或通知解密原文。 */
public record PaymentTraceVO(
    BatchViews.Payment payment,
    BatchViews.Batch batch,
    List<Suborder> suborders,
    List<RefundViews.Refund> refunds,
    List<CheckoutViews.FactoryQuote> groups,
    String couponUsageId, String scene, String extensionId, String receivableId, @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String customOrderId) {
  public record Suborder(
      String id,
      String factoryId,
      String orderId,
      String paymentStatus,
      String fundsStatus,
      int payableFen,
      int paidFen,
      int refundedFen,
      String channelTradeNo,
      String channelTransactionId,
      String paidAt,
      String freezeExpireAt,
      String freezeAlertStatus) {}
}
