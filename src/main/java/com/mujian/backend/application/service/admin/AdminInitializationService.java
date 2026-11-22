package com.mujian.backend.application.service.admin;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Codes.*;

import com.mujian.backend.account.mapper.AccountMapper;
import com.mujian.backend.common.support.*;
import com.mujian.backend.infrastructure.persistence.mapper.admin.AdminMapper;
import com.mujian.backend.infrastructure.persistence.record.admin.AdminRecord;
import com.mujian.backend.infrastructure.persistence.record.admin.RoleRecord;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminInitializationService {
  private final AdminMapper adminMapper;
  private final AccountMapper accountMapper;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public AdminInitializationService(AdminMapper adminMapper, AccountMapper accountMapper) {
    this.adminMapper = adminMapper;
    this.accountMapper = accountMapper;
  }

  /** 幂等初始化管理员及角色关系，拒绝覆盖不完整账号。 */
  @Transactional
  public String initialize(String input, String password) {
    String username = input.trim().toLowerCase(Locale.ROOT);
    if (!username.matches("[a-z0-9_.-]{3,64}")
        || password.length() < 12
        || password.getBytes(StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException("用户名或初始密码格式不符合 C-03");
    if (accountMapper.countInvalidSystemIdentity(SystemIdentity.SYSTEM_USER_ID) != 0)
      throw new IllegalStateException("系统身份不合法");
    AdminRecord adminRecord = adminMapper.selectAdminByUsername(username);
    if (adminRecord != null) {
      if (!adminRecord.enabled()
          || !CODE_ACTIVE.equals(adminRecord.userStatus())
          || !D2Codes.CODE_ADMIN.equals(adminRecord.roleCode()))
        throw new IllegalStateException("已有账号关系不完整，停止且不修改");
      boolean platformAdmin = false;
      for (RoleRecord roleRecord : adminMapper.selectAdminRolesByAccountId(adminRecord.id())) {
        if (roleRecord.id().equals(SystemIdentity.PLATFORM_ADMIN_ROLE_ID)) {
          platformAdmin = true;
          break;
        }
      }
      if (!platformAdmin) throw new IllegalStateException("已有账号关系不完整，停止且不修改");
      return "已存在；没有修改密码或关系";
    }
    LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
    String userId = Ids.next();
    String accountId = Ids.next();
    accountMapper.insertAdminUser(userId, username, now);
    accountMapper.insertUserRole(Ids.next(), userId, D2Codes.CODE_ADMIN, now);
    adminMapper.insertAdminAccount(
        accountId, userId, username, new BCryptPasswordEncoder(12).encode(password), now);
    adminMapper.insertAdminRoleBinding(
        Ids.next(),
        accountId,
        SystemIdentity.PLATFORM_ADMIN_ROLE_ID,
        SystemIdentity.SYSTEM_USER_ID,
        now);
    return "管理员已初始化";
  }
}
