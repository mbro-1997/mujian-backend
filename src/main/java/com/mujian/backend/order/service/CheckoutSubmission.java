package com.mujian.backend.order.service;

import com.mujian.backend.order.dto.*;
import com.mujian.backend.order.vo.*;

/** 租赁结算批次提交与付款命令。 */
public interface CheckoutSubmission {
  /** 根据已确认报价提交合并付款批次。 */
  BatchViews.Submission submit(TradingCommand tradingCommand, CheckoutDtos.Submit submitInput);

  /** 按批次版本发起原合并付款。 */
  BatchViews.Launch launch(
      TradingCommand tradingCommand,
      String batchId,
      CheckoutDtos.ExpectedVersion expectedVersionInput);

  /** 按原批次和版本取消未完成付款。 */
  TradingReceipt cancel(
      TradingCommand tradingCommand,
      String batchId,
      CheckoutDtos.ExpectedVersion expectedVersionInput);
}
