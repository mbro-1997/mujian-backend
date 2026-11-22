package com.mujian.backend.payment.controller;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.payment.service.PaymentNotificationInbox;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;

/** 渠道原始通知专用应答，不套业务ApiResponse或请求摘要。 */
@RestController
public class PaymentNotificationController {
  private final PaymentGateway paymentGateway;
  private final PaymentNotificationInbox paymentNotificationInbox;

  /** 统一渠道验签和持久收件，不在控制器写资金或SQL。 */
  public PaymentNotificationController(
      PaymentGateway paymentGateway, PaymentNotificationInbox paymentNotificationInbox) {
    this.paymentGateway = paymentGateway;
    this.paymentNotificationInbox = paymentNotificationInbox;
  }

  /** 两条精确路由接收原始字节，持久化提交返回后才确认204。 */
  @PostMapping({"/api/wechat-pay/notifications/payment", "/api/wechat-pay/notifications/refund"})
  public void receive(HttpServletRequest request, HttpServletResponse response) throws IOException {
    try {
      if (request.getQueryString() != null
          || request.getContentType() == null
          || !"application/json".equalsIgnoreCase(request.getContentType().split(";", 2)[0].trim()))
        throw ApiFailure.invalid();
      byte[] body = request.getInputStream().readNBytes(1048577);
      if (body.length > 1048576) throw ApiFailure.invalid();
      String kind = request.getRequestURI().endsWith("/payment") ? "payment" : "refund";
      PaymentGateway.Headers headers =
          new PaymentGateway.Headers(
              request.getHeader("Wechatpay-Timestamp"),
              request.getHeader("Wechatpay-Nonce"),
              request.getHeader("Wechatpay-Serial"),
              request.getHeader("Wechatpay-Signature"));
      paymentNotificationInbox.receive(kind, paymentGateway.notification(kind, headers, body));
      response.setStatus(204);
    } catch (ApiFailure | tools.jackson.core.JacksonException exception) {
      response.setStatus(400);
      response.setContentType("application/json");
      response.getWriter().write("{\"code\":\"FAIL\",\"message\":\"通知校验未通过\"}");
    } catch (org.springframework.dao.DataAccessException
        | org.springframework.transaction.TransactionException exception) {
      response.setStatus(500);
      response.setContentType("application/json");
      response.getWriter().write("{\"code\":\"FAIL\",\"message\":\"通知尚未持久接收\"}");
    }
  }
}
