package com.mujian.backend.realname.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.material.service.*;
import com.mujian.backend.verification.vo.VerificationReviewResultVO;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.*;

public interface RealnameReviewService {
  public record ReviewCommand(
      String caseId,
      int expectedVersion,
      int expectedCaseVersion,
      String remark,
      Integer validYears) {}

  /** 执行审核及幂等回放。 */
  VerificationReviewResultVO review(
      String actor,
      String application,
      String request,
      boolean approve,
      ReviewCommand reviewCommand,
      String trace);
}
