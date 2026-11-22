package com.mujian.backend.scheme.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.planning.service.PlanningOperations;
import com.mujian.backend.scheme.domain.*;
import com.mujian.backend.scheme.dto.SchemeDtos.ExportPdf;
import com.mujian.backend.scheme.mapper.SchemeExportMapper;
import com.mujian.backend.scheme.service.*;
import com.mujian.backend.scheme.vo.SchemeViews.ExportStatus;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public class SchemeExportServiceImpl implements SchemeExportService {
  private final PdfReservationService pdfReservationService;
  private final SchemeExportMapper schemeExportMapper;
  private final PdfFiles pdfFiles;
  private final SchemePdfCache schemePdfCache;
  private final SchemePdfRenderer schemePdfRenderer;
  private final PlanningOperations planningOperations;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 生成与HTTP传输均不持有业务事务。 */
  public SchemeExportServiceImpl(
      PdfReservationService pdfReservationService,
      SchemeExportMapper schemeExportMapper,
      PdfFiles pdfFiles,
      SchemePdfCache schemePdfCache,
      SchemePdfRenderer schemePdfRenderer,
      PlanningOperations planningOperations,
      ObjectMapper objectMapper,
      Clock clock) {
    this.pdfReservationService = pdfReservationService;
    this.schemeExportMapper = schemeExportMapper;
    this.pdfFiles = pdfFiles;
    this.schemePdfCache = schemePdfCache;
    this.schemePdfRenderer = schemePdfRenderer;
    this.planningOperations = planningOperations;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 单POST按当前内容取得成品或生成；已提交任务失败也计次。 */
  public Result export(String actor, String schemeId, ExportPdf exportPdf) {
    return openExport(actor, schemeId, exportPdf, true);
  }

  /** 先打开文件再返回，命中定位失效时最多重新走一次未命中流程。 */
  private Result openExport(
      String actor, String schemeId, ExportPdf exportPdf, boolean allowCacheMiss) {
    PdfReservationService.Reservation reservation =
        pdfReservationService.reserve(actor, schemeId, exportPdf);
    if (reservation.cachedFile() != null) {
      try {
        return new Result(reservation.cachedFile(), true, pdfFiles.open(reservation.cachedFile()));
      } catch (IOException | ApiFailure exception) {
        if (exception instanceof ApiFailure failure
            && !PlanningReason.PDF_CACHE_EXPIRED.name().equals(failure.reason)) throw failure;
        schemePdfCache.remove(reservation.cacheKey(), reservation.exportId());
        if (allowCacheMiss) return openExport(actor, schemeId, exportPdf, false);
        throw conflict(PlanningReason.PDF_CACHE_EXPIRED.name()).causedBy(exception);
      }
    }
    if (reservation.generate()) {
      String id = reservation.exportId();
      ReentrantLock reentrantLockLock = pdfFiles.lock(id);
      reentrantLockLock.lock();
      try {
        SchemeExportDO schemeExportDO = schemeExportMapper.selectExportById(id);
        PdfSnapshot pdfSnapshot =
            objectMapper.readValue(schemeExportDO.snapshotJson(), PdfSnapshot.class);
        try (OutputStream outputStreamOutput = pdfFiles.output(id)) {
          schemePdfRenderer.render(
              pdfSnapshot,
              outputStreamOutput,
              schemeExportDO.processingDeadlineAt().atZone(ZONE).toInstant());
        }
        pdfFiles.publish(id);
        LocalDateTime now = now(clock);
        if (schemeExportMapper.completeExport(id, now, now.plusMinutes(5), id + ".pdf") != 1) {
          schemeExportMapper.failExport(id, now, PlanningReason.PDF_GENERATION_TIMEOUT.name());
          pdfFiles.delete(id);
        }
      } catch (Exception exception) {
        SchemeExportDO schemeExportDO = schemeExportMapper.selectExportById(id);
        String reason =
            schemeExportDO != null && !now(clock).isBefore(schemeExportDO.processingDeadlineAt())
                ? PlanningReason.PDF_GENERATION_TIMEOUT.name()
                : PlanningReason.PDF_GENERATION_FAILED.name();
        schemeExportMapper.failExport(id, now(clock), reason);
        try {
          pdfFiles.delete(id);
        } catch (IOException iOExceptionCleanupFailure) {
          exception.addSuppressed(iOExceptionCleanupFailure);
        }
        throw unavailable(reason).causedBy(exception);
      } finally {
        reentrantLockLock.unlock();
        pdfFiles.release();
      }
    }
    SchemeExportDO schemeExportDO =
        schemeExportMapper.selectOwnedExportById(actor, reservation.exportId());
    if (schemeExportDO == null) throw conflict(PlanningReason.PDF_CACHE_EXPIRED.name());
    if (SchemeExportStatus.processing.name().equals(schemeExportDO.exportStatus()))
      throw conflict(PlanningReason.PDF_PROCESSING.name());
    if (SchemeExportStatus.failed.name().equals(schemeExportDO.exportStatus()))
      throw unavailable(schemeExportDO.errorMessage());
    if (reservation.generate()) schemePdfCache.put(schemeExportDO);
    try {
      PdfFiles.Lease lease = pdfFiles.open(schemeExportDO);
      return new Result(
          new PdfFileMetadata(schemeExportDO.id(), schemeExportDO.fileExpireAt()), false, lease);
    } catch (IOException | ApiFailure exception) {
      if (exception instanceof ApiFailure apiFailure
          && !PlanningReason.PDF_CACHE_EXPIRED.name().equals(apiFailure.reason)) throw apiFailure;
      schemePdfCache.remove(
          schemePdfCache.key(
              schemeId, schemeExportDO.sourceContentVersion(), schemeExportDO.hidePrice()),
          schemeExportDO.id());
      throw conflict(PlanningReason.PDF_CACHE_EXPIRED.name()).causedBy(exception);
    }
  }

  /** 查询生成事实，文件丢失不会抹掉success事实。 */
  public ExportStatus status(String actor, String id) {
    planningOperations.readable(actor);
    SchemeExportDO schemeExportDO = schemeExportMapper.selectOwnedExportById(actor, id);
    if (schemeExportDO == null) throw missing();
    return new ExportStatus(
        schemeExportDO.id(),
        schemeExportDO.schemeId(),
        schemeExportDO.exportStatus(),
        schemeExportDO.sourceContentVersion(),
        schemeExportDO.hidePrice(),
        schemeExportDO.templateVersion(),
        time(schemeExportDO.createdAt()),
        time(schemeExportDO.generatedAt()),
        time(schemeExportDO.fileExpireAt()),
        pdfFiles.available(schemeExportDO),
        schemeExportDO.errorMessage());
  }

  /** 只读取已有文件，账号受限仍可获权下载。 */
  public SchemeExportDO file(String actor, String id) {
    planningOperations.readable(actor);
    SchemeExportDO schemeExportDO = schemeExportMapper.selectOwnedExportById(actor, id);
    if (schemeExportDO == null) throw missing();
    if (SchemeExportStatus.processing.name().equals(schemeExportDO.exportStatus()))
      throw conflict(PlanningReason.PDF_PROCESSING.name());
    if (SchemeExportStatus.failed.name().equals(schemeExportDO.exportStatus()))
      throw unavailable(schemeExportDO.errorMessage());
    if (!pdfFiles.available(schemeExportDO))
      throw conflict(PlanningReason.PDF_CACHE_EXPIRED.name());
    return schemeExportDO;
  }

  /** 每分钟恢复过期processing，清文件与终态元数据分开，回执保留。 */
  @Scheduled(fixedDelay = 60000, initialDelay = 60000)
  public void cleanup() {
    LocalDateTime now = now(clock);
    for (SchemeExportDO schemeExportDO : schemeExportMapper.selectTimedOutExportsByDeadline(now))
      schemeExportMapper.failExport(
          schemeExportDO.id(), now, PlanningReason.PDF_GENERATION_TIMEOUT.name());
    try {
      for (Path path : pdfFiles.candidates()) {
        String name = path.getFileName().toString();
        if (!name.matches("[0-9a-f]{32}\\.(pdf|tmp)")) continue;
        String id = name.substring(0, 32);
        ReentrantLock reentrantLockLock = pdfFiles.lock(id);
        if (!reentrantLockLock.tryLock()) continue;
        try {
          SchemeExportDO schemeExportDO = schemeExportMapper.selectExportById(id);
          boolean orphan =
              schemeExportDO == null
                  && Files.getLastModifiedTime(path)
                      .toInstant()
                      .isBefore(clock.instant().minusSeconds(120));
          if (orphan
              || schemeExportDO != null
                  && !SchemeExportStatus.processing.name().equals(schemeExportDO.exportStatus())
                  && !pdfFiles.available(schemeExportDO)) pdfFiles.delete(id);
        } finally {
          reentrantLockLock.unlock();
        }
      }
    } catch (IOException iOExceptionException) {
      org.slf4j.LoggerFactory.getLogger(getClass())
          .warn(
              "event=pdf_cleanup_failed type={}", iOExceptionException.getClass().getSimpleName());
    }
    schemeExportMapper.deleteExpiredExportRecordsByTime(now);
  }
}
