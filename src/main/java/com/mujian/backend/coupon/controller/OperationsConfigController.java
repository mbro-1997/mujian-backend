package com.mujian.backend.coupon.controller;

import com.mujian.backend.admin.service.CommandAuditService.Command;
import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.coupon.dto.OperationsInput;
import com.mujian.backend.coupon.service.OperationsConfiguration;
import com.mujian.backend.coupon.vo.OperationsViews.*;
import com.mujian.backend.order.service.TradingHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class OperationsConfigController extends BaseController {
  private final OperationsConfiguration operationsConfiguration;
  private final TradingHttp tradingHttp;

  /** 复用平台用户会话与追踪编号。 */
  public OperationsConfigController(
      OperationsConfiguration operationsConfiguration, TradingHttp tradingHttp) {
    this.operationsConfiguration = operationsConfiguration;
    this.tradingHttp = tradingHttp;
  }

  /** 请求键与网络追踪号分别传入。 */
  private Command command(HttpServletRequest r) {
    return new Command(
        tradingHttp.actor(r), r.getHeader("Idempotency-Key"), (String) r.getAttribute("traceId"));
  }

  /** create Template，操作者与回执绑定。 */
  @PostMapping("/coupon-templates")
  public ApiResponse<Template> createTemplate(
      HttpServletRequest r, @RequestBody OperationsInput.Template input) {
    return success(r, operationsConfiguration.template(command(r), null, input));
  }

  /** save Template，操作者与回执绑定。 */
  @PostMapping("/coupon-templates/{id}/save")
  public ApiResponse<Template> saveTemplate(
      HttpServletRequest r, @PathVariable String id, @RequestBody OperationsInput.Template input) {
    return success(r, operationsConfiguration.template(command(r), id, input));
  }

  /** publish Template，操作者与回执绑定。 */
  @PostMapping("/coupon-templates/{id}/publish")
  public ApiResponse<Template> publishTemplate(
      HttpServletRequest r, @PathVariable String id, @RequestBody OperationsInput.Snapshot input) {
    return success(r, operationsConfiguration.templateState(command(r), id, "publish", input));
  }

  /** pause Template，操作者与回执绑定。 */
  @PostMapping("/coupon-templates/{id}/pause")
  public ApiResponse<Template> pauseTemplate(
      HttpServletRequest r, @PathVariable String id, @RequestBody OperationsInput.Snapshot input) {
    return success(r, operationsConfiguration.templateState(command(r), id, "pause", input));
  }

  /** resume Template，操作者与回执绑定。 */
  @PostMapping("/coupon-templates/{id}/resume")
  public ApiResponse<Template> resumeTemplate(
      HttpServletRequest r, @PathVariable String id, @RequestBody OperationsInput.Snapshot input) {
    return success(r, operationsConfiguration.templateState(command(r), id, "resume", input));
  }

  /** end Template，操作者与回执绑定。 */
  @PostMapping("/coupon-templates/{id}/end")
  public ApiResponse<Template> endTemplate(
      HttpServletRequest r, @PathVariable String id, @RequestBody OperationsInput.Snapshot input) {
    return success(r, operationsConfiguration.templateState(command(r), id, "end", input));
  }

  /** create Activity，操作者与回执绑定。 */
  @PostMapping("/coupon-activities")
  public ApiResponse<Activity> createActivity(
      HttpServletRequest r, @RequestBody OperationsInput.Activity input) {
    return success(r, operationsConfiguration.activity(command(r), null, input));
  }

  /** save Activity，操作者与回执绑定。 */
  @PostMapping("/coupon-activities/{id}/save")
  public ApiResponse<Activity> saveActivity(
      HttpServletRequest r, @PathVariable String id, @RequestBody OperationsInput.Activity input) {
    return success(r, operationsConfiguration.activity(command(r), id, input));
  }

  /** publish Activity，操作者与回执绑定。 */
  @PostMapping("/coupon-activities/{id}/publish")
  public ApiResponse<Activity> publishActivity(
      HttpServletRequest r, @PathVariable String id, @RequestBody OperationsInput.Version input) {
    return success(r, operationsConfiguration.activityState(command(r), id, "publish", input));
  }

  /** cancel Activity，操作者与回执绑定。 */
  @PostMapping("/coupon-activities/{id}/cancel")
  public ApiResponse<Activity> cancelActivity(
      HttpServletRequest r, @PathVariable String id, @RequestBody OperationsInput.Version input) {
    return success(r, operationsConfiguration.activityState(command(r), id, "cancel", input));
  }

  /** create Rule，操作者与回执绑定。 */
  @PostMapping("/coupon-reward-rules")
  public ApiResponse<Rule> createRule(
      HttpServletRequest r, @RequestBody OperationsInput.Rule input) {
    return success(r, operationsConfiguration.rule(command(r), null, input));
  }

  /** save Rule，操作者与回执绑定。 */
  @PostMapping("/coupon-reward-rules/{id}/save")
  public ApiResponse<Rule> saveRule(
      HttpServletRequest r, @PathVariable String id, @RequestBody OperationsInput.Rule input) {
    return success(r, operationsConfiguration.rule(command(r), id, input));
  }

  /** activate Rule，操作者与回执绑定。 */
  @PostMapping("/coupon-reward-rules/{id}/activate")
  public ApiResponse<Rule> activateRule(
      HttpServletRequest r, @PathVariable String id, @RequestBody OperationsInput.Snapshot input) {
    return success(r, operationsConfiguration.ruleState(command(r), id, "activate", input));
  }

  /** end Rule，操作者与回执绑定。 */
  @PostMapping("/coupon-reward-rules/{id}/end")
  public ApiResponse<Rule> endRule(
      HttpServletRequest r, @PathVariable String id, @RequestBody OperationsInput.Snapshot input) {
    return success(r, operationsConfiguration.ruleState(command(r), id, "end", input));
  }
}
