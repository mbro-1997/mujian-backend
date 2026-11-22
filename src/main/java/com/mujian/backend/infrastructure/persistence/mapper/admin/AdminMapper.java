package com.mujian.backend.infrastructure.persistence.mapper.admin;

import com.mujian.backend.infrastructure.persistence.record.admin.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AdminMapper {
  /** 按 username 查询 admin_account 记录。 */
  AdminRecord selectAdminByUsername(@Param("username") String username);

  /** 按 userId 查询 admin_account 记录。 */
  AdminRecord selectAdminByUserId(@Param("userId") String userId);

  /** 按 accountId 查询 admin_account_role 记录。 */
  List<RoleRecord> selectAdminRolesByAccountId(@Param("accountId") String accountId);

  /** 查询 admin_menu 记录。 */
  List<MenuRecord> selectAllAdminMenus();

  /** 按 accountId 查询 admin_account_role 记录。 */
  List<String> selectAssignedMenuIdsByAccountId(@Param("accountId") String accountId);

  /** 按 id, now 更新 admin_account 记录。 */
  void updateAdminLoginTime(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 按 id, userId, username 新增 admin_account 记录。 */
  void insertAdminAccount(
      @Param("id") String id,
      @Param("userId") String userId,
      @Param("username") String username,
      @Param("hash") String hash,
      @Param("now") LocalDateTime now);

  /** 按 id, accountId, roleId 新增 admin_account_role 记录。 */
  void insertAdminRoleBinding(
      @Param("id") String id,
      @Param("accountId") String accountId,
      @Param("roleId") String roleId,
      @Param("actor") String actor,
      @Param("now") LocalDateTime now);
}
