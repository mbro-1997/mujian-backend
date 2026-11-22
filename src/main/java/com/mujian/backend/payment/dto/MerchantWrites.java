package com.mujian.backend.payment.dto;

import java.time.LocalDateTime;

public final class MerchantWrites {
  /** 禁止实例化此静态结构容器。 */
  private MerchantWrites() {}

  public record Sync(
      String id,
      int version,
      String applymentId,
      String requestNo,
      String subMchid,
      String accountStatus,
      String profitSharingStatus,
      String snapshot,
      LocalDateTime now) {}
}
