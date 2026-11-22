package com.mujian.backend.casework.projection;

import java.time.LocalDateTime;

public record ServiceCaseDetailProjection(
      String id,
      String caseNo,
      String caseType,
      String caseStatus,
      String title,
      LocalDateTime deadlineAt,
      String sourceType,
      String sourceId,
      int version,
      String summary,
      String resolutionSummary,
      String refsJson,
      String contextId,
      String demand,
      String mainConflict) {}
