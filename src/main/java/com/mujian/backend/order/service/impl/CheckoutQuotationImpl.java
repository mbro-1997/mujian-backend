package com.mujian.backend.order.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.coupon.service.CheckoutCoupons;
import com.mujian.backend.coupon.vo.CouponViews;
import com.mujian.backend.delivery.domain.PriceRuleDO;
import com.mujian.backend.delivery.domain.VehicleDO;
import com.mujian.backend.delivery.policy.TransportCalculation;
import com.mujian.backend.delivery.service.DeliveryQuotation;
import com.mujian.backend.order.domain.CheckoutQuoteData;
import com.mujian.backend.order.domain.CheckoutQuoteData.*;
import com.mujian.backend.order.dto.CheckoutDtos.*;
import com.mujian.backend.order.projection.*;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.CheckoutViews.*;
import com.mujian.backend.planning.domain.*;
import com.mujian.backend.planning.service.PlanningImages;
import com.mujian.backend.prop.domain.PropSkuDO;
import com.mujian.backend.prop.vo.PublicImageViewVO;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

/** 报价与选券公共计算，外部路线发生在数据库读取事务结束之后。 */
@Service
public class CheckoutQuotationImpl implements CheckoutQuotation {

  private final CheckoutSourceReader checkoutSourceReader;
  private final DeliveryQuotation deliveryQuotation;
  private final CheckoutCoupons checkoutCoupons;
  private final TradingTokens tradingTokens;
  private final PlanningImages planningImages;
  private final Clock clock;

  /** 批量来源、路线、整数券分摊分别复用，不复制权限或库存算法。 */
  public CheckoutQuotationImpl(
      CheckoutSourceReader checkoutSourceReader,
      DeliveryQuotation deliveryQuotation,
      CheckoutCoupons checkoutCoupons,
      TradingTokens tradingTokens,
      PlanningImages planningImages,
      Clock clock) {
    this.checkoutSourceReader = checkoutSourceReader;
    this.deliveryQuotation = deliveryQuotation;
    this.checkoutCoupons = checkoutCoupons;
    this.tradingTokens = tradingTokens;
    this.planningImages = planningImages;
    this.clock = clock;
  }

