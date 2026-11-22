package com.mujian.backend.payment.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.coupon.domain.CouponUsageDO;
import com.mujian.backend.coupon.mapper.CouponUsageMapper;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.dto.OrderQuery;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.*;
import com.mujian.backend.order.vo.OrderViews;
import com.mujian.backend.payment.converter.PaymentViewConverter;
import com.mujian.backend.payment.domain.ChannelSuborderDO;
import com.mujian.backend.payment.domain.PaymentOrderDO;
import com.mujian.backend.payment.domain.RefundOrderDO;
import com.mujian.backend.payment.dto.*;
import com.mujian.backend.payment.mapper.*;
import com.mujian.backend.payment.service.*;
import com.mujian.backend.payment.vo.*;
import com.mujian.backend.payment.vo.PaymentTraceVO;
import com.mujian.backend.payment.vo.RefundViews;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 后台只读追溯按各菜单精确授权；付款分页按工厂EXISTS避免重复总额。 */
@Service
public class AdminTradeReaderImpl implements AdminTradeReader {
  private final VerificationAccess verificationAccess;
  private final RentalOrderReader rentalOrderReader;
  private final RentalOrderMapper rentalOrderMapper;
  private final CheckoutBatchReader checkoutBatchReader;
  private final TradeSnapshotReader tradeSnapshotReader;
  private final TradeReadAudit tradeReadAudit;
  private final PaymentOrderMapper paymentOrderMapper;
  private final RefundMapper refundMapper;
  private final RefundReader refundReader;
  private final CouponUsageMapper couponUsageMapper;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public AdminTradeReaderImpl(
      VerificationAccess verificationAccess,
      RentalOrderReader rentalOrderReader,
      RentalOrderMapper rentalOrderMapper,
      CheckoutBatchReader checkoutBatchReader,
      TradeSnapshotReader tradeSnapshotReader,
      TradeReadAudit tradeReadAudit,
      PaymentOrderMapper paymentOrderMapper,
      RefundMapper refundMapper,
      RefundReader refundReader,
      CouponUsageMapper couponUsageMapper) {
    this.verificationAccess = verificationAccess;
    this.rentalOrderReader = rentalOrderReader;
    this.rentalOrderMapper = rentalOrderMapper;
    this.checkoutBatchReader = checkoutBatchReader;
    this.tradeSnapshotReader = tradeSnapshotReader;
    this.tradeReadAudit = tradeReadAudit;
    this.paymentOrderMapper = paymentOrderMapper;
    this.refundMapper = refundMapper;
    this.refundReader = refundReader;
    this.couponUsageMapper = couponUsageMapper;
  }

  /** 按管理订单权限读取有范围的分页结果。 */
  public PageResult<OrderViews.Summary> orders(String actor, OrderQuery orderQuery) {
    verificationAccess.admin(actor, "admin:trade:order:read");
    return rentalOrderReader.readPage(orderQuery, false);
  }

  /** 读取原订单快照并记录最小敏感访问审计。 */
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public OrderViews.Detail order(String actor, String id, String trace) {
    String permission = "admin:trade:order:read";
    verificationAccess.admin(actor, permission);
    RentalOrderDO rentalOrderDO = rentalOrderMapper.selectOrderById(id);
    if (rentalOrderDO == null) throw missing();
    OrderViews.Detail detailResult = rentalOrderReader.authorizedDetail(rentalOrderDO, false);
    tradeReadAudit.record(actor, permission, TradingResource.rental_order.name(), id, trace);
    return detailResult;
  }

