package com.mujian.backend.coupon.mapper;

import org.apache.ibatis.annotations.Param;
import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.coupon.projection.DistributionRows.*;
import com.mujian.backend.coupon.vo.ActivityViews.Claim;
import java.time.LocalDateTime;
import java.util.*;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DistributionMapper {
  /** 一次读取学生和实名联合资格。 */
  boolean hasStudentBenefit(@Param("user") String user, @Param("at") LocalDateTime at);

  /** 开放、可发且尚未领取的活动总数。 */
  long countOffers(@Param("user") String user, @Param("student") boolean student, @Param("at") LocalDateTime at);

  /** 先过滤再分页，按结束时刻及 ID 稳定排序。 */
  List<OfferRow> selectOffers(@Param("user") String user, @Param("student") boolean student, @Param("at") LocalDateTime at, @Param("page") PageQuery page);

  /** 一批查询适用工厂名称。 */
  List<Scope> selectScopes(@Param("ids") Set<String> ids);

  /** 读取原券当前状态。 */
  Claim selectClaim(@Param("key") String key, @Param("user") String user, @Param("at") LocalDateTime at);

  /** 领取时先锁活动再锁模板。 */
  Activity lockActivity(@Param("id") String id);

  /** 获取不可变的模板快照及当前发放状态。 */
  Template lockTemplate(@Param("id") String id);

  /** 同事务分段写入独立用户券。 */
  int insertCoupons(@Param("coupons") List<CouponWrite> coupons,
      @Param("template") Template template,
      @Param("source") String source,
      @Param("sourceId") String sourceId,
      @Param("at") LocalDateTime at);

  /** 对每张券落一次来源流水。 */
  int insertIssuedLogs(@Param("coupons") List<CouponWrite> coupons,
      @Param("operatorType") String operatorType,
      @Param("operator") String operator,
      @Param("bizType") String bizType,
      @Param("sourceId") String sourceId,
      @Param("reason") String reason,
      @Param("at") LocalDateTime at);

  /** 活动成功增加实际领取数。 */
  void incrementActivity(@Param("id") String id);

  /** 模板累计只增一次。 */
  void incrementTemplate(@Param("id") String id, @Param("count") long count, @Param("at") LocalDateTime at);
}
