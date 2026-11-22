package com.mujian.backend.infrastructure.payment.wechat;

/** 仅查询已可信登记进件来源，自动进件需获准完整方案后另接。 */
public interface MerchantGateway {
  record Status(
      String applymentId, String requestNo, String subMchid, String state, String actionUrl) {}

  /** 按可信原进件号查询渠道，返回事实而非手工激活结果。 */
  Status query(String applymentId, String requestNo);
}
