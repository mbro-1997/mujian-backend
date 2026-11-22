package com.mujian.backend.payment.dto;

import java.time.LocalDateTime;

/** 支付状态更新使用明确事实与CAS版本，不将HTTP响应整包入库。 */
public final class PaymentWrites {
  /** 禁止实例化此静态结构容器。 */
  private PaymentWrites() {}

  public record SuborderFact(
      String id,
      String transactionId,
      String tradeState,
      int paidFen,
      LocalDateTime paidAt,
      LocalDateTime now) {}

  public record Summary(
      String id,
      String status,
      int paidFen,
      LocalDateTime paidAt,
      String tradeState,
      LocalDateTime now) {}

  public record Attempt(String id, int version, LocalDateTime now, LocalDateTime next) {}

  public record Finish(
      String id,
      int version,
      String prepayId,
      String errorCode,
      LocalDateTime now,
      LocalDateTime next) {}
}