  /** 两个只读入口共用完整计算，提交传入已验签凭据以保留预分配明细ID和有效路线。 */
  public Calculation calculate(
      String actor, QuoteInput quoteInput, CheckoutQuoteData checkoutQuoteDataConfirmed) {
    try {
      CheckoutSource checkoutSource = checkoutSourceReader.read(actor, quoteInput);
      Map<String, Factory> factoryMapConfirmedFactories = new HashMap<>();
      Map<String, String> snapshotIds = new HashMap<>();
      if (checkoutQuoteDataConfirmed != null) {
        if (!checkoutQuoteDataConfirmed.address().equals(checkoutSource.address()))
          throw conflict(TradingReason.QUOTE_CHANGED.name());
        for (Factory factory : checkoutQuoteDataConfirmed.factories()) {
          factoryMapConfirmedFactories.put(factory.factoryId(), factory);
          for (Item item : factory.items())
            snapshotIds.put(item.sourceItemId(), item.snapshotItemId());
        }
      }
      Map<String, DeliveryChoice> deliveryChoiceMap = new HashMap<>();
      for (DeliveryChoice deliveryChoice : quoteInput.deliveries())
        deliveryChoiceMap.put(deliveryChoice.factoryId(), deliveryChoice);
      Map<String, List<RentalChecklistItemDO>> rentalChecklistItemDOListMapItemGroups =
          new TreeMap<>();
      for (RentalChecklistItemDO rentalChecklistItemDO : checkoutSource.items()) {
        List<RentalChecklistItemDO> rentalChecklistItemDOListItems =
            rentalChecklistItemDOListMapItemGroups.get(rentalChecklistItemDO.factoryId());
        if (rentalChecklistItemDOListItems == null) {
          rentalChecklistItemDOListItems = new ArrayList<>();
          rentalChecklistItemDOListMapItemGroups.put(
              rentalChecklistItemDO.factoryId(), rentalChecklistItemDOListItems);
        }
        rentalChecklistItemDOListItems.add(rentalChecklistItemDO);
      }
      List<Factory> factoryListFactories = new ArrayList<>();
      for (CheckoutFactoryProjection checkoutFactoryProjection : checkoutSource.factories()) {
        List<Item> itemListItems = new ArrayList<>();
        List<TransportCalculation.Item> itemListLoad = new ArrayList<>();
        int rent = 0, deposit = 0;
        for (RentalChecklistItemDO rentalChecklistItemDO :
            rentalChecklistItemDOListMapItemGroups.get(checkoutFactoryProjection.id())) {
          PropSkuDO propSkuDO = checkoutSource.skus().get(rentalChecklistItemDO.skuId());
          SnapshotV1 snapshotV1 = checkoutSource.snapshots().get(rentalChecklistItemDO.skuId());
          String snapshotId = snapshotIds.get(rentalChecklistItemDO.id());
          if (snapshotId == null) {
            if (checkoutQuoteDataConfirmed != null)
              throw conflict(TradingReason.QUOTE_CHANGED.name());
            snapshotId = Ids.next();
          }
          int rentFen =
              Math.multiplyExact(
                  Math.multiplyExact(
                      propSkuDO.getDailyRentPriceFen(), rentalChecklistItemDO.quantity()),
                  checkoutSource.checklist().rentalDays());
          int depositFen =
              Math.multiplyExact(propSkuDO.getDepositAmountFen(), rentalChecklistItemDO.quantity());
          rent = Math.addExact(rent, rentFen);
          deposit = Math.addExact(deposit, depositFen);
          itemListItems.add(
              new Item(
                  snapshotId,
                  rentalChecklistItemDO.id(),
                  propSkuDO.getVersion(),
                  snapshotV1,
                  propSkuDO.getPackagingFactorBps(),
                  rentalChecklistItemDO.quantity(),
                  rentFen,
                  0,
                  rentFen,
                  depositFen,
                  propSkuDO.getTransportSuggestion()));
          itemListLoad.add(
              new TransportCalculation.Item(
                  propSkuDO.getLengthCm(),
                  propSkuDO.getWidthCm(),
                  propSkuDO.getHeightCm(),
                  propSkuDO.getWeightKg(),
                  propSkuDO.getPackagingFactorBps(),
                  rentalChecklistItemDO.quantity()));
        }
        DeliveryChoice deliveryChoice = deliveryChoiceMap.get(checkoutFactoryProjection.id());
        Factory factoryOriginal = factoryMapConfirmedFactories.get(checkoutFactoryProjection.id());
        Leg legOutbound, legReturnLeg;
        if (checkoutQuoteDataConfirmed != null) {
          if (factoryOriginal == null
              || factoryOriginal.profileVersion() != checkoutFactoryProjection.profileVersion())
            throw conflict(TradingReason.QUOTE_CHANGED.name());
          legOutbound = factoryOriginal.outbound();
          legReturnLeg = factoryOriginal.returnLeg();
          for (Leg leg : List.of(legOutbound, legReturnLeg))
            if (leg.rule() != null) {
              boolean ruleFound = false, vehicleFound = false;
              for (PriceRuleDO priceRuleDO : checkoutSource.rules())
                if (priceRuleDO.id().equals(leg.rule().id())
                    && priceRuleDO.version() == leg.rule().version()) ruleFound = true;
              for (VehicleDO vehicleDO : checkoutSource.vehicles())
                if (vehicleDO.id().equals(leg.vehicle().id())
                    && vehicleDO.version() == leg.vehicle().version()) vehicleFound = true;
              if (!ruleFound || !vehicleFound) throw conflict(TradingReason.QUOTE_CHANGED.name());
            }
        } else {
          String receiver =
              checkoutSource.address().regionText() + checkoutSource.address().detailAddress();
          legOutbound =
              deliveryQuotation.quote(
                  deliveryChoice.outboundService(),
                  checkoutFactoryProjection.factoryAddress(),
                  receiver,
                  itemListLoad,
                  checkoutSource.vehicles(),
                  checkoutSource.rules());
          legReturnLeg =
              deliveryQuotation.quote(
                  deliveryChoice.returnService(),
                  checkoutFactoryProjection.factoryAddress(),
                  receiver,
                  itemListLoad,
                  checkoutSource.vehicles(),
                  checkoutSource.rules());
        }
        int freight = Math.addExact(legOutbound.prepaidFen(), legReturnLeg.prepaidFen());
        Amounts amounts =
            new Amounts(
                rent,
                0,
                rent,
                deposit,
                freight,
                Math.addExact(Math.addExact(rent, deposit), freight));
        factoryListFactories.add(
            new Factory(
                checkoutFactoryProjection.id(),
                checkoutFactoryProjection.ownerUserId(),
                checkoutFactoryProjection.factoryName(),
                checkoutFactoryProjection.factoryAddress(),
                checkoutFactoryProjection.profileVersion(),
                checkoutFactoryProjection.merchantId(),
                checkoutFactoryProjection.subMchid(),
                checkoutFactoryProjection.merchantVersion(),
                List.copyOf(itemListItems),
                legOutbound,
                legReturnLeg,
                deliveryChoice.buyerRemark(),
                amounts));
      }
      CheckoutCoupons.Evaluation evaluation =
          checkoutCoupons.evaluate(actor, quoteInput.couponId(), factoryListFactories);
      List<Factory> factoryListAllocated = new ArrayList<>();
      int rentSum = 0, discountSum = 0, depositSum = 0, freightSum = 0;
      for (Factory factory : factoryListFactories) {
        List<Item> itemListItems = new ArrayList<>();
        int discount = 0;
        for (Item item : factory.items()) {
          int saving = evaluation.itemDiscounts().getOrDefault(item.snapshotItemId(), 0);
          discount = Math.addExact(discount, saving);
          itemListItems.add(
              new Item(
                  item.snapshotItemId(),
                  item.sourceItemId(),
                  item.skuVersion(),
                  item.snapshot(),
                  item.packagingFactorBps(),
                  item.quantity(),
                  item.rentOriginalFen(),
                  saving,
                  item.rentOriginalFen() - saving,
                  item.depositFen(),
                  item.transportRequirement()));
        }
        Amounts amounts = factory.amounts();
        int cash = amounts.totalFen() - discount;
        if (cash < 1) throw conflict(TradingReason.AMOUNT_OUT_OF_RANGE.name());
        factoryListAllocated.add(
            new Factory(
                factory.factoryId(),
                factory.ownerUserId(),
                factory.factoryName(),
                factory.address(),
                factory.profileVersion(),
                factory.merchantId(),
                factory.subMchid(),
                factory.merchantVersion(),
                List.copyOf(itemListItems),
                factory.outbound(),
                factory.returnLeg(),
                factory.buyerRemark(),
                new Amounts(
                    amounts.rentOriginalFen(),
                    discount,
                    amounts.rentOriginalFen() - discount,
                    amounts.depositFen(),
                    amounts.freightFen(),
                    cash)));
        rentSum = Math.addExact(rentSum, amounts.rentOriginalFen());
        discountSum = Math.addExact(discountSum, discount);
        depositSum = Math.addExact(depositSum, amounts.depositFen());
        freightSum = Math.addExact(freightSum, amounts.freightFen());
      }
      LocalDateTime issued =
          checkoutQuoteDataConfirmed == null ? now(clock) : checkoutQuoteDataConfirmed.issuedAt();
      CheckoutQuoteData checkoutQuoteData =
          new CheckoutQuoteData(
              1,
              actor,
              issued,
              issued.plusMinutes(5),
              quoteInput,
              checkoutSource.checklist().rentalStartDate(),
              checkoutSource.checklist().rentalEndDate(),
              checkoutSource.checklist().rentalDays(),
              checkoutSource.address(),
              evaluation.couponVersion(),
              List.copyOf(factoryListAllocated),
              new Amounts(
                  rentSum,
                  discountSum,
                  rentSum - discountSum,
                  depositSum,
                  freightSum,
                  Math.addExact(Math.addExact(rentSum - discountSum, depositSum), freightSum)));
      if (checkoutQuoteDataConfirmed != null
          && !checkoutQuoteDataConfirmed.equals(checkoutQuoteData))
        throw conflict(TradingReason.QUOTE_CHANGED.name());
      return new Calculation(checkoutQuoteData, evaluation.choices());
    } catch (ArithmeticException exception) {
      throw conflict(TradingReason.AMOUNT_OUT_OF_RANGE.name());
    }
  }

