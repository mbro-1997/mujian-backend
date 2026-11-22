package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropOperationRecordDO;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropOperationRecordMapper {

  /** 保存幂等操作记录。 */
  void insertPropOperationRecord(@Param("row") PropOperationRecordDO propOperationRecordDO);

  /** 按记录标识读取幂等操作记录。 */
  PropOperationRecordDO selectPropOperationRecord(
      @Param("id") String id, @Param("lock") boolean lock);

  /** 按用户与请求编号读取已登记的幂等结果。 */
  List<PropOperationRecordDO> selectPropOperationRecordListByActorAndRequest(
      @Param("actorId") String actorId, @Param("requestId") String requestId);
}
