package com.mujian.backend.admin.mapper;

import com.mujian.backend.admin.vo.Administration.*;
import com.mujian.backend.common.api.PageQuery;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdministrationMapper {
  /** 统计符合筛选条件的后台账号。 */
  long countAccount(@Param("filter") AccountFilter filter, @Param("at") LocalDateTime at);

  /** 分页读取筛选后的后台账号。 */
  List<Account> pageAccount(
      @Param("filter") AccountFilter filter,
      @Param("page") PageQuery page,
      @Param("at") LocalDateTime at);

  /** 按标识读取后台账号。 */
  Account getAccount(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 统计符合筛选条件的平台规则文档。 */
  long countRule(@Param("filter") RuleFilter filter, @Param("at") LocalDateTime at);

  /** 分页读取筛选后的平台规则文档。 */
  List<Rule> pageRule(
      @Param("filter") RuleFilter filter,
      @Param("page") PageQuery page,
      @Param("at") LocalDateTime at);

  /** 按标识读取平台规则文档。 */
  Rule getRule(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 统计符合筛选条件的用户资料。 */
  long countUser(@Param("filter") UserFilter filter, @Param("at") LocalDateTime at);

  /** 分页读取筛选后的用户资料。 */
  List<User> pageUser(
      @Param("filter") UserFilter filter,
      @Param("page") PageQuery page,
      @Param("at") LocalDateTime at);

  /** 按标识读取用户资料。 */
  User getUser(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 统计符合筛选条件的操作审计记录。 */
  long countAudit(@Param("filter") AuditFilter filter, @Param("at") LocalDateTime at);

  /** 分页读取筛选后的操作审计记录。 */
  List<Audit> pageAudit(
      @Param("filter") AuditFilter filter,
      @Param("page") PageQuery page,
      @Param("at") LocalDateTime at);

  /** 按标识读取操作审计记录。 */
  Audit getAudit(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 按标识读取菜单配置。 */
  Menu getMenu(@Param("id") String id);

  /** 列出角色已获授权的菜单标识。 */
  List<String> selectAdminRoleMenuGranted(@Param("roleId") String roleId);

  /** 修改授权前锁定角色。 */
  String lockRole(@Param("id") String id);

  /** 变更后台账号前锁定账号记录。 */
  String lockAccount(@Param("id") String id);

  /** 修改或发布规则前锁定文档。 */
  String lockRule(@Param("id") String id);

  /** 读取用户学生认证的有效截止时间。 */
  String studentUntil(@Param("id") String id);

  /** 列出用户确认过的规则版本。 */
  List<String> ruleVersions(@Param("id") String id);

  /** 读取审计的操作原因及附加信息。 */
  AuditExtra auditExtra(@Param("id") String id);

  /** 按同一统计时点读取仪表盘汇总。 */
  Totals selectDashboardTotals(@Param("at") LocalDateTime at);

  /** 按事项分组汇总待处理数量。 */
  List<CaseGroup> caseGroups(@Param("at") LocalDateTime at);

  /** 按告警原因统计数量。 */
  List<Alert> selectAlertCountsByReason(@Param("at") LocalDateTime at);

  /** 读取需要后台优先关注的事项。 */
  List<Attention> selectAdminCaseAttention(@Param("at") LocalDateTime at);

  /** 按标识读取一次用券记录。 */
  Usage selectCouponUsageById(@Param("id") String id);

  /** 列出该次用券的订单分摊明细。 */
  List<Allocation> selectCouponAllocationsByUsage(@Param("id") String id);

  /** 将指定用户券标记为失效。 */
  int invalidateUserCoupon(
      @Param("id") String id, @Param("input") Invalidation input, @Param("at") LocalDateTime at);

  /** 写入用户券作废流水。 */
  int logInvalidation(
      @Param("logId") String logId,
      @Param("id") String id,
      @Param("actor") String actor,
      @Param("input") Invalidation input,
      @Param("at") LocalDateTime at);

  /** 更新后台账号的启停状态。 */
  int updateAdminAccountStatus(
      @Param("id") String id, @Param("enabled") boolean enabled, @Param("at") LocalDateTime at);

  /** 移除角色的旧菜单授权。 */
  int removeGrants(@Param("roleId") String roleId);

  /** 为角色写入一条菜单授权。 */
  int addGrant(
      @Param("id") String id,
      @Param("roleId") String roleId,
      @Param("menuId") String menuId,
      @Param("actor") String actor,
      @Param("at") LocalDateTime at);

  /** 新增后台菜单配置。 */
  int createMenu(
      @Param("id") String id, @Param("input") MenuInput input, @Param("at") LocalDateTime at);

  /** 更新指定菜单配置。 */
  int saveMenu(
      @Param("id") String id, @Param("input") MenuInput input, @Param("at") LocalDateTime at);

  /** 新增平台规则文档。 */
  int createRule(
      @Param("id") String id, @Param("input") RuleInput input, @Param("at") LocalDateTime at);

  /** 更新平台规则草稿。 */
  int saveRule(
      @Param("id") String id, @Param("input") RuleInput input, @Param("at") LocalDateTime at);

  /** 归档被新版本替换的规则。 */
  int archiveRules(@Param("code") String code, @Param("at") LocalDateTime at);

  /** 将指定版本规则发布生效。 */
  int publishRule(@Param("id") String id, @Param("at") LocalDateTime at);

  /** 逐行写出订单无关的脱敏查询，沿用相同筛选和排序。 */
  void exportUser(
      @Param("filter") UserFilter filter,
      @Param("at") LocalDateTime at,
      org.apache.ibatis.session.ResultHandler<User> handler);

  /** 逐行写出订单无关的脱敏查询，沿用相同筛选和排序。 */
  void exportAudit(
      @Param("filter") AuditFilter filter,
      @Param("at") LocalDateTime at,
      org.apache.ibatis.session.ResultHandler<Audit> handler);
}
