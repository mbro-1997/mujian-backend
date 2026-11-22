package com.mujian.backend.settlement.policy;

import java.util.*;

/** 先对订单的现金租金取整，再按来源租金占比分配，以稳定子单号处置相同余数。 */
public final class SettlementAmounts {
  /** 仅承载本领域的结构定义或纯计算。 */
  private SettlementAmounts() {}

  public record Cash(String suborderId, int rentBaseFen, int freightFen) {}

  public record Split(String suborderId, int commissionFen, int factoryUnlockFen) {}

  private record Fraction(Cash cash, int floor, long remainder) {}

  /** 按订单先取整佣金，再按来源基数分配余数。 */
  public static List<Split> split(List<Cash> sources) {
    Set<String> unique = new HashSet<>();
    long sum = 0;
    for (Cash cash : sources) {
      if (cash.rentBaseFen() < 0 || cash.freightFen() < 0 || !unique.add(cash.suborderId()))
        throw new IllegalArgumentException("invalid cash source");
      sum = Math.addExact(sum, cash.rentBaseFen());
    }
    int base = Math.toIntExact(sum), commission = base / 5;
    List<Fraction> ranked = new ArrayList<>();
    int remaining = commission;
    for (Cash cash : sources) {
      long numerator = (long) commission * cash.rentBaseFen();
      int floor = base == 0 ? 0 : (int) (numerator / base);
      ranked.add(new Fraction(cash, floor, base == 0 ? 0 : numerator % base));
      remaining -= floor;
    }
    ranked.sort(
        Comparator.comparingLong(Fraction::remainder)
            .reversed()
            .thenComparing(f -> f.cash().suborderId()));
    List<Split> result = new ArrayList<>();
    for (int i = 0; i < ranked.size(); i++) {
      Fraction fraction = ranked.get(i);
      int fee = fraction.floor() + (i < remaining ? 1 : 0);
      result.add(
          new Split(
              fraction.cash().suborderId(),
              fee,
              Math.addExact(fraction.cash().rentBaseFen() - fee, fraction.cash().freightFen())));
    }
    result.sort(Comparator.comparing(Split::suborderId));
    return List.copyOf(result);
  }
}
