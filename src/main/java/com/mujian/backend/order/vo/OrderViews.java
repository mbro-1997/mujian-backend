package com.mujian.backend.order.vo;

import com.fasterxml.jackson.annotation.*;
import java.util.List;

/** 页面展示只返回本批资金科目和已获权历史快照。 */
public final class OrderViews {
  /** 禁止实例化此静态结构容器。 */
  private OrderViews() {}

  public record Summary(
      String id,
      String orderNo,
      String factoryId,
      String factoryName,
      String orderStatus,
      String acceptStatus,
      String refundStatus,
      String displayStatus,
      int version,
      String startDate,
      String endDate,
      CheckoutViews.Amounts amounts,
      int paidFen,
      int refundedFen,
      int deductedFen,
      String acceptDeadlineAt,
      String createdAt,
      List<String> availableActions,
      String fulfillmentStage,
      String nextDeadlineAt) {}

  public record Receiver(String name, String phone, String regionText, String detailAddress) {}

  public record CancellationRule(
      String ruleCode,
      String version,
      String documentId,
      int freeHoursExclusive,
      int middleHoursInclusive,
      int middleRateBps,
      int lateRateBps) {}

  public record Detail(
      @JsonUnwrapped Summary summary,
      String batchId,
      String paymentId,
      List<CheckoutViews.TradeItem> items,
      Receiver receiver,
      String buyerRemark,
      CheckoutViews.RoutePrice outbound,
      @JsonProperty("return") CheckoutViews.RoutePrice returnLeg,
      String transportNotice,
      String acceptedAt,
      String rejectReason,
      String cancelReason,
      List<String> refundIds,
      boolean supplyWarning,
      CancellationRule cancellationRule,
      com.mujian.backend.delivery.vo.DisputeContext disputes) {}

  public record Overview(
      String pendingAcceptCount,
      String acceptedCount,
      String refundExceptionCount,
      String allOrderCount,
      String generatedAt,
      String pendingOutboundCount,
      String returnCheckingCount,String exceptionOrderCount) {}
}
