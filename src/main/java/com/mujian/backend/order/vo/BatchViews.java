package com.mujian.backend.order.vo;

import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import java.util.List;

/** 批次页面按真实状态决定下一动作，支付签名不进入回执或持久快照。 */
public final class BatchViews {
  /** 禁止实例化此静态结构容器。 */
  private BatchViews() {}

  public record CouponReturn(
      String status, String userCouponId, String returnedAt, String validUntil) {}

  public record Payment(
      String id,
      String paymentNo,
      String status,
      int payableFen,
      int paidFen,
      int refundedFen,
      String expireAt,
      String paidAt,
      String lastCheckedAt) {}

  public record Batch(
      CouponReturn couponReturn,
      String id,
      String batchNo,
      String checklistId,
      String status,
      int version,
      CheckoutViews.Amounts amounts,
      Payment payment,
      List<String> orderIds,
      List<String> refundIds,
      String recoveryMode,
      String nextAction,
      boolean canContinuePay,
      boolean canCancelPayment,
      String holdExpireAt,
      String createdAt) {}

  public record Submission(
      TradingReceipt receipt, Batch batch, PaymentGateway.Parameters parameters) {}

  public record Launch(
      String batchId, Payment payment, PaymentGateway.Parameters parameters, String nextAction) {}
}
