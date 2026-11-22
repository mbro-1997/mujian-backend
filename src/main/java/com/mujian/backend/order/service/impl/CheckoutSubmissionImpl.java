package com.mujian.backend.order.service.impl;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.order.domain.CheckoutQuoteData;
import com.mujian.backend.order.dto.*;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.*;
import com.mujian.backend.order.vo.BatchViews;
import com.mujian.backend.payment.domain.PaymentOrderDO;
import com.mujian.backend.payment.dto.PaymentAccount;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import com.mujian.backend.payment.service.PaymentExecution;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 同号恢复优先于报价过期与渠道就绪检查，原提交不会被新状态重复创建。 */
@Service
public class CheckoutSubmissionImpl implements CheckoutSubmission {
  private final TradingOperations tradingOperations;
  private final TradingTokens tradingTokens;
  private final CheckoutSubmissionTransaction checkoutSubmissionTransaction;
  private final PaymentGateway paymentGateway;
  private final PaymentExecution paymentExecution;
  private final CheckoutBatchReader checkoutBatchReader;
  private final CheckoutPaymentCommands checkoutPaymentCommands;
  private final PaymentOrderMapper paymentOrderMapper;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public CheckoutSubmissionImpl(
      TradingOperations tradingOperations,
      TradingTokens tradingTokens,
      CheckoutSubmissionTransaction checkoutSubmissionTransaction,
      PaymentGateway paymentGateway,
      PaymentExecution paymentExecution,
      CheckoutBatchReader checkoutBatchReader,
      CheckoutPaymentCommands checkoutPaymentCommands,
      PaymentOrderMapper paymentOrderMapper) {
    this.tradingOperations = tradingOperations;
    this.tradingTokens = tradingTokens;
    this.checkoutSubmissionTransaction = checkoutSubmissionTransaction;
    this.paymentGateway = paymentGateway;
    this.paymentExecution = paymentExecution;
    this.checkoutBatchReader = checkoutBatchReader;
    this.checkoutPaymentCommands = checkoutPaymentCommands;
    this.paymentOrderMapper = paymentOrderMapper;
  }

  /** 恢复原请求或一次提交原报价，渠道创建发生在本地事务之后。 */
  public BatchViews.Submission submit(
      TradingCommand tradingCommand, CheckoutDtos.Submit submitInput) {
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt == null) {
      CheckoutQuoteData checkoutQuoteDataQuote =
          tradingTokens.readQuote(tradingCommand.actor(), submitInput.quoteToken());
      PaymentAccount paymentAccount = paymentGateway.requireReady();
      tradingReceipt =
          checkoutSubmissionTransaction.submit(
              tradingCommand, checkoutQuoteDataQuote, paymentAccount);
    }
    paymentExecution.execute(tradingReceipt.resourceId());
    BatchViews.Batch batch =
        checkoutBatchReader.read(tradingCommand.actor(), tradingReceipt.resourceId());
    return new BatchViews.Submission(tradingReceipt, batch, parameters(batch));
  }

  /** 继续原批次付款，不延长原截止或另建支付号。 */
  public BatchViews.Launch launch(
      TradingCommand tradingCommand,
      String batchId,
      CheckoutDtos.ExpectedVersion expectedVersionInput) {
    if (tradingOperations.replay(tradingCommand) == null
        && checkoutBatchReader.read(tradingCommand.actor(), batchId).canContinuePay())
      paymentGateway.requireReady();
    checkoutPaymentCommands.apply(tradingCommand, batchId, expectedVersionInput.expectedVersion());
    paymentExecution.execute(batchId);
    BatchViews.Batch batch = checkoutBatchReader.read(tradingCommand.actor(), batchId);
    return new BatchViews.Launch(batchId, batch.payment(), parameters(batch), batch.nextAction());
  }

  /** 保存原来源的取消意图，未知渠道结果继续原号恢复。 */
  public TradingReceipt cancel(
      TradingCommand tradingCommand,
      String batchId,
      CheckoutDtos.ExpectedVersion expectedVersionInput) {
    TradingReceipt tradingReceipt =
        checkoutPaymentCommands.apply(
            tradingCommand, batchId, expectedVersionInput.expectedVersion());
    paymentExecution.execute(batchId);
    return tradingReceipt;
  }

  /** 只对仍可支付的原prepay生成签名；任何前端支付结果都不改变本地成功状态。 */
  private PaymentGateway.Parameters parameters(BatchViews.Batch batch) {
    if (!batch.canContinuePay() || !TradingNextAction.pay.name().equals(batch.nextAction()))
      return null;
    PaymentOrderDO paymentOrderDO = paymentOrderMapper.selectPaymentByBatch(batch.id(), false);
    if (paymentOrderDO.channelPrepayId() == null
        || paymentOrderDO.closeRequestedAt() != null
        || !Set.of(TradingState.pending.name(), TradingState.paying.name())
            .contains(paymentOrderDO.paymentStatus())) return null;
    return paymentGateway.parameters(
        paymentOrderDO.channelAppId(), paymentOrderDO.channelPrepayId());
  }
}
