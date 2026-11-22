package com.mujian.backend.delivery.service;

import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.vo.TradingReceipt;

/** 去程出库和收货的交易边界。 */
public interface HandoverCommands {
  /** 全项/留证及原分配齐全后一个事务交接。 */
  TradingReceipt dispatch(TradingCommand command, String orderId, int version);

  /** 买家可在核验窗开始前主动收货。 */
  TradingReceipt receive(TradingCommand command, String orderId, int version);

  /** 任务重入按原截止重查门禁和状态，只有到期未暂停才自动。 */
  boolean receiveDue(String orderId);
}
