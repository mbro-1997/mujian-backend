package com.mujian.backend.review.mapper;

import org.apache.ibatis.annotations.Param;
import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.review.dto.ReviewInput;
import com.mujian.backend.review.projection.ReviewProjection;
import com.mujian.backend.review.projection.ReviewProjection.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ReviewMapper {
  /** 从白名单来源获取订单；提交时先锁业务根。 */
  Order selectOrder(@Param("type") String type, @Param("id") String id, @Param("lock") boolean lock);

  /** 查询会阻止新评价的在途业务。 */
  boolean hasOpenDispute(@Param("type") String type, @Param("id") String id);

  /** 查询本方向历史，即使已隐藏也占用唯一键。 */
  ReviewProjection selectByOrderRole(@Param("type") String type, @Param("id") String id, @Param("role") String role, @Param("actor") Actor actor);

  /** 按会话归属读取可见的单条评价。 */
  ReviewProjection selectVisibleById(@Param("id") String id, @Param("actor") Actor actor);

  /** 计算同一筛选下的真实总数。 */
  long countVisible(@Param("actor") Actor actor, @Param("type") String type, @Param("direction") String direction);

  /** 联读本页相关奖励，避免逐行查询。 */
  List<ReviewProjection> selectVisiblePage(@Param("actor") Actor actor,
      @Param("type") String type,
      @Param("direction") String direction,
      @Param("page") PageQuery page);

  /** 提交本方向评价及对象快照。 */
  void insertReview(@Param("id") String id,
      @Param("input") ReviewInput input,
      @Param("actor") Actor actor,
      @Param("targetId") String targetId,
      @Param("tags") String tags,
      @Param("now") LocalDateTime now);
}
