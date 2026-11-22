package com.mujian.backend.planning.service.impl;

import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.planning.utils.PlanningValues.*;
import static com.mujian.backend.planning.utils.PlanningValues.inconsistent;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.planning.converter.PlanningDisplayConverter;
import com.mujian.backend.planning.domain.*;
import com.mujian.backend.planning.dto.*;
import com.mujian.backend.planning.dto.PlanningDtos.*;
import com.mujian.backend.planning.mapper.ChecklistMapper;
import com.mujian.backend.planning.service.*;
import com.mujian.backend.planning.vo.PlanningViews.*;
import com.mujian.backend.prop.policy.PropAvailability;
import com.mujian.backend.prop.vo.PublicImageViewVO;
import com.mujian.backend.scheme.domain.*;
import com.mujian.backend.scheme.mapper.SchemeMapper;
import java.math.BigInteger;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ChecklistServiceImpl implements ChecklistService {
  private final ChecklistMapper checklistMapper;
  private final SchemeMapper schemeMapper;
  private final PlanningOperations planningOperations;
  private final PlanningSnapshots planningSnapshots;
  private final PlanningImages planningImages;
  private final ChecklistSummaries checklistSummaries;
  private final ChecklistMembers checklistMembers;
  private final PropAvailability propAvailability;
  private final Clock clock;

  /** 注入共享根锁、快照和可租能力。 */
  public ChecklistServiceImpl(
      ChecklistMapper checklistMapper,
      SchemeMapper schemeMapper,
      PlanningOperations planningOperations,
      PlanningSnapshots planningSnapshots,
      PlanningImages planningImages,
      ChecklistSummaries checklistSummaries,
      ChecklistMembers checklistMembers,
      PropAvailability propAvailability,
      Clock clock) {
    this.checklistMapper = checklistMapper;
    this.schemeMapper = schemeMapper;
    this.planningOperations = planningOperations;
    this.planningSnapshots = planningSnapshots;
    this.planningImages = planningImages;
    this.checklistSummaries = checklistSummaries;
    this.checklistMembers = checklistMembers;
    this.propAvailability = propAvailability;
    this.clock = clock;
  }

  /** 读取两类名额与唯一当前清单，不写库存。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public Context context(String actor) {
    planningOperations.readable(actor);
    List<RentalChecklistDO> rentalChecklistDOListActive =
        checklistMapper.selectActiveChecklistsByActor(actor, false);
    RentalChecklistDO rentalChecklistDOCurrent = null;
    for (RentalChecklistDO rentalChecklistDOInitial : rentalChecklistDOListActive)
      if (rentalChecklistDOInitial.currentOwnerGuard() != null) {
        if (rentalChecklistDOCurrent != null) throw inconsistent();
        rentalChecklistDOCurrent = rentalChecklistDOInitial;
      }
    ChecklistSummary checklistSummarySummary =
        rentalChecklistDOCurrent == null
            ? null
            : checklistSummaries.summaries(List.of(rentalChecklistDOCurrent)).get(0);
    return new Context(
        schemeMapper.selectSchemeActiveSlotsByActor(actor).size(),
        10,
        rentalChecklistDOListActive.size(),
        3,
        checklistSummarySummary,
        true);
  }

  /** 先归属过滤分页，再批量组装冻结预算。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<ChecklistSummary> list(PlanningQuery planningQuery) {
    planningOperations.readable(planningQuery.actor());
    if (!Set.of("all", "active", "history").contains(planningQuery.scope()))
      throw ApiFailure.invalid();
    long total = checklistMapper.selectChecklistCountByQuery(planningQuery);
    List<RentalChecklistDO> rentalChecklistDOListRoots =
        planningQuery.offset() >= total
            ? List.of()
            : checklistMapper.selectChecklistPageByQuery(planningQuery);
    return PageResult.of(
        checklistSummaries.summaries(rentalChecklistDOListRoots), total, planningQuery.paging());
  }

  /** 当前可公开项使用实时展示价；失效和过去项保留本人历史快照。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ChecklistDetail detail(String actor, String id) {
    boolean writable = planningOperations.readable(actor);
    RentalChecklistDO rentalChecklistDOInitial =
        checklistMapper.selectOwnedChecklistById(actor, id, false);
    if (rentalChecklistDOInitial == null) throw missing();
    List<RentalChecklistItemDO> rentalChecklistItemDOListRows =
        checklistMapper.selectChecklistItemsByChecklistIds(List.of(id));
    List<String> ids = new ArrayList<>();
    for (RentalChecklistItemDO rentalChecklistItemDO : rentalChecklistItemDOListRows)
      ids.add(rentalChecklistItemDO.skuId());
    boolean past = rentalChecklistDOInitial.rentalStartDate().isBefore(now(clock).toLocalDate());
    Map<String, PlanningSnapshots.Current> currentMapCurrent =
        past ? Map.of() : planningSnapshots.current(ids);
    Map<String, Integer> quantities =
        past
            ? Map.of()
            : propAvailability.quantities(
                ids,
                new PropAvailability.Dates(
                    rentalChecklistDOInitial.rentalStartDate(),
                    rentalChecklistDOInitial.rentalEndDate()));
    List<SnapshotV1> snapshotV1ListSnapshots = new ArrayList<>();
    for (RentalChecklistItemDO rentalChecklistItemDO : rentalChecklistItemDOListRows) {
      PlanningSnapshots.Current currentFact = currentMapCurrent.get(rentalChecklistItemDO.skuId());
      if (currentFact != null
          && !currentFact.snapshot().factoryId().equals(rentalChecklistItemDO.factoryId()))
        throw inconsistent();
      snapshotV1ListSnapshots.add(
          currentFact != null && currentFact.publicVisible()
              ? currentFact.snapshot()
              : planningSnapshots.read(
                  rentalChecklistItemDO.skuSnapshotJson(),
                  rentalChecklistItemDO.skuId(),
                  rentalChecklistItemDO.factoryId()));
    }
    Map<String, PublicImageViewVO> publicImageViewVOMapImages =
        planningImages.sign(snapshotV1ListSnapshots);
    Map<String, List<ChecklistItem>> checklistItemListMapGrouped = new LinkedHashMap<>();
    Map<String, String> factoryNames = new HashMap<>();
    BigInteger bigIntegerQuantity = BigInteger.ZERO;
    int daily = 0, deposit = 0, rent = 0;
    boolean unavailable = false;
    for (int index = 0; index < rentalChecklistItemDOListRows.size(); index++) {
      RentalChecklistItemDO rentalChecklistItemDO = rentalChecklistItemDOListRows.get(index);
      SnapshotV1 snapshotV1 = snapshotV1ListSnapshots.get(index);
      PlanningSnapshots.Current currentFact = currentMapCurrent.get(rentalChecklistItemDO.skuId());
      int maximum =
          past || currentFact == null || !currentFact.publicVisible()
              ? 0
              : quantities.getOrDefault(rentalChecklistItemDO.skuId(), 0);
      String reason =
          past
              ? PlanningReason.RENTAL_DATE_PASSED.name()
              : currentFact == null || !currentFact.publicVisible()
                  ? PlanningReason.SKU_NOT_PUBLIC.name()
                  : maximum < rentalChecklistItemDO.quantity()
                      ? PlanningReason.STOCK_NOT_ENOUGH.name()
                      : null;
      unavailable |= reason != null;
      int
          d =
              multiply(
                  snapshotV1.prices().dailyRentPriceFen(), rentalChecklistItemDO.quantity(), 1),
          p = multiply(snapshotV1.prices().depositAmountFen(), rentalChecklistItemDO.quantity(), 1),
          r = multiply(d, 1, rentalChecklistDOInitial.rentalDays());
      daily = add(daily, d);
      deposit = add(deposit, p);
      rent = add(rent, r);
      bigIntegerQuantity =
          bigIntegerQuantity.add(BigInteger.valueOf(rentalChecklistItemDO.quantity()));
      List<ChecklistItem> checklistItemListItems =
          checklistItemListMapGrouped.get(rentalChecklistItemDO.factoryId());
      if (checklistItemListItems == null) {
        checklistItemListItems = new ArrayList<>();
        checklistItemListMapGrouped.put(rentalChecklistItemDO.factoryId(), checklistItemListItems);
      }
      factoryNames.put(rentalChecklistItemDO.factoryId(), snapshotV1.factoryName());
      checklistItemListItems.add(
          new ChecklistItem(
              rentalChecklistItemDO.id(),
              PlanningDisplayConverter.priced(
                  snapshotV1, planningImages.cover(snapshotV1, publicImageViewVOMapImages)),
              rentalChecklistItemDO.quantity(),
              rentalChecklistItemDO.remark(),
              rentalChecklistItemDO.sortOrder(),
              new Availability(maximum, reason == null, reason),
              r,
              p));
    }
    List<ChecklistGroup> checklistGroupListGroups = new ArrayList<>();
    for (Map.Entry<String, List<ChecklistItem>> entry : checklistItemListMapGrouped.entrySet()) {
      entry
          .getValue()
          .sort(
              Comparator.comparing(
                      (ChecklistItem checklistItem) -> !checklistItem.availability().isRentable())
                  .thenComparingInt(ChecklistItem::sortOrder)
                  .thenComparing(ChecklistItem::id));
      int r = 0, p = 0;
      for (ChecklistItem checklistItem : entry.getValue()) {
        r = add(r, checklistItem.rentEstimateFen());
        p = add(p, checklistItem.depositEstimateFen());
      }
      checklistGroupListGroups.add(
          new ChecklistGroup(
              entry.getKey(), factoryNames.get(entry.getKey()), entry.getValue(), r, p));
    }
    LocalDateTime opens = rentalChecklistDOInitial.rentalStartDate().minusDays(14).atStartOfDay(),
        closes = rentalChecklistDOInitial.rentalStartDate().minusDays(2).atTime(11, 0),
        now = now(clock);
    boolean selecting =
        ChecklistStatus.selecting.name().equals(rentalChecklistDOInitial.checklistStatus());
    String reason =
        !selecting
            ? PlanningReason.CHECKLIST_NOT_CURRENT.name()
            : rentalChecklistItemDOListRows.isEmpty()
                ? PlanningReason.CHECKLIST_EMPTY.name()
                : now.isBefore(opens)
                    ? PlanningReason.RENTAL_WINDOW_NOT_OPEN.name()
                    : !now.isBefore(closes)
                        ? PlanningReason.RENTAL_WINDOW_CLOSED.name()
                        : unavailable
                            ? PlanningReason.CHECKLIST_ITEMS_UNAVAILABLE.name()
                            : !writable
                                ? com.mujian.backend.common.support.D2Codes
                                    .CODE_ACCOUNT_ACTION_RESTRICTED
                                : null;
    boolean payment = false;
    for (RentalChecklistDO rentalChecklistDOActive :
        checklistMapper.selectActiveChecklistsByActor(actor, false))
      payment |=
          ChecklistStatus.checkout_processing
              .name()
              .equals(rentalChecklistDOActive.checklistStatus());
    return new ChecklistDetail(
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
        rentalChecklistItemDOListRows.size(),
        bigIntegerQuantity.toString(),
        daily,
        deposit,
        time(rentalChecklistDOInitial.createdAt()),
        time(rentalChecklistDOInitial.updatedAt()),
        checklistGroupListGroups,
        rent,
        deposit,
        new Submission(reason == null, reason, time(opens), time(closes)),
        new ChecklistActions(
            writable && selecting,
            writable
                && !payment
                && ChecklistStatus.suspended
                    .name()
                    .equals(rentalChecklistDOInitial.checklistStatus()),
            writable && selecting && !past));
  }

  /** 用户→来源方案→清单根顺序，先校验后挂起原当前清单。 */
  public Receipt create(PlanningCommand planningCommand, CreateChecklist createChecklist) {
    planningOperations.lock(planningCommand.actor());
    SchemeDO schemeDOSource = null;
    if (ChecklistSource.scheme.name().equals(createChecklist.sourceType())) {
      schemeDOSource =
          schemeMapper.selectOwnedSchemeById(
              planningCommand.actor(), createChecklist.schemeId(), true);
      if (schemeDOSource == null) throw missing();
    } else if (!ChecklistSource.manual.name().equals(createChecklist.sourceType()))
      throw ApiFailure.invalid();
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    if (schemeDOSource != null)
      version(schemeDOSource.version(), createChecklist.expectedSchemeVersion());
    PropAvailability.Dates dates =
        propAvailability.dates(createChecklist.startDate(), createChecklist.endDate(), false);
    if (dates == null) throw ApiFailure.invalid();
    List<RentalChecklistDO> rentalChecklistDOListActive =
        checklistMapper.selectActiveChecklistsByActor(planningCommand.actor(), true);
    Set<Integer> slots = new HashSet<>();
    for (RentalChecklistDO rentalChecklistDOInitial : rentalChecklistDOListActive) {
      if (ChecklistStatus.checkout_processing
          .name()
          .equals(rentalChecklistDOInitial.checklistStatus()))
        throw conflict(PlanningReason.CHECKLIST_PAYMENT_IN_PROGRESS.name());
      slots.add(rentalChecklistDOInitial.activeSlotNo());
    }
    int slot = 1;
    while (slots.contains(slot)) slot++;
    if (slot > 3) throw conflict(PlanningReason.CHECKLIST_LIMIT_REACHED.name());
    LocalDateTime now = now(clock);
    int days = (int) ChronoUnit.DAYS.between(dates.start(), dates.end()) + 1;
    String id = Ids.next();
    List<RentalChecklistItemDO> rentalChecklistItemDOListCopies = new ArrayList<>();
    if (schemeDOSource != null) {
      List<SchemeItemDO> schemeItemDOListItems =
          schemeMapper.selectSchemeItemsBySchemeIds(List.of(schemeDOSource.id()));
      List<String> ids = new ArrayList<>();
      for (SchemeItemDO schemeItemDO : schemeItemDOListItems) ids.add(schemeItemDO.skuId());
      Map<String, PlanningSnapshots.Current> currentMapCurrent = planningSnapshots.current(ids);
      Map<String, Integer> quantities = propAvailability.quantities(ids, dates);
      int daily = 0, deposit = 0;
      for (SchemeItemDO schemeItemDO : schemeItemDOListItems) {
        PlanningSnapshots.Current currentFact = currentMapCurrent.get(schemeItemDO.skuId());
        if (currentFact == null
            || !currentFact.snapshot().factoryId().equals(schemeItemDO.factoryId()))
          throw inconsistent();
        SnapshotV1 snapshotV1 =
            currentFact.publicVisible()
                ? currentFact.snapshot()
                : planningSnapshots.read(
                    schemeItemDO.skuSnapshotJson(), schemeItemDO.skuId(), schemeItemDO.factoryId());
        int maximum =
            currentFact.publicVisible() ? quantities.getOrDefault(schemeItemDO.skuId(), 0) : 0;
        String reason =
            !currentFact.publicVisible()
                ? PlanningReason.SKU_NOT_PUBLIC.name()
                : maximum < schemeItemDO.quantity() ? PlanningReason.STOCK_NOT_ENOUGH.name() : null;
        daily =
            add(
                daily,
                multiply(snapshotV1.prices().dailyRentPriceFen(), schemeItemDO.quantity(), 1));
        deposit =
            add(
                deposit,
                multiply(snapshotV1.prices().depositAmountFen(), schemeItemDO.quantity(), 1));
        multiply(daily, 1, days);
        rentalChecklistItemDOListCopies.add(
            new RentalChecklistItemDO(
                Ids.next(),
                id,
                schemeItemDO.skuId(),
                snapshotV1.factoryId(),
                schemeItemDO.quantity(),
                schemeItemDO.remark(),
                schemeItemDO.sortOrder(),
                maximum,
                reason,
                planningSnapshots.write(snapshotV1),
                now,
                now));
      }
    }
    for (RentalChecklistDO rentalChecklistDOInitial : rentalChecklistDOListActive)
      if (ChecklistStatus.selecting.name().equals(rentalChecklistDOInitial.checklistStatus()))
        checklistMapper.updateChecklist(
            new RentalChecklistDO(
                rentalChecklistDOInitial.id(),
                rentalChecklistDOInitial.ownerUserId(),
                rentalChecklistDOInitial.checklistName(),
                rentalChecklistDOInitial.rentalStartDate(),
                rentalChecklistDOInitial.rentalEndDate(),
                rentalChecklistDOInitial.rentalDays(),
                ChecklistStatus.suspended.name(),
                rentalChecklistDOInitial.activeSlotNo(),
                null,
                null,
                rentalChecklistDOInitial.sourceType(),
                rentalChecklistDOInitial.sourceSchemeId(),
                rentalChecklistDOInitial.activatedAt(),
                rentalChecklistDOInitial.checkoutProcessingAt(),
                rentalChecklistDOInitial.convertedOrderAt(),
                next(rentalChecklistDOInitial.version()),
                rentalChecklistDOInitial.createdAt(),
                now));
    RentalChecklistDO rentalChecklistDOInitial =
        new RentalChecklistDO(
            id,
            planningCommand.actor(),
            dates.start() + "—" + dates.end() + "租赁清单",
            dates.start(),
            dates.end(),
            days,
            ChecklistStatus.selecting.name(),
            slot,
            null,
            null,
            createChecklist.sourceType(),
            schemeDOSource == null ? null : schemeDOSource.id(),
            now,
            null,
            null,
            0,
            now,
            now);
    checklistMapper.insertChecklist(rentalChecklistDOInitial);
    for (RentalChecklistItemDO rentalChecklistItemDO : rentalChecklistItemDOListCopies)
      checklistMapper.insertChecklistItem(rentalChecklistItemDO);
    return planningOperations.record(planningCommand, PlanningResource.checklist, id, 0);
  }

  /** 先释放原当前守卫，再激活目标，全过程一个事务。 */
  public Receipt activate(
      PlanningCommand planningCommand, String id, ExpectedVersion expectedVersionInput) {
    planningOperations.lock(planningCommand.actor());
    RentalChecklistDO rentalChecklistDOInitial =
        checklistMapper.selectOwnedChecklistById(planningCommand.actor(), id, false);
    if (rentalChecklistDOInitial == null) throw missing();
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    List<RentalChecklistDO> rentalChecklistDOListActive =
        checklistMapper.selectActiveChecklistsByActor(planningCommand.actor(), true);
    RentalChecklistDO rentalChecklistDOTarget = null;
    for (RentalChecklistDO rentalChecklistDOActive : rentalChecklistDOListActive) {
      if (ChecklistStatus.checkout_processing
          .name()
          .equals(rentalChecklistDOActive.checklistStatus()))
        throw conflict(PlanningReason.CHECKLIST_PAYMENT_IN_PROGRESS.name());
      if (rentalChecklistDOActive.id().equals(id))
        rentalChecklistDOTarget = rentalChecklistDOActive;
    }
    if (rentalChecklistDOTarget == null) throw conflict(PlanningReason.CHECKLIST_READ_ONLY.name());
    version(rentalChecklistDOTarget.version(), expectedVersionInput.expectedVersion());
    if (ChecklistStatus.selecting.name().equals(rentalChecklistDOTarget.checklistStatus()))
      return planningOperations.record(
          planningCommand, PlanningResource.checklist, id, rentalChecklistDOTarget.version());
    LocalDateTime now = now(clock);
    for (RentalChecklistDO rentalChecklistDOPrevious : rentalChecklistDOListActive)
      if (ChecklistStatus.selecting.name().equals(rentalChecklistDOPrevious.checklistStatus()))
        checklistMapper.updateChecklist(
            new RentalChecklistDO(
                rentalChecklistDOPrevious.id(),
                rentalChecklistDOPrevious.ownerUserId(),
                rentalChecklistDOPrevious.checklistName(),
                rentalChecklistDOPrevious.rentalStartDate(),
                rentalChecklistDOPrevious.rentalEndDate(),
                rentalChecklistDOPrevious.rentalDays(),
                ChecklistStatus.suspended.name(),
                rentalChecklistDOPrevious.activeSlotNo(),
                null,
                null,
                rentalChecklistDOPrevious.sourceType(),
                rentalChecklistDOPrevious.sourceSchemeId(),
                rentalChecklistDOPrevious.activatedAt(),
                rentalChecklistDOPrevious.checkoutProcessingAt(),
                rentalChecklistDOPrevious.convertedOrderAt(),
                next(rentalChecklistDOPrevious.version()),
                rentalChecklistDOPrevious.createdAt(),
                now));
    checklistMapper.updateChecklist(
        new RentalChecklistDO(
            rentalChecklistDOTarget.id(),
            rentalChecklistDOTarget.ownerUserId(),
            rentalChecklistDOTarget.checklistName(),
            rentalChecklistDOTarget.rentalStartDate(),
            rentalChecklistDOTarget.rentalEndDate(),
            rentalChecklistDOTarget.rentalDays(),
            ChecklistStatus.selecting.name(),
            rentalChecklistDOTarget.activeSlotNo(),
            null,
            null,
            rentalChecklistDOTarget.sourceType(),
            rentalChecklistDOTarget.sourceSchemeId(),
            now,
            rentalChecklistDOTarget.checkoutProcessingAt(),
            rentalChecklistDOTarget.convertedOrderAt(),
            next(rentalChecklistDOTarget.version()),
            rentalChecklistDOTarget.createdAt(),
            now));
    return planningOperations.record(
        planningCommand, PlanningResource.checklist, id, next(rentalChecklistDOTarget.version()));
  }

  /** 继续选品合并最终数量，不占用库存。 */
  public Receipt setItem(PlanningCommand planningCommand, String id, ItemSet itemSet) {
    planningOperations.lock(planningCommand.actor());
    RentalChecklistDO rentalChecklistDOInitial =
        checklistMapper.selectOwnedChecklistById(planningCommand.actor(), id, true);
    if (rentalChecklistDOInitial == null) throw missing();
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    version(rentalChecklistDOInitial.version(), itemSet.expectedVersion());
    if (ChecklistStatus.suspended.name().equals(rentalChecklistDOInitial.checklistStatus()))
      throw conflict(PlanningReason.CHECKLIST_NOT_CURRENT.name());
    if (!ChecklistStatus.selecting.name().equals(rentalChecklistDOInitial.checklistStatus()))
      throw conflict(PlanningReason.CHECKLIST_READ_ONLY.name());
    List<RentalChecklistItemDO> rentalChecklistItemDOListRows =
        checklistMapper.selectChecklistItemsByChecklistIds(List.of(id));
    List<ChecklistMembers.Entry> entryListEntries = new ArrayList<>();
    boolean found = false;
    for (RentalChecklistItemDO rentalChecklistItemDO : rentalChecklistItemDOListRows) {
      if (rentalChecklistItemDO.skuId().equals(itemSet.skuId())) {
        entryListEntries.add(
            new ChecklistMembers.Entry(
                rentalChecklistItemDO.id(),
                rentalChecklistItemDO.skuId(),
                itemSet.quantity(),
                itemSet.remark()));
        found = true;
      } else
        entryListEntries.add(
            new ChecklistMembers.Entry(
                rentalChecklistItemDO.id(),
                rentalChecklistItemDO.skuId(),
                rentalChecklistItemDO.quantity(),
                rentalChecklistItemDO.remark()));
    }
    if (!found)
      entryListEntries.add(
          new ChecklistMembers.Entry(null, itemSet.skuId(), itemSet.quantity(), itemSet.remark()));
    checklistMembers.replace(rentalChecklistDOInitial, entryListEntries, true);
    LocalDateTime now = now(clock);
    checklistMapper.updateChecklist(
        new RentalChecklistDO(
            rentalChecklistDOInitial.id(),
            rentalChecklistDOInitial.ownerUserId(),
            rentalChecklistDOInitial.checklistName(),
            rentalChecklistDOInitial.rentalStartDate(),
            rentalChecklistDOInitial.rentalEndDate(),
            rentalChecklistDOInitial.rentalDays(),
            rentalChecklistDOInitial.checklistStatus(),
            rentalChecklistDOInitial.activeSlotNo(),
            rentalChecklistDOInitial.currentOwnerGuard(),
            rentalChecklistDOInitial.currentCheckoutBatchId(),
            rentalChecklistDOInitial.sourceType(),
            rentalChecklistDOInitial.sourceSchemeId(),
            rentalChecklistDOInitial.activatedAt(),
            rentalChecklistDOInitial.checkoutProcessingAt(),
            rentalChecklistDOInitial.convertedOrderAt(),
            next(rentalChecklistDOInitial.version()),
            rentalChecklistDOInitial.createdAt(),
            now));
    return planningOperations.record(
        planningCommand, PlanningResource.checklist, id, next(rentalChecklistDOInitial.version()));
  }

  /** 只调整既有项，非法父ID拒绝。 */
  public Receipt updateItem(
      PlanningCommand planningCommand,
      String id,
      String itemId,
      ChecklistItemUpdate checklistItemUpdate) {
    planningOperations.lock(planningCommand.actor());
    RentalChecklistDO rentalChecklistDOInitial =
        checklistMapper.selectOwnedChecklistById(planningCommand.actor(), id, true);
    if (rentalChecklistDOInitial == null) throw missing();
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    version(rentalChecklistDOInitial.version(), checklistItemUpdate.expectedVersion());
    if (ChecklistStatus.suspended.name().equals(rentalChecklistDOInitial.checklistStatus()))
      throw conflict(PlanningReason.CHECKLIST_NOT_CURRENT.name());
    if (!ChecklistStatus.selecting.name().equals(rentalChecklistDOInitial.checklistStatus()))
      throw conflict(PlanningReason.CHECKLIST_READ_ONLY.name());
    List<RentalChecklistItemDO> rentalChecklistItemDOListRows =
        checklistMapper.selectChecklistItemsByChecklistIds(List.of(id));
    List<ChecklistMembers.Entry> entryListEntries = new ArrayList<>();
    boolean found = false;
    for (RentalChecklistItemDO rentalChecklistItemDO : rentalChecklistItemDOListRows) {
      if (rentalChecklistItemDO.id().equals(itemId)) {
        entryListEntries.add(
            new ChecklistMembers.Entry(
                rentalChecklistItemDO.id(),
                rentalChecklistItemDO.skuId(),
                checklistItemUpdate.quantity(),
                checklistItemUpdate.remark()));
        found = true;
      } else
        entryListEntries.add(
            new ChecklistMembers.Entry(
                rentalChecklistItemDO.id(),
                rentalChecklistItemDO.skuId(),
                rentalChecklistItemDO.quantity(),
                rentalChecklistItemDO.remark()));
    }
    if (!found) throw missing();
    checklistMembers.replace(rentalChecklistDOInitial, entryListEntries, false);
    LocalDateTime now = now(clock);
    checklistMapper.updateChecklist(
        new RentalChecklistDO(
            rentalChecklistDOInitial.id(),
            rentalChecklistDOInitial.ownerUserId(),
            rentalChecklistDOInitial.checklistName(),
            rentalChecklistDOInitial.rentalStartDate(),
            rentalChecklistDOInitial.rentalEndDate(),
            rentalChecklistDOInitial.rentalDays(),
            rentalChecklistDOInitial.checklistStatus(),
            rentalChecklistDOInitial.activeSlotNo(),
            rentalChecklistDOInitial.currentOwnerGuard(),
            rentalChecklistDOInitial.currentCheckoutBatchId(),
            rentalChecklistDOInitial.sourceType(),
            rentalChecklistDOInitial.sourceSchemeId(),
            rentalChecklistDOInitial.activatedAt(),
            rentalChecklistDOInitial.checkoutProcessingAt(),
            rentalChecklistDOInitial.convertedOrderAt(),
            next(rentalChecklistDOInitial.version()),
            rentalChecklistDOInitial.createdAt(),
            now));
    return planningOperations.record(
        planningCommand, PlanningResource.checklist, id, next(rentalChecklistDOInitial.version()));
  }

  /** 全量保留列表一次事务保存，遗漏项明确删除。 */
  public Receipt save(PlanningCommand planningCommand, String id, SaveChecklist saveChecklist) {
    planningOperations.lock(planningCommand.actor());
    RentalChecklistDO rentalChecklistDOInitial =
        checklistMapper.selectOwnedChecklistById(planningCommand.actor(), id, true);
    if (rentalChecklistDOInitial == null) throw missing();
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    version(rentalChecklistDOInitial.version(), saveChecklist.expectedVersion());
    if (ChecklistStatus.suspended.name().equals(rentalChecklistDOInitial.checklistStatus()))
      throw conflict(PlanningReason.CHECKLIST_NOT_CURRENT.name());
    if (!ChecklistStatus.selecting.name().equals(rentalChecklistDOInitial.checklistStatus()))
      throw conflict(PlanningReason.CHECKLIST_READ_ONLY.name());
    List<RentalChecklistItemDO> rentalChecklistItemDOListRows =
        checklistMapper.selectChecklistItemsByChecklistIds(List.of(id));
    Map<String, RentalChecklistItemDO> rentalChecklistItemDOMapById = new HashMap<>();
    for (RentalChecklistItemDO rentalChecklistItemDO : rentalChecklistItemDOListRows)
      rentalChecklistItemDOMapById.put(rentalChecklistItemDO.id(), rentalChecklistItemDO);
    List<ChecklistMembers.Entry> entryListEntries = new ArrayList<>();
    for (ChecklistEntry checklistEntryEntry : saveChecklist.items()) {
      RentalChecklistItemDO rentalChecklistItemDO =
          rentalChecklistItemDOMapById.get(checklistEntryEntry.itemId());
      if (rentalChecklistItemDO == null) throw missing();
      entryListEntries.add(
          new ChecklistMembers.Entry(
              rentalChecklistItemDO.id(),
              rentalChecklistItemDO.skuId(),
              checklistEntryEntry.quantity(),
              checklistEntryEntry.remark()));
    }
    checklistMembers.replace(rentalChecklistDOInitial, entryListEntries, false);
    LocalDateTime now = now(clock);
    checklistMapper.updateChecklist(
        new RentalChecklistDO(
            rentalChecklistDOInitial.id(),
            rentalChecklistDOInitial.ownerUserId(),
            rentalChecklistDOInitial.checklistName(),
            rentalChecklistDOInitial.rentalStartDate(),
            rentalChecklistDOInitial.rentalEndDate(),
            rentalChecklistDOInitial.rentalDays(),
            rentalChecklistDOInitial.checklistStatus(),
            rentalChecklistDOInitial.activeSlotNo(),
            rentalChecklistDOInitial.currentOwnerGuard(),
            rentalChecklistDOInitial.currentCheckoutBatchId(),
            rentalChecklistDOInitial.sourceType(),
            rentalChecklistDOInitial.sourceSchemeId(),
            rentalChecklistDOInitial.activatedAt(),
            rentalChecklistDOInitial.checkoutProcessingAt(),
            rentalChecklistDOInitial.convertedOrderAt(),
            next(rentalChecklistDOInitial.version()),
            rentalChecklistDOInitial.createdAt(),
            now));
    return planningOperations.record(
        planningCommand, PlanningResource.checklist, id, next(rentalChecklistDOInitial.version()));
  }

  /** 删项不释放根槽位，失效商品也可整理。 */
  public Receipt removeItem(
      PlanningCommand planningCommand,
      String id,
      String itemId,
      ExpectedVersion expectedVersionInput) {
    planningOperations.lock(planningCommand.actor());
    RentalChecklistDO rentalChecklistDOInitial =
        checklistMapper.selectOwnedChecklistById(planningCommand.actor(), id, true);
    if (rentalChecklistDOInitial == null) throw missing();
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    version(rentalChecklistDOInitial.version(), expectedVersionInput.expectedVersion());
    if (ChecklistStatus.suspended.name().equals(rentalChecklistDOInitial.checklistStatus()))
      throw conflict(PlanningReason.CHECKLIST_NOT_CURRENT.name());
    if (!ChecklistStatus.selecting.name().equals(rentalChecklistDOInitial.checklistStatus()))
      throw conflict(PlanningReason.CHECKLIST_READ_ONLY.name());
    List<RentalChecklistItemDO> rentalChecklistItemDOListRows =
        checklistMapper.selectChecklistItemsByChecklistIds(List.of(id));
    List<ChecklistMembers.Entry> entryListEntries = new ArrayList<>();
    boolean found = false;
    for (RentalChecklistItemDO rentalChecklistItemDO : rentalChecklistItemDOListRows) {
      if (rentalChecklistItemDO.id().equals(itemId)) found = true;
      else
        entryListEntries.add(
            new ChecklistMembers.Entry(
                rentalChecklistItemDO.id(),
                rentalChecklistItemDO.skuId(),
                rentalChecklistItemDO.quantity(),
                rentalChecklistItemDO.remark()));
    }
    if (!found) throw missing();
    checklistMembers.replace(rentalChecklistDOInitial, entryListEntries, false);
    LocalDateTime now = now(clock);
    checklistMapper.updateChecklist(
        new RentalChecklistDO(
            rentalChecklistDOInitial.id(),
            rentalChecklistDOInitial.ownerUserId(),
            rentalChecklistDOInitial.checklistName(),
            rentalChecklistDOInitial.rentalStartDate(),
            rentalChecklistDOInitial.rentalEndDate(),
            rentalChecklistDOInitial.rentalDays(),
            rentalChecklistDOInitial.checklistStatus(),
            rentalChecklistDOInitial.activeSlotNo(),
            rentalChecklistDOInitial.currentOwnerGuard(),
            rentalChecklistDOInitial.currentCheckoutBatchId(),
            rentalChecklistDOInitial.sourceType(),
            rentalChecklistDOInitial.sourceSchemeId(),
            rentalChecklistDOInitial.activatedAt(),
            rentalChecklistDOInitial.checkoutProcessingAt(),
            rentalChecklistDOInitial.convertedOrderAt(),
            next(rentalChecklistDOInitial.version()),
            rentalChecklistDOInitial.createdAt(),
            now));
    return planningOperations.record(
        planningCommand, PlanningResource.checklist, id, next(rentalChecklistDOInitial.version()));
  }
}
