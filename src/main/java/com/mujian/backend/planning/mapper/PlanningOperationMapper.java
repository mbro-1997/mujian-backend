package com.mujian.backend.planning.mapper;

import com.mujian.backend.planning.domain.PlanningOperationDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PlanningOperationMapper {

  /** 在用户根锁内查询原请求事实；恢复读取不依赖资源当前状态。 */
  PlanningOperationDO selectPlanningOperationByActorAndRequestId(
      @Param("actor") String actor, @Param("requestId") String requestId);

  /** 与业务写入或PDF预留在同一事务保存回执。 */
  void insertPlanningOperation(@Param("row") PlanningOperationDO planningOperationDO);
}
