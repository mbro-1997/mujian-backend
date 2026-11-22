package com.mujian.backend.delivery.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.delivery.converter.TransportConverter;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.dto.*;
import com.mujian.backend.delivery.dto.TransportDtos.*;
import com.mujian.backend.delivery.mapper.TransportMapper;
import com.mujian.backend.delivery.service.TransportService;
import com.mujian.backend.delivery.vo.TransportViews.*;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.TradingOperations;
import com.mujian.backend.order.vo.TradingReceipt;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class TransportServiceImpl implements TransportService {
  private final TransportMapper transportMapper;
  private final TransportConverter transportConverter;
  private final TradingOperations tradingOperations;
  private final VerificationAccess verificationAccess;
  private final FactoryAccess factoryAccess;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 装配既有权限、根锁、事务回执和本模块持久化。 */
  public TransportServiceImpl(
      TransportMapper transportMapper,
      TransportConverter transportConverter,
      TradingOperations tradingOperations,
      VerificationAccess verificationAccess,
      FactoryAccess factoryAccess,
      ObjectMapper objectMapper,
      Clock clock) {
    this.transportMapper = transportMapper;
    this.transportConverter = transportConverter;
    this.tradingOperations = tradingOperations;
    this.verificationAccess = verificationAccess;
    this.factoryAccess = factoryAccess;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 十进制容量先检查有符号BIGINT边界，格式合法也不能溢出成500。 */
  private long capacity(String text) {
    try {
      long value = Long.parseLong(text);
      if (value <= 0) throw ApiFailure.invalid();
      return value;
    } catch (NumberFormatException failure) {
      throw ApiFailure.invalid();
    }
  }

  /** 管理列表先授权再分页。 */
  @Transactional(readOnly = true)
  public PageResult<Vehicle> vehicles(String actor, TransportQuery transportQuery) {
    verificationAccess.admin(actor, "admin:trade:transport:manage");
    List<Vehicle> vehicleListResult = new ArrayList<>();
    for (VehicleDO vehicleDO : transportMapper.selectVehiclesByQuery(transportQuery))
      vehicleListResult.add(transportConverter.vehicle(vehicleDO));
    return PageResult.of(
        vehicleListResult,
        transportMapper.countVehiclesByQuery(transportQuery),
        transportQuery.page());
  }

  /** 读取管理车型详情。 */
  @Transactional(readOnly = true)
  public Vehicle vehicle(String actor, String id) {
    verificationAccess.admin(actor, "admin:trade:transport:manage");
    VehicleDO vehicleDO = transportMapper.selectVehicleById(id, false);
    if (vehicleDO == null) throw missing();
    return transportConverter.vehicle(vehicleDO);
  }

  /** 同号回执优先于版本检查，写入和回执原子提交。 */
  public TradingReceipt saveVehicle(
      TradingCommand tradingCommand, String id, VehicleInput vehicleInputInput) {
    tradingOperations.authorize(tradingCommand.actor(), tradingCommand.operation());
    tradingOperations.lock(tradingCommand.actor());
    TradingReceipt tradingReceiptReplay = tradingOperations.replay(tradingCommand);
    if (tradingReceiptReplay != null) return tradingReceiptReplay;
    VehicleDO vehicleDOBefore = id == null ? null : transportMapper.selectVehicleById(id, true);
    if (id != null && vehicleDOBefore == null) throw missing();
    if (vehicleDOBefore != null
        && !Objects.equals(vehicleInputInput.expectedVersion(), vehicleDOBefore.version()))
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    LocalDateTime at = now(clock);
    VehicleDO vehicleDO =
        new VehicleDO(
            vehicleDOBefore == null ? Ids.next() : id,
            vehicleInputInput.tierCode(),
            vehicleInputInput.tierName(),
            vehicleInputInput.cargoLengthMm(),
            vehicleInputInput.cargoWidthMm(),
            vehicleInputInput.cargoHeightMm(),
            capacity(vehicleInputInput.effectiveVolumeCm3()),
            capacity(vehicleInputInput.safePayloadGram()),
            vehicleInputInput.sortNo(),
            vehicleInputInput.status(),
            vehicleDOBefore == null ? at : vehicleDOBefore.createdAt(),
            at,
            vehicleDOBefore == null ? 0 : Math.addExact(vehicleDOBefore.version(), 1));
    try {
      if (vehicleDOBefore == null) transportMapper.insertVehicle(vehicleDO);
      else if (transportMapper.updateVehicle(vehicleDO, vehicleDOBefore.version()) != 1)
        throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    } catch (org.springframework.dao.DuplicateKeyException duplicateKeyException) {
      throw ApiFailure.invalid();
    }
    return tradingOperations.record(
        tradingCommand,
        TradingResource.transport_vehicle_tier.name(),
        vehicleDO.id(),
        vehicleDO.version());
  }

  /** 管理规则列表只展示本次筛选结果。 */
  @Transactional(readOnly = true)
  public PageResult<PriceRule> rules(String actor, TransportQuery transportQuery) {
    verificationAccess.admin(actor, "admin:trade:transport:manage");
    List<PriceRule> priceRuleListResult = new ArrayList<>();
    for (PriceRuleDO priceRuleDO :
        transportMapper.selectPriceRulesByQuery(transportQuery, now(clock)))
      priceRuleListResult.add(transportConverter.rule(priceRuleDO, now(clock)));
    return PageResult.of(
        priceRuleListResult,
        transportMapper.countPriceRulesByQuery(transportQuery, now(clock)),
        transportQuery.page());
  }

  /** 读取管理规则详情。 */
  @Transactional(readOnly = true)
  public PriceRule rule(String actor, String id) {
    verificationAccess.admin(actor, "admin:trade:transport:manage");
    PriceRuleDO priceRuleDO = transportMapper.selectPriceRuleById(id, false);
    if (priceRuleDO == null) throw missing();
    return transportConverter.rule(priceRuleDO, now(clock));
  }

  /** 草稿允许修改；车辆根锁排在规则根锁之前。 */
  public TradingReceipt saveRule(
      TradingCommand tradingCommand, String id, RuleInput ruleInputInput) {
    tradingOperations.authorize(tradingCommand.actor(), tradingCommand.operation());
    tradingOperations.lock(tradingCommand.actor());
    TradingReceipt tradingReceiptReplay = tradingOperations.replay(tradingCommand);
    if (tradingReceiptReplay != null) return tradingReceiptReplay;
    PriceRuleDO priceRuleDOHint =
        id == null ? null : transportMapper.selectPriceRuleById(id, false);
    if (id != null && priceRuleDOHint == null) throw missing();
    TreeSet<String> vehicleIds = new TreeSet<>();
    vehicleIds.add(ruleInputInput.vehicleTierId());
    if (priceRuleDOHint != null) vehicleIds.add(priceRuleDOHint.vehicleTierId());
    for (String vehicleId : vehicleIds)
      if (transportMapper.selectVehicleById(vehicleId, true) == null) throw missing();
    PriceRuleDO priceRuleDOBefore =
        id == null ? null : transportMapper.selectPriceRuleById(id, true);
    if (priceRuleDOBefore != null
        && !Objects.equals(ruleInputInput.expectedVersion(), priceRuleDOBefore.version()))
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if (priceRuleDOBefore != null
        && !TransportRuleStatus.draft.name().equals(priceRuleDOBefore.ruleStatus()))
      throw conflict(TradingReason.PRICE_RULE_IMMUTABLE.name());
    LocalDateTime at = now(clock),
        effective =
            OffsetDateTime.parse(ruleInputInput.effectiveAt())
                .atZoneSameInstant(ZONE)
                .toLocalDateTime();
    LocalDateTime expiry =
        ruleInputInput.expireAt() == null
            ? null
            : OffsetDateTime.parse(ruleInputInput.expireAt())
                .atZoneSameInstant(ZONE)
                .toLocalDateTime();
    if (ruleInputInput.distanceMaxMeter() != null
            && ruleInputInput.distanceMaxMeter() <= ruleInputInput.distanceMinMeter()
        || ruleInputInput.guideMinFen() > ruleInputInput.guideMaxFen()
        || expiry != null && !expiry.isAfter(effective)) throw ApiFailure.invalid();
    Set<String> fees = new HashSet<>();
    for (ExtraFee extraFeeFee : ruleInputInput.extraFees())
      if (!fees.add(extraFeeFee.type())) throw ApiFailure.invalid();
    PriceRuleDO priceRuleDO =
        new PriceRuleDO(
            priceRuleDOBefore == null ? Ids.next() : id,
            ruleInputInput.ruleVersion(),
            ruleInputInput.cityCode(),
            ruleInputInput.vehicleTierId(),
            ruleInputInput.distanceMinMeter(),
            ruleInputInput.distanceMaxMeter(),
            ruleInputInput.guideMinFen(),
            ruleInputInput.guideMaxFen(),
            ruleInputInput.ownVehicleSettleFen(),
            ruleInputInput.extraKmFen(),
            objectMapper.writeValueAsString(ruleInputInput.extraFees()),
            TransportRuleStatus.draft.name(),
            effective,
            expiry,
            priceRuleDOBefore == null ? at : priceRuleDOBefore.createdAt(),
            at,
            priceRuleDOBefore == null ? 0 : Math.addExact(priceRuleDOBefore.version(), 1),
            ruleInputInput.includedDistanceMeter());
    try {
      if (priceRuleDOBefore == null) transportMapper.insertRule(priceRuleDO);
      else if (transportMapper.updateRule(priceRuleDO, priceRuleDOBefore.version()) != 1)
        throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    } catch (org.springframework.dao.DuplicateKeyException duplicateKeyException) {
      throw ApiFailure.invalid();
    }
    return tradingOperations.record(
        tradingCommand,
        TradingResource.transport_pricing_rule.name(),
        priceRuleDO.id(),
        priceRuleDO.version());
  }

  /** 发布锁定车型，禁止并发发布相交有效规则。 */
  public TradingReceipt publish(TradingCommand tradingCommand, String id, int expected) {
    tradingOperations.authorize(tradingCommand.actor(), tradingCommand.operation());
    tradingOperations.lock(tradingCommand.actor());
    TradingReceipt tradingReceiptReplay = tradingOperations.replay(tradingCommand);
    if (tradingReceiptReplay != null) return tradingReceiptReplay;
    PriceRuleDO priceRuleDOHint = transportMapper.selectPriceRuleById(id, false);
    if (priceRuleDOHint == null) throw missing();
    transportMapper.selectVehicleById(priceRuleDOHint.vehicleTierId(), true);
    PriceRuleDO priceRuleDO = transportMapper.selectPriceRuleById(id, true);
    if (priceRuleDO.version() != expected) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if (!TransportRuleStatus.draft.name().equals(priceRuleDO.ruleStatus())
        || priceRuleDO.effectiveAt().isBefore(now(clock)))
      throw conflict(TradingReason.PRICE_RULE_IMMUTABLE.name());
    if (transportMapper.countOverlappingPriceRules(priceRuleDO) != 0)
      throw conflict(TradingReason.PRICE_RULE_OVERLAP.name());
    PriceRuleDO priceRuleDOChanged =
        new PriceRuleDO(
            priceRuleDO.id(),
            priceRuleDO.ruleVersion(),
            priceRuleDO.cityCode(),
            priceRuleDO.vehicleTierId(),
            priceRuleDO.distanceMinMeter(),
            priceRuleDO.distanceMaxMeter(),
            priceRuleDO.guideMinAmountFen(),
            priceRuleDO.guideMaxAmountFen(),
            priceRuleDO.ownVehicleSettleAmountFen(),
            priceRuleDO.extraKmAmountFen(),
            priceRuleDO.allowedExtraFeeJson(),
            D2Codes.CODE_ACTIVE,
            priceRuleDO.effectiveAt(),
            priceRuleDO.expireAt(),
            priceRuleDO.createdAt(),
            now(clock),
            Math.addExact(priceRuleDO.version(), 1),
            priceRuleDO.includedDistanceMeter());
    if (transportMapper.updateRule(priceRuleDOChanged, expected) != 1)
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    return tradingOperations.record(
        tradingCommand,
        TradingResource.transport_pricing_rule.name(),
        id,
        priceRuleDOChanged.version());
  }

  /** 结束时间只能向未来缩短，不覆盖已冻结历史定价。 */
  public TradingReceipt end(TradingCommand tradingCommand, String id, EndRule endRuleInput) {
    tradingOperations.authorize(tradingCommand.actor(), tradingCommand.operation());
    tradingOperations.lock(tradingCommand.actor());
    TradingReceipt tradingReceiptReplay = tradingOperations.replay(tradingCommand);
    if (tradingReceiptReplay != null) return tradingReceiptReplay;
    PriceRuleDO priceRuleDOHint = transportMapper.selectPriceRuleById(id, false);
    if (priceRuleDOHint == null) throw missing();
    transportMapper.selectVehicleById(priceRuleDOHint.vehicleTierId(), true);
    PriceRuleDO priceRuleDO = transportMapper.selectPriceRuleById(id, true);
    if (priceRuleDO.version() != endRuleInput.expectedVersion())
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    LocalDateTime expiry =
        OffsetDateTime.parse(endRuleInput.expireAt()).atZoneSameInstant(ZONE).toLocalDateTime();
    if (!D2Codes.CODE_ACTIVE.equals(priceRuleDO.ruleStatus())
        || !expiry.isAfter(now(clock))
        || !expiry.isAfter(priceRuleDO.effectiveAt())
        || priceRuleDO.expireAt() != null && !expiry.isBefore(priceRuleDO.expireAt()))
      throw conflict(TradingReason.PRICE_RULE_IMMUTABLE.name());
    PriceRuleDO priceRuleDOChanged =
        new PriceRuleDO(
            priceRuleDO.id(),
            priceRuleDO.ruleVersion(),
            priceRuleDO.cityCode(),
            priceRuleDO.vehicleTierId(),
            priceRuleDO.distanceMinMeter(),
            priceRuleDO.distanceMaxMeter(),
            priceRuleDO.guideMinAmountFen(),
            priceRuleDO.guideMaxAmountFen(),
            priceRuleDO.ownVehicleSettleAmountFen(),
            priceRuleDO.extraKmAmountFen(),
            priceRuleDO.allowedExtraFeeJson(),
            priceRuleDO.ruleStatus(),
            priceRuleDO.effectiveAt(),
            expiry,
            priceRuleDO.createdAt(),
            now(clock),
            Math.addExact(priceRuleDO.version(), 1),
            priceRuleDO.includedDistanceMeter());
    if (transportMapper.updateRule(priceRuleDOChanged, endRuleInput.expectedVersion()) != 1)
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    return tradingOperations.record(
        tradingCommand,
        TradingResource.transport_pricing_rule.name(),
        id,
        priceRuleDOChanged.version());
  }

  /** 供配送页展示当前有效价格，具体报价仍按距离逐段匹配。 */
  @Transactional(readOnly = true)
  public PriceSheet priceSheet(String actor, String cityCode) {
    factoryAccess.context(actor, false);
    List<Vehicle> vehicleListVehicles = new ArrayList<>();
    for (VehicleDO vehicleDO : transportMapper.selectEnabledVehicles())
      vehicleListVehicles.add(transportConverter.vehicle(vehicleDO));
    List<PriceRule> priceRuleListRules = new ArrayList<>();
    for (PriceRuleDO priceRuleDO : transportMapper.selectActivePriceRules(now(clock), cityCode))
      priceRuleListRules.add(transportConverter.rule(priceRuleDO, now(clock)));
    return new PriceSheet(vehicleListVehicles, priceRuleListRules, time(now(clock)));
  }
}
