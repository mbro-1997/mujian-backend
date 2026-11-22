package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.address.domain.ShippingAddressDO;
import com.mujian.backend.address.mapper.AddressMapper;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.delivery.mapper.TransportMapper;
import com.mujian.backend.order.dto.CheckoutDtos.*;
import com.mujian.backend.order.mapper.CheckoutMapper;
import com.mujian.backend.order.projection.*;
import com.mujian.backend.planning.domain.*;
import com.mujian.backend.planning.mapper.ChecklistMapper;
import com.mujian.backend.planning.service.PlanningSnapshots;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.mapper.PropSkuMapper;
import com.mujian.backend.prop.policy.PropAvailability;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 报价公共准入与批量来源读取；无写锁，无外部调用。 */
@Service
public class CheckoutSourceReader {
  private final TradingAccess tradingAccess;
  private final ChecklistMapper checklistMapper;
  private final AddressMapper addressMapper;
  private final PropSkuMapper propSkuMapper;
  private final PlanningSnapshots planningSnapshots;
  private final PropAvailability propAvailability;
  private final CheckoutMapper checkoutMapper;
  private final TransportMapper transportMapper;
  private final Clock clock;

  /** 复用清单、地址、当前公开资料及库存峰值。 */
  public CheckoutSourceReader(
      TradingAccess tradingAccess,
      ChecklistMapper checklistMapper,
      AddressMapper addressMapper,
      PropSkuMapper propSkuMapper,
      PlanningSnapshots planningSnapshots,
      PropAvailability propAvailability,
      CheckoutMapper checkoutMapper,
      TransportMapper transportMapper,
      Clock clock) {
    this.tradingAccess = tradingAccess;
    this.checklistMapper = checklistMapper;
    this.addressMapper = addressMapper;
    this.propSkuMapper = propSkuMapper;
    this.planningSnapshots = planningSnapshots;
    this.propAvailability = propAvailability;
    this.checkoutMapper = checkoutMapper;
    this.transportMapper = transportMapper;
    this.clock = clock;
  }

