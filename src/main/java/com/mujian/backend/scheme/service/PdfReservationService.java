package com.mujian.backend.scheme.service;

import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.planning.utils.PlanningValues.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.planning.domain.SnapshotV1;
import com.mujian.backend.planning.service.*;
import com.mujian.backend.scheme.domain.*;
import com.mujian.backend.scheme.dto.SchemeDtos.ExportPdf;
import com.mujian.backend.scheme.mapper.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import tools.jackson.databind.ObjectMapper;

/** 短事务只预留事实，不在用户根锁内绘制或下载图片。 */
@Service
public class PdfReservationService {
  private final com.mujian.backend.account.mapper.AccountMapper accountMapper;
  private final PlanningOperations planningOperations;
  private final PlanningSnapshots planningSnapshots;
  private final SchemeMapper schemeMapper;
  private final SchemeExportMapper schemeExportMapper;
  private final PdfFiles pdfFiles;
  private final SchemePdfCache schemePdfCache;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public record Reservation(
      String exportId, boolean generate, String cacheKey, PdfFileMetadata cachedFile) {}

  /** 复用本批根锁及私有容量。 */
  public PdfReservationService(
      com.mujian.backend.account.mapper.AccountMapper accountMapper,
      PlanningOperations planningOperations,
      PlanningSnapshots planningSnapshots,
      SchemeMapper schemeMapper,
      SchemeExportMapper schemeExportMapper,
      PdfFiles pdfFiles,
      SchemePdfCache schemePdfCache,
      ObjectMapper objectMapper,
      Clock clock) {
    this.accountMapper = accountMapper;
    this.planningOperations = planningOperations;
    this.planningSnapshots = planningSnapshots;
    this.schemeMapper = schemeMapper;
    this.schemeExportMapper = schemeExportMapper;
    this.pdfFiles = pdfFiles;
    this.schemePdfCache = schemePdfCache;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 授权当前已保存内容，先Redis成品，再processing/冷却/配额/容量。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Reservation reserve(String actor, String schemeId, ExportPdf exportPdf) {
    planningOperations.lock(actor);
    SchemeDO schemeDOInitial = schemeMapper.selectOwnedSchemeById(actor, schemeId, true);
    if (schemeDOInitial == null) throw missing();
    LocalDateTime now = now(clock);
    String cacheKey =
        schemePdfCache.key(schemeId, schemeDOInitial.contentVersion(), exportPdf.hidePrice());
    PdfFileMetadata cached = schemePdfCache.get(cacheKey);
    if (cached != null) return new Reservation(cached.id(), false, cacheKey, cached);
    planningOperations.writable(actor);
    if (schemeExportMapper.selectProcessingExportBySchemeId(schemeId) != null)
      throw conflict(PlanningReason.PDF_PROCESSING.name());
    LocalDateTime latest = schemeExportMapper.selectLatestExportAttemptBySchemeId(schemeId);
    if (latest != null && now.isBefore(latest.plusMinutes(3))) {
      long millis = Duration.between(now, latest.plusMinutes(3)).toMillis();
      throw new ApiFailure(
          429,
          D2Codes.CODE_RATE_LIMITED,
          PlanningReason.PDF_COOLDOWN.name(),
          (int) Math.max(1, (millis + 999) / 1000));
    }
    LocalDateTime start = now.toLocalDate().atStartOfDay(), end = start.plusDays(1);
    if (schemeExportMapper.selectExportCountByActorAndDay(actor, start, end) >= 5) {
      long millis = Duration.between(now, end).toMillis();
      throw new ApiFailure(
          429,
          D2Codes.CODE_RATE_LIMITED,
          PlanningReason.PDF_DAILY_LIMIT.name(),
          (int) Math.max(1, (millis + 999) / 1000));
    }
    if (!pdfFiles.acquire()) throw unavailable(PlanningReason.PDF_CAPACITY_UNAVAILABLE.name());
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          /** 预留事务失败必须归还未开始生成的容量。 */
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) pdfFiles.release();
          }
        });
    List<PdfSnapshot.Item> itemListItems = new ArrayList<>();
    for (SchemeItemDO schemeItemDO : schemeMapper.selectSchemeItemsBySchemeIds(List.of(schemeId))) {
      SnapshotV1 snapshotV1 =
          planningSnapshots.read(
              schemeItemDO.skuSnapshotJson(), schemeItemDO.skuId(), schemeItemDO.factoryId());
      itemListItems.add(
          new PdfSnapshot.Item(
              snapshotV1.name(),
              snapshotV1.factoryName(),
              snapshotV1.typeLabel(),
              snapshotV1.styleLabels(),
              snapshotV1.sizeDesc(),
              snapshotV1.materialDesc(),
              snapshotV1.imageKeys(),
              schemeItemDO.quantity(),
              schemeItemDO.remark(),
              exportPdf.hidePrice() ? null : snapshotV1.prices()));
    }
    PdfSnapshot pdfSnapshot =
        new PdfSnapshot(
            schemeDOInitial.schemeName(),
            schemeDOInitial.description(),
            schemeDOInitial.contentVersion(),
            SchemePdfCache.TEMPLATE,
            exportPdf.hidePrice(),
            SchemePdfCache.WATERMARK,
            time(now),
            accountMapper.selectAccountContextByUserId(actor).nickname(),
            itemListItems);
    String id = Ids.next();
    schemeExportMapper.insertExport(
        new SchemeExportDO(
            id,
            schemeId,
            SchemeExportStatus.processing.name(),
            schemeDOInitial.contentVersion(),
            SchemePdfCache.TEMPLATE,
            exportPdf.hidePrice(),
            true,
            pdfSnapshot.watermark(),
            null,
            null,
            now.plusSeconds(120),
            null,
            now.plusDays(7),
            null,
            null,
            objectMapper.writeValueAsString(pdfSnapshot),
            actor,
            now,
            now));
    return new Reservation(id, true, cacheKey, null);
  }
}
