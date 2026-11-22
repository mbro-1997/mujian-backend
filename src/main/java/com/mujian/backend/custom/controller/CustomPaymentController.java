package com.mujian.backend.custom.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.custom.dto.CustomForms.*;
import com.mujian.backend.custom.service.*;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.order.vo.TradingReceipt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class CustomPaymentController extends BaseController {
  private final TradingHttp tradingHttp;
  private final CustomPurchase customPurchase;
  private final CustomPayments payments;

  public CustomPaymentController(
      TradingHttp tradingHttp, CustomPurchase customPurchase, CustomPayments payments) {
    this.tradingHttp = tradingHttp;
    this.customPurchase = customPurchase;
    this.payments = payments;
  }

  /** 接受定制报价。 */
  @PostMapping("/api/custom-requirements/{id}/accept-quote")
  public ApiResponse<CustomPayments.Launch> acceptCustomQuote(
      HttpServletRequest r, @PathVariable String id, @RequestBody AcceptQuote input) {
    return success(
        r,
        payments.launch(
            tradingHttp.actor(r),
            customPurchase.accept(tradingHttp.command(r, TradingOperation.acceptCustomQuote), id, input)));
  }

  /** 发起定制订单付款。 */
  @PostMapping("/api/custom-orders/{id}/payments")
  public ApiResponse<CustomPayments.Launch> launchCustomPayment(
      HttpServletRequest r, @PathVariable String id, @RequestBody Action input) {
    return success(
        r,
        payments.launch(
            tradingHttp.actor(r),
            customPurchase.retry(
                tradingHttp.command(r, TradingOperation.launchCustomPayment),
                id,
                input.expectedVersion())));
  }

  /** 在生产前取消定制订单。 */
  @PostMapping("/api/custom-orders/{id}/cancel")
  public ApiResponse<TradingReceipt> cancelBeforeProduction(
      HttpServletRequest r, @PathVariable String id, @RequestBody Action input) {
    return success(
        r,
        customPurchase.production(
            tradingHttp.command(r, TradingOperation.cancelBeforeProduction), id, input.expectedVersion()));
  }

  /** 读取定制订单的付款信息。 */
  @GetMapping("/api/custom-orders/{id}/payment")
  public ApiResponse<CustomPayments.Launch> getCustomPayment(
      HttpServletRequest r, @PathVariable String id) {
    return success(r, payments.current(tradingHttp.actor(r), id));
  }

  /** 确认定制订单开始生产。 */
  @PostMapping("/api/me/factory/custom-orders/{id}/start-production")
  public ApiResponse<TradingReceipt> startCustomProduction(
      HttpServletRequest r, @PathVariable String id, @RequestBody Action input) {
    return success(
        r,
        customPurchase.production(
            tradingHttp.command(r, TradingOperation.startCustomProduction), id, input.expectedVersion()));
  }
}
