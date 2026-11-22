package com.mujian.backend.payment.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.SystemIdentity;
import com.mujian.backend.coupon.service.CouponLifecycle;
import com.mujian.backend.order.domain.*;
import com.mujian.backend.order.mapper.CheckoutBatchMapper;
import com.mujian.backend.order.service.TradingRoots;
import com.mujian.backend.payment.domain.ChannelSuborderDO;
import com.mujian.backend.payment.dto.CashComponents;
import com.mujian.backend.prop.domain.InventoryReservationDO;
import com.mujian.backend.prop.mapper.InventoryReservationMapper;
import com.mujian.backend.prop.service.TradingReservations;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 未成单原批次收退款，旧来源晚到实收也只能沿旧号退款。 */
@Service
public class UnformedBatchRefunds {
  private final TradingRoots tradingRoots;
  private final TradingReservations tradingReservations;
  private final CouponLifecycle couponLifecycle;
  private final CheckoutBatchMapper checkoutBatchMapper;
  private final RefundObligations refundObligations;
  private final InventoryReservationMapper inventoryReservationMapper;
  private final Clock clock;

  /** 退款与安全释放沿同一原根锁，不抢占新清单guard。 */
  public UnformedBatchRefunds(
      TradingRoots tradingRoots,
      TradingReservations tradingReservations,
      CouponLifecycle couponLifecycle,
      CheckoutBatchMapper checkoutBatchMapper,
      RefundObligations refundObligations,
      InventoryReservationMapper inventoryReservationMapper,
      Clock clock) {
    this.tradingRoots = tradingRoots;
    this.tradingReservations = tradingReservations;
    this.couponLifecycle = couponLifecycle;
    this.checkoutBatchMapper = checkoutBatchMapper;
    this.refundObligations = refundObligations;
    this.inventoryReservationMapper = inventoryReservationMapper;
    this.clock = clock;
  }

  /** 原已付子单创建唯一退款；全部未知消除且原款返清后才恢复原清单。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public List<String> reconcile(String batchId) {
    TradingRoots.Locked locked = tradingRoots.lock(batchId);
    if (!locked.orderIds().isEmpty()) return List.of();
    List<InventoryReservationDO> inventoryReservationDOListReservations =
        tradingReservations.lockExisting(batchId);
    CouponLifecycle.Locked lockedCoupon =
        couponLifecycle.lock(locked.batch().buyerUserId(), batchId);
    boolean refundBatch =
        TradingRecoveryMode.refund_batch.name().equals(locked.batch().recoveryMode());
    Map<String, Integer> rent = new HashMap<>(),
        deposit = new HashMap<>(),
        freight = new HashMap<>();
    for (BatchItemSnapshotDO batchItemSnapshotDO :
        checkoutBatchMapper.selectItemSnapshotsByBatch(batchId, null)) {
      rent.put(
          batchItemSnapshotDO.factoryId(),
          Math.addExact(
              rent.getOrDefault(batchItemSnapshotDO.factoryId(), 0),
              batchItemSnapshotDO.rentCashPayableAmountFen()));
      deposit.put(
          batchItemSnapshotDO.factoryId(),
          Math.addExact(
              deposit.getOrDefault(batchItemSnapshotDO.factoryId(), 0),
              batchItemSnapshotDO.depositPayableAmountFen()));
    }
    for (BatchTransportSnapshotDO batchTransportSnapshotDOTransport :
        checkoutBatchMapper.selectTransportSnapshotsByBatch(batchId, null))
      freight.put(
          batchTransportSnapshotDOTransport.factoryId(),
          batchTransportSnapshotDOTransport.freightPrepaidAmountFen());
    List<String> refundIds = new ArrayList<>();
    boolean safe = true;
    int paid = 0;
    for (ChannelSuborderDO channelSuborderDOSuborder : locked.suborders()) {
      paid = Math.addExact(paid, channelSuborderDOSuborder.paidAmountFen());
      if (!Set.of(TradingState.success.name(), TradingState.closed.name())
              .contains(channelSuborderDOSuborder.paymentStatus())
          || channelSuborderDOSuborder.refundedAmountFen()
              != channelSuborderDOSuborder.paidAmountFen()) safe = false;
      if (refundBatch && channelSuborderDOSuborder.paidAmountFen() > 0) {
        CashComponents cashComponentsCash =
            new CashComponents(
                rent.get(channelSuborderDOSuborder.factoryId()),
                deposit.get(channelSuborderDOSuborder.factoryId()),
                freight.get(channelSuborderDOSuborder.factoryId()));
        String refundId =
            refundObligations.reserve(
                SystemIdentity.SYSTEM_USER_ID,
                TradingResource.checkout_batch.name(),
                batchId,
                TradingRefundType.unformed_batch_refund.name(),
                "未成单批次原款退回",
                channelSuborderDOSuborder,
                cashComponentsCash,
                cashComponentsCash);
        if (refundId != null) refundIds.add(refundId);
      }
    }
    if (safe
        && Set.of(
                TradingState.refunded_closed.name(),
                TradingState.closed.name(),
                TradingState.expired.name())
            .contains(locked.batch().batchStatus())) return List.copyOf(refundIds);
    if (refundBatch && paid > 0) checkoutBatchMapper.markBatchRefunding(batchId, now(clock));
    if (safe) {
      couponLifecycle.reconcile(lockedCoupon, locked.suborders(), false, false);
      String status =
          paid > 0
              ? TradingState.refunded_closed.name()
              : TradingState.expired.name().equals(locked.payment().closeReason())
                  ? TradingState.expired.name()
                  : TradingState.closed.name();
      inventoryReservationMapper.releaseBatch(
          batchId,
          TradingState.expired.name().equals(status)
              ? TradingState.expired.name()
              : TradingState.released.name(),
          now(clock));
      Set<String> skuIds = new TreeSet<>();
      for (InventoryReservationDO inventoryReservationDOReservation :
          inventoryReservationDOListReservations)
        skuIds.add(inventoryReservationDOReservation.getSkuId());
      tradingReservations.refreshCurrent(skuIds);
      checkoutBatchMapper.closeBatch(batchId, status, now(clock));
      checkoutBatchMapper.restoreChecklist(locked.checklist().id(), batchId, now(clock));
    }
    return List.copyOf(refundIds);
  }
}
