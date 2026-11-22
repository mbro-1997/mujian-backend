package com.mujian.backend.delivery.dto;

/** D4履约机器契约的明确字段。 */
public final class FulfillmentDtos {
  /** 仅承载明确的请求/响应类型。 */
  private FulfillmentDtos() {}

  public record ExpectedVersion(int expectedVersion) {}

  public record OutboundDraft(int expectedVersion, String remark) {}

  public record ScanInput(int expectedVersion, String code) {}

  public record QuantityInput(int expectedVersion, String itemId, int quantity) {}

  public record ShipmentInput(
      int expectedVersion, String sentAt, String trackingNo, String carrierName, String remark,String handoverMethod,Boolean disputed) {}

  public record PickupInput(int expectedVersion, String expectedArrivalAt, String remark) {}

  public record AdminReason(int expectedVersion, String reason) {}
}
