package com.mujian.backend.web.controller.admin;

import java.util.List;

import com.mujian.backend.web.response.admin.MenuResponse;

import com.mujian.backend.web.response.admin.AdminMeResponse;

import com.mujian.backend.common.base.BaseController;

import com.mujian.backend.application.service.admin.*;
import com.mujian.backend.application.service.admin.AdminLoginService.Authenticated;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.common.security.SessionService.Handle;
import com.mujian.backend.web.request.admin.LoginRequest;
import jakarta.servlet.http.*;
import com.mujian.backend.web.response.admin.CsrfTokenResponse;
import com.mujian.backend.web.response.admin.AdminLoginResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdminController extends BaseController {
  private final SessionService sessionService;
  private final AdminLoginService adminLoginService;
  private final AdminCaptchaService adminCaptchaService;

  public AdminController(SessionService sessionService, AdminLoginService adminLoginService,AdminCaptchaService adminCaptchaService) {
    this.adminCaptchaService=adminCaptchaService;
    this.sessionService = sessionService;
    this.adminLoginService = adminLoginService;
  }

  /** 返回当前会话的 CSRF 令牌。 */
  @GetMapping("/api/admin/auth/csrf")
  public ApiResponse<CsrfTokenResponse> csrf(HttpServletRequest r) {
    CsrfToken t = (CsrfToken) r.getAttribute(CsrfToken.class.getName());
    return success(r, new CsrfTokenResponse(t.getToken()));
  }

  /** 为匿名管理会话签发验证码挑战。 */
  @GetMapping("/api/admin/auth/captcha")
  public ApiResponse<AdminCaptchaService.Challenge> captcha(HttpServletRequest r) {
    Handle handle=sessionService.from(r);
    if(handle==null)throw ApiFailure.unauthorized();
    return success(r,adminCaptchaService.create(handle.session().getId(),r.getRemoteAddr()));
  }

  /** 核验管理员凭证并轮换登录会话。 */
  @PostMapping("/api/admin/auth/login")
  public ApiResponse<AdminLoginResponse> login(
      HttpServletRequest r, HttpServletResponse response, @RequestBody LoginRequest input) {
    try (Authenticated authenticated =
        adminLoginService.authenticate(input.username(), input.password(), r.getRemoteAddr(),sessionService.from(r).session().getId(),input.captchaId(),input.captchaAnswer())) {
      Handle old = sessionService.from(r);
      sessionService.revoke(old.session().getId());
      Handle next = sessionService.create(authenticated.userId(), "admin");
      try {
        authenticated.success();
      } catch (RuntimeException failure) {
        sessionService.revoke(next.session().getId());
        throw failure;
      }
      sessionService.attach(r, next);
      sessionService.cookie(response, next.session().getId());
      return success(r, new AdminLoginResponse(authenticated.userId()));
    }
  }

  /** 返回管理员当前可见的权限入口。 */
  private AdminAccessService.Access access(HttpServletRequest r) {
    return (AdminAccessService.Access) r.getAttribute("adminAccess");
  }

  /** 读取当前管理员资料、角色及权限。 */
  @GetMapping("/api/admin/me")
  public ApiResponse<AdminMeResponse> me(HttpServletRequest r) {
    return success(r, access(r).me());
  }

  /** 返回当前管理员可见的导航树。 */
  @GetMapping("/api/admin/menus")
  public ApiResponse<List<MenuResponse>> menus(HttpServletRequest r) {
    return success(r, access(r).navigation());
  }

  /** 返回菜单配置页使用的完整菜单树。 */
  @GetMapping("/api/admin/system/menus")
  public ApiResponse<List<MenuResponse>> all(HttpServletRequest r) {
    return success(r, access(r).configuration());
  }

  /** 撤销当前管理员会话并移除登录 Cookie。 */
  @PostMapping("/api/admin/auth/logout")
  public ApiResponse<Void> logout(HttpServletRequest r, HttpServletResponse response) {
    sessionService.revoke(sessionService.from(r).session().getId());
    sessionService.cookie(response, null);
    sessionService.attach(r, null);
    return success(r, null);
  }
}
