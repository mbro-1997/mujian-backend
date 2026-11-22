package com.mujian.backend.factory.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.dto.FactoryDtos.ChangeSubmit;
import com.mujian.backend.factory.dto.FactoryDtos.ChangeSubmitResult;
import com.mujian.backend.factory.dto.FactoryDtos.OnboardingSubmit;
import com.mujian.backend.factory.dto.FactoryDtos.OnboardingSubmitResult;
import com.mujian.backend.factory.projection.*;
import java.time.*;
import java.util.*;

/** 工厂业务契约，复用现有流程及响应。 */
public interface FactorySubmissionService {

  /** 处理工厂入驻资料。 */
  OnboardingSubmitResult onboarding(String owner, String key, OnboardingSubmit onboardingSubmit);

  /** 处理工厂资料变更。 */
  ChangeSubmitResult change(String owner, String key, ChangeSubmit changeSubmit);
}
