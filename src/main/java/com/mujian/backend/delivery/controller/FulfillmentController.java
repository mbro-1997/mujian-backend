package com.mujian.backend.delivery.controller;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.delivery.dto.FulfillmentDtos;
import com.mujian.backend.delivery.service.FulfillmentReader;
import com.mujian.backend.delivery.service.OutboundCommands;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.order.vo.TradingReceipt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

/** 履约接口只接收规范DTO并调用领域服务，身份取自既有会话。 */
@RestController
public class FulfillmentController extends BaseController {
  private final com.mujian.backend.delivery.service.HandoverCommands handoverCommands;
  private final com.mujian.backend.delivery.service.FulfillmentEvents fulfillmentEvents;
  private final TradingHttp tradingHttp;
  private final FulfillmentReader fulfillmentReader;
  private final OutboundCommands outboundCommands;
  private final com.mujian.backend.delivery.service.OutboundScans outboundScans;

  /** 复用统一鉴权、请求摘要和响应协议。 */
  public FulfillmentController(
      TradingHttp tradingHttp,
      FulfillmentReader fulfillmentReader,
      OutboundCommands outboundCommands,
      com.mujian.backend.delivery.service.OutboundScans outboundScans,
      com.mujian.backend.delivery.service.HandoverCommands handoverCommands,
      com.mujian.backend.delivery.service.FulfillmentEvents fulfillmentEvents) {
    this.handoverCommands = handoverCommands;
    this.fulfillmentEvents = fulfillmentEvents;
    this.tradingHttp = tradingHttp;
    this.fulfillmentReader = fulfillmentReader;
    this.outboundCommands = outboundCommands;
    this.outboundScans = outboundScans;
  }

  /** 工厂仅查看本厂订单，读取不建立出库草稿。 */
  @GetMapping("/api/me/factory-orders/{orderId}/fulfillment")
  public ApiResponse<FulfillmentViews.Fulfillment> factory(
      HttpServletRequest request, @PathVariable String orderId) {
    return success(
        request, fulfillmentReader.read(tradingHttp.actor(request), orderId, true, false));
  }

  /** 买家仅查看本人订单，动作按买家身份过滤。 */
  @GetMapping("/api/me/rental-orders/{orderId}/fulfillment")
  public ApiResponse<FulfillmentViews.Fulfillment> buyer(
      HttpServletRequest request, @PathVariable String orderId) {
    return success(
        request, fulfillmentReader.read(tradingHttp.actor(request), orderId, false, false));
  }

  /** 管理员需原订单读取权限，不获得买家或工厂动作。 */
  @GetMapping("/api/admin/rental-orders/{orderId}/fulfillment")
  public ApiResponse<FulfillmentViews.Fulfillment> admin(
      HttpServletRequest request, @PathVariable String orderId) {
    return success(
        request, fulfillmentReader.read(tradingHttp.actor(request), orderId, false, true));
  }

  /** 保存草稿备注，原请求号可从共用交易回执恢复。 */
  @PostMapping("/api/me/factory-orders/{orderId}/outbound/save")
  public ApiResponse<TradingReceipt> save(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody FulfillmentDtos.OutboundDraft input) {
    return success(
        request,
        outboundCommands.save(
            tradingHttp.command(request, TradingOperation.saveOutbound), orderId, input));
  }

  /** B类设置已核对总量，错误保留原草稿与进度。 */
  @PostMapping("/api/me/factory-orders/{orderId}/outbound/quantity")
  public ApiResponse<TradingReceipt> quantity(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody FulfillmentDtos.QuantityInput input) {
    return success(
        request,
        outboundCommands.quantity(
            tradingHttp.command(request, TradingOperation.setOutboundQuantity), orderId, input));
  }

  /** 本单原分配A类实物核对，响应持久保留当次进度。 */
  @PostMapping("/api/me/factory-orders/{orderId}/outbound/scans")
  public ApiResponse<FulfillmentViews.ScanResult> scan(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody FulfillmentDtos.ScanInput input) {
    return success(
        request,
        outboundScans.scan(
            tradingHttp.command(request, TradingOperation.scanOutbound), orderId, input));
  }

  /** 返回真实成功扫描分页，失败扫描不会占成功记录。 */
  @GetMapping("/api/me/factory-orders/{orderId}/outbound/scans")
  public ApiResponse<FulfillmentViews.ScanPage> scans(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        outboundScans.page(
            tradingHttp.actor(request),
            orderId,
            com.mujian.backend.common.api.PageQuery.of(page, pageSize)));
  }

  /** 锁内重核全部明细和有效证据后确认出库。 */
  @PostMapping("/api/me/factory-orders/{orderId}/outbound/confirm")
  public ApiResponse<TradingReceipt> dispatch(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody FulfillmentDtos.ExpectedVersion input) {
    return success(
        request,
        handoverCommands.dispatch(
            tradingHttp.command(request, TradingOperation.confirmOutbound),
            orderId,
            input.expectedVersion()));
  }

  /** 本人提前确认或在收货窗口内确认。 */
  @PostMapping("/api/me/rental-orders/{orderId}/receipt/confirm")
  public ApiResponse<TradingReceipt> receive(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody FulfillmentDtos.ExpectedVersion input) {
    return success(
        request,
        handoverCommands.receive(
            tradingHttp.command(request, TradingOperation.confirmReceipt),
            orderId,
            input.expectedVersion()));
  }

  /** 工厂查看本单实际履约事件。 */
  @GetMapping("/api/me/factory-orders/{orderId}/fulfillment/events")
  public ApiResponse<FulfillmentViews.EventPage> factoryEvents(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        fulfillmentEvents.page(
            tradingHttp.actor(request),
            orderId,
            com.mujian.backend.common.enums.FulfillmentRole.factory,
            com.mujian.backend.common.api.PageQuery.of(page, pageSize)));
  }

  /** 买家查看本单实际履约事件。 */
  @GetMapping("/api/me/rental-orders/{orderId}/fulfillment/events")
  public ApiResponse<FulfillmentViews.EventPage> buyerEvents(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        fulfillmentEvents.page(
            tradingHttp.actor(request),
            orderId,
            com.mujian.backend.common.enums.FulfillmentRole.buyer,
            com.mujian.backend.common.api.PageQuery.of(page, pageSize)));
  }

  /** 管理员查看本单实际履约事件。 */
  @GetMapping("/api/admin/rental-orders/{orderId}/fulfillment/events")
  public ApiResponse<FulfillmentViews.EventPage> adminEvents(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        fulfillmentEvents.page(
            tradingHttp.actor(request),
            orderId,
            com.mujian.backend.common.enums.FulfillmentRole.admin,
            com.mujian.backend.common.api.PageQuery.of(page, pageSize)));
  }
}
