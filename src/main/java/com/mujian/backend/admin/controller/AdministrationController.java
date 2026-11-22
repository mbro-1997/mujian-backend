package com.mujian.backend.admin.controller;

import com.mujian.backend.admin.service.*;
import com.mujian.backend.admin.service.CommandAuditService.Command;
import com.mujian.backend.admin.vo.Administration.*;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.coupon.vo.OperationsViews.Issued;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.service.TradingHttp;
import jakarta.servlet.http.*;
import java.io.*;
import java.time.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdministrationController extends BaseController {
  private final AdministrationReader administrationReader;
  private final AdministrationCommands administrationCommands;
  private final TradingHttp tradingHttp;
  private final VerificationAccess verificationAccess;

  /** 复用运营数据、当前权限及事务服务。 */
  public AdministrationController(
      AdministrationReader administrationReader,
      AdministrationCommands administrationCommands,
      TradingHttp tradingHttp,
      VerificationAccess verificationAccess) {
    this.administrationReader = administrationReader;
    this.administrationCommands = administrationCommands;
    this.tradingHttp = tradingHttp;
    this.verificationAccess = verificationAccess;
  }

  /** 按当前管理员的实时权限校验读取入口。 */
  private void read(HttpServletRequest r, String permission) {
    verificationAccess.readPermissions(tradingHttp.actor(r), permission);
  }

  /** 从当前会话和请求提取命令审计身份与幂等键。 */
  private Command cmd(HttpServletRequest r) {
    return new Command(
        tradingHttp.actor(r), r.getHeader("Idempotency-Key"), (String) r.getAttribute("traceId"));
  }

  /** 将查询时间统一换算为业务时区。 */
  private LocalDateTime local(OffsetDateTime t) {
    return t == null
        ? null
        : t.atZoneSameInstant(com.mujian.backend.common.support.D2Values.ZONE).toLocalDateTime();
  }

  /** 按后台身份状态和搜索条件分页读取管理员。 */
  @GetMapping("/system/accounts")
  public ApiResponse<PageResult<Account>> listAccount(
      HttpServletRequest r,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    read(r, "admin:account:manage");
    return success(
        r,
        administrationReader.listAccount(
            new AccountFilter(enabled, keyword), PageQuery.of(page, pageSize)));
  }

  /** 核对用户身份与管理员登录账号的对应关系。 */
  @GetMapping("/system/accounts/{id}")
  public ApiResponse<Account> getAccount(HttpServletRequest r, @PathVariable String id) {
    read(r, "admin:account:manage");
    return success(r, administrationReader.getAccount(id));
  }

  /** 按编码与发布状态分页读取规则版本。 */
  @GetMapping("/platform-rules")
  public ApiResponse<PageResult<Rule>> listRule(
      HttpServletRequest r,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String ruleCode,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    read(r, "admin:rule:read");
    return success(
        r,
        administrationReader.listRule(
            new RuleFilter(status, ruleCode), PageQuery.of(page, pageSize)));
  }

  /** 读取指定版本，供草稿编辑或核对原文。 */
  @GetMapping("/platform-rules/{id}")
  public ApiResponse<Rule> getRule(HttpServletRequest r, @PathVariable String id) {
    read(r, "admin:rule:read");
    return success(r, administrationReader.getRule(id));
  }

  /** 在授权范围读取脱敏用户列表。 */
  @GetMapping("/users")
  public ApiResponse<PageResult<User>> listUser(
      HttpServletRequest r,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String studentStatus,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    read(r, "admin:user:read");
    return success(
        r,
        administrationReader.listUser(
            new UserFilter(status, keyword, studentStatus), PageQuery.of(page, pageSize)));
  }

  /** 读取所选用户的脱敏信息及身份状态。 */
  @GetMapping("/users/{id}")
  public ApiResponse<UserDetail> getUser(HttpServletRequest r, @PathVariable String id) {
    read(r, "admin:user:read");
    return success(r, administrationReader.userDetail(id));
  }

  /** 按操作者、时间和对象范围检索审计。 */
  @GetMapping("/system/audits")
  public ApiResponse<PageResult<Audit>> listAudit(
      HttpServletRequest r,
      @RequestParam(required = false) String adminUserId,
      @RequestParam(required = false) String targetModule,
      @RequestParam(required = false) String resultStatus,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime until,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    read(r, "admin:audit:view");
    return success(
        r,
        administrationReader.listAudit(
            new AuditFilter(adminUserId, targetModule, resultStatus, local(from), local(until)),
            PageQuery.of(page, pageSize)));
  }

  /** 读取单次操作的审计事实。 */
  @GetMapping("/system/audits/{id}")
  public ApiResponse<AuditDetail> getAudit(HttpServletRequest r, @PathVariable String id) {
    read(r, "admin:audit:view");
    return success(r, administrationReader.auditDetail(id));
  }

  /** 按同一业务时点统计待办、渠道预警及案件。 */
  @GetMapping("/dashboard")
  public ApiResponse<Dashboard> dashboard(HttpServletRequest r) {
    read(r, "admin:dashboard:view");
    return success(r, administrationReader.dashboard());
  }

  /** 在内置角色范围核对授权节点与快照。 */
  @GetMapping("/system/role-grants")
  public ApiResponse<Grants> grants(HttpServletRequest r) {
    read(r, "admin:role:manage");
    return success(r, administrationReader.grants());
  }

  /** 核对注册菜单节点与授权信息。 */
  @GetMapping("/system/menus/{id}")
  public ApiResponse<Menu> menu(HttpServletRequest r, @PathVariable String id) {
    read(r, "admin:menu:manage");
    return success(r, administrationReader.menu(id));
  }

  /** 按券使用记录读取结算时冻结的逐项优惠。 */
  @GetMapping("/coupon-usages/{id}/allocations")
  public ApiResponse<AllocationAudit> allocations(HttpServletRequest r, @PathVariable String id) {
    read(r, "admin:coupon:read");
    return success(r, administrationReader.allocations(id));
  }

  /** 对已有业务用户开通后台账号并留存审计。 */
  @PostMapping("/system/accounts")
  public ApiResponse<Account> createAccount(HttpServletRequest r, @RequestBody NewAccount input) {
    return success(r, administrationCommands.createAccount(cmd(r), input));
  }

  /** 校验旧快照后调整账号状态及会话。 */
  @PostMapping("/system/accounts/{id}/status")
  public ApiResponse<Account> toggle(
      HttpServletRequest r, @PathVariable String id, @RequestBody Toggle input) {
    return success(r, administrationCommands.toggle(cmd(r), id, input));
  }

  /** 在内置角色范围核对授权节点与快照。 */
  @PostMapping("/system/role-grants/{id}/save")
  public ApiResponse<Grants> grants(
      HttpServletRequest r, @PathVariable String id, @RequestBody GrantInput input) {
    return success(r, administrationCommands.grants(cmd(r), id, input));
  }

  /** 建立受注册表白名单约束的菜单节点。 */
  @PostMapping("/system/menus")
  public ApiResponse<Menu> menuDraft(HttpServletRequest r, @RequestBody MenuInput input) {
    return success(r, administrationCommands.menu(cmd(r), null, input));
  }

  /** 核对注册菜单节点与授权信息。 */
  @PostMapping("/system/menus/{id}/save")
  public ApiResponse<Menu> menu(
      HttpServletRequest r, @PathVariable String id, @RequestBody MenuInput input) {
    return success(r, administrationCommands.menu(cmd(r), id, input));
  }

  /** 登记可继续编辑的平台规则草稿。 */
  @PostMapping("/platform-rules")
  public ApiResponse<Rule> ruleDraft(HttpServletRequest r, @RequestBody RuleInput input) {
    return success(r, administrationCommands.rule(cmd(r), null, input));
  }

  /** 校验正文与适用身份后保存规则草稿。 */
  @PostMapping("/platform-rules/{id}/save")
  public ApiResponse<Rule> rule(
      HttpServletRequest r, @PathVariable String id, @RequestBody RuleInput input) {
    return success(r, administrationCommands.rule(cmd(r), id, input));
  }

  /** 使新规则生效并保留该编码的旧版本。 */
  @PostMapping("/platform-rules/{id}/publish")
  public ApiResponse<Rule> publish(
      HttpServletRequest r, @PathVariable String id, @RequestBody Snapshot input) {
    return success(r, administrationCommands.publish(cmd(r), id, input));
  }

  /** 锁定持券并在同一事务记录作废原因和流水。 */
  @PostMapping("/user-coupons/{id}/invalidate")
  public ApiResponse<Issued> invalidate(
      HttpServletRequest r, @PathVariable String id, @RequestBody Invalidation input) {
    return success(r, administrationCommands.invalidate(cmd(r), id, input));
  }

  /** 导出沿用列表字段白名单，并防止表格程序解释外部输入为公式。 */
  private PrintWriter csv(HttpServletResponse response, String name) throws IOException {
    response.setContentType("text/csv;charset=UTF-8");
    response.setHeader("Content-Disposition", "attachment; filename=" + name + ".csv");
    PrintWriter out = response.getWriter();
    out.print('\ufeff');
    return out;
  }

  /** 按 CSV 转义要求写入一行已脱敏的数据。 */
  private void line(PrintWriter out, String... values) {
    for (int i = 0; i < values.length; i++) {
      if (i > 0) out.print(',');
      String v = values[i] == null ? "" : values[i];
      if (v.stripLeading().matches("(?s)^[=+@\\-].*") || v.startsWith("\t") || v.startsWith("\r"))
        v = "'" + v;
      out.print('"');
      out.print(v.replace("\"", "\"\""));
      out.print('"');
    }
    out.print("\r\n");
  }

  /** 按当前筛选条件导出用户清单。 */
  @GetMapping("/users/export")
  @org.springframework.transaction.annotation.Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  /** 按原筛选条件流式导出脱敏用户信息。 */
  public void exportUser(
      HttpServletRequest r,
      HttpServletResponse response,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String studentStatus)
      throws IOException {
    read(r, "admin:user:read");
    read(r, "admin:user:export");
    UserFilter userFilter = new UserFilter(status, keyword, studentStatus);
    PrintWriter out = csv(response, "users");
    line(
        out,
        "userId",
        "nickname",
        "phoneMasked",
        "userStatus",
        "registrationStatus",
        "businessRole",
        "realnameStatus",
        "studentStatus",
        "createdAt");
    administrationReader.exportUser(
        userFilter,
        row ->
            line(
                out,
                row.userId(),
                row.nickname(),
                row.phoneMasked(),
                row.userStatus(),
                row.registrationStatus(),
                row.businessRole(),
                row.realnameStatus(),
                row.studentStatus(),
                row.createdAt()));
    out.flush();
  }

  /** 导出匹配条件的后台操作审计。 */
  @GetMapping("/system/audits/export")
  @org.springframework.transaction.annotation.Transactional(
      readOnly = true,
      isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
  /** 将筛选后的审计行流式写入导出内容。 */
  public void exportAudit(
      HttpServletRequest r,
      HttpServletResponse response,
      @RequestParam(required = false) String adminUserId,
      @RequestParam(required = false) String targetModule,
      @RequestParam(required = false) String resultStatus,
      @RequestParam(required = false) OffsetDateTime from,
      @RequestParam(required = false) OffsetDateTime until)
      throws IOException {
    read(r, "admin:audit:view");
    read(r, "admin:audit:export");
    AuditFilter auditFilter =
        new AuditFilter(adminUserId, targetModule, resultStatus, local(from), local(until));
    PrintWriter out = csv(response, "audits");
    line(
        out,
        "id",
        "adminUserId",
        "operationCode",
        "permissionCode",
        "targetType",
        "targetId",
        "caseId",
        "resultStatus",
        "traceId",
        "createdAt");
    administrationReader.exportAudit(
        auditFilter,
        row ->
            line(
                out,
                row.id(),
                row.adminUserId(),
                row.operationCode(),
                row.permissionCode(),
                row.targetType(),
                row.targetId(),
                row.caseId(),
                row.resultStatus(),
                row.traceId(),
                row.createdAt()));
    out.flush();
  }
}
