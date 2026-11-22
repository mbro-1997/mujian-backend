package com.mujian.backend.account.service;

import static com.mujian.backend.common.support.D2Codes.*;

import com.mujian.backend.account.mapper.AccountMapper;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.SystemIdentity;
import com.mujian.backend.domain.account.AccountContextPolicy;
import com.mujian.backend.infrastructure.persistence.record.account.UserRecord;
import com.mujian.backend.web.response.account.ContextResponse;
import org.springframework.stereotype.Service;

@Service
public class AccountContextService {
  private final AccountMapper accountMapper;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public AccountContextService(AccountMapper accountMapper) {
    this.accountMapper = accountMapper;
  }

  /** 取得账号上下文并按当前业务要求检查状态。 */
  public ContextResponse context(String userId) {
    if (SystemIdentity.SYSTEM_USER_ID.equals(userId))
      throw ApiFailure.denied(CODE_ACCOUNT_ACTION_RESTRICTED);
    UserRecord userRecord = accountMapper.selectAccountContextByUserId(userId);
    if (userRecord == null) throw ApiFailure.unauthorized();
    String next =
        AccountContextPolicy.next(
            userRecord.userStatus(), userRecord.registerStatus(), userRecord.roleCode());
    return new ContextResponse(
        userRecord.id(),
        userRecord.nickname(),
        userRecord.userStatus(),
        userRecord.roleCode(),
        userRecord.registerStatus(),
        CODE_DISABLED.equals(userRecord.userStatus()) ? CODE_RESTRICTED : CODE_NORMAL,
        next);
  }
}
