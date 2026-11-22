package com.mujian.backend.payment.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.infrastructure.payment.wechat.MerchantGateway;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.TradingOperations;
import com.mujian.backend.payment.mapper.MerchantMapper;
import com.mujian.backend.payment.projection.MerchantSourceProjection;
import com.mujian.backend.payment.service.*;
import com.mujian.backend.payment.vo.MerchantAccountVO;
import org.springframework.stereotype.Service;

/** 不收集未经批准的银行资料，不生成虚假托管链接或人工激活入口。 */
@Service
public class MerchantCommandsImpl implements MerchantCommands {
  private final MerchantReader merchantReader;
  private final MerchantMapper merchantMapper;
  private final TradingOperations tradingOperations;
  private final MerchantGateway merchantGateway;
  private final MerchantSyncTransaction merchantSyncTransaction;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public MerchantCommandsImpl(
      MerchantReader merchantReader,
      MerchantMapper merchantMapper,
      TradingOperations tradingOperations,
      MerchantGateway merchantGateway,
      MerchantSyncTransaction merchantSyncTransaction) {
    this.merchantReader = merchantReader;
    this.merchantMapper = merchantMapper;
    this.tradingOperations = tradingOperations;
    this.merchantGateway = merchantGateway;
    this.merchantSyncTransaction = merchantSyncTransaction;
  }

  /** 只使用获准渠道能力发起进件，缺接入能力明确不可用。 */
  public MerchantAccountVO onboarding(TradingCommand tradingCommand, int expectedVersion) {
    tradingOperations.authorize(tradingCommand.actor(), tradingCommand.operation());
    merchantReader.owned(tradingCommand.actor());
    throw unavailable(TradingReason.MERCHANT_ONBOARDING_NOT_READY.name());
  }

  /** 按可信原进件号查询并合并状态，不接受客户端传入渠道成功。 */
  public MerchantAccountVO sync(TradingCommand tradingCommand, String factoryId) {
    tradingOperations.authorize(tradingCommand.actor(), tradingCommand.operation());
    if (tradingCommand.operation() == TradingOperation.syncFactoryMerchantAccount)
      factoryId = merchantReader.factory(tradingCommand.actor());
    MerchantSourceProjection merchantSourceProjectionSource =
        merchantMapper.selectMerchantByFactory(factoryId, false);
    if (merchantSourceProjectionSource == null) throw missing();
    if (tradingOperations.replay(tradingCommand) != null)
      return merchantReader.view(merchantSourceProjectionSource);
    if (merchantSourceProjectionSource.id() == null
        || merchantSourceProjectionSource.applymentId() == null
            && merchantSourceProjectionSource.onboardingRequestNo() == null)
      throw unavailable(TradingReason.PAYMENT_CHANNEL_NOT_READY.name());
    MerchantGateway.Status statusFact =
        merchantGateway.query(
            merchantSourceProjectionSource.applymentId(),
            merchantSourceProjectionSource.onboardingRequestNo());
    merchantSyncTransaction.apply(tradingCommand, merchantSourceProjectionSource, statusFact);
    return merchantReader.view(merchantMapper.selectMerchantByFactory(factoryId, false));
  }
}
