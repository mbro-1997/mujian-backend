package com.mujian.backend.dispute.controller;

import com.mujian.backend.common.enums.OrderParticipantRole;

import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.dispute.service.*;
import com.mujian.backend.order.dto.CheckoutDtos;
import com.mujian.backend.order.service.*;
import com.mujian.backend.payment.service.PaymentExecution;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
@RestController
public class ReceivableController extends BaseController {
  private final Receivables receivables;
  private final TradingHttp tradingHttp;
  private final ExtensionPayment launches;
  private final PaymentExecution execution;
  private final PaymentGateway paymentGateway;
  public ReceivableController(Receivables receivables,TradingHttp tradingHttp,ExtensionPayment launches,PaymentExecution execution,PaymentGateway paymentGateway){
    this.receivables=receivables;this.tradingHttp=tradingHttp;this.launches=launches;this.execution=execution;this.paymentGateway=paymentGateway;
  }
  /** 准备本次渠道付款。 */
  @PostMapping("/api/me/compensation-receivables/{id}/pay")
  public ApiResponse<ExtensionPayment.Launch> pay(HttpServletRequest r,@PathVariable String id,@RequestBody CheckoutDtos.ExpectedVersion v){
    TradingCommand tradingCommand=tradingHttp.command(r,TradingOperation.payCompensation);
    receivables.read(tradingCommand.actor(),OrderParticipantRole.buyer,id);paymentGateway.requireReady();
    String payment=receivables.prepare(tradingCommand,id,v.expectedVersion());execution.execute(payment);
    return success(r,launches.parameters(id,payment));
  }
  /** 返回本人补偿应收分页列表。 */
  @GetMapping("/api/me/compensation-receivables")
  public ApiResponse<PageResult<Receivables.Detail>> buyerPage(HttpServletRequest r,@RequestParam(required=false) String orderId,@RequestParam(required=false) String status,
      @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize){
    return success(r,receivables.page(tradingHttp.actor(r),OrderParticipantRole.buyer,orderId,status,PageQuery.of(page,pageSize)));
  }
  /** 返回本人补偿应收详情。 */
  @GetMapping("/api/me/compensation-receivables/{id}")
  public ApiResponse<Receivables.Detail> buyerDetail(HttpServletRequest r,@PathVariable String id){return success(r,receivables.read(tradingHttp.actor(r),OrderParticipantRole.buyer,id));}
  /** 返回工厂补偿应收分页列表。 */
  @GetMapping("/api/me/factory-compensation-receivables")
  public ApiResponse<PageResult<Receivables.Detail>> factoryPage(HttpServletRequest r,@RequestParam(required=false) String orderId,@RequestParam(required=false) String status,
      @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize){
    return success(r,receivables.page(tradingHttp.actor(r),OrderParticipantRole.factory,orderId,status,PageQuery.of(page,pageSize)));
  }
  /** 返回工厂补偿应收详情。 */
  @GetMapping("/api/me/factory-compensation-receivables/{id}")
  public ApiResponse<Receivables.Detail> factoryDetail(HttpServletRequest r,@PathVariable String id){return success(r,receivables.read(tradingHttp.actor(r),OrderParticipantRole.factory,id));}
  /** 返回管理端补偿应收分页列表。 */
  @GetMapping("/api/admin/compensation-receivables")
  public ApiResponse<PageResult<Receivables.Detail>> adminPage(HttpServletRequest r,@RequestParam(required=false) String orderId,@RequestParam(required=false) String status,
      @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize){
    return success(r,receivables.page(tradingHttp.actor(r),OrderParticipantRole.admin,orderId,status,PageQuery.of(page,pageSize)));
  }
  /** 返回管理端补偿应收详情。 */
  @GetMapping("/api/admin/compensation-receivables/{id}")
  public ApiResponse<Receivables.Detail> adminDetail(HttpServletRequest r,@PathVariable String id){return success(r,receivables.read(tradingHttp.actor(r),OrderParticipantRole.admin,id));}
}
