package com.mujian.backend.delivery.service;

import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.delivery.dto.*;
import com.mujian.backend.delivery.dto.TransportDtos.*;
import com.mujian.backend.delivery.vo.TransportViews.*;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.vo.TradingReceipt;

/** 平台车型和版本运价维护与价格单读取。 */
public interface TransportService {
  /** 分页读取车型。 */
  PageResult<Vehicle> vehicles(String actor, TransportQuery transportQuery);

  /** 读取单车型。 */
  Vehicle vehicle(String actor, String id);

  /** 创建或按版本更新车型。 */
  TradingReceipt saveVehicle(
      TradingCommand tradingCommand, String id, VehicleInput vehicleInputInput);

  /** 分页读取规则。 */
  PageResult<PriceRule> rules(String actor, TransportQuery transportQuery);

  /** 读取单规则。 */
  PriceRule rule(String actor, String id);

  /** 创建或保存草稿。 */
  TradingReceipt saveRule(TradingCommand tradingCommand, String id, RuleInput ruleInputInput);

  /** 发布草稿，校验时间及距离不重叠。 */
  TradingReceipt publish(TradingCommand tradingCommand, String id, int expected);

  /** 缩短已发布规则的未来结束时间。 */
  TradingReceipt end(TradingCommand tradingCommand, String id, EndRule endRuleInput);

  /** 当前生效价格单。 */
  PriceSheet priceSheet(String actor, String cityCode);
}
