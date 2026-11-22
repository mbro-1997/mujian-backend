package com.mujian.backend.delivery.controller;

import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.delivery.dto.*;
import com.mujian.backend.delivery.service.*;
import com.mujian.backend.delivery.vo.*;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.order.vo.TradingReceipt;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** 私有订单证据按角色入口鉴权并通过所属节点绑定。 */
@RestController
public class EvidenceController extends BaseController {
  private final OrderEvidence orderEvidence;
  private final TradingHttp tradingHttp;

  /** 复用已认证会话和已有交易请求摘要。 */
  public EvidenceController(OrderEvidence orderEvidence, TradingHttp tradingHttp) {
    this.orderEvidence = orderEvidence;
    this.tradingHttp = tradingHttp;
  }

  /** multipart仅接受file和node各一次，禁止任意附加字段/多文件。 */
  private byte[] bytes(HttpServletRequest request, MultipartFile file)
      throws IOException, jakarta.servlet.ServletException {
    Set<String> names = new HashSet<>();
    for (Part part : request.getParts()) {
      if (!Set.of("file", "node").contains(part.getName())
          || !names.add(part.getName())
          || (part.getSubmittedFileName() != null) != part.getName().equals("file"))
        throw ApiFailure.invalid();
    }
    if (names.size() != 2 || file.isEmpty()) throw ApiFailure.invalid();
    if (file.getSize() > 5242880)
      throw new ApiFailure(400, D2Codes.CODE_INVALID_ARGUMENT, FulfillmentReason.MATERIAL_TOO_LARGE.name());
    return file.getBytes();
  }

  /** factoryEvidence。 */
  @GetMapping("/api/me/factory-orders/{orderId}/evidence")
  public ApiResponse<EvidenceViews.Page> factoryEvidence(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestParam(required = false) EvidenceNode node,
      @RequestParam(required = false) String statementId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        orderEvidence.page(
            tradingHttp.actor(request),
            orderId,
            FulfillmentRole.factory,
            node,
            statementId,
            PageQuery.of(page, pageSize)));
  }

  /** factoryAttachEvidence。 */
  @PostMapping("/api/me/factory-orders/{orderId}/evidence")
  public ApiResponse<TradingReceipt> factoryAttachEvidence(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody EvidenceDtos.Attach input) {
    return success(
        request,
        orderEvidence.attach(
            tradingHttp.command(request, TradingOperation.factoryAttachEvidence),
            orderId,
            FulfillmentRole.factory,
            input));
  }

  /** factoryEvidenceView。 */
  @PostMapping("/api/me/factory-orders/{orderId}/evidence/{evidenceId}/view")
  public ApiResponse<EvidenceViews.View> factoryEvidenceView(
      HttpServletRequest request, @PathVariable String orderId, @PathVariable String evidenceId) {
    return success(
        request,
        orderEvidence.view(
            tradingHttp.actor(request), orderId, FulfillmentRole.factory, evidenceId));
  }

  /** buyerEvidence。 */
  @GetMapping("/api/me/rental-orders/{orderId}/evidence")
  public ApiResponse<EvidenceViews.Page> buyerEvidence(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestParam(required = false) EvidenceNode node,
      @RequestParam(required = false) String statementId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        orderEvidence.page(
            tradingHttp.actor(request),
            orderId,
            FulfillmentRole.buyer,
            node,
            statementId,
            PageQuery.of(page, pageSize)));
  }

  /** buyerAttachEvidence。 */
  @PostMapping("/api/me/rental-orders/{orderId}/evidence")
  public ApiResponse<TradingReceipt> buyerAttachEvidence(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody EvidenceDtos.Attach input) {
    return success(
        request,
        orderEvidence.attach(
            tradingHttp.command(request, TradingOperation.buyerAttachEvidence),
            orderId,
            FulfillmentRole.buyer,
            input));
  }

  /** buyerEvidenceView。 */
  @PostMapping("/api/me/rental-orders/{orderId}/evidence/{evidenceId}/view")
  public ApiResponse<EvidenceViews.View> buyerEvidenceView(
      HttpServletRequest request, @PathVariable String orderId, @PathVariable String evidenceId) {
    return success(
        request,
        orderEvidence.view(tradingHttp.actor(request), orderId, FulfillmentRole.buyer, evidenceId));
  }

  /** adminEvidence。 */
  @GetMapping("/api/admin/rental-orders/{orderId}/evidence")
  public ApiResponse<EvidenceViews.Page> adminEvidence(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestParam(required = false) EvidenceNode node,
      @RequestParam(required = false) String statementId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        orderEvidence.page(
            tradingHttp.actor(request),
            orderId,
            FulfillmentRole.admin,
            node,
            statementId,
            PageQuery.of(page, pageSize)));
  }

  /** adminEvidenceView。 */
  @PostMapping("/api/admin/rental-orders/{orderId}/evidence/{evidenceId}/view")
  public ApiResponse<EvidenceViews.View> adminEvidenceView(
      HttpServletRequest request, @PathVariable String orderId, @PathVariable String evidenceId) {
    return success(
        request,
        orderEvidence.view(tradingHttp.actor(request), orderId, FulfillmentRole.admin, evidenceId));
  }

  /** factoryUploadEvidence。 */
  @PostMapping("/api/me/factory-orders/{orderId}/evidence/uploads")
  public ApiResponse<EvidenceViews.Upload> factoryUploadEvidence(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestParam("file") MultipartFile file,
      @RequestParam("node") EvidenceNode node)
      throws IOException, jakarta.servlet.ServletException {
    return success(
        request,
        orderEvidence.upload(
            tradingHttp.actor(request),
            orderId,
            FulfillmentRole.factory,
            node,
            bytes(request, file)));
  }

  /** factoryRemoveDraftEvidence。 */
  @PostMapping("/api/me/factory-orders/{orderId}/evidence/{evidenceId}/remove")
  public ApiResponse<TradingReceipt> factoryRemoveDraftEvidence(
      HttpServletRequest request,
      @PathVariable String orderId,
      @PathVariable String evidenceId,
      @RequestBody FulfillmentDtos.ExpectedVersion input) {
    return success(
        request,
        orderEvidence.remove(
            tradingHttp.command(request, TradingOperation.factoryRemoveDraftEvidence),
            orderId,
            evidenceId,
            FulfillmentRole.factory,
            input.expectedVersion()));
  }

  /** buyerUploadEvidence。 */
  @PostMapping("/api/me/rental-orders/{orderId}/evidence/uploads")
  public ApiResponse<EvidenceViews.Upload> buyerUploadEvidence(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestParam("file") MultipartFile file,
      @RequestParam("node") EvidenceNode node)
      throws IOException, jakarta.servlet.ServletException {
    return success(
        request,
        orderEvidence.upload(
            tradingHttp.actor(request),
            orderId,
            FulfillmentRole.buyer,
            node,
            bytes(request, file)));
  }

  /** buyerRemoveDraftEvidence。 */
  @PostMapping("/api/me/rental-orders/{orderId}/evidence/{evidenceId}/remove")
  public ApiResponse<TradingReceipt> buyerRemoveDraftEvidence(
      HttpServletRequest request,
      @PathVariable String orderId,
      @PathVariable String evidenceId,
      @RequestBody FulfillmentDtos.ExpectedVersion input) {
    return success(
        request,
        orderEvidence.remove(
            tradingHttp.command(request, TradingOperation.buyerRemoveDraftEvidence),
            orderId,
            evidenceId,
            FulfillmentRole.buyer,
            input.expectedVersion()));
  }
}
