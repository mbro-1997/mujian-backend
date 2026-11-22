package com.mujian.backend.scheme.service;

import com.mujian.backend.scheme.domain.PdfFileMetadata;
import com.mujian.backend.scheme.domain.SchemeExportDO;
import com.mujian.backend.scheme.dto.SchemeDtos.ExportPdf;
import com.mujian.backend.scheme.vo.SchemeViews.ExportStatus;

public interface SchemeExportService {
  record Result(PdfFileMetadata file, boolean cacheHit, PdfFiles.Lease lease) {}

  /** 按当前内容取得已打开读租约；调用方必须关闭租约。 */
  Result export(String actor, String schemeId, ExportPdf exportPdf);

  /** 只查询原导出状态。 */
  ExportStatus status(String actor, String id);

  /** 鉴权原文件，绝不自动生成。 */
  SchemeExportDO file(String actor, String id);

  /** 有限批处理超时和私有缓存。 */
  void cleanup();
}
