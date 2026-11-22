package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropSnAdjustmentAuditDO;
import com.mujian.backend.prop.dto.PropWriteDTO;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropSnAdjustmentAuditMapper {

  /** 保存实物调整申请的审核决定和版本。 */
  int updateSnAdjustmentDecision(@Param("command") PropWriteDTO.DecideReview decideReview);

  /** 保存实物调整申请的撤回状态和版本。 */
  int updateSnAdjustmentCancellation(@Param("command") PropWriteDTO.CancelReview cancelReview);

  /** 保存实物调整申请。 */
  void insertPropSnAdjustmentAudit(@Param("row") PropSnAdjustmentAuditDO propSnAdjustmentAuditDO);

  /** 读取尚待补齐工单关联的实物调整申请。 */
  List<PropSnAdjustmentAuditDO> selectUnlinkedPendingPropSnAdjustmentAuditList();

  /** 按用户与请求编号恢复实物调整申请。 */
  List<PropSnAdjustmentAuditDO> selectPropSnAdjustmentAuditListByActorAndRequest(
      @Param("actorId") String actorId, @Param("requestId") String requestId);

  /** 按记录标识读取实物调整申请。 */
  PropSnAdjustmentAuditDO selectPropSnAdjustmentAudit(
      @Param("id") String id, @Param("lock") boolean lock);

  /** 查找实物尚未处理的调整申请。 */
  List<PropSnAdjustmentAuditDO> selectPendingSnAdjustmentListBySnId(@Param("snId") String snId);

  /** 批量读取当前页实物的待审申请。 */
  List<com.mujian.backend.prop.projection.PropSnPendingProjection>
      selectPendingSnAdjustmentListBySnIds(@Param("snIds") List<String> snIds);
}
