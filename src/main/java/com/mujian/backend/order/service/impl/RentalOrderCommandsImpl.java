package com.mujian.backend.order.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.common.support.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.coupon.service.CouponLifecycle;
import com.mujian.backend.order.domain.*;
import com.mujian.backend.order.dto.*;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.policy.CancellationCalculation;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.service.TradingRoots;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.payment.domain.ChannelSuborderDO;
import com.mujian.backend.payment.dto.CashComponents;
import com.mujian.backend.payment.mapper.RefundMapper;
import com.mujian.backend.payment.projection.RefundExposureProjection;
import com.mujian.backend.payment.service.RefundObligations;
import com.mujian.backend.prop.domain.InventoryReservationDO;
import com.mujian.backend.prop.mapper.InventoryReservationMapper;
import com.mujian.backend.prop.service.TradingReservations;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 接单、拒绝、取消和超时共用订单根与原资金义务，互斥提交。 */
@Service
public class RentalOrderCommandsImpl implements RentalOrderCommands {
  private final RentalOrderAccess rentalOrderAccess;
  private final TradingRoots tradingRoots;
  private final TradingOperations tradingOperations;
  private final RentalOrderMapper rentalOrderMapper;
  private final TradingReservations tradingReservations;
  private final InventoryReservationMapper inventoryReservationMapper;
  private final RefundMapper refundMapper;
  private final CouponLifecycle couponLifecycle;
  private final RefundObligations refundObligations;
  private final TradingTokens tradingTokens;
  private final OrderCancellationQuotation orderCancellationQuotation;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public RentalOrderCommandsImpl(
      RentalOrderAccess rentalOrderAccess,
      TradingRoots tradingRoots,
      TradingOperations tradingOperations,
      RentalOrderMapper rentalOrderMapper,
      TradingReservations tradingReservations,
      InventoryReservationMapper inventoryReservationMapper,
      RefundMapper refundMapper,
      CouponLifecycle couponLifecycle,
      RefundObligations refundObligations,
      TradingTokens tradingTokens,
      OrderCancellationQuotation orderCancellationQuotation,
      ObjectMapper objectMapper,
      Clock clock) {
    this.rentalOrderAccess = rentalOrderAccess;
    this.tradingRoots = tradingRoots;
    this.tradingOperations = tradingOperations;
    this.rentalOrderMapper = rentalOrderMapper;
    this.tradingReservations = tradingReservations;
    this.inventoryReservationMapper = inventoryReservationMapper;
    this.refundMapper = refundMapper;
    this.couponLifecycle = couponLifecycle;
    this.refundObligations = refundObligations;
    this.tradingTokens = tradingTokens;
    this.orderCancellationQuotation = orderCancellationQuotation;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 接拒只允许截止前的待接原单，库存保持原已付占用，接单不重新选SN。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt factory(
      TradingCommand tradingCommand, String orderId, int expectedVersion, String reason) {
    tradingOperations.authorize(tradingCommand.actor(), tradingCommand.operation());
    RentalOrderDO rentalOrderDOHint =
        rentalOrderAccess.owned(tradingCommand.actor(), orderId, true, false);
    TradingRoots.Locked locked = tradingRoots.lock(rentalOrderDOHint.checkoutBatchId());
    TradingReceipt tradingReceiptReplay = tradingOperations.replay(tradingCommand);
    if (tradingReceiptReplay != null) return tradingReceiptReplay;
    RentalOrderDO rentalOrderDO = rentalOrderMapper.selectOrderById(orderId);
    boolean accept = tradingCommand.operation() == TradingOperation.acceptFactoryOrder;
    if (!accept && tradingCommand.operation() != TradingOperation.rejectFactoryOrder)
      throw new IllegalArgumentException("invalid factory command");
    if (accept) rentalOrderAccess.owned(tradingCommand.actor(), orderId, true, true);
    if (rentalOrderDO.version() != expectedVersion) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if (!TradingState.pending_accept.name().equals(rentalOrderDO.orderStatus())
        || !TradingState.pending_accept.name().equals(rentalOrderDO.acceptStatus()))
      throw conflict(TradingReason.ORDER_STATE_CHANGED.name());
    if (!now(clock).isBefore(rentalOrderDO.acceptDeadlineAt()))
      throw conflict(TradingReason.ACCEPT_DEADLINE_PASSED.name());
    rentalOrderAccess.cancellable(rentalOrderDO);
    if (accept) {
      if (rentalOrderMapper.acceptOrder(orderId, expectedVersion, now(clock)) != 1)
        throw conflict(TradingReason.ORDER_STATE_CHANGED.name());
      rentalOrderMapper.readyTransportLegs(orderId, now(clock));
    } else
      terminate(
          tradingCommand.actor(),
          rentalOrderDO,
          locked,
          TradingRefundType.factory_manual_reject.name(),
          reason,
          new CancellationCalculation.Result(
              0,
              0,
              rentalOrderDO.rentPaidAmountFen(),
              rentalOrderDO.depositPaidAmountFen(),
              rentalOrderDO.freightPrepaidPaidAmountFen(),
              rentalOrderDO.totalPaidAmountFen()),
          null);
    return tradingOperations.record(
        tradingCommand, TradingResource.rental_order.name(), orderId, expectedVersion + 1);
  }

  /** 同号先恢复；新确认必须与锁内重新计算的档位和原单版本一致。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt cancel(
      TradingCommand tradingCommand, String orderId, OrderDtos.Cancel cancelInput) {
    tradingOperations.authorize(tradingCommand.actor(), tradingCommand.operation());
    RentalOrderDO rentalOrderDOHint =
        rentalOrderAccess.owned(tradingCommand.actor(), orderId, false, false);
    TradingRoots.Locked locked = tradingRoots.lock(rentalOrderDOHint.checkoutBatchId());
    TradingReceipt tradingReceiptReplay = tradingOperations.replay(tradingCommand);
    if (tradingReceiptReplay != null) return tradingReceiptReplay;
    RentalOrderDO rentalOrderDO = rentalOrderMapper.selectOrderById(orderId);
    rentalOrderAccess.cancellable(rentalOrderDO);
    CancellationQuoteData
        cancellationQuoteDataConfirmed =
            tradingTokens.readCancellation(
                tradingCommand.actor(), orderId, cancelInput.confirmToken()),
        cancellationQuoteDataCurrent = orderCancellationQuotation.data(rentalOrderDO);
    if (cancellationQuoteDataConfirmed.orderVersion() != rentalOrderDO.version()
        || !cancellationQuoteDataConfirmed
            .ruleCode()
            .equals(cancellationQuoteDataCurrent.ruleCode())
        || !Objects.equals(
            cancellationQuoteDataConfirmed.ruleVersion(),
            cancellationQuoteDataCurrent.ruleVersion())
        || !cancellationQuoteDataConfirmed.amounts().equals(cancellationQuoteDataCurrent.amounts()))
      throw conflict(TradingReason.CANCELLATION_QUOTE_CHANGED.name());
    String type =
        TradingState.accepted.name().equals(rentalOrderDO.acceptStatus())
            ? TradingRefundType.user_cancel_rule.name()
            : TradingRefundType.user_cancel_before_accept.name();
    terminate(
        tradingCommand.actor(),
        rentalOrderDO,
        locked,
        type,
        cancelInput.reason(),
        cancellationQuoteDataCurrent.amounts(),
        cancellationQuoteDataCurrent);
    return tradingOperations.record(
        tradingCommand, TradingResource.rental_order.name(), orderId, rentalOrderDO.version() + 1);
  }

  /** 系统到期使用固定系统用户；与人工接单、拒绝、取消争同一根锁。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void timeout(String orderId) {
    RentalOrderDO rentalOrderDOHint = rentalOrderMapper.selectOrderById(orderId);
    if (rentalOrderDOHint == null) return;
    TradingRoots.Locked locked = tradingRoots.lock(rentalOrderDOHint.checkoutBatchId());
    RentalOrderDO rentalOrderDO = rentalOrderMapper.selectOrderById(orderId);
    if (!TradingState.pending_accept.name().equals(rentalOrderDO.orderStatus())
        || !TradingState.pending_accept.name().equals(rentalOrderDO.acceptStatus())
        || now(clock).isBefore(rentalOrderDO.acceptDeadlineAt())) return;
    rentalOrderAccess.cancellable(rentalOrderDO);
    terminate(
        SystemIdentity.SYSTEM_USER_ID,
        rentalOrderDO,
        locked,
        TradingRefundType.factory_timeout_reject.name(),
        "工厂接单超时",
        new CancellationCalculation.Result(
            0,
            0,
            rentalOrderDO.rentPaidAmountFen(),
            rentalOrderDO.depositPaidAmountFen(),
            rentalOrderDO.freightPrepaidPaidAmountFen(),
            rentalOrderDO.totalPaidAmountFen()),
        null);
  }

  /** 所有终止来源在同一事务内释放占用、保留退款额度、写扣款和业务状态。 */
  private void terminate(
      String actor,
      RentalOrderDO rentalOrderDO,
      TradingRoots.Locked locked,
      String type,
      String reason,
      CancellationCalculation.Result resultAmounts,
      CancellationQuoteData cancellationDecision) {
    List<InventoryReservationDO> inventoryReservationDOListReservations =
        tradingReservations.lockExisting(rentalOrderDO.checkoutBatchId());
    couponLifecycle.lock(rentalOrderDO.buyerUserId(), rentalOrderDO.checkoutBatchId());
    ChannelSuborderDO channelSuborderDOSuborder = null;
    for (ChannelSuborderDO channelSuborderDO : locked.suborders())
      if (rentalOrderDO.id().equals(channelSuborderDO.rentalOrderId())) {
        channelSuborderDOSuborder = channelSuborderDO;
        break;
      }
    if (channelSuborderDOSuborder == null)
      throw new IllegalStateException("original suborder missing");
    CashComponents cashComponentsOriginal =
        new CashComponents(
            rentalOrderDO.rentPaidAmountFen(),
            rentalOrderDO.depositPaidAmountFen(),
            rentalOrderDO.freightPrepaidPaidAmountFen());
    CancellationDeductionSnapshot deductionSnapshot = null;
    if (resultAmounts.deductionFen() > 0) {
      if (cancellationDecision == null)
        throw new IllegalStateException("cancellation decision missing");
      RefundExposureProjection exposure =
          refundMapper.selectRefundExposureBySuborder(channelSuborderDOSuborder.id());
      CashComponents refunded =
          new CashComponents(
              rentalOrderDO.rentRefundedAmountFen(),
              rentalOrderDO.depositRefundedAmountFen(),
              rentalOrderDO.freightRefundedAmountFen());
      CashComponents inFlight =
          new CashComponents(
              Math.toIntExact(exposure.rentFen() - refunded.rentFen()),
              Math.toIntExact(exposure.depositFen() - refunded.depositFen()),
              Math.toIntExact(exposure.freightFen() - refunded.freightFen()));
      LocalDateTime rentalStart = rentalOrderDO.rentalStartDate().atStartOfDay();
      deductionSnapshot =
          new CancellationDeductionSnapshot(
              1,
              cancellationDecision.ruleCode(),
              cancellationDecision.ruleVersion(),
              rentalOrderDO.version(),
              cancellationDecision.issuedAt(),
              rentalStart,
              Duration.between(cancellationDecision.issuedAt(), rentalStart).getSeconds(),
              rentalOrderDO.rentOriginalAmountFen(),
              rentalOrderDO.rentPaidAmountFen(),
              resultAmounts,
              refunded,
              inFlight);
    }
    refundObligations.reserve(
        actor,
        TradingResource.rental_order.name(),
        rentalOrderDO.id(),
        type,
        reason,
        channelSuborderDOSuborder,
        cashComponentsOriginal,
        new CashComponents(
            resultAmounts.rentRefundFen(),
            resultAmounts.depositRefundFen(),
            resultAmounts.freightRefundFen()));
    if (deductionSnapshot != null) {
      rentalOrderMapper.insertCancellationDeduction(
          new OrderWrites.Deduction(
              Ids.next(),
              rentalOrderDO.id(),
              resultAmounts.deductionFen(),
              rentalOrderDO.rentOriginalAmountFen(),
              resultAmounts.rateBps(),
              objectMapper.writeValueAsString(deductionSnapshot),
              reason,
              actor,
              now(clock)));
    }
    String status =
        TradingRefundType.factory_manual_reject.name().equals(type)
            ? TradingState.manual_rejected.name()
            : TradingRefundType.factory_timeout_reject.name().equals(type)
                ? TradingState.timeout_rejected.name()
                : TradingState.user_cancelled.name();
    String acceptStatus =
        type.startsWith("factory_")
            ? status
            : TradingState.pending_accept.name().equals(rentalOrderDO.acceptStatus())
                ? TradingState.cancelled_before_accept.name()
                : rentalOrderDO.acceptStatus();
    String rejectType =
        TradingRefundType.factory_manual_reject.name().equals(type)
            ? "manual"
            : TradingRefundType.factory_timeout_reject.name().equals(type) ? "timeout" : null;
    if (rentalOrderMapper.terminateOrder(
            new OrderWrites.Termination(
                rentalOrderDO.id(),
                rentalOrderDO.version(),
                status,
                acceptStatus,
                rejectType,
                reason,
                type,
                resultAmounts.deductionFen(),
                resultAmounts.totalRefundFen(),
                now(clock)))
        != 1) throw conflict(TradingReason.ORDER_STATE_CHANGED.name());
    rentalOrderMapper.cancelOrderDelivery(rentalOrderDO.id(), now(clock));
    rentalOrderMapper.cancelTransportLegs(rentalOrderDO.id(), now(clock));
    rentalOrderMapper.cancelFreight(rentalOrderDO.id(), now(clock));
    inventoryReservationMapper.releaseOrder(rentalOrderDO.id(), now(clock));
    Set<String> skuIds = new TreeSet<>();
    for (InventoryReservationDO inventoryReservationDOReservation :
        inventoryReservationDOListReservations)
      if (rentalOrderDO.id().equals(inventoryReservationDOReservation.getRentalOrderId()))
        skuIds.add(inventoryReservationDOReservation.getSkuId());
    tradingReservations.refreshCurrent(skuIds);
  }
}
