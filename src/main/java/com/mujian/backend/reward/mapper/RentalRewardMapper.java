package com.mujian.backend.reward.mapper;

import com.mujian.backend.reward.projection.RentalRewardProjection;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 首次付款奖励专用读写，不接管其他首次事件。 */
@Mapper
public interface RentalRewardMapper {
  /** 每轮限定一百条到期义务，规则停用不删除既有义务。 */
  List<String> selectDueRentalRewardIds(@Param("now") LocalDateTime now);

  /** 用不可变归属定位用户和模板锁，不在此获取跨表锁。 */
  RentalRewardProjection selectRentalRewardById(@Param("id") String id);

  /** 用户根之后锁原模板，避免计数丢失。 */
  String lockOriginalTemplate(@Param("id") String id);

  /** 模板之后锁事实；重复执行会读到已履行。 */
  String lockRewardStatus(@Param("id") String id);

  /** 从原模板复制发券快照，幂等键固定为 reward:事件ID。 */
  void insertRewardCoupon(
      @Param("id") String id,
      @Param("reward") RentalRewardProjection rentalRewardProjection,
      @Param("validFrom") LocalDateTime validFrom,
      @Param("validUntil") LocalDateTime validUntil,
      @Param("now") LocalDateTime now);

  /** 发券、流水、模板计数及履行状态在同一事务落库。 */
  void insertRewardCouponLog(
      @Param("id") String id,
      @Param("couponId") String couponId,
      @Param("rewardId") String rewardId,
      @Param("reason") String reason,
      @Param("operator") String operator,
      @Param("now") LocalDateTime now);

  /** 计数只随成功履行增加一次。 */
  void increaseTemplateIssuedCount(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 仅完成仍待履行的原事实。 */
  int completeRentalReward(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 模板暂停、到期或事务失败保留原义务，五分钟后重试。 */
  void deferRentalReward(
      @Param("id") String id, @Param("reason") String reason, @Param("now") LocalDateTime now);
}
