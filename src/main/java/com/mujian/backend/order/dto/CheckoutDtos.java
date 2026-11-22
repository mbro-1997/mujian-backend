package com.mujian.backend.order.dto;

import java.util.List;

/** 结算输入只引用本人清单、地址和券，不接收权威价格或商户。 */
public final class CheckoutDtos {
  /** 禁止实例化此静态结构容器。 */
  private CheckoutDtos() {}

  public record DeliveryChoice(
      String factoryId, String outboundService, String returnService, String buyerRemark) {}

  public record QuoteInput(
      String checklistId,
      int expectedVersion,
      String addressId,
      int addressVersion,
      String couponId,
      List<DeliveryChoice> deliveries) {}

  public record Submit(String quoteToken) {}

  public record ExpectedVersion(int expectedVersion) {}
}
