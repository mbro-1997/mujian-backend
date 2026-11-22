package com.mujian.backend.common.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

/** 独立分享HTML的精确静态资源边界，不扩展业务API授权。 */
public final class ShareStaticResources {
  private static final Set<String> PATHS =
      Set.of("/share/scheme.html", "/share/scheme.js", "/share/scheme.css");

  private ShareStaticResources() {}

  /** 仅允许这三个静态资源的GET读取。 */
  public static boolean matches(HttpServletRequest request) {
    return "GET".equals(request.getMethod()) && PATHS.contains(request.getRequestURI());
  }
}
