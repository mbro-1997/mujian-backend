package com.mujian.backend.casework.mapper;

import com.mujian.backend.casework.domain.*;
import com.mujian.backend.casework.dto.CloseCaseDTO;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminCaseMapper {
  /** 按明确工单业务条件访问对应记录。 */
  void insertAdminCase(@Param("adminCaseDO") AdminCaseDO adminCaseDO);

  /** 按明确工单业务条件访问对应记录。 */
  List<AdminCaseDO> selectAdminCasesBySource(
      @Param("type") String type, @Param("source") String source, @Param("lock") boolean lock);

  /** 按明确工单业务条件访问对应记录。 */
  int closeAdminCaseByVersion(@Param("closeCaseDTO") CloseCaseDTO closeCaseDTO);

  /** 按明确工单业务条件访问对应记录。 */
  int updateAdminCaseFactory(@Param("factory") String factory, @Param("id") String id);
}
