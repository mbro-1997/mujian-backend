package com.mujian.backend.prop.job;

import com.mujian.backend.prop.service.ReservationArchive;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 每轮最多一百条终态归档，历史不参与可租计算。 */
@Component
public class ReservationArchiveJob {
  private final ReservationArchive reservationArchive;
  private final Environment environment;

  /** 调度只调用短事务归档服务。 */
  public ReservationArchiveJob(ReservationArchive reservationArchive, Environment environment) {
    this.reservationArchive = reservationArchive;
    this.environment = environment;
  }

  /** 复用交易任务开关，重启后继续扫描符合六个月边界的记录。 */
  @Scheduled(
      fixedDelayString = "${mj.trading.archive-delay-ms:60000}",
      initialDelayString = "${mj.trading.archive-delay-ms:60000}")
  public void archive() {
    if (environment.getProperty("mj.trading.recovery-enabled", Boolean.class, true))
      reservationArchive.archiveBatch();
  }
}
