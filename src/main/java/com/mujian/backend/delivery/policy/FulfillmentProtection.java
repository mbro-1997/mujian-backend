package com.mujian.backend.delivery.policy;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.FulfillmentReason;
import com.mujian.backend.delivery.service.DisputeGuard;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 开放开关和真实争议能力同时满足才允许正常履约写入与收尾。 */
@Component
public class FulfillmentProtection {
  private final DisputeGuard disputeGuard;
  private final boolean enabled;
  /** 根据已知争议事实判断是否允许结算。 */
  public boolean canSettle(String order){
    DisputeGuard.Facts facts=facts(order);
    if(!enabled || !facts.ready() || facts.paused() || facts.claimActive())return false;
    if(!facts.terminationActive())return true;
    return disputeGuard.terminationSettled(order);
  }

  /** 默认关闭；测试只能通过测试Bean提供已知事实，生产配置不能伪造能力齐备。 */
  public FulfillmentProtection(
      DisputeGuard disputeGuard, @Value("${mj.fulfillment.enabled:false}") boolean enabled) {
    this.disputeGuard = disputeGuard;
    this.enabled = enabled;
  }

  /** 读取能力用于上下文投影，不推进任何领域状态。 */
  public DisputeGuard.Facts facts(String orderId) {
    return disputeGuard.inspect(orderId);
  }

  /** 共享本页订单的只读事实。 */
  public java.util.Map<String,DisputeGuard.Facts> pageFacts(java.util.List<String> ids) {
    return disputeGuard.pageFacts(ids);
  }

  /** SQL列表只在整个授权范围的保护事实可查询时开放正常待办。 */
  public java.util.Optional<java.util.Set<String>> blockedOrders(String buyerId, String factoryId) {
    return enabled ? disputeGuard.blockedOrders(buyerId, factoryId) : java.util.Optional.empty();
  }

  /** 页面动作与写入采用同一开放及互斥判定，配置关闭时不展示可执行按钮。 */
  public boolean writable(DisputeGuard.Facts facts) {
    return enabled
        && facts.ready()
        && !facts.paused()
        && !facts.claimActive()
        && !facts.terminationActive();
  }

  /** 判断终止中的订单是否仍可执行归还。 */
  public boolean terminationReturnAllowed(DisputeGuard.Facts facts) {
    return enabled && facts.ready() && !facts.claimActive() && facts.terminationActive();
  }

  /** 终止专用回收仍需开关、可信来源和无生效索赔。 */
  public boolean readyForFee(DisputeGuard.Facts facts) {
    return enabled && facts.ready();
  }

  /** 核责可与未结争议共存，真实收尾开关仍须开放。 */
  public void requireReady(String orderId) {
    if(!readyForFee(facts(orderId)))throw unavailable(FulfillmentReason.FULFILLMENT_NOT_OPEN.name());
  }

  /** 阻止争议事实不允许的归还命令。 */
  public boolean requireReturn(String orderId) {
    DisputeGuard.Facts facts=disputeGuard.inspect(orderId);
    if (!enabled || !facts.ready()) throw unavailable(FulfillmentReason.FULFILLMENT_NOT_OPEN.name());
    if (facts.claimActive()) throw conflict(FulfillmentReason.RETURN_CLAIM_EXISTS.name());
    if (!facts.terminationActive() && facts.paused()) throw conflict(FulfillmentReason.FULFILLMENT_PAUSED.name());
    return facts.terminationActive();
  }

  /** 任何资金或实物写入前，在订单根锁内核实当前保护事实。 */
  public void requireNormal(String orderId) {
    DisputeGuard.Facts facts = disputeGuard.inspect(orderId);
    if (!enabled || !facts.ready())
      throw unavailable(FulfillmentReason.FULFILLMENT_NOT_OPEN.name());
    if (facts.claimActive()) throw conflict(FulfillmentReason.RETURN_CLAIM_EXISTS.name());
    if (facts.paused() || facts.terminationActive())
      throw conflict(FulfillmentReason.FULFILLMENT_PAUSED.name());
  }
}
