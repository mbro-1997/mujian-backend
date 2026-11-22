package com.mujian.backend.account.controller;

import com.mujian.backend.account.service.*;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.security.*;
import jakarta.servlet.http.*;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class RegistrationController extends com.mujian.backend.common.base.BaseController {
  private final RuleAcceptanceService ruleAcceptanceService;
  private final WechatPhoneLoginService wechatPhoneLoginService;
  private final DemanderBindingService demanderBindingService;
  private final SessionService sessionService;
  private final D2RateLimiter d2RateLimiter;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public RegistrationController(
      RuleAcceptanceService ruleAcceptanceService,
      WechatPhoneLoginService wechatPhoneLoginService,
      DemanderBindingService demanderBindingService,
      SessionService sessionService,
      D2RateLimiter d2RateLimiter) {
    this.ruleAcceptanceService = ruleAcceptanceService;
    this.wechatPhoneLoginService = wechatPhoneLoginService;
    this.demanderBindingService = demanderBindingService;
    this.sessionService = sessionService;
    this.d2RateLimiter = d2RateLimiter;
  }

  public record LoginRequest(
      String loginCode,
      String phoneCode,
      boolean consentAccepted,
      List<RuleAcceptanceService.AcceptedRule> acceptedRules) {}

  /** 返回当前生效的业务规则版本。 */
  @GetMapping("/api/public/rules")
  public ApiResponse<com.mujian.backend.account.vo.RuleCollectionVO> rules(
      HttpServletRequest httpServletRequest) {
    return success(httpServletRequest, ruleAcceptanceService.collection());
  }

  /** 按文档编号返回当前有效规则内容。 */
  @GetMapping("/api/public/rules/{id}")
  public ApiResponse<com.mujian.backend.account.vo.RuleDocumentVO> rule(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(httpServletRequest, ruleAcceptanceService.document(id));
  }

  /** 交换微信凭证，在身份事务成功后创建业务会话。 */
  @PostMapping("/api/public/auth/wechat-phone-login")
  public ApiResponse<com.mujian.backend.account.vo.WechatLoginVO> login(
      HttpServletRequest httpServletRequest, @RequestBody LoginRequest loginRequest) {
    d2RateLimiter.check("ip", httpServletRequest.getRemoteAddr(), 20);
    return success(
        httpServletRequest,
        wechatPhoneLoginService.login(
            loginRequest.loginCode(), loginRequest.phoneCode(), loginRequest.acceptedRules()));
  }

  /** 校验互斥身份后绑定租赁方，重复绑定返回原上下文。 */
  @PostMapping("/api/me/roles/demander")
  public ApiResponse<com.mujian.backend.web.response.account.ContextResponse> bind(
      HttpServletRequest httpServletRequest) {
    return success(
        httpServletRequest,
        demanderBindingService.bind(sessionService.user(sessionService.from(httpServletRequest))));
  }

  /** 组装公开摘要，保持脱敏与字段契约。 */
  @GetMapping("/api/me/account-summary")
  public ApiResponse<com.mujian.backend.account.vo.IdentitySummaryVO> summary(
      HttpServletRequest httpServletRequest) {
    return success(
        httpServletRequest,
        demanderBindingService.summary(
            sessionService.user(sessionService.from(httpServletRequest))));
  }
}
