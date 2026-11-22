package com.mujian.backend.custom.service;

import com.mujian.backend.common.enums.BusinessFailureReason;
import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.custom.service.CustomRequirements.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.custom.mapper.CustomOrderMapper;
import com.mujian.backend.custom.mapper.CustomOrderMapper.Order;
import com.mujian.backend.payment.domain.*;
import com.mujian.backend.payment.mapper.*;
import com.mujian.backend.payment.policy.RefundIdentity;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CustomFunds {
  private final CustomOrderMapper customOrderMapper;
  private final PaymentOrderMapper paymentOrderMapper;
  private final RefundMapper refundMapper;
  private final Clock clock;

  public CustomFunds(
      CustomOrderMapper customOrderMapper, PaymentOrderMapper paymentOrderMapper, RefundMapper refundMapper, Clock clock) {
    this.customOrderMapper = customOrderMapper;
    this.paymentOrderMapper = paymentOrderMapper;
    this.refundMapper = refundMapper;
    this.clock = clock;
  }

  /** 在事务中为本次操作预留额度。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public String reserve(
      Order order,
      ChannelSuborderDO sub,
      String actor,
      String sourceType,
      String sourceId,
      String type,
      String reason,
      int goods,
      int freight,
      LocalDateTime at) {
    if (!order.id().equals(customOrderMapper.fundingOrder(sub.paymentOrderId())))
      throw new IllegalStateException("定制退款原子单错误");
    String key = RefundIdentity.key(sourceType, sourceId, sub.id(), type);
    RefundOrderDO refundOrderDO = refundMapper.selectRefundBySourceKey(key);
    if (refundOrderDO != null) return refundOrderDO.id();
    CustomOrderMapper.Exposure available = customOrderMapper.selectRefundableCustomAmountsBySuborder(sub.id());
    if (available == null
        || goods < 0
        || freight < 0
        || goods > available.goods()
        || freight > available.freight()
        || (long) goods + freight <= 0) throw conflict(BusinessFailureReason.REFUND_ALLOCATION_EXCEEDED.name());
    String id = Ids.next();
    if (customOrderMapper.insertRefundOrder(id, key, order.id(), sub.id(), actor, type, reason, goods, freight, at) != 1)
      throw new IllegalStateException("定制退款来源缺失");
    return id;
  }

  /** 合并外部结果并推进本地状态。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean observe(String payment, ChannelSuborderDO channelSuborderDO, LocalDateTime at) {
    String id = customOrderMapper.fundingOrder(payment);
    if (id == null) return false;
    Order customOrderOrder = customOrderMapper.selectCustomOrderById(id, true);
    if ("closed".equals(channelSuborderDO.paymentStatus())) {
      closeIfExpired(customOrderOrder, payment, at);
      return true;
    }
    if (!"success".equals(channelSuborderDO.paymentStatus())) return true;
    if (channelSuborderDO.paidAmountFen() != customOrderOrder.totalFen()) throw new IllegalStateException("定制实收与原报价不符");
    if (customOrderMapper.insertPaymentAllocation(Ids.next(), id, payment, channelSuborderDO.id(), at) == 0) return true;
    PaymentOrderDO paymentOrderDO = paymentOrderMapper.selectPaymentById(payment, false);
    if (customOrderOrder.paid() == 0 && payment.equals(customOrderOrder.paymentId()) && !channelSuborderDO.paidAt().isAfter(paymentOrderDO.expireAt())) {
      if (customOrderMapper.updateCustomOrderPaid(id, payment, customOrderOrder.goods(), customOrderOrder.freight(), at) != 1)
        throw new IllegalStateException("定制实收重复推进");
    } else {
      reserve(
          customOrderOrder,
          channelSuborderDO,
          SystemIdentity.SYSTEM_USER_ID,
          "custom_order",
          id,
          "platform_adjustment",
          "窗外或重复实收原路退回",
          customOrderOrder.goods(),
          customOrderOrder.freight(),
          at);
      if (payment.equals(customOrderOrder.paymentId()) && customOrderOrder.paid() == 0 && !at.isBefore(customOrderOrder.quoteDeadline()))
        customOrderMapper.updateCustomOrderState(id, "payment_closed", at);
    }
    return true;
  }

  /** 判断本次付款是否已结束且没有有效收款。 */
  static boolean endedWithoutValidPayment(PaymentOrderDO payment) {
    return "closed".equals(payment.paymentStatus())
        || ("success".equals(payment.paymentStatus())
            && payment.paidAt() != null
            && payment.paidAt().isAfter(payment.expireAt()));
  }

  /** 校验到期事实后关闭仍未付款的记录。 */
  private void closeIfExpired(Order o, String payment, LocalDateTime at) {
    if (o.state().equals("pending_payment")
        && payment.equals(o.paymentId())
        && !at.isBefore(o.quoteDeadline())) customOrderMapper.updateCustomOrderState(o.id(), "payment_closed", at);
  }

  /** 处理已到期的未付款记录。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void closeExpired(String id) {
    CustomOrderMapper.Order customOrderOrder = customOrderMapper.selectCustomOrderById(id, true);
    if (customOrderOrder != null && endedWithoutValidPayment(paymentOrderMapper.selectPaymentById(customOrderOrder.paymentId(), true)))
      closeIfExpired(customOrderOrder, customOrderOrder.paymentId(), now(clock));
  }
}
