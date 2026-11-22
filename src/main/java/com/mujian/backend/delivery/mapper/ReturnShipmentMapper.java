package com.mujian.backend.delivery.mapper;

import com.mujian.backend.delivery.domain.OrderReturnShipmentDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ReturnShipmentMapper {
  /** 合法写入时按订单唯一插入草稿。 */
  void insertDraft(@Param("row") OrderReturnShipmentDO row);

  /** 写草稿或发出事实，已发出不可覆写。 */
  int updateOrderReturnShipment(@Param("row") OrderReturnShipmentDO row, @Param("version") int version);
}
