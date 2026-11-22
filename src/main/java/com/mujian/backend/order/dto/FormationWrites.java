package com.mujian.backend.order.dto;

import com.mujian.backend.order.domain.*;
import com.mujian.backend.order.vo.CheckoutViews.Amounts;
import java.time.LocalDateTime;

/** 只使用已全付的原快照形成订单，不重新计价或分配库存。 */
public final class FormationWrites {
  /** 禁止实例化此静态结构容器。 */
  private FormationWrites() {}

  public record Order(
      String id,
      CheckoutBatchDO batch,
      String paymentId,
      BatchTransportSnapshotDO transport,
      TradingSnapshots.Receiver receiver,
      Amounts amounts,
      LocalDateTime paidAt,
      LocalDateTime at,
      LocalDateTime deadline) {}

  public record Item(String id, String orderId, String snapshotId, LocalDateTime at) {}

  public record Delivery(
      String id,
      String orderId,
      BatchTransportSnapshotDO transport,
      TradingSnapshots.Receiver receiver,
      String notice,
      LocalDateTime at) {}

  public record Freight(
      String id, String orderId, BatchTransportSnapshotDO transport, LocalDateTime at) {}

  public record Allocation(
      String id,
      String orderId,
      String paymentId,
      String suborderId,
      Amounts amounts,
      LocalDateTime at) {}

  public record Leg(
      String id,
      String orderId,
      String deliveryId,
      String factoryId,
      String type,
      String fromAddress,
      String toAddress,
      Integer maxItemLengthMm,
      Integer maxItemWidthMm,
      Integer maxItemHeightMm,
      CheckoutQuoteData.Leg pricing,
      String pricingJson,
      LocalDateTime mapAt,
      LocalDateTime at) {}
}
