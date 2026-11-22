package com.mujian.backend.delivery.converter;

import static com.mujian.backend.common.support.D2Values.time;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.dto.TransportDtos.ExtraFee;
import com.mujian.backend.delivery.vo.TransportViews.*;
import java.time.LocalDateTime;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** 运输配置纯转换，不执行查询或状态修改。 */
@Component
public class TransportConverter {
  private final ObjectMapper objectMapper;

  /** 使用同一JSON格式解析价格额外费用白名单。 */
  public TransportConverter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** BIGINT保持十进制精度。 */
  public Vehicle vehicle(VehicleDO vehicleDO) {
    return new Vehicle(
        vehicleDO.id(),
        vehicleDO.version(),
        vehicleDO.tierCode(),
        vehicleDO.tierName(),
        vehicleDO.cargoLengthMm(),
        vehicleDO.cargoWidthMm(),
        vehicleDO.cargoHeightMm(),
        Long.toString(vehicleDO.effectiveVolumeCm3()),
        Long.toString(vehicleDO.safePayloadGram()),
        vehicleDO.sortNo(),
        vehicleDO.tierStatus());
  }

  /** 将持久规则转换为明确响应字段。 */
  public PriceRule rule(PriceRuleDO priceRuleDO, LocalDateTime now) {
    return new PriceRule(
        priceRuleDO.id(),
        priceRuleDO.version(),
        D2Codes.CODE_ACTIVE.equals(priceRuleDO.ruleStatus())
                && priceRuleDO.expireAt() != null
                && !priceRuleDO.expireAt().isAfter(now)
            ? TradingState.expired.name()
            : priceRuleDO.ruleStatus(),
        priceRuleDO.ruleVersion(),
        priceRuleDO.cityCode(),
        priceRuleDO.vehicleTierId(),
        priceRuleDO.distanceMinMeter(),
        priceRuleDO.distanceMaxMeter(),
        priceRuleDO.includedDistanceMeter(),
        priceRuleDO.guideMinAmountFen(),
        priceRuleDO.guideMaxAmountFen(),
        priceRuleDO.ownVehicleSettleAmountFen(),
        priceRuleDO.extraKmAmountFen(),
        time(priceRuleDO.effectiveAt()),
        time(priceRuleDO.expireAt()),
        Arrays.asList(objectMapper.readValue(priceRuleDO.allowedExtraFeeJson(), ExtraFee[].class)));
  }
}
