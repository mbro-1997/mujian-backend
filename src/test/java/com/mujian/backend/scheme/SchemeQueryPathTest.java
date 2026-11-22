package com.mujian.backend.scheme;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mujian.backend.account.mapper.AccountMapper;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.SchemeSaveAction;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.planning.domain.SnapshotV1;
import com.mujian.backend.planning.service.*;
import com.mujian.backend.planning.vo.PlanningViews.Prices;
import com.mujian.backend.scheme.domain.*;
import com.mujian.backend.scheme.dto.SchemeDtos.*;
import com.mujian.backend.scheme.mapper.*;
import com.mujian.backend.scheme.projection.SchemeAmountProjection;
import com.mujian.backend.scheme.service.*;
import com.mujian.backend.scheme.service.impl.SchemeExportServiceImpl;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

/** 两处查询路径的定向回归，不连接或改动共享数据。 */
class SchemeQueryPathTest {
  final SchemeMapper mapper = mock(SchemeMapper.class);
  final PlanningSnapshots snapshots = mock(PlanningSnapshots.class);
  final SchemeMembers members = new SchemeMembers(mapper, snapshots);
  final Clock clock = Clock.fixed(Instant.parse("2026-11-18T03:00:00Z"), ZoneOffset.UTC);
  final LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai"));
  final String actor = Ids.next(),
      sid = Ids.next(),
      iid = Ids.next(),
      sku = Ids.next(),
      factory = Ids.next();
  final SchemeDO scheme =
      new SchemeDO(sid, actor, "原名称", null, "active", 1, 1, 1, false, null, now, now);

  /** 基本信息变更和无变化均不得读取明细或SKU。 */
  @Test
  void renameDoesNotQueryMembers() {
    assertTrue(
        members
            .save(
                scheme,
                List.of(
                    new SaveAction(
                        SchemeSaveAction.updateSchemeInfo,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "新名称",
                        null)),
                now)
            .changed());
    assertFalse(
        members
            .save(
                scheme,
                List.of(
                    new SaveAction(
                        SchemeSaveAction.updateSchemeInfo,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "原名称",
                        null)),
                now)
            .changed());
    verifyNoInteractions(mapper, snapshots);
  }

  SchemeItemDO item() {
    return new SchemeItemDO(iid, sid, sku, factory, 1, null, 0, "frozen", now, now);
  }

  SaveAction update(int quantity, String remark) {
    return new SaveAction(SchemeSaveAction.updateItem, null, iid, quantity, remark, 0, null, null);
  }

  /** 一条数量变更仅加载该行，聚合其余金额，保留快照并只写该行。 */
  @Test
  void quantityLoadsOnlyTargetAndChecksAggregate() {
    when(mapper.selectSchemeItemsBySchemeAndIds(sid, List.of(iid))).thenReturn(List.of(item()));
    when(mapper.selectSchemeAmountsExcludingIds(sid, List.of(iid)))
        .thenReturn(new SchemeAmountProjection(BigDecimal.TEN, BigDecimal.TEN));
    SnapshotV1 snapshot = mock(SnapshotV1.class);
    Prices prices = mock(Prices.class);
    when(snapshot.prices()).thenReturn(prices);
    when(prices.dailyRentPriceFen()).thenReturn(100);
    when(prices.depositAmountFen()).thenReturn(200);
    when(snapshots.read("frozen", sku, factory)).thenReturn(snapshot);
    assertTrue(members.save(scheme, List.of(update(2, null)), now).changed());
    verify(mapper).selectSchemeItemsBySchemeAndIds(sid, List.of(iid));
    verify(mapper).selectSchemeAmountsExcludingIds(sid, List.of(iid));
    verify(mapper)
        .updateSchemeItemBySchemeAndId(
            argThat(
                r ->
                    r.id().equals(iid)
                        && r.quantity() == 2
                        && r.skuSnapshotJson().equals("frozen")));
    verifyNoMoreInteractions(mapper);
    verify(snapshots).read("frozen", sku, factory);
    verifyNoMoreInteractions(snapshots);
  }

  /** 备注和无变化不触发金额及SKU快照解析。 */
  @Test
  void remarkAndNoopAvoidAmounts() {
    when(mapper.selectSchemeItemsBySchemeAndIds(sid, List.of(iid))).thenReturn(List.of(item()));
    assertFalse(members.save(scheme, List.of(update(1, null)), now).changed());
    assertTrue(members.save(scheme, List.of(update(1, "备注")), now).changed());
    verify(mapper, times(2)).selectSchemeItemsBySchemeAndIds(sid, List.of(iid));
    verify(mapper).updateSchemeItemBySchemeAndId(any());
    verifyNoMoreInteractions(mapper);
    verifyNoInteractions(snapshots);
  }

  /** 跨方案明细及重复动作在任何写入之前拒绝。 */
  @Test
  void foreignOrDuplicateTargetsNeverWrite() {
    when(mapper.selectSchemeItemsBySchemeAndIds(sid, List.of(iid))).thenReturn(List.of());
    assertThrows(ApiFailure.class, () -> members.save(scheme, List.of(update(2, null)), now));
    assertThrows(
        ApiFailure.class,
        () -> members.save(scheme, List.of(update(2, null), update(3, null)), now));
    verify(mapper).selectSchemeItemsBySchemeAndIds(sid, List.of(iid));
    verifyNoMoreInteractions(mapper);
    verifyNoInteractions(snapshots);
  }

