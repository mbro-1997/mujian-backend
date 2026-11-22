package com.mujian.backend.payment.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.order.mapper.CheckoutBatchMapper;
import com.mujian.backend.order.mapper.CheckoutMapper;
import com.mujian.backend.order.policy.TradingTime;
import com.mujian.backend.order.service.TradingRoots;
import com.mujian.backend.payment.domain.*;
import com.mujian.backend.payment.dto.PaymentWrites;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import com.mujian.backend.payment.policy.PaymentFactValidation;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 外部调用前后的独立持久事务；成单故障不回滚已确认的渠道实收。 */
@Service
public class PaymentWorkTransaction {
  public record Ticket(
      PaymentOrderDO payment,
      List<ChannelSuborderDO> suborders,
      int claimedVersion,
      boolean firstAttempt,
      boolean refundBatch) {}

  public record Observation(
      String batchId, boolean allPaid, boolean allClosed, boolean refundBatch) {}

  private final com.mujian.backend.custom.service.CustomFunds customFunds;
  private final TradingRoots tradingRoots;
  private final PaymentOrderMapper paymentOrderMapper;
  private final CheckoutBatchMapper checkoutBatchMapper;
  private final Clock clock;
  private final CheckoutMapper checkoutMapper;
  private final com.mujian.backend.dispute.service.Receivables receivables;
  private final com.mujian.backend.order.service.OrderExtensions extensions;

  /** 全部支付变化经过同一来源锁序和单向资金核验。 */
  public PaymentWorkTransaction(
      TradingRoots tradingRoots,
      PaymentOrderMapper paymentOrderMapper,
      CheckoutBatchMapper checkoutBatchMapper,
      Clock clock, CheckoutMapper checkoutMapper,com.mujian.backend.order.service.OrderExtensions extensions, com.mujian.backend.dispute.service.Receivables receivables,com.mujian.backend.custom.service.CustomFunds customFunds) {
    this.customFunds=customFunds;
    this.checkoutMapper=checkoutMapper;
    this.receivables=receivables;
    this.extensions=extensions;
    this.tradingRoots = tradingRoots;
    this.paymentOrderMapper = paymentOrderMapper;
    this.checkoutBatchMapper = checkoutBatchMapper;
    this.clock = clock;
  }

  /** 在独立事务中归集渠道付款结果。 */
  private void observeStandalone(String payment,ChannelSuborderDO channelSuborderDO,LocalDateTime at){
    if(!customFunds.observe(payment,channelSuborderDO,at) && !receivables.observe(payment,channelSuborderDO,at))extensions.observePayment(payment,channelSuborderDO,at);
  }

