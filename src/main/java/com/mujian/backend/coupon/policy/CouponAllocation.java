package com.mujian.backend.coupon.policy;

import com.mujian.backend.common.enums.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** 优惠只抵租金；每厂现金至少一分，封顶后按剩余权重重新分摊。 */
public final class CouponAllocation {
  public record Item(String snapshotItemId, String factoryId, int rentFen) {}

  public record Factory(String factoryId, int rentFen, int otherCashFen, boolean eligible) {}

  public record Share(String id, int discountFen) {}

  private record Weight(String id, long rent, long cap) {}

  private record Remainder(String id, long rent, BigInteger remainder) {}

  /** 禁止实例化此静态结构容器。 */
  private CouponAllocation() {}

  /** 计算固定金额或比例券的实际优惠，门槛由命中范围原租金判断。 */
  public static int saving(
      String type,
      Integer faceFen,
      Integer rateBps,
      int thresholdFen,
      List<Factory> factoryListFactories) {
    long eligible = 0, capacity = 0;
    HashSet<String> seen = new HashSet<>();
    for (Factory factory : factoryListFactories) {
      if (!seen.add(factory.factoryId()) || factory.rentFen() < 0 || factory.otherCashFen() < 0)
        throw new IllegalArgumentException("invalid coupon factory");
      if (!factory.eligible()) continue;
      eligible = Math.addExact(eligible, factory.rentFen());
      capacity =
          Math.addExact(
              capacity,
              Math.max(
                  0,
                  Math.min(
                      factory.rentFen(), (long) factory.rentFen() + factory.otherCashFen() - 1)));
    }
    if (thresholdFen < 0) throw new IllegalArgumentException("invalid coupon threshold");
    if (eligible < thresholdFen || eligible == 0) return 0;
    long requested;
    if (TradingCouponType.fixed_amount.name().equals(type)) {
      if (faceFen == null || faceFen <= 0)
        throw new IllegalArgumentException("invalid coupon face");
      requested = Math.min(faceFen, eligible);
    } else if (TradingCouponType.percentage.name().equals(type)) {
      if (rateBps == null || rateBps <= 0 || rateBps > 10000)
        throw new IllegalArgumentException("invalid coupon rate");
      requested =
          BigInteger.valueOf(eligible)
              .multiply(BigInteger.valueOf(rateBps))
              .divide(BigInteger.valueOf(10000))
              .longValueExact();
    } else throw new IllegalArgumentException("invalid coupon type");
    return Math.toIntExact(Math.min(requested, capacity));
  }

  /** 厂级按余数、原租金、ID稳定分配；未命中工厂固定为零。 */
  public static List<Share> factories(int savingFen, List<Factory> factoryListFactories) {
    List<Weight> weightListWeights = new ArrayList<>();
    for (Factory factory : factoryListFactories) {
      long cap =
          factory.eligible()
              ? Math.max(
                  0,
                  Math.min(
                      factory.rentFen(), (long) factory.rentFen() + factory.otherCashFen() - 1))
              : 0;
      weightListWeights.add(
          new Weight(factory.factoryId(), factory.eligible() ? factory.rentFen() : 0, cap));
    }
    return distribute(savingFen, weightListWeights, true);
  }

  /** 同厂各项按租金权重分配，尾分按快照ID保持重放一致。 */
  public static List<Share> items(int savingFen, List<Item> itemListItems) {
    List<Weight> weightListWeights = new ArrayList<>();
    String factoryId = itemListItems.isEmpty() ? null : itemListItems.get(0).factoryId();
    for (Item item : itemListItems) {
      if (!item.factoryId().equals(factoryId))
        throw new IllegalArgumentException("mixed factory allocation");
      weightListWeights.add(new Weight(item.snapshotItemId(), item.rentFen(), item.rentFen()));
    }
    return distribute(savingFen, weightListWeights, false);
  }

  /** 封顶对象先移除并重新计算比例，避免先取尾分造成额度偏移。 */
  private static List<Share> distribute(
      int amount, List<Weight> weightListWeights, boolean factoryTie) {
    if (amount < 0) throw new IllegalArgumentException("negative allocation");
    long capacity = 0;
    Map<String, Long> assigned = new HashMap<>();
    List<Weight> weightListRemaining = new ArrayList<>();
    for (Weight weight : weightListWeights) {
      if (weight.rent() < 0
          || weight.cap() < 0
          || weight.cap() > weight.rent()
          || assigned.put(weight.id(), 0L) != null)
        throw new IllegalArgumentException("invalid allocation weight");
      capacity = Math.addExact(capacity, weight.cap());
      if (weight.cap() > 0) weightListRemaining.add(weight);
    }
    if (amount > capacity) throw new IllegalArgumentException("allocation exceeds capacity");
    long left = amount;
    while (left > 0) {
      long totalWeight = 0;
      for (Weight weight : weightListRemaining)
        totalWeight = Math.addExact(totalWeight, weight.rent());
      BigInteger denominator = BigInteger.valueOf(totalWeight);
      List<Weight> weightListCapped = new ArrayList<>();
      for (Weight weight : weightListRemaining) {
        BigInteger numerator = BigInteger.valueOf(left).multiply(BigInteger.valueOf(weight.rent()));
        if (numerator.compareTo(BigInteger.valueOf(weight.cap()).multiply(denominator)) >= 0)
          weightListCapped.add(weight);
      }
      if (!weightListCapped.isEmpty()) {
        for (Weight weight : weightListCapped) {
          assigned.put(weight.id(), weight.cap());
          left -= weight.cap();
        }
        weightListRemaining.removeAll(weightListCapped);
        continue;
      }
      List<Remainder> remainderListRemainders = new ArrayList<>();
      long rounded = 0;
      for (Weight weight : weightListRemaining) {
        BigInteger[] division =
            BigInteger.valueOf(left)
                .multiply(BigInteger.valueOf(weight.rent()))
                .divideAndRemainder(denominator);
        long floor = division[0].longValueExact();
        assigned.put(weight.id(), floor);
        rounded += floor;
        remainderListRemainders.add(new Remainder(weight.id(), weight.rent(), division[1]));
      }
      Comparator<Remainder> comparator = Comparator.comparing(Remainder::remainder).reversed();
      if (factoryTie)
        comparator = comparator.thenComparing(Comparator.comparingLong(Remainder::rent).reversed());
      remainderListRemainders.sort(comparator.thenComparing(Remainder::id));
      for (int index = 0; index < left - rounded; index++) {
        String id = remainderListRemainders.get(index).id();
        assigned.put(id, assigned.get(id) + 1);
      }
      left = 0;
    }
    List<Share> shareListResult = new ArrayList<>();
    for (Weight weight : weightListWeights)
      shareListResult.add(new Share(weight.id(), Math.toIntExact(assigned.get(weight.id()))));
    return List.copyOf(shareListResult);
  }
}
