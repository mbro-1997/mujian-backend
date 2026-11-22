package com.mujian.backend.account.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.web.response.account.ContextResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemanderBindingService {
  private final LoginMapper loginMapper;
  private final AccountContextService accountContextService;
  private final RuleAcceptanceService ruleAcceptanceService;
  private final Clock clock;
  private final String appId;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public DemanderBindingService(
      LoginMapper loginMapper,
      AccountContextService accountContextService,
      RuleAcceptanceService ruleAcceptanceService,
      Clock clock,
      @Value("${mj.wechat.app-id:}") String appId) {
    this.loginMapper = loginMapper;
    this.accountContextService = accountContextService;
    this.ruleAcceptanceService = ruleAcceptanceService;
    this.clock = clock;
    this.appId = appId;
  }

  /** 校验互斥身份后绑定租赁方，重复绑定返回原上下文。 */
  @Transactional
  public ContextResponse bind(String user) {
    loginMapper.selectUserByIdForUpdate(user);
    ContextResponse contextResponse = accountContextService.context(user);
    ruleAcceptanceService.requireAccepted(user);
    if (CODE_DEMANDER.equals(contextResponse.roleCode())) return contextResponse;
    if (!CODE_ACTIVE.equals(contextResponse.userStatus()))
      throw ApiFailure.denied(CODE_ACCOUNT_ACTION_RESTRICTED);
    if (contextResponse.roleCode() != null || !CODE_NEW.equals(contextResponse.registrationStage()))
      throw conflict(CODE_IDENTITY_BINDING_CONFLICT);
    LocalDateTime now = now(clock);
    loginMapper.insertDemanderRole(Ids.next(), user, now);
    loginMapper.markDemanderBound(user, now);
    return accountContextService.context(user);
  }

  /** 组装公开摘要，保持脱敏与字段契约。 */
  public com.mujian.backend.account.vo.IdentitySummaryVO summary(String user) {
    ContextResponse contextResponse = accountContextService.context(user);
    return new com.mujian.backend.account.vo.IdentitySummaryVO(
        maskPhone(loginMapper.selectUserPhoneById(user)),
        loginMapper.countWechatIdentitiesByUserAndApp(user, appId) > 0,
        contextResponse.roleCode(),
        contextResponse.userStatus());
  }
}
