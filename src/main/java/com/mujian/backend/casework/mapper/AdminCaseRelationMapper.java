package com.mujian.backend.casework.mapper;

import com.mujian.backend.casework.domain.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminCaseRelationMapper {
  /** 按明确工单业务条件访问对应记录。 */
  void insertAdminCaseRelation(@Param("adminCaseRelationDO") AdminCaseRelationDO adminCaseRelationDO);
}
