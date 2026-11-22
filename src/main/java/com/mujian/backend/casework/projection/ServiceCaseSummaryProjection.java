package com.mujian.backend.casework.projection;

import java.time.LocalDateTime;

public record ServiceCaseSummaryProjection(
      String id,
      String caseNo,
      String caseType,
      String caseStatus,
      String title,
      LocalDateTime deadlineAt,
      String sourceType,
      String sourceId,
      int version) {}
