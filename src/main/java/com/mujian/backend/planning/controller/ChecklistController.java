package com.mujian.backend.planning.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.PlanningOperation;
import com.mujian.backend.planning.dto.*;
import com.mujian.backend.planning.dto.PlanningDtos.*;
import com.mujian.backend.planning.service.*;
import com.mujian.backend.planning.service.ChecklistService;
import com.mujian.backend.planning.vo.PlanningViews.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChecklistController extends BaseController {
  private final ChecklistService checklistService;
  private final PlanningHttp planningHttp;

  /** 装配领域服务和统一会话适配。 */
  public ChecklistController(ChecklistService checklistService, PlanningHttp planningHttp) {
    this.checklistService = checklistService;
    this.planningHttp = planningHttp;
  }

  /** 读取本人分页列表。 */
  @GetMapping("/api/me/checklists")
  public ApiResponse<PageResult<ChecklistSummary>> list(
      HttpServletRequest httpServletRequestRequest,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(defaultValue = "active") String scope) {
    return success(
        httpServletRequestRequest,
        checklistService.list(
            new PlanningQuery(
                planningHttp.actor(httpServletRequestRequest),
                scope,
                PageQuery.of(page, pageSize))));
  }

  /** 读取本人详情。 */
  @GetMapping("/api/me/checklists/{id}")
  public ApiResponse<ChecklistDetail> detail(
      HttpServletRequest httpServletRequestRequest, @PathVariable String id) {
    return success(
        httpServletRequestRequest,
        checklistService.detail(planningHttp.actor(httpServletRequestRequest), id));
  }

  /** 按租期手工新建清单或复制方案道具。 */
  @PostMapping("/api/me/checklists")
  public ApiResponse<Receipt> createChecklist(
      HttpServletRequest httpServletRequestRequest,
      @RequestBody CreateChecklist createChecklistInput) {
    return success(
        httpServletRequestRequest,
        checklistService.create(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.createChecklist),
            createChecklistInput));
  }

  /** 将指定清单设为唯一当前清单，挂起原当前清单。 */
  @PostMapping("/api/me/checklists/{id}/activate")
  public ApiResponse<Receipt> activateChecklist(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @RequestBody ExpectedVersion expectedVersion) {
    return success(
        httpServletRequestRequest,
        checklistService.activate(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.activateChecklist),
            id,
            expectedVersion));
  }

  /** 向清单加入道具或设置已有道具的总数量。 */
  @PostMapping("/api/me/checklists/{id}/items")
  public ApiResponse<Receipt> setChecklistItem(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @RequestBody ItemSet itemSet) {
    return success(
        httpServletRequestRequest,
        checklistService.setItem(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.setChecklistItem),
            id,
            itemSet));
  }

  /** 更新清单指定明细的数量与备注。 */
  @PostMapping("/api/me/checklists/{id}/items/{itemId}/update")
  public ApiResponse<Receipt> updateChecklistItem(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @PathVariable String itemId,
      @RequestBody ChecklistItemUpdate checklistItemUpdate) {
    return success(
        httpServletRequestRequest,
        checklistService.updateItem(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.updateChecklistItem),
            id,
            itemId,
            checklistItemUpdate));
  }

  /** 同事务保存清单全部明细，禁止部分成功。 */
  @PostMapping("/api/me/checklists/{id}/save")
  public ApiResponse<Receipt> saveChecklist(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @RequestBody SaveChecklist saveChecklistInput) {
    return success(
        httpServletRequestRequest,
        checklistService.save(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.saveChecklist),
            id,
            saveChecklistInput));
  }

  /** 移除清单指定明细，不触发库存占用写入。 */
  @PostMapping("/api/me/checklists/{id}/items/{itemId}/remove")
  public ApiResponse<Receipt> removeChecklistItem(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @PathVariable String itemId,
      @RequestBody ExpectedVersion expectedVersion) {
    return success(
        httpServletRequestRequest,
        checklistService.removeItem(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.removeChecklistItem),
            id,
            itemId,
            expectedVersion));
  }
}
