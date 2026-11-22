package com.mujian.backend.custom.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.custom.dto.CustomForms.*;
import com.mujian.backend.custom.service.*;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.order.vo.TradingReceipt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import org.springframework.web.bind.annotation.*;

@RestController
public class CustomOrderController extends BaseController {
  private final TradingHttp tradingHttp;
  private final CustomOrderReader customOrderReader;
  private final CustomFulfillment customFulfillment;
  private final Clock clock;

  public CustomOrderController(
      TradingHttp tradingHttp, CustomOrderReader customOrderReader, CustomFulfillment customFulfillment, Clock clock) {
    this.clock = clock;
    this.tradingHttp = tradingHttp;
    this.customOrderReader = customOrderReader;
    this.customFulfillment = customFulfillment;
  }

  /** 返回本人定制订单分页列表。 */
  @GetMapping("/api/custom-orders")
  public ApiResponse<PageResult<CustomOrderReader.Summary>> listBuyerCustomOrders(
      HttpServletRequest r,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String status) {
    return success(r, customOrderReader.page(tradingHttp.actor(r), false, status, PageQuery.of(page, pageSize)));
  }

  /** 返回本人定制订单详情。 */
  @GetMapping("/api/custom-orders/{id}")
  public ApiResponse<CustomOrderReader.Detail> getBuyerCustomOrder(
      HttpServletRequest r, HttpServletResponse response, @PathVariable String id) {
    CustomOrderReader.Detail detail = customOrderReader.detail(tradingHttp.actor(r), false, id);
    response.setHeader("X-Mujian-Time-Ms", Long.toString(clock.millis()));
    return success(r, detail);
  }

  /** 返回工厂定制订单分页列表。 */
  @GetMapping("/api/me/factory/custom-orders")
  public ApiResponse<PageResult<CustomOrderReader.Summary>> listFactoryCustomOrders(
      HttpServletRequest r,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String status) {
    return success(r, customOrderReader.page(tradingHttp.actor(r), true, status, PageQuery.of(page, pageSize)));
  }

  /** 返回工厂定制订单详情。 */
  @GetMapping("/api/me/factory/custom-orders/{id}")
  public ApiResponse<CustomOrderReader.Detail> getFactoryCustomOrder(
      HttpServletRequest r, @PathVariable String id) {
    return success(r, customOrderReader.detail(tradingHttp.actor(r), true, id));
  }

  /** 确认定制订单已自提。 */
  @PostMapping("/api/custom-orders/{id}/confirm-pickup")
  public ApiResponse<TradingReceipt> confirmCustomPickup(
      HttpServletRequest r, @PathVariable String id, @RequestBody DeliveryAction input) {
    return success(
        r,
        customFulfillment.confirm(
            tradingHttp.command(r, TradingOperation.confirmCustomPickup), id, input, true));
  }

  /** 确认收到定制订单交付物。 */
  @PostMapping("/api/custom-orders/{id}/accept-delivery")
  public ApiResponse<TradingReceipt> acceptCustomDelivery(
      HttpServletRequest r, @PathVariable String id, @RequestBody DeliveryAction input) {
    return success(
        r,
        customFulfillment.confirm(
            tradingHttp.command(r, TradingOperation.acceptCustomDelivery), id, input, false));
  }

  /** 将定制订单标记为待交付。 */
  @PostMapping("/api/me/factory/custom-orders/{id}/ready")
  public ApiResponse<TradingReceipt> readyCustomDelivery(
      HttpServletRequest r, @PathVariable String id, @RequestBody Action input) {
    return success(
        r,
        customFulfillment.progress(
            tradingHttp.command(r, TradingOperation.readyCustomDelivery), id, input.expectedVersion()));
  }

  /** 登记本次发货。 */
  @PostMapping("/api/me/factory/custom-orders/{id}/dispatch")
  public ApiResponse<TradingReceipt> dispatchCustomDelivery(
      HttpServletRequest r, @PathVariable String id, @RequestBody Action input) {
    return success(
        r,
        customFulfillment.progress(
            tradingHttp.command(r, TradingOperation.dispatchCustomDelivery), id, input.expectedVersion()));
  }

  /** 登记定制订单发货。 */
  @PostMapping("/api/me/factory/custom-orders/{id}/deliver")
  public ApiResponse<TradingReceipt> deliverCustomOrder(
      HttpServletRequest r, @PathVariable String id, @RequestBody DeliveryInput input) {
    return success(
        r,
        customFulfillment.deliver(
            tradingHttp.command(r, TradingOperation.deliverCustomOrder), id, input, false));
  }

  /** 登记定制订单的自提信息。 */
  @PostMapping("/api/me/factory/custom-orders/{id}/register-pickup")
  public ApiResponse<TradingReceipt> registerCustomPickup(
      HttpServletRequest r, @PathVariable String id, @RequestBody DeliveryInput input) {
    return success(
        r,
        customFulfillment.deliver(
            tradingHttp.command(r, TradingOperation.registerCustomPickup), id, input, true));
  }
}
