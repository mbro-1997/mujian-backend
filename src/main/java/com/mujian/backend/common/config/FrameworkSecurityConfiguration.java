package com.mujian.backend.common.config;

import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.security.SessionCsrfRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
public class FrameworkSecurityConfiguration {
  /** 配置登录、授权及请求防护链。 */
  @Bean
  SecurityFilterChain security(
      HttpSecurity http,
      SessionCsrfRepository csrf,
      ObjectMapper json,
      com.mujian.backend.common.security.SessionService sessions,
      com.mujian.backend.application.service.admin.AdminAccessService access)
      throws Exception {
    http.addFilterBefore(
        new com.mujian.backend.common.security.IdentityFilter(sessions, access),
        org.springframework.security.web.csrf.CsrfFilter.class);
    http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    http.csrf(
        c ->
            c.csrfTokenRepository(csrf)
                .csrfTokenRequestHandler(new XorCsrfTokenRequestAttributeHandler())
                .requireCsrfProtectionMatcher(
                    r ->
                        "POST".equals(r.getMethod())
                            && r.getRequestURI().startsWith("/api/admin/")));
    http.authorizeHttpRequests(
        a ->
            a.requestMatchers(com.mujian.backend.common.security.PaymentNotificationRoutes::matches)
                .permitAll()
                .requestMatchers(com.mujian.backend.common.security.ShareStaticResources::matches)
                .permitAll()
                .requestMatchers(
                    r ->
                        "GET".equals(r.getMethod())
                            && r.getRequestURI().matches("/api/public/scheme-shares/[^/]+"))
                .permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/public/props/*/images/*")
                .permitAll()
                .requestMatchers(
                    "/api/public/props",
                    "/api/public/props/*",
                    "/api/public/rules",
                    "/api/public/rules/*",
                    "/api/public/auth/wechat-phone-login",
                    "/api/admin/auth/csrf",
                    "/api/admin/auth/captcha",
                    "/api/admin/auth/login",
                    "/api/auth/logout")
                .permitAll()
                .requestMatchers("/api/admin/system/menus")
                .hasAuthority("admin:menu:manage")
                .anyRequest()
                .authenticated());
    http.exceptionHandling(
        e ->
            e.authenticationEntryPoint(
                    (r, s, x) -> {
                      s.setStatus(401);
                      s.setContentType("application/json");
                      s.getWriter()
                          .write(
                              json.writeValueAsString(
                                  ApiReply.error(r, ApiFailure.unauthorized())));
                    })
                .accessDeniedHandler(
                    (r, s, x) -> {
                      ApiFailure apiFailure =
                          ApiFailure.denied(
                              x instanceof org.springframework.security.web.csrf.CsrfException
                                  ? D2Codes.CODE_CSRF_INVALID
                                  : D2Codes.CODE_PERMISSION_DENIED);
                      s.setStatus(403);
                      s.setContentType("application/json");
                      s.getWriter().write(json.writeValueAsString(ApiReply.error(r, apiFailure)));
                    }));
    return http.build();
  }

  /** 关闭框架生成的默认账号入口。 */
  @Bean
  UserDetailsService noGeneratedAccount() {
    return u -> {
      throw new UsernameNotFoundException("无框架默认账号");
    };
  }
}