  /** 聚合溢出时一行也不写，外层事务及一次版本提交仍由原保存主流程负责。 */
  @Test
  void aggregateOverflowRejectsBeforeWrite() {
    when(mapper.selectSchemeItemsBySchemeAndIds(sid, List.of(iid))).thenReturn(List.of(item()));
    when(mapper.selectSchemeAmountsExcludingIds(sid, List.of(iid)))
        .thenReturn(
            new SchemeAmountProjection(BigDecimal.valueOf(Integer.MAX_VALUE), BigDecimal.ZERO));
    SnapshotV1 snapshot = mock(SnapshotV1.class);
    Prices prices = mock(Prices.class);
    when(snapshot.prices()).thenReturn(prices);
    when(prices.dailyRentPriceFen()).thenReturn(1);
    when(snapshots.read("frozen", sku, factory)).thenReturn(snapshot);
    assertThrows(ApiFailure.class, () -> members.save(scheme, List.of(update(2, null)), now));
    verify(mapper, never()).updateSchemeItemBySchemeAndId(any());
  }

  /** 真正打开文件的命中链路不得读取导出表、生成快照或写任务。 */
  @Test
  void cacheHitOpensUnderLeaseWithoutExportQuery(@TempDir Path directory) throws Exception {
    SchemeExportMapper exports = mock(SchemeExportMapper.class);
    SchemePdfCache cache = mock(SchemePdfCache.class);
    PlanningOperations operations = mock(PlanningOperations.class);
    AccountMapper accounts = mock(AccountMapper.class);
    PdfFiles files = new PdfFiles(clock, directory.toString(), 2, 1024, 4096);
    PdfFileMetadata metadata = new PdfFileMetadata(Ids.next(), now.plusMinutes(5));
    Files.writeString(files.path(metadata.id(), false), "%PDF-test");
    when(mapper.selectOwnedSchemeById(actor, sid, true)).thenReturn(scheme);
    when(cache.key(sid, 1, false)).thenReturn("content-key");
    when(cache.get("content-key")).thenReturn(metadata);
    var reservation =
        new PdfReservationService(
            accounts,
            operations,
            snapshots,
            mapper,
            exports,
            files,
            cache,
            new ObjectMapper(),
            clock);
    SchemePdfRenderer renderer = mock(SchemePdfRenderer.class);
    var service =
        new SchemeExportServiceImpl(
            reservation, exports, files, cache, renderer, operations, new ObjectMapper(), clock);
    var result = service.export(actor, sid, new ExportPdf(false));
    assertTrue(result.cacheHit());
    assertEquals(metadata, result.file());
    try (var lease = result.lease()) {
      assertTrue(lease.lock().isHeldByCurrentThread());
      assertEquals("%PDF-test", new String(lease.input().readAllBytes()));
    }
    verify(operations).lock(actor);
    verifyNoMoreInteractions(operations);
    verify(mapper).selectOwnedSchemeById(actor, sid, true);
    verifyNoMoreInteractions(mapper);
    verifyNoInteractions(exports, snapshots, renderer, accounts);
    verify(cache).key(sid, 1, false);
    verify(cache).get("content-key");
    verifyNoMoreInteractions(cache);
  }

  /** 丢失文件清除原映射，再按原冷却拒绝，不从磁盘或导出表找替代成品。 */
  @Test
  void missingCachedFileUsesNormalCooldown(@TempDir Path directory) throws Exception {
    var exports = mock(SchemeExportMapper.class);
    var cache = mock(SchemePdfCache.class);
    var operations = mock(PlanningOperations.class);
    var files = new PdfFiles(clock, directory.toString(), 2, 1024, 4096);
    var metadata = new PdfFileMetadata(Ids.next(), now.plusMinutes(5));
    when(mapper.selectOwnedSchemeById(actor, sid, true)).thenReturn(scheme);
    when(cache.key(sid, 1, false)).thenReturn("key");
    when(cache.get("key")).thenReturn(metadata, null);
    when(exports.selectLatestExportAttemptBySchemeId(sid)).thenReturn(now);
    var reservation =
        new PdfReservationService(
            mock(AccountMapper.class),
            operations,
            snapshots,
            mapper,
            exports,
            files,
            cache,
            new ObjectMapper(),
            clock);
    var service =
        new SchemeExportServiceImpl(
            reservation,
            exports,
            files,
            cache,
            mock(SchemePdfRenderer.class),
            operations,
            new ObjectMapper(),
            clock);
    assertEquals(
        "PDF_COOLDOWN",
        assertThrows(ApiFailure.class, () -> service.export(actor, sid, new ExportPdf(false)))
            .reason);
    verify(cache).remove("key", metadata.id());
    verify(exports).selectProcessingExportBySchemeId(sid);
    verify(exports).selectLatestExportAttemptBySchemeId(sid);
    verifyNoMoreInteractions(exports);
  }
}
