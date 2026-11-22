package com.mujian.backend.scheme.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.security.D2RateLimiter;
import com.mujian.backend.planning.dto.*;
import com.mujian.backend.planning.dto.PlanningDtos.*;
import com.mujian.backend.planning.service.*;
import com.mujian.backend.planning.vo.PlanningViews.*;
import com.mujian.backend.scheme.service.SchemeService;
import com.mujian.backend.scheme.vo.SchemeViews.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class SchemeShareController extends BaseController {
  private final SchemeService schemeService;
  private final D2RateLimiter d2RateLimiter;

  @org.springframework.beans.factory.annotation.Value("${mj.planning.share-requests-per-minute:60}")
  private int limit;

  /** 复用既有限流组件，IP只以哈希限流。 */
  public SchemeShareController(SchemeService schemeService, D2RateLimiter d2RateLimiter) {
    this.schemeService = schemeService;
    this.d2RateLimiter = d2RateLimiter;
  }

  /** 匿名仅能GET当前token获权的只读投影。 */
  @GetMapping("/api/public/scheme-shares/{token}")
  public ApiResponse<SharedScheme> shared(
      HttpServletRequest httpServletRequestRequest, @PathVariable String token) {
    d2RateLimiter.check("scheme-share", httpServletRequestRequest.getRemoteAddr(), limit);
    return success(httpServletRequestRequest, schemeService.shared(token));
  }
}
