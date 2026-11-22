package com.mujian.backend.factory.mapper;

import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.projection.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FactoryProfileChangeAuditMapper {

  /** 保存工厂资料变更申请及冻结快照。 */
  void insertFactoryProfileChange(
      @Param("factoryProfileChangeAuditDO") FactoryProfileChangeAuditDO factoryProfileChangeAuditDO);

  /** 按申请版本保存资料变更审核决定。 */
  int reviewFactoryProfileChange(
      @Param("factoryProfileChangeAuditDO") FactoryProfileChangeAuditDO factoryProfileChangeAuditDO);
}
