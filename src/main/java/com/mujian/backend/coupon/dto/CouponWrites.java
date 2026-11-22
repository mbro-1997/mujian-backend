package com.mujian.backend.coupon.dto;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.common.support.SystemIdentity;
import java.time.LocalDateTime;
import java.util.List;

/** 券资金写入只接受服务端已复核分摊结果。 */
public final class CouponWrites {
  /** 禁止实例化此静态结构容器。 */
  private CouponWrites() {}

  public record Allocation(
      String id,
      String usageId,
      String batchId,
      String snapshotId,
      String factoryId,
      String skuId,
      int eligibleRentFen,
      int discountFen,
      LocalDateTime at) {}

  public record Adjustment(
      String id,
      String batchId,
      String factoryId,
      String couponId,
      int discountFen,
      String snapshotJson,
      LocalDateTime at) {}

  public record AdjustmentSnapshot(
      int schemaVersion,
      String sourceType,
      String sourceId,
      int sourceVersion,
      String factoryId,
      List<String> snapshotItemIds,
      String scopeType,
      int thresholdRentFen,
      int discountFen) {}

  public record Log(
      String id,
      String requestId,
      String couponId,
      String usageId,
      String operation,
      String fromStatus,
      String toStatus,
      String actor,
      String batchId,
      String reason,
      LocalDateTime at) {
    /** 系统来源只用既有固定系统用户，不伪造本人操作。 */
    public String operatorType() {
      return SystemIdentity.SYSTEM_USER_ID.equals(actor) ? FulfillmentRole.system.name() : "user";
    }
  }
}
