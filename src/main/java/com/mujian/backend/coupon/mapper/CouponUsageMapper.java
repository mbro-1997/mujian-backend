package com.mujian.backend.coupon.mapper;

import com.mujian.backend.coupon.domain.CouponUsageDO;
import com.mujian.backend.coupon.dto.CouponWrites;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CouponUsageMapper {
  /** 无实收安全关闭释放原券，不延长原有效期。 */
  int releaseCoupon(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 全退仅返还一次，旧到期与确认后七天取最大值。 */
  int returnCoupon(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 使用记录保留释放或返还历史，不能伪造核销。 */
  int finishUsage(
      @Param("id") String id, @Param("status") String status, @Param("now") LocalDateTime now);

  /** 已成单返还分摊，未成单作废分摊。 */
  void finishAllocations(
      @Param("usage") String usage,
      @Param("status") String status,
      @Param("now") LocalDateTime now);

  /** 全付成单后原锁定券核销，不能重新选择券或重新分摊。 */
  int markCouponUsed(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 同事务确认原使用记录核销。 */
  int markUsageUsed(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 成单按原快照关联订单及明细，保留原优惠归属。 */
  void bindAllocationsToOrders(@Param("batch") String batch, @Param("now") LocalDateTime now);

  /** 原厂优惠只补新形成的订单，不重建金额调整。 */
  void bindAdjustmentsToOrders(@Param("batch") String batch, @Param("now") LocalDateTime now);

  /** 在券根锁内将原状态转锁定，原有效期不延长。 */
  int lockCoupon(
      @Param("id") String id,
      @Param("version") int version,
      @Param("now") LocalDateTime now,
      @Param("expiry") LocalDateTime expiry);

  /** 原批次唯一使用记录，与占用同事务。 */
  void insertUsage(@Param("usage") CouponUsageDO couponUsageDO);

  /** 明细分摊记录不可因取消删除。 */
  void insertAllocation(@Param("allocation") CouponWrites.Allocation allocation);

  /** 按厂保留优惠来源，成单只补订单关联。 */
  void insertPriceAdjustment(@Param("adjustment") CouponWrites.Adjustment adjustment);

  /** 记录原券状态迁移及稳定业务来源。 */
  void insertOperationLog(@Param("log") CouponWrites.Log log);

  /** 读取原批次使用事实，可按既定顺序加锁。 */
  CouponUsageDO selectUsageByBatch(@Param("id") String id, @Param("lock") boolean lock);
}
