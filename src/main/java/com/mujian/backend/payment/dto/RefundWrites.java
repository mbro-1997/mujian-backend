package com.mujian.backend.payment.dto;

import java.time.LocalDateTime;

/** 已验证退款状态和成功时间，不以请求受理时间充当到账。 */
public final class RefundWrites {
  /** 禁止实例化此静态结构容器。 */
  private RefundWrites() {}

  public record Fact(
      String id,
      String status,
      String channelRefundNo,
      LocalDateTime successAt,
      String error,
      LocalDateTime now,
      LocalDateTime next) {}
}
