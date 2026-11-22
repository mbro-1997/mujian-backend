package com.mujian.backend.coupon.mapper;

import org.apache.ibatis.annotations.Param;
import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.coupon.vo.OperationsViews.*;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationsMapper {
  /** 筛选后的Template总数。 */
  long countTemplate(@Param("filter") TemplateFilter filter);

  /** 按筛选与稳定顺序分页。 */
  List<TemplateRow> selectTemplatePage(@Param("filter") TemplateFilter filter, @Param("page") PageQuery page);

  /** 读取固定 ID 的Template详情。 */
  TemplateRow selectTemplateById(@Param("id") String id, @Param("lock") boolean lock);

  /** 筛选后的Activity总数。 */
  long countActivity(@Param("filter") ActivityFilter filter);

  /** 按筛选与稳定顺序分页。 */
  List<Activity> selectActivityPage(@Param("filter") ActivityFilter filter, @Param("page") PageQuery page);

  /** 读取固定 ID 的Activity详情。 */
  Activity selectActivityById(@Param("id") String id, @Param("lock") boolean lock);

  /** 筛选后的Rule总数。 */
  long countRule(@Param("filter") RuleFilter filter);

  /** 按筛选与稳定顺序分页。 */
  List<Rule> selectRulePage(@Param("filter") RuleFilter filter, @Param("page") PageQuery page);

  /** 读取固定 ID 的Rule详情。 */
  Rule selectRuleById(@Param("id") String id, @Param("lock") boolean lock);

  /** 筛选后的Reward总数。 */
  long countReward(@Param("filter") RewardFilter filter);

  /** 按筛选与稳定顺序分页。 */
  List<Reward> selectRewardPage(@Param("filter") RewardFilter filter, @Param("page") PageQuery page);

  /** 筛选后的Distribution总数。 */
  long countDistribution(@Param("filter") DistributionFilter filter);

  /** 按筛选与稳定顺序分页。 */
  List<Distribution> selectDistributionPage(@Param("filter") DistributionFilter filter, @Param("page") PageQuery page);

  /** 读取固定 ID 的Distribution详情。 */
  Distribution selectDistributionById(@Param("id") String id, @Param("lock") boolean lock);

  /** 筛选后的Issued总数。 */
  long countIssued(@Param("filter") IssuedFilter filter);

  /** 按筛选与稳定顺序分页。 */
  List<Issued> selectIssuedPage(@Param("filter") IssuedFilter filter, @Param("page") PageQuery page);

  /** 读取固定 ID 的Issued详情。 */
  Issued selectIssuedById(@Param("id") String id, @Param("lock") boolean lock);

  /** 从原使用记录取得本券全部冻结分摊的查询入口。 */
  List<String> selectUsageIds(@Param("couponId") String couponId);
}
