package com.mujian.backend.student.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.material.service.*;
import com.mujian.backend.student.vo.StudentReviewResultVO;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.*;

public interface StudentReviewService {
  public record ReviewCommand(
      String caseId,
      int expectedVersion,
      int expectedCaseVersion,
      String remark,
      Integer validYears) {}

  /** 执行审核及幂等回放。 */
  StudentReviewResultVO review(
      String actor,
      String application,
      String request,
      boolean approve,
      ReviewCommand reviewCommand,
      String trace);
}
