package com.mujian.backend.account.service;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.*;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.common.security.SessionService.Handle;
import com.mujian.backend.common.support.*;
import com.mujian.backend.infrastructure.persistence.record.account.LoginIdentity;
import com.mujian.backend.infrastructure.persistence.record.account.UserRecord;
import com.mujian.backend.infrastructure.wechat.WechatGateway;
import com.mujian.backend.infrastructure.wechat.WechatGateway.Identity;
import com.mujian.backend.web.response.account.ContextResponse;
import java.time.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.TreeSet;
import org.springframework.dao.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class WechatPhoneLoginService {
  private final LoginMapper loginMapper;
  private final AccountMapper accountMapper;
  private final RuleAcceptanceService ruleAcceptanceService;
  private final AccountContextService accountContextService;
  private final SessionService sessionService;
  private final WechatGateway wechatGateway;
  private final TransactionTemplate transactionTemplate;
  private final Clock clock;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public WechatPhoneLoginService(
      LoginMapper loginMapper,
      AccountMapper accountMapper,
      RuleAcceptanceService ruleAcceptanceService,
      AccountContextService accountContextService,
      SessionService sessionService,
      WechatGateway wechatGateway,
      PlatformTransactionManager platformTransactionManager,
      Clock clock) {
    this.loginMapper = loginMapper;
    this.accountMapper = accountMapper;
    this.ruleAcceptanceService = ruleAcceptanceService;
    this.accountContextService = accountContextService;
    this.sessionService = sessionService;
    this.wechatGateway = wechatGateway;
    transactionTemplate = new TransactionTemplate(platformTransactionManager);
    transactionTemplate.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
    this.clock = clock;
  }

  /** 交换微信凭证，在身份事务成功后创建业务会话。 */
  public com.mujian.backend.account.vo.WechatLoginVO login(
      String loginCode, String phoneCode, List<RuleAcceptanceService.AcceptedRule> accepted) {
    Identity identity = wechatGateway.exchange(loginCode, phoneCode);
    Result result = null;
    for (int attempt = 0; attempt < 3; attempt++) {
      try {
        result = transactionTemplate.execute(s -> match(identity, accepted));
        break;
      } catch (DuplicateKeyException | CannotAcquireLockException dataAccessException) {
        if (attempt == 2) throw dataAccessException;
      }
    }
    ContextResponse contextResponse = accountContextService.context(result.user());
    Handle handle = sessionService.create(result.user(), "miniapp");
    return new com.mujian.backend.account.vo.WechatLoginVO(
        handle.session().getId(), contextResponse, result.notice());
  }

  private record Result(String user, String notice) {}

  /** 按稳定锁序匹配微信和手机号身份并登记规则接受。 */
  private Result match(
      WechatGateway.Identity identity, List<RuleAcceptanceService.AcceptedRule> accepted) {
    LoginIdentity loginIdentity =
        loginMapper.selectWechatIdentityByAppAndOpenId(identity.appId(), identity.openid());
    LoginIdentity loginIdentityPhone = loginMapper.selectPhoneIdentityByPhone(identity.phone());
    TreeSet<String> locks = new TreeSet<String>();
    if (loginIdentity != null) locks.add(loginIdentity.userId());
    if (loginIdentityPhone != null) locks.add(loginIdentityPhone.userId());
    for (String id : locks) loginMapper.selectUserByIdForUpdate(id);
    for (String id : locks) loginMapper.selectIdentityIdsByUserForUpdate(id);
    loginIdentity =
        loginMapper.selectWechatIdentityByAppAndOpenId(identity.appId(), identity.openid());
    loginIdentityPhone = loginMapper.selectPhoneIdentityByPhone(identity.phone());
    // A mapping that appeared after discovery is retried in a fresh transaction with correct lock
    // order.
    if ((loginIdentity != null && !locks.contains(loginIdentity.userId()))
        || (loginIdentityPhone != null && !locks.contains(loginIdentityPhone.userId())))
      throw new CannotAcquireLockException("identity mapping changed");
    if (loginIdentity != null && !CODE_VERIFIED.equals(loginIdentity.verifiedStatus()))
      throw ApiFailure.denied(CODE_ACCOUNT_ACTION_RESTRICTED);
    if (loginIdentity != null
        && loginIdentityPhone != null
        && !loginIdentity.userId().equals(loginIdentityPhone.userId()))
      throw conflict(CODE_LOGIN_IDENTITY_CONFLICT);
    LocalDateTime now = now(clock);
    String user =
        loginIdentity != null
            ? loginIdentity.userId()
            : loginIdentityPhone != null ? loginIdentityPhone.userId() : Ids.next();
    if (loginIdentity == null && loginIdentityPhone == null)
      loginMapper.insertRegisteredUser(user, now);
    else {
      UserRecord userRecord = accountMapper.selectAccountContextByUserId(user);
      if (userRecord == null
          || SystemIdentity.SYSTEM_USER_ID.equals(user)
          || D2Codes.CODE_ADMIN.equals(userRecord.roleCode())
          || "locked".equals(userRecord.userStatus()))
        throw ApiFailure.denied(CODE_ACCOUNT_ACTION_RESTRICTED);
      accountContextService.context(user);
      if (CODE_DISABLED.equals(userRecord.userStatus())
          && (loginIdentity == null || loginMapper.selectPrimaryPhoneIdentityByUser(user) == null))
        throw ApiFailure.denied(CODE_ACCOUNT_ACTION_RESTRICTED);
    }
    if (loginIdentity == null)
      loginMapper.insertWechatIdentity(
          Ids.next(), user, identity.appId(), identity.openid(), identity.unionid(), now);
    LoginIdentity loginIdentityPrimary = loginMapper.selectPrimaryPhoneIdentityByUser(user);
    String notice = null;
    if (loginIdentityPrimary == null) {
      loginMapper.insertPhoneIdentity(
          Ids.next(), user, identity.phone(), identity.countryCode(), now);
      loginMapper.updateUserPhoneSnapshot(user, identity.phone(), now);
    } else if (!loginIdentityPrimary.phone().equals(identity.phone()))
      notice = CODE_PHONE_UNCHANGED;
    ruleAcceptanceService.accept(user, accepted);
    loginMapper.updateUserLoginTime(user, now);
    return new Result(user, notice);
  }
}
