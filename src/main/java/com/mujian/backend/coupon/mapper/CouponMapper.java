package com.mujian.backend.coupon.mapper;

import com.mujian.backend.coupon.projection.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface CouponMapper {
  /** 按本人及可用分类分页，不在内存裁剪权限。 */
  List<CouponProjection> selectCouponsByQuery(
      @Param("actor") String actor,
      @Param("status") String status,
      @Param("now") LocalDateTime now,
      @Param("limit") int limit,
      @Param("offset") long offset);

  record Counts(long available, long unavailable) {}

  /** 同一时点按本人范围统计两个互补分类。 */
  Counts countByAvailability(@Param("actor") String actor, @Param("now") LocalDateTime now);

  /** 报价读取本人全部券并据结算判断可用原因。 */
  List<CouponProjection> selectCandidateCouponsByUser(@Param("actor") String actor);

  /** 按本人读取券及必要根锁。 */
  CouponProjection selectCouponByUserAndId(
      @Param("actor") String actor, @Param("id") String id, @Param("lock") boolean lock);

  /** 批量取模板工厂范围。 */
  List<CouponScopeProjection> selectScopesByTemplateIds(@Param("ids") List<String> ids);

  /** 分页读取已核销或已返还的使用记录。 */
  List<CouponUseProjection> selectUsesByUser(
      @Param("actor") String actor, @Param("limit") int limit, @Param("offset") long offset);

  /** 本人有效使用历史总数。 */
  long countUsesByUser(@Param("actor") String actor);
}
