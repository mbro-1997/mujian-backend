package com.mujian.backend.order.vo;

import com.mujian.backend.prop.vo.PublicImageViewVO;
import java.util.List;

/** 展示金额与内部冻结快照分离。 */
public final class CheckoutViews {
  /** 禁止实例化此静态结构容器。 */
  private CheckoutViews() {}

  public record Amounts(
      int rentOriginalFen,
      int couponDiscountFen,
      int rentCashFen,
      int depositFen,
      int freightFen,
      int totalFen) {}

  public record RoutePrice(
      String serviceMode,
      String chargeMode,
      String vehicleTierId,
      String vehicleTierName,
      Integer distanceMeter,
      String pricingRuleId,
      String ruleVersion,
      Integer guideMinFen,
      Integer guideMaxFen,
      int prepaidFen,
      String calculatedAt) {}

  public record TradeItem(
      String snapshotItemId,
      String sourceItemId,
      String skuId,
      String name,
      PublicImageViewVO image,
      String assetType,
      int quantity,
      int dailyRentFen,
      int rentOriginalFen,
      int couponDiscountFen,
      int rentCashFen,
      int depositFen,
      String transportRequirement) {}

  public record FactoryQuote(
      String factoryId,
      String factoryName,
      List<TradeItem> items,
      Amounts amounts,
      RoutePrice outbound,
      @com.fasterxml.jackson.annotation.JsonProperty("return") RoutePrice returnRoute,
      String buyerRemark) {}

  public record Quote(
      String quoteToken,
      String checklistId,
      int checklistVersion,
      String startDate,
      String endDate,
      int rentalDays,
      String couponId,
      List<FactoryQuote> groups,
      Amounts amounts,
      String quotedAt,
      String expiresAt,
      String submissionClosesAt,
      String transportNotice) {}
}
