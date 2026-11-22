package com.mujian.backend.prop.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.FulfillmentReason;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.prop.domain.*;
import com.mujian.backend.prop.mapper.*;
import com.mujian.backend.prop.projection.PropQuantityProjection;
import com.mujian.backend.prop.utils.PropJson;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 出库与归还只转换本单实际分配，沿既有库存锁及流水保存实际效果。 */
@Service
public class FulfillmentInventory {
  private final TradingReservations tradingReservations;
  private final InventoryReservationMapper inventoryReservationMapper;
  private final PropSkuStockMapper propSkuStockMapper;
  private final PropSnInstanceMapper propSnInstanceMapper;
  private final PropInstanceStatusLogMapper propInstanceStatusLogMapper;
  private final PropStockMovementMapper propStockMovementMapper;
  private final PropInventory propInventory;
  private final PropJson propJson;
  private final Clock clock;

  /** 复用库存快照、锁序和峰值，不另建履约库存账。 */
  public FulfillmentInventory(
      TradingReservations tradingReservations,
      InventoryReservationMapper inventoryReservationMapper,
      PropSkuStockMapper propSkuStockMapper,
      PropSnInstanceMapper propSnInstanceMapper,
      PropInstanceStatusLogMapper propInstanceStatusLogMapper,
      PropStockMovementMapper propStockMovementMapper,
      PropInventory propInventory,
      PropJson propJson,
      Clock clock) {
    this.tradingReservations = tradingReservations;
    this.inventoryReservationMapper = inventoryReservationMapper;
    this.propSkuStockMapper = propSkuStockMapper;
    this.propSnInstanceMapper = propSnInstanceMapper;
    this.propInstanceStatusLogMapper = propInstanceStatusLogMapper;
    this.propStockMovementMapper = propStockMovementMapper;
    this.propInventory = propInventory;
    this.propJson = propJson;
    this.clock = clock;
  }

  /** 核对完整原分配后逐笔转换，本事务中任一失败全部回滚。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void transfer(
      RentalOrderDO order,
      List<FulfillmentViews.CheckItem> items,
      String actor,
      String role,
      boolean outbound) {
    List<InventoryReservationDO> rows =
        tradingReservations.lockExisting(order.checkoutBatchId()).stream()
            .filter(row -> order.id().equals(row.getRentalOrderId()))
            .toList();
    if (!outbound)
      rows = inventoryReservationMapper.selectOrderReservationsIncludingHistory(order.id());
    Map<String, Integer> actual = new HashMap<>();
    for (InventoryReservationDO inventoryReservationDO : rows) {
      if (outbound
          ? !"paid_locked".equals(inventoryReservationDO.getReservationStatus())
          : !Set.of("renting", "overdue_holding", "completed").contains(inventoryReservationDO.getReservationStatus()))
        throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
      actual.merge(inventoryReservationDO.getRentalOrderItemId(), inventoryReservationDO.getQuantity(), Math::addExact);
    }
    if (items.isEmpty() || items.size() != actual.size())
      throw new IllegalStateException("original inventory allocation missing");
    for (FulfillmentViews.CheckItem item : items)
      if (!Objects.equals(actual.get(item.itemId()), item.expectedQuantity()))
        throw new IllegalStateException("original inventory quantity mismatch");
    LocalDateTime at = now(clock);
    for (InventoryReservationDO inventoryReservationDORow : rows) {
      if (!outbound && "completed".equals(inventoryReservationDORow.getReservationStatus())) continue;
      if (outbound && at.toLocalDate().isBefore(inventoryReservationDORow.getOccupyStartDate()))
        validateEarlyInterval(inventoryReservationDORow, at.toLocalDate());
      if (inventoryReservationDORow.getSnId() == null) transferQuantity(inventoryReservationDORow, actor, role, outbound, at);
      else transferInstance(inventoryReservationDORow, actor, role, outbound, at);
    }
  }

  /** 实际提前交接时只核对新增占用区间，保留合同日期和原结束缓冲。 */
  private void validateEarlyInterval(InventoryReservationDO inventoryReservationDO, LocalDate today) {
    LocalDate end = inventoryReservationDO.getOccupyStartDate().minusDays(1);
    if (inventoryReservationDO.getSnId() != null) {
      if (inventoryReservationMapper.hasOtherSnOccupancy(
          inventoryReservationDO.getSnId(), inventoryReservationDO.getId(), today, end, today))
        throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    } else {
      List<PropQuantityProjection> result =
          propSkuStockMapper.selectStockOccupancyListBySkuIdsAndDates(
              List.of(inventoryReservationDO.getSkuId()), today, end, today);
      if (result.size() != 1 || result.get(0).getAmount().intValueExact() < inventoryReservationDO.getQuantity())
        throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    }
  }

