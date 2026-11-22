package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropInstanceStatusLogDO;
import com.mujian.backend.prop.dto.LogQueryDTO;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropInstanceStatusLogMapper {

  /** 保存实物状态流水。 */
  void insertPropInstanceStatusLog(@Param("row") PropInstanceStatusLogDO propInstanceStatusLogDO);

  /** 按已校验条件和分页读取实物状态流水记录。 */
  List<PropInstanceStatusLogDO> selectPropInstanceStatusLogPageByQuery(
      @Param("logQueryDTO") LogQueryDTO logQueryDTO);

  /** 统计匹配条件的实物状态流水数量供分页。 */
  long selectPropInstanceStatusLogCountByQuery(@Param("logQueryDTO") LogQueryDTO logQueryDTO);

  /** 按记录标识读取实物状态流水。 */
  PropInstanceStatusLogDO selectPropInstanceStatusLog(
      @Param("id") String id, @Param("lock") boolean lock);
}
