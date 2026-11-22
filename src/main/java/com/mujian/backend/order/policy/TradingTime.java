package com.mujian.backend.order.policy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 本批日期边界；所有调用方传入统一业务时钟，不读取系统时间。 */
public final class TradingTime {
  /** 禁止实例化此静态结构容器。 */
  private TradingTime() {}

  /** 14天窗口和使用前两天11点仅限制新批次。 */
  public static boolean canSubmit(LocalDate start, LocalDateTime now) {
    return !now.isBefore(start.minusDays(14).atStartOfDay())
        && now.isBefore(start.minusDays(2).atTime(11, 0));
  }

  /** 支付到期固定于首次提交，恢复不得重算。 */
  public static LocalDateTime paymentExpiry(LocalDateTime submittedAt) {
    return submittedAt.plusMinutes(10);
  }

  /** 渠道首次创建必须能保持原固定到期时间。 */
  public static boolean canCreatePayment(LocalDateTime expiry, LocalDateTime now) {
    return Duration.between(now, expiry.withNano(0)).getSeconds() >= 60;
  }

  /** 工厂接单必须严格早于接单截止，等于截止交给超时处理。 */
  public static boolean canAccept(LocalDateTime deadline, LocalDateTime now) {
    return now.isBefore(deadline);
  }
}
