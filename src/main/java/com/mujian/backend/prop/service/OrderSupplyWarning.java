package com.mujian.backend.prop.service;

import com.mujian.backend.prop.domain.InventoryReservationDO;
import com.mujian.backend.prop.domain.PropSnInstanceDO;
import com.mujian.backend.prop.projection.PropQuantityProjection;

import com.mujian.backend.prop.mapper.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;

/** 已付订单检查原分配实物及同一日期峰值，不用“剩余可租为零”误报已占用订单。 */
@Service
public class OrderSupplyWarning {
  private final java.time.Clock clock;
  private final InventoryReservationMapper inventoryReservationMapper;
  private final PropSnInstanceMapper propSnInstanceMapper;
  private final PropSkuStockMapper propSkuStockMapper;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public OrderSupplyWarning(
      InventoryReservationMapper inventoryReservationMapper,
      PropSnInstanceMapper propSnInstanceMapper,
      PropSkuStockMapper propSkuStockMapper,
      java.time.Clock clock) {
    this.clock = clock;
    this.inventoryReservationMapper = inventoryReservationMapper;
    this.propSnInstanceMapper = propSnInstanceMapper;
    this.propSkuStockMapper = propSkuStockMapper;
  }

  /** 只读取既定逾期供货风险，不扩展为一般库存不足告警。 */
  public boolean warning(String batchId, String orderId, LocalDate start, LocalDate end) {
    Set<String> snIds = new TreeSet<>(), skuIds = new TreeSet<>();
    for (InventoryReservationDO reservation : inventoryReservationMapper.selectReservationsByBatch(batchId))
      if (orderId.equals(reservation.getRentalOrderId())
          && "paid_locked".equals(reservation.getReservationStatus())) {
        if (reservation.getSnId() != null) snIds.add(reservation.getSnId());
        else skuIds.add(reservation.getSkuId());
      }
    if (!snIds.isEmpty()) {
      List<PropSnInstanceDO> instances = propSnInstanceMapper.selectPropSnInstanceListByIds(new ArrayList<>(snIds));
      if (instances.size() != snIds.size()) return true;
      for (PropSnInstanceDO instance : instances)
        if (!"available".equals(instance.getInstanceStatus())) return true;
    }
    if (!skuIds.isEmpty())
      for (PropQuantityProjection balance :
          propSkuStockMapper.selectStockBalanceBySkuIdsAndDates(
              skuIds,
              start,
              end.plusDays(1),
              com.mujian.backend.common.support.D2Values.now(clock).toLocalDate()))
        if (balance.getAmount().signum() < 0) return true;
    return false;
  }
}
