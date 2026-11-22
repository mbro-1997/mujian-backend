package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.coupon.domain.CouponUsageDO;
import com.mujian.backend.coupon.dto.CouponWrites;
import com.mujian.backend.coupon.mapper.*;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.order.domain.*;
import com.mujian.backend.order.dto.FormationWrites;
import com.mujian.backend.order.mapper.*;
import com.mujian.backend.order.vo.CheckoutViews.Amounts;
import com.mujian.backend.payment.domain.ChannelSuborderDO;
import com.mujian.backend.prop.domain.InventoryReservationDO;
import com.mujian.backend.prop.mapper.InventoryReservationMapper;
import com.mujian.backend.prop.service.TradingReservations;
import com.mujian.backend.reward.service.FirstRewardCaptureService;
import com.mujian.backend.web.response.account.ContextResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 真实全付后的唯一原子成单事务；失败回滚本事务，已持久实收仍可恢复。 */
@Service
public class OrderFormation {
  private final TradingRoots tradingRoots;
  private final CheckoutBatchMapper checkoutBatchMapper;
  private final RentalOrderMapper rentalOrderMapper;
  private final TradingReservations tradingReservations;
  private final InventoryReservationMapper inventoryReservationMapper;
  private final CouponMapper couponMapper;
  private final CouponUsageMapper couponUsageMapper;
  private final FirstRewardCaptureService firstRewardCaptureService;
  private final FactoryAccess factoryAccess;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 复用库存来源、原券分摊与已有首次事实表。 */
  public OrderFormation(
      TradingRoots tradingRoots,
      CheckoutBatchMapper checkoutBatchMapper,
      RentalOrderMapper rentalOrderMapper,
      TradingReservations tradingReservations,
      InventoryReservationMapper inventoryReservationMapper,
      CouponMapper couponMapper,
      CouponUsageMapper couponUsageMapper,
      FirstRewardCaptureService firstRewardCaptureService,
      FactoryAccess factoryAccess,
      ObjectMapper objectMapper,
      Clock clock) {
    this.tradingRoots = tradingRoots;
    this.checkoutBatchMapper = checkoutBatchMapper;
    this.rentalOrderMapper = rentalOrderMapper;
    this.tradingReservations = tradingReservations;
    this.inventoryReservationMapper = inventoryReservationMapper;
    this.couponMapper = couponMapper;
    this.couponUsageMapper = couponUsageMapper;
    this.firstRewardCaptureService = firstRewardCaptureService;
    this.factoryAccess = factoryAccess;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 所有原子单已付才成单；退整批模式和已释放guard的旧批次永不成单。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public List<String> form(String batchId) {
    TradingRoots.Locked locked = tradingRoots.lock(batchId);
    if (TradingState.paid.name().equals(locked.batch().batchStatus())) return locked.orderIds();
    if (TradingRecoveryMode.refund_batch.name().equals(locked.batch().recoveryMode())
        || locked.batch().guardReleasedAt() != null) return List.of();
    if (!TradingState.success.name().equals(locked.payment().paymentStatus())) return List.of();
    Map<String, ChannelSuborderDO> channelSuborderDOMapSuborders = new TreeMap<>();
    LocalDateTime paidAt = null;
    int paid = 0;
    for (ChannelSuborderDO channelSuborderDOSuborder : locked.suborders()) {
      if (!TradingState.success.name().equals(channelSuborderDOSuborder.paymentStatus())
          || channelSuborderDOSuborder.paidAmountFen()
              != channelSuborderDOSuborder.channelSuborderAmountFen()
          || channelSuborderDOSuborder.paidAt() == null) return List.of();
      channelSuborderDOMapSuborders.put(
          channelSuborderDOSuborder.factoryId(), channelSuborderDOSuborder);
      paid = Math.addExact(paid, channelSuborderDOSuborder.paidAmountFen());
      if (paidAt == null || channelSuborderDOSuborder.paidAt().isAfter(paidAt))
        paidAt = channelSuborderDOSuborder.paidAt();
    }
    if (paid != locked.batch().payableAmountFen()
        || !locked.orderIds().isEmpty()
        || !batchId.equals(locked.checklist().currentCheckoutBatchId())
        || !"checkout_processing".equals(locked.checklist().checklistStatus()))
      throw new IllegalStateException("formation source inconsistent");
    List<InventoryReservationDO> inventoryReservationDOListReservations =
        tradingReservations.lockExisting(batchId);
    Map<String, Integer> quantities = new HashMap<>(), reservationCounts = new HashMap<>();
    Set<String> skuIds = new TreeSet<>();
    for (InventoryReservationDO inventoryReservationDOReservation :
        inventoryReservationDOListReservations) {
      if (!"held".equals(inventoryReservationDOReservation.getReservationStatus()))
        throw new IllegalStateException("formation reservation not held");
      String snapshotId = inventoryReservationDOReservation.getCheckoutBatchItemSnapshotId();
      quantities.put(
          snapshotId,
          Math.addExact(
              quantities.getOrDefault(snapshotId, 0),
              inventoryReservationDOReservation.getQuantity()));
      reservationCounts.put(snapshotId, reservationCounts.getOrDefault(snapshotId, 0) + 1);
      skuIds.add(inventoryReservationDOReservation.getSkuId());
    }
    CouponUsageDO couponUsageDO = couponUsageMapper.selectUsageByBatch(batchId, false);
    if (couponUsageDO != null) {
      if (couponMapper.selectCouponByUserAndId(
              locked.batch().buyerUserId(), couponUsageDO.userCouponId(), true)
          == null) throw new IllegalStateException("formation coupon missing");
      couponUsageDO = couponUsageMapper.selectUsageByBatch(batchId, true);
      if (!TradingState.locked.name().equals(couponUsageDO.usageStatus()))
        throw new IllegalStateException("formation coupon not locked");
    }
    Map<String, List<BatchItemSnapshotDO>> batchItemSnapshotDOListMapItemGroups = new TreeMap<>();
    for (BatchItemSnapshotDO batchItemSnapshotDO :
        checkoutBatchMapper.selectItemSnapshotsByBatch(batchId, null)) {
      if (quantities.getOrDefault(batchItemSnapshotDO.id(), 0) != batchItemSnapshotDO.quantity())
        throw new IllegalStateException("formation reservation quantity mismatch");
      List<BatchItemSnapshotDO> batchItemSnapshotDOListItems =
          batchItemSnapshotDOListMapItemGroups.get(batchItemSnapshotDO.factoryId());
      if (batchItemSnapshotDOListItems == null) {
        batchItemSnapshotDOListItems = new ArrayList<>();
        batchItemSnapshotDOListMapItemGroups.put(
            batchItemSnapshotDO.factoryId(), batchItemSnapshotDOListItems);
      }
      batchItemSnapshotDOListItems.add(batchItemSnapshotDO);
    }
    List<BatchTransportSnapshotDO> batchTransportSnapshotDOListTransports =
        checkoutBatchMapper.selectTransportSnapshotsByBatch(batchId, null);
    if (batchTransportSnapshotDOListTransports.size() != channelSuborderDOMapSuborders.size()
        || batchItemSnapshotDOListMapItemGroups.size() != channelSuborderDOMapSuborders.size())
      throw new IllegalStateException("formation factory mismatch");
    LocalDateTime at = now(clock);
    List<String> orderIds = new ArrayList<>();
    for (BatchTransportSnapshotDO batchTransportSnapshotDOTransport :
        batchTransportSnapshotDOListTransports) {
      ChannelSuborderDO channelSuborderDOSuborder =
          channelSuborderDOMapSuborders.get(batchTransportSnapshotDOTransport.factoryId());
      if (channelSuborderDOSuborder == null)
        throw new IllegalStateException("formation suborder missing");
      List<BatchItemSnapshotDO> batchItemSnapshotDOListItems =
          batchItemSnapshotDOListMapItemGroups.get(batchTransportSnapshotDOTransport.factoryId());
      int rent = 0, discount = 0, deposit = 0;
      for (BatchItemSnapshotDO batchItemSnapshotDO : batchItemSnapshotDOListItems) {
        rent = Math.addExact(rent, batchItemSnapshotDO.rentAmountFen());
        discount = Math.addExact(discount, batchItemSnapshotDO.couponDiscountAmountFen());
        deposit = Math.addExact(deposit, batchItemSnapshotDO.depositPayableAmountFen());
      }
      int total =
          Math.addExact(
              Math.addExact(rent - discount, deposit),
              batchTransportSnapshotDOTransport.freightPrepaidAmountFen());
      if (total != channelSuborderDOSuborder.paidAmountFen())
        throw new IllegalStateException("formation cash mismatch");
      Amounts amounts =
          new Amounts(
              rent,
              discount,
              rent - discount,
              deposit,
              batchTransportSnapshotDOTransport.freightPrepaidAmountFen(),
              total);
      TradingSnapshots.Routes routes =
          objectMapper.readValue(
              batchTransportSnapshotDOTransport.routePricingSnapshotJson(),
              TradingSnapshots.Routes.class);
      if (routes.schemaVersion() != 1)
        throw new IllegalStateException("unsupported transport snapshot");
      String orderId = Ids.next(), deliveryId = Ids.next();
      orderIds.add(orderId);
      rentalOrderMapper.insertOrder(
          new FormationWrites.Order(
              orderId,
              locked.batch(),
              locked.payment().id(),
              batchTransportSnapshotDOTransport,
              routes.receiver(),
              amounts,
              paidAt,
              at,
              at.plusHours(2)));
      rentalOrderMapper.insertDelivery(
          new FormationWrites.Delivery(
              deliveryId,
              orderId,
              batchTransportSnapshotDOTransport,
              routes.receiver(),
              locked.batch().transportNoticeSnapshot(),
              at));
      rentalOrderMapper.insertFreight(
          new FormationWrites.Freight(Ids.next(), orderId, batchTransportSnapshotDOTransport, at));
      for (BatchItemSnapshotDO batchItemSnapshotDO : batchItemSnapshotDOListItems) {
        String itemId = Ids.next();
        rentalOrderMapper.insertItemFromSnapshot(
            new FormationWrites.Item(itemId, orderId, batchItemSnapshotDO.id(), at));
        if (inventoryReservationMapper.confirmPaid(
                batchId, batchItemSnapshotDO.id(), orderId, itemId, at)
            != reservationCounts.get(batchItemSnapshotDO.id()))
          throw new IllegalStateException("formation reservation changed");
      }
      rentalOrderMapper.insertPaymentAllocation(
          new FormationWrites.Allocation(
              Ids.next(),
              orderId,
              locked.payment().id(),
              channelSuborderDOSuborder.id(),
              amounts,
              at));
      rentalOrderMapper.bindSuborder(channelSuborderDOSuborder.id(), orderId, at);
      int maxLength = 0, maxWidth = 0, maxHeight = 0;
      if (routes.outbound().route() != null || routes.returnLeg().route() != null)
        for (BatchItemSnapshotDO item : batchItemSnapshotDOListItems) {
          TradingSnapshots.Item snapshot =
              objectMapper.readValue(item.itemSnapshotJson(), TradingSnapshots.Item.class);
          maxLength = Math.max(maxLength, millimeters(snapshot.lengthCm()));
          maxWidth = Math.max(maxWidth, millimeters(snapshot.widthCm()));
          maxHeight = Math.max(maxHeight, millimeters(snapshot.heightCm()));
        }
      for (String type : List.of("outbound", "return")) {
        CheckoutQuoteData.Leg leg =
            "outbound".equals(type) ? routes.outbound() : routes.returnLeg();
        String
            from =
                "outbound".equals(type)
                    ? batchTransportSnapshotDOTransport.factoryAddressSnapshot()
                    : batchTransportSnapshotDOTransport.receiverAddressSnapshot(),
            to =
                "outbound".equals(type)
                    ? batchTransportSnapshotDOTransport.receiverAddressSnapshot()
                    : batchTransportSnapshotDOTransport.factoryAddressSnapshot();
        rentalOrderMapper.insertTransportLeg(
            new FormationWrites.Leg(
                Ids.next(),
                orderId,
                deliveryId,
                batchTransportSnapshotDOTransport.factoryId(),
                type,
                from,
                to,
                leg.route() == null ? null : maxLength,
                leg.route() == null ? null : maxWidth,
                leg.route() == null ? null : maxHeight,
                leg,
                objectMapper.writeValueAsString(new LegSnapshot(1, leg)),
                leg.route() == null
                    ? null
                    : LocalDateTime.ofInstant(leg.route().calculatedAt(), ZONE),
                at));
      }
    }
    if (couponUsageDO != null) {
      if (couponUsageMapper.markCouponUsed(couponUsageDO.userCouponId(), at) != 1
          || couponUsageMapper.markUsageUsed(couponUsageDO.id(), at) != 1)
        throw new IllegalStateException("formation coupon changed");
      couponUsageMapper.bindAllocationsToOrders(batchId, at);
      couponUsageMapper.bindAdjustmentsToOrders(batchId, at);
      couponUsageMapper.insertOperationLog(
          new CouponWrites.Log(
              Ids.next(),
              "used:" + batchId,
              couponUsageDO.userCouponId(),
              couponUsageDO.id(),
              TradingState.used.name(),
              TradingState.locked.name(),
              TradingState.used.name(),
              locked.batch().buyerUserId(),
              batchId,
              "全付正式成单核销",
              at));
    }
    tradingReservations.refreshCurrent(skuIds);
    if (checkoutBatchMapper.markChecklistConverted(locked.checklist().id(), batchId, at) != 1)
      throw new IllegalStateException("formation checklist guard changed");
    checkoutBatchMapper.markBatchPaid(batchId, paidAt, at);
    ContextResponse contextResponseContext =
        factoryAccess.context(locked.batch().buyerUserId(), false);
    firstRewardCaptureService.capture(
        com.mujian.backend.common.enums.FirstRewardEvent.first_rental_paid,
        locked.batch().buyerUserId(),
        locked.payment().id(),
        D2Codes.CODE_DEMANDER.equals(contextResponseContext.roleCode()),
        D2Codes.CODE_ACTIVE.equals(contextResponseContext.userStatus()),
        at);
    return List.copyOf(orderIds);
  }

  /** 原尺寸按毫米向上取整，仅用于运输段快照，不重新选车型。 */
  private static int millimeters(String centimeters) {
    return new BigDecimal(centimeters)
        .multiply(BigDecimal.TEN)
        .setScale(0, RoundingMode.CEILING)
        .intValueExact();
  }

  public record LegSnapshot(int schemaVersion, CheckoutQuoteData.Leg leg) {}
}
