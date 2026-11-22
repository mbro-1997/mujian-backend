package com.mujian.backend.factory.mapper;

import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.projection.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FactoryOnboardingAuditMapper {

  /** 保存工厂入驻申请、材料及已接受规则。 */
  void insertFactoryOnboarding(@Param("factoryOnboardingAuditDO") FactoryOnboardingAuditDO factoryOnboardingAuditDO);

  /** 按申请版本保存入驻审核决定及关联工厂。 */
  int reviewFactoryOnboarding(@Param("factoryOnboardingAuditDO") FactoryOnboardingAuditDO factoryOnboardingAuditDO);

  /** 判断邀请码是否已关联入驻申请。 */
  boolean hasFactoryOnboardingByInvitation(@Param("invite") String invite);
}
