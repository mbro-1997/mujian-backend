package com.mujian.backend.scheme.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.PlanningOperation;
import com.mujian.backend.planning.dto.*;
import com.mujian.backend.planning.dto.PlanningDtos.*;
import com.mujian.backend.planning.service.*;
import com.mujian.backend.planning.vo.PlanningViews.*;
import com.mujian.backend.scheme.dto.SchemeDtos.*;
import com.mujian.backend.scheme.service.SchemeService;
import com.mujian.backend.scheme.vo.SchemeViews.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class SchemeController extends BaseController {
  private final SchemeService schemeService;
  private final PlanningHttp planningHttp;

  /** 装配领域服务和统一会话适配。 */
  public SchemeController(SchemeService schemeService, PlanningHttp planningHttp) {
    this.schemeService = schemeService;
    this.planningHttp = planningHttp;
  }

  /** 读取本人分页列表。 */
  @GetMapping("/api/me/schemes")
  public ApiResponse<PageResult<SchemeSummary>> list(
      HttpServletRequest httpServletRequestRequest,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(defaultValue = "all") String status) {
    return success(
        httpServletRequestRequest,
        schemeService.list(
            new PlanningQuery(
                planningHttp.actor(httpServletRequestRequest),
                status,
                PageQuery.of(page, pageSize))));
  }

  /** 读取本人详情。 */
  @GetMapping("/api/me/schemes/{id}")
  public ApiResponse<SchemeDetail> detail(
      HttpServletRequest httpServletRequestRequest, @PathVariable String id) {
    return success(
        httpServletRequestRequest,
        schemeService.detail(planningHttp.actor(httpServletRequestRequest), id));
  }

  /** 新建本人方案，返回可恢复的创建回执。 */
  @PostMapping("/api/me/schemes")
  public ApiResponse<Receipt> createScheme(
      HttpServletRequest httpServletRequestRequest, @RequestBody CreateScheme createSchemeInput) {
    return success(
        httpServletRequestRequest,
        schemeService.create(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.createScheme),
            createSchemeInput));
  }

  /** 同事务保存方案名称、简介和完整道具明细。 */
  @PostMapping("/api/me/schemes/{id}/update")
  public ApiResponse<Receipt> updateScheme(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @RequestBody UpdateScheme updateSchemeInput) {
    return success(
        httpServletRequestRequest,
        schemeService.update(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.updateScheme),
            id,
            updateSchemeInput));
  }

  /** 将方案归档为永久只读，返回原操作回执。 */
  @PostMapping("/api/me/schemes/{id}/archive")
  public ApiResponse<Receipt> archiveScheme(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @RequestBody ExpectedVersion expectedVersion) {
    return success(
        httpServletRequestRequest,
        schemeService.archive(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.archiveScheme),
            id,
            expectedVersion));
  }

  /** 将道具加入方案或设置其数量与备注。 */
  @PostMapping("/api/me/schemes/{id}/items")
  public ApiResponse<Receipt> setSchemeItem(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @RequestBody ItemSet itemSet) {
    return success(
        httpServletRequestRequest,
        schemeService.setItem(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.setSchemeItem),
            id,
            itemSet));
  }

  /** 移除方案中的指定明细并保持其他道具不变。 */
  @PostMapping("/api/me/schemes/{id}/items/{itemId}/remove")
  public ApiResponse<Receipt> removeSchemeItem(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @PathVariable String itemId,
      @RequestBody ExpectedVersion expectedVersion) {
    return success(
        httpServletRequestRequest,
        schemeService.removeItem(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.removeSchemeItem),
            id,
            itemId,
            expectedVersion));
  }

  /** 按提交顺序完整重排方案道具。 */
  @PostMapping("/api/me/schemes/{id}/items/reorder")
  public ApiResponse<Receipt> reorderSchemeItems(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @RequestBody ReorderScheme reorderScheme) {
    return success(
        httpServletRequestRequest,
        schemeService.reorder(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.reorderSchemeItems),
            id,
            reorderScheme));
  }

  /** 启用方案分享并保存对外价格口径。 */
  @PostMapping("/api/me/schemes/{id}/share")
  public ApiResponse<ShareIssued> setSchemeShare(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @RequestBody ShareSet shareSet) {
    return success(
        httpServletRequestRequest,
        schemeService.share(
            planningHttp.command(httpServletRequestRequest, PlanningOperation.setSchemeShare),
            id,
            shareSet,
            false));
  }

  /** 关闭匿名分享，使原链接立即失效。 */
  @PostMapping("/api/me/schemes/{id}/share/disable")
  public ApiResponse<Receipt> disableSchemeShare(
      HttpServletRequest httpServletRequestRequest,
      @PathVariable String id,
      @RequestBody ExpectedVersion expectedVersionInput) {
    return success(
        httpServletRequestRequest,
        schemeService
            .share(
                planningHttp.command(
                    httpServletRequestRequest, PlanningOperation.disableSchemeShare),
                id,
                new ShareSet(expectedVersionInput.expectedVersion(), false),
                true)
            .receipt());
  }
}
