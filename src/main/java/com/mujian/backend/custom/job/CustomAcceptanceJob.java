package com.mujian.backend.custom.job;

import static com.mujian.backend.common.support.D2Values.now;

import com.mujian.backend.custom.mapper.CustomFulfillmentMapper;
import com.mujian.backend.custom.service.CustomFulfillment;
import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mj.fulfillment.enabled", havingValue = "true")
public class CustomAcceptanceJob {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CustomAcceptanceJob.class);
  private final CustomFulfillmentMapper customFulfillmentMapper;
  private final CustomFulfillment flow;
  private final Clock clock;

  public CustomAcceptanceJob(CustomFulfillmentMapper customFulfillmentMapper, CustomFulfillment customFulfillment, Clock clock) {
    this.customFulfillmentMapper = customFulfillmentMapper;
    this.flow = customFulfillment;
    this.clock = clock;
  }

  /** 按到期条件选取并处理可推进的记录。 */
  @Scheduled(
      fixedDelayString = "${mj.fulfillment.recovery-delay-ms:10000}",
      initialDelayString = "${mj.fulfillment.recovery-delay-ms:10000}")
  public void scan() {
    for (CustomFulfillmentMapper.Due task : customFulfillmentMapper.selectOrdersDueForAcceptance(now(clock))) {
      try {
        flow.automatic(task);
      } catch (RuntimeException failure) {
        log.error("定制验收任务失败 orderId={} deliveryVersion={}", task.id(), task.deliveryVersion(), failure);
      }
    }
  }
}
