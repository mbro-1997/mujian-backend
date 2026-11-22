package com.mujian.backend.payment.service;

import com.mujian.backend.delivery.mapper.IssuePlanMapper;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.coupon.service.CouponLifecycle;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.order.service.TradingRoots;
import com.mujian.backend.payment.domain.*;
import com.mujian.backend.payment.dto.*;
import com.mujian.backend.payment.mapper.*;
import com.mujian.backend.payment.policy.RefundFactValidation;
import com.mujian.backend.prop.service.TradingReservations;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 退款租约、资金合并、按科目汇总与整券返回均沿原交易根锁。 */
@Service
public class RefundWorkTransaction {
  public record Ticket(
      RefundOrderDO refund, PaymentGateway.RefundRequest request, int claimedVersion) {}

  private final com.mujian.backend.delivery.mapper.IssuePlanMapper issuePlanMapper;
  private final com.mujian.backend.delivery.service.IssuePlans issuePlans;
  private final TradingRoots tradingRoots;
  private final TradingReservations tradingReservations;
  private final CouponLifecycle couponLifecycle;
  private final RefundMapper refundMapper;
  private final PaymentOrderMapper paymentOrderMapper;
  private final Clock clock;
  private final com.mujian.backend.order.mapper.ExtensionRequestMapper extensionRequestMapper;
  private final com.mujian.backend.custom.mapper.CustomOrderMapper customOrderMapper;
  private final com.mujian.backend.custom.service.CustomAfterSaleCompletion customAfterSaleCompletion;
  private final NormalOrderFunds normalOrderFunds;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public RefundWorkTransaction(
      TradingRoots tradingRoots,
      TradingReservations tradingReservations,
      CouponLifecycle couponLifecycle,
      RefundMapper refundMapper,
      PaymentOrderMapper paymentOrderMapper,
      Clock clock,
      NormalOrderFunds normalOrderFunds,com.mujian.backend.delivery.mapper.IssuePlanMapper issuePlanMapper,
      com.mujian.backend.delivery.service.IssuePlans issuePlans,com.mujian.backend.order.mapper.ExtensionRequestMapper extensionRequestMapper,com.mujian.backend.custom.mapper.CustomOrderMapper customOrderMapper,com.mujian.backend.custom.service.CustomAfterSaleCompletion customAfterSaleCompletion) {
    this.customAfterSaleCompletion=customAfterSaleCompletion;
    this.customOrderMapper=customOrderMapper;
    this.extensionRequestMapper=extensionRequestMapper;
    this.issuePlanMapper=issuePlanMapper;
    this.issuePlans=issuePlans;
    this.normalOrderFunds = normalOrderFunds;
    this.tradingRoots = tradingRoots;
    this.tradingReservations = tradingReservations;
    this.couponLifecycle = couponLifecycle;
    this.refundMapper = refundMapper;
    this.paymentOrderMapper = paymentOrderMapper;
    this.clock = clock;
  }

  /** 读取原来源并按全局顺序加锁，绝不在事务内请求渠道。 */
  private TradingRoots.Locked roots(String refundId) {
    RefundOrderDO refundOrderDOHint = refundMapper.selectRefundById(refundId, false);
    if (refundOrderDOHint == null) throw missing();
    PaymentOrderDO paymentOrderDO =
        paymentOrderMapper.selectPaymentById(refundOrderDOHint.paymentOrderId(), false);
    return tradingRoots.lockFunding(paymentOrderDO.fundingKey());
  }

