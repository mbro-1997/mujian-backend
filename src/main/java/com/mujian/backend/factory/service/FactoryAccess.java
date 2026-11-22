package com.mujian.backend.factory.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.account.service.AccountContextService;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.FactoryReasonCode;
import com.mujian.backend.common.enums.FactorySource;
import com.mujian.backend.factory.mapper.FactoryProfileMapper;
import com.mujian.backend.factory.mapper.FactoryReviewMapper;
import com.mujian.backend.factory.projection.*;
import com.mujian.backend.infrastructure.persistence.record.account.LoginIdentity;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.web.response.account.ContextResponse;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class FactoryAccess {
  private final AccountContextService accountContextService;
  private final LoginMapper loginMapper;
  private final FactoryProfileMapper factoryProfileMapper;
  private final FactoryReviewMapper factoryReviewMapper;
  private final VerificationAccess verificationAccess;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactoryAccess(
      AccountContextService accountContextService,
      LoginMapper loginMapper,
      FactoryProfileMapper factoryProfileMapper,
      FactoryReviewMapper factoryReviewMapper,
      VerificationAccess verificationAccess) {
    this.accountContextService = accountContextService;
    this.loginMapper = loginMapper;
    this.factoryProfileMapper = factoryProfileMapper;
    this.factoryReviewMapper = factoryReviewMapper;
    this.verificationAccess = verificationAccess;
  }

  /** 按稳定顺序锁定业务用户，防止并发交叉加锁。 */
  public void lock(String... ids) {
    SortedSet<String> userIds = new TreeSet<>();
    for (String userId : ids) if (userId != null) userIds.add(userId);
    for (String userId : userIds) {
      if (loginMapper.selectUserByIdForUpdate(id(userId)) == null) throw missing();
    }
  }

  /** 取得账号上下文并按当前业务要求检查状态。 */
  public ContextResponse context(String user, boolean write) {
    ContextResponse contextResponse = accountContextService.context(user);
    if (write && !CODE_ACTIVE.equals(contextResponse.userStatus()))
      throw ApiFailure.denied(CODE_ACCOUNT_ACTION_RESTRICTED);
    return contextResponse;
  }

  /** 检查账号是否具备工厂入驻资格。 */
  public void entrant(String user, boolean write) {
    ContextResponse contextResponse = context(user, write);
    if (contextResponse.roleCode() != null)
      throw ApiFailure.denied(FactoryReasonCode.FACTORY_ROLE_NOT_ELIGIBLE.name());
  }

  /** 根据已核实的工厂账号读取所属工厂 ID；缺少工厂记录仍按数据异常处理。 */
  public String requireOwnedFactoryId(String ownerUserId) {
    String factoryId = factoryProfileMapper.selectFactoryIdByOwner(ownerUserId);
    if (factoryId == null)
      throw new ApiFailure(
          500, CODE_DATA_INTEGRITY_ERROR, FactoryReasonCode.FACTORY_DATA_INCONSISTENT.name());
    return factoryId;
  }

  /** 取得当前账号关联的工厂资料并核对访问条件。 */
  public FactorySourceProjection profile(String user, boolean write, boolean lock) {
    ContextResponse contextResponse = context(user, write);
    if (!CODE_SUPPLIER.equals(contextResponse.roleCode()))
      throw ApiFailure.denied(FactoryReasonCode.FACTORY_ROLE_NOT_ELIGIBLE.name());
    FactorySourceProjection factorySourceProjection =
        factoryProfileMapper.selectFactoryProfileByOwner(user, lock);
    if (factorySourceProjection == null)
      throw new ApiFailure(
          500, CODE_DATA_INTEGRITY_ERROR, FactoryReasonCode.FACTORY_DATA_INCONSISTENT.name());
    return factorySourceProjection;
  }

  /** 核对申请归属后读取业务记录。 */
  public FactorySourceProjection owned(String user, FactorySource factorySource, String key) {
    context(user, false);
    FactorySourceProjection factorySourceProjection =
        factoryReviewMapper.selectFactorySourceById(factorySource, id(key), false);
    if (factorySourceProjection == null
        || !user.equals(
            (factorySource == FactorySource.PROFILE
                ? factorySourceProjection.getOwnerUserId()
                : factorySourceProjection.getApplicantUserId()))) throw missing();
    return factorySourceProjection;
  }

  /** 获取本次只读页面已核实的权限。 */
  public List<String> readPermissions(String actor, String domain) {
    return verificationAccess.readPermissions(actor, "admin:" + domain + ":read");
  }

  /** 核对管理员对应业务权限。 */
  public void admin(String actor, String domain, String action) {
    verificationAccess.admin(actor, "admin:" + domain + ":read");
    if (!action.equals("read")) verificationAccess.admin(actor, "admin:" + domain + ":" + action);
  }

  /** 判断当前管理员是否具有指定操作权限。 */
  public boolean has(String actor, String domain, String action) {
    return verificationAccess.has(actor, "admin:" + domain + ":read")
        && verificationAccess.has(actor, "admin:" + domain + ":" + action);
  }

  /** 读取或校验工厂联系电话。 */
  public String phone(String owner) {
    LoginIdentity loginIdentity = loginMapper.selectPrimaryPhoneIdentityByUser(owner);
    if (loginIdentity == null || !CODE_VERIFIED.equals(loginIdentity.verifiedStatus()))
      throw new ApiFailure(500, CODE_DATA_INTEGRITY_ERROR, CODE_ACCOUNT_CONTEXT_INCONSISTENT);
    return loginIdentity.phone();
  }
}
