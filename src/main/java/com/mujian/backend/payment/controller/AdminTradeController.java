package com.mujian.backend.payment.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.order.dto.OrderQuery;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.order.vo.*;
import com.mujian.backend.payment.dto.*;
import com.mujian.backend.payment.service.AdminTradeReader;
import com.mujian.backend.payment.vo.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import org.springframework.web.bind.annotation.*;

@RestController
public class AdminTradeController extends BaseController {
  private final TradingHttp tradingHttp;
  private final AdminTradeReader adminTradeReader;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public AdminTradeController(TradingHttp tradingHttp, AdminTradeReader adminTradeReader) {
    this.tradingHttp = tradingHttp;
    this.adminTradeReader = adminTradeReader;
  }

  /** 按管理订单权限读取有范围的分页结果。 */
  @GetMapping("/api/admin/rental-orders")
  public ApiResponse<PageResult<OrderViews.Summary>> orders(
      HttpServletRequest request,
      @RequestParam(defaultValue = "all") String view,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String factoryId,
      @RequestParam(required = false) String buyerUserId,
      @RequestParam(required = false) LocalDate startDate,
      @RequestParam(required = false) LocalDate endDate,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        adminTradeReader.orders(
            tradingHttp.actor(request),
            new OrderQuery(
                buyerUserId,
                factoryId,
                view,
                keyword,
                startDate,
                endDate,
                PageQuery.of(page, pageSize))));
  }

  /** 读取原订单快照并记录最小敏感访问审计。 */
  @GetMapping("/api/admin/rental-orders/{id}")
  public ApiResponse<OrderViews.Detail> order(HttpServletRequest request, @PathVariable String id) {
    return success(
        request,
        adminTradeReader.order(
            tradingHttp.actor(request), id, (String) request.getAttribute("traceId")));
  }

  /** 按支付追溯权限分页读取原主支付，不重复多厂金额。 */
  @GetMapping("/api/admin/payments")
  public ApiResponse<PageResult<BatchViews.Payment>> payments(
      HttpServletRequest request,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String factoryId,
      @RequestParam(required = false) String buyerUserId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        adminTradeReader.payments(
            tradingHttp.actor(request),
            new PaymentQuery(
                status, factoryId, buyerUserId, keyword, PageQuery.of(page, pageSize))));
  }

  /** 追溯原支付、子单、订单和退款关系并记录访问。 */
  @GetMapping("/api/admin/payments/{id}")
  public ApiResponse<PaymentTraceVO> payment(HttpServletRequest request, @PathVariable String id) {
    return success(
        request,
        adminTradeReader.payment(
            tradingHttp.actor(request), id, (String) request.getAttribute("traceId")));
  }

  /** 按退款权限分页读取原义务及原支付返券事实。 */
  @GetMapping("/api/admin/refunds")
  public ApiResponse<PageResult<RefundViews.Refund>> refunds(
      HttpServletRequest request,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String businessType,
      @RequestParam(required = false) String factoryId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        adminTradeReader.refunds(
            tradingHttp.actor(request),
            new RefundQuery(status, businessType, factoryId, PageQuery.of(page, pageSize))));
  }

  /** 读取原退款号、查询进度和来源，记录详情访问。 */
  @GetMapping("/api/admin/refunds/{id}")
  public ApiResponse<RefundViews.Trace> refund(
      HttpServletRequest request, @PathVariable String id) {
    return success(
        request,
        adminTradeReader.refund(
            tradingHttp.actor(request), id, (String) request.getAttribute("traceId")));
  }
}
