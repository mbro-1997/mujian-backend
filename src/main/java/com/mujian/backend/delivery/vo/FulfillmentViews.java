package com.mujian.backend.delivery.vo;

import java.util.List;

/** D4履约机器契约的明确字段。 */
public final class FulfillmentViews {
  /** 仅承载明确的请求/响应类型。 */
  private FulfillmentViews() {}

  public record CheckItem(
      String id,
      String itemId,
      String skuId,
      String name,
      String assetType,
      int expectedQuantity,
      int checkedQuantity,
      int version,
      boolean complete) {}

  public record Outbound(
      String id,
      String orderId,
      int version,
      String status,
      String serviceMode,
      List<CheckItem> items,
      int photoCount,
      String remark,
      String outboundAt,
      String dispatchAt,
      List<String> availableActions) {}

  public record ReceiptCheck(
      String id,
      String orderId,
      int version,
      String status,
      String startAt,
      String deadlineAt,
      String confirmedAt,
      boolean paused,
      List<String> availableActions) {}

  public record ReturnShipment(
      String id,
      String orderId,
      int version,
      String method,
      String status,
      String sentAt,
      String expectedArrivalAt,
      String expectedCheckStartAt,
      String trackingNo,
      String carrierName,
      String remark,
      int photoCount,
      String transportNotice,
      List<String> availableActions) {}

  public record ReturnCheck(
      String id,
      String orderId,
      int version,
      String status,
      String startAt,
      String warningAt,
      String autoCloseAt,
      String closedAt,
      String checkedAt,
      String closeReason,
      boolean claimCreated,
      List<CheckItem> items,
      boolean inventoryReleased,
      String depositStatus,
      List<String> refundIds,
      List<String> availableActions) {}

  public record Fulfillment(
      String orderId,
      String serverTime,
      Outbound outbound,
      ReceiptCheck receipt,
      ReturnShipment shipment,
      ReturnCheck returnCheck,
      boolean overdue,
      boolean disputeProtectionReady,
      DisputeContext disputes) {}

  public record ScanRecord(
      String id, String itemId, String snId, String snCode, String qrId, String scannedAt) {
    /** 持久时间统一用平台上海时区及毫秒精度序列化。 */
    public ScanRecord(
        String id,
        String itemId,
        String snId,
        String snCode,
        String qrId,
        java.time.LocalDateTime time) {
      this(id, itemId, snId, snCode, qrId, com.mujian.backend.common.support.D2Values.time(time));
    }
  }

  public record ScanPage(List<ScanRecord> items, int page, int pageSize, String total) {}

  public record ScanResult(
      com.mujian.backend.order.vo.TradingReceipt receipt,
      boolean duplicate,
      String itemId,
      int checkedQuantity,
      int expectedQuantity,
      int version) {}

  public record CloseResult(
      com.mujian.backend.order.vo.TradingReceipt receipt,
      String checkId,
      String actionId,
      boolean checkClosed,
      boolean refundObligationRecorded,
      boolean inventoryReleased,
      boolean eventRecorded,
      List<String> refundIds) {}

  public record Event(
      String id,
      String orderId,
      String type,
      String time,
      String operatorRole,
      String sourceType,
      String sourceId,
      String summary) {
    /** 与全平台交易时间共用上海时区表示。 */
    public Event(
        String id,
        String orderId,
        String type,
        java.time.LocalDateTime at,
        String operatorRole,
        String sourceType,
        String sourceId,
        String summary) {
      this(
          id,
          orderId,
          type,
          com.mujian.backend.common.support.D2Values.time(at),
          operatorRole,
          sourceType,
          sourceId,
          summary);
    }
  }

  public record EventPage(List<Event> items, int page, int pageSize, String total) {}
}
