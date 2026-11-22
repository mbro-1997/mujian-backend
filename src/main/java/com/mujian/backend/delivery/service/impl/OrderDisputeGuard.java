package com.mujian.backend.delivery.service.impl;

import com.mujian.backend.delivery.mapper.DisputeMapper;
import com.mujian.backend.delivery.service.DisputeGuard;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class OrderDisputeGuard implements DisputeGuard {
  private final DisputeMapper disputeMapper;

  /** 共用同一组事实条件生成详情和队列。 */
  public OrderDisputeGuard(DisputeMapper disputeMapper) {
    this.disputeMapper=disputeMapper;
  }

  /** 判断终止资金是否已完成处置。 */
  @Override public boolean terminationSettled(String orderId){return disputeMapper.terminationSettled(orderId);}

  /** 无订单无法认定无争议；数据库读取失败交给现有依赖异常处理。 */
  @Override
  public Facts inspect(String orderId) {
    Facts facts=disputeMapper.selectFactsByOrderId(orderId);
    return facts==null ? new Facts(false,false,false,false) : facts;
  }

  /** 按页建立索引，空页无需读取争议表。 */
  @Override
  public java.util.Map<String,Facts> pageFacts(java.util.List<String> ids) {
    java.util.Map<String,Facts> result = new java.util.HashMap<>();
    if (!ids.isEmpty()) {
      for (DisputeMapper.OrderFacts orderFacts : disputeMapper.selectPageFacts(ids)) {
        result.put(orderFacts.id(), new Facts(orderFacts.ready(), orderFacts.paused(), orderFacts.claimActive(), orderFacts.terminationActive()));
      }
    }
    return result;
  }

  /** 已知阻断与来源不完整的订单一并从普通待办剔除。 */
  @Override
  public Optional<Set<String>> blockedOrders(String buyerId,String factoryId) {
    return Optional.of(disputeMapper.selectBlockedOrderIds(buyerId,factoryId));
  }
}
