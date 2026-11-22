package com.mujian.backend.prop.controller;

import static com.mujian.backend.common.support.PropReviewSources.*;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.prop.dto.*;
import com.mujian.backend.prop.dto.QrExceptionCreateDTO;
import com.mujian.backend.prop.dto.QrIssueDTO;
import com.mujian.backend.prop.dto.QrResolveDTO;
import com.mujian.backend.prop.dto.ReviewDecisionDTO;
import com.mujian.backend.prop.service.PropQrService;
import com.mujian.backend.prop.service.PropReviewQueryService;
import com.mujian.backend.prop.service.PropReviewService;
import com.mujian.backend.prop.vo.CommandResultVO;
import com.mujian.backend.prop.vo.QrExceptionPageVO;
import com.mujian.backend.prop.vo.QrExceptionVO;
import com.mujian.backend.prop.vo.QrVO;
import com.mujian.backend.prop.vo.ReviewResultVO;
import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class PropQrController extends PropEndpointController {
  private final PropReviewService propReviewService;
  private final PropReviewQueryService propReviewQueryService;
  private final PropQrService propQrService;

  /** 装配本业务接口所需服务，公共会话与追踪边界复用父类。 */
  public PropQrController(
      SessionService sessionService,
      PropReviewService propReviewService,
      PropReviewQueryService propReviewQueryService,
      PropQrService propQrService) {
    super(sessionService);
    this.propReviewService = propReviewService;
    this.propReviewQueryService = propReviewQueryService;
    this.propQrService = propQrService;
  }

  /** 将扫码内容解析为当前工厂可访问的二维码。 */
  @PostMapping("/api/me/prop-qr/resolve")
  public ApiResponse<QrVO> resolveOwnPropQr(
      HttpServletRequest httpServletRequest, @RequestBody QrResolveDTO qrResolveDTO) {
    return success(
        httpServletRequest,
        propQrService.resolve(actor(httpServletRequest), qrResolveDTO.qrCode()));
  }

  /** 返回当前工厂二维码详情。 */
  @GetMapping("/api/me/prop-qr/{id}")
  public ApiResponse<QrVO> ownPropQr(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(httpServletRequest, propQrService.read(actor(httpServletRequest), id, false));
  }

  /** 重试当前工厂可恢复的码图生成任务。 */
  @PostMapping("/api/me/prop-qr/{id}/retry-image")
  public ApiResponse<QrVO> retryOwnQrImage(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(httpServletRequest, propQrService.retry(actor(httpServletRequest), id, false));
  }

  /** 返回管理员可访问的二维码详情。 */
  @GetMapping("/api/admin/prop-qr/{id}")
  public ApiResponse<QrVO> adminPropQr(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(httpServletRequest, propQrService.read(actor(httpServletRequest), id, true));
  }

  /** 提交管理员替换二维码操作。 */
  @PostMapping("/api/admin/prop-qr/issue")
  public ApiResponse<CommandResultVO> issuePropQr(
      HttpServletRequest httpServletRequest,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody QrIssueDTO qrIssueDTO) {
    return success(
        httpServletRequest,
        propQrService.issue(actor(httpServletRequest), key, qrIssueDTO, trace(httpServletRequest)));
  }

  /** 由管理员重试可恢复的码图生成任务。 */
  @PostMapping("/api/admin/prop-qr/{id}/retry-image")
  public ApiResponse<QrVO> retryAdminQrImage(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(httpServletRequest, propQrService.retry(actor(httpServletRequest), id, true));
  }

  /** 返回管理端二维码异常列表与统计。 */
  @GetMapping("/api/admin/prop-qr-exceptions")
  public ApiResponse<QrExceptionPageVO> listQrExceptions(
      HttpServletRequest httpServletRequest,
      @ModelAttribute QrExceptionQueryDTO qrExceptionQueryDTO) {
    return success(
        httpServletRequest,
        propReviewQueryService.exceptions(actor(httpServletRequest), qrExceptionQueryDTO));
  }

  /** 提交二维码异常报告及证据。 */
  @PostMapping("/api/admin/prop-qr-exceptions")
  public ApiResponse<CommandResultVO> createQrException(
      HttpServletRequest httpServletRequest,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody QrExceptionCreateDTO qrExceptionCreateDTO) {
    return success(
        httpServletRequest,
        propQrService.report(actor(httpServletRequest), key, qrExceptionCreateDTO));
  }

  /** 返回管理端二维码异常详情。 */
  @GetMapping("/api/admin/prop-qr-exceptions/{id}")
  public ApiResponse<QrExceptionVO> adminQrException(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(
        httpServletRequest, propReviewQueryService.exception(actor(httpServletRequest), id));
  }

  /** 批准二维码异常处理申请。 */
  @PostMapping("/api/admin/prop-qr-exceptions/{id}/approve")
  public ApiResponse<ReviewResultVO> approveQrException(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody ReviewDecisionDTO reviewDecisionDTO) {
    return success(
        httpServletRequest,
        propReviewService.decide(
            actor(httpServletRequest),
            QR_EXCEPTION,
            id,
            key,
            true,
            reviewDecisionDTO,
            trace(httpServletRequest)));
  }

  /** 驳回二维码异常处理申请。 */
  @PostMapping("/api/admin/prop-qr-exceptions/{id}/reject")
  public ApiResponse<ReviewResultVO> rejectQrException(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody ReviewDecisionDTO reviewDecisionDTO) {
    return success(
        httpServletRequest,
        propReviewService.decide(
            actor(httpServletRequest),
            QR_EXCEPTION,
            id,
            key,
            false,
            reviewDecisionDTO,
            trace(httpServletRequest)));
  }
}
