package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.order.domain.CheckoutBatchDO;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.mapper.*;
import com.mujian.backend.order.projection.CheckoutFactoryProjection;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import java.time.Clock;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 支付相关按钮只保存原批次意图，渠道调用交给事务外统一执行器。 */
@Service
public class CheckoutPaymentCommands {
  private final TradingOperations tradingOperations;
  private final TradingRoots tradingRoots;
  private final CheckoutBatchMapper checkoutBatchMapper;
  private final PaymentOrderMapper paymentOrderMapper;
  private final FactoryAccess factoryAccess;
  private final CheckoutMapper checkoutMapper;
  private final Clock clock;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public CheckoutPaymentCommands(
      TradingOperations tradingOperations,
      TradingRoots tradingRoots,
      CheckoutBatchMapper checkoutBatchMapper,
      PaymentOrderMapper paymentOrderMapper,
      FactoryAccess factoryAccess,
      CheckoutMapper checkoutMapper,
      Clock clock) {
    this.tradingOperations = tradingOperations;
    this.tradingRoots = tradingRoots;
    this.checkoutBatchMapper = checkoutBatchMapper;
    this.paymentOrderMapper = paymentOrderMapper;
    this.factoryAccess = factoryAccess;
    this.checkoutMapper = checkoutMapper;
    this.clock = clock;
  }

  /** 锁定原业务根，核对版本和回执后提交本地意图。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt apply(TradingCommand tradingCommand, String batchId, int expectedVersion) {
    tradingOperations.authorize(tradingCommand.actor(), tradingCommand.operation());
    CheckoutBatchDO checkoutBatchDOHint = checkoutBatchMapper.selectBatchById(batchId, false);
    if (checkoutBatchDOHint == null
        || !checkoutBatchDOHint.buyerUserId().equals(tradingCommand.actor())) throw missing();
    TradingRoots.Locked locked = tradingRoots.lock(batchId);
    TradingReceipt tradingReceiptReplay = tradingOperations.replay(tradingCommand);
    if (tradingReceiptReplay != null) return tradingReceiptReplay;
    if (tradingCommand.operation() == TradingOperation.launchCheckoutPayment
        && Set.of(
                TradingState.paid.name(),
                TradingState.closed.name(),
                TradingState.expired.name(),
                TradingState.refunded_closed.name())
            .contains(locked.batch().batchStatus()))
      return tradingOperations.record(
          tradingCommand, TradingResource.checkout_batch.name(), batchId, locked.batch().version());
    if (locked.batch().version() != expectedVersion) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if (tradingCommand.operation() == TradingOperation.launchCheckoutPayment) {
      factoryAccess.context(tradingCommand.actor(), true);
      if(checkoutMapper.existsOutstandingCompensation(tradingCommand.actor()))throw conflict(TradingReason.COMPENSATION_OUTSTANDING.name());
      if (locked.payment().closeRequestedAt() != null
          || locked.batch().guardReleasedAt() != null
          || !TradingState.pending_payment.name().equals(locked.batch().batchStatus())
          || !Set.of(TradingState.pending.name(), TradingState.paying.name())
              .contains(locked.payment().paymentStatus())
          || !now(clock).isBefore(locked.payment().expireAt()))
        throw conflict(TradingReason.PAYMENT_WINDOW_EXPIRED.name());
      List<CheckoutFactoryProjection> checkoutFactoryProjectionListFactories =
          checkoutMapper.selectFactoriesByIds(checkoutBatchMapper.selectFactoryIdsByBatch(batchId));
      for (CheckoutFactoryProjection checkoutFactoryProjectionFactory :
          checkoutFactoryProjectionListFactories)
        if (!D2Codes.CODE_ACTIVE.equals(checkoutFactoryProjectionFactory.accountStatus())
            || !D2Codes.CODE_ACTIVE.equals(checkoutFactoryProjectionFactory.profitSharingStatus()))
          throw conflict(TradingReason.MERCHANT_NOT_READY.name());
    } else if (tradingCommand.operation() == TradingOperation.cancelCheckoutPayment) {
      paymentOrderMapper.requestClose(locked.payment().id(), "user_cancel", now(clock));
    } else throw new IllegalArgumentException("invalid payment command");
    if (checkoutBatchMapper.touchBatch(batchId, expectedVersion, now(clock)) != 1)
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    return tradingOperations.record(
        tradingCommand, TradingResource.checkout_batch.name(), batchId, expectedVersion + 1);
  }
}
