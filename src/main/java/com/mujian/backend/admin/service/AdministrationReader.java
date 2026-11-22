package com.mujian.backend.admin.service;

import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.common.support.SystemIdentity.*;

import com.mujian.backend.admin.mapper.AdministrationMapper;
import com.mujian.backend.admin.vo.Administration.*;
import com.mujian.backend.common.api.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdministrationReader {
  private final AdministrationMapper administrationMapper;
  private final Clock clock;
  private final ObjectMapper objectMapper;

  /** 复用运营数据、当前权限及事务服务。 */
  public AdministrationReader(
      AdministrationMapper administrationMapper, Clock clock, ObjectMapper objectMapper) {
    this.administrationMapper = administrationMapper;
    this.clock = clock;
    this.objectMapper = objectMapper;
  }

  /** 业务记录缺失时按统一原因拒绝读取。 */
  private static <T> T present(T value) {
    if (value == null) throw missing();
    return value;
  }

  /** 同一事务内按相同条件计数和分页。 */
  public PageResult<Account> listAccount(AccountFilter filter, PageQuery page) {
    LocalDateTime at = now(clock);
    long total = administrationMapper.countAccount(filter, at);
    return PageResult.of(
        total > page.offset() ? administrationMapper.pageAccount(filter, page, at) : List.of(),
        total,
        page);
  }

  /** 核对用户身份与管理员登录账号的对应关系。 */
  public Account getAccount(String id) {
    return present(administrationMapper.getAccount(id, now(clock)));
  }

  /** 同一事务内按相同条件计数和分页。 */
  public PageResult<Rule> listRule(RuleFilter filter, PageQuery page) {
    LocalDateTime at = now(clock);
    long total = administrationMapper.countRule(filter, at);
    return PageResult.of(
        total > page.offset() ? administrationMapper.pageRule(filter, page, at) : List.of(),
        total,
        page);
  }

  /** 读取指定版本，供草稿编辑或核对原文。 */
  public Rule getRule(String id) {
    return present(administrationMapper.getRule(id, now(clock)));
  }

  /** 同一事务内按相同条件计数和分页。 */
  public PageResult<User> listUser(UserFilter filter, PageQuery page) {
    LocalDateTime at = now(clock);
    long total = administrationMapper.countUser(filter, at);
    return PageResult.of(
        total > page.offset() ? administrationMapper.pageUser(filter, page, at) : List.of(),
        total,
        page);
  }

  /** 读取所选用户的脱敏信息及身份状态。 */
  public User getUser(String id) {
    return present(administrationMapper.getUser(id, now(clock)));
  }

  /** 同一事务内按相同条件计数和分页。 */
  public PageResult<Audit> listAudit(AuditFilter filter, PageQuery page) {
    LocalDateTime at = now(clock);
    long total = administrationMapper.countAudit(filter, at);
    return PageResult.of(
        total > page.offset() ? administrationMapper.pageAudit(filter, page, at) : List.of(),
        total,
        page);
  }

  /** 读取单次操作的审计事实。 */
  public Audit getAudit(String id) {
    return present(administrationMapper.getAudit(id, now(clock)));
  }

  /** 核对注册菜单节点与授权信息。 */
  public Menu menu(String id) {
    return present(administrationMapper.getMenu(id));
  }

  /** 在内置角色范围核对授权节点与快照。 */
  public Grants grants() {
    List<String> ids = administrationMapper.selectAdminRoleMenuGranted(PLATFORM_ADMIN_ROLE_ID);
    String token;
    try {
      token =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          (PLATFORM_ADMIN_ROLE_ID + String.join("", ids))
                              .getBytes(StandardCharsets.US_ASCII)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
    return new Grants(PLATFORM_ADMIN_ROLE_ID, "platform_admin", ids, token);
  }

  /** 关联学生有效期及已同意的历史规则版本。 */
  public UserDetail userDetail(String id) {
    User user = getUser(id);
    return new UserDetail(
        user.userId(),
        user.nickname(),
        user.phoneMasked(),
        user.userStatus(),
        user.registrationStatus(),
        user.businessRole(),
        user.realnameStatus(),
        user.studentStatus(),
        user.createdAt(),
        administrationMapper.studentUntil(id),
        administrationMapper.ruleVersions(id));
  }

  /** 只投影审计快照中允许公开的标量变更，不返回回执、原始身份或材料。 */
  public AuditDetail auditDetail(String id) {
    Audit audit = getAudit(id);
    AuditExtra auditExtra = administrationMapper.auditExtra(id);
    List<Change> changes = new ArrayList<>();
    if (auditExtra != null) {
      JsonNode before =
          auditExtra.beforeJson() == null
              ? objectMapper.createObjectNode()
              : objectMapper.readTree(auditExtra.beforeJson());
      JsonNode after =
          auditExtra.afterJson() == null
              ? objectMapper.createObjectNode()
              : objectMapper.readTree(auditExtra.afterJson());
      if (after.has("result")) after = after.path("result");
      for (String field :
          List.of(
              "enabled",
              "status",
              "caseStatus",
              "couponStatus",
              "ruleVersion",
              "version",
              "templateVersion",
              "issuedCount",
              "verificationStatus",
              "sortNo",
              "visible",
              "menuName",
              "ruleTitle",
              "menuIds")) {
        if ("menuIds".equals(field)) {
          Set<String> prior = new TreeSet<String>();
          Set<String> next = new TreeSet<String>();
          for (JsonNode idValue : before.path(field)) prior.add(idValue.asString());
          for (JsonNode idValue : after.path(field)) next.add(idValue.asString());
          for (String nodeId : prior)
            if (!next.contains(nodeId)) changes.add(new Change("menuId", nodeId, null));
          for (String nodeId : next)
            if (!prior.contains(nodeId)) changes.add(new Change("menuId", null, nodeId));
          continue;
        }
        String b = value(before, field), c = value(after, field);
        if (!Objects.equals(b, c)) changes.add(new Change(field, b, c));
      }
    }
    return new AuditDetail(
        audit.id(),
        audit.adminUserId(),
        audit.operationCode(),
        audit.permissionCode(),
        audit.targetType(),
        audit.targetId(),
        audit.caseId(),
        audit.resultStatus(),
        audit.traceId(),
        audit.createdAt(),
        auditExtra == null ? null : auditExtra.reason(),
        auditExtra == null ? null : auditExtra.resultMessage(),
        changes);
  }

  /** 仅提取授权展示的审计字段。 */
  private String value(JsonNode node, String field) {
    JsonNode v = node.path(field);
    return v.isString() || v.isBoolean() || v.isNumber() ? v.asString() : null;
  }

  /** 按同一业务时点统计待办、渠道预警及案件。 */
  public Dashboard dashboard() {
    LocalDateTime at = now(clock);
    Totals administrationTotals = administrationMapper.selectDashboardTotals(at);
    return new Dashboard(
        time(at),
        administrationTotals.pending(),
        administrationTotals.waitingUser(),
        administrationTotals.waitingFactory(),
        administrationTotals.dueToday(),
        administrationMapper.caseGroups(at),
        administrationMapper.selectAlertCountsByReason(at),
        administrationMapper.selectAdminCaseAttention(at));
  }

  /** 按券使用记录读取结算时冻结的逐项优惠。 */
  public AllocationAudit allocations(String id) {
    Usage usage = present(administrationMapper.selectCouponUsageById(id));
    return new AllocationAudit(
        usage.usageId(),
        usage.userCouponId(),
        usage.checkoutBatchId(),
        administrationMapper.selectCouponAllocationsByUsage(id),
        usage.paymentId());
  }

  /** 按原筛选条件流式导出脱敏用户信息。 */
  public void exportUser(UserFilter userFilter, java.util.function.Consumer<User> sink) {
    administrationMapper.exportUser(
        userFilter, now(clock), context -> sink.accept(context.getResultObject()));
  }

  /** 将筛选后的审计行流式写入导出内容。 */
  public void exportAudit(AuditFilter auditFilter, java.util.function.Consumer<Audit> sink) {
    administrationMapper.exportAudit(
        auditFilter, now(clock), context -> sink.accept(context.getResultObject()));
  }
}
