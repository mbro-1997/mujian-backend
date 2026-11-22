package com.mujian.backend.settlement.service;

import static com.mujian.backend.common.support.D2Values.now;

import com.mujian.backend.common.enums.ProfitSharingOutcome;
import com.mujian.backend.delivery.policy.FulfillmentProtection;
import com.mujian.backend.infrastructure.payment.wechat.ProfitSharingGateway.*;
import com.mujian.backend.settlement.mapper.SharingMapper;
import com.mujian.backend.settlement.mapper.SharingMapper.*;
import java.time.*;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 领取与回写之间释放事务。版本锁使旧租约结果不能覆盖已确认金额。 */
@Service
public class SharingWork {
  public record Claim(State row, Request request, boolean query, int version) {}

  private final SharingMapper sharingMapper;
  private final Clock clock;
  private final FulfillmentProtection fulfillmentProtection;
  private final ObjectMapper objectMapper;

  /** 装配分账执行及回写所需依赖。 */
  public SharingWork(
      SharingMapper sharingMapper,
      Clock clock,
      FulfillmentProtection fulfillmentProtection,
      ObjectMapper objectMapper) {
    this.sharingMapper = sharingMapper;
    this.clock = clock;
    this.fulfillmentProtection = fulfillmentProtection;
    this.objectMapper = objectMapper;
  }

  /** 沿已有订单及资金根锁序重新读取命令执行依据。 */
  private void lock(State row) {
    if(row.custom())sharingMapper.lockCustom(row.orderId());else sharingMapper.lockOrder(row.orderId());
    sharingMapper.lockSource(row.suborderId());
    sharingMapper.lockBatch(row.batchId());
  }

  /** 在原订单和批次锁内领取到期阶段，租约过期后优先查原号。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Claim claim(String id) {
    State sharingState = sharingMapper.selectWechatProfitSharingOrderState(id);
    if (sharingState == null) return null;
    // 对同批次的领取、进度确认与管理员已阅采用同一个根。
    lock(sharingState);
    sharingState = sharingMapper.selectWechatProfitSharingOrderState(id);
    LocalDateTime at = now(clock);
    if (!Set.of("pending", "processing").contains(sharingState.status())
        || sharingState.due() == null
        || sharingState.due().isAfter(at)
        || (sharingState.lease() != null && sharingState.lease().isAfter(at))
        || !(sharingState.custom()?sharingMapper.customEligible(sharingState.orderId()):fulfillmentProtection.canSettle(sharingState.orderId()))) return null;
    boolean finish = Set.of("success", "not_required").contains(sharingState.commission());
    boolean query =
        // 旧候选把已收到渠道单号的关闭回执标作可重试，先按原号重新查明终态。
        sharingState.lease() != null
            || sharingState.retryHasChannelOrder()
            || "processing".equals(finish ? sharingState.unfreeze() : sharingState.commission());
    if (sharingMapper.updateWechatProfitSharingOrderLease(sharingState, finish, at) != 1) return null;
    return new Claim(
        sharingState,
        new Request(
            sharingState.platform(),
            sharingState.merchant(),
            sharingState.transactionId(),
            finish ? sharingState.finishNo() : sharingState.shareNo(),
            finish,
            finish ? sharingState.unlockFen() : sharingState.fee()),
        query,
        sharingState.version() + 1);
  }

  /** 按领取版本记录已验渠道结果，防止迟到回执回退状态。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void observe(Claim claim, Observation fact) {
    State sharingState = claim.row();
    lock(sharingState);
    sharingState = sharingMapper.selectWechatProfitSharingOrderState(sharingState.id());
    if (sharingState.version() != claim.version()) return;
    LocalDateTime at = now(clock), due = at.plusSeconds(10);
    boolean finish = claim.request().finish();
    String commission = finish ? sharingState.commission() : "processing",
        unfreeze = finish ? "processing" : "blocked",
        status = "processing",
        exception = null;
    int feeRetries = sharingState.commissionRetries(), unlockRetries = sharingState.unfreezeRetries();
    Integer actualFee = null, actualUnlock = null;
    ProfitSharingOutcome outcome = fact.outcome();
    if (outcome == ProfitSharingOutcome.CLOSED && claim.query() && !finish) {
      status = "failed";
      commission = "failed";
      exception = fact.closeReason().name();
      due = null;
    } else if (sharingState.autoUnfrozen() && !finish) {
      status = "failed";
      exception = "auto_unfrozen";
      commission = "failed";
      due = null;
    } else if (outcome == ProfitSharingOutcome.SUCCESS
        && claim.query()
        && fact.actual() == claim.request().amount()
        && fact.channelNo() != null) {
      if (finish) {
        actualUnlock = fact.actual();
        unfreeze = "success";
        status = "success";
        due = null;
      } else {
        actualFee = fact.actual();
        commission = "success";
        unfreeze = "pending";
        due = at;
      }
    } else if (outcome == ProfitSharingOutcome.ABSENT
        || outcome == ProfitSharingOutcome.RETRYABLE) {
      int retries = finish ? unlockRetries : feeRetries;
      if (retries == 3) {
        status = "failed";
        due = null;
        exception = "sharing_retry_exhausted";
        if (finish) unfreeze = "failed";
        else commission = "failed";
      } else {
        if (finish) {
          unlockRetries++;
          unfreeze = "pending";
        } else {
          feeRetries++;
          commission = "pending";
        }
        due = at.plusSeconds(new int[] {30, 60, 300}[retries]);
        exception = "sharing_retry_pending";
      }
    } else if (outcome == ProfitSharingOutcome.MISMATCH
        || outcome == ProfitSharingOutcome.SUCCESS) {
      status = "failed";
      due = null;
      exception = "channel_result_mismatch";
      if (finish) unfreeze = "failed";
      else commission = "failed";
    } else if (outcome == ProfitSharingOutcome.UNKNOWN) {
      exception = "channel_result_unknown";
      due = at.plusSeconds(30);
    }
    Write sharingWrite =
        new Write(
            commission,
            unfreeze,
            status,
            feeRetries,
            unlockRetries,
            actualFee,
            actualUnlock,
            finish ? null : fact.channelNo(),
            finish ? fact.channelNo() : null,
            exception,
            objectMapper.writeValueAsString(fact),
            claim.query(),
            due,
            at);
    if (sharingMapper.updateWechatProfitSharingOrderApplication(sharingState.id(), claim.version(), sharingWrite) == 0) return;
    if ("success".equals(status) && sharingMapper.settledSource(sharingState.suborderId(), at) != 1)
      throw new IllegalStateException("sharing source changed");
    sharingMapper.reconcileCommissions(sharingState.batchId(), at);
    sharingMapper.reconcileDetails(sharingState.batchId(), at);
    if(sharingState.custom())sharingMapper.reconcileCustom(sharingState.batchId(),at);
    sharingMapper.reconcilePayment(sharingState.batchId(), at);
    sharingMapper.reconcileBatch(sharingState.batchId(), at);
  }
}
