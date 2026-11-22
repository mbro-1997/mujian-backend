package com.mujian.backend.factory.mapper;

import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.dto.FactoryPageQueryDTO;
import com.mujian.backend.factory.projection.*;
import java.util.*;
import org.apache.ibatis.annotations.*;

@Mapper
public interface FactoryInviteCodeMapper {

  /** 按创建管理员和请求编号查找已生成的邀请码。 */
  List<FactoryInviteProjection> selectFactoryInvitationByRequest(
      @Param("actor") String actor, @Param("key") String key);

  /** 按创建请求恢复邀请码，供权限校验后返回。 */
  List<FactoryInviteProjection> selectFactoryInvitationForRecovery(
      @Param("actor") String actor, @Param("key") String key);

  /** 按邀请码摘要查找并锁定邀请码。 */
  List<FactoryInviteProjection> selectFactoryInvitationByHashForUpdate(@Param("hash") String hash);

  /** 按编号读取邀请码，可加行锁。 */
  FactoryInviteProjection selectFactoryInvitationById(
      @Param("id") String id, @Param("lock") boolean lock);

  /** 读取用户最近认领的邀请码。 */
  FactoryInviteProjection selectLatestFactoryInvitationByOwner(@Param("owner") String owner);

  /** 按筛选条件统计邀请码数量。 */
  long selectFactoryInvitationCountByQuery(@Param("factoryPageQueryDTO") FactoryPageQueryDTO factoryPageQueryDTO);

  /** 分页读取邀请码及是否已有入驻申请的标志。 */
  List<FactoryInviteProjection> selectFactoryInvitationPageByQuery(
      @Param("factoryPageQueryDTO") FactoryPageQueryDTO factoryPageQueryDTO);

  /** 保存新邀请码的摘要、请求编号和有效期。 */
  void insertFactoryInvitation(@Param("factoryInviteCodeDO") FactoryInviteCodeDO factoryInviteCodeDO);

  /** 更新邀请码认领用户、锁定状态及时间。 */
  int claimFactoryInvitation(@Param("factoryInviteCodeDO") FactoryInviteCodeDO factoryInviteCodeDO);

  /** 登记邀请码作废状态、操作者及时间。 */
  int revokeFactoryInvitation(@Param("factoryInviteCodeDO") FactoryInviteCodeDO factoryInviteCodeDO);

  /** 登记邀请码已用于入驻申请及其使用时间。 */
  int useFactoryInvitation(@Param("factoryInviteCodeDO") FactoryInviteCodeDO factoryInviteCodeDO);
}
