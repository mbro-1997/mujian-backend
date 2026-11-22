package com.mujian.backend.delivery.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.delivery.dto.*;
import com.mujian.backend.delivery.service.*;
import com.mujian.backend.delivery.vo.*;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.order.vo.TradingReceipt;
import jakarta.servlet.http.*;
import org.springframework.web.bind.annotation.*;

/** 归还执行方式来自原回程运输段。 */
@RestController
public class ReturnShipmentController extends BaseController {
  private final ReturnShipments returnShipments;
  private final TradingHttp tradingHttp;

  /** 复用真实订单身份和命令恢复规则。 */
  public ReturnShipmentController(ReturnShipments returnShipments, TradingHttp tradingHttp) {
    this.returnShipments = returnShipments;
    this.tradingHttp = tradingHttp;
  }

  /** saveReturnShipment。 */
  @PostMapping("/api/me/rental-orders/{orderId}/return-shipment/save")
  public ApiResponse<TradingReceipt> saveReturnShipment(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody FulfillmentDtos.ShipmentInput input) {
    return success(
        request,
        returnShipments.buyer(
            tradingHttp.command(request, TradingOperation.saveReturnShipment), orderId, input));
  }

  /** sendReturnShipment。 */
  @PostMapping("/api/me/rental-orders/{orderId}/return-shipment/send")
  public ApiResponse<TradingReceipt> sendReturnShipment(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody FulfillmentDtos.ShipmentInput input) {
    return success(
        request,
        returnShipments.buyer(
            tradingHttp.command(request, TradingOperation.sendReturnShipment), orderId, input));
  }

  /** 登记本厂实际取回的交接凭据。 */
  @PostMapping("/api/me/factory-orders/{orderId}/return-shipment/handover")
  public ApiResponse<TradingReceipt> handover(HttpServletRequest request,@PathVariable String orderId,@RequestBody OverdueFees.Pickup input) {
    return success(request,returnShipments.handover(tradingHttp.command(request,TradingOperation.confirmPickupHandover),orderId,input));
  }

  /** 从原归还核验页核定日租和额外扣款。 */
  @PostMapping("/api/admin/rental-orders/{orderId}/return-check/overdue-fee")
  public ApiResponse<TradingReceipt> assess(HttpServletRequest request,@PathVariable String orderId,@RequestBody OverdueFees.Review input) {
    return success(request,returnShipments.assess(tradingHttp.command(request,TradingOperation.assessOverdueFee),orderId,input));
  }

  /** startFactoryPickup。 */
  @PostMapping("/api/me/factory-orders/{orderId}/return-shipment/start-pickup")
  public ApiResponse<TradingReceipt> startFactoryPickup(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody FulfillmentDtos.PickupInput input) {
    return success(
        request,
        returnShipments.pickup(
            tradingHttp.command(request, TradingOperation.startFactoryPickup), orderId, input));
  }
}
