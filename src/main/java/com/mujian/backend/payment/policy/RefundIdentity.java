package com.mujian.backend.payment.policy;

import com.mujian.backend.common.enums.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

/** 新旧退款义务固定来源键，不受重试次数、请求编号或当前时间影响。 */
public final class RefundIdentity {
  /** 禁止实例化此静态结构容器。 */
  private RefundIdentity() {}

  /** 对已验证来源和ID构造版本化JSON数组摘要。 */
  public static String key(String type, String sourceId, String suborderId, String reason) {
    if (!sourceId.matches("[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}")
        || !suborderId.matches("[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}"))
      throw new IllegalArgumentException("invalid refund source id");
    boolean order =
        TradingResource.rental_order.name().equals(type)
            && Set.of(
                    TradingRefundType.user_cancel_before_accept.name(),
                    TradingRefundType.user_cancel_rule.name(),
                    TradingRefundType.factory_manual_reject.name(),
                    TradingRefundType.factory_timeout_reject.name())
                .contains(reason);
    boolean batch =
        TradingResource.checkout_batch.name().equals(type)
            && TradingRefundType.unformed_batch_refund.name().equals(reason);
    boolean normal =
        RefundObligationSource.return_check.name().equals(type)
                && TradingRefundType.deposit_release.name().equals(reason)
            || RefundObligationSource.expense_statement.name().equals(type)
                && TradingRefundType.freight_pool_release.name().equals(reason);
    boolean dispute = ("order_claim".equals(type) && "deposit_release".equals(reason)) || ("arbitration_decision".equals(type) && "arbitration_refund".equals(reason));
    boolean issue = "admin_case_action".equals(type) && "customer_service_refund".equals(reason);
    boolean extension="extension_request".equals(type) && Set.of("extension_cancelled","extension_rejected").contains(reason);
    boolean custom = "custom_order".equals(type) && Set.of("user_cancel_before_accept","platform_adjustment").contains(reason)
        || "custom_after_sale_request".equals(type) && "customer_service_refund".equals(reason);
    if (!custom && !order && !batch && !normal && !issue && !extension && !dispute)
      throw new IllegalArgumentException("invalid refund obligation");
    String canonical =
        "[\""
            + (issue || extension || dispute ? "v3" : normal ? "v2" : "v1")
            + "\",\""
            + type
            + "\",\""
            + sourceId
            + "\",\""
            + suborderId
            + "\",\""
            + (extension?"extension_refund":reason)
            + "\"]";
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
