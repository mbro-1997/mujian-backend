package com.mujian.backend.order.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.order.domain.*;
import com.mujian.backend.order.domain.CheckoutBatchDO;
import com.mujian.backend.order.domain.TradingSnapshots;
import com.mujian.backend.order.mapper.CheckoutBatchMapper;
import com.mujian.backend.order.policy.CancellationCalculation;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.CancellationQuoteVO;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 预览与执行共用取消计算，跨档位或接单变化不能沿旧确认金额取消。 */
@Service
public class OrderCancellationQuotationImpl implements OrderCancellationQuotation {
  private final RentalOrderAccess rentalOrderAccess;
  private final CheckoutBatchMapper checkoutBatchMapper;
  private final TradingTokens tradingTokens;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public OrderCancellationQuotationImpl(
      RentalOrderAccess rentalOrderAccess,
      CheckoutBatchMapper checkoutBatchMapper,
      TradingTokens tradingTokens,
      ObjectMapper objectMapper,
      Clock clock) {
    this.rentalOrderAccess = rentalOrderAccess;
    this.checkoutBatchMapper = checkoutBatchMapper;
    this.tradingTokens = tradingTokens;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 读取原订单和当前取消档位，返回短期确认凭据。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public CancellationQuoteVO quote(String actor, String orderId, int expectedVersion) {
    RentalOrderDO rentalOrderDO = rentalOrderAccess.owned(actor, orderId, false, false);
    if (rentalOrderDO.version() != expectedVersion) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    rentalOrderAccess.cancellable(rentalOrderDO);
    CancellationQuoteData cancellationQuoteDataData = data(rentalOrderDO);
    CancellationCalculation.Result resultAmounts = cancellationQuoteDataData.amounts();
    return new CancellationQuoteVO(
        rentalOrderDO.id(),
        rentalOrderDO.version(),
        tradingTokens.cancellation(cancellationQuoteDataData),
        resultAmounts.deductionFen(),
        resultAmounts.rentRefundFen(),
        resultAmounts.depositRefundFen(),
        resultAmounts.freightRefundFen(),
        resultAmounts.totalRefundFen(),
        resultAmounts.rateBps(),
        time(cancellationQuoteDataData.expiresAt()));
  }

  /** 传入已加锁订单时只读取原规则快照，不调用任何外部服务。 */
  public CancellationQuoteData data(RentalOrderDO rentalOrderDO) {
    CheckoutBatchDO checkoutBatchDOBatch =
        checkoutBatchMapper.selectBatchById(rentalOrderDO.checkoutBatchId(), false);
    TradingSnapshots.Rule rule =
        objectMapper.readValue(
            checkoutBatchDOBatch.ruleSnapshotJson(), TradingSnapshots.Rule.class);
    CancellationCalculation.Result resultAmounts =
        CancellationCalculation.calculate(
            TradingState.accepted.name().equals(rentalOrderDO.acceptStatus()),
            rentalOrderDO.rentalStartDate(),
            now(clock),
            rentalOrderDO.rentOriginalAmountFen(),
            rentalOrderDO.rentPaidAmountFen(),
            rentalOrderDO.depositPaidAmountFen(),
            rentalOrderDO.freightPrepaidPaidAmountFen());
    return new CancellationQuoteData(
        1,
        rentalOrderDO.buyerUserId(),
        rentalOrderDO.id(),
        rentalOrderDO.version(),
        rule.cancellationRule(),
        rule.cancellationRuleVersion(),
        resultAmounts,
        now(clock),
        now(clock).plusMinutes(5));
  }
}
