package com.mujian.backend.prop.service;

import com.mujian.backend.common.enums.PropReasonCode;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.prop.domain.*;
import com.mujian.backend.prop.dto.*;
import com.mujian.backend.prop.mapper.*;
import com.mujian.backend.prop.policy.PropAvailability;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 交易调用前必须持有用户、工厂、清单和支付来源根锁。 */
@Service
public class TradingReservations {
  private final PropSkuMapper propSkuMapper;
  private final PropSkuStockMapper propSkuStockMapper;
  private final PropSnInstanceMapper propSnInstanceMapper;
  private final InventoryReservationMapper inventoryReservationMapper;
  private final PropAvailability propAvailability;
  private final Clock clock;

  /** 复用A/B库存根和既有峰值算法。 */
  public TradingReservations(
      PropSkuMapper propSkuMapper,
      PropSkuStockMapper propSkuStockMapper,
      PropSnInstanceMapper propSnInstanceMapper,
      InventoryReservationMapper inventoryReservationMapper,
      PropAvailability propAvailability,
      Clock clock) {
    this.propSkuMapper = propSkuMapper;
    this.propSkuStockMapper = propSkuStockMapper;
    this.propSnInstanceMapper = propSnInstanceMapper;
    this.inventoryReservationMapper = inventoryReservationMapper;
    this.propAvailability = propAvailability;
    this.clock = clock;
  }

  /** 全部SKU顺序锁定后选SN或核对数量，返回本次确定分配。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<ReservationAssignment> allocate(
      List<ReservationItem> reservationItemListItems, LocalDate start, LocalDate end) {
    List<ReservationItem> reservationItemListSorted = new ArrayList<>(reservationItemListItems);
    reservationItemListSorted.sort(Comparator.comparing(ReservationItem::skuId));
    Map<String, PropSkuDO> propSkuDOMapSkus = new HashMap<>();
    for (ReservationItem reservationItemItem : reservationItemListSorted) {
      if (reservationItemItem.quantity() < 1
          || propSkuDOMapSkus.containsKey(reservationItemItem.skuId()))
        throw new IllegalArgumentException("invalid reservation item");
      PropSkuDO propSkuDOSku = propSkuMapper.selectPropSku(reservationItemItem.skuId(), true);
      if (propSkuDOSku == null) throw missing();
      propSkuDOMapSkus.put(reservationItemItem.skuId(), propSkuDOSku);
    }
    List<ReservationAssignment> reservationAssignmentListAssignments = new ArrayList<>();
    for (ReservationItem reservationItemItem : reservationItemListSorted) {
      PropSkuDO propSkuDOSku = propSkuDOMapSkus.get(reservationItemItem.skuId());
      List<String> sns = List.of();
      if ("A_ASSET".equals(propSkuDOSku.getAssetType())) {
        sns =
            propSnInstanceMapper.selectAvailableSnIdsForUpdate(
                reservationItemItem.skuId(),
                start,
                end.plusDays(1),
                reservationItemItem.quantity(),
                now(clock).toLocalDate());
        if (sns.size() != reservationItemItem.quantity()) throw conflict(PropReasonCode.STOCK_NOT_ENOUGH.name());
      } else {
        if (propSkuStockMapper
                .selectPropSkuStockListBySkuId(reservationItemItem.skuId(), true)
                .size()
            != 1) throw missing();
        int available =
            propAvailability
                .quantities(
                    List.of(reservationItemItem.skuId()), new PropAvailability.Dates(start, end))
                .getOrDefault(reservationItemItem.skuId(), 0);
        if (available < reservationItemItem.quantity()) throw conflict(PropReasonCode.STOCK_NOT_ENOUGH.name());
      }
      reservationAssignmentListAssignments.add(
          new ReservationAssignment(
              reservationItemItem.skuId(),
              reservationItemItem.snapshotItemId(),
              reservationItemItem.quantity(),
              List.copyOf(sns)));
    }
    return List.copyOf(reservationAssignmentListAssignments);
  }

  /** 创建支付短锁；日期只将合同结束加一天，未知支付不凭expires_at排除。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void hold(
      String batchId,
      List<ReservationAssignment> reservationAssignmentListAssignments,
      LocalDate start,
      LocalDate end,
      LocalDateTime expires) {
    LocalDateTime at = now(clock);
    for (ReservationAssignment reservationAssignmentAssignment :
        reservationAssignmentListAssignments) {
      List<String> snIds =
          reservationAssignmentAssignment.snIds().isEmpty()
              ? Collections.singletonList(null)
              : reservationAssignmentAssignment.snIds();
      for (String sn : snIds)
        inventoryReservationMapper.insertReservation(
            new InventoryReservationDO()
                .id(Ids.next())
                .skuId(reservationAssignmentAssignment.skuId())
                .snId(sn)
                .quantity(sn == null ? reservationAssignmentAssignment.quantity() : 1)
                .checkoutBatchId(batchId)
                .checkoutBatchItemSnapshotId(reservationAssignmentAssignment.snapshotItemId())
                .rentalStartDate(start)
                .rentalEndDate(end)
                .occupyStartDate(start)
                .occupyEndDate(end.plusDays(1))
                .expiresAt(expires)
                .idempotencyKey(
                    "checkout:"
                        + reservationAssignmentAssignment.snapshotItemId()
                        + ":"
                        + (sn == null ? "quantity" : sn))
                .createdAt(at)
                .updatedAt(at));
    }
    List<String> skuIds = new ArrayList<>();
    for (ReservationAssignment reservationAssignmentAssignment :
        reservationAssignmentListAssignments) skuIds.add(reservationAssignmentAssignment.skuId());
    refreshCurrent(skuIds);
  }

  /** 已有批次按SKU→SN/数量库存根顺序锁定；不重新选SN，也不依当前上架状态抹掉实收。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<InventoryReservationDO> lockExisting(String batchId) {
    List<InventoryReservationDO> inventoryReservationDOListReservations =
        inventoryReservationMapper.selectReservationsByBatch(batchId);
    Set<String> skuIds = new TreeSet<>(), snIds = new TreeSet<>(), stockIds = new TreeSet<>();
    for (InventoryReservationDO inventoryReservationDOReservation :
        inventoryReservationDOListReservations) {
      skuIds.add(inventoryReservationDOReservation.getSkuId());
      if (inventoryReservationDOReservation.getSnId() != null)
        snIds.add(inventoryReservationDOReservation.getSnId());
      else stockIds.add(inventoryReservationDOReservation.getSkuId());
    }
    for (String skuId : skuIds)
      if (propSkuMapper.selectPropSku(skuId, true) == null) throw missing();
    for (String snId : snIds)
      if (propSnInstanceMapper.selectPropSnInstance(snId, true) == null) throw missing();
    for (String skuId : stockIds)
      if (propSkuStockMapper.selectPropSkuStockListBySkuId(skuId, true).size() != 1)
        throw missing();
    return inventoryReservationMapper.selectReservationsByBatch(batchId);
  }

  /** 占用改变后同事务刷新当前日B类看板，未来可租仍复用原区间峰值。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void refreshCurrent(Collection<String> skuIds) {
    for (String skuId : new TreeSet<>(skuIds))
      propSkuStockMapper.refreshCurrentQuantities(skuId, now(clock).toLocalDate(), now(clock));
  }
}
