package com.mujian.backend.delivery.service;

import com.mujian.backend.delivery.dto.FulfillmentDtos;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.vo.TradingReceipt;

/** 工厂出库命令共用订单归属、根版本与原交易回执。 */
public interface OutboundCommands {
  /** 保存出库备注与已有核对进度。 */
  TradingReceipt save(
      TradingCommand tradingCommand, String orderId, FulfillmentDtos.OutboundDraft outboundDraft);

  /** 设置本单B类明细已核对总量。 */
  TradingReceipt quantity(
      TradingCommand tradingCommand, String orderId, FulfillmentDtos.QuantityInput quantityInput);
}
