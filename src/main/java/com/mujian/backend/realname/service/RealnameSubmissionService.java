package com.mujian.backend.realname.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.support.*;
import com.mujian.backend.material.service.*;
import com.mujian.backend.realname.vo.RealnameApplicationVO;
import com.mujian.backend.verification.vo.VerificationSubmitVO;
import java.time.*;
import java.util.*;
import org.springframework.transaction.annotation.*;

public interface RealnameSubmissionService {
  public record SubmitRequest(
      String realName,
      String idCard,
      String frontUploadId,
      String backUploadId,
      boolean consentAccepted,
      String consentVersion) {}

  /** 校验并提交认证申请。 */
  VerificationSubmitVO<RealnameApplicationVO> submit(
      String user, String key, SubmitRequest submitRequest);
}