  /** 签名只引用稳定资料，返回图片才进行有权限的临时签名。 */
  public Quote quote(String actor, QuoteInput quoteInput) {
    CheckoutQuoteData checkoutQuoteData = calculate(actor, quoteInput, null).data();
    List<SnapshotV1> snapshotV1List = new ArrayList<>();
    for (Factory factory : checkoutQuoteData.factories())
      for (Item item : factory.items()) snapshotV1List.add(item.snapshot());
    Map<String, PublicImageViewVO> publicImageViewVOMapImageMap =
        planningImages.sign(snapshotV1List);
    List<FactoryQuote> factoryQuoteListGroups = new ArrayList<>();
    for (Factory factory : checkoutQuoteData.factories()) {
      List<TradeItem> tradeItemListItems = new ArrayList<>();
      for (Item item : factory.items())
        tradeItemListItems.add(
            new TradeItem(
                item.snapshotItemId(),
                item.sourceItemId(),
                item.snapshot().skuId(),
                item.snapshot().name(),
                planningImages.cover(item.snapshot(), publicImageViewVOMapImageMap),
                item.snapshot().assetType(),
                item.quantity(),
                item.snapshot().prices().dailyRentPriceFen(),
                item.rentOriginalFen(),
                item.couponDiscountFen(),
                item.rentCashFen(),
                item.depositFen(),
                item.transportRequirement()));
      List<RoutePrice> routePriceListLegs = new ArrayList<>();
      for (Leg leg : List.of(factory.outbound(), factory.returnLeg()))
        routePriceListLegs.add(
            new RoutePrice(
                leg.serviceMode(),
                leg.chargeMode(),
                leg.vehicle() == null ? null : leg.vehicle().id(),
                leg.vehicle() == null ? null : leg.vehicle().tierName(),
                leg.route() == null ? null : leg.route().distanceMeter(),
                leg.rule() == null ? null : leg.rule().id(),
                leg.rule() == null ? null : leg.rule().ruleVersion(),
                leg.guideMinFen(),
                leg.guideMaxFen(),
                leg.prepaidFen(),
                leg.route() == null
                    ? null
                    : time(LocalDateTime.ofInstant(leg.route().calculatedAt(), ZONE))));
      factoryQuoteListGroups.add(
          new FactoryQuote(
              factory.factoryId(),
              factory.factoryName(),
              List.copyOf(tradeItemListItems),
              factory.amounts(),
              routePriceListLegs.get(0),
              routePriceListLegs.get(1),
              factory.buyerRemark()));
    }
    return new Quote(
        tradingTokens.quote(checkoutQuoteData),
        quoteInput.checklistId(),
        quoteInput.expectedVersion(),
        checkoutQuoteData.startDate().toString(),
        checkoutQuoteData.endDate().toString(),
        checkoutQuoteData.rentalDays(),
        quoteInput.couponId(),
        List.copyOf(factoryQuoteListGroups),
        checkoutQuoteData.amounts(),
        time(checkoutQuoteData.issuedAt()),
        time(checkoutQuoteData.expiresAt()),
        time(checkoutQuoteData.startDate().minusDays(2).atTime(11, 0)),
        "预收运费按指导上限收取；实际运输费用按履约核算。工厂协助叫车为到付，自提及自行归还不预收运费。");
  }

  /** 候选券逐券给出真实节省，不改变用户已明确的选择。 */
  public CouponViews.Choices coupons(String actor, QuoteInput quoteInput) {
    return new CouponViews.Choices(calculate(actor, quoteInput, null).coupons(), time(now(clock)));
  }
}
