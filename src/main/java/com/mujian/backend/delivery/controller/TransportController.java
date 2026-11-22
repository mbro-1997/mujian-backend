package com.mujian.backend.delivery.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.delivery.dto.*;
import com.mujian.backend.delivery.dto.TransportDtos.*;
import com.mujian.backend.delivery.service.TransportService;
import com.mujian.backend.delivery.vo.TransportViews.*;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.order.vo.TradingReceipt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class TransportController extends BaseController {
  private final TradingHttp tradingHttp;
  private final TransportService transportService;

  /** 装配本模块入口与统一会话。 */
  public TransportController(TradingHttp tradingHttp, TransportService transportService) {
    this.tradingHttp = tradingHttp;
    this.transportService = transportService;
  }

  /** 平台车型分页。 */
  @GetMapping("/api/admin/transport-vehicles")
  public ApiResponse<PageResult<Vehicle>> vehicles(
      HttpServletRequest request,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status) {
    return success(
        request,
        transportService.vehicles(
            tradingHttp.actor(request),
            new TransportQuery(PageQuery.of(page, pageSize), keyword, status, null, null)));
  }

  /** 平台车型详情。 */
  @GetMapping("/api/admin/transport-vehicles/{id}")
  public ApiResponse<Vehicle> vehicle(HttpServletRequest request, @PathVariable String id) {
    return success(request, transportService.vehicle(tradingHttp.actor(request), id));
  }

  /** 创建真实车型配置。 */
  @PostMapping("/api/admin/transport-vehicles")
  public ApiResponse<TradingReceipt> createVehicle(
      HttpServletRequest request, @RequestBody VehicleInput vehicleInputBody) {
    return success(
        request,
        transportService.saveVehicle(
            tradingHttp.command(request, TradingOperation.createVehicle), null, vehicleInputBody));
  }

  /** 按版本更新车型。 */
  @PostMapping("/api/admin/transport-vehicles/{id}/update")
  public ApiResponse<TradingReceipt> updateVehicle(
      HttpServletRequest request,
      @PathVariable String id,
      @RequestBody VehicleInput vehicleInputBody) {
    return success(
        request,
        transportService.saveVehicle(
            tradingHttp.command(request, TradingOperation.updateVehicle), id, vehicleInputBody));
  }

  /** 平台价格规则分页。 */
  @GetMapping("/api/admin/transport-price-rules")
  public ApiResponse<PageResult<PriceRule>> rules(
      HttpServletRequest request,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String cityCode,
      @RequestParam(required = false) String vehicleTierId,
      @RequestParam(required = false) String status) {
    return success(
        request,
        transportService.rules(
            tradingHttp.actor(request),
            new TransportQuery(
                PageQuery.of(page, pageSize), null, status, cityCode, vehicleTierId)));
  }

  /** 单条规则详情。 */
  @GetMapping("/api/admin/transport-price-rules/{id}")
  public ApiResponse<PriceRule> rule(HttpServletRequest request, @PathVariable String id) {
    return success(request, transportService.rule(tradingHttp.actor(request), id));
  }

  /** 创建未发布规则。 */
  @PostMapping("/api/admin/transport-price-rules")
  public ApiResponse<TradingReceipt> createRule(
      HttpServletRequest request, @RequestBody RuleInput ruleInputBody) {
    return success(
        request,
        transportService.saveRule(
            tradingHttp.command(request, TradingOperation.createPriceRule), null, ruleInputBody));
  }

  /** 按版本保存未发布规则。 */
  @PostMapping("/api/admin/transport-price-rules/{id}/update")
  public ApiResponse<TradingReceipt> updateRule(
      HttpServletRequest request, @PathVariable String id, @RequestBody RuleInput ruleInputBody) {
    return success(
        request,
        transportService.saveRule(
            tradingHttp.command(request, TradingOperation.updatePriceRule), id, ruleInputBody));
  }

  /** 校验范围后发布规则。 */
  @PostMapping("/api/admin/transport-price-rules/{id}/publish")
  public ApiResponse<TradingReceipt> publish(
      HttpServletRequest request, @PathVariable String id, @RequestBody Version versionBody) {
    return success(
        request,
        transportService.publish(
            tradingHttp.command(request, TradingOperation.publishPriceRule),
            id,
            versionBody.expectedVersion()));
  }

  /** 为已发布规则设置未来结束时间。 */
  @PostMapping("/api/admin/transport-price-rules/{id}/end")
  public ApiResponse<TradingReceipt> end(
      HttpServletRequest request, @PathVariable String id, @RequestBody EndRule endRuleBody) {
    return success(
        request,
        transportService.end(
            tradingHttp.command(request, TradingOperation.endPriceRule), id, endRuleBody));
  }

  /** 小程序配送入口查看有效价格单。 */
  @GetMapping("/api/me/transport-price-sheet")
  public ApiResponse<PriceSheet> priceSheet(
      HttpServletRequest request, @RequestParam(required = false) String cityCode) {
    return success(request, transportService.priceSheet(tradingHttp.actor(request), cityCode));
  }
}
