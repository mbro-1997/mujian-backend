package com.mujian.backend.delivery.controller;

import com.mujian.backend.casework.service.RiskContacts;
import com.mujian.backend.casework.dto.CaseContact;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.api.PageResult;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.ReturnCheckStatus;
import com.mujian.backend.delivery.dto.MonitoringQuery;
import com.mujian.backend.delivery.service.FulfillmentMonitoring;
import com.mujian.backend.delivery.vo.MonitoringViews;
import com.mujian.backend.order.service.TradingHttp;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
public class FulfillmentMonitoringController extends BaseController {
  private final FulfillmentMonitoring fulfillmentMonitoring;
  private final RiskContacts riskContacts;
  private final TradingHttp tradingHttp;

  /** 装配履约监控查询所需依赖。 */
  public FulfillmentMonitoringController(
      FulfillmentMonitoring fulfillmentMonitoring, TradingHttp tradingHttp, RiskContacts riskContacts) {
    this.riskContacts = riskContacts;
    this.fulfillmentMonitoring = fulfillmentMonitoring;
    this.tradingHttp = tradingHttp;
  }

  /** 读取风险对应事项的联系历史与合法引用。 */
  @GetMapping("/api/admin/fulfillment-risks/{riskId}")
  public ApiResponse<RiskContacts.Detail> detail(HttpServletRequest request, @PathVariable String riskId) {
    return success(request,riskContacts.detail(tradingHttp.actor(request),riskId));
  }

  /** 以原事项版本提交线下沟通，保留原号用于网络恢复。 */
  @PostMapping("/api/admin/cases/{caseId}/contact-records")
  public ApiResponse<CaseContact.Result> append(HttpServletRequest request, @PathVariable String caseId,
      @RequestBody CaseContact.Input input) {
    return success(request,riskContacts.append(tradingHttp.command(request,TradingOperation.case_contact_record),
        caseId,input,(String)request.getAttribute("traceId")));
  }

  /** 按授权工厂及时间筛选待处理的归还核验预警。 */
  @GetMapping("/api/admin/return-checks")
  public ApiResponse<PageResult<MonitoringViews.Warning>> warnings(
      HttpServletRequest request,
      @RequestParam(required = false) ReturnCheckStatus status,
      @RequestParam(required = false) String factoryId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) LocalDate autoCloseFrom,
      @RequestParam(required = false) LocalDate autoCloseTo,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        fulfillmentMonitoring.warnings(
            tradingHttp.actor(request),
            new MonitoringQuery.Warnings(
                status,
                factoryId,
                keyword,
                autoCloseFrom,
                autoCloseTo,
                PageQuery.of(page, pageSize))));
  }

  /** 按来源和实际受影响订单筛选库存风险。 */
  @GetMapping("/api/admin/fulfillment-risks")
  public ApiResponse<PageResult<MonitoringViews.Risk>> risks(
      HttpServletRequest request,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String sourceOrderId,
      @RequestParam(required = false) String affectedOrderId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        fulfillmentMonitoring.risks(
            tradingHttp.actor(request),
            new MonitoringQuery.Risks(
                status, sourceOrderId, affectedOrderId, PageQuery.of(page, pageSize))));
  }
}
