package com.mujian.backend.order.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.OrderViews;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class RentalOrderReaderController extends BaseController {
  private final TradingHttp tradingHttp;
  private final RentalOrderReader rentalOrderReader;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public RentalOrderReaderController(TradingHttp tradingHttp, RentalOrderReader rentalOrderReader) {
    this.tradingHttp = tradingHttp;
    this.rentalOrderReader = rentalOrderReader;
  }

  /** 按本人归属读取分页订单，状态保持服务器事实。 */
  @GetMapping("/api/me/rental-orders")
  public ApiResponse<PageResult<OrderViews.Summary>> mine(
      HttpServletRequest request,
      @RequestParam(defaultValue = "all") String view,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        rentalOrderReader.page(
            tradingHttp.actor(request), false, view, keyword, PageQuery.of(page, pageSize)));
  }

  /** 按当前工厂身份限定数据范围。 */
  @GetMapping("/api/me/factory-orders")
  public ApiResponse<PageResult<OrderViews.Summary>> factory(
      HttpServletRequest request,
      @RequestParam(defaultValue = "all") String view,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        rentalOrderReader.page(
            tradingHttp.actor(request), true, view, keyword, PageQuery.of(page, pageSize)));
  }

  /** 只读取本人原订单详情，历史图片按原归属续取。 */
  @GetMapping("/api/me/rental-orders/{id}")
  public ApiResponse<OrderViews.Detail> mineDetail(
      HttpServletRequest request, @PathVariable String id) {
    return success(request, rentalOrderReader.detail(tradingHttp.actor(request), id, false));
  }

  /** 只读取本工厂原订单详情，不暴露其他工厂分组。 */
  @GetMapping("/api/me/factory-orders/{id}")
  public ApiResponse<OrderViews.Detail> factoryDetail(
      HttpServletRequest request, @PathVariable String id) {
    return success(request, rentalOrderReader.detail(tradingHttp.actor(request), id, true));
  }

  /** 统计本工厂全部待办与退款异常，不截成首页展示条数。 */
  @GetMapping("/api/me/factory-orders/overview")
  public ApiResponse<OrderViews.Overview> overview(HttpServletRequest request) {
    return success(request, rentalOrderReader.overview(tradingHttp.actor(request)));
  }
}
