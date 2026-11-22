package com.mujian.backend.realname.controller;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.material.service.*;
import com.mujian.backend.material.service.VerificationMaterials.Receipt;
import com.mujian.backend.material.vo.RealnameMaterialReceiptVO;
import com.mujian.backend.realname.service.*;
import com.mujian.backend.verification.dto.VerificationListQueryDTO;
import jakarta.servlet.http.*;
import jakarta.servlet.http.Part;
import java.util.*;
import java.util.HashSet;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class RealnameController extends com.mujian.backend.common.base.BaseController {
  private final RealnameQueryService realnameQueryService;
  private final RealnameSubmissionService realnameSubmissionService;
  private final RealnameReviewService realnameReviewService;
  private final VerificationMaterials verificationMaterials;
  private final VerificationSensitiveViews verificationSensitiveViews;
  private final SessionService sessionService;

  /** 装配本业务所需依赖，复用现有公共能力。 */
  public RealnameController(
      RealnameQueryService realnameQueryService,
      RealnameSubmissionService realnameSubmissionService,
      RealnameReviewService realnameReviewService,
      VerificationMaterials verificationMaterials,
      VerificationSensitiveViews verificationSensitiveViews,
      SessionService sessionService) {
    this.realnameQueryService = realnameQueryService;
    this.realnameSubmissionService = realnameSubmissionService;
    this.realnameReviewService = realnameReviewService;
    this.verificationMaterials = verificationMaterials;
    this.verificationSensitiveViews = verificationSensitiveViews;
    this.sessionService = sessionService;
  }

  /** 从当前会话取得操作者，拒绝未授权访问。 */
  private String actor(HttpServletRequest httpServletRequest) {
    return sessionService.user(sessionService.from(httpServletRequest));
  }

  /** 读取当前生效记录并组装业务上下文。 */
  @GetMapping("/api/me/realname-verifications/current")
  public ApiResponse<com.mujian.backend.realname.vo.RealnameCurrentVO> current(
      HttpServletRequest httpServletRequest) {
    return success(httpServletRequest, realnameQueryService.current(actor(httpServletRequest)));
  }

  /** 按原请求编号恢复已提交结果并核对访问权限。 */
  @GetMapping("/api/me/realname-verifications/by-request/{key}")
  public ApiResponse<com.mujian.backend.realname.vo.RealnameApplicationVO> recover(
      HttpServletRequest httpServletRequest, @PathVariable String key) {
    return success(
        httpServletRequest, realnameQueryService.recover(actor(httpServletRequest), key));
  }

  /** 核对幂等与待审状态，在原事务内保存认证申请。 */
  @PostMapping("/api/me/realname-verifications")
  public ApiResponse<
          com.mujian.backend.verification.vo.VerificationSubmitVO<
              com.mujian.backend.realname.vo.RealnameApplicationVO>>
      submit(
          HttpServletRequest httpServletRequest,
          @RequestHeader("Idempotency-Key") String key,
          @RequestBody RealnameSubmissionService.SubmitRequest submitRequest) {
    return success(
        httpServletRequest,
        realnameSubmissionService.submit(actor(httpServletRequest), key, submitRequest));
  }

  /** 校验用途、身份和文件后复用私有材料上传。 */
  @PostMapping(value = "/api/me/realname-materials", consumes = "multipart/form-data")
  public ApiResponse<com.mujian.backend.material.vo.RealnameMaterialReceiptVO> upload(
      HttpServletRequest httpServletRequest,
      @RequestParam("file") MultipartFile multipartFile,
      @RequestParam String consentAccepted,
      @RequestParam String consentVersion,
      @RequestParam String side)
      throws java.io.IOException, jakarta.servlet.ServletException {
    if (httpServletRequest.getQueryString() != null) throw ApiFailure.invalid();
    HashSet<String> names = new HashSet<String>();
    for (Part part : httpServletRequest.getParts()) {
      if (!names.add(part.getName())) throw ApiFailure.invalid();
    }
    if (!names.equals(Set.of("file", "consentAccepted", "consentVersion", "side")))
      throw ApiFailure.invalid();
    if (!"true".equals(consentAccepted)) throw ApiFailure.invalid();
    if (!Set.of("front", "back").contains(side)) throw ApiFailure.invalid();
    if (multipartFile.getSize() > 5242880)
      throw new ApiFailure(400, D2Codes.CODE_INVALID_ARGUMENT, D2Codes.CODE_MATERIAL_TOO_LARGE);
    Receipt receipt =
        verificationMaterials.upload(
            actor(httpServletRequest),
            "realname_" + side,
            consentVersion,
            true,
            multipartFile.getBytes());
    RealnameMaterialReceiptVO realnameMaterialReceiptVO =
        new RealnameMaterialReceiptVO()
            .uploadId(receipt.uploadId())
            .expiresAt(time(java.time.LocalDateTime.ofInstant(receipt.acceptUntil(), ZONE)))
            .side(side);
    return success(httpServletRequest, realnameMaterialReceiptVO);
  }

  /** 校验筛选后执行数据库分页，仅组装列表所需字段。 */
  @GetMapping("/api/admin/realname-verifications")
  public ApiResponse<PageResult<com.mujian.backend.realname.vo.RealnameListItemVO>> list(
      HttpServletRequest httpServletRequest,
      @ModelAttribute VerificationListQueryDTO verificationListQueryDTO) {
    return success(
        httpServletRequest,
        realnameQueryService.list(actor(httpServletRequest), verificationListQueryDTO));
  }

  /** 检查审核权限后返回申请详情和工单信息。 */
  @GetMapping("/api/admin/realname-verifications/{id}")
  public ApiResponse<com.mujian.backend.realname.vo.RealnameDetailVO> detail(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(httpServletRequest, realnameQueryService.detail(actor(httpServletRequest), id));
  }

  /** 校验敏感查看权限并记录审计后返回材料。 */
  @PostMapping("/api/admin/realname-verifications/{id}/sensitive-view")
  public ApiResponse<com.mujian.backend.material.vo.RealnameSensitiveVO> sensitiveView(
      HttpServletRequest httpServletRequest, @PathVariable String id) {
    return success(
        httpServletRequest,
        verificationSensitiveViews.realname(
            actor(httpServletRequest), id, (String) httpServletRequest.getAttribute("traceId")));
  }

  /** 按当前版本批准认证申请并返回幂等结果。 */
  @PostMapping("/api/admin/realname-verifications/{id}/approve")
  public ApiResponse<com.mujian.backend.verification.vo.VerificationReviewResultVO> approve(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody RealnameReviewService.ReviewCommand reviewCommand) {
    return success(
        httpServletRequest,
        realnameReviewService.review(
            actor(httpServletRequest),
            id,
            key,
            true,
            reviewCommand,
            (String) httpServletRequest.getAttribute("traceId")));
  }

  /** 按当前版本驳回认证申请并保留审核备注。 */
  @PostMapping("/api/admin/realname-verifications/{id}/reject")
  public ApiResponse<com.mujian.backend.verification.vo.VerificationReviewResultVO> reject(
      HttpServletRequest httpServletRequest,
      @PathVariable String id,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody RealnameReviewService.ReviewCommand reviewCommand) {
    return success(
        httpServletRequest,
        realnameReviewService.review(
            actor(httpServletRequest),
            id,
            key,
            false,
            reviewCommand,
            (String) httpServletRequest.getAttribute("traceId")));
  }
}
