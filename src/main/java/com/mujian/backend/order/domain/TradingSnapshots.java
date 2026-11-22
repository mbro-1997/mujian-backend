package com.mujian.backend.order.domain;

import java.util.List;

/** 资金历史JSON的显式白名单，展示不依赖当前地址簿或当前商品资料。 */
public final class TradingSnapshots {
  /** 禁止实例化此静态结构容器。 */
  private TradingSnapshots() {}

  public record Rule(
      int schemaVersion,
      String currency,
      String creditRule,
      String creditRuleVersion,
      boolean creditEnabled,
      int waivedDepositFen,
      String cancellationRule,
      String cancellationRuleVersion,
      String cancellationDocumentId,
      String cancellationContentHash,
      List<Integer> cancellationRatesBps,
      int openingDaysBefore,
      int closingDaysBefore,
      String closingTime,
      int paymentSeconds,
      String couponId,
      Integer couponTemplateVersion,
      String transportRule,
      String transportRuleVersion,
      String depositRule,
      String depositRuleVersion) {}

  public record Item(
      int schemaVersion,
      String imageKey,
      String lengthCm,
      String widthCm,
      String heightCm,
      String weightKg,
      int packagingFactorBps,
      int skuVersion,
      List<String> snIds) {}

  public record Receiver(
      String name,
      String phone,
      String regionCode,
      String regionText,
      String detailAddress,
      String addressId,
      int addressVersion) {}

  public record Routes(
      int schemaVersion,
      Receiver receiver,
      long factoryAddressVersion,
      CheckoutQuoteData.Leg outbound,
      @com.fasterxml.jackson.annotation.JsonProperty("return") CheckoutQuoteData.Leg returnLeg) {}
}
