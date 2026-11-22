package com.mujian.backend.factory.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.*;
import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.projection.*;
import java.util.*;

/** 工厂业务契约，复用现有流程及响应。 */
public interface FactoryReadService {

  /** 处理工厂入驻资料。 */
  OnboardingApplication onboarding(FactorySourceProjection factorySourceProjection);

  /** 处理工厂资料变更。 */
  ChangeApplication change(FactorySourceProjection factorySourceProjection);

  /** 核对本人归属并返回入驻申请。 */
  OnboardingApplication ownOnboarding(String user, String id);

  /** 核对本人归属并返回资料变更申请。 */
  ChangeApplication ownChange(String user, String id);

  /** 按本人请求编号恢复入驻申请。 */
  OnboardingApplication recoverOnboarding(String user, String key);

  /** 按本人请求编号恢复资料变更申请。 */
  ChangeApplication recoverChange(String user, String key);

  /** 返回当前入驻状态及允许的下一步操作。 */
  OnboardingCurrent currentOnboarding(String user);

  /** 返回当前资料变更状态及允许的下一步操作。 */
  ChangeCurrent currentChange(String user);

  /** 取得当前账号关联的工厂资料并核对访问条件。 */
  FactoryProfile profile(String user);

  /** 检查管理权限后返回入驻审核详情。 */
  AdminOnboarding adminOnboarding(String actor, String id);

  /** 检查管理权限后返回资料变更审核详情。 */
  AdminChange adminChange(String actor, String id);

  /** 组装管理员工厂详情，保持材料权限边界。 */
  AdminFactory adminProfile(String actor, String id);

  /** 组装管理员工厂详情，保持材料权限边界。 */
  AdminFactory adminProfile(FactorySourceProjection factorySourceProjection);
}
