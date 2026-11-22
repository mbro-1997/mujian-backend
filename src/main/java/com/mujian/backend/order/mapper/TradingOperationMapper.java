package com.mujian.backend.order.mapper;

import com.mujian.backend.order.domain.TradingOperationDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TradingOperationMapper {
  /** 只读取本人请求，避免跨账号请求号泄漏。 */
  TradingOperationDO selectOperationByActorAndRequest(
      @Param("actor") String actor, @Param("requestId") String requestId);

  /** 按 actor, operation, digest 查询 trading_operation_record 记录。 */
  String selectEvidenceBinding(
      @Param("actor") String actor,
      @Param("operation") String operation,
      @Param("digest") String digest);

  /** 与本地业务变化在同一事务保存完成回执。 */
  void insertTradingOperation(@Param("row") TradingOperationDO tradingOperationDO);
}
