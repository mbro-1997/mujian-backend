package com.mujian.backend.prop.controller;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.prop.dto.*;
import com.mujian.backend.prop.dto.ReviewDecisionDTO;
import com.mujian.backend.prop.dto.VersionReasonDTO;
import com.mujian.backend.prop.service.PropCommandService;
import com.mujian.backend.prop.service.PropReviewQueryService;
import com.mujian.backend.prop.service.PropReviewService;
import com.mujian.backend.prop.vo.CommandResultVO;
import com.mujian.backend.prop.vo.ReviewBaseVO;
import com.mujian.backend.prop.vo.ReviewPageVO;
import com.mujian.backend.prop.vo.ReviewResultVO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PropReviewController extends PropEndpointController {
  private final PropCommandService propCommandService;
  private final PropReviewService propReviewService;
  private final PropReviewQueryService propReviewQueryService;

  /** 装配本业务接口所需服务，公共会话与追踪边界复用父类。 */
  public PropReviewController(
      SessionService sessionService,
      PropCommandService propCommandService,
      PropReviewService propReviewService,
      PropReviewQueryService propReviewQueryService) {
    super(sessionService);
    this.propCommandService = propCommandService;
    this.propReviewService = propReviewService;
    this.propReviewQueryService = propReviewQueryService;
  }

  /** 返回当前工厂的道具与库存申请列表。 */
  @GetMapping("/api/me/prop-reviews")
  public ApiResponse<ReviewPageVO> listOwnPropReviews(
      HttpServletRequest httpServletRequest, @ModelAttribute ReviewQueryDTO reviewQueryDTO) {
    return success(
        httpServletRequest,
        propReviewQueryService.list(actor(httpServletRequest), reviewQueryDTO, false));
  }

  /** 返回当前工厂申请详情及历史快照。 */
  @GetMapping("/api/me/prop-reviews/{sourceType}/{id}")
  public ApiResponse<ReviewBaseVO> ownPropReview(
      HttpServletRequest httpServletRequest,
      @PathVariable String sourceType,
      @PathVariable String id) {
    return success(
        httpServletRequest,
        propReviewQueryService.detail(actor(httpServletRequest), sourceType, id, false));
  }

  /** 撤回当前工厂尚未处理的申请。 */
  @PostMapping("/api/me/prop-reviews/{sourceType}/{id}/cancel")
  public ApiResponse<CommandResultVO> cancelPropReview(
      HttpServletRequest httpServletRequest,
      @PathVariable String sourceType,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody VersionReasonDTO versionReasonDTO) {
    return success(
        httpServletRequest,
        propCommandService.cancel(
            actor(httpServletRequest), sourceType, id, key, versionReasonDTO));
  }

  /** 返回管理端申请列表及待审统计。 */
  @GetMapping("/api/admin/prop-reviews")
  public ApiResponse<ReviewPageVO> listAdminPropReviews(
      HttpServletRequest httpServletRequest, @ModelAttribute ReviewQueryDTO reviewQueryDTO) {
    return success(
        httpServletRequest,
        propReviewQueryService.list(actor(httpServletRequest), reviewQueryDTO, true));
  }

  /** 返回管理端审核详情及获授权的材料。 */
  @GetMapping("/api/admin/prop-reviews/{sourceType}/{id}")
  public ApiResponse<ReviewBaseVO> adminPropReview(
      HttpServletRequest httpServletRequest,
      @PathVariable String sourceType,
      @PathVariable String id) {
    return success(
        httpServletRequest,
        propReviewQueryService.detail(actor(httpServletRequest), sourceType, id, true));
  }

  /** 执行道具或库存申请批准操作。 */
  @PostMapping("/api/admin/prop-reviews/{sourceType}/{id}/approve")
  public ApiResponse<ReviewResultVO> approvePropReview(
      HttpServletRequest httpServletRequest,
      @PathVariable String sourceType,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody ReviewDecisionDTO reviewDecisionDTO) {
    return success(
        httpServletRequest,
        propReviewService.decide(
            actor(httpServletRequest),
            sourceType,
            id,
            key,
            true,
            reviewDecisionDTO,
            trace(httpServletRequest)));
  }

  /** 执行道具或库存申请驳回操作。 */
  @PostMapping("/api/admin/prop-reviews/{sourceType}/{id}/reject")
  public ApiResponse<ReviewResultVO> rejectPropReview(
      HttpServletRequest httpServletRequest,
      @PathVariable String sourceType,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody ReviewDecisionDTO reviewDecisionDTO) {
    return success(
        httpServletRequest,
        propReviewService.decide(
            actor(httpServletRequest),
            sourceType,
            id,
            key,
            false,
            reviewDecisionDTO,
            trace(httpServletRequest)));
  }
}
