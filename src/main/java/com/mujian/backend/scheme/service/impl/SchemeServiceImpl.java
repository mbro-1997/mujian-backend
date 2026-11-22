package com.mujian.backend.scheme.service.impl;

import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.planning.utils.PlanningValues.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.planning.converter.PlanningDisplayConverter;
import com.mujian.backend.planning.domain.SnapshotV1;
import com.mujian.backend.planning.dto.*;
import com.mujian.backend.planning.dto.PlanningDtos.*;
import com.mujian.backend.planning.service.*;
import com.mujian.backend.planning.vo.PlanningViews.*;
import com.mujian.backend.prop.vo.PublicImageViewVO;
import com.mujian.backend.scheme.domain.*;
import com.mujian.backend.scheme.dto.SchemeDtos.*;
import com.mujian.backend.scheme.mapper.SchemeMapper;
import com.mujian.backend.scheme.service.*;
import com.mujian.backend.scheme.vo.SchemeViews.*;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class SchemeServiceImpl implements SchemeService {
  private final SchemeMapper schemeMapper;
  private final PlanningOperations planningOperations;
  private final PlanningSnapshots planningSnapshots;
  private final PlanningImages planningImages;
  private final SchemeMembers schemeMembers;
  private final Clock clock;
  private final tools.jackson.databind.ObjectMapper objectMapper;

  /** 装配方案领域服务，保留单事务保存。 */
  public SchemeServiceImpl(
      SchemeMapper schemeMapper,
      PlanningOperations planningOperations,
      PlanningSnapshots planningSnapshots,
      PlanningImages planningImages,
      SchemeMembers schemeMembers,
      Clock clock,
      tools.jackson.databind.ObjectMapper objectMapper) {
    this.schemeMapper = schemeMapper;
    this.planningOperations = planningOperations;
    this.planningSnapshots = planningSnapshots;
    this.planningImages = planningImages;
    this.schemeMembers = schemeMembers;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  /** 先分页查根，再一次批量查本页成员汇总。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public PageResult<SchemeSummary> list(PlanningQuery planningQuery) {
    planningOperations.readable(planningQuery.actor());
    if (!Set.of("all", SchemeStatus.editing.name(), SchemeStatus.archived.name())
        .contains(planningQuery.scope())) throw ApiFailure.invalid();
    long total = schemeMapper.selectSchemeCountByQuery(planningQuery);
    List<SchemeDO> schemeDOListRoots =
        planningQuery.offset() >= total
            ? List.of()
            : schemeMapper.selectSchemePageByQuery(planningQuery);
    List<String> ids = new ArrayList<>();
    for (SchemeDO schemeDOInitial : schemeDOListRoots) ids.add(schemeDOInitial.id());
    Map<String, List<SchemeItemDO>> schemeItemDOListMapGroups = new HashMap<>();
    if (!ids.isEmpty())
      for (SchemeItemDO schemeItemDO : schemeMapper.selectSchemeItemsBySchemeIds(ids)) {
        List<SchemeItemDO> schemeItemDOListValues =
            schemeItemDOListMapGroups.get(schemeItemDO.schemeId());
        if (schemeItemDOListValues == null) {
          schemeItemDOListValues = new ArrayList<>();
          schemeItemDOListMapGroups.put(schemeItemDO.schemeId(), schemeItemDOListValues);
        }
        schemeItemDOListValues.add(schemeItemDO);
      }
    List<SchemeSummary> schemeSummaryListResult = new ArrayList<>();
    for (SchemeDO schemeDOInitial : schemeDOListRoots) {
      List<SchemeItemDO> schemeItemDOListItems =
          schemeItemDOListMapGroups.getOrDefault(schemeDOInitial.id(), List.of());
      BigInteger bigIntegerQuantity = BigInteger.ZERO;
      int daily = 0, deposit = 0;
      for (SchemeItemDO schemeItemDO : schemeItemDOListItems) {
        SnapshotV1 snapshotV1 =
            planningSnapshots.read(
                schemeItemDO.skuSnapshotJson(), schemeItemDO.skuId(), schemeItemDO.factoryId());
        bigIntegerQuantity = bigIntegerQuantity.add(BigInteger.valueOf(schemeItemDO.quantity()));
        daily =
            add(
                daily,
                multiply(snapshotV1.prices().dailyRentPriceFen(), schemeItemDO.quantity(), 1));
        deposit =
            add(
                deposit,
                multiply(snapshotV1.prices().depositAmountFen(), schemeItemDO.quantity(), 1));
      }
      schemeSummaryListResult.add(
          new SchemeSummary(
              schemeDOInitial.id(),
              schemeDOInitial.schemeName(),
              schemeDOInitial.description(),
              schemeDOInitial.schemeStatus(),
              schemeDOInitial.version(),
              schemeDOInitial.contentVersion(),
              schemeItemDOListItems.size(),
              bigIntegerQuantity.toString(),
              daily,
              deposit,
              time(schemeDOInitial.createdAt()),
              time(schemeDOInitial.updatedAt()),
              time(schemeDOInitial.archivedAt())));
    }
    return PageResult.of(schemeSummaryListResult, total, planningQuery.paging());
  }

  /** 本人完整快照及当前动作，不用当前SKU覆盖历史。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public SchemeDetail detail(String actor, String id) {
    boolean writable = planningOperations.readable(actor);
    SchemeDO schemeDOInitial = schemeMapper.selectOwnedSchemeById(actor, id, false);
    if (schemeDOInitial == null) throw missing();
    List<SchemeItemDO> schemeItemDOListRows =
        schemeMapper.selectSchemeItemsBySchemeIds(List.of(id));
    List<SnapshotV1> snapshotV1ListSnapshots = new ArrayList<>();
    for (SchemeItemDO schemeItemDO : schemeItemDOListRows)
      snapshotV1ListSnapshots.add(
          planningSnapshots.read(
              schemeItemDO.skuSnapshotJson(), schemeItemDO.skuId(), schemeItemDO.factoryId()));
    Map<String, PublicImageViewVO> publicImageViewVOMapImages =
        planningImages.sign(snapshotV1ListSnapshots);
    List<SchemeItem> schemeItemListItems = new ArrayList<>();
    BigInteger bigIntegerQuantity = BigInteger.ZERO;
    int daily = 0, deposit = 0;
    for (int index = 0; index < schemeItemDOListRows.size(); index++) {
      SchemeItemDO schemeItemDO = schemeItemDOListRows.get(index);
      SnapshotV1 snapshotV1 = snapshotV1ListSnapshots.get(index);
      int d = multiply(snapshotV1.prices().dailyRentPriceFen(), schemeItemDO.quantity(), 1),
          p = multiply(snapshotV1.prices().depositAmountFen(), schemeItemDO.quantity(), 1);
      daily = add(daily, d);
      deposit = add(deposit, p);
      bigIntegerQuantity = bigIntegerQuantity.add(BigInteger.valueOf(schemeItemDO.quantity()));
      schemeItemListItems.add(
          new SchemeItem(
              schemeItemDO.id(),
              PlanningDisplayConverter.priced(
                  snapshotV1, planningImages.cover(snapshotV1, publicImageViewVOMapImages)),
              schemeItemDO.quantity(),
              schemeItemDO.remark(),
              schemeItemDO.sortOrder(),
              d,
              p));
    }
    SchemeShareDO schemeShareDOShare = schemeMapper.selectSchemeShareBySchemeId(id);
    ShareConfig shareConfigConfig =
        schemeShareDOShare == null
            ? null
            : new ShareConfig(
                schemeShareDOShare.id(),
                schemeShareDOShare.shareToken(),
                schemeShareDOShare.shareStatus(),
                schemeShareDOShare.hidePrice(),
                time(schemeShareDOShare.expiresAt()),
                Integer.toString(schemeShareDOShare.viewCount()));
    boolean edit = writable && SchemeStatus.editing.name().equals(schemeDOInitial.schemeStatus());
    return new SchemeDetail(
        schemeDOInitial.id(),
        schemeDOInitial.schemeName(),
        schemeDOInitial.description(),
        schemeDOInitial.schemeStatus(),
        schemeDOInitial.version(),
        schemeDOInitial.contentVersion(),
        schemeItemListItems.size(),
        bigIntegerQuantity.toString(),
        daily,
        deposit,
        time(schemeDOInitial.createdAt()),
        time(schemeDOInitial.updatedAt()),
        time(schemeDOInitial.archivedAt()),
        schemeItemListItems,
        shareConfigConfig,
        new SchemeActions(
            edit, edit, writable && schemeDOInitial.shareEnabled(), writable, writable));
  }

  /** 用户锁内分配最低空槽，失败不写回执。 */
  public Receipt create(PlanningCommand planningCommand, CreateScheme createScheme) {
    planningOperations.lock(planningCommand.actor());
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    List<Integer> slots = schemeMapper.selectSchemeActiveSlotsByActor(planningCommand.actor());
    int slot = 1;
    while (slots.contains(slot)) slot++;
    if (slot > 10) throw conflict(PlanningReason.SCHEME_LIMIT_REACHED.name());
    LocalDateTime now = now(clock);
    SchemeDO schemeDOInitial =
        new SchemeDO(
            Ids.next(),
            planningCommand.actor(),
            text(createScheme.name(), 128),
            createScheme.description(),
            SchemeStatus.editing.name(),
            slot,
            1,
            0,
            true,
            null,
            now,
            now);
    schemeMapper.insertScheme(schemeDOInitial);
    return planningOperations.record(
        planningCommand, PlanningResource.scheme, schemeDOInitial.id(), 0);
  }

  /** 动作数组原子保存，内容实际变化才推进一次版本。 */
  public Receipt update(
      PlanningCommand planningCommand, String id, UpdateScheme updateSchemeInput) {
    planningOperations.lock(planningCommand.actor());
    SchemeDO schemeDOInitial =
        schemeMapper.selectOwnedSchemeById(planningCommand.actor(), id, true);
    if (schemeDOInitial == null) throw missing();
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    if (!SchemeStatus.editing.name().equals(schemeDOInitial.schemeStatus()))
      throw conflict(PlanningReason.SCHEME_ARCHIVED.name());
    LocalDateTime now = now(clock);
    SchemeMembers.Changes changes =
        schemeMembers.save(schemeDOInitial, updateSchemeInput.actions(), now);
    if (!changes.changed())
      return planningOperations.record(
          planningCommand, PlanningResource.scheme, id, schemeDOInitial.version());
    SchemeDO schemeDOUpdated =
        new SchemeDO(
            schemeDOInitial.id(),
            schemeDOInitial.ownerUserId(),
            changes.name(),
            changes.description(),
            schemeDOInitial.schemeStatus(),
            schemeDOInitial.activeSlotNo(),
            next(schemeDOInitial.contentVersion()),
            next(schemeDOInitial.version()),
            schemeDOInitial.shareEnabled(),
            schemeDOInitial.archivedAt(),
            schemeDOInitial.createdAt(),
            now);
    schemeMapper.updateScheme(schemeDOUpdated);
    return planningOperations.record(
        planningCommand, PlanningResource.scheme, id, schemeDOUpdated.version());
  }

  /** 归档释放槽位，仅增加资源版本，内容不变。 */
  public Receipt archive(
      PlanningCommand planningCommand, String id, ExpectedVersion expectedVersionInput) {
    planningOperations.lock(planningCommand.actor());
    SchemeDO schemeDOInitial =
        schemeMapper.selectOwnedSchemeById(planningCommand.actor(), id, true);
    if (schemeDOInitial == null) throw missing();
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    version(schemeDOInitial.version(), expectedVersionInput.expectedVersion());
    if (!SchemeStatus.editing.name().equals(schemeDOInitial.schemeStatus()))
      throw conflict(PlanningReason.SCHEME_ARCHIVED.name());
    LocalDateTime now = now(clock);
    SchemeDO schemeDOUpdated =
        new SchemeDO(
            schemeDOInitial.id(),
            schemeDOInitial.ownerUserId(),
            schemeDOInitial.schemeName(),
            schemeDOInitial.description(),
            SchemeStatus.archived.name(),
            null,
            schemeDOInitial.contentVersion(),
            next(schemeDOInitial.version()),
            schemeDOInitial.shareEnabled(),
            now,
            schemeDOInitial.createdAt(),
            now);
    schemeMapper.updateScheme(schemeDOUpdated);
    return planningOperations.record(
        planningCommand, PlanningResource.scheme, id, schemeDOUpdated.version());
  }

  /** 加入方案使用最终总量，事务中合并已有项；不检查租期库存。 */
  public Receipt setItem(PlanningCommand planningCommand, String id, ItemSet itemSet) {
    planningOperations.lock(planningCommand.actor());
    SchemeDO schemeDOInitial =
        schemeMapper.selectOwnedSchemeById(planningCommand.actor(), id, true);
    if (schemeDOInitial == null) throw missing();
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    version(schemeDOInitial.version(), itemSet.expectedVersion());
    if (!SchemeStatus.editing.name().equals(schemeDOInitial.schemeStatus()))
      throw conflict(PlanningReason.SCHEME_ARCHIVED.name());
    PlanningSnapshots.Current current =
        planningSnapshots.current(List.of(itemSet.skuId())).get(itemSet.skuId());
    if (current == null || !current.publicVisible())
      throw conflict(PlanningReason.SKU_NOT_PUBLIC.name());
    List<SchemeEntry> schemeEntryListEntries = new ArrayList<>();
    boolean found = false;
    for (SchemeItemDO schemeItemDO : schemeMapper.selectSchemeItemsBySchemeIds(List.of(id))) {
      if (schemeItemDO.skuId().equals(itemSet.skuId())) {
        schemeEntryListEntries.add(
            new SchemeEntry(itemSet.skuId(), itemSet.quantity(), itemSet.remark()));
        found = true;
      } else
        schemeEntryListEntries.add(
            new SchemeEntry(schemeItemDO.skuId(), schemeItemDO.quantity(), schemeItemDO.remark()));
    }
    if (!found)
      schemeEntryListEntries.add(
          new SchemeEntry(itemSet.skuId(), itemSet.quantity(), itemSet.remark()));
    LocalDateTime now = now(clock);
    if (!schemeMembers.replace(schemeDOInitial, schemeEntryListEntries, now))
      return planningOperations.record(
          planningCommand, PlanningResource.scheme, id, schemeDOInitial.version());
    SchemeDO schemeDOUpdated =
        new SchemeDO(
            schemeDOInitial.id(),
            schemeDOInitial.ownerUserId(),
            schemeDOInitial.schemeName(),
            schemeDOInitial.description(),
            schemeDOInitial.schemeStatus(),
            schemeDOInitial.activeSlotNo(),
            next(schemeDOInitial.contentVersion()),
            next(schemeDOInitial.version()),
            schemeDOInitial.shareEnabled(),
            schemeDOInitial.archivedAt(),
            schemeDOInitial.createdAt(),
            now);
    schemeMapper.updateScheme(schemeDOUpdated);
    return planningOperations.record(
        planningCommand, PlanningResource.scheme, id, schemeDOUpdated.version());
  }

  /** 跨父成员统一不存在；重放优先于已删除成员检查。 */
  public Receipt removeItem(
      PlanningCommand planningCommand,
      String id,
      String itemId,
      ExpectedVersion expectedVersionInput) {
    planningOperations.lock(planningCommand.actor());
    SchemeDO schemeDOInitial =
        schemeMapper.selectOwnedSchemeById(planningCommand.actor(), id, true);
    if (schemeDOInitial == null) throw missing();
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    version(schemeDOInitial.version(), expectedVersionInput.expectedVersion());
    if (!SchemeStatus.editing.name().equals(schemeDOInitial.schemeStatus()))
      throw conflict(PlanningReason.SCHEME_ARCHIVED.name());
    List<SchemeEntry> schemeEntryListEntries = new ArrayList<>();
    boolean found = false;
    for (SchemeItemDO schemeItemDO : schemeMapper.selectSchemeItemsBySchemeIds(List.of(id))) {
      if (schemeItemDO.id().equals(itemId)) found = true;
      else
        schemeEntryListEntries.add(
            new SchemeEntry(schemeItemDO.skuId(), schemeItemDO.quantity(), schemeItemDO.remark()));
    }
    if (!found) throw missing();
    LocalDateTime now = now(clock);
    if (!schemeMembers.replace(schemeDOInitial, schemeEntryListEntries, now))
      return planningOperations.record(
          planningCommand, PlanningResource.scheme, id, schemeDOInitial.version());
    SchemeDO schemeDOUpdated =
        new SchemeDO(
            schemeDOInitial.id(),
            schemeDOInitial.ownerUserId(),
            schemeDOInitial.schemeName(),
            schemeDOInitial.description(),
            schemeDOInitial.schemeStatus(),
            schemeDOInitial.activeSlotNo(),
            next(schemeDOInitial.contentVersion()),
            next(schemeDOInitial.version()),
            schemeDOInitial.shareEnabled(),
            schemeDOInitial.archivedAt(),
            schemeDOInitial.createdAt(),
            now);
    schemeMapper.updateScheme(schemeDOUpdated);
    return planningOperations.record(
        planningCommand, PlanningResource.scheme, id, schemeDOUpdated.version());
  }

  /** 重排必须恰好覆盖原成员，不能跨父或重复。 */
  public Receipt reorder(PlanningCommand planningCommand, String id, ReorderScheme reorderScheme) {
    planningOperations.lock(planningCommand.actor());
    SchemeDO schemeDOInitial =
        schemeMapper.selectOwnedSchemeById(planningCommand.actor(), id, true);
    if (schemeDOInitial == null) throw missing();
    Receipt receiptReplay = planningOperations.replay(planningCommand);
    if (receiptReplay != null) return receiptReplay;
    planningOperations.writable(planningCommand.actor());
    version(schemeDOInitial.version(), reorderScheme.expectedVersion());
    if (!SchemeStatus.editing.name().equals(schemeDOInitial.schemeStatus()))
      throw conflict(PlanningReason.SCHEME_ARCHIVED.name());
    Map<String, SchemeItemDO> schemeItemDOMapMembers = new HashMap<>();
    for (SchemeItemDO schemeItemDO : schemeMapper.selectSchemeItemsBySchemeIds(List.of(id)))
      schemeItemDOMapMembers.put(schemeItemDO.id(), schemeItemDO);
    Set<String> seen = new HashSet<>();
    List<SchemeEntry> schemeEntryListEntries = new ArrayList<>();
    for (String itemId : reorderScheme.itemIds()) {
      SchemeItemDO schemeItemDO = schemeItemDOMapMembers.get(itemId);
      if (schemeItemDO == null) throw missing();
      if (!seen.add(itemId)) throw ApiFailure.invalid();
      schemeEntryListEntries.add(
          new SchemeEntry(schemeItemDO.skuId(), schemeItemDO.quantity(), schemeItemDO.remark()));
    }
    if (seen.size() != schemeItemDOMapMembers.size()) throw ApiFailure.invalid();
    LocalDateTime now = now(clock);
    if (!schemeMembers.replace(schemeDOInitial, schemeEntryListEntries, now))
      return planningOperations.record(
          planningCommand, PlanningResource.scheme, id, schemeDOInitial.version());
    SchemeDO schemeDOUpdated =
        new SchemeDO(
            schemeDOInitial.id(),
            schemeDOInitial.ownerUserId(),
            schemeDOInitial.schemeName(),
            schemeDOInitial.description(),
            schemeDOInitial.schemeStatus(),
            schemeDOInitial.activeSlotNo(),
            next(schemeDOInitial.contentVersion()),
            next(schemeDOInitial.version()),
            schemeDOInitial.shareEnabled(),
            schemeDOInitial.archivedAt(),
            schemeDOInitial.createdAt(),
            now);
    schemeMapper.updateScheme(schemeDOUpdated);
    return planningOperations.record(
        planningCommand, PlanningResource.scheme, id, schemeDOUpdated.version());
  }

  /** 独立签发链接，同请求回放原链接，内容按版本和价格模式复用不可变快照。 */
  public ShareIssued share(
      PlanningCommand planningCommand, String id, ShareSet shareSet, boolean disable) {
    planningOperations.lock(planningCommand.actor());
    SchemeDO schemeDO = schemeMapper.selectOwnedSchemeById(planningCommand.actor(), id, true);
    if (schemeDO == null) throw missing();
    Receipt receipt = planningOperations.replay(planningCommand);
    if (receipt != null) {
      SchemeShareDO previousSchemeShareDO =
          schemeMapper.selectSchemeShareByRequest(
              planningCommand.actor(), planningCommand.requestId());
      if (!disable && previousSchemeShareDO == null) throw missing();
      return new ShareIssued(
          receipt,
          previousSchemeShareDO == null
              ? null
              : new ShareConfig(
                  previousSchemeShareDO.id(),
                  previousSchemeShareDO.shareToken(),
                  previousSchemeShareDO.shareStatus(),
                  previousSchemeShareDO.hidePrice(),
                  time(previousSchemeShareDO.expiresAt()),
                  Integer.toString(previousSchemeShareDO.viewCount())));
    }
    planningOperations.writable(planningCommand.actor());
    version(schemeDO.version(), shareSet.expectedVersion());
    if (!schemeDO.shareEnabled()) throw ApiFailure.denied(D2Codes.CODE_PERMISSION_DENIED);
    LocalDateTime now = now(clock);
    SchemeShareDO schemeShareDO = null;
    if (disable) schemeMapper.disableSchemeSharesBySchemeId(id, now);
    else {
      SchemeShareSnapshotDO schemeShareSnapshotDO =
          schemeMapper.selectShareSnapshotByContent(
              id, schemeDO.contentVersion(), shareSet.hidePrice());
      if (schemeShareSnapshotDO == null) {
        List<ShareSnapshot.Item> items = new ArrayList<>();
        for (SchemeItemDO schemeItemDO : schemeMapper.selectSchemeItemsBySchemeIds(List.of(id))) {
          SnapshotV1 snapshotV1 =
              planningSnapshots.read(
                  schemeItemDO.skuSnapshotJson(), schemeItemDO.skuId(), schemeItemDO.factoryId());
          if (shareSet.hidePrice())
            snapshotV1 =
                new SnapshotV1(
                    snapshotV1.schemaVersion(),
                    snapshotV1.skuId(),
                    snapshotV1.factoryId(),
                    snapshotV1.factoryName(),
                    snapshotV1.name(),
                    snapshotV1.assetType(),
                    snapshotV1.typeCode(),
                    snapshotV1.typeLabel(),
                    snapshotV1.styleCodes(),
                    snapshotV1.styleLabels(),
                    snapshotV1.sizeDesc(),
                    snapshotV1.lengthCm(),
                    snapshotV1.widthCm(),
                    snapshotV1.heightCm(),
                    snapshotV1.weightKg(),
                    snapshotV1.materialDesc(),
                    snapshotV1.imageKeys(),
                    null);
          items.add(
              new ShareSnapshot.Item(
                  snapshotV1,
                  schemeItemDO.quantity(),
                  schemeItemDO.remark(),
                  schemeItemDO.sortOrder()));
        }
        ShareSnapshot shareSnapshot =
            new ShareSnapshot(
                schemeDO.schemeName(),
                schemeDO.description(),
                schemeDO.schemeStatus(),
                schemeDO.contentVersion(),
                shareSet.hidePrice(),
                items);
        schemeShareSnapshotDO =
            new SchemeShareSnapshotDO(
                Ids.next(),
                id,
                schemeDO.contentVersion(),
                shareSet.hidePrice(),
                objectMapper.writeValueAsString(shareSnapshot),
                now);
        schemeMapper.insertShareSnapshot(schemeShareSnapshotDO);
      }
      byte[] bytes = new byte[32];
      new SecureRandom().nextBytes(bytes);
      String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
      schemeShareDO =
          new SchemeShareDO(
              Ids.next(),
              id,
              schemeShareSnapshotDO.id(),
              planningCommand.requestId(),
              token,
              SchemeShareStatus.active.name(),
              shareSet.hidePrice(),
              now.plusDays(7),
              0,
              planningCommand.actor(),
              now,
              now);
      schemeMapper.insertSchemeShare(schemeShareDO);
    }
    SchemeDO schemeDOUpdated =
        new SchemeDO(
            schemeDO.id(),
            schemeDO.ownerUserId(),
            schemeDO.schemeName(),
            schemeDO.description(),
            schemeDO.schemeStatus(),
            schemeDO.activeSlotNo(),
            schemeDO.contentVersion(),
            next(schemeDO.version()),
            schemeDO.shareEnabled(),
            schemeDO.archivedAt(),
            schemeDO.createdAt(),
            now);
    schemeMapper.updateScheme(schemeDOUpdated);
    receipt =
        planningOperations.record(
            planningCommand, PlanningResource.scheme, id, schemeDOUpdated.version());
    return new ShareIssued(
        receipt,
        schemeShareDO == null
            ? null
            : new ShareConfig(
                schemeShareDO.id(),
                schemeShareDO.shareToken(),
                schemeShareDO.shareStatus(),
                schemeShareDO.hidePrice(),
                time(schemeShareDO.expiresAt()),
                "0"));
  }

  /** 使用独立隐藏响应类型，成功读取后才计浏览。 */
  @Transactional(isolation = Isolation.REPEATABLE_READ)
  public SharedScheme shared(String token) {
    SchemeShareDO schemeShareDOShare = schemeMapper.selectSchemeShareByToken(token);
    if (schemeShareDOShare == null
        || !SchemeShareStatus.active.name().equals(schemeShareDOShare.shareStatus())
        || schemeShareDOShare.expiresAt() == null
        || !now(clock).isBefore(schemeShareDOShare.expiresAt())
        || schemeShareDOShare.snapshotId() == null)
      throw new ApiFailure(404, D2Codes.CODE_NOT_FOUND, PlanningReason.SHARE_INVALID.name());
    SchemeDO schemeDOInitial = schemeMapper.selectSchemeById(schemeShareDOShare.schemeId());
    if (schemeDOInitial == null || !schemeDOInitial.shareEnabled())
      throw new ApiFailure(404, D2Codes.CODE_NOT_FOUND, PlanningReason.SHARE_INVALID.name());
    SchemeShareSnapshotDO schemeShareSnapshotDO =
        schemeMapper.selectShareSnapshotById(schemeShareDOShare.snapshotId());
    if (schemeShareSnapshotDO == null
        || !schemeShareSnapshotDO.schemeId().equals(schemeDOInitial.id())
        || schemeShareSnapshotDO.hidePrice() != schemeShareDOShare.hidePrice())
      throw new ApiFailure(404, D2Codes.CODE_NOT_FOUND, PlanningReason.SHARE_INVALID.name());
    ShareSnapshot shareSnapshot =
        objectMapper.readValue(schemeShareSnapshotDO.snapshotJson(), ShareSnapshot.class);
    List<ShareSnapshot.Item> shareSnapshotItemList = shareSnapshot.items();
    List<SnapshotV1> snapshotV1ListSnapshots = new ArrayList<>();
    for (ShareSnapshot.Item item : shareSnapshotItemList)
      snapshotV1ListSnapshots.add(item.display());
    Map<String, PublicImageViewVO> publicImageViewVOMapImages =
        planningImages.sign(snapshotV1ListSnapshots);
    BigInteger bigIntegerQuantity = BigInteger.ZERO;
    int daily = 0, deposit = 0;
    List<HiddenItem> hiddenItemListHidden = new ArrayList<>();
    List<PricedItem> pricedItemListPriced = new ArrayList<>();
    for (int index = 0; index < shareSnapshotItemList.size(); index++) {
      ShareSnapshot.Item shareSnapshotItem = shareSnapshotItemList.get(index);
      SnapshotV1 snapshotV1 = snapshotV1ListSnapshots.get(index);
      bigIntegerQuantity = bigIntegerQuantity.add(BigInteger.valueOf(shareSnapshotItem.quantity()));
      if (schemeShareDOShare.hidePrice())
        hiddenItemListHidden.add(
            new HiddenItem(
                PlanningDisplayConverter.hidden(
                    snapshotV1, planningImages.cover(snapshotV1, publicImageViewVOMapImages)),
                shareSnapshotItem.quantity(),
                shareSnapshotItem.remark(),
                shareSnapshotItem.sortOrder()));
      else {
        int d = multiply(snapshotV1.prices().dailyRentPriceFen(), shareSnapshotItem.quantity(), 1),
            p = multiply(snapshotV1.prices().depositAmountFen(), shareSnapshotItem.quantity(), 1);
        daily = add(daily, d);
        deposit = add(deposit, p);
        pricedItemListPriced.add(
            new PricedItem(
                PlanningDisplayConverter.priced(
                    snapshotV1, planningImages.cover(snapshotV1, publicImageViewVOMapImages)),
                shareSnapshotItem.quantity(),
                shareSnapshotItem.remark(),
                shareSnapshotItem.sortOrder(),
                d,
                p));
      }
    }
    schemeMapper.incrementSchemeShareViewsByToken(schemeShareDOShare.id(), token);
    if (schemeShareDOShare.hidePrice())
      return new HiddenScheme(
          shareSnapshot.name(),
          shareSnapshot.description(),
          shareSnapshot.status(),
          shareSnapshot.contentVersion(),
          bigIntegerQuantity.toString(),
          DISCLAIMER,
          true,
          hiddenItemListHidden);
    return new PricedScheme(
        shareSnapshot.name(),
        shareSnapshot.description(),
        shareSnapshot.status(),
        shareSnapshot.contentVersion(),
        bigIntegerQuantity.toString(),
        DISCLAIMER,
        false,
        pricedItemListPriced,
        daily,
        deposit);
  }
}
