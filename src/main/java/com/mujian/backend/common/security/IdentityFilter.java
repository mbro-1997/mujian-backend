package com.mujian.backend.common.security;

import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.application.service.admin.AdminAccessService;
import com.mujian.backend.application.service.admin.AdminAccessService.Access;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.security.SessionService.Handle;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class IdentityFilter extends OncePerRequestFilter {
  private final SessionService sessionService;
  private final AdminAccessService adminAccessService;

  public IdentityFilter(SessionService s, AdminAccessService a) {
    sessionService = s;
    adminAccessService = a;
  }

  /** 检查本次请求并决定是否继续后续过滤链。 */
  protected void doFilterInternal(
      HttpServletRequest r, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (ShareStaticResources.matches(r) || PaymentNotificationRoutes.matches(r)) {
      chain.doFilter(r, response);
      return;
    }
    String path = r.getRequestURI();
    boolean admin = path.startsWith("/api/admin/");
    Handle handle = sessionService.load(sessionService.credential(r));
    if (handle != null && !((admin ? "admin" : "miniapp").equals(sessionService.type(handle))))
      throw ApiFailure.denied(D2Codes.CODE_CLIENT_TYPE_MISMATCH);
    if ("/api/admin/auth/csrf".equals(path) && handle == null) {
      handle = sessionService.create(null, "admin");
      sessionService.cookie(response, handle.session().getId());
    }
    sessionService.attach(r, handle);
    if ("/api/admin/auth/login".equals(path) && handle == null) throw ApiFailure.denied(D2Codes.CODE_CSRF_INVALID);
    boolean publicShare =
        "GET".equals(r.getMethod()) && path.matches("/api/public/scheme-shares/[^/]+");
    boolean publicPath =
        publicShare
            || (path.equals("/api/public/props")
                || path.equals("/api/public/props/options")
                || path.matches("/api/public/props/[0-9a-f]{32}"))
            || path.equals("/api/public/rules")
            || path.matches("/api/public/rules/[0-9a-f]{32}")
            || path.equals("/api/public/auth/wechat-phone-login")
            || path.endsWith("/auth/csrf")
            || path.equals("/api/admin/auth/captcha")
            || path.endsWith("/admin/auth/login")
            || path.equals("/api/auth/logout");
    String user = sessionService.user(handle);
    if (!publicPath && user == null) throw ApiFailure.unauthorized();
    if (user != null) {
      List<SimpleGrantedAuthority> permissions = List.of();
      if (admin
          && !path.endsWith("/auth/logout")
          && !path.endsWith("/auth/login")
          && !path.endsWith("/auth/csrf")) {
        try {
          Access access = adminAccessService.load(user);
          r.setAttribute("adminAccess", access);
          permissions =
              access.me().permissions().stream().map(SimpleGrantedAuthority::new).toList();
        } catch (ApiFailure f) {
          if (D2Codes.CODE_ACCOUNT_ACTION_RESTRICTED.equals(f.reason)) {
            sessionService.revoke(handle.session().getId());
            sessionService.cookie(response, null);
          }
          throw f;
        }
      }
      SecurityContext context = SecurityContextHolder.createEmptyContext();
      context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, permissions));
      SecurityContextHolder.setContext(context);
    }
    chain.doFilter(r, response);
  }
}
