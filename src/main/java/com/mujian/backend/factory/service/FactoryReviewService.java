package com.mujian.backend.factory.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.*;
import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.dto.FactoryDtos.ReviewResult;
import com.mujian.backend.factory.projection.*;
import java.time.*;
import java.util.*;

/** 工厂业务契约，复用现有流程及响应。 */
public interface FactoryReviewService {
  public record Command(
      int expectedVersion,
      String baseProfileVersion,
      boolean sameLegalEntityConfirmed,
      String remark) {}

  /** 按原请求编号恢复已提交结果并核对访问权限。 */
  ReviewRecovery recover(String actor, String key);

  /** 校验并执行工厂审核。 */
  ReviewResult review(
      String actor,
      FactorySource factorySource,
      String id,
      String key,
      boolean approve,
      Command command,
      String trace);
}
