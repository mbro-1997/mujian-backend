package com.mujian.backend.delivery.policy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 包装、车型、运价的整数计算；不将地图失败降级为零距离。 */
public final class TransportCalculation {
  public record Item(
      BigDecimal lengthCm,
      BigDecimal widthCm,
      BigDecimal heightCm,
      BigDecimal weightKg,
      int packagingFactorBps,
      int quantity) {}

  public record Vehicle(
      String id,
      int lengthMm,
      int widthMm,
      int heightMm,
      long volumeCm3,
      long payloadGram,
      int sortNo) {}

  public record Load(BigInteger volumeCm3, BigInteger weightGram) {}

  public record Price(int guideMinFen, int guideMaxFen, int ownVehicleFen, int extraKm) {}

  /** 禁止实例化此静态结构容器。 */
  private TransportCalculation() {}

  /** 汇总后才向上取整体积，包装系数不影响重量。 */
  public static Load load(List<Item> itemListItems) {
    if (itemListItems.isEmpty()) throw new IllegalArgumentException("empty transport load");
    BigDecimal volume = BigDecimal.ZERO, weight = BigDecimal.ZERO;
    for (Item item : itemListItems) {
      if (item.quantity() < 1
          || item.packagingFactorBps() < 10000
          || item.packagingFactorBps() > 1000000
          || item.lengthCm().signum() <= 0
          || item.widthCm().signum() <= 0
          || item.heightCm().signum() <= 0
          || item.weightKg().signum() <= 0)
        throw new IllegalArgumentException("invalid packaging parameters");
      BigDecimal quantity = BigDecimal.valueOf(item.quantity());
      volume =
          volume.add(
              item.lengthCm()
                  .multiply(item.widthCm())
                  .multiply(item.heightCm())
                  .multiply(BigDecimal.valueOf(item.packagingFactorBps(), 4))
                  .multiply(quantity));
      weight = weight.add(item.weightKg().movePointRight(3).multiply(quantity));
    }
    return new Load(
        volume.setScale(0, RoundingMode.CEILING).toBigIntegerExact(), weight.toBigIntegerExact());
  }

  /** 单件六向进入车厢并同时满足总容量，按排序及ID选择首个合适车型。 */
  public static Vehicle select(List<Item> itemListItems, List<Vehicle> vehicleListVehicles) {
    Load load = load(itemListItems);
    ArrayList<Vehicle> sorted = new ArrayList<>(vehicleListVehicles);
    sorted.sort(Comparator.comparingInt(Vehicle::sortNo).thenComparing(Vehicle::id));
    for (Vehicle vehicle : sorted) {
      if (load.volumeCm3().compareTo(BigInteger.valueOf(vehicle.volumeCm3())) > 0
          || load.weightGram().compareTo(BigInteger.valueOf(vehicle.payloadGram())) > 0) continue;
      boolean fit = true;
      for (Item item : itemListItems) {
        List<BigDecimal> dimensions =
            new ArrayList<>(
                List.of(
                    item.lengthCm().movePointRight(1),
                    item.widthCm().movePointRight(1),
                    item.heightCm().movePointRight(1)));
        dimensions.sort(Comparator.naturalOrder());
        List<Integer> cabin =
            new ArrayList<>(List.of(vehicle.lengthMm(), vehicle.widthMm(), vehicle.heightMm()));
        cabin.sort(Comparator.naturalOrder());
        for (int index = 0; index < 3; index++)
          if (dimensions.get(index).compareTo(BigDecimal.valueOf(cabin.get(index))) > 0)
            fit = false;
      }
      if (fit) return vehicle;
    }
    return null;
  }

  /** 超出部分整公里上取整，并以精确整数拒绝金额溢出。 */
  public static Price price(
      int distanceMeter,
      int includedMeter,
      int guideMinFen,
      int guideMaxFen,
      int ownVehicleFen,
      Integer extraKmFen) {
    if (distanceMeter < 0
        || includedMeter < 0
        || guideMinFen < 0
        || guideMaxFen < guideMinFen
        || ownVehicleFen < 0
        || extraKmFen != null && extraKmFen < 0)
      throw new IllegalArgumentException("invalid pricing rule");
    long extraKm = (Math.max((long) distanceMeter - includedMeter, 0) + 999) / 1000;
    long extra = Math.multiplyExact(extraKm, extraKmFen == null ? 0 : extraKmFen);
    return new Price(
        Math.toIntExact(guideMinFen + extra),
        Math.toIntExact(guideMaxFen + extra),
        Math.toIntExact(ownVehicleFen + extra),
        Math.toIntExact(extraKm));
  }
}
