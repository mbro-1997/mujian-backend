package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.order.domain.*;
import com.mujian.backend.order.domain.BatchItemSnapshotDO;
import com.mujian.backend.order.domain.BatchTransportSnapshotDO;
import com.mujian.backend.order.domain.TradingSnapshots;
import com.mujian.backend.order.mapper.CheckoutBatchMapper;
import com.mujian.backend.order.vo.CheckoutViews;
import com.mujian.backend.prop.service.PropMaterialService;
import com.mujian.backend.prop.vo.PublicImageViewVO;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 订单/后台支付追溯复用冻结分厂快照；调用者先完成归属或菜单鉴权。 */
@Service
public class TradeSnapshotReader {
  private final CheckoutBatchMapper checkoutBatchMapper;
  private final PropMaterialService propMaterialService;
  private final ObjectMapper objectMapper;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public TradeSnapshotReader(
      CheckoutBatchMapper checkoutBatchMapper,
      PropMaterialService propMaterialService,
      ObjectMapper objectMapper) {
    this.checkoutBatchMapper = checkoutBatchMapper;
    this.propMaterialService = propMaterialService;
    this.objectMapper = objectMapper;
  }

  /** 管理追溯读取整批；工厂详情只读取自己的冻结分组。 */
  public List<CheckoutViews.FactoryQuote> groups(String batchId) {
    return groups(batchId, null);
  }

  /** 在数据库过滤工厂范围，避免批量续签无关工厂的图片。 */
  public List<CheckoutViews.FactoryQuote> groups(String batchId, String factoryId) {
    Map<String, List<CheckoutViews.TradeItem>> tradeItemListMapGrouped = new LinkedHashMap<>();
    Map<String, String> names = new HashMap<>();
    Map<String, PublicImageViewVO> publicImageViewVOMapImages = new HashMap<>();
    for (BatchItemSnapshotDO batchItemSnapshotDO :
        checkoutBatchMapper.selectItemSnapshotsByBatch(batchId, factoryId)) {
      TradingSnapshots.Item itemSnapshot =
          objectMapper.readValue(
              batchItemSnapshotDO.itemSnapshotJson(), TradingSnapshots.Item.class);
      PublicImageViewVO publicImageViewVOImage = null;
      if (itemSnapshot.imageKey() != null) {
        publicImageViewVOImage = publicImageViewVOMapImages.get(itemSnapshot.imageKey());
        if (publicImageViewVOImage == null) {
          publicImageViewVOImage = propMaterialService.publicImage(itemSnapshot.imageKey());
          publicImageViewVOMapImages.put(itemSnapshot.imageKey(), publicImageViewVOImage);
        }
      }
      List<CheckoutViews.TradeItem> tradeItemListFactoryItems =
          tradeItemListMapGrouped.get(batchItemSnapshotDO.factoryId());
      if (tradeItemListFactoryItems == null) {
        tradeItemListFactoryItems = new ArrayList<>();
        tradeItemListMapGrouped.put(batchItemSnapshotDO.factoryId(), tradeItemListFactoryItems);
      }
      tradeItemListFactoryItems.add(
          new CheckoutViews.TradeItem(
              batchItemSnapshotDO.id(),
              batchItemSnapshotDO.sourceChecklistItemId(),
              batchItemSnapshotDO.skuId(),
              batchItemSnapshotDO.propNameSnapshot(),
              publicImageViewVOImage,
              batchItemSnapshotDO.assetTypeSnapshot(),
              batchItemSnapshotDO.quantity(),
              batchItemSnapshotDO.dailyRentPriceFen(),
              batchItemSnapshotDO.rentAmountFen(),
              batchItemSnapshotDO.couponDiscountAmountFen(),
              batchItemSnapshotDO.rentCashPayableAmountFen(),
              batchItemSnapshotDO.depositPayableAmountFen(),
              batchItemSnapshotDO.transportRequirementSnapshot()));
      names.putIfAbsent(batchItemSnapshotDO.factoryId(), batchItemSnapshotDO.factoryNameSnapshot());
    }
    List<CheckoutViews.FactoryQuote> factoryQuoteListResult = new ArrayList<>();
    for (BatchTransportSnapshotDO batchTransportSnapshotDOTransport :
        checkoutBatchMapper.selectTransportSnapshotsByBatch(batchId, factoryId)) {
      List<CheckoutViews.TradeItem> tradeItemListItems =
          tradeItemListMapGrouped.get(batchTransportSnapshotDOTransport.factoryId());
      if (tradeItemListItems == null) throw new IllegalStateException("trade items missing");
      int rent = 0, discount = 0, cash = 0, deposit = 0;
      for (CheckoutViews.TradeItem tradeItemItem : tradeItemListItems) {
        rent = Math.addExact(rent, tradeItemItem.rentOriginalFen());
        discount = Math.addExact(discount, tradeItemItem.couponDiscountFen());
        cash = Math.addExact(cash, tradeItemItem.rentCashFen());
        deposit = Math.addExact(deposit, tradeItemItem.depositFen());
      }
      int freight = batchTransportSnapshotDOTransport.freightPrepaidAmountFen();
      CheckoutViews.Amounts amounts =
          new CheckoutViews.Amounts(
              rent,
              discount,
              cash,
              deposit,
              freight,
              Math.addExact(Math.addExact(cash, deposit), freight));
      TradingSnapshots.Routes routes =
          objectMapper.readValue(
              batchTransportSnapshotDOTransport.routePricingSnapshotJson(),
              TradingSnapshots.Routes.class);
      factoryQuoteListResult.add(
          new CheckoutViews.FactoryQuote(
              batchTransportSnapshotDOTransport.factoryId(),
              names.get(batchTransportSnapshotDOTransport.factoryId()),
              List.copyOf(tradeItemListItems),
              amounts,
              route(routes.outbound()),
              route(routes.returnLeg()),
              batchTransportSnapshotDOTransport.buyerRemarkSnapshot()));
    }
    return List.copyOf(factoryQuoteListResult);
  }

  /** 只映射冻结路线及金额，历史读取不重新调用地图。 */
  public static CheckoutViews.RoutePrice route(CheckoutQuoteData.Leg leg) {
    return new CheckoutViews.RoutePrice(
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
            : time(LocalDateTime.ofInstant(leg.route().calculatedAt(), ZONE)));
  }
}
