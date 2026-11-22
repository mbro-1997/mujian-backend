package com.mujian.backend.casework.mapper;

import com.mujian.backend.casework.domain.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminOperationAuditMapper {
  /** 按明确工单业务条件访问对应记录。 */
  void insertAdminOperationAudit(@Param("adminOperationAuditDO") AdminOperationAuditDO adminOperationAuditDO);

  /** 按明确工单业务条件访问对应记录。 */
  List<String> selectInvitationRevocationAuditByRequest(
      @Param("actor") String actor, @Param("key") String key);
}
