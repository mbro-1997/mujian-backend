package com.mujian.backend.domain.account;

import static com.mujian.backend.common.support.D2Codes.*;

import com.mujian.backend.common.api.ApiFailure;
import java.util.*;

public final class AccountContextPolicy {
  private static final Map<String, String> NEXT =
      Map.of(
          CODE_NEW,
          CODE_SELECT_IDENTITY,
          CODE_DEMANDER_BOUND,
          CODE_ENTER_DEMANDER_HOME,
          CODE_FACTORY_INVITE_LOCKED,
          CODE_COMPLETE_FACTORY_PROFILE,
          CODE_FACTORY_PENDING,
          CODE_WAIT_FACTORY_REVIEW,
          CODE_FACTORY_REJECTED,
          CODE_REVISE_FACTORY_APPLICATION,
          CODE_FACTORY_BOUND,
          CODE_ENTER_FACTORY_HOME);

  /** 准入映射为固定规则，无需实例化。 */
  private AccountContextPolicy() {}

  /** 检查状态与互斥角色一致后返回允许的下一步。 */
  public static String next(String status, String stage, String role) {
    if ("locked".equals(status)) throw ApiFailure.denied(CODE_ACCOUNT_ACTION_RESTRICTED);
    if (!Set.of(CODE_ACTIVE, CODE_DISABLED).contains(status) || !NEXT.containsKey(stage))
      throw ApiFailure.inconsistent();
    String expected =
        CODE_DEMANDER_BOUND.equals(stage)
            ? CODE_DEMANDER
            : CODE_FACTORY_BOUND.equals(stage) ? CODE_SUPPLIER : null;
    if (!Objects.equals(expected, role)) throw ApiFailure.inconsistent();
    return CODE_DISABLED.equals(status) ? CODE_ENTER_RESTRICTED_HOME : NEXT.get(stage);
  }
}
