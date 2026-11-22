package com.mujian.backend.account.mapper;

import com.mujian.backend.infrastructure.persistence.record.account.UserRecord;
import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountMapper {
  /** 读取用户状态及启用角色，供准入上下文判断。 */
  UserRecord selectAccountContextByUserId(@Param("id") String id);

  /** 创建管理员关联的业务用户。 */
  void insertAdminUser(
      @Param("id") String id,
      @Param("nickname") String nickname,
      @Param("now") java.time.LocalDateTime now);

  /** 登记业务用户角色，不绕过上层互斥检查。 */
  void insertUserRole(
      @Param("id") String id,
      @Param("userId") String userId,
      @Param("role") String role,
      @Param("now") java.time.LocalDateTime now);

  /** 统计指定用户的登录身份，用于启动时排除系统身份登录。 */
  long countLoginIdentitiesByUserId(@Param("userId") String userId);

  /** 检查固定系统身份与禁止的关联关系。 */
  int countInvalidSystemIdentity(@Param("id") String id);
}
