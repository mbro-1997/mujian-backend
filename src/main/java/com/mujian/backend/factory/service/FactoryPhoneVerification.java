package com.mujian.backend.factory.service;

import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.FactoryReasonCode;
import com.mujian.backend.factory.dto.FactoryDtos.PhoneCapability;
import com.mujian.backend.web.response.account.ContextResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 腾讯云短信/验证码未来适配入口。没有生产适配器时始终失败关闭，不产生假成功。 */
@Service
public class FactoryPhoneVerification {
  private final FactoryAccess factoryAccess;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public FactoryPhoneVerification(
      FactoryAccess factoryAccess, @Value("${mj.factory-phone.enabled:false}") boolean enabled) {
    this.factoryAccess = factoryAccess;
    if (enabled)
      throw new IllegalStateException(
          "Factory phone provider has not been implemented; keep disabled");
  }

  /** 返回联系电话验证能力及当前不可用原因。 */
  public PhoneCapability capability(String user) {
    ContextResponse contextResponse = factoryAccess.context(user, false);
    factoryAccess.profile(user, false, false);
    return new PhoneCapability(
        false,
        CODE_ACTIVE.equals(contextResponse.userStatus())
            ? FactoryReasonCode.PHONE_VERIFICATION_NOT_CONFIGURED.name()
            : CODE_ACCOUNT_ACTION_RESTRICTED,
        null);
  }

  /** 检查工厂权限后按未配置短信规则拒绝执行。 */
  public void command(String user) {
    factoryAccess.profile(user, true, false);
    throw unavailable(FactoryReasonCode.PHONE_VERIFICATION_NOT_CONFIGURED.name());
  }

  /** 拒绝尚未开放的联系电话变更，不产生虚假验证结果。 */
  public void unchanged(String current, String proposed, String proof) {
    if (proof != null || !current.equals(proposed))
      throw unavailable(FactoryReasonCode.PHONE_VERIFICATION_NOT_CONFIGURED.name());
  }
}
