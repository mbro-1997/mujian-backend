package com.mujian.backend.order.policy;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 普通出库前取消的科目试算，金额来自原成单现金快照。 */
public final class CancellationCalculation {
  public record Result(
      int rateBps,
      int deductionFen,
      int rentRefundFen,
      int depositRefundFen,
      int freightRefundFen,
      int totalRefundFen) {}

  /** 禁止实例化此静态结构容器。 */
  private CancellationCalculation() {}

  /** 48/24小时边界含等号，比例基数为原租金，实际扣款不超过现金租金。 */
  public static Result calculate(
      boolean accepted,
      LocalDate startDate,
      LocalDateTime now,
      int originalRentFen,
      int cashRentFen,
      int depositFen,
      int freightFen) {
    if (originalRentFen < 0
        || cashRentFen < 0
        || cashRentFen > originalRentFen
        || depositFen < 0
        || freightFen < 0) throw new IllegalArgumentException("invalid cancellation cash");
    LocalDateTime starts = startDate.atStartOfDay();
    int rate =
        !accepted || now.isBefore(starts.minusHours(48))
            ? 0
            : !now.isAfter(starts.minusHours(24)) ? 3000 : 6000;
    int deduction = (int) Math.min(cashRentFen, (long) originalRentFen * rate / 10000);
    int rent = cashRentFen - deduction;
    return new Result(
        rate,
        deduction,
        rent,
        depositFen,
        freightFen,
        Math.toIntExact((long) rent + depositFen + freightFen));
  }
}
