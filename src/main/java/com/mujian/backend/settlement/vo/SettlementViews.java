package com.mujian.backend.settlement.vo;

/** 工厂及管理员使用同一金额含义，批次长整型分值以十进制字符串传输。 */
public final class SettlementViews {
  /** 仅承载本领域的结构定义或纯计算。 */
  private SettlementViews() {}

  public record Summary(
      String id,
      String batchNo,
      String factoryId,
      String factoryName,
      String status,
      int orderCount,
      String rentBaseFen,
      String commissionFen,
      String depositCompensationFen,
      String freightFen,
      String factorySettleFen,
      String actualUnlockFen,
      String generatedAt,
      String settledAt) {}

  public record Item(
      String id,
      String orderId,
      String orderNo,
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Integer goodsPaidFen,
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Integer goodsRefundedFen,
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Integer goodsBaseFen,
      @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Integer factoryGoodsFen,
      int initialRentFen,
      int couponDiscountFen,
      int extensionRentFen,
      int overdueRentFen,
      int overdueCommissionFen,
      int rentRefundedFen,
      int rentAdjustmentFen,
      int rentBaseFen,
      int commissionRateBps,
      int commissionFen,
      int factoryRentFen,
      int depositCompensationFen,
      int excessCompensationFen,
      int freightApprovedFen,
      int freightRefundedFen,
      int freightSettleFen,
      int factorySettleFen,
      String status) {}

  public record Execution(
      String id,
      String detailId,
      String sourceSuborderId,
      String type,
      String commissionStatus,
      String unfreezeStatus,
      int expectedCommissionFen,
      int actualCommissionFen,
      int expectedUnlockFen,
      int actualUnlockFen,
      String channelSharingNo,
      String channelFinishNo,
      String exceptionCode,
      String lastQueriedAt,
      String successAt) {}

  public record Detail(
      Summary summary,
      String receiverName,
      String accountMasked,
      String lastCheckedAt,
      String exceptionCode,
      String snapshotToken,
      String lastAcknowledgedAt) {}

  public record Acknowledgement(String acknowledgementId, String batchId, String acknowledgedAt) {}
}
