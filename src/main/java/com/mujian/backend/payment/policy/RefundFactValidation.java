package com.mujian.backend.payment.policy;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.payment.projection.RefundSourceProjection;
import java.util.*;

/** 通知和主动查询共用原交易身份、原退款号、金额和币种校验。 */
public final class RefundFactValidation {
  /** 禁止实例化此静态结构容器。 */
  private RefundFactValidation() {}

  /** 核对原商户、原交易、退款金额及币种，再接纳渠道事实。 */
  public static void verify(
      RefundSourceProjection refundSourceProjectionSource, PaymentGateway.RefundFact refundFact) {
    if (refundSourceProjectionSource == null
        || !Objects.equals(refundSourceProjectionSource.channelMchId(), refundFact.mchid())
        || !Objects.equals(refundSourceProjectionSource.subMchidSnapshot(), refundFact.subMchid())
        || !Objects.equals(refundSourceProjectionSource.channelOutRefundNo(), refundFact.refundNo())
        || !Objects.equals(
            refundSourceProjectionSource.channelOutTradeNo(), refundFact.outTradeNo())
        || !Objects.equals(
            refundSourceProjectionSource.channelTransactionId(), refundFact.transactionId()))
      throw ApiFailure.invalid();
    if (refundFact.notFound()) return;
    if (refundSourceProjectionSource.refundAmountFen() != refundFact.refundFen()
        || refundFact.totalFen() == null
        || refundSourceProjectionSource.channelSuborderAmountFen() != refundFact.totalFen()
        || refundFact.currency() != null && !"CNY".equals(refundFact.currency())
        || !Set.of("SUCCESS", "PROCESSING", "CLOSED", "ABNORMAL").contains(refundFact.state())
        || refundFact.refundId() == null
        || "SUCCESS".equals(refundFact.state()) && refundFact.successAt() == null)
      throw ApiFailure.invalid();
  }
}
