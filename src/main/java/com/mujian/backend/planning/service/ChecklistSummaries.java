package com.mujian.backend.planning.service;

import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.planning.utils.PlanningValues.*;

import com.mujian.backend.planning.domain.*;
import com.mujian.backend.planning.mapper.ChecklistMapper;
import com.mujian.backend.planning.vo.PlanningViews.ChecklistSummary;
import java.math.BigInteger;
import java.util.*;
import org.springframework.stereotype.Service;

/** 列表与当前清单共用的冻结预算摘要，不承诺库存。 */
@Service
public class ChecklistSummaries {
  private final ChecklistMapper checklistMapper;
  private final PlanningSnapshots planningSnapshots;

  /** 使用同一批量成员查询。 */
  public ChecklistSummaries(ChecklistMapper checklistMapper, PlanningSnapshots planningSnapshots) {
    this.checklistMapper = checklistMapper;
    this.planningSnapshots = planningSnapshots;
  }

  /** 批量组装清单摘要，避免逐清单读成员。 */
  public List<ChecklistSummary> summaries(List<RentalChecklistDO> rentalChecklistDOListRoots) {
    List<String> ids = new ArrayList<>();
    for (RentalChecklistDO rentalChecklistDOInitial : rentalChecklistDOListRoots)
      ids.add(rentalChecklistDOInitial.id());
    Map<String, List<RentalChecklistItemDO>> rentalChecklistItemDOListMapGroups = new HashMap<>();
    if (!ids.isEmpty())
      for (RentalChecklistItemDO rentalChecklistItemDO :
          checklistMapper.selectChecklistItemsByChecklistIds(ids)) {
        List<RentalChecklistItemDO> rentalChecklistItemDOListValues =
            rentalChecklistItemDOListMapGroups.get(rentalChecklistItemDO.checklistId());
        if (rentalChecklistItemDOListValues == null) {
          rentalChecklistItemDOListValues = new ArrayList<>();
          rentalChecklistItemDOListMapGroups.put(
              rentalChecklistItemDO.checklistId(), rentalChecklistItemDOListValues);
        }
        rentalChecklistItemDOListValues.add(rentalChecklistItemDO);
      }
    List<ChecklistSummary> checklistSummaryListResult = new ArrayList<>();
    for (RentalChecklistDO rentalChecklistDOInitial : rentalChecklistDOListRoots) {
      List<RentalChecklistItemDO> rentalChecklistItemDOListItems =
          rentalChecklistItemDOListMapGroups.getOrDefault(rentalChecklistDOInitial.id(), List.of());
      BigInteger bigIntegerQuantity = BigInteger.ZERO;
      int daily = 0, deposit = 0;
      for (RentalChecklistItemDO rentalChecklistItemDO : rentalChecklistItemDOListItems) {
        SnapshotV1 snapshotV1 =
            planningSnapshots.read(
                rentalChecklistItemDO.skuSnapshotJson(),
                rentalChecklistItemDO.skuId(),
                rentalChecklistItemDO.factoryId());
        bigIntegerQuantity =
            bigIntegerQuantity.add(BigInteger.valueOf(rentalChecklistItemDO.quantity()));
        daily =
            add(
                daily,
                multiply(
                    snapshotV1.prices().dailyRentPriceFen(), rentalChecklistItemDO.quantity(), 1));
        deposit =
            add(
                deposit,
                multiply(
                    snapshotV1.prices().depositAmountFen(), rentalChecklistItemDO.quantity(), 1));
      }
      checklistSummaryListResult.add(
          new ChecklistSummary(
              rentalChecklistDOInitial.id(),
              rentalChecklistDOInitial.checklistName(),
              rentalChecklistDOInitial.rentalStartDate().toString(),
              rentalChecklistDOInitial.rentalEndDate().toString(),
              rentalChecklistDOInitial.rentalDays(),
              rentalChecklistDOInitial.checklistStatus(),
              rentalChecklistDOInitial.version(),
              rentalChecklistDOInitial.sourceType(),
              rentalChecklistDOInitial.sourceSchemeId(),
              rentalChecklistDOInitial.currentCheckoutBatchId(),
              rentalChecklistItemDOListItems.size(),
              bigIntegerQuantity.toString(),
              daily,
              deposit,
              time(rentalChecklistDOInitial.createdAt()),
              time(rentalChecklistDOInitial.updatedAt())));
    }
    return checklistSummaryListResult;
  }
}