  /** 恢复租约按数据库CAS发放，进程死亡后下一执行者等待短租约到期接续。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Ticket claim(String batchId) {
    TradingRoots.Locked locked = tradingRoots.lockFunding(batchId);
    PaymentOrderDO paymentOrderDO = locked.payment();
    LocalDateTime at = now(clock);
    if (paymentOrderDO.nextAttemptAt() != null && paymentOrderDO.nextAttemptAt().isAfter(at))
      return null;
    if (paymentOrderMapper.claimAttempt(
            new PaymentWrites.Attempt(
                paymentOrderDO.id(), paymentOrderDO.version(), at, at.plusSeconds(30)))
        != 1) return null;
    return new Ticket(
        paymentOrderDO,
        locked.suborders(),
        paymentOrderDO.version() + 1,
        paymentOrderDO.attemptCount() == 0,
        locked.batch()!=null && TradingRecoveryMode.refund_batch.name().equals(locked.batch().recoveryMode()));
  }

  /** 每次创建前重读持久代次、关单意图和原截止，失效执行者不能再次发起原号创建。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public boolean canDispatchCreate(Ticket ticket) {
    TradingRoots.Locked locked = tradingRoots.lockFunding(ticket.payment().fundingKey());
    PaymentOrderDO paymentOrderDO = locked.payment();
    LocalDateTime at = now(clock);
    return paymentOrderDO.attemptCount() == ticket.payment().attemptCount() + 1
        && paymentOrderDO.nextAttemptAt() != null
        && !paymentOrderDO.nextAttemptAt().isBefore(at.plusSeconds(5))
        && paymentOrderDO.closeRequestedAt() == null
        && paymentOrderDO.channelPrepayId() == null
        && paymentOrderDO.paidAmountFen() == 0
        && Set.of(TradingState.pending.name(), TradingState.paying.name())
            .contains(paymentOrderDO.paymentStatus())
        && (paymentOrderDO.checkoutBatchId()==null || TradingState.pending_payment.name().equals(locked.batch().batchStatus()))
        && (paymentOrderDO.checkoutBatchId()==null || !checkoutMapper.existsOutstandingCompensation(paymentOrderDO.buyerUserId()))
        && TradingTime.canCreatePayment(paymentOrderDO.expireAt(), at);
  }

  /** 原截止及一轮租约之后，前后查无记录且关单得到明确响应才允许关闭未建单来源。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public boolean closeConfirmedAbsent(Ticket ticket, PaymentGateway.PaymentFact paymentFact) {
    TradingRoots.Locked locked = tradingRoots.lockFunding(ticket.payment().fundingKey());
    PaymentOrderDO paymentOrderDO = locked.payment();
    PaymentFactValidation.verify(paymentOrderDO, locked.suborders(), paymentFact, true);
    LocalDateTime at = now(clock);
    if (!paymentFact.notFound()
        || at.isBefore(paymentOrderDO.expireAt().plusSeconds(30))
        || paymentOrderDO.closeRequestedAt() == null
        || paymentOrderDO.attemptCount() != ticket.payment().attemptCount() + 1
        || paymentOrderDO.version() != ticket.claimedVersion()
        || paymentOrderDO.nextAttemptAt() == null
        || !at.isBefore(paymentOrderDO.nextAttemptAt())
        || paymentOrderDO.channelPrepayId() != null
        || paymentOrderDO.paidAmountFen() != 0
        || paymentOrderDO.checkoutBatchId()!=null && !locked.orderIds().isEmpty()) return false;
    for (ChannelSuborderDO channelSuborderDO : locked.suborders()) {
      if (channelSuborderDO.paidAmountFen() != 0
          || channelSuborderDO.channelSubTransactionId() != null) return false;
    }
    for (ChannelSuborderDO channelSuborderDO : locked.suborders())
      paymentOrderMapper.confirmSuborderClosed(channelSuborderDO.id(), at);
    paymentOrderMapper.updatePaymentSummary(
        new PaymentWrites.Summary(
            paymentOrderDO.id(), TradingState.closed.name(), 0, null, "CLOSED", at));
    if (paymentOrderDO.checkoutBatchId()==null) observeStandalone(paymentOrderDO.id(),paymentOrderMapper.selectSubordersByPayment(paymentOrderDO.id(),false).get(0),at);
    return true;
  }

  /** 原支付从未发起渠道请求时可安全关闭；已有尝试仍必须查关原号。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void closeUnattempted(Ticket ticket) {
    TradingRoots.Locked locked = tradingRoots.lockFunding(ticket.payment().fundingKey());
    if (!ticket.firstAttempt()
        || locked.payment().version() != ticket.claimedVersion()
        || locked.payment().channelPrepayId() != null
        || locked.payment().paidAmountFen() != 0)
      throw new IllegalStateException("payment is not unattempted");
    for (ChannelSuborderDO channelSuborderDOSuborder : locked.suborders())
      paymentOrderMapper.confirmSuborderClosed(channelSuborderDOSuborder.id(), now(clock));
    paymentOrderMapper.updatePaymentSummary(
        new PaymentWrites.Summary(
            locked.payment().id(), TradingState.closed.name(), 0, null, "CLOSED", now(clock)));
    if(locked.payment().checkoutBatchId()==null) observeStandalone(locked.payment().id(),paymentOrderMapper.selectSubordersByPayment(locked.payment().id(),false).get(0),now(clock));
  }

  /** 调用结果调度不得覆盖回调推进后的新版本；付款号和到期时刻保持原值。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void finish(Ticket ticket, String prepayId, String error) {
    TradingRoots.Locked locked = tradingRoots.lockFunding(ticket.payment().fundingKey());
    if (prepayId != null)
      paymentOrderMapper.rememberPrepay(locked.payment().id(), prepayId, now(clock));
    paymentOrderMapper.finishAttempt(
        new PaymentWrites.Finish(
            locked.payment().id(),
            ticket.claimedVersion(),
            prepayId,
            error,
            now(clock),
            now(clock)
                .plusSeconds(
                    error == null
                        ? 15
                        : ticket.payment().attemptCount() == 0
                            ? 10
                            : ticket.payment().attemptCount() == 1
                                ? 30
                                : ticket.payment().attemptCount() == 2 ? 60 : 300)));
  }

  /** 取消/到期都先保存关单意图，明确终止新调起后才请求渠道。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void closeIntent(String batchId, String reason) {
    TradingRoots.Locked locked = tradingRoots.lockFunding(batchId);
    paymentOrderMapper.requestClose(locked.payment().id(), reason, now(clock));
  }

  /** 已验证通知与主动查单在这里合并，未付不能回退成功，晚付不能重获旧guard。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Observation observe(
      String batchId, PaymentGateway.PaymentFact paymentFact, boolean complete) {
    TradingRoots.Locked locked = tradingRoots.lockFunding(batchId);
    PaymentFactValidation.verify(locked.payment(), locked.suborders(), paymentFact, complete);
    LocalDateTime at = now(clock);
    if (paymentFact.notFound())
      return new Observation(
          batchId,
          false,
          false,
          locked.batch()!=null && TradingRecoveryMode.refund_batch.name().equals(locked.batch().recoveryMode()));
    Map<String, ChannelSuborderDO> channelSuborderDOMapByNumber = new HashMap<>();
    for (ChannelSuborderDO channelSuborderDOSuborder : locked.suborders())
      channelSuborderDOMapByNumber.put(
          channelSuborderDOSuborder.channelSubOrderNo(), channelSuborderDOSuborder);
    for (PaymentGateway.SuborderFact suborderFactSubfact : paymentFact.suborders()) {
      ChannelSuborderDO channelSuborderDOSuborder =
          channelSuborderDOMapByNumber.get(suborderFactSubfact.outTradeNo());
      if (Set.of("SUCCESS", "REFUND").contains(suborderFactSubfact.state()))
        paymentOrderMapper.confirmSuborderPaid(
            new PaymentWrites.SuborderFact(
                channelSuborderDOSuborder.id(),
                suborderFactSubfact.transactionId(),
                suborderFactSubfact.state(),
                suborderFactSubfact.totalFen(),
                LocalDateTime.ofInstant(suborderFactSubfact.paidAt(), ZONE),
                at));
      else if ("CLOSED".equals(suborderFactSubfact.state()))
        paymentOrderMapper.confirmSuborderClosed(channelSuborderDOSuborder.id(), at);
    }
    List<ChannelSuborderDO> channelSuborderDOListSuborders =
        paymentOrderMapper.selectSubordersByPayment(locked.payment().id(), false);
    int paid = 0, count = 0, closed = 0;
    LocalDateTime paidAt = null;
    boolean afterExpiry = false;
    for (ChannelSuborderDO channelSuborderDOSuborder : channelSuborderDOListSuborders) {
      paid = Math.addExact(paid, channelSuborderDOSuborder.paidAmountFen());
      if (TradingState.success.name().equals(channelSuborderDOSuborder.paymentStatus())) {
        count++;
        if (paidAt == null || channelSuborderDOSuborder.paidAt().isAfter(paidAt))
          paidAt = channelSuborderDOSuborder.paidAt();
        if (channelSuborderDOSuborder.paidAt().isAfter(locked.payment().expireAt()))
          afterExpiry = true;
      }
      if (TradingState.closed.name().equals(channelSuborderDOSuborder.paymentStatus())) closed++;
    }
    boolean allPaid = count == channelSuborderDOListSuborders.size(),
        allClosed = closed == channelSuborderDOListSuborders.size();
    String status =
        allPaid
            ? TradingState.success.name()
            : paid > 0
                ? TradingState.partial_received.name()
                : allClosed ? TradingState.closed.name() : locked.payment().paymentStatus();
    paymentOrderMapper.updatePaymentSummary(
        new PaymentWrites.Summary(
            locked.payment().id(),
            status,
            paid,
            paidAt,
            allPaid ? "SUCCESS" : allClosed ? "CLOSED" : "CHECKED",
            at));
    if (locked.payment().checkoutBatchId()==null) {
      observeStandalone(locked.payment().id(),channelSuborderDOListSuborders.get(0),at);
      return new Observation(batchId,allPaid,allClosed,false);
    }
    boolean refund =
        locked.batch()!=null && TradingRecoveryMode.refund_batch.name().equals(locked.batch().recoveryMode())
            || paid > 0
                && locked.orderIds().isEmpty()
                && (locked.batch().guardReleasedAt() != null
                    || afterExpiry
                    || complete && !allPaid);
    if (paid > 0
        && (allPaid || refund)
        && !TradingState.paid.name().equals(locked.batch().batchStatus()))
      checkoutBatchMapper.markRecovery(
          batchId,
          refund
              ? TradingRecoveryMode.refund_batch.name()
              : TradingRecoveryMode.recover_orders.name(),
          refund ? "已确认实收需整批退款" : "已确认全付，待原子成单",
          at);

    return new Observation(batchId, allPaid, allClosed, refund);
  }
}
