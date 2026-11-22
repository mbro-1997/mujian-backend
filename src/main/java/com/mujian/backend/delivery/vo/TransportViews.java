package com.mujian.backend.delivery.vo;

import com.mujian.backend.delivery.dto.TransportDtos.ExtraFee;
import java.util.List;

/** 运输配置接口展示，BIGINT容量使用精确十进制字符串。 */
public final class TransportViews {
  /** 禁止实例化此静态结构容器。 */
  private TransportViews() {}

  public record Vehicle(
      String id,
      int version,
      String tierCode,
      String tierName,
      int cargoLengthMm,
      int cargoWidthMm,
      int cargoHeightMm,
      String effectiveVolumeCm3,
      String safePayloadGram,
      int sortNo,
      String status) {}

  public record PriceRule(
      String id,
      int version,
      String status,
      String ruleVersion,
      String cityCode,
      String vehicleTierId,
      int distanceMinMeter,
      Integer distanceMaxMeter,
      int includedDistanceMeter,
      int guideMinFen,
      int guideMaxFen,
      int ownVehicleSettleFen,
      Integer extraKmFen,
      String effectiveAt,
      String expireAt,
      List<ExtraFee> extraFees) {}

  public record PriceSheet(List<Vehicle> vehicles, List<PriceRule> rules, String generatedAt) {}
}
