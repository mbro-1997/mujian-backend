package com.mujian.backend.delivery.service;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.policy.TransportCalculation;
import com.mujian.backend.infrastructure.map.RouteGateway;
import com.mujian.backend.order.domain.CheckoutQuoteData.Leg;
import java.util.*;
import org.springframework.stereotype.Service;

/** 运输报价独立职责：非预收不调地图，预收逐方向取得唯一规则。 */
@Service
public class DeliveryQuotation {
  private final RouteGateway routeGateway;

  /** 外部地图由基础设施适配，隔离测试可注入显式替身。 */
  public DeliveryQuotation(RouteGateway routeGateway) {
    this.routeGateway = routeGateway;
  }

  /** 方向明确，零费用仅来自约定的非预收服务。 */
  public Leg quote(
      String serviceMode,
      String factoryAddress,
      String receiverAddress,
      List<TransportCalculation.Item> itemListItems,
      List<VehicleDO> vehicleDOListVehicles,
      List<PriceRuleDO> priceRuleDOListRules) {
    boolean prepaid =
        TransportServiceMode.factory_delivery.name().equals(serviceMode)
            || TransportServiceMode.factory_pickup.name().equals(serviceMode);
    if (!prepaid)
      return new Leg(
          serviceMode,
          TransportServiceMode.factory_assisted_call.name().equals(serviceMode)
              ? TransportChargeMode.cod.name()
              : TradingState.none.name(),
          null,
          null,
          null,
          null,
          null,
          null,
          null,
          0,
          0,
          0);
    List<TransportCalculation.Vehicle> vehicleListOptions = new ArrayList<>();
    Map<String, VehicleDO> vehicleDOMap = new HashMap<>();
    for (VehicleDO vehicleDO : vehicleDOListVehicles) {
      vehicleListOptions.add(
          new TransportCalculation.Vehicle(
              vehicleDO.id(),
              vehicleDO.cargoLengthMm(),
              vehicleDO.cargoWidthMm(),
              vehicleDO.cargoHeightMm(),
              vehicleDO.effectiveVolumeCm3(),
              vehicleDO.safePayloadGram(),
              vehicleDO.sortNo()));
      vehicleDOMap.put(vehicleDO.id(), vehicleDO);
    }
    TransportCalculation.Vehicle vehicle =
        TransportCalculation.select(itemListItems, vehicleListOptions);
    if (vehicle == null) throw conflict(TradingReason.VEHICLE_NOT_MATCHED.name());
    RouteGateway.Point pointOrigin = routeGateway.geocode(factoryAddress),
        pointDestination = routeGateway.geocode(receiverAddress);
    if (!pointOrigin.cityCode().equals(pointDestination.cityCode()))
      throw conflict(TradingReason.PRICE_RULE_NOT_FOUND.name());
    RouteGateway.Route route =
        TransportServiceMode.factory_pickup.name().equals(serviceMode)
            ? routeGateway.driving(pointDestination, pointOrigin)
            : routeGateway.driving(pointOrigin, pointDestination);
    PriceRuleDO priceRuleDO = null;
    for (PriceRuleDO priceRuleDOCandidate : priceRuleDOListRules) {
      if (priceRuleDOCandidate.vehicleTierId().equals(vehicle.id())
          && priceRuleDOCandidate.cityCode().equals(pointOrigin.cityCode())
          && route.distanceMeter() >= priceRuleDOCandidate.distanceMinMeter()
          && (priceRuleDOCandidate.distanceMaxMeter() == null
              || route.distanceMeter() < priceRuleDOCandidate.distanceMaxMeter())) {
        if (priceRuleDO != null)
          throw new ApiFailure(
              500, D2Codes.CODE_DATA_INTEGRITY_ERROR, TradingReason.TRADE_DATA_INCONSISTENT.name());
        priceRuleDO = priceRuleDOCandidate;
      }
    }
    if (priceRuleDO == null) throw conflict(TradingReason.PRICE_RULE_NOT_FOUND.name());
    TransportCalculation.Price price =
        TransportCalculation.price(
            route.distanceMeter(),
            priceRuleDO.includedDistanceMeter(),
            priceRuleDO.guideMinAmountFen(),
            priceRuleDO.guideMaxAmountFen(),
            priceRuleDO.ownVehicleSettleAmountFen(),
            priceRuleDO.extraKmAmountFen());
    TransportCalculation.Load load = TransportCalculation.load(itemListItems);
    return new Leg(
        serviceMode,
        TransportChargeMode.freight_pool.name(),
        route,
        vehicleDOMap.get(vehicle.id()),
        priceRuleDO,
        "LEAST_TIME",
        load.volumeCm3().toString(),
        load.weightGram().toString(),
        price.extraKm(),
        price.guideMinFen(),
        price.guideMaxFen(),
        price.guideMaxFen());
  }
}
