package com.mujian.backend.account.mapper;

import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.projection.*;
import com.mujian.backend.infrastructure.persistence.record.account.*;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface LoginMapper {
  /** 读取手机号身份当前是否可设置为主绑定。 */
  int selectWritablePrimaryFlag();

  /** 锁定用户行，统一身份写入的锁顺序。 */
  String selectUserByIdForUpdate(@Param("id") String id);

  /** 按本人及支付AppID读取已验证微信身份，不由前端提交OpenID。 */
  String selectWechatOpenIdByUserAndApp(@Param("user") String user, @Param("appId") String appId);

  /** 按应用及OpenID读取微信身份映射。 */
  LoginIdentity selectWechatIdentityByAppAndOpenId(
      @Param("appId") String appId, @Param("openid") String openid);

  /** 按手机号读取既有身份映射。 */
  LoginIdentity selectPhoneIdentityByPhone(@Param("phone") String phone);

  /** 读取用户已验证的主手机号身份。 */
  LoginIdentity selectPrimaryPhoneIdentityByUser(@Param("user") String user);

  /** 按既定顺序锁定用户身份关联。 */
  List<String> selectIdentityIdsByUserForUpdate(@Param("user") String user);

  /** 共享锁读取注册必须接受的当前规则。 */
  List<RuleRecord> selectRequiredRegistrationRulesForShare();

  /** 按编号读取规则文档。 */
  RuleRecord selectRegistrationRuleById(@Param("id") String id);

  /** 核对用户是否已接受指定规则版本及摘要。 */
  int countAcceptedRegistrationRuleVersion(
      @Param("user") String user,
      @Param("document") String document,
      @Param("version") String version,
      @Param("hash") String hash);

  /** 登记用户接受的规则版本和时间。 */
  void insertRegistrationRuleAcceptance(
      @Param("id") String id,
      @Param("user") String user,
      @Param("ruleRecord") RuleRecord ruleRecord,
      @Param("now") LocalDateTime now);

  /** 创建已完成手机号验证的注册用户。 */
  void insertRegisteredUser(@Param("id") String id, @Param("now") LocalDateTime now);

  /** 保存微信身份与业务用户的关联。 */
  void insertWechatIdentity(
      @Param("id") String id,
      @Param("user") String user,
      @Param("appId") String appId,
      @Param("openid") String openid,
      @Param("unionid") String unionid,
      @Param("now") LocalDateTime now);

  /** 保存已验证的主手机号身份。 */
  void insertPhoneIdentity(
      @Param("id") String id,
      @Param("user") String user,
      @Param("phone") String phone,
      @Param("country") String country,
      @Param("now") LocalDateTime now);

  /** 同步用户手机号展示快照。 */
  void updateUserPhoneSnapshot(
      @Param("user") String user, @Param("phone") String phone, @Param("now") LocalDateTime now);

  /** 更新本次登录时间，不改变身份关系。 */
  void updateUserLoginTime(@Param("user") String user, @Param("now") LocalDateTime now);

  /** 为通过准入检查的用户建立租赁方角色。 */
  void insertDemanderRole(
      @Param("id") String id, @Param("user") String user, @Param("now") LocalDateTime now);

  /** 更新用户已绑定租赁方的注册阶段。 */
  void markDemanderBound(@Param("user") String user, @Param("now") LocalDateTime now);

  /** 按用户编号读取展示昵称。 */
  String selectUserNicknameById(@Param("user") String user);

  /** 按用户编号读取展示手机号。 */
  String selectUserPhoneById(@Param("user") String user);

  /** 判断用户在当前小程序是否已有微信绑定。 */
  int countWechatIdentitiesByUserAndApp(@Param("user") String user, @Param("appId") String appId);

  /** 将账号注册阶段更新为已认领工厂邀请码。 */
  int markFactoryInvitationLocked(
      @Param("now") java.time.LocalDateTime now, @Param("id") String id);

  /** 为入驻审核通过的用户建立工厂角色。 */
  int insertSupplierRole(
      @Param("id") String id,
      @Param("user") String user,
      @Param("now") java.time.LocalDateTime now);

  /** 更新审核决定对应的账号注册阶段。 */
  int updateRegistrationStage(
      @Param("stage") String stage,
      @Param("now") java.time.LocalDateTime now,
      @Param("id") String id);

  /** 将已提交入驻申请的账号标记为待审核。 */
  int markFactoryPending(@Param("now") java.time.LocalDateTime now, @Param("id") String id);
}
