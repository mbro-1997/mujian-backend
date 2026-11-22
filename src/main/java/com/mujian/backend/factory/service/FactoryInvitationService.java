package com.mujian.backend.factory.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.factory.domain.*;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.dto.FactoryDtos.Invitation;
import com.mujian.backend.factory.dto.FactoryDtos.InvitationCreate;
import com.mujian.backend.factory.projection.*;
import java.time.*;
import java.util.*;

/** 工厂业务契约，复用现有流程及响应。 */
public interface FactoryInvitationService {

  /** 按有效期和申请使用情况计算邀请码展示状态。 */
  String status(FactoryInviteProjection factoryInviteProjection, boolean submitted);

  /** 组装邀请码响应，按操作权限控制可作废标志。 */
  Invitation invitation(
      FactoryInviteProjection factoryInviteProjection, boolean submitted, boolean manage);

  /** 创建可恢复的邀请码。 */
  InvitationResult create(
      String actor, String key, InvitationCreate invitationCreate, String trace);

  /** 按原请求编号恢复已提交结果并核对访问权限。 */
  InvitationResult recover(String actor, String key);

  /** 领取邀请码并锁定归属。 */
  ClaimResult claim(String owner, ClaimRequest claimRequest);

  /** 撤销尚未提交申请的邀请码。 */
  Invitation revoke(
      String actor, String target, String key, RevokeRequest revokeRequest, String trace);
}
