package com.mujian.backend.admin.service;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.common.support.SystemIdentity.*;

import com.mujian.backend.account.mapper.*;
import com.mujian.backend.admin.mapper.AdministrationMapper;
import com.mujian.backend.admin.service.CommandAuditService.Command;
import com.mujian.backend.admin.vo.Administration.*;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.config.MenuRegistry;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.coupon.service.OperationsReader;
import com.mujian.backend.coupon.vo.OperationsViews.Issued;
import com.mujian.backend.infrastructure.persistence.mapper.admin.AdminMapper;
import com.mujian.backend.infrastructure.persistence.record.account.UserRecord;
import com.mujian.backend.infrastructure.persistence.record.admin.AdminRecord;
import com.mujian.backend.infrastructure.persistence.record.admin.MenuRecord;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AdministrationCommands {
  private final AdministrationMapper administrationMapper;
  private final AdministrationReader administrationReader;
  private final AdminMapper adminMapper;
  private final AccountMapper accountMapper;
  private final LoginMapper loginMapper;
  private final MenuRegistry menuRegistry;
  private final OperationsReader operationsReader;
  private final CommandAuditService commandAuditService;
  private final SessionService sessionService;
  private final Clock clock;
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(12);

  /** 复用运营数据、当前权限及事务服务。 */
  public AdministrationCommands(
      AdministrationMapper administrationMapper,
      AdministrationReader administrationReader,
      AdminMapper adminMapper,
      AccountMapper accountMapper,
      LoginMapper loginMapper,
      MenuRegistry menuRegistry,
      OperationsReader operationsReader,
      CommandAuditService commandAuditService,
      SessionService sessionService,
      Clock clock) {
    this.administrationMapper = administrationMapper;
    this.administrationReader = administrationReader;
    this.adminMapper = adminMapper;
    this.accountMapper = accountMapper;
    this.loginMapper = loginMapper;
    this.menuRegistry = menuRegistry;
    this.operationsReader = operationsReader;
    this.commandAuditService = commandAuditService;
    this.sessionService = sessionService;
    this.clock = clock;
  }

  /** 阻止使用旧快照覆盖并发修改。 */
  private void snapshot(String expected, String actual) {
    if (!Objects.equals(expected, actual)) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
  }

  /** 独立管理员身份，回执仅保留不含口令的请求摘要。 */
  public Account createAccount(Command command, NewAccount newAccount) {
    String name = newAccount.username().strip().toLowerCase(Locale.ROOT);
    if (!name.matches("[a-z0-9_.-]{3,64}")
        || newAccount.password().getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiFailure.invalid();
    Account result =
        commandAuditService.execute(
            command,
            "admin:account:manage",
            "createAdminAccount",
            "创建管理账号",
            "08",
            "admin_account",
            null,
            Map.of("userId", newAccount.userId(), "username", name, "reason", newAccount.reason()),
            Account.class,
            Account::id,
            () -> {
              loginMapper.selectUserByIdForUpdate(newAccount.userId());
              UserRecord userRecord = accountMapper.selectAccountContextByUserId(newAccount.userId());
              if (userRecord == null) throw missing();
              if (SYSTEM_USER_ID.equals(userRecord.id())
                  || !"active".equals(userRecord.userStatus())
                  || userRecord.roleCode() != null
                  || adminMapper.selectAdminByUsername(name) != null
                  || adminMapper.selectAdminByUserId(userRecord.id()) != null
                  || !loginMapper.selectIdentityIdsByUserForUpdate(userRecord.id()).isEmpty())
                throw conflict(BusinessFailureReason.IDENTITY_CONFLICT.name());
              LocalDateTime at = now(clock);
              String id = Ids.next();
              try {
                accountMapper.insertUserRole(Ids.next(), userRecord.id(), D2Codes.CODE_ADMIN, at);
                adminMapper.insertAdminAccount(
                    id, userRecord.id(), name, passwordEncoder.encode(newAccount.password()), at);
                adminMapper.insertAdminRoleBinding(
                    Ids.next(), id, PLATFORM_ADMIN_ROLE_ID, command.actor(), at);
              } catch (DataIntegrityViolationException e) {
                throw conflict(BusinessFailureReason.IDENTITY_CONFLICT.name());
              }
              return administrationReader.getAccount(id);
            });
    AdminRecord stored = adminMapper.selectAdminByUsername(result.username());
    if (stored == null || !passwordEncoder.matches(newAccount.password(), stored.passwordHash()))
      throw conflict(D2Codes.CODE_IDEMPOTENCY_KEY_REUSED);
    return result;
  }

  /** 账号事实先提交，重试原停用操作仍会清理存量会话。 */
  public Account toggle(Command command, String id, Toggle toggle) {
    Account result =
        commandAuditService.execute(
            command,
            "admin:account:manage",
            "setAdminStatus",
            "变更管理账号状态",
            "08",
            "admin_account",
            id,
            toggle,
            Account.class,
            Account::id,
            before -> {
              administrationMapper.lockAccount(id);
              Account old = administrationReader.getAccount(id);
              before.accept(old);
              snapshot(toggle.snapshotToken(), old.snapshotToken());
              if (toggle.enabled() && !old.enabled()) sessionService.revokeUser(old.userId());
              administrationMapper.updateAdminAccountStatus(id, toggle.enabled(), now(clock));
              return administrationReader.getAccount(id);
            });
    if (!toggle.enabled() && !administrationReader.getAccount(id).enabled())
      sessionService.revokeUser(result.userId());
    return result;
  }

  /** 授权和菜单共用角色根锁，避免同时移动父节点和编辑授权。 */
  public Grants grants(Command command, String id, GrantInput grantInput) {
    TreeSet<String> menuIds = new TreeSet<>(grantInput.menuIds());
    return commandAuditService.execute(
        command,
        "admin:role:manage",
        "setRoleGrants",
        "保存角色授权",
        "08",
        "admin_role",
        id,
        new GrantInput(List.copyOf(menuIds), grantInput.snapshotToken(), grantInput.reason()),
        Grants.class,
        Grants::roleId,
        before -> {
          if (!PLATFORM_ADMIN_ROLE_ID.equals(id) || administrationMapper.lockRole(id) == null)
            throw missing();
          Grants old = administrationReader.grants();
          before.accept(old);
          snapshot(grantInput.snapshotToken(), old.snapshotToken());
          List<MenuRecord> nodes = adminMapper.selectAllAdminMenus();
          Set<String> known = new HashSet<>();
          for (MenuRecord menuRecord : nodes) {
            known.add(menuRecord.id());
          }
          if (!known.containsAll(menuIds)) throw missing();
          administrationMapper.removeGrants(id);
          LocalDateTime at = now(clock);
          for (String menu : menuIds)
            administrationMapper.addGrant(Ids.next(), id, menu, command.actor(), at);
          return administrationReader.grants();
        });
  }

  /** 核对注册菜单节点与授权信息。 */
  public Menu menu(Command command, String id, MenuInput menuInput) {
    return commandAuditService.execute(
        command,
        "admin:menu:manage",
        id == null ? "createMenuNode" : "editMenuNode",
        "维护菜单",
        "08",
        "admin_menu",
        id,
        menuInput,
        Menu.class,
        Menu::id,
        before -> {
          administrationMapper.lockRole(PLATFORM_ADMIN_ROLE_ID);
          String target = id == null ? Ids.next() : id;
          if (id != null) {
            Menu old = administrationReader.menu(id);
            before.accept(old);
            snapshot(menuInput.snapshotToken(), old.snapshotToken());
          }
          boolean page = "menu".equals(menuInput.menuType()),
              button = "button".equals(menuInput.menuType());
          if (page && !menuRegistry.contains(menuInput.routePath(), menuInput.componentPath())
              || !page && menuInput.componentPath() != null
              || button && (menuInput.routePath() != null || menuInput.permissionCode() == null))
            throw conflict(BusinessFailureReason.MENU_CONFLICT.name());
          List<MenuRecord> nodes = adminMapper.selectAllAdminMenus();
          Map<String, String> parents = new HashMap<>();
          for (MenuRecord menuRecord : nodes) {
            parents.put(menuRecord.id(), menuRecord.parentId());
            if (menuRecord.id().equals(target)) continue;
            if ((menuInput.permissionCode() != null
                    && menuInput.permissionCode().equals(menuRecord.permissionCode()))
                || (menuInput.routePath() != null && menuInput.routePath().equals(menuRecord.routePath()))
                || button && target.equals(menuRecord.parentId())
                || menuRecord.id().equals(menuInput.parentId()) && "button".equals(menuRecord.menuType()))
              throw conflict(BusinessFailureReason.MENU_CONFLICT.name());
          }
          if (menuInput.parentId() != null && !parents.containsKey(menuInput.parentId()))
            throw conflict(BusinessFailureReason.MENU_CONFLICT.name());
          for (String p = menuInput.parentId(); p != null; p = parents.get(p))
            if (target.equals(p)) throw conflict(BusinessFailureReason.MENU_CONFLICT.name());
          try {
            if (id == null) administrationMapper.createMenu(target, menuInput, now(clock));
            else administrationMapper.saveMenu(id, menuInput, now(clock));
          } catch (DataIntegrityViolationException e) {
            throw conflict(BusinessFailureReason.MENU_CONFLICT.name());
          }
          return administrationReader.menu(target);
        });
  }

  /** 校验正文与适用身份后保存规则草稿。 */
  public Rule rule(Command command, String id, RuleInput ruleInput) {
    return commandAuditService.execute(
        command,
        "admin:rule:manage",
        id == null ? "createPlatformRule" : "editPlatformRule",
        "保存规则草稿",
        "01",
        "platform_rule_document",
        id,
        ruleInput,
        Rule.class,
        Rule::id,
        before -> {
          boolean url = "url".equals(ruleInput.contentType());
          if (url != (ruleInput.contentUrl() != null) || url == (ruleInput.contentText() != null))
            throw ApiFailure.invalid();
          if (url) {
            try {
              java.net.URI uri = java.net.URI.create(ruleInput.contentUrl());
              if (!Set.of("https", "http").contains(Objects.toString(uri.getScheme(), ""))
                  || uri.getHost() == null) throw ApiFailure.invalid();
            } catch (IllegalArgumentException e) {
              throw ApiFailure.invalid();
            }
          }
          String target = id == null ? Ids.next() : id;
          if (id != null) {
            administrationMapper.lockRule(id);
            Rule old = administrationReader.getRule(id);
            before.accept(old);
            snapshot(ruleInput.snapshotToken(), old.snapshotToken());
            if (!"draft".equals(old.status())) throw conflict(BusinessFailureReason.STATE_NOT_EDITABLE.name());
          }
          try {
            if (id == null) administrationMapper.createRule(target, ruleInput, now(clock));
            else administrationMapper.saveRule(id, ruleInput, now(clock));
          } catch (DataIntegrityViolationException e) {
            throw conflict(D2Codes.CODE_VERSION_CONFLICT);
          }
          return administrationReader.getRule(target);
        });
  }

  /** 使新规则生效并保留该编码的旧版本。 */
  public Rule publish(Command command, String id, Snapshot administrationSnapshot) {
    return commandAuditService.execute(
        command,
        "admin:rule:manage",
        "publishPlatformRule",
        "发布平台规则",
        "01",
        "platform_rule_document",
        id,
        administrationSnapshot,
        Rule.class,
        Rule::id,
        before -> {
          administrationMapper.lockRole(PLATFORM_ADMIN_ROLE_ID);
          administrationMapper.lockRule(id);
          Rule old = administrationReader.getRule(id);
          before.accept(old);
          snapshot(administrationSnapshot.snapshotToken(), old.snapshotToken());
          if (!"draft".equals(old.status())) throw conflict(BusinessFailureReason.STATE_NOT_EDITABLE.name());
          LocalDateTime at = now(clock);
          administrationMapper.archiveRules(old.ruleCode(), at);
          administrationMapper.publishRule(id, at);
          return administrationReader.getRule(id);
        });
  }

  /** 锁定持券并在同一事务记录作废原因和流水。 */
  public Issued invalidate(Command command, String id, Invalidation invalidation) {
    return commandAuditService.execute(
        command,
        "admin:coupon:invalidate",
        "invalidateAvailableCoupon",
        "作废用户券",
        "12",
        "user_coupon",
        id,
        invalidation,
        Issued.class,
        Issued::id,
        before -> {
          before.accept(operationsReader.readIssued(id, true));
          LocalDateTime at = now(clock);
          if (administrationMapper.invalidateUserCoupon(id, invalidation, at) != 1)
            throw conflict(BusinessFailureReason.COUPON_NOT_AVAILABLE.name());
          administrationMapper.logInvalidation(Ids.next(), id, command.actor(), invalidation, at);
          return operationsReader.readIssued(id, false);
        });
  }
}
