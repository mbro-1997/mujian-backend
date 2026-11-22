package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropStockMovementDO;
import com.mujian.backend.prop.dto.MovementQueryDTO;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropStockMovementMapper {

  /** 保存库存变更流水。 */
  void insertPropStockMovement(@Param("row") PropStockMovementDO propStockMovementDO);

  /** 按已校验条件和分页读取库存流水记录。 */
  List<PropStockMovementDO> selectPropStockMovementPageByQuery(
      @Param("movementQueryDTO") MovementQueryDTO movementQueryDTO);

  /** 统计匹配条件的库存流水数量供分页。 */
  long selectPropStockMovementCountByQuery(@Param("movementQueryDTO") MovementQueryDTO movementQueryDTO);

  /** 按记录标识读取库存变更流水。 */
  PropStockMovementDO selectPropStockMovement(@Param("id") String id, @Param("lock") boolean lock);
}
