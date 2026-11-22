package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.order.mapper.RentalOrderMapper;
import java.time.Clock;
import org.slf4j.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** 接单超时扫描有限批次，不等待用户进入页面触发。 */
@Service
public class OrderTimeoutRecovery {
  private static final Logger LOG = LoggerFactory.getLogger(OrderTimeoutRecovery.class);
  private final RentalOrderMapper rentalOrderMapper;
  private final RentalOrderCommands rentalOrderCommands;
  private final Clock clock;
  private final Environment environment;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public OrderTimeoutRecovery(
      RentalOrderMapper rentalOrderMapper,
      RentalOrderCommands rentalOrderCommands,
      Clock clock,
      Environment environment) {
    this.rentalOrderMapper = rentalOrderMapper;
    this.rentalOrderCommands = rentalOrderCommands;
    this.clock = clock;
    this.environment = environment;
  }

  /** 从持久记录接续原义务，单项失败留待原号重试。 */
  public void recover() {
    try {
      for (String id : rentalOrderMapper.selectAcceptanceTimeoutOrderIds(now(clock)))
        try {
          rentalOrderCommands.timeout(id);
        } catch (RuntimeException failure) {
          LOG.warn("event=order_timeout_recovery_pending orderId={}", id);
        }
    } catch (RuntimeException failure) {
      LOG.warn("event=order_timeout_scan_failed");
    }
  }
}
