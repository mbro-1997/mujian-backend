package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropSkuAuditDO;
import com.mujian.backend.prop.dto.PropWriteDTO;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropSkuAuditMapper {

  /** 保存道具申请的审核决定和版本。 */
  int updateSkuAuditDecision(@Param("command") PropWriteDTO.DecideReview decideReview);

  /** 保存道具申请的撤回状态和版本。 */
  int updateSkuAuditCancellation(@Param("command") PropWriteDTO.CancelReview cancelReview);

  /** 保存道具审核申请。 */
  void insertPropSkuAudit(@Param("row") PropSkuAuditDO propSkuAuditDO);

  /** 读取尚待补齐工单关联的道具审核申请。 */
  List<PropSkuAuditDO> selectUnlinkedPendingPropSkuAuditList();

  /** 按用户与请求编号恢复道具审核申请。 */
  List<PropSkuAuditDO> selectPropSkuAuditListByActorAndRequest(
      @Param("actorId") String actorId, @Param("requestId") String requestId);

  /** 按记录标识读取道具审核申请。 */
  PropSkuAuditDO selectPropSkuAudit(@Param("id") String id, @Param("lock") boolean lock);

  /** 查找道具尚未处理的审核申请。 */
  List<PropSkuAuditDO> selectPendingPropSkuAuditListBySkuId(@Param("skuId") String skuId);

  /** 读取道具最近一次审核申请以限制旧申请重提。 */
  List<PropSkuAuditDO> selectLatestPropSkuAuditListBySkuId(@Param("skuId") String skuId);

  /** 批量读取当前页道具的待审申请，避免每条道具回查。 */
  List<PropSkuAuditDO> selectPendingPropSkuAuditListBySkuIds(@Param("skuIds") List<String> skuIds);
}
