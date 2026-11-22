package com.mujian.backend.order.service;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.infrastructure.persistence.record.account.LoginIdentity;
import com.mujian.backend.order.mapper.CheckoutMapper;
import com.mujian.backend.realname.mapper.RealnameMapper;
import org.springframework.stereotype.Service;

/** 新交易准入与合法存量查询/取消的权限边界分开。 */
@Service
public class TradingAccess {
  private final FactoryAccess factoryAccess;
  private final LoginMapper loginMapper;
  private final RealnameMapper realnameMapper;
  private final CheckoutMapper checkoutMapper;

  /** 复用手机号、实名、业务身份已有事实。 */
  public TradingAccess(
      FactoryAccess factoryAccess,
      LoginMapper loginMapper,
      RealnameMapper realnameMapper,
      CheckoutMapper checkoutMapper) {
    this.factoryAccess = factoryAccess;
    this.loginMapper = loginMapper;
    this.realnameMapper = realnameMapper;
    this.checkoutMapper = checkoutMapper;
  }

  /** 买家合法历史可读，不因后续禁用拒绝资金恢复。 */
  public void buyer(String actor) {
    if (!D2Codes.CODE_DEMANDER.equals(factoryAccess.context(actor, false).roleCode()))
      throw ApiFailure.denied(TradingReason.DEMANDER_ROLE_REQUIRED.name());
  }

  /** 检查是否有影响交易准入的待缴款项。 */
  public boolean hasOutstandingCompensation(String actor) {
    return checkoutMapper.existsOutstandingCompensation(actor);
  }

  /** 报价、新提交及继续支付使用同一准入规则。 */
  public void newTrade(String actor) {
    buyer(actor);
    factoryAccess.context(actor, true);
    LoginIdentity loginIdentityPhone = loginMapper.selectPrimaryPhoneIdentityByUser(actor);
    if (loginIdentityPhone == null
        || !D2Codes.CODE_VERIFIED.equals(loginIdentityPhone.verifiedStatus()))
      throw ApiFailure.denied(D2Codes.CODE_ACCOUNT_ACTION_RESTRICTED);
    if (!D2Codes.CODE_VERIFIED.equals(realnameMapper.selectCurrentRealnameStatus(actor)))
      throw new ApiFailure(
          409, D2Codes.CODE_STATE_CONFLICT, TradingReason.REALNAME_REQUIRED.name());
    if (hasOutstandingCompensation(actor))
      throw new ApiFailure(
          409, D2Codes.CODE_STATE_CONFLICT, TradingReason.COMPENSATION_OUTSTANDING.name());
  }
}
