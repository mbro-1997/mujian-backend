package com.mujian.backend.payment.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.payment.vo.MerchantAccountVO;

/** 工厂微信商户进件与平台状态同步。 */
public interface MerchantCommands {
  /** 按资料版本为当前工厂发起商户进件。 */
  MerchantAccountVO onboarding(TradingCommand tradingCommand, int expectedVersion);

  /** 向支付平台核对指定工厂的商户状态。 */
  MerchantAccountVO sync(TradingCommand tradingCommand, String factoryId);
}
