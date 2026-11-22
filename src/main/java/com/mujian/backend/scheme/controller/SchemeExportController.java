package com.mujian.backend.scheme.controller;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.planning.service.PlanningHttp;
import com.mujian.backend.scheme.domain.PdfFileMetadata;
import com.mujian.backend.scheme.domain.SchemeExportDO;
import com.mujian.backend.scheme.dto.SchemeDtos.ExportPdf;
import com.mujian.backend.scheme.service.*;
import com.mujian.backend.scheme.vo.SchemeViews.ExportStatus;
import jakarta.servlet.http.*;
import java.io.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class SchemeExportController extends BaseController {
  private final SchemeExportService schemeExportService;
  private final PlanningHttp planningHttp;
  private final PdfFiles pdfFiles;

  /** 文件输出复用统一身份及契约过滤器。 */
  public SchemeExportController(
      SchemeExportService schemeExportService, PlanningHttp planningHttp, PdfFiles pdfFiles) {
    this.schemeExportService = schemeExportService;
    this.planningHttp = planningHttp;
    this.pdfFiles = pdfFiles;
  }

  /** 接纳或恢复生成，完成后同步有界传输。 */
  @PostMapping("/api/me/schemes/{id}/exports")
  public void export(
      HttpServletRequest httpServletRequestRequest,
      HttpServletResponse httpServletResponseResponse,
      @PathVariable String id,
      @RequestBody ExportPdf exportPdf)
      throws IOException {
    SchemeExportService.Result result =
        schemeExportService.export(planningHttp.actor(httpServletRequestRequest), id, exportPdf);
    try (PdfFiles.Lease lease = result.lease()) {
      stream(httpServletResponseResponse, result.file(), result.cacheHit(), lease);
    }
  }

  /** 查询原生成状态。 */
  @GetMapping("/api/me/scheme-exports/{id}")
  public ApiResponse<ExportStatus> status(
      HttpServletRequest httpServletRequestRequest, @PathVariable String id) {
    return success(
        httpServletRequestRequest,
        schemeExportService.status(planningHttp.actor(httpServletRequestRequest), id));
  }

  /** GET只重取原文件，不消耗额度。 */
  @GetMapping("/api/me/scheme-exports/{id}/file")
  public void file(
      HttpServletRequest httpServletRequestRequest,
      HttpServletResponse httpServletResponseResponse,
      @PathVariable String id)
      throws IOException {
    download(
        httpServletResponseResponse,
        schemeExportService.file(planningHttp.actor(httpServletRequestRequest), id),
        true);
  }

  /** 打开租约后再提交PDF响应，传输过程中不会被清理删除。 */
  private void download(
      HttpServletResponse httpServletResponseResponse,
      SchemeExportDO schemeExportDO,
      boolean cacheHit)
      throws IOException {
    try (PdfFiles.Lease lease = pdfFiles.open(schemeExportDO)) {
      stream(
          httpServletResponseResponse,
          new PdfFileMetadata(schemeExportDO.id(), schemeExportDO.fileExpireAt()),
          cacheHit,
          lease);
    }
  }

  /** 输出已打开的同一租约，不再进行缓存查找或重新生成。 */
  private void stream(
      HttpServletResponse httpServletResponseResponse,
      PdfFileMetadata file,
      boolean cacheHit,
      PdfFiles.Lease lease)
      throws IOException {
    httpServletResponseResponse.setCharacterEncoding((String) null);
    httpServletResponseResponse.setContentType("application/pdf");
    httpServletResponseResponse.setContentLengthLong(lease.length());
    httpServletResponseResponse.setHeader(
        "Content-Disposition",
        "attachment; filename=scheme-"
            + file.id()
            + ".pdf; filename*=UTF-8''"
            + java.net.URLEncoder.encode("幕间方案书.pdf", java.nio.charset.StandardCharsets.UTF_8));
    httpServletResponseResponse.setHeader("X-Export-Id", file.id());
    httpServletResponseResponse.setHeader("X-Cache-Hit", Boolean.toString(cacheHit));
    httpServletResponseResponse.setHeader("X-File-Expires-At", time(file.fileExpireAt()));
    byte[] buffer = new byte[16384];
    int count;
    while ((count = lease.input().read(buffer)) != -1)
      httpServletResponseResponse.getOutputStream().write(buffer, 0, count);
    httpServletResponseResponse.getOutputStream().flush();
  }
}
