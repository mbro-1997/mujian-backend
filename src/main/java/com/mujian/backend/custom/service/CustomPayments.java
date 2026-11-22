package com.mujian.backend.custom.service;

import com.mujian.backend.custom.mapper.CustomOrderMapper;

import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.order.service.ExtensionPayment;
import com.mujian.backend.order.vo.BatchViews;
import com.mujian.backend.payment.service.PaymentExecution;
import org.springframework.stereotype.Service;

@Service
public class CustomPayments {
  public record Launch(
      String orderId, BatchViews.Payment payment, PaymentGateway.Parameters parameters) {}

  private final PaymentExecution execution;
  private final ExtensionPayment reader;
  private final CustomPurchase customPurchase;
  private final FactoryAccess factoryAccess;

  public CustomPayments(
      PaymentExecution execution,
      ExtensionPayment reader,
      CustomPurchase customPurchase,
      FactoryAccess factoryAccess) {
    this.execution = execution;
    this.reader = reader;
    this.customPurchase = customPurchase;
    this.factoryAccess = factoryAccess;
  }

  /** 使用原支付记录准备渠道付款参数。 */
  public Launch launch(String actor, CustomPurchase.Attempt attempt) {
    boolean active = "active".equals(factoryAccess.context(actor, false).userStatus());
    if (active) execution.execute(attempt.paymentId());
    return read(attempt.orderId(), attempt.paymentId(), active);
  }

  /** 读取当前付款状态。 */
  public Launch current(String actor, String id) {
    CustomOrderMapper.Order customOrderOrder = customPurchase.owned(actor, false, id, false);
    return customOrderOrder.paymentId() == null
        ? null
        : read(id, customOrderOrder.paymentId(), "active".equals(factoryAccess.context(actor, false).userStatus()));
  }

  /** 校验访问范围后返回业务详情。 */
  public Launch read(String order, String payment, boolean parameters) {
    ExtensionPayment.Launch result = reader.parameters(order, payment, parameters);
    return new Launch(order, result.payment(), result.parameters());
  }
}
