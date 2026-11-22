package com.mujian.backend.common.security;

import jakarta.servlet.http.HttpServletRequest;

/** 仅两个原始渠道通知POST例外，不放开相邻路由或普通业务请求。 */
public final class PaymentNotificationRoutes {
  private PaymentNotificationRoutes() {}

  /** 所有公共过滤层引用同一精确方法及路径判断。 */
  public static boolean matches(HttpServletRequest request) {
    return "POST".equals(request.getMethod())
        && ("/api/wechat-pay/notifications/payment".equals(request.getRequestURI())
            || "/api/wechat-pay/notifications/refund".equals(request.getRequestURI()));
  }
}
