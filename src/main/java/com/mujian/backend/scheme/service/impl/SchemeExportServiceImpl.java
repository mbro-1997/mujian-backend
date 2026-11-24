package com.mujian.backend.scheme.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

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
  private final SchemePdfRenderer schemePdfRenderer;
  private final PlanningOperations planningOperations;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 生成与HTTP传输均不持有业务事务。 */
  public SchemeExportServiceImpl(
      PdfReservationService pdfReservationService,
      SchemeExportMapper schemeExportMapper,
      PdfFiles pdfFiles,
      SchemePdfRenderer schemePdfRenderer,
      PlanningOperations planningOperations,
      ObjectMapper objectMapper,
      Clock clock) {
    this.pdfReservationService = pdfReservationService;
    this.schemeExportMapper = schemeExportMapper;
    this.pdfFiles = pdfFiles;
    this.schemePdfRenderer = schemePdfRenderer;
    this.planningOperations = planningOperations;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 单POST按当前内容取得成品或生成；已提交任务失败也计次。 */
  public Result export(String actor, String schemeId, ExportPdf exportPdf) {
    PdfReservationService.Reservation reservation =
        pdfReservationService.reserve(actor, schemeId, exportPdf);
    if (reservation.cachedResult() != null) return reservation.cachedResult();
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
    return pdfReservationService.openReady(actor, id, false);
  }

  /** 查询生成事实，文件丢失不会抹掉success事实。 */
  public ExportStatus status(String actor, String id) {
    planningOperations.readable(actor);
    SchemeExportDO schemeExportDO = schemeExportMapper.selectDownloadableExportById(actor, id);
    boolean sourceExists = schemeExportDO != null;
    if (!sourceExists) schemeExportDO = schemeExportMapper.selectOwnedExportById(actor, id);
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
        sourceExists && pdfFiles.available(schemeExportDO),
        schemeExportDO.errorMessage());
  }

  /** 只读取已有文件，账号受限仍可获权下载。 */
  public Result file(String actor, String id) {
    planningOperations.readable(actor);
    return pdfReservationService.openReady(actor, id, true);
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
