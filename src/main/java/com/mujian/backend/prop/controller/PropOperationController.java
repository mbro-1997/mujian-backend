package com.mujian.backend.prop.controller;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.prop.dto.*;
import com.mujian.backend.prop.service.PropRequests;
import com.mujian.backend.prop.service.PropReviewService;
import com.mujian.backend.prop.vo.CommandResultVO;
import com.mujian.backend.prop.vo.ReviewResultVO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PropOperationController extends PropEndpointController {
  private final PropReviewService propReviewService;
  private final PropRequests propRequests;

  /** 装配本业务接口所需服务，公共会话与追踪边界复用父类。 */
  public PropOperationController(
      SessionService sessionService,
      PropReviewService propReviewService,
      PropRequests propRequests) {
    super(sessionService);
    this.propReviewService = propReviewService;
    this.propRequests = propRequests;
  }

  /** 恢复当前工厂原操作结果并核对请求指定的操作类型。 */
  @GetMapping("/api/me/prop-operations/by-request/{requestId}")
  public ApiResponse<CommandResultVO> recoverOwnPropOperation(
      HttpServletRequest httpServletRequest,
      @PathVariable String requestId,
      @RequestParam(required = false) String operation) {
    return success(
        httpServletRequest,
        propRequests.recover(actor(httpServletRequest), requestId, operation, false));
  }

  /** 恢复管理员已执行道具操作的原结果。 */
  @GetMapping("/api/admin/prop-operations/by-request/{requestId}")
  public ApiResponse<CommandResultVO> recoverAdminPropOperation(
      HttpServletRequest httpServletRequest,
      @PathVariable String requestId,
      @RequestParam(required = false) String operation) {
    return success(
        httpServletRequest,
        propRequests.recover(actor(httpServletRequest), requestId, operation, true));
  }

  /** 恢复管理员审核请求的原结果。 */
  @GetMapping("/api/admin/prop-review-actions/by-request/{requestId}")
  public ApiResponse<ReviewResultVO> recoverPropReviewAction(
      HttpServletRequest httpServletRequest, @PathVariable String requestId) {
    return success(
        httpServletRequest, propReviewService.recover(actor(httpServletRequest), requestId));
  }
}
