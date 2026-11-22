package com.mujian.backend.payment.job;

import com.mujian.backend.payment.service.TradingRecovery;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 定时入口只调用既有持久恢复服务，不另建资金或订单规则。 */
@Component
public class TradingRecoveryJob {
  private final TradingRecovery tradingRecovery;
  private final Environment environment;

  /** 注入统一恢复服务与本机任务开关。 */
  public TradingRecoveryJob(TradingRecovery tradingRecovery, Environment environment) {
    this.tradingRecovery = tradingRecovery;
    this.environment = environment;
  }

  /** 每轮有限扫描，跨进程争用继续由业务事务租约处理。 */
  @Scheduled(
      fixedDelayString = "${mj.trading.recovery-delay-ms:10000}",
      initialDelayString = "${mj.trading.recovery-delay-ms:10000}")
  public void recover() {
    if (environment.getProperty("mj.trading.recovery-enabled", Boolean.class, true))
      tradingRecovery.recover();
  }
}
