package com.mujian.backend.payment.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.order.policy.TradingTime;
import com.mujian.backend.order.service.OrderFormation;
import com.mujian.backend.payment.domain.ChannelSuborderDO;
import com.mujian.backend.payment.domain.PaymentOrderDO;
import com.mujian.backend.payment.dto.PaymentAccount;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;

/** 提交、主动恢复、通知恢复共用执行入口；外部请求不占业务事务锁。 */
@Service
public class PaymentExecution {
  private final PaymentWorkTransaction paymentWorkTransaction;
  private final PaymentOrderMapper paymentOrderMapper;
  private final PaymentGateway paymentGateway;
  private final OrderFormation orderFormation;
  private final UnformedBatchRefunds unformedBatchRefunds;
  private final Clock clock;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public PaymentExecution(
      PaymentWorkTransaction paymentWorkTransaction,
      PaymentOrderMapper paymentOrderMapper,
      PaymentGateway paymentGateway,
      OrderFormation orderFormation,
      UnformedBatchRefunds unformedBatchRefunds,
      Clock clock) {
    this.paymentWorkTransaction = paymentWorkTransaction;
    this.paymentOrderMapper = paymentOrderMapper;
    this.paymentGateway = paymentGateway;
    this.orderFormation = orderFormation;
    this.unformedBatchRefunds = unformedBatchRefunds;
    this.clock = clock;
  }

  /** 从数据库取原号、原商户和原截止；同一短租约只执行一轮。 */
  public void execute(String batchId) {
    PaymentOrderDO paymentOrderDOHint = paymentOrderMapper.selectPaymentByBatch(batchId, false);
    if (paymentOrderDOHint == null) {
      paymentOrderDOHint=paymentOrderMapper.selectPaymentById(batchId,false);
      if(paymentOrderDOHint==null || paymentOrderDOHint.checkoutBatchId()!=null)return;
    }
    boolean initial=paymentOrderDOHint.checkoutBatchId()!=null;
    if (!now(clock).isBefore(paymentOrderDOHint.expireAt())
        && paymentOrderDOHint.closeRequestedAt() == null
        && !Set.of(TradingState.success.name(), TradingState.closed.name())
            .contains(paymentOrderDOHint.paymentStatus()))
      paymentWorkTransaction.closeIntent(batchId, TradingState.expired.name());
    PaymentWorkTransaction.Ticket ticket = paymentWorkTransaction.claim(batchId);
    if (ticket == null) return;
    PaymentOrderDO paymentOrderDO = ticket.payment();
    try {
      if (TradingState.success.name().equals(paymentOrderDO.paymentStatus())) {
        if (initial) {
          if (ticket.refundBatch()) unformedBatchRefunds.reconcile(batchId);
          else orderFormation.form(batchId);
        }
        paymentWorkTransaction.finish(ticket, null, null);
        return;
      }
      if (TradingState.closed.name().equals(paymentOrderDO.paymentStatus())) {
        if(initial) unformedBatchRefunds.reconcile(batchId);
        paymentWorkTransaction.finish(ticket, null, null);
        return;
      }
      boolean close =
          paymentOrderDO.closeRequestedAt() != null
              || ticket.refundBatch()
              || !now(clock).isBefore(paymentOrderDO.expireAt());
      if (ticket.firstAttempt()
          && (close || !TradingTime.canCreatePayment(paymentOrderDO.expireAt(), now(clock)))) {
        paymentWorkTransaction.closeUnattempted(ticket);
        if(initial) unformedBatchRefunds.reconcile(batchId);
        return;
      }
      PaymentAccount paymentAccount =
          new PaymentAccount(
              paymentOrderDO.channelAppId(),
              paymentOrderDO.channelMchId(),
              paymentOrderDO.channelNotifyUrl());
      List<PaymentGateway.Suborder> suborderListSuborders = new ArrayList<>();
      for (ChannelSuborderDO channelSuborderDO : ticket.suborders())
        suborderListSuborders.add(
            new PaymentGateway.Suborder(
                channelSuborderDO.channelSubOrderNo(),
                channelSuborderDO.subMchidSnapshot(),
                channelSuborderDO.channelDescription(),
                channelSuborderDO.channelSuborderAmountFen()));
      PaymentGateway.Reference reference =
          new PaymentGateway.Reference(
              paymentAccount, paymentOrderDO.channelCombineOutTradeNo(), suborderListSuborders);
      if (ticket.firstAttempt()) {
        if (!paymentWorkTransaction.canDispatchCreate(ticket)) return;
        String prepay =
            paymentGateway.create(
                new PaymentGateway.Create(
                    paymentAccount,
                    paymentOrderDO.channelCombineOutTradeNo(),
                    paymentOrderDO.payerOpenid(),
                    paymentOrderDO.expireAt(),
                    suborderListSuborders));
        paymentWorkTransaction.finish(ticket, prepay, null);
        return;
      }
      PaymentGateway.PaymentFact paymentFact = paymentGateway.query(reference);
      PaymentWorkTransaction.Observation observation =
          paymentWorkTransaction.observe(batchId, paymentFact, true);
      if (observation.allPaid() && !observation.refundBatch()) {
        if(initial) orderFormation.form(batchId);
        return;
      }
      close = close || observation.refundBatch();
      if (close && !observation.allClosed() && !observation.allPaid()) {
        boolean absentBeforeClose = paymentFact.notFound();
        PaymentCloseResult paymentCloseResult = paymentGateway.close(reference);
        paymentFact = paymentGateway.query(reference);
        observation = paymentWorkTransaction.observe(batchId, paymentFact, true);
        if (absentBeforeClose
            && paymentFact.notFound()
            && Set.of(PaymentCloseResult.accepted, PaymentCloseResult.not_found)
                .contains(paymentCloseResult)
            && paymentWorkTransaction.closeConfirmedAbsent(ticket, paymentFact)) {
          if(initial) unformedBatchRefunds.reconcile(batchId);
          return;
        }
        if (observation.allPaid() && !observation.refundBatch()) {
          if(initial) orderFormation.form(batchId);
          return;
        }
      }
      if (observation.refundBatch() || observation.allClosed())
        if(initial) unformedBatchRefunds.reconcile(batchId);
      boolean allNotPaid = true;
      for (PaymentGateway.SuborderFact suborderFact : paymentFact.suborders())
        if (!"NOTPAY".equals(suborderFact.state())) allNotPaid = false;
      if ((paymentFact.notFound() || paymentOrderDO.channelPrepayId() == null && allNotPaid)
          && !close
          && TradingTime.canCreatePayment(paymentOrderDO.expireAt(), now(clock))
          && paymentWorkTransaction.canDispatchCreate(ticket)) {
        String prepay =
            paymentGateway.create(
                new PaymentGateway.Create(
                    paymentAccount,
                    paymentOrderDO.channelCombineOutTradeNo(),
                    paymentOrderDO.payerOpenid(),
                    paymentOrderDO.expireAt(),
                    suborderListSuborders));
        paymentWorkTransaction.finish(ticket, prepay, null);
      } else paymentWorkTransaction.finish(ticket, null, null);
    } catch (ApiFailure failure) {
      paymentWorkTransaction.finish(ticket, null, failure.reason);
    }
  }
}
