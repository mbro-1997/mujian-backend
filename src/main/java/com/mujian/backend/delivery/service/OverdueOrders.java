package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.now;

import com.mujian.backend.casework.service.FulfillmentRiskCase;
import com.mujian.backend.common.enums.FulfillmentEventType;
import com.mujian.backend.common.enums.FulfillmentRole;
import com.mujian.backend.common.enums.TradingResource;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.common.support.SystemIdentity;
import com.mujian.backend.delivery.mapper.OverdueMapper;
import com.mujian.backend.factory.service.FactoryRequests;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.service.TradingRoots;
import com.mujian.backend.prop.mapper.PropSkuStockMapper;
import com.mujian.backend.prop.projection.PropQuantityProjection;
import com.mujian.backend.prop.service.TradingReservations;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** 只消费已在租订单的持久逾期事件；普通缺货不进入履约风险队列。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class OverdueOrders {
  private final RentalOrderMapper rentalOrderMapper;
  private final TradingRoots tradingRoots;
  private final TradingReservations tradingReservations;
  private final PropSkuStockMapper propSkuStockMapper;
  private final FulfillmentRiskCase fulfillmentRiskCase;
  private final OverdueMapper overdueMapper;
  private final FulfillmentEvents fulfillmentEvents;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  /** 装配逾期保护及风险处理所需依赖。 */
  public OverdueOrders(
      RentalOrderMapper rentalOrderMapper,
      TradingRoots tradingRoots,
      TradingReservations tradingReservations,
      PropSkuStockMapper propSkuStockMapper,
      OverdueMapper overdueMapper,
      FulfillmentRiskCase fulfillmentRiskCase,
      FulfillmentEvents fulfillmentEvents,
      Clock clock,
      ObjectMapper objectMapper) {
    this.rentalOrderMapper = rentalOrderMapper;
    this.tradingRoots = tradingRoots;
    this.tradingReservations = tradingReservations;
    this.propSkuStockMapper = propSkuStockMapper;
    this.fulfillmentRiskCase = fulfillmentRiskCase;
    this.overdueMapper = overdueMapper;
    this.fulfillmentEvents = fulfillmentEvents;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  /** 保护库存不依赖异议结果，不能在此方法中生成退押或正常关闭事实。 */
  public void transition(String orderId) {
    RentalOrderDO order = rentalOrderMapper.selectOrderById(orderId);
    if (order == null) return;
    tradingRoots.lock(order.checkoutBatchId());
    order = rentalOrderMapper.selectOrderById(orderId);
    tradingReservations.lockExisting(order.checkoutBatchId());
    LocalDateTime at = now(clock);
    if (overdueMapper.markOverdue(orderId, at) == 0) return;
    fulfillmentEvents.append(
        "rental:" + orderId + ":overdue",
        orderId,
        null,
        TradingResource.rental_order.name(),
        orderId,
        FulfillmentEventType.rental_overdue,
        order.currentDueDate().plusDays(1).atStartOfDay(),
        SystemIdentity.SYSTEM_USER_ID,
        FulfillmentRole.system,
        "有效租期已结束，道具仍在租");
  }

  /** 仅消费已登记风险；原库存或后续订单提供解除依据，沟通记录不参与计算。 */
  public void reconcileRisk(String riskId) {
    OverdueMapper.RiskSource riskSource=overdueMapper.selectRiskSource(riskId);
    if (riskSource==null) return;
    for (String orderId : new TreeSet<>(List.of(riskSource.sourceId(),riskSource.affectedId()))) overdueMapper.lockOrder(orderId);
    RentalOrderDO affected=rentalOrderMapper.selectOrderById(riskSource.affectedId());
    tradingReservations.lockExisting(affected.checkoutBatchId());
    OverdueMapper.RiskSource risk=overdueMapper.selectRiskSource(riskId);
    if (risk==null || !List.of("pending","processing").contains(risk.status())) return;
    LocalDateTime at=now(clock);
    String reason=null;
    if ("released".equals(risk.sourceStatus()) || "completed".equals(risk.sourceStatus())) reason="来源订单实物占用已释放";
    else if (List.of("manual_rejected","timeout_rejected","user_cancelled","cancelled","fulfilling","return_checking","completed")
        .contains(risk.affectedStatus())) reason="后续订单已取消或已完成出库";
    else if (risk.snId()==null && risk.startDate()!=null && !risk.endDate().isBefore(at.toLocalDate())) {
      java.time.LocalDate start=risk.startDate().isBefore(at.toLocalDate()) ? at.toLocalDate() : risk.startDate();
      List<PropQuantityProjection> balances=propSkuStockMapper.selectStockBalanceBySkuIdsAndDates(
          List.of(risk.skuId()),start,risk.endDate(),at.toLocalDate());
      if (balances.size()==1 && balances.get(0).getAmount().signum()>=0) reason="可用数量已覆盖受影响订单的占用区间";
    }
    if (reason!=null) overdueMapper.resolveRiskBySource(riskId,at,reason,SystemIdentity.SYSTEM_USER_ID);
  }

  /** 收集后按订单ID锁住全体，再取库存根重读；候选变化回滚到下一次事件重放。 */
  public void consume(String eventId) {
    OverdueMapper.Event overdueEvent = overdueMapper.selectEvent(eventId, false);
    if (overdueEvent == null) return;
    LocalDateTime at = now(clock);
    TreeSet<String> ids = new TreeSet<String>();
    ids.add(overdueEvent.orderId());
    for (OverdueMapper.Candidate overdueCandidate :
        overdueMapper.selectCandidates(overdueEvent.orderId(), at.toLocalDate())) ids.add(overdueCandidate.orderId());
    for (String id : ids) overdueMapper.lockOrder(id);
    OverdueMapper.Event event = overdueMapper.selectEvent(eventId, true);
    if (event == null) return;
    RentalOrderDO source = rentalOrderMapper.selectOrderById(event.orderId());
    tradingReservations.lockExisting(source.checkoutBatchId());
    for (OverdueMapper.Candidate overdueCandidateRow :
        overdueMapper.selectCandidates(source.id(), at.toLocalDate())) {
      if (!ids.contains(overdueCandidateRow.orderId())) throw new IllegalStateException("逾期事件影响集合变化，等待重新领取");
      if (overdueCandidateRow.snId() == null) {
        List<PropQuantityProjection> balances =
            propSkuStockMapper.selectStockBalanceBySkuIdsAndDates(
                List.of(overdueCandidateRow.skuId()), overdueCandidateRow.startDate(), overdueCandidateRow.endDate(), at.toLocalDate());
        if (balances.size() != 1) throw new IllegalStateException("逾期事件缺少数量库存");
        BigDecimal balance = balances.get(0).getAmount();
        if (balance.signum() >= 0
            || balance.add(BigDecimal.valueOf(overdueCandidateRow.sourceQuantity())).signum() < 0) continue;
      }
      String key =
          FactoryRequests.sha(
              objectMapper.writeValueAsString(
                  Arrays.asList("v1", source.id(), overdueCandidateRow.itemId(), overdueCandidateRow.skuId(), overdueCandidateRow.snId())));
      String riskId = overdueMapper.selectRiskIdByKey(key);
      if (riskId == null) {
        riskId = Ids.next();
        overdueMapper.insertRisk(riskId, key, source.id(), overdueCandidateRow, at);
      }
      fulfillmentRiskCase.register(riskId,source,overdueCandidateRow.orderId(),at);
    }
    if (overdueMapper.markProcessed(eventId, at) != 1) throw new IllegalStateException("逾期事件已经处理");
  }
}
