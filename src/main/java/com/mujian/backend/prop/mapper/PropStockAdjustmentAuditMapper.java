package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropStockAdjustmentAuditDO;
import com.mujian.backend.prop.dto.PropWriteDTO;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropStockAdjustmentAuditMapper {

  /** 保存库存调整申请的审核决定和版本。 */
  int updateStockAdjustmentDecision(@Param("command") PropWriteDTO.DecideReview decideReview);

  /** 保存库存调整申请的撤回状态和版本。 */
  int updateStockAdjustmentCancellation(@Param("command") PropWriteDTO.CancelReview cancelReview);

  /** 把库存审核申请关联到实际变更流水。 */
  int updateStockAdjustmentMovement(
      @Param("command") PropWriteDTO.LinkStockMovement linkStockMovement);

  /** 保存库存调整申请。 */
  void insertPropStockAdjustmentAudit(
      @Param("row") PropStockAdjustmentAuditDO propStockAdjustmentAuditDO);

  /** 读取尚待补齐工单关联的库存调整申请。 */
  List<PropStockAdjustmentAuditDO> selectUnlinkedPendingPropStockAdjustmentAuditList();

  /** 按用户与请求编号恢复库存调整申请。 */
  List<PropStockAdjustmentAuditDO> selectPropStockAdjustmentAuditListByActorAndRequest(
      @Param("actorId") String actorId, @Param("requestId") String requestId);

  /** 按记录标识读取库存调整申请。 */
  PropStockAdjustmentAuditDO selectPropStockAdjustmentAudit(
      @Param("id") String id, @Param("lock") boolean lock);
}
