package com.mujian.backend.planning.service;

import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.planning.utils.PlanningValues.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.planning.domain.*;
import com.mujian.backend.planning.mapper.ChecklistMapper;
import com.mujian.backend.prop.policy.PropAvailability;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 成员完整保存及增加量校验，调用方必须持有用户/清单根锁。 */
@Service
public class ChecklistMembers {
  private final ChecklistMapper checklistMapper;
  private final PlanningSnapshots planningSnapshots;
  private final PropAvailability propAvailability;
  private final Clock clock;

  public record Entry(String itemId, String skuId, int quantity, String remark) {}

  /** 复用公共A/B库存算法。 */
  public ChecklistMembers(
      ChecklistMapper checklistMapper,
      PlanningSnapshots planningSnapshots,
      PropAvailability propAvailability,
      Clock clock) {
    this.checklistMapper = checklistMapper;
    this.planningSnapshots = planningSnapshots;
    this.propAvailability = propAvailability;
    this.clock = clock;
  }

  /** 减量、删项、备注允许整理不可租项；仅新增或增量核对当前总上限。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void replace(
      RentalChecklistDO rentalChecklistDOInitial, List<Entry> entryListEntries, boolean allowNew) {
    Map<String, RentalChecklistItemDO> rentalChecklistItemDOMapInitial = new HashMap<>();
    for (RentalChecklistItemDO rentalChecklistItemDO :
        checklistMapper.selectChecklistItemsByChecklistIds(List.of(rentalChecklistDOInitial.id())))
      rentalChecklistItemDOMapInitial.put(rentalChecklistItemDO.id(), rentalChecklistItemDO);
    Set<String> seen = new HashSet<>(), skus = new HashSet<>();
    List<String> increased = new ArrayList<>();
    for (Entry entry : entryListEntries) {
      quantity(entry.quantity());
      RentalChecklistItemDO rentalChecklistItemDO =
          entry.itemId() == null ? null : rentalChecklistItemDOMapInitial.get(entry.itemId());
      if (entry.itemId() != null
          && (rentalChecklistItemDO == null
              || !rentalChecklistItemDO.skuId().equals(entry.skuId()))) throw missing();
      if (rentalChecklistItemDO == null && !allowNew) throw missing();
      if (!skus.add(entry.skuId()) || entry.itemId() != null && !seen.add(entry.itemId()))
        throw ApiFailure.invalid();
      if (rentalChecklistItemDO == null || entry.quantity() > rentalChecklistItemDO.quantity())
        increased.add(entry.skuId());
    }
    LocalDateTime now = now(clock);
    if (!increased.isEmpty()
        && rentalChecklistDOInitial.rentalStartDate().isBefore(now.toLocalDate()))
      throw conflict(PlanningReason.RENTAL_DATE_PASSED.name());
    Map<String, PlanningSnapshots.Current> currentMapCurrent = planningSnapshots.current(increased);
    Map<String, Integer> quantities =
        increased.isEmpty()
            ? Map.of()
            : propAvailability.quantities(
                increased,
                new PropAvailability.Dates(
                    rentalChecklistDOInitial.rentalStartDate(),
                    rentalChecklistDOInitial.rentalEndDate()));
    List<RentalChecklistItemDO> rentalChecklistItemDOListReplacement = new ArrayList<>();
    int daily = 0, deposit = 0;
    for (int index = 0; index < entryListEntries.size(); index++) {
      Entry entry = entryListEntries.get(index);
      RentalChecklistItemDO rentalChecklistItemDO =
          entry.itemId() == null ? null : rentalChecklistItemDOMapInitial.get(entry.itemId());
      SnapshotV1 snapshotV1;
      Integer maximum =
          rentalChecklistItemDO == null
              ? null
              : rentalChecklistItemDO.maxRentableQuantitySnapshot();
      String reason =
          rentalChecklistItemDO == null ? null : rentalChecklistItemDO.unavailableReason();
      if (increased.contains(entry.skuId())) {
        PlanningSnapshots.Current currentFact = currentMapCurrent.get(entry.skuId());
        if (currentFact == null || !currentFact.publicVisible())
          throw conflict(PlanningReason.SKU_NOT_PUBLIC.name());
        maximum = quantities.getOrDefault(entry.skuId(), 0);
        if (entry.quantity() > maximum) throw conflict(PlanningReason.STOCK_NOT_ENOUGH.name());
        snapshotV1 = currentFact.snapshot();
        reason = null;
      } else
        snapshotV1 =
            planningSnapshots.read(
                rentalChecklistItemDO.skuSnapshotJson(),
                rentalChecklistItemDO.skuId(),
                rentalChecklistItemDO.factoryId());
      daily = add(daily, multiply(snapshotV1.prices().dailyRentPriceFen(), entry.quantity(), 1));
      deposit = add(deposit, multiply(snapshotV1.prices().depositAmountFen(), entry.quantity(), 1));
      multiply(daily, 1, rentalChecklistDOInitial.rentalDays());
      rentalChecklistItemDOListReplacement.add(
          new RentalChecklistItemDO(
              rentalChecklistItemDO == null ? Ids.next() : rentalChecklistItemDO.id(),
              rentalChecklistDOInitial.id(),
              entry.skuId(),
              snapshotV1.factoryId(),
              entry.quantity(),
              entry.remark(),
              index,
              maximum,
              reason,
              planningSnapshots.write(snapshotV1),
              rentalChecklistItemDO == null ? now : rentalChecklistItemDO.createdAt(),
              now));
    }
    checklistMapper.deleteChecklistItemsByChecklistId(rentalChecklistDOInitial.id());
    for (RentalChecklistItemDO rentalChecklistItemDO : rentalChecklistItemDOListReplacement)
      checklistMapper.insertChecklistItem(rentalChecklistItemDO);
  }
}
