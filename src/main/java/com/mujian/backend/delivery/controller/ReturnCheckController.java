package com.mujian.backend.delivery.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.delivery.dto.FulfillmentDtos;
import com.mujian.backend.delivery.service.*;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.order.vo.TradingReceipt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** 原订单归还核验及实际处置。 */
@RestController
public class ReturnCheckController extends BaseController {
  private final ReturnChecks returnChecks;
  private final ReturnClosure returnClosure;
  private final TradingHttp tradingHttp;

  /** 各入口复用同一处置锁、原请求及真实权限。 */
  public ReturnCheckController(
      ReturnChecks returnChecks, ReturnClosure returnClosure, TradingHttp tradingHttp) {
    this.returnChecks = returnChecks;
    this.returnClosure = returnClosure;
    this.tradingHttp = tradingHttp;
  }

  /** scanReturn。 */
  @PostMapping("/api/me/factory-orders/{orderId}/return-check/scans")
  public ApiResponse<FulfillmentViews.ScanResult> scanReturn(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody FulfillmentDtos.ScanInput input) {
    return success(
        request,
        returnChecks.scan(
            tradingHttp.command(request, TradingOperation.scanReturn), orderId, input));
  }

  /** returnScans。 */
  @GetMapping("/api/me/factory-orders/{orderId}/return-check/scans")
  public ApiResponse<FulfillmentViews.ScanPage> returnScans(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        returnChecks.scans(tradingHttp.actor(request), orderId, PageQuery.of(page, pageSize)));
  }

  /** setReturnQuantity。 */
  @PostMapping("/api/me/factory-orders/{orderId}/return-check/quantity")
  public ApiResponse<TradingReceipt> setReturnQuantity(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody FulfillmentDtos.QuantityInput input) {
    return success(
        request,
        returnChecks.quantity(
            tradingHttp.command(request, TradingOperation.setReturnQuantity), orderId, input));
  }

  /** 确认终止订单的归还核验。 */
  @PostMapping("/api/me/factory-orders/{orderId}/return-check/confirm-termination")
  public ApiResponse<FulfillmentViews.CloseResult> confirmTermination(HttpServletRequest request,@PathVariable String orderId,
      @RequestBody FulfillmentDtos.ExpectedVersion input) {
    return success(request,returnClosure.confirmTermination(tradingHttp.command(request,TradingOperation.confirmTerminationReturn),orderId,input.expectedVersion()));
  }

  /** confirmReturn。 */
  @PostMapping("/api/me/factory-orders/{orderId}/return-check/confirm")
  public ApiResponse<FulfillmentViews.CloseResult> confirmReturn(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody FulfillmentDtos.ExpectedVersion input) {
    return success(
        request,
        returnClosure.confirm(
            tradingHttp.command(request, TradingOperation.confirmReturn),
            orderId,
            input.expectedVersion(),
            null));
  }

  /** adminConfirmArrival。 */
  @PostMapping("/api/admin/rental-orders/{orderId}/return-check/arrival")
  public ApiResponse<TradingReceipt> adminConfirmArrival(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody FulfillmentDtos.AdminReason input) {
    return success(
        request,
        returnChecks.arrival(
            tradingHttp.command(request, TradingOperation.adminConfirmArrival), orderId, input));
  }

  /** adminCloseReturn。 */
  @PostMapping("/api/admin/rental-orders/{orderId}/return-check/close")
  public ApiResponse<FulfillmentViews.CloseResult> adminCloseReturn(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody FulfillmentDtos.AdminReason input) {
    return success(
        request,
        returnClosure.confirm(
            tradingHttp.command(request, TradingOperation.adminCloseReturn),
            orderId,
            input.expectedVersion(),
            input.reason()));
  }
}