  /** 三个报价/提交入口共享来源规则；提交仍须在锁内再次校验版本及库存。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CheckoutSource read(String actor, QuoteInput quoteInput) {
    tradingAccess.newTrade(actor);
    LocalDateTime at = now(clock);
    RentalChecklistDO rentalChecklistDO =
        checklistMapper.selectOwnedChecklistById(actor, quoteInput.checklistId(), false);
    if (rentalChecklistDO == null) throw missing();
    if (rentalChecklistDO.version() != quoteInput.expectedVersion())
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if (!"selecting".equals(rentalChecklistDO.checklistStatus()))
      throw conflict(TradingReason.CHECKLIST_READ_ONLY.name());
    if (!actor.equals(rentalChecklistDO.currentOwnerGuard()))
      throw conflict(TradingReason.CHECKLIST_NOT_CURRENT.name());
    if (at.isBefore(rentalChecklistDO.rentalStartDate().minusDays(14).atStartOfDay()))
      throw conflict(TradingReason.RENTAL_WINDOW_NOT_OPEN.name());
    if (!at.isBefore(rentalChecklistDO.rentalStartDate().minusDays(2).atTime(11, 0)))
      throw conflict(TradingReason.RENTAL_WINDOW_CLOSED.name());
    ShippingAddressDO shippingAddressDO =
        addressMapper.selectOwnedAddressById(actor, quoteInput.addressId(), false);
    if (shippingAddressDO == null) throw missing();
    if (shippingAddressDO.version() != quoteInput.addressVersion()
        || !D2Codes.CODE_ACTIVE.equals(shippingAddressDO.addressStatus()))
      throw conflict(TradingReason.ADDRESS_CHANGED.name());
    List<RentalChecklistItemDO> rentalChecklistItemDOList =
        checklistMapper.selectChecklistItemsByChecklistIds(List.of(rentalChecklistDO.id()));
    if (rentalChecklistItemDOList.isEmpty()) throw conflict(TradingReason.CHECKLIST_EMPTY.name());
    Set<String> skuIds = new TreeSet<>(), factoryIds = new TreeSet<>();
    for (RentalChecklistItemDO rentalChecklistItemDO : rentalChecklistItemDOList) {
      skuIds.add(rentalChecklistItemDO.skuId());
      factoryIds.add(rentalChecklistItemDO.factoryId());
    }
    if (factoryIds.size() > 50) throw conflict(TradingReason.FACTORY_COUNT_EXCEEDED.name());
    Set<String> deliveryIds = new TreeSet<>();
    for (DeliveryChoice deliveryChoice : quoteInput.deliveries())
      if (!deliveryIds.add(deliveryChoice.factoryId())) throw ApiFailure.invalid();
    if (!factoryIds.equals(deliveryIds)) throw ApiFailure.invalid();
    Map<String, PlanningSnapshots.Current> currentMap = planningSnapshots.current(skuIds);
    Map<String, Integer> availableMap =
        propAvailability.quantities(
            new ArrayList<>(skuIds),
            new PropAvailability.Dates(
                rentalChecklistDO.rentalStartDate(), rentalChecklistDO.rentalEndDate()));
    Map<String, PropSkuDO> propSkuDOMap = new HashMap<>();
    for (PropSkuDO propSkuDO : propSkuMapper.selectPropSkuListByIds(new ArrayList<>(skuIds)))
      propSkuDOMap.put(propSkuDO.getId(), propSkuDO);
    Map<String, SnapshotV1> snapshotV1Map = new HashMap<>();
    for (RentalChecklistItemDO rentalChecklistItemDO : rentalChecklistItemDOList) {
      PlanningSnapshots.Current current = currentMap.get(rentalChecklistItemDO.skuId());
      PropSkuDO propSkuDO = propSkuDOMap.get(rentalChecklistItemDO.skuId());
      if (current == null
          || !current.publicVisible()
          || !rentalChecklistItemDO.factoryId().equals(current.snapshot().factoryId()))
        throw conflict(TradingReason.SKU_NOT_PUBLIC.name());
      if (availableMap.getOrDefault(rentalChecklistItemDO.skuId(), 0)
          < rentalChecklistItemDO.quantity()) throw conflict(TradingReason.STOCK_NOT_ENOUGH.name());
      if (propSkuDO.getPackagingFactorBps() == null)
        throw conflict(TradingReason.PACKAGING_DATA_MISSING.name());
      snapshotV1Map.put(rentalChecklistItemDO.skuId(), current.snapshot());
    }
    List<CheckoutFactoryProjection> checkoutFactoryProjectionList =
        checkoutMapper.selectFactoriesByIds(new ArrayList<>(factoryIds));
    if (checkoutFactoryProjectionList.size() != factoryIds.size())
      throw conflict(TradingReason.SKU_NOT_PUBLIC.name());
    for (CheckoutFactoryProjection checkoutFactoryProjection : checkoutFactoryProjectionList) {
      if (!D2Codes.CODE_ACTIVE.equals(checkoutFactoryProjection.ownerStatus())
          || !D2Codes.CODE_ACTIVE.equals(checkoutFactoryProjection.factoryStatus()))
        throw conflict(TradingReason.SKU_NOT_PUBLIC.name());
      if (!D2Codes.CODE_ACTIVE.equals(checkoutFactoryProjection.accountStatus())
          || !D2Codes.CODE_ACTIVE.equals(checkoutFactoryProjection.profitSharingStatus())
          || checkoutFactoryProjection.subMchid() == null)
        throw conflict(TradingReason.MERCHANT_NOT_READY.name());
    }
    return new CheckoutSource(
        rentalChecklistDO,
        shippingAddressDO,
        rentalChecklistItemDOList,
        Map.copyOf(propSkuDOMap),
        Map.copyOf(snapshotV1Map),
        checkoutFactoryProjectionList,
        transportMapper.selectEnabledVehicles(),
        transportMapper.selectActivePriceRules(at, null));
  }
}
