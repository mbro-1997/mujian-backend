package com.mujian.backend.order.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.coupon.domain.CouponUsageDO;
import com.mujian.backend.coupon.mapper.*;
import com.mujian.backend.coupon.projection.CouponProjection;
import com.mujian.backend.order.domain.CheckoutBatchDO;
import com.mujian.backend.order.mapper.*;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.*;
import com.mujian.backend.order.vo.CheckoutViews;
import com.mujian.backend.payment.converter.PaymentViewConverter;
import com.mujian.backend.payment.domain.PaymentOrderDO;
import com.mujian.backend.payment.mapper.*;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 批次读取仅展示持久事实；GET不触发渠道付款、不延长原有效期。 */
@Service
public class CheckoutBatchReaderImpl implements CheckoutBatchReader {
  private final TradingAccess tradingAccess;
  private final CheckoutBatchMapper checkoutBatchMapper;
  private final PaymentOrderMapper paymentOrderMapper;
  private final RentalOrderMapper rentalOrderMapper;
  private final RefundMapper refundMapper;
  private final CouponUsageMapper couponUsageMapper;
  private final CouponMapper couponMapper;
  private final Clock clock;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public CheckoutBatchReaderImpl(
      TradingAccess tradingAccess,
      CheckoutBatchMapper checkoutBatchMapper,
      PaymentOrderMapper paymentOrderMapper,
      RentalOrderMapper rentalOrderMapper,
      RefundMapper refundMapper,
      CouponUsageMapper couponUsageMapper,
      CouponMapper couponMapper,
      Clock clock) {
    this.tradingAccess = tradingAccess;
    this.checkoutBatchMapper = checkoutBatchMapper;
    this.paymentOrderMapper = paymentOrderMapper;
    this.rentalOrderMapper = rentalOrderMapper;
    this.refundMapper = refundMapper;
    this.couponUsageMapper = couponUsageMapper;
    this.couponMapper = couponMapper;
    this.clock = clock;
  }

  /** 先鉴权后读取相关支付和退款，避免通过金额、渠道字段探测他人批次。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public BatchViews.Batch read(String actor, String batchId) {
    tradingAccess.buyer(actor);
    CheckoutBatchDO checkoutBatchDOBatch = checkoutBatchMapper.selectBatchById(batchId, false);
    if (checkoutBatchDOBatch == null || !checkoutBatchDOBatch.buyerUserId().equals(actor))
      throw missing();
    PaymentOrderDO paymentOrderDO = paymentOrderMapper.selectPaymentByBatch(batchId, false);
    if (paymentOrderDO == null) throw new IllegalStateException("batch payment missing");
    List<String> orderIds = rentalOrderMapper.selectOrderIdsByBatch(batchId, false),
        refundIds = refundMapper.selectRefundIdsByBatch(batchId);
    boolean pay =
        TradingState.pending_payment.name().equals(checkoutBatchDOBatch.batchStatus())
            && checkoutBatchDOBatch.guardReleasedAt() == null
            && paymentOrderDO.closeRequestedAt() == null
            && Set.of(TradingState.pending.name(), TradingState.paying.name())
                .contains(paymentOrderDO.paymentStatus())
            && now(clock).isBefore(paymentOrderDO.expireAt())
            && !tradingAccess.hasOutstandingCompensation(actor);
    boolean cancel =
        Set.of(TradingState.pending_payment.name(), "payment_exception")
                .contains(checkoutBatchDOBatch.batchStatus())
            && checkoutBatchDOBatch.guardReleasedAt() == null
            && !TradingState.success.name().equals(paymentOrderDO.paymentStatus())
            && paymentOrderDO.closeRequestedAt() == null;
    String action =
        !orderIds.isEmpty()
            ? TradingNextAction.view_orders.name()
            : !refundIds.isEmpty()
                ? TradingNextAction.view_refunds.name()
                : Set.of(
                            TradingState.closed.name(),
                            TradingState.expired.name(),
                            TradingState.refunded_closed.name())
                        .contains(checkoutBatchDOBatch.batchStatus())
                    ? TradingNextAction.return_checklist.name()
                    : pay && paymentOrderDO.channelPrepayId() != null
                        ? TradingNextAction.pay.name()
                        : TradingNextAction.wait.name();
    BatchViews.CouponReturn couponReturn =
        new BatchViews.CouponReturn(TradingState.none.name(), null, null, null);
    CouponUsageDO couponUsageDOUsage = couponUsageMapper.selectUsageByBatch(batchId, false);
    if (couponUsageDOUsage != null) {
      CouponProjection couponProjectionCoupon =
          couponMapper.selectCouponByUserAndId(actor, couponUsageDOUsage.userCouponId(), false);
      couponReturn =
          new BatchViews.CouponReturn(
              TradingState.returned.name().equals(couponUsageDOUsage.usageStatus())
                  ? TradingState.returned.name()
                  : TradingState.not_returned.name(),
              couponUsageDOUsage.userCouponId(),
              time(couponUsageDOUsage.returnConfirmedAt()),
              couponProjectionCoupon == null ? null : time(couponProjectionCoupon.validUntil()));
    }
    CheckoutViews.Amounts amounts =
        new CheckoutViews.Amounts(
            checkoutBatchDOBatch.rentPayableAmountFen(),
            checkoutBatchDOBatch.couponDiscountAmountFen(),
            Math.subtractExact(
                checkoutBatchDOBatch.rentPayableAmountFen(),
                checkoutBatchDOBatch.couponDiscountAmountFen()),
            checkoutBatchDOBatch.depositPayableAmountFen(),
            checkoutBatchDOBatch.freightPrepaidAmountFen(),
            checkoutBatchDOBatch.payableAmountFen());
    return new BatchViews.Batch(
        couponReturn,
        checkoutBatchDOBatch.id(),
        checkoutBatchDOBatch.batchNo(),
        checkoutBatchDOBatch.sourceChecklistId(),
        checkoutBatchDOBatch.batchStatus(),
        checkoutBatchDOBatch.version(),
        amounts,
        PaymentViewConverter.payment(paymentOrderDO),
        List.copyOf(orderIds),
        refundIds,
        checkoutBatchDOBatch.recoveryMode(),
        action,
        pay,
        cancel,
        time(checkoutBatchDOBatch.holdExpireAt()),
        time(checkoutBatchDOBatch.createdAt()));
  }
}
