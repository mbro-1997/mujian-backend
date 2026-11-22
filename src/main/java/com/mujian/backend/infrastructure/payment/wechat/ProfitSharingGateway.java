package com.mujian.backend.infrastructure.payment.wechat;

import com.mujian.backend.common.enums.ProfitSharingOutcome;
import com.mujian.backend.common.enums.WechatSharingCloseReason;

/** 原渠道交易、平台账户与阶段原号同时匹配后才允许确认金额。 */
public interface ProfitSharingGateway {
  record Request(
      String platformMerchant,
      String subMerchant,
      String transactionId,
      String outNo,
      boolean finish,
      int amount) {}

  record Observation(
      ProfitSharingOutcome outcome,
      int actual,
      String channelNo,
      WechatSharingCloseReason closeReason) {
    /** 普通阶段观察不附带接收方关闭原因。 */
    public Observation(ProfitSharingOutcome outcome, int actual, String channelNo) {
      this(outcome, actual, channelNo, null);
    }
  }

  /** 按原商户、交易和阶段单号执行请求或签名查询。 */
  Observation execute(Request request, boolean query);
}
