package com.mujian.backend.custom.controller;

import com.mujian.backend.common.enums.FulfillmentRole;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.custom.dto.CustomForms.*;
import com.mujian.backend.custom.service.*;
import com.mujian.backend.order.service.TradingHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class CustomMaterialController extends BaseController {
  private final TradingHttp tradingHttp;
  private final CustomMaterials customMaterials;

  public CustomMaterialController(TradingHttp tradingHttp, CustomMaterials customMaterials) {
    this.tradingHttp = tradingHttp;
    this.customMaterials = customMaterials;
  }

  /** 返回本人定制需求材料的预览凭据。 */
  @GetMapping("/api/custom-requirements/{id}/materials/{materialId}/view")
  public ApiResponse<CustomMaterials.Preview> viewBuyerRequirementsMaterial(
      HttpServletRequest r, @PathVariable String id, @PathVariable String materialId) {
    return success(r, customMaterials.preview(tradingHttp.actor(r), FulfillmentRole.buyer.name(), true, id, materialId));
  }

  /** 为买家定制需求上传材料。 */
  @PostMapping("/api/custom-requirements/{id}/materials")
  public ApiResponse<EvidenceRef> uploadBuyerRequirementsMaterial(
      HttpServletRequest r,
      @PathVariable String id,
      @RequestParam MultipartFile file,
      @RequestParam String purpose) {
    return success(
        r,
        customMaterials.upload(
            tradingHttp.actor(r), FulfillmentRole.buyer.name(), true, id, purpose, r.getHeader("Idempotency-Key"), file));
  }

  /** 返回本人定制订单材料的预览凭据。 */
  @GetMapping("/api/custom-orders/{id}/materials/{materialId}/view")
  public ApiResponse<CustomMaterials.Preview> viewBuyerOrdersMaterial(
      HttpServletRequest r, @PathVariable String id, @PathVariable String materialId) {
    return success(r, customMaterials.preview(tradingHttp.actor(r), FulfillmentRole.buyer.name(), false, id, materialId));
  }

  /** 为买家定制订单上传材料。 */
  @PostMapping("/api/custom-orders/{id}/materials")
  public ApiResponse<EvidenceRef> uploadBuyerOrdersMaterial(
      HttpServletRequest r,
      @PathVariable String id,
      @RequestParam MultipartFile file,
      @RequestParam String purpose) {
    return success(
        r,
        customMaterials.upload(
            tradingHttp.actor(r), FulfillmentRole.buyer.name(), false, id, purpose, r.getHeader("Idempotency-Key"), file));
  }

  /** 返回工厂定制需求材料的预览凭据。 */
  @GetMapping("/api/me/factory/custom-requirements/{id}/materials/{materialId}/view")
  public ApiResponse<CustomMaterials.Preview> viewFactoryRequirementsMaterial(
      HttpServletRequest r, @PathVariable String id, @PathVariable String materialId) {
    return success(r, customMaterials.preview(tradingHttp.actor(r), FulfillmentRole.factory.name(), true, id, materialId));
  }

  /** 返回工厂定制订单材料的预览凭据。 */
  @GetMapping("/api/me/factory/custom-orders/{id}/materials/{materialId}/view")
  public ApiResponse<CustomMaterials.Preview> viewFactoryOrdersMaterial(
      HttpServletRequest r, @PathVariable String id, @PathVariable String materialId) {
    return success(r, customMaterials.preview(tradingHttp.actor(r), FulfillmentRole.factory.name(), false, id, materialId));
  }

  /** 为工厂定制订单上传材料。 */
  @PostMapping("/api/me/factory/custom-orders/{id}/materials")
  public ApiResponse<EvidenceRef> uploadFactoryOrdersMaterial(
      HttpServletRequest r,
      @PathVariable String id,
      @RequestParam MultipartFile file,
      @RequestParam String purpose) {
    return success(
        r,
        customMaterials.upload(
            tradingHttp.actor(r), FulfillmentRole.factory.name(), false, id, purpose, r.getHeader("Idempotency-Key"), file));
  }

  /** 返回管理端定制需求材料的预览凭据。 */
  @GetMapping("/api/admin/custom-requirements/{id}/materials/{materialId}/view")
  public ApiResponse<CustomMaterials.Preview> viewAdminRequirementsMaterial(
      HttpServletRequest r, @PathVariable String id, @PathVariable String materialId) {
    return success(r, customMaterials.preview(tradingHttp.actor(r), FulfillmentRole.admin.name(), true, id, materialId));
  }

  /** 返回管理端定制订单材料的预览凭据。 */
  @GetMapping("/api/admin/custom-orders/{id}/materials/{materialId}/view")
  public ApiResponse<CustomMaterials.Preview> viewAdminOrdersMaterial(
      HttpServletRequest r, @PathVariable String id, @PathVariable String materialId) {
    return success(r, customMaterials.preview(tradingHttp.actor(r), FulfillmentRole.admin.name(), false, id, materialId));
  }
}
