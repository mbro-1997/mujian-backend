package com.mujian.backend.order.dto;

import java.time.LocalDateTime;

/** 订单终止和扣款的同事务输入；渠道到账另由退款事实确认。 */
public final class OrderWrites {
  /** 禁止实例化此静态结构容器。 */
  private OrderWrites() {}

  public record Termination(
      String id,
      int version,
      String orderStatus,
      String acceptStatus,
      String rejectType,
      String reason,
      String refundType,
      int deductionFen,
      int refundFen,
      LocalDateTime now) {}

  public record Deduction(
      String id,
      String orderId,
      int amountFen,
      int baseFen,
      int rateBps,
      String snapshot,
      String reason,
      String actor,
      LocalDateTime now) {}
}
