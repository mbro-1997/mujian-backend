package com.mujian.backend.casework.controller;

import com.mujian.backend.admin.service.CommandAuditService.Command;
import com.mujian.backend.casework.service.CustomerCaseService;
import com.mujian.backend.casework.vo.ServiceCaseViews.*;
import com.mujian.backend.casework.dto.*;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.coupon.vo.OperationsViews.Issued;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.service.TradingHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class CustomerCaseController extends BaseController {
  private final CustomerCaseService customerCaseService;
  private final VerificationAccess verificationAccess;
  private final TradingHttp tradingHttp;

  /** 共用案件读模型及原平台权限。 */
  public CustomerCaseController(
      CustomerCaseService customerCaseService,
      VerificationAccess verificationAccess,
      TradingHttp tradingHttp) {
    this.customerCaseService = customerCaseService;
    this.verificationAccess = verificationAccess;
    this.tradingHttp = tradingHttp;
  }

  /** 构造当前操作回执作用域。 */
  private Command command(HttpServletRequest r) {
    return new Command(
        tradingHttp.actor(r), r.getHeader("Idempotency-Key"), (String) r.getAttribute("traceId"));
  }

  /** 统一案件列表。 */
  @GetMapping("/cases")
  public ApiResponse<PageResult<Row>> list(
      HttpServletRequest r,
      @RequestParam(required = false) String caseType,
      @RequestParam(required = false) String caseStatus,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    verificationAccess.readPermissions(tradingHttp.actor(r), "admin:case:list");
    return success(
        r,
        customerCaseService.page(
            new ServiceCaseQueryDTO(caseType, caseStatus, keyword), PageQuery.of(page, pageSize)));
  }

  /** 有详情权限才返回诉求及解决说明。 */
  @GetMapping("/cases/{id}")
  public ApiResponse<Detail> detail(HttpServletRequest r, @PathVariable String id) {
    verificationAccess.readPermissions(tradingHttp.actor(r), "admin:case:view");
    return success(r, customerCaseService.detail(id));
  }

  /** 人工登记客服案件。 */
  @PostMapping("/customer-service-cases")
  public ApiResponse<Detail> create(HttpServletRequest r, @RequestBody CreateServiceCaseDTO input) {
    return success(r, customerCaseService.create(command(r), input));
  }

  /** 记录经核对的解决结果。 */
  @PostMapping("/customer-service-cases/{id}/resolve")
  public ApiResponse<Detail> resolve(
      HttpServletRequest r, @PathVariable String id, @RequestBody ResolveServiceCaseDTO input) {
    return success(r, customerCaseService.resolve(command(r), id, input));
  }

  /** 与退款独立的手动补偿。 */
  @PostMapping("/cases/{id}/compensation-coupons")
  public ApiResponse<Issued> compensate(
      HttpServletRequest r, @PathVariable String id, @RequestBody CaseCompensationDTO input) {
    return success(r, customerCaseService.compensate(command(r), id, input));
  }
}
