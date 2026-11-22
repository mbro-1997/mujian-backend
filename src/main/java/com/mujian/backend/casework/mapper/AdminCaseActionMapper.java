package com.mujian.backend.casework.mapper;

import com.mujian.backend.casework.domain.*;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminCaseActionMapper {
  /** 核验父动作的成功步骤。 */
  List<Integer> selectReturnSteps(@Param("parent") String parent);

  /** 在同一案件下定位先前已落库的核验父动作。 */
  String selectReturnParent(@Param("caseId") String caseId,@Param("checkId") String checkId);

  /** 按明确工单业务条件访问对应记录。 */
  void insertAdminCaseAction(@Param("adminCaseActionDO") AdminCaseActionDO adminCaseActionDO);

  /** 按明确工单业务条件访问对应记录。 */
  List<AdminCaseActionDO> selectAdminCaseActionsByIdempotencyKey(@Param("key") String key);

  /** 按明确工单业务条件访问对应记录。 */
  String selectLatestSucceededActionRemark(@Param("source") String source);
}
