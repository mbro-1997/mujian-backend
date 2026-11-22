package com.mujian.backend.coupon.controller;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.coupon.dto.IssueCouponsInput;
import com.mujian.backend.coupon.mapper.ManualIssueMapper.Receipt;
import com.mujian.backend.coupon.service.ManualIssueService;
import com.mujian.backend.order.service.TradingHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class ManualIssueController {
  private final ManualIssueService manualIssueService;
  private final TradingHttp tradingHttp;
  private final com.mujian.backend.common.security.D2Contract d2Contract;
  private final com.mujian.backend.material.service.VerificationAccess verificationAccess;
  private final tools.jackson.databind.ObjectMapper objectMapper;

  /** 复用管理会话和领域事务。 */
  public ManualIssueController(
      ManualIssueService manualIssueService,
      TradingHttp tradingHttp,
      com.mujian.backend.common.security.D2Contract d2Contract,
      com.mujian.backend.material.service.VerificationAccess verificationAccess,
      tools.jackson.databind.ObjectMapper objectMapper) {
    this.d2Contract = d2Contract;
    this.verificationAccess = verificationAccess;
    this.objectMapper = objectMapper;
    this.manualIssueService = manualIssueService;
    this.tradingHttp = tradingHttp;
  }

  /** 最终确认后一次同步提交。 */
  @PostMapping("/api/admin/coupon-distributions/issue")
  public ApiResponse<Receipt> issue(
      HttpServletRequest r,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody tools.jackson.databind.JsonNode body) {
    String actor = tradingHttp.actor(r);
    verificationAccess.readPermissions(actor, "admin:coupon:issue");
    tools.jackson.databind.JsonNode schema =
        d2Contract
            .route(r.getRequestURI(), "POST")
            .operation()
            .path("requestBody")
            .path("content")
            .path("application/json")
            .path("schema");
    if ("selected_users".equals(body.path("mode").asString())) {
      try {
        d2Contract.validate(
            schema.path("oneOf").get(0).path("properties").path("userIds"), body.path("userIds"));
      } catch (com.mujian.backend.common.api.ApiFailure e) {
        throw new com.mujian.backend.common.api.ApiFailure(
            400, D2Codes.CODE_INVALID_ARGUMENT, BusinessFailureReason.TARGET_USER_INVALID.name());
      }
    }
    d2Contract.validate(schema, body);
    IssueCouponsInput issueCouponsInput = objectMapper.treeToValue(body, IssueCouponsInput.class);
    String trace = (String) r.getAttribute("traceId");
    return new ApiResponse<>(
        D2Codes.CODE_OK, "发放成功", manualIssueService.issue(actor, key, trace, issueCouponsInput), trace);
  }
}
