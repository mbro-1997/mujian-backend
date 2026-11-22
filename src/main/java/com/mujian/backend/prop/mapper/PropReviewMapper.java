package com.mujian.backend.prop.mapper;

import com.mujian.backend.prop.dto.ReviewQueryDTO;
import com.mujian.backend.prop.projection.PropApplicantProjection;
import com.mujian.backend.prop.projection.PropReviewCountProjection;
import com.mujian.backend.prop.projection.PropReviewListProjection;
import java.time.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PropReviewMapper {

  /** 关闭已撤回来源对应的审核工单。 */
  int updatePropCaseForCancelledSource(
      @Param("operatorId") String operatorId,
      @Param("id") String id,
      @Param("reason") String reason,
      @Param("at") LocalDateTime at,
      @Param("facts") String facts);

  /** 读取申请人展示名称，供审核详情使用。 */
  List<PropApplicantProjection> selectPropApplicantNameByUserId(@Param("id") String id);

  /** 统计道具与库存各类待审申请数量。 */
  List<PropReviewCountProjection> selectPropReviewPendingCountByQuery(
      @Param("reviewQueryDTO") ReviewQueryDTO reviewQueryDTO);

  /** 统计匹配条件的道具与库存申请数量。 */
  long selectPropReviewCountByQuery(@Param("reviewQueryDTO") ReviewQueryDTO reviewQueryDTO);

  /** 按筛选和分页读取道具与库存申请列表。 */
  List<PropReviewListProjection> selectPropReviewPageByQuery(
      @Param("reviewQueryDTO") ReviewQueryDTO reviewQueryDTO);
}