  /** 用原版本领取短租约，跨进程只允许一轮渠道执行。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Ticket claim(String refundId) {
    TradingRoots.Locked locked = roots(refundId);
    RefundOrderDO refundOrderDO = refundMapper.selectRefundById(refundId, true);
    LocalDateTime at = now(clock);
    if (Set.of(TradingState.success.name(), TradingState.cancelled.name())
            .contains(refundOrderDO.refundStatus())
        || refundOrderDO.nextAttemptAt() != null && refundOrderDO.nextAttemptAt().isAfter(at))
      return null;
    String parent=issuePlanMapper.selectRefundParent(refundId);
    if (parent!=null) {
      IssuePlanMapper.Action action=issuePlanMapper.actionById(parent);
      if (action==null || !"executing".equals(action.status()) || !issuePlans.readyForRefund(action)) return null;
    }
    if (refundMapper.claimRefundAttempt(
            new PaymentWrites.Attempt(
                refundOrderDO.id(), refundOrderDO.version(), at, at.plusSeconds(30)))
        != 1) return null;
    if (parent!=null) {
      issuePlanMapper.markSubmitting(refundId,at);
      issuePlanMapper.lockTerminationPlanForRefundSubmission(parent,at);
    }
    ChannelSuborderDO channelSuborderDOSuborder = null;
    for (ChannelSuborderDO channelSuborderDO : locked.suborders())
      if (channelSuborderDO.id().equals(refundOrderDO.paymentChannelSuborderId())) {
        channelSuborderDOSuborder = channelSuborderDO;
        break;
      }
    if (channelSuborderDOSuborder == null)
      throw new IllegalStateException("original suborder missing");
    PaymentOrderDO paymentOrderDO = locked.payment();
    PaymentAccount paymentAccount =
        new PaymentAccount(
            paymentOrderDO.channelAppId(),
            paymentOrderDO.channelMchId(),
            paymentOrderDO.channelNotifyUrl());
    String notifyUrl =
        paymentOrderDO
            .channelNotifyUrl()
            .replace("/notifications/payment", "/notifications/refund");
    return new Ticket(
        refundOrderDO,
        new PaymentGateway.RefundRequest(
            paymentAccount,
            channelSuborderDOSuborder.subMchidSnapshot(),
            refundOrderDO.channelOutTradeNo(),
            refundOrderDO.channelTransactionId(),
            refundOrderDO.channelOutRefundNo(),
            channelSuborderDOSuborder.paidAmountFen(),
            refundOrderDO.refundAmountFen(),
            refundOrderDO.refundReason(),
            notifyUrl),
        refundOrderDO.version() + 1);
  }

  /** 仅调度未知结果，不能据本地失败抹掉原退款额度。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void finish(Ticket ticket, String error) {
    roots(ticket.refund().id());
    int attempt = ticket.refund().attemptCount() + 1;
    int delay =
        error == null ? 10 : attempt == 1 ? 10 : attempt == 2 ? 30 : attempt == 3 ? 60 : 300;
    refundMapper.finishRefundAttempt(
        new PaymentWrites.Finish(
            ticket.refund().id(),
            ticket.claimedVersion(),
            null,
            error,
            now(clock),
            now(clock).plusSeconds(delay)));
  }

  /** 到账事实按原退款号只确认一次；随后重算累计值而不是重复累加。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public String observe(String refundId, PaymentGateway.RefundFact refundFact) {
    TradingRoots.Locked locked = roots(refundId);
    boolean custom=locked.batch()==null;
    if(!custom)tradingReservations.lockExisting(locked.batch().id());
    CouponLifecycle.Locked lockedCoupon =
        custom?null:couponLifecycle.lock(locked.batch().buyerUserId(), locked.batch().id());
    RefundOrderDO refundOrderDO = refundMapper.selectRefundById(refundId, true);
    RefundFactValidation.verify(
        refundMapper.selectRefundSourceByChannelNo(refundOrderDO.channelOutRefundNo()), refundFact);
    if (refundFact.notFound() || TradingState.success.name().equals(refundOrderDO.refundStatus()))
      return custom?null:locked.batch().id();
    LocalDateTime at = now(clock);
    String status =
        "SUCCESS".equals(refundFact.state())
            ? TradingState.success.name()
            : "PROCESSING".equals(refundFact.state())
                ? TradingState.processing.name()
                : TradingState.failed.name();
    String error =
        TradingState.failed.name().equals(status) ? "REFUND_" + refundFact.state() : null;
    refundMapper.mergeRefundFact(
        new RefundWrites.Fact(
            refundId,
            status,
            refundFact.refundId(),
            refundFact.successAt() == null
                ? null
                : LocalDateTime.ofInstant(refundFact.successAt(), ZONE),
            error,
            at,
            TradingState.success.name().equals(status)
                ? null
                : at.plusSeconds(error == null ? 10 : 300)));
    refundMapper.refreshSuborderRefunds(refundOrderDO.paymentChannelSuborderId(), at);
    refundMapper.refreshPaymentRefunds(refundOrderDO.paymentOrderId(), at);
    if(custom){
      if(!"custom_order".equals(refundOrderDO.bizOrderType()) || !locked.orderIds().contains(refundOrderDO.bizOrderId()))throw new IllegalStateException("定制退款归属不符");
      if("success".equals(status)){customOrderMapper.refreshRefunded(refundOrderDO.bizOrderId(),at);customAfterSaleCompletion.reconcile(refundOrderDO.bizOrderId(),at);}
      return null;
    }
    if (refundOrderDO.rentalOrderId() != null) {
      refundMapper.refreshOrderRefunds(refundOrderDO.rentalOrderId(), at);
      refundMapper.refreshFreightRefunds(refundOrderDO.rentalOrderId(), at);
      if (TradingRefundType.deposit_release.name().equals(refundOrderDO.refundType())
          || TradingRefundType.freight_pool_release.name().equals(refundOrderDO.refundType()))
        normalOrderFunds.reconcile(refundOrderDO.rentalOrderId());
    }
    String parent=issuePlanMapper.selectRefundParent(refundId);
    if (parent!=null) {
      issuePlans.reconcileLocked(issuePlanMapper.actionById(parent),com.mujian.backend.common.support.SystemIdentity.SYSTEM_USER_ID,at);
      return locked.batch().id();
    }
    if(locked.payment().checkoutBatchId()==null) {
      if(TradingState.success.name().equals(status)) extensionRequestMapper.markRefunded(refundId,at);
      return null;
    }
    couponLifecycle.reconcile(
        lockedCoupon,
        paymentOrderMapper.selectSubordersByPayment(refundOrderDO.paymentOrderId(), false),
        !locked.orderIds().isEmpty(),
        refundMapper.hasBatchDeductions(locked.batch().id()));
    return locked.batch().id();
  }
}
