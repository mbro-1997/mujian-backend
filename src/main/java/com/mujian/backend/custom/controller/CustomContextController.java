package com.mujian.backend.custom.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.custom.mapper.CustomContextMapper.Factory;
import com.mujian.backend.custom.service.CustomContextReader;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.payment.vo.RefundViews;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
public class CustomContextController extends BaseController {
  private final CustomContextReader customContextReader;
  private final TradingHttp tradingHttp;

  public CustomContextController(CustomContextReader customContextReader, TradingHttp tradingHttp) {
    this.customContextReader = customContextReader;
    this.tradingHttp = tradingHttp;
  }

  /** 读取工厂已提交的售后说明。 */
  @GetMapping("/api/me/factory/custom-after-sales/{id}/statements")
  public ApiResponse<CustomContextReader.Statements> statements(
      HttpServletRequest r, @PathVariable String id) {
    return success(r, customContextReader.factoryStatements(tradingHttp.actor(r), id));
  }

  /** 查询可承接定制需求的工厂。 */
  @GetMapping("/api/custom-factories")
  public ApiResponse<PageResult<Factory>> factories(
      HttpServletRequest r,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(r, customContextReader.factories(tradingHttp.actor(r), PageQuery.of(page, pageSize)));
  }

  /** 返回定制关联业务详情。 */
  @GetMapping("/api/custom-factories/{id}")
  public ApiResponse<Factory> factory(HttpServletRequest r, @PathVariable String id) {
    return success(r, customContextReader.factory(tradingHttp.actor(r), id));
  }

  /** 查询指定定制订单的退款记录。 */
  @GetMapping("/api/custom-orders/{id}/refunds")
  public ApiResponse<List<RefundViews.Refund>> refunds(
      HttpServletRequest r, @PathVariable String id) {
    return success(r, customContextReader.refunds(tradingHttp.actor(r), id));
  }

  /** 查询定制延期关联的管理事项。 */
  @GetMapping("/api/admin/custom-extensions/{id}/case")
  public ApiResponse<CustomContextReader.CaseView> extensionCase(
      HttpServletRequest r, @PathVariable String id) {
    return success(r, customContextReader.caseView(tradingHttp.actor(r), id, true));
  }

  /** 查询定制售后关联的管理事项。 */
  @GetMapping("/api/admin/custom-after-sales/{id}/case")
  public ApiResponse<CustomContextReader.CaseView> saleCase(
      HttpServletRequest r, @PathVariable String id) {
    return success(r, customContextReader.caseView(tradingHttp.actor(r), id, false));
  }
}