  /** 按支付追溯权限分页读取原主支付，不重复多厂金额。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<BatchViews.Payment> payments(String actor, PaymentQuery paymentQuery) {
    verificationAccess.admin(actor, "admin:trade:payment:read");
    List<BatchViews.Payment> paymentList = new ArrayList<>();
    for (PaymentOrderDO paymentOrderDO : paymentOrderMapper.selectPaymentPage(paymentQuery))
      paymentList.add(PaymentViewConverter.payment(paymentOrderDO));
    return PageResult.of(
        paymentList, paymentOrderMapper.countPayments(paymentQuery), paymentQuery.page());
  }

  /** 追溯原支付、子单、订单和退款关系并记录访问。 */
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public PaymentTraceVO payment(String actor, String id, String trace) {
    String permission = "admin:trade:payment:read";
    verificationAccess.admin(actor, permission);
    PaymentOrderDO paymentOrderDO = paymentOrderMapper.selectPaymentById(id, false);
    if (paymentOrderDO == null) throw missing();
    List<PaymentTraceVO.Suborder> suborderListSuborders = new ArrayList<>();
    for (ChannelSuborderDO channelSuborderDOSub :
        paymentOrderMapper.selectSubordersByPayment(id, false))
      suborderListSuborders.add(
          new PaymentTraceVO.Suborder(
              channelSuborderDOSub.id(),
              channelSuborderDOSub.factoryId(),
              channelSuborderDOSub.rentalOrderId(),
              channelSuborderDOSub.paymentStatus(),
              channelSuborderDOSub.channelSuborderStatus(),
              channelSuborderDOSub.channelSuborderAmountFen(),
              channelSuborderDOSub.paidAmountFen(),
              channelSuborderDOSub.refundedAmountFen(),
              channelSuborderDOSub.channelSubOrderNo(),
              channelSuborderDOSub.channelSubTransactionId(),
              time(channelSuborderDOSub.paidAt()),
              time(channelSuborderDOSub.freezeExpireAt()),
              channelSuborderDOSub.freezeAlertStatus()));
    PaymentOrderMapper.Source source=paymentOrderMapper.selectSource(id);
    boolean initial=paymentOrderDO.checkoutBatchId()!=null;
    List<RefundViews.Refund> refundListRefunds =
        refundReader.authorizedList(
            refundMapper.selectRefundsByPayment(id));
    CouponUsageDO couponUsageDOUsage =
        initial ? couponUsageMapper.selectUsageByBatch(paymentOrderDO.checkoutBatchId(), false) : null;
    PaymentTraceVO paymentTraceVOResult =
        new PaymentTraceVO(
            PaymentViewConverter.payment(paymentOrderDO),
            initial ? checkoutBatchReader.read(
                paymentOrderDO.buyerUserId(), paymentOrderDO.checkoutBatchId()) : null,
            suborderListSuborders,
            refundListRefunds,
            initial ? tradeSnapshotReader.groups(paymentOrderDO.checkoutBatchId()) : List.of(),
            couponUsageDOUsage == null ? null : couponUsageDOUsage.id(),source.scene(),source.extensionId(),source.receivableId(),source.customOrderId());
    tradeReadAudit.record(actor, permission, "payment_order", id, trace);
    return paymentTraceVOResult;
  }

  /** 按退款权限分页读取原义务及原支付返券事实。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<RefundViews.Refund> refunds(String actor, RefundQuery refundQuery) {
    verificationAccess.admin(actor, "admin:trade:refund:read");
    return PageResult.of(
        refundReader.authorizedList(refundMapper.selectRefundPage(refundQuery)),
        refundMapper.countRefunds(refundQuery),
        refundQuery.page());
  }

  /** 读取原退款号、查询进度和来源，记录详情访问。 */
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public RefundViews.Trace refund(String actor, String id, String trace) {
    String permission = "admin:trade:refund:read";
    verificationAccess.admin(actor, permission);
    RefundOrderDO refundOrderDO = refundMapper.selectRefundById(id, false);
    if (refundOrderDO == null) throw missing();
    RefundViews.Trace traceResult =
        new RefundViews.Trace(
            refundReader.authorized(refundOrderDO),
            refundOrderDO.channelOutRefundNo(),
            refundOrderDO.channelRefundNo(),
            time(refundOrderDO.lastCheckedAt()),
            refundOrderDO.attemptCount());
    tradeReadAudit.record(actor, permission, "refund_order", id, trace);
    return traceResult;
  }
}