  /** 单条原占用通过状态条件，拒绝重复或来自其它订单的转换。 */
  private void move(InventoryReservationDO inventoryReservationDO, boolean outbound, LocalDateTime at) {
    if (inventoryReservationMapper.transitionFulfillment(
            inventoryReservationDO.getId(), inventoryReservationDO.getRentalOrderId(), inventoryReservationDO.getReservationStatus(), outbound, at)
        != 1) throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
  }

  /** B类记录实际占用变化前后快照，不调整总量。 */
  private void transferQuantity(
      InventoryReservationDO inventoryReservationDO, String actor, String role, boolean outbound, LocalDateTime at) {
    PropSkuStockDO before = propInventory.stock(inventoryReservationDO.getSkuId(), false);
    move(inventoryReservationDO, outbound, at);
    tradingReservations.refreshCurrent(List.of(inventoryReservationDO.getSkuId()));
    PropSkuStockDO after = propInventory.stock(inventoryReservationDO.getSkuId(), false);
    String type = outbound ? "outbound" : "return";
    propStockMovementMapper.insertPropStockMovement(
        new PropStockMovementDO()
            .id(Ids.next())
            .skuId(inventoryReservationDO.getSkuId())
            .movementType(type)
            .sourceType("order_fulfillment")
            .quantity(inventoryReservationDO.getQuantity())
            .beforeQuantity(before.getRentingQuantity())
            .afterQuantity(after.getRentingQuantity())
            .beforeStockSnapshotJson(propJson.write(propInventory.stockSnapshot(before)))
            .afterStockSnapshotJson(propJson.write(propInventory.stockSnapshot(after)))
            .relatedOrderId(inventoryReservationDO.getRentalOrderId())
            .relatedReservationId(inventoryReservationDO.getId())
            .operatorUserId(actor)
            .operatorRole(role)
            .remark(outbound ? "订单出库" : "订单归还")
            .businessKey(type + ":" + inventoryReservationDO.getId())
            .createdAt(at));
  }

  /** A类归还只清本单关联，独立维修/丢失状态保留。 */
  private void transferInstance(
      InventoryReservationDO inventoryReservationDO, String actor, String role, boolean outbound, LocalDateTime at) {
    PropSnInstanceDO propSnInstanceDO = propInventory.sn(inventoryReservationDO.getSnId(), false);
    String from = propSnInstanceDO.getInstanceStatus();
    if (outbound
        && (!Set.of("idle", "locked").contains(from)
            || propSnInstanceDO.getCurrentOrderId() != null
                && !propSnInstanceDO.getCurrentOrderId().equals(inventoryReservationDO.getRentalOrderId())))
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    if (!outbound
        && propSnInstanceDO.getCurrentOrderId() != null
        && !propSnInstanceDO.getCurrentOrderId().equals(inventoryReservationDO.getRentalOrderId()))
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    String to = outbound ? "renting" : ("renting".equals(from) ? "idle" : from);
    move(inventoryReservationDO, outbound, at);
    if (propSnInstanceMapper.transitionFulfillment(
            propSnInstanceDO.getId(), propSnInstanceDO.getVersion(), to, outbound ? inventoryReservationDO.getRentalOrderId() : null, actor, at)
        != 1) throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    propInstanceStatusLogMapper.insertPropInstanceStatusLog(
        new PropInstanceStatusLogDO()
            .id(Ids.next())
            .propInstanceId(propSnInstanceDO.getId())
            .skuId(propSnInstanceDO.getSkuId())
            .snCode(propSnInstanceDO.getSnCode())
            .qrCodeId(propSnInstanceDO.getQrCodeId())
            .fromStatus(from)
            .toStatus(to)
            .sourceType("order_fulfillment")
            .reason(outbound ? "订单出库" : "订单归还")
            .operatorUserId(actor)
            .operatorRole(role)
            .relatedOrderId(inventoryReservationDO.getRentalOrderId())
            .businessKey((outbound ? "outbound:" : "return:") + inventoryReservationDO.getId())
            .createdAt(at));
  }
}
