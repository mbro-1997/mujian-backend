package com.mujian.backend.reward.service;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.now;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.common.support.SystemIdentity;

import com.mujian.backend.reward.mapper.RentalRewardMapper;
import com.mujian.backend.reward.projection.RentalRewardProjection;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 已成单首次付款奖励的独立短事务；失败不回滚订单和实收。 */
@Service
public class RentalRewardIssuer {
  private final RentalRewardMapper rentalRewardMapper;
  private final Clock clock;

  /** 复用用户根锁和可控时钟。 */
  public RentalRewardIssuer(
      RentalRewardMapper rentalRewardMapper, Clock clock) {
    this.rentalRewardMapper = rentalRewardMapper;
    this.clock = clock;
  }

  /** 只提供本轮到期义务，单条发券仍由原事务处理。 */
  public java.util.List<String> dueRewardIds() {
    return rentalRewardMapper.selectDueRentalRewardIds(now(clock));
  }

  /** 严格履行已捕获资格，不能因规则换版改发另一张券。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void issue(String rewardId) {
    if (!"pending".equals(rentalRewardMapper.lockRewardStatus(rewardId))) return;
    RentalRewardProjection rentalRewardProjection = rentalRewardMapper.selectRentalRewardById(rewardId);
    rentalRewardMapper.lockOriginalTemplate(rentalRewardProjection.templateId());
    rentalRewardProjection = rentalRewardMapper.selectRentalRewardById(rewardId);
    LocalDateTime now = now(clock);
    if (!"active".equals(rentalRewardProjection.userStatus())) {
      rentalRewardMapper.deferRentalReward(rewardId, D2Codes.CODE_ACCOUNT_RESTRICTED, now);
      return;
    }
    if (!"active".equals(rentalRewardProjection.templateStatus())) {
      rentalRewardMapper.deferRentalReward(rewardId, "TEMPLATE_NOT_ACTIVE", now);
      return;
    }
    LocalDateTime validFrom = now;
    LocalDateTime validUntil;
    if ("relative_days".equals(rentalRewardProjection.validityType())) {
      validUntil = now.plusDays(rentalRewardProjection.validDays());
    } else {
      validFrom = rentalRewardProjection.validStartAt();
      validUntil = rentalRewardProjection.validEndAt();
      if (!now.isBefore(validUntil)) {
        rentalRewardMapper.deferRentalReward(rewardId, "TEMPLATE_EXPIRED", now);
        return;
      }
    }
    String couponId = Ids.next();
    rentalRewardMapper.insertRewardCoupon(
        couponId, rentalRewardProjection, validFrom, validUntil, now);
    rentalRewardMapper.insertRewardCouponLog(
        Ids.next(), couponId, rewardId, rentalRewardProjection.eventType().description, SystemIdentity.SYSTEM_USER_ID, now);
    rentalRewardMapper.increaseTemplateIssuedCount(rentalRewardProjection.templateId(), now);
    if (rentalRewardMapper.completeRentalReward(rewardId, now) != 1)
      throw new IllegalStateException("reward obligation changed");
  }

  /** 独立记录失败退避，不能覆盖另一执行者已成功的结果。 */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void defer(String rewardId) {
    rentalRewardMapper.deferRentalReward(rewardId, "REWARD_TRANSACTION_FAILED", now(clock));
  }
}
