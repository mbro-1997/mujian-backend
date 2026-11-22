package com.mujian.backend.payment.dto;

/** 已核对原渠道实收的现金科目；优惠不是可退款现金。 */
public record CashComponents(int rentFen, int depositFen, int freightFen) {
  /** 精确加总，溢出不以负数流入资金表。 */
  public int totalFen() {
    return Math.addExact(Math.addExact(rentFen, depositFen), freightFen);
  }
}
