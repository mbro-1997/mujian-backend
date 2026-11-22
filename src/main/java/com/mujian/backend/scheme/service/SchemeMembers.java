package com.mujian.backend.scheme.service;

import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.planning.utils.PlanningValues.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.PlanningReason;
import com.mujian.backend.common.enums.SchemeSaveAction;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.planning.domain.SnapshotV1;
import com.mujian.backend.planning.service.PlanningSnapshots;
import com.mujian.backend.scheme.domain.*;
import com.mujian.backend.scheme.dto.SchemeDtos.*;
import com.mujian.backend.scheme.mapper.SchemeMapper;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 在已持有用户和方案根锁的事务中验证并写入变动行。 */
@Service
public class SchemeMembers {
  private final SchemeMapper schemeMapper;
  private final PlanningSnapshots planningSnapshots;

  public record Changes(String name, String description, boolean changed) {}

  /** 注入可信快照和成员存储。 */
  public SchemeMembers(SchemeMapper schemeMapper, PlanningSnapshots planningSnapshots) {
    this.schemeMapper = schemeMapper;
    this.planningSnapshots = planningSnapshots;
  }

  /** 兼容原单项和重排入口，转换为稳定ID动作，保留其版本校验。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public boolean replace(SchemeDO schemeDO, List<SchemeEntry> schemeEntryList, LocalDateTime now) {
    Map<String, SchemeItemDO> existing = new HashMap<>();
    for (SchemeItemDO schemeItemDO :
        schemeMapper.selectSchemeItemsBySchemeIds(List.of(schemeDO.id())))
      existing.put(schemeItemDO.skuId(), schemeItemDO);
    List<SaveAction> saveActionList = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int index = 0; index < schemeEntryList.size(); index++) {
      SchemeEntry schemeEntry = schemeEntryList.get(index);
      if (!seen.add(schemeEntry.skuId())) throw ApiFailure.invalid();
      SchemeItemDO schemeItemDO = existing.remove(schemeEntry.skuId());
      saveActionList.add(
          new SaveAction(
              schemeItemDO == null ? SchemeSaveAction.addItem : SchemeSaveAction.updateItem,
              schemeItemDO == null ? schemeEntry.skuId() : null,
              schemeItemDO == null ? null : schemeItemDO.id(),
              schemeEntry.quantity(),
              schemeEntry.remark(),
              index,
              null,
              null));
    }
    for (SchemeItemDO schemeItemDO : existing.values())
      saveActionList.add(
          new SaveAction(
              SchemeSaveAction.removeItem, null, schemeItemDO.id(), null, null, null, null, null));
    return save(schemeDO, saveActionList, now).changed();
  }

  /** 先验证整组动作与最终金额，已有项沿用原快照，再按ID只写受影响行。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public Changes save(SchemeDO schemeDO, List<SaveAction> saveActionList, LocalDateTime now) {
    if (saveActionList == null) throw ApiFailure.invalid();
    Set<String> touched = new LinkedHashSet<>();
    Set<String> addedSkuIds = new LinkedHashSet<>();
    boolean infoSeen = false;
    String name = schemeDO.schemeName(), description = schemeDO.description();
    for (SaveAction action : saveActionList) {
      if (action == null || action.action() == null) throw ApiFailure.invalid();
      switch (action.action()) {
        case updateSchemeInfo -> {
          if (infoSeen) throw ApiFailure.invalid();
          infoSeen = true;
          name = text(action.name(), 128);
          description = action.description();
        }
        case addItem -> {
          id(action.skuId());
          if (!addedSkuIds.add(action.skuId())) throw ApiFailure.invalid();
          validateItem(action);
        }
        case updateItem, removeItem -> {
          id(action.itemId());
          if (!touched.add(action.itemId())) throw ApiFailure.invalid();
          if (action.action() == SchemeSaveAction.updateItem) validateItem(action);
        }
      }
    }
    boolean changed =
        !Objects.equals(name, schemeDO.schemeName())
            || !Objects.equals(description, schemeDO.description());
    if (touched.isEmpty() && addedSkuIds.isEmpty()) return new Changes(name, description, changed);

    Map<String, SchemeItemDO> targets = new HashMap<>();
    if (!touched.isEmpty()) {
      for (SchemeItemDO item :
          schemeMapper.selectSchemeItemsBySchemeAndIds(schemeDO.id(), List.copyOf(touched)))
        targets.put(item.id(), item);
      if (targets.size() != touched.size()) throw missing();
    }
    if (!addedSkuIds.isEmpty()
        && !schemeMapper
            .selectExistingSkuIdsByScheme(schemeDO.id(), List.copyOf(addedSkuIds))
            .isEmpty()) throw ApiFailure.invalid();
    Map<String, PlanningSnapshots.Current> current =
        addedSkuIds.isEmpty() ? Map.of() : planningSnapshots.current(List.copyOf(addedSkuIds));
    List<SchemeItemDO> inserts = new ArrayList<>(),
        updates = new ArrayList<>(),
        amountItems = new ArrayList<>();
    List<String> deletes = new ArrayList<>(), amountChangedIds = new ArrayList<>();
    boolean mayIncreaseAmount = !addedSkuIds.isEmpty();
    for (SaveAction action : saveActionList) {
      switch (action.action()) {
        case updateSchemeInfo -> {}
        case addItem -> {
          PlanningSnapshots.Current snapshot = current.get(action.skuId());
          if (snapshot == null || !snapshot.publicVisible())
            throw conflict(PlanningReason.SKU_NOT_PUBLIC.name());
          SchemeItemDO item =
              new SchemeItemDO(
                  Ids.next(),
                  schemeDO.id(),
                  action.skuId(),
                  snapshot.snapshot().factoryId(),
                  action.quantity(),
                  action.remark(),
                  action.sortOrder(),
                  planningSnapshots.write(snapshot.snapshot()),
                  now,
                  now);
          inserts.add(item);
          amountItems.add(item);
        }
        case removeItem -> {
          deletes.add(action.itemId());
          amountChangedIds.add(action.itemId());
        }
        case updateItem -> {
          SchemeItemDO old = targets.get(action.itemId());
          boolean quantityChanged = old.quantity() != action.quantity();
          if (!quantityChanged
              && old.sortOrder() == action.sortOrder()
              && Objects.equals(old.remark(), action.remark())) continue;
          SchemeItemDO item =
              new SchemeItemDO(
                  old.id(),
                  old.schemeId(),
                  old.skuId(),
                  old.factoryId(),
                  action.quantity(),
                  action.remark(),
                  action.sortOrder(),
                  old.skuSnapshotJson(),
                  old.createdAt(),
                  now);
          updates.add(item);
          if (quantityChanged) {
            amountChangedIds.add(old.id());
            amountItems.add(item);
            mayIncreaseAmount |= action.quantity() > old.quantity();
          }
        }
      }
    }
    // 只有可能增加金额时校验最终合计；未变化行只在数据库聚合，不加载或解析其快照。
    if (mayIncreaseAmount) validateAmounts(schemeDO.id(), amountChangedIds, amountItems);
    for (String itemId : deletes) schemeMapper.deleteSchemeItemBySchemeAndId(schemeDO.id(), itemId);
    for (SchemeItemDO item : updates) schemeMapper.updateSchemeItemBySchemeAndId(item);
    for (SchemeItemDO item : inserts) schemeMapper.insertSchemeItem(item);
    return new Changes(
        name,
        description,
        changed || !deletes.isEmpty() || !updates.isEmpty() || !inserts.isEmpty());
  }

  /** 校验新增及修改动作的显式数量和排序。 */
  private void validateItem(SaveAction action) {
    if (action.quantity() == null || action.sortOrder() == null || action.sortOrder() < 0)
      throw ApiFailure.invalid();
    quantity(action.quantity());
  }

  /** 合计未变化金额与本次变化后的金额，先校验再写入，避免动作顺序造成中间态溢出。 */
  private void validateAmounts(
      String schemeId, List<String> excludedIds, List<SchemeItemDO> items) {
    com.mujian.backend.scheme.projection.SchemeAmountProjection totals =
        schemeMapper.selectSchemeAmountsExcludingIds(schemeId, excludedIds);
    java.math.BigInteger daily = totals.dailyRentFen().toBigIntegerExact();
    java.math.BigInteger deposit = totals.depositFen().toBigIntegerExact();
    for (SchemeItemDO item : items) {
      SnapshotV1 snapshot =
          planningSnapshots.read(item.skuSnapshotJson(), item.skuId(), item.factoryId());
      daily =
          daily.add(
              java.math.BigInteger.valueOf(
                  multiply(snapshot.prices().dailyRentPriceFen(), item.quantity(), 1)));
      deposit =
          deposit.add(
              java.math.BigInteger.valueOf(
                  multiply(snapshot.prices().depositAmountFen(), item.quantity(), 1)));
    }
    amount(daily);
    amount(deposit);
  }
}
