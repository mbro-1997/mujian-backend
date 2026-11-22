package com.mujian.backend.payment.vo;

import com.mujian.backend.order.vo.BatchViews;

/** 已成功实退和仍在处理的义务明确区分，不向买家暴露原始渠道载荷。 */
public final class RefundViews {
  /** 禁止实例化此静态结构容器。 */
  private RefundViews() {}

  public record Refund(
      BatchViews.CouponReturn couponReturn,
      String id,
      String refundNo,
      String businessType,
      String businessId,
      String paymentId,
      String suborderId,
      String type,
      String status,
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Integer goodsFen,
      int rentFen,
      int depositFen,
      int freightFen,
      int totalFen,
      String reason,
      String requestedAt,
      String successAt,
      String failureReasonCode) {}

  public record Trace(
      Refund refund,
      String channelOutRefundNo,
      String channelRefundId,
      String lastCheckedAt,
      int attemptCount) {}
}
