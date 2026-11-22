package com.mujian.backend.order.service;

import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.enums.TradingReason;

import com.mujian.backend.account.mapper.PlatformRuleDocumentMapper;
import com.mujian.backend.account.projection.TradeRuleProjection;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.TradeRuleCode;
import com.mujian.backend.order.domain.TradingSnapshots;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;

/** 提交只冻结已发布的实际规则，旧订单不随现行文档变动。 */
@Service
public class TradeRuleSnapshots {
  private final PlatformRuleDocumentMapper platformRuleDocumentMapper;

  /** 复用已有规则文档持久层。 */
  public TradeRuleSnapshots(PlatformRuleDocumentMapper platformRuleDocumentMapper) {
    this.platformRuleDocumentMapper = platformRuleDocumentMapper;
  }

  /** 信用首期未启用，押金不减免；所有生效文档版本来自数据库。 */
  public TradingSnapshots.Rule capture(String couponId, Integer templateVersion, LocalDateTime at) {
    Set<String> codes = new HashSet<>();
    for (TradeRuleCode code : TradeRuleCode.values()) codes.add(code.name());
    Map<String, TradeRuleProjection> rules = new HashMap<>();
    for (TradeRuleProjection rule : platformRuleDocumentMapper.selectActiveTradeRules(codes, at))
      rules.put(rule.ruleCode(), rule);
    if (rules.size() != codes.size())
      throw new ApiFailure(500, D2Codes.CODE_DATA_INTEGRITY_ERROR, TradingReason.TRADE_DATA_INCONSISTENT.name());
    TradeRuleProjection cancellation =
        rules.get(TradeRuleCode.rental_order_cancellation_refund_rule.name());
    TradeRuleProjection deposit = rules.get(TradeRuleCode.deposit_compensation_rule.name());
    TradeRuleProjection transport = rules.get(TradeRuleCode.logistics_transport_rule.name());
    return new TradingSnapshots.Rule(
        1,
        "CNY",
        null,
        null,
        false,
        0,
        cancellation.ruleCode(),
        cancellation.ruleVersion(),
        cancellation.id(),
        cancellation.contentHash(),
        List.of(0, 3000, 6000),
        14,
        2,
        "11:00",
        600,
        couponId,
        templateVersion,
        transport.ruleCode(),
        transport.ruleVersion(),
        deposit.ruleCode(),
        deposit.ruleVersion());
  }
}
