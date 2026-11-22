package com.mujian.backend.prop.service;

import static com.mujian.backend.common.support.D2Values.now;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.prop.domain.InventoryReservationDO;
import com.mujian.backend.prop.mapper.InventoryReservationMapper;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 仅搬迁已结束占用；历史不参与实时库存计算。 */
@Service
public class ReservationArchive {
  private final InventoryReservationMapper inventoryReservationMapper;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 使用现有占用Mapper和统一时钟。 */
  public ReservationArchive(
      InventoryReservationMapper inventoryReservationMapper,
      ObjectMapper objectMapper,
      Clock clock) {
    this.inventoryReservationMapper = inventoryReservationMapper;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 小批复制、逐字段核验、删热行同事务，中断回滚且重放不重复。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public int archiveBatch() {
    List<InventoryReservationDO> inventoryReservationDOListRows =
        inventoryReservationMapper.archiveCandidates(now(clock).minusMonths(6));
    String batch = Ids.next();
    for (InventoryReservationDO inventoryReservationDORow : inventoryReservationDOListRows) {
      InventoryReservationDO inventoryReservationDOHistory =
          inventoryReservationMapper.selectInventoryReservationHistoryByOrigin(inventoryReservationDORow.getId());
      if (inventoryReservationDOHistory == null) {
        inventoryReservationMapper.copyHistory(
            Ids.next(), inventoryReservationDORow.getId(), batch, now(clock));
        inventoryReservationDOHistory =
            inventoryReservationMapper.selectInventoryReservationHistoryByOrigin(inventoryReservationDORow.getId());
      }
      if (!objectMapper
          .valueToTree(inventoryReservationDORow)
          .equals(objectMapper.valueToTree(inventoryReservationDOHistory)))
        throw new IllegalStateException("reservation archive verification failed");
      if (inventoryReservationMapper.deleteArchived(
              inventoryReservationDORow.getId(), inventoryReservationDORow.getVersion())
          != 1) throw new IllegalStateException("reservation archive source changed");
    }
    return inventoryReservationDOListRows.size();
  }
}
