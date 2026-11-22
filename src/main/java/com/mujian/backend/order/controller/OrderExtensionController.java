package com.mujian.backend.order.controller;

import com.mujian.backend.common.enums.OrderParticipantRole;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.dto.CheckoutDtos;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.order.service.TradingHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderExtensionController extends BaseController {
  private final OrderExtensionQuote orderExtensionQuote;
  private final TradingHttp tradingHttp;
  private final OrderExtensions extensions;
  private final ExtensionPayment payment;
  private final ExtensionReader extensionReader;

  /** 使用交易会话取得实际买家身份。 */
  public OrderExtensionController(OrderExtensionQuote orderExtensionQuote,TradingHttp tradingHttp,OrderExtensions extensions,ExtensionPayment payment,ExtensionReader extensionReader) {
    this.extensions=extensions;this.payment=payment;this.extensionReader=extensionReader;
    this.orderExtensionQuote=orderExtensionQuote;
    this.tradingHttp=tradingHttp;
  }

  /** 只读报价不提前创建延期期限或占用。 */
  @GetMapping("/api/me/rental-orders/{orderId}/extension-quote")
  public ApiResponse<OrderExtensionQuote.Quote> quote(HttpServletRequest request,@PathVariable String orderId,
      @RequestParam int extraDays) {
    return success(request,orderExtensionQuote.quote(tradingHttp.actor(request),orderId,extraDays));
  }

  /** 提交租赁延期申请。 */
  @PostMapping("/api/me/rental-orders/{orderId}/extensions")
  public ApiResponse<ExtensionPayment.Launch> submit(HttpServletRequest r,@PathVariable String orderId,@RequestBody OrderExtensions.Days days) {
    return success(r,payment.launch(tradingHttp.command(r,TradingOperation.submitExtension),orderId,days));
  }
  /** 准备本次渠道付款。 */
  @PostMapping("/api/me/extensions/{id}/pay")
  public ApiResponse<ExtensionPayment.Launch> pay(HttpServletRequest r,@PathVariable String id,@RequestBody CheckoutDtos.ExpectedVersion input) {
    return success(r,payment.launch(tradingHttp.command(r,TradingOperation.payExtension),id,new OrderExtensions.Days(input.expectedVersion(),0,null)));
  }
  public record Decision(int expectedVersion,String reason) {}
  /** 取消租赁延期申请。 */
  @PostMapping("/api/me/extensions/{id}/cancel")
  public ApiResponse<TradingReceipt> cancel(HttpServletRequest r,@PathVariable String id,@RequestBody Decision input) {
    return success(r,extensions.command(tradingHttp.command(r,TradingOperation.cancelExtension),id,input.expectedVersion(),input.reason()));
  }
  /** 批准申请并更新生效状态。 */
  @PostMapping("/api/me/factory-extensions/{id}/approve")
  public ApiResponse<TradingReceipt> approve(HttpServletRequest r,@PathVariable String id,@RequestBody Decision input) {
    return success(r,extensions.command(tradingHttp.command(r,TradingOperation.approveExtension),id,input.expectedVersion(),input.reason()));
  }
  /** 驳回申请并记录原因。 */
  @PostMapping("/api/me/factory-extensions/{id}/reject")
  public ApiResponse<TradingReceipt> reject(HttpServletRequest r,@PathVariable String id,@RequestBody Decision input) {
    return success(r,extensions.command(tradingHttp.command(r,TradingOperation.rejectExtension),id,input.expectedVersion(),input.reason()));
  }
  /** 返回本人租赁延期分页列表。 */
  @GetMapping("/api/me/extensions")
  public ApiResponse<PageResult<ExtensionReader.Detail>> buyerList(HttpServletRequest r,@RequestParam(required=false) String orderId,
      @RequestParam(required=false) String status,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) {
    return success(r,extensionReader.page(tradingHttp.actor(r),OrderParticipantRole.buyer,orderId,status,PageQuery.of(page,pageSize)));
  }
  /** 返回本人租赁延期详情。 */
  @GetMapping("/api/me/extensions/{id}")
  public ApiResponse<ExtensionReader.Detail> buyerDetail(HttpServletRequest r,@PathVariable String id) {
    return success(r,extensionReader.detail(tradingHttp.actor(r),OrderParticipantRole.buyer,id));
  }
  /** 返回工厂租赁延期分页列表。 */
  @GetMapping("/api/me/factory-extensions")
  public ApiResponse<PageResult<ExtensionReader.Detail>> factoryList(HttpServletRequest r,@RequestParam(required=false) String orderId,
      @RequestParam(required=false) String status,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) {
    return success(r,extensionReader.page(tradingHttp.actor(r),OrderParticipantRole.factory,orderId,status,PageQuery.of(page,pageSize)));
  }
  /** 返回工厂租赁延期详情。 */
  @GetMapping("/api/me/factory-extensions/{id}")
  public ApiResponse<ExtensionReader.Detail> factoryDetail(HttpServletRequest r,@PathVariable String id) {
    return success(r,extensionReader.detail(tradingHttp.actor(r),OrderParticipantRole.factory,id));
  }
  /** 返回管理端租赁延期分页列表。 */
  @GetMapping("/api/admin/extensions")
  public ApiResponse<PageResult<ExtensionReader.Detail>> adminList(HttpServletRequest r,@RequestParam(required=false) String orderId,
      @RequestParam(required=false) String status,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) {
    return success(r,extensionReader.page(tradingHttp.actor(r),OrderParticipantRole.admin,orderId,status,PageQuery.of(page,pageSize)));
  }
  /** 返回管理端租赁延期详情。 */
  @GetMapping("/api/admin/extensions/{id}")
  public ApiResponse<ExtensionReader.Detail> adminDetail(HttpServletRequest r,@PathVariable String id) {
    return success(r,extensionReader.detail(tradingHttp.actor(r),OrderParticipantRole.admin,id));
  }
}
