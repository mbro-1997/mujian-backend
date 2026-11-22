package com.mujian.backend.order.controller;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.order.dto.*;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class RentalOrderCommandController extends BaseController {
  private final TradingHttp tradingHttp;
  private final RentalOrderCommands rentalOrderCommands;
  private final OrderCancellationQuotation orderCancellationQuotation;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public RentalOrderCommandController(
      TradingHttp tradingHttp,
      RentalOrderCommands rentalOrderCommands,
      OrderCancellationQuotation orderCancellationQuotation) {
    this.tradingHttp = tradingHttp;
    this.rentalOrderCommands = rentalOrderCommands;
    this.orderCancellationQuotation = orderCancellationQuotation;
  }

  /** 由本工厂按原版本接单，截止和并发冲突交统一服务判定。 */
  @PostMapping("/api/me/factory-orders/{id}/accept")
  public ApiResponse<TradingReceipt> accept(
      HttpServletRequest request,
      @PathVariable String id,
      @RequestBody CheckoutDtos.ExpectedVersion expectedVersionInput) {
    return success(
        request,
        rentalOrderCommands.factory(
            tradingHttp.command(request, TradingOperation.acceptFactoryOrder),
            id,
            expectedVersionInput.expectedVersion(),
            null));
  }

  /** 由本工厂拒绝原单，终止与退款义务同事务。 */
  @PostMapping("/api/me/factory-orders/{id}/reject")
  public ApiResponse<TradingReceipt> reject(
      HttpServletRequest request,
      @PathVariable String id,
      @RequestBody OrderDtos.Reject rejectInput) {
    return success(
        request,
        rentalOrderCommands.factory(
            tradingHttp.command(request, TradingOperation.rejectFactoryOrder),
            id,
            rejectInput.expectedVersion(),
            rejectInput.reason()));
  }

  /** 读取原订单和当前取消档位，返回短期确认凭据。 */
  @PostMapping("/api/me/rental-orders/{id}/cancel-quote")
  public ApiResponse<CancellationQuoteVO> quote(
      HttpServletRequest request,
      @PathVariable String id,
      @RequestBody CheckoutDtos.ExpectedVersion expectedVersionInput) {
    return success(
        request,
        orderCancellationQuotation.quote(
            tradingHttp.actor(request), id, expectedVersionInput.expectedVersion()));
  }

  /** 保存原来源的取消意图，未知渠道结果继续原号恢复。 */
  @PostMapping("/api/me/rental-orders/{id}/cancel")
  public ApiResponse<TradingReceipt> cancel(
      HttpServletRequest request,
      @PathVariable String id,
      @RequestBody OrderDtos.Cancel cancelInput) {
    return success(
        request,
        rentalOrderCommands.cancel(
            tradingHttp.command(request, TradingOperation.cancelRentalOrder), id, cancelInput));
  }
}
