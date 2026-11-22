package com.mujian.backend.common.enums;

/** 已验签收付通分账接收方关闭原因。未识别的渠道文本不进入审计和页面。 */
public enum WechatSharingCloseReason {
  ACCOUNT_ABNORMAL,
  NO_RELATION,
  RECEIVER_HIGH_RISK,
  RECEIVER_REAL_NAME_NOT_VERIFIED,
  NO_AUTH,
  RECEIVER_RECEIPT_LIMIT,
  PAYER_ACCOUNT_ABNORMAL,
  INVALID_REQUEST,
  CHANNEL_CLOSED;

  /** 已确认关闭但缺少可识别原因时，仍保留关闭终态。 */
  public static WechatSharingCloseReason fromChannel(String value) {
    for (WechatSharingCloseReason reason : values()) {
      if (reason.name().equals(value)) return reason;
    }
    return CHANNEL_CLOSED;
  }
}
