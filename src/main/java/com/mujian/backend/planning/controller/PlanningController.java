package com.mujian.backend.planning.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.planning.dto.*;
import com.mujian.backend.planning.dto.PlanningDtos.*;
import com.mujian.backend.planning.service.*;
import com.mujian.backend.planning.vo.PlanningViews.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class PlanningController extends BaseController {
  private final ChecklistService checklistService;
  private final PlanningOperations planningOperations;
  private final PlanningHttp planningHttp;

  /** 装配清单上下文及恢复服务。 */
  public PlanningController(
      ChecklistService checklistService,
      PlanningOperations planningOperations,
      PlanningHttp planningHttp) {
    this.checklistService = checklistService;
    this.planningOperations = planningOperations;
    this.planningHttp = planningHttp;
  }

  /** 获取当前清单及名额。 */
  @GetMapping("/api/me/planning-context")
  public ApiResponse<Context> context(HttpServletRequest httpServletRequestRequest) {
    return success(
        httpServletRequestRequest,
        checklistService.context(planningHttp.actor(httpServletRequestRequest)));
  }

  /** 查询原请求事实，不触发新业务。 */
  @GetMapping("/api/me/planning-operations/by-request/{requestId}")
  public ApiResponse<Receipt> recover(
      HttpServletRequest httpServletRequestRequest, @PathVariable String requestId) {
    return success(
        httpServletRequestRequest,
        planningOperations.recover(planningHttp.actor(httpServletRequestRequest), requestId));
  }
}
