package com.mujian.backend.delivery.dto;

import java.util.List;

/** 车型及运价请求；创建/更新各自机器契约决定expectedVersion是否出现。 */
public final class TransportDtos {
  /** 禁止实例化此静态结构容器。 */
  private TransportDtos() {}

  public record VehicleInput(
      Integer expectedVersion,
      String tierCode,
      String tierName,
      int cargoLengthMm,
      int cargoWidthMm,
      int cargoHeightMm,
      String effectiveVolumeCm3,
      String safePayloadGram,
      int sortNo,
      String status) {}

  public record ExtraFee(String type, int maxAmountFen) {}

  public record RuleInput(
      Integer expectedVersion,
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

  public record Version(int expectedVersion) {}

  public record EndRule(int expectedVersion, String expireAt) {}
}
