package com.mujian.backend.account.controller;

import com.mujian.backend.account.service.AccountContextService;
import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.common.security.SessionService.Handle;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class AccountController extends com.mujian.backend.common.base.BaseController {
  private final SessionService sessionService;
  private final AccountContextService accountContextService;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public AccountController(
      SessionService sessionService, AccountContextService accountContextService) {
    this.sessionService = sessionService;
    this.accountContextService = accountContextService;
  }

  /** 取得账号上下文并按当前业务要求检查状态。 */
  @GetMapping("/api/me/context")
  public ApiResponse<com.mujian.backend.web.response.account.ContextResponse> context(
      HttpServletRequest httpServletRequest) {
    return success(
        httpServletRequest,
        accountContextService.context(
            sessionService.user(sessionService.from(httpServletRequest))));
  }

  /** 撤销当前会话并返回既定退出结果。 */
  @PostMapping("/api/auth/logout")
  public ApiResponse<Void> logout(HttpServletRequest httpServletRequest) {
    Handle handle = sessionService.from(httpServletRequest);
    if (handle != null) sessionService.revoke(handle.session().getId());
    sessionService.attach(httpServletRequest, null);
    return success(httpServletRequest, null);
  }
}
