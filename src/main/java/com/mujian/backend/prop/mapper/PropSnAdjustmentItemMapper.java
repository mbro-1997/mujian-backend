package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.domain.PropSnAdjustmentItemDO;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropSnAdjustmentItemMapper {

  /** 保存实物调整明细。 */
  void insertPropSnAdjustmentItem(@Param("row") PropSnAdjustmentItemDO propSnAdjustmentItemDO);

  /** 按记录标识读取实物调整明细。 */
  PropSnAdjustmentItemDO selectPropSnAdjustmentItem(
      @Param("id") String id, @Param("lock") boolean lock);

  /** 读取申请涉及的实物调整明细。 */
  List<PropSnAdjustmentItemDO> selectSnAdjustmentItemListByAuditId(
      @Param("adjustmentId") String adjustmentId);

  /** 读取审核页面需要的实物明细。 */
  List<PropSnAdjustmentItemDO> selectReviewSnAdjustmentItemListByAuditId(
      @Param("adjustmentId") String adjustmentId);

  /** 按批准流程需要的顺序读取实物调整明细。 */
  List<PropSnAdjustmentItemDO> selectApprovalSnAdjustmentItemListByAuditId(
      @Param("adjustmentId") String adjustmentId);

  /** 读取已经审核的历史实物明细。 */
  List<PropSnAdjustmentItemDO> selectReviewedSnAdjustmentItemListByAuditId(
      @Param("adjustmentId") String adjustmentId);
}
