package com.mujian.backend.order.dto;

import com.mujian.backend.order.domain.CheckoutQuoteData;
import java.time.LocalDateTime;

/** 提交事务的明确写入参数，不接收客户端金额。 */
public final class CheckoutWrites {
  /** 禁止实例化此静态结构容器。 */
  private CheckoutWrites() {}

  public record Batch(
      String id,
      String paymentId,
      String requestId,
      String requestHash,
      CheckoutQuoteData quote,
      String ruleSnapshotJson,
      String transportNotice,
      LocalDateTime at,
      LocalDateTime expiry) {}

  public record Transport(
      String id,
      String batchId,
      CheckoutQuoteData.Factory factory,
      String receiverAddress,
      String routeSnapshotJson,
      String transportRequirements,
      LocalDateTime at) {}

  public record Item(
      String batchId,
      String checklistId,
      String transportId,
      CheckoutQuoteData.Factory factory,
      CheckoutQuoteData.Item item,
      CheckoutQuoteData quote,
      String imageKey,
      String styleLabels,
      String itemSnapshotJson,
      LocalDateTime at) {}
}
