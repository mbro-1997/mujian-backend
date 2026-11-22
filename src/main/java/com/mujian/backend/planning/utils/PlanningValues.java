package com.mujian.backend.planning.utils;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.PlanningReason;
import java.math.BigInteger;

/** 数量、版本、金额的纯边界计算。 */
public final class PlanningValues {
  public static final String DISCLAIMER = "本方案仅供参考，不锁定库存，不代表工厂最终可供或租赁承诺";

  private PlanningValues() {}

  /** 校验版本，禁止覆盖他端已保存内容。 */
  public static void version(int actual, Integer expected) {
    if (expected == null || actual != expected) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
  }

  /** 整数分运算越界按业务冲突拒绝。 */
  public static int amount(BigInteger bigIntegerValue) {
    if (bigIntegerValue.signum() < 0
        || bigIntegerValue.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0)
      throw conflict(PlanningReason.AMOUNT_OUT_OF_RANGE.name());
    return bigIntegerValue.intValue();
  }

  /** 使用宽整数完成金额乘法。 */
  public static int multiply(int price, int quantity, int days) {
    return amount(
        BigInteger.valueOf(price)
            .multiply(BigInteger.valueOf(quantity))
            .multiply(BigInteger.valueOf(days)));
  }

  /** 使用宽整数完成金额合计。 */
  public static int add(int left, int right) {
    return amount(BigInteger.valueOf(left).add(BigInteger.valueOf(right)));
  }

  /** 递增版本前检查INT边界。 */
  public static int next(int value) {
    if (value == Integer.MAX_VALUE) throw inconsistent();
    return value + 1;
  }

  /** 明细数量始终为正整数。 */
  public static void quantity(int quantity) {
    if (quantity < 1) throw ApiFailure.invalid();
  }

  /** 本批持久数据异常不暴露SQL或快照。 */
  public static ApiFailure inconsistent() {
    return new ApiFailure(
        500, D2Codes.CODE_DATA_INTEGRITY_ERROR, PlanningReason.PLANNING_DATA_INCONSISTENT.name());
  }
}
