package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.now;

import com.mujian.backend.delivery.mapper.DisputeMapper;
import com.mujian.backend.delivery.mapper.OrderIssueMapper;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.service.TradingRoots;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptTimerRecovery {
  private final RentalOrderMapper rentalOrderMapper;
  private final TradingRoots tradingRoots;
  private final DisputeMapper disputeMapper;
  private final OrderIssueMapper orderIssueMapper;
  private final Clock clock;

  /** 与人工收货及问题提交使用同一订单锁。 */
  public ReceiptTimerRecovery(RentalOrderMapper rentalOrderMapper, TradingRoots tradingRoots,
      DisputeMapper disputeMapper, OrderIssueMapper orderIssueMapper, Clock clock) {
    this.rentalOrderMapper = rentalOrderMapper;
    this.tradingRoots = tradingRoots;
    this.disputeMapper = disputeMapper;
    this.orderIssueMapper = orderIssueMapper;
    this.clock = clock;
  }

  /** 有冻结事实的原订单均可在重启后重查。 */
  public List<String> candidates(String afterId) {
    return orderIssueMapper.selectFrozenReceipts(afterId);
  }

  /** 最后一个阻断解除才恢复倒计时，不代买家确认。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void restore(String orderId) {
    RentalOrderDO order = rentalOrderMapper.selectOrderById(orderId);
    if (order == null) return;
    tradingRoots.lock(order.checkoutBatchId());
    if (!disputeMapper.canResumeReceipt(orderId)) return;
    LocalDateTime at = now(clock);
    if (orderIssueMapper.resumeReceipt(orderId, at) == 1)
      orderIssueMapper.synchronizeReceiptDeadline(orderId, at);
  }
}
