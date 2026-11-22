package com.mujian.backend.coupon.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.coupon.domain.CouponUsageDO;
import com.mujian.backend.coupon.dto.CouponWrites;
import com.mujian.backend.coupon.mapper.*;
import com.mujian.backend.coupon.projection.CouponProjection;
import com.mujian.backend.payment.domain.ChannelSuborderDO;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 整张原付款的券生命周期，包含所有工厂原现金，不只检查得到优惠的工厂。 */
@Service
public class CouponLifecycle {
  public record Locked(CouponUsageDO usage, CouponProjection coupon) {}

  private final CouponMapper couponMapper;
  private final CouponUsageMapper couponUsageMapper;
  private final Clock clock;

  /** 券根在库存之后，usage和allocation在券根之后。 */
  public CouponLifecycle(
      CouponMapper couponMapper, CouponUsageMapper couponUsageMapper, Clock clock) {
    this.couponMapper = couponMapper;
    this.couponUsageMapper = couponUsageMapper;
    this.clock = clock;
  }

  /** 同批原券及使用记录锁定，不因当前券状态不可用而遗漏合法退款。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public Locked lock(String buyer, String batchId) {
    CouponUsageDO couponUsageDOUsage = couponUsageMapper.selectUsageByBatch(batchId, false);
    if (couponUsageDOUsage == null) return null;
    CouponProjection couponProjectionCoupon =
        couponMapper.selectCouponByUserAndId(buyer, couponUsageDOUsage.userCouponId(), true);
    if (couponProjectionCoupon == null) throw new IllegalStateException("coupon source missing");
    couponUsageDOUsage = couponUsageMapper.selectUsageByBatch(batchId, true);
    return new Locked(couponUsageDOUsage, couponProjectionCoupon);
  }

  /** 仅在所有原子单不可再支付且原款全部返清后返券；未收款关闭走释放。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void reconcile(
      Locked locked,
      List<ChannelSuborderDO> channelSuborderDOListSuborders,
      boolean formed,
      boolean hasDeduction) {
    if (locked == null
        || Set.of(TradingState.returned.name(), TradingState.released.name())
            .contains(locked.usage().usageStatus())
        || hasDeduction) return;
    int paid = 0;
    for (ChannelSuborderDO channelSuborderDOSuborder : channelSuborderDOListSuborders) {
      if (!Set.of(TradingState.success.name(), TradingState.closed.name())
              .contains(channelSuborderDOSuborder.paymentStatus())
          || channelSuborderDOSuborder.refundedAmountFen()
              != channelSuborderDOSuborder.paidAmountFen()) return;
      paid = Math.addExact(paid, channelSuborderDOSuborder.paidAmountFen());
    }
    LocalDateTime at = now(clock);
    boolean returned = paid > 0;
    String status = returned ? TradingState.returned.name() : TradingState.released.name();
    if (returned) {
      if (couponUsageMapper.returnCoupon(locked.coupon().id(), at) != 1)
        throw new IllegalStateException("coupon return state changed");
    } else if (couponUsageMapper.releaseCoupon(locked.coupon().id(), at) != 1)
      throw new IllegalStateException("coupon release state changed");
    if (couponUsageMapper.finishUsage(locked.usage().id(), status, at) != 1)
      throw new IllegalStateException("coupon usage state changed");
    couponUsageMapper.finishAllocations(
        locked.usage().id(), returned && formed ? TradingState.returned.name() : "voided", at);
    couponUsageMapper.insertOperationLog(
        new CouponWrites.Log(
            Ids.next(),
            (returned ? "return:" : "release:") + locked.usage().id(),
            locked.coupon().id(),
            locked.usage().id(),
            status,
            locked.coupon().couponStatus(),
            returned
                ? TradingState.returned.name()
                : locked.coupon().validUntil().isAfter(at)
                    ? TradingState.available.name()
                    : TradingState.expired.name(),
            SystemIdentity.SYSTEM_USER_ID,
            locked.usage().checkoutBatchId(),
            returned ? "原付款全部现金已返清" : "全未付安全关闭释放",
            at));
  }
}
