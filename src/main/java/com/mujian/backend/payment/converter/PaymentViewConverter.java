package com.mujian.backend.payment.converter;

import static com.mujian.backend.common.support.D2Values.time;

import com.mujian.backend.order.vo.BatchViews;
import com.mujian.backend.payment.domain.PaymentOrderDO;

/** 资金摘要纯映射，不查询或改变支付状态。 */
public final class PaymentViewConverter {
  /** 禁止实例化此静态结构容器。 */
  private PaymentViewConverter() {}

  /** 返回原支付事实与截止时间，供批次和管理追溯复用。 */
  public static BatchViews.Payment payment(PaymentOrderDO paymentOrderDO) {
    return new BatchViews.Payment(
        paymentOrderDO.id(),
        paymentOrderDO.paymentNo(),
        paymentOrderDO.paymentStatus(),
        paymentOrderDO.payableAmountFen(),
        paymentOrderDO.paidAmountFen(),
        paymentOrderDO.refundedAmountFen(),
        time(paymentOrderDO.expireAt()),
        time(paymentOrderDO.paidAt()),
        time(paymentOrderDO.lastCheckedAt()));
  }
}
