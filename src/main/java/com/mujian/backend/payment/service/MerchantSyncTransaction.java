package com.mujian.backend.payment.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.factory.mapper.FactoryProfileMapper;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.infrastructure.payment.wechat.MerchantGateway;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.TradingOperations;
import com.mujian.backend.payment.domain.MerchantStatusSnapshot;
import com.mujian.backend.payment.dto.MerchantWrites;
import com.mujian.backend.payment.mapper.MerchantMapper;
import com.mujian.backend.payment.projection.MerchantSourceProjection;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 渠道查询在事务外；状态同步与回执/管理审计在同一短事务内。 */
@Service
public class MerchantSyncTransaction {
  private final FactoryAccess factoryAccess;
  private final FactoryProfileMapper factoryProfileMapper;
  private final TradingOperations tradingOperations;
  private final MerchantMapper merchantMapper;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public MerchantSyncTransaction(
      FactoryAccess factoryAccess,
      FactoryProfileMapper factoryProfileMapper,
      TradingOperations tradingOperations,
      MerchantMapper merchantMapper,
      ObjectMapper objectMapper,
      Clock clock) {
    this.factoryAccess = factoryAccess;
    this.factoryProfileMapper = factoryProfileMapper;
    this.tradingOperations = tradingOperations;
    this.merchantMapper = merchantMapper;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 锁定原业务根，核对版本和回执后提交本地意图。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void apply(
      TradingCommand tradingCommand,
      MerchantSourceProjection merchantSourceProjectionOriginal,
      MerchantGateway.Status statusFact) {
    factoryAccess.lock(tradingCommand.actor(), merchantSourceProjectionOriginal.ownerId());
    factoryProfileMapper.selectFactoryProfileByOwner(
        merchantSourceProjectionOriginal.ownerId(), true);
    if (tradingOperations.replay(tradingCommand) != null) return;
    MerchantSourceProjection merchantSourceProjectionCurrent =
        merchantMapper.selectMerchantByFactory(merchantSourceProjectionOriginal.factoryId(), true);
    if (merchantSourceProjectionCurrent == null || merchantSourceProjectionCurrent.id() == null)
      throw missing();
    if (!Objects.equals(
            merchantSourceProjectionCurrent.applymentId(),
            merchantSourceProjectionOriginal.applymentId())
        || !Objects.equals(
            merchantSourceProjectionCurrent.onboardingRequestNo(),
            merchantSourceProjectionOriginal.onboardingRequestNo())
        || merchantSourceProjectionOriginal.applymentId() != null
            && !merchantSourceProjectionOriginal.applymentId().equals(statusFact.applymentId())
        || merchantSourceProjectionOriginal.onboardingRequestNo() != null
            && !merchantSourceProjectionOriginal
                .onboardingRequestNo()
                .equals(statusFact.requestNo())
        || merchantSourceProjectionCurrent.subMchid() != null
            && statusFact.subMchid() != null
            && !merchantSourceProjectionCurrent.subMchid().equals(statusFact.subMchid()))
      throw conflict(TradingReason.CHANNEL_IDENTITY_MISMATCH.name());
    if (merchantSourceProjectionCurrent.version() != merchantSourceProjectionOriginal.version()) {
      tradingOperations.record(
          tradingCommand,
          "factory_wechat_account",
          merchantSourceProjectionCurrent.id(),
          merchantSourceProjectionCurrent.version());
      return;
    }
    String status =
        switch (statusFact.state()) {
          case "CHECKING", "AUDITING" -> MerchantAccountStatus.reviewing.name();
          case "ACCOUNT_NEED_VERIFY", "NEED_SIGN" -> MerchantAccountStatus.need_action.name();
          case "REJECTED" -> "rejected";
          case "FINISH" -> D2Codes.CODE_ACTIVE;
          case "FROZEN" -> MerchantAccountStatus.frozen.name();
          case "CANCELED" -> TradingState.closed.name();
          default -> throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
        };
    String sharing =
        Set.of(MerchantAccountStatus.frozen.name(), TradingState.closed.name()).contains(status)
            ? MerchantAccountStatus.restricted.name()
            : merchantSourceProjectionCurrent.profitSharingStatus();
    MerchantStatusSnapshot merchantStatusSnapshotSnapshot =
        new MerchantStatusSnapshot(
            1,
            statusFact.applymentId(),
            statusFact.requestNo(),
            statusFact.subMchid(),
            statusFact.state(),
            statusFact.actionUrl(),
            now(clock));
    try {
      if (merchantMapper.updateMerchantStatus(
              new MerchantWrites.Sync(
                  merchantSourceProjectionCurrent.id(),
                  merchantSourceProjectionCurrent.version(),
                  statusFact.applymentId(),
                  statusFact.requestNo(),
                  statusFact.subMchid(),
                  status,
                  sharing,
                  objectMapper.writeValueAsString(merchantStatusSnapshotSnapshot),
                  now(clock)))
          != 1) throw conflict(TradingReason.CHANNEL_IDENTITY_MISMATCH.name());
    } catch (org.springframework.dao.DuplicateKeyException duplicate) {
      throw conflict(TradingReason.CHANNEL_IDENTITY_MISMATCH.name());
    }
    tradingOperations.record(
        tradingCommand,
        "factory_wechat_account",
        merchantSourceProjectionCurrent.id(),
        merchantSourceProjectionCurrent.version() + 1);
  }
}
