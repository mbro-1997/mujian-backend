package com.mujian.backend.student.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.support.*;
import com.mujian.backend.material.service.*;
import com.mujian.backend.student.vo.StudentApplicationVO;
import com.mujian.backend.verification.vo.VerificationSubmitVO;
import java.time.*;
import java.util.*;
import java.util.List;
import org.springframework.transaction.annotation.*;

public interface StudentSubmissionService {
  public record SubmitRequest(
      String schoolName,
      List<String> materialUploadIds,
      boolean consentAccepted,
      String consentVersion) {}

  /** 校验并提交认证申请。 */
  VerificationSubmitVO<StudentApplicationVO> submit(
      String user, String key, SubmitRequest submitRequest);
}
