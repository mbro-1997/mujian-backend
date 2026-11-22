package com.mujian.backend.reward.job;

import com.mujian.backend.reward.service.RentalRewardIssuer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 恢复三类首次奖励，单条失败不会阻断下一条。 */
@Component
public class RentalRewardRecoveryJob {
  private static final Logger LOG = LoggerFactory.getLogger(RentalRewardRecoveryJob.class);
  private final RentalRewardIssuer rentalRewardIssuer;
  private final Environment environment;

  /** 扫描与单条短事务分离，跨进程并发由原事实锁串行。 */
  public RentalRewardRecoveryJob(
      RentalRewardIssuer rentalRewardIssuer, Environment environment) {
    this.rentalRewardIssuer = rentalRewardIssuer;
    this.environment = environment;
  }

  /** 使用独立奖励恢复开关；从数据库恢复，无内存队列。 */
  @Scheduled(
      fixedDelayString = "${mj.reward.recovery-delay-ms:10000}",
      initialDelayString = "${mj.reward.recovery-delay-ms:10000}")
  public void recover() {
    if (!environment.getProperty("mj.reward.recovery-enabled", Boolean.class, false)) return;
    for (String rewardId : rentalRewardIssuer.dueRewardIds()) {
      try {
        rentalRewardIssuer.issue(rewardId);
      } catch (RuntimeException failure) {
        LOG.warn("event=first_reward_issue_failed rewardId={}",rewardId);
        try {
          rentalRewardIssuer.defer(rewardId);
        } catch (RuntimeException storageFailure) {
          LOG.warn("event=first_reward_retry_pending rewardId={}", rewardId);
        }
      }
    }
  }
}
