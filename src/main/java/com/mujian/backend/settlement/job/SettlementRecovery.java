package com.mujian.backend.settlement.job;

import static com.mujian.backend.common.support.D2Values.now;

import com.mujian.backend.common.enums.ProfitSharingOutcome;
import com.mujian.backend.infrastructure.payment.wechat.ProfitSharingGateway;
import com.mujian.backend.infrastructure.payment.wechat.ProfitSharingGateway.*;
import com.mujian.backend.settlement.mapper.*;
import com.mujian.backend.settlement.service.*;
import java.time.Clock;
import java.util.List;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 02:00后领取前一日及此前未结来源；逐次按数据库租约及原请求号恢复分账。 */
@Service
@ConditionalOnProperty(name = "mj.fulfillment.enabled", havingValue = "true")
public class SettlementRecovery {
  private static final Logger LOG = LoggerFactory.getLogger(SettlementRecovery.class);
  private final com.mujian.backend.order.mapper.RentalOrderMapper rentalOrderMapper;
  private final Clock clock;
  private final SettlementMapper settlementMapper;
  private final SettlementGeneration settlementGeneration;
  private final SharingMapper sharingMapper;
  private final SharingWork sharingWork;
  private final ProfitSharingGateway profitSharingGateway;
  private String factoryCursor = "", executionCursor = "";

  /** 装配分账恢复任务所需依赖。 */
  public SettlementRecovery(
      com.mujian.backend.order.mapper.RentalOrderMapper rentalOrderMapper,
      Clock clock,
      SettlementMapper settlementMapper,
      SettlementGeneration settlementGeneration,
      SharingMapper sharingMapper,
      SharingWork sharingWork,
      ProfitSharingGateway profitSharingGateway) {
    this.rentalOrderMapper = rentalOrderMapper;
    this.clock = clock;
    this.settlementMapper = settlementMapper;
    this.settlementGeneration = settlementGeneration;
    this.sharingMapper = sharingMapper;
    this.sharingWork = sharingWork;
    this.profitSharingGateway = profitSharingGateway;
  }

  /** 按截止时间补生成批次并分页恢复到期的渠道执行。 */
  @Scheduled(
      fixedDelayString = "${mj.fulfillment.recovery-delay-ms:10000}",
      initialDelayString = "${mj.fulfillment.recovery-delay-ms:10000}")
  public synchronized void scan() {
    if (!rentalOrderMapper.hasSettlementTables()) return;
    List<String> factories =
        settlementMapper.selectRentalOrderFactories(SettlementGeneration.cutoff(now(clock)), factoryCursor);
    for (String factory : factories)
      try {
        settlementGeneration.generate(factory);
      } catch (RuntimeException e) {
        LOG.warn("结算固化未完成 factoryId={}", factory, e);
      }
    factoryCursor = factories.size() == 100 ? factories.get(99) : "";
    List<String> due = sharingMapper.selectWechatProfitSharingOrderDueForProcessing(now(clock), executionCursor);
    for (String id : due)
      try {
        run(id);
      } catch (RuntimeException e) {
        LOG.warn("分账恢复未完成 executionId={}", id, e);
      }
    executionCursor = due.size() == 100 ? due.get(99) : "";
  }

  /** 领取单个执行阶段，在事务外收发渠道请求并回写结果。 */
  public void run(String id) {
    SharingWork.Claim claim = sharingWork.claim(id);
    if (claim == null) return;
    Observation observation;
    if (claim.row().autoUnfrozen() && !claim.request().finish())
      observation = new Observation(ProfitSharingOutcome.MISMATCH, 0, null);
    else
      try {
        observation = profitSharingGateway.execute(claim.request(), claim.query());
      } catch (RuntimeException e) {
        observation = new Observation(ProfitSharingOutcome.UNKNOWN, 0, null);
      }
    sharingWork.observe(claim, observation);
  }
}
