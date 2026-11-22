package com.mujian.backend.casework.vo;

import com.mujian.backend.customer.vo.ServiceViews.Source;
import java.util.List;

public final class ServiceCaseViews {
  public record Row(
      String id,
      String caseNo,
      String caseType,
      String caseStatus,
      String title,
      String deadlineAt,
      Source source,
      int version) {}

  public record Detail(
      String id,
      String caseNo,
      String caseType,
      String caseStatus,
      String title,
      String deadlineAt,
      Source source,
      int version,
      String summary,
      String resolutionSummary,
      List<Source> resultRefs,
      String contextId,
      String demand,
      String mainConflict) {}
}
