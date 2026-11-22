package com.mujian.backend.infrastructure.payment.wechat;

import com.mujian.backend.payment.dto.PaymentAccount;
import java.time.*;
import java.util.List;

/** 平台收付通协议边界；仅返回经过验签的渠道事实，不决定本地订单状态。 */
public interface PaymentGateway {
  record Suborder(String outTradeNo, String subMchid, String description, int totalFen) {}

  record Create(
      PaymentAccount account,
      String outTradeNo,
      String openid,
      LocalDateTime expiry,
      List<Suborder> suborders) {}

  record Reference(PaymentAccount account, String outTradeNo, List<Suborder> suborders) {}

  record SuborderFact(
      String mchid,
      String subMchid,
      String outTradeNo,
      String transactionId,
      String state,
      int totalFen,
      String currency,
      Integer payerFen,
      String payerCurrency,
      Instant paidAt) {}

  record PaymentFact(
      String appId,
      String mchid,
      String outTradeNo,
      List<SuborderFact> suborders,
      boolean notFound) {}

  record RefundRequest(
      PaymentAccount account,
      String subMchid,
      String outTradeNo,
      String transactionId,
      String refundNo,
      int totalFen,
      int refundFen,
      String reason,
      String notifyUrl) {}

  record RefundFact(
      String mchid,
      String subMchid,
      String outTradeNo,
      String transactionId,
      String refundNo,
      String refundId,
      String state,
      Integer totalFen,
      int refundFen,
      String currency,
      Instant successAt,
      boolean notFound) {}

  record Headers(String timestamp, String nonce, String serial, String signature) {}

  record Notification(
      String eventId,
      String eventType,
      String serial,
      String encryptedJson,
      PaymentFact payment,
      RefundFact refund) {}

  record Parameters(
      String appId,
      String timeStamp,
      String nonceStr,
      @com.fasterxml.jackson.annotation.JsonProperty("package") String packageValue,
      String signType,
      String paySign) {}

  /** 检查完整签名验签和回调配置；缺失不得创建本地交易事实。 */
  PaymentAccount requireReady();

  /** 只向渠道发送原合单号，调用方先持久化尝试意图。 */
  String create(Create create);

  /** 查询原合单，不产生新的付款。 */
  PaymentFact query(Reference reference);

  /** 使用原商户及原子单号关单，明确不存在也须由调用方结合截止与创建租约继续核实。 */
  com.mujian.backend.common.enums.PaymentCloseResult close(Reference reference);

  /** 原退款号提交；未知结果由业务按原号恢复。 */
  RefundFact refund(RefundRequest refundRequest);

  /** 原退款号查询，返回确认的资金结果。 */
  RefundFact queryRefund(RefundRequest refundRequest);

  /** 对原prepay生成短期调起参数，不续支付到期时间。 */
  Parameters parameters(String appId, String prepayId);

  /** 先验原始字节签名再解密，仅提取白名单交易字段。 */
  Notification notification(String kind, Headers headers, byte[] body);

  /** 只用于数据库中已验签入箱的加密原文恢复，不能用于外部入口。 */
  Notification restoreVerifiedNotification(String kind, String serial, String encryptedJson);
}
