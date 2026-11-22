package com.mujian.backend.payment.policy;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.payment.domain.*;
import java.util.*;

/** 通知与查单共享来源核验；总额相同不能替代每个原厂子单匹配。 */
public final class PaymentFactValidation {
  /** 禁止实例化此静态结构容器。 */
  private PaymentFactValidation() {}

  /** 查询完整原集合，通知可逐次确认已知子单，未出现的子单绝不推断已付。 */
  public static void verify(
      PaymentOrderDO paymentOrderDO,
      List<ChannelSuborderDO> channelSuborderDOList,
      PaymentGateway.PaymentFact paymentFact,
      boolean complete) {
    if (!Objects.equals(paymentOrderDO.channelAppId(), paymentFact.appId())
        || !Objects.equals(paymentOrderDO.channelMchId(), paymentFact.mchid())
        || !Objects.equals(paymentOrderDO.channelCombineOutTradeNo(), paymentFact.outTradeNo()))
      throw ApiFailure.invalid();
    if (paymentFact.notFound()) {
      if (!paymentFact.suborders().isEmpty()) throw ApiFailure.invalid();
      return;
    }
    Map<String, ChannelSuborderDO> channelSuborderDOMapExpected = new HashMap<>();
    for (ChannelSuborderDO channelSuborderDO : channelSuborderDOList)
      channelSuborderDOMapExpected.put(channelSuborderDO.channelSubOrderNo(), channelSuborderDO);
    if (complete && channelSuborderDOMapExpected.size() != paymentFact.suborders().size())
      throw ApiFailure.invalid();
    Set<String> seen = new HashSet<>();
    for (PaymentGateway.SuborderFact suborderFact : paymentFact.suborders()) {
      ChannelSuborderDO channelSuborderDOExpectedSuborder =
          channelSuborderDOMapExpected.get(suborderFact.outTradeNo());
      if (channelSuborderDOExpectedSuborder == null
          || !seen.add(suborderFact.outTradeNo())
          || !Objects.equals(paymentOrderDO.channelMchId(), suborderFact.mchid())
          || !Objects.equals(
              channelSuborderDOExpectedSuborder.subMchidSnapshot(), suborderFact.subMchid())
          || channelSuborderDOExpectedSuborder.channelSuborderAmountFen() != suborderFact.totalFen()
          || !"CNY".equals(suborderFact.currency())
          || suborderFact.payerCurrency() != null && !"CNY".equals(suborderFact.payerCurrency()))
        throw ApiFailure.invalid();
      if (Set.of("SUCCESS", "REFUND").contains(suborderFact.state())
          && (suborderFact.paidAt() == null
              || suborderFact.transactionId() == null
              || suborderFact.transactionId().isBlank()
              || suborderFact.payerFen() != null
                  && suborderFact.payerFen() != suborderFact.totalFen()))
        throw ApiFailure.invalid();
      if (channelSuborderDOExpectedSuborder.channelSubTransactionId() != null
          && !Objects.equals(
              channelSuborderDOExpectedSuborder.channelSubTransactionId(),
              suborderFact.transactionId())
          && Set.of("SUCCESS", "REFUND").contains(suborderFact.state())) throw ApiFailure.invalid();
    }
  }
}
