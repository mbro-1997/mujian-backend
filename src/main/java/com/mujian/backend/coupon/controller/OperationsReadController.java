package com.mujian.backend.coupon.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.coupon.service.OperationsReader;
import com.mujian.backend.coupon.vo.OperationsViews.*;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.service.TradingHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class OperationsReadController extends BaseController {
  private final OperationsReader operationsReader;
  private final VerificationAccess verificationAccess;
  private final TradingHttp tradingHttp;

  /** 各查询分别校验对应权限。 */
  public OperationsReadController(
      OperationsReader operationsReader,
      VerificationAccess verificationAccess,
      TradingHttp tradingHttp) {
    this.operationsReader = operationsReader;
    this.verificationAccess = verificationAccess;
    this.tradingHttp = tradingHttp;
  }

  /** 核对所选Template详情。 */
  @GetMapping("/coupon-templates/{id}")
  public ApiResponse<Template> readTemplate(HttpServletRequest r, @PathVariable String id) {
    verificationAccess.readPermissions(tradingHttp.actor(r), "admin:coupon-template:read");
    return success(r, operationsReader.readTemplate(id, false));
  }

  /** 按管理权限查询Template列表。 */
  @GetMapping("/coupon-templates")
  public ApiResponse<PageResult<Template>> pageTemplate(
      HttpServletRequest r,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    verificationAccess.readPermissions(tradingHttp.actor(r), "admin:coupon-template:read");
    return success(
        r,
        operationsReader.pageTemplate(
            new TemplateFilter(status, keyword), PageQuery.of(page, pageSize)));
  }

  /** 核对所选Activity详情。 */
  @GetMapping("/coupon-activities/{id}")
  public ApiResponse<Activity> readActivity(HttpServletRequest r, @PathVariable String id) {
    verificationAccess.readPermissions(tradingHttp.actor(r), "admin:coupon-activity:read");
    return success(r, operationsReader.readActivity(id, false));
  }

  /** 按管理权限查询Activity列表。 */
  @GetMapping("/coupon-activities")
  public ApiResponse<PageResult<Activity>> pageActivity(
      HttpServletRequest r,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    verificationAccess.readPermissions(tradingHttp.actor(r), "admin:coupon-activity:read");
    return success(
        r, operationsReader.pageActivity(new ActivityFilter(status), PageQuery.of(page, pageSize)));
  }

  /** 核对所选Rule详情。 */
  @GetMapping("/coupon-reward-rules/{id}")
  public ApiResponse<Rule> readRule(HttpServletRequest r, @PathVariable String id) {
    verificationAccess.readPermissions(tradingHttp.actor(r), "admin:coupon-reward:read");
    return success(r, operationsReader.readRule(id, false));
  }

  /** 按管理权限查询Rule列表。 */
  @GetMapping("/coupon-reward-rules")
  public ApiResponse<PageResult<Rule>> pageRule(
      HttpServletRequest r,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    verificationAccess.readPermissions(tradingHttp.actor(r), "admin:coupon-reward:read");
    return success(
        r, operationsReader.pageRule(new RuleFilter(eventType), PageQuery.of(page, pageSize)));
  }

  /** 按管理权限查询Reward列表。 */
  @GetMapping("/coupon-reward-records")
  public ApiResponse<PageResult<Reward>> pageReward(
      HttpServletRequest r,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    verificationAccess.readPermissions(tradingHttp.actor(r), "admin:coupon-reward:read");
    return success(
        r,
        operationsReader.pageReward(
            new RewardFilter(eventType, userId, status), PageQuery.of(page, pageSize)));
  }

  /** 核对所选Distribution详情。 */
  @GetMapping("/coupon-distributions/{id}")
  public ApiResponse<Distribution> readDistribution(HttpServletRequest r, @PathVariable String id) {
    verificationAccess.readPermissions(tradingHttp.actor(r), "admin:coupon:read");
    return success(r, operationsReader.readDistribution(id, false));
  }

  /** 按管理权限查询Distribution列表。 */
  @GetMapping("/coupon-distributions")
  public ApiResponse<PageResult<Distribution>> pageDistribution(
      HttpServletRequest r,
      @RequestParam(required = false) String requestId,
      @RequestParam(required = false) String templateId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    verificationAccess.readPermissions(tradingHttp.actor(r), "admin:coupon:read");
    return success(
        r,
        operationsReader.pageDistribution(
            new DistributionFilter(requestId, templateId), PageQuery.of(page, pageSize)));
  }

  /** 核对所选Issued详情。 */
  @GetMapping("/user-coupons/{id}")
  public ApiResponse<IssuedDetail> readIssued(HttpServletRequest r, @PathVariable String id) {
    verificationAccess.readPermissions(tradingHttp.actor(r), "admin:coupon:read");
    return success(r, operationsReader.issuedDetail(id));
  }

  /** 按管理权限查询Issued列表。 */
  @GetMapping("/user-coupons")
  public ApiResponse<PageResult<Issued>> pageIssued(
      HttpServletRequest r,
      @RequestParam(required = false) String userId,
      @RequestParam(required = false) String issueSourceType,
      @RequestParam(required = false) String issueSourceId,
      @RequestParam(required = false) String templateId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    verificationAccess.readPermissions(tradingHttp.actor(r), "admin:coupon:read");
    return success(
        r,
        operationsReader.pageIssued(
            new IssuedFilter(userId, issueSourceType, issueSourceId, templateId),
            PageQuery.of(page, pageSize)));
  }
}
