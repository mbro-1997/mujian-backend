package com.mujian.backend.dispute.controller;

import com.mujian.backend.common.enums.OrderParticipantRole;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.delivery.dto.IssueInput;
import com.mujian.backend.dispute.dto.ClaimInput;
import com.mujian.backend.dispute.service.*;
import com.mujian.backend.order.dto.CheckoutDtos;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.order.vo.TradingReceipt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClaimController extends BaseController {
  private final ClaimService claimService;
  private final TradingHttp tradingHttp;
  private final ClaimStock claimStock;
  public ClaimController(ClaimService claimService,TradingHttp tradingHttp,ClaimStock claimStock){this.claimService=claimService;this.tradingHttp=tradingHttp;this.claimStock=claimStock;}
  /** 查询当前工厂归还验收的索赔库存事实。 */
  @GetMapping("/api/me/factory-orders/{orderId}/return-check/claim-stock")
  public ApiResponse<ClaimStock.State> claimStock(HttpServletRequest r,@PathVariable String orderId,@RequestParam String claimId){
    return success(r,claimStock.read(tradingHttp.actor(r),orderId,claimId));
  }
  /** 登记归还索赔的库存处理。 */
  @PostMapping("/api/me/factory-orders/{orderId}/return-check/claim-stock")
  public ApiResponse<TradingReceipt> recordClaimStock(HttpServletRequest r,@PathVariable String orderId,@RequestBody ClaimStock.Input input){
    return success(r,claimStock.record(tradingHttp.command(r,TradingOperation.recordClaimStock),orderId,input));
  }
  /** 返回本人归还索赔分页列表。 */
  @GetMapping("/api/me/claims")
  public ApiResponse<PageResult<ClaimService.Detail>> buyerList(HttpServletRequest r,@RequestParam(required=false) String orderId,
      @RequestParam(required=false) String status,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize){
    return success(r,claimService.page(tradingHttp.actor(r),OrderParticipantRole.buyer,orderId,status,PageQuery.of(page,pageSize)));
  }
  /** 返回本人归还索赔详情。 */
  @GetMapping("/api/me/claims/{id}")
  public ApiResponse<ClaimService.Detail> buyerDetail(HttpServletRequest r,@PathVariable String id){
    return success(r,claimService.read(tradingHttp.actor(r),OrderParticipantRole.buyer,id));
  }
  /** 返回工厂归还索赔分页列表。 */
  @GetMapping("/api/me/factory-claims")
  public ApiResponse<PageResult<ClaimService.Detail>> factoryList(HttpServletRequest r,@RequestParam(required=false) String orderId,
      @RequestParam(required=false) String status,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize){
    return success(r,claimService.page(tradingHttp.actor(r),OrderParticipantRole.factory,orderId,status,PageQuery.of(page,pageSize)));
  }
  /** 返回工厂归还索赔详情。 */
  @GetMapping("/api/me/factory-claims/{id}")
  public ApiResponse<ClaimService.Detail> factoryDetail(HttpServletRequest r,@PathVariable String id){
    return success(r,claimService.read(tradingHttp.actor(r),OrderParticipantRole.factory,id));
  }
  /** 返回管理端归还索赔分页列表。 */
  @GetMapping("/api/admin/claims")
  public ApiResponse<PageResult<ClaimService.Detail>> adminList(HttpServletRequest r,@RequestParam(required=false) String orderId,
      @RequestParam(required=false) String status,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize){
    return success(r,claimService.page(tradingHttp.actor(r),OrderParticipantRole.admin,orderId,status,PageQuery.of(page,pageSize)));
  }
  /** 返回管理端归还索赔详情。 */
  @GetMapping("/api/admin/claims/{id}")
  public ApiResponse<ClaimService.Detail> adminDetail(HttpServletRequest r,@PathVariable String id){
    return success(r,claimService.read(tradingHttp.actor(r),OrderParticipantRole.admin,id));
  }
  /** 发起归还索赔。 */
  @PostMapping("/api/me/factory-orders/{id}/claims")
  public ApiResponse<TradingReceipt> createClaim(HttpServletRequest r,@PathVariable String id,@RequestBody ClaimInput input){
    return success(r,claimService.draft(tradingHttp.command(r,TradingOperation.createClaim),id,input,true));
  }
  /** 保存归还索赔草稿。 */
  @PostMapping("/api/me/factory-claims/{id}/save")
  public ApiResponse<TradingReceipt> saveClaim(HttpServletRequest r,@PathVariable String id,@RequestBody ClaimInput input){
    return success(r,claimService.draft(tradingHttp.command(r,TradingOperation.saveClaim),id,input,false));
  }
  /** 提交申请进入审核。 */
  @PostMapping("/api/me/factory-claims/{id}/submit")
  public ApiResponse<TradingReceipt> submitClaim(HttpServletRequest r,@PathVariable String id,@RequestBody CheckoutDtos.ExpectedVersion input){
    return success(r,claimService.submit(tradingHttp.command(r,TradingOperation.submitClaim),id,input.expectedVersion()));
  }
  /** 撤回尚可撤回的申请。 */
  @PostMapping("/api/me/factory-claims/{id}/withdraw")
  public ApiResponse<TradingReceipt> withdrawClaim(HttpServletRequest r,@PathVariable String id,@RequestBody CheckoutDtos.ExpectedVersion input){
    return success(r,claimService.withdraw(tradingHttp.command(r,TradingOperation.withdrawClaim),id,input.expectedVersion(),"所属工厂撤回索赔"));
  }
  /** 登记补充说明和材料。 */
  @PostMapping("/api/me/factory-claims/{id}/supplement")
  public ApiResponse<TradingReceipt> supplementClaim(HttpServletRequest r,@PathVariable String id,@RequestBody IssueInput.Supplement input){
    return success(r,claimService.supplement(tradingHttp.command(r,TradingOperation.supplementClaim),id,input));
  }
  /** 登记审核结果。 */
  @PostMapping("/api/admin/claims/{id}/review")
  public ApiResponse<TradingReceipt> reviewClaim(HttpServletRequest r,@PathVariable String id,@RequestBody ClaimInput.Review input){
    return success(r,claimService.review(tradingHttp.command(r,TradingOperation.reviewClaim),id,input));
  }
  /** 确认当前审核或处理结果。 */
  @PostMapping("/api/me/claims/{id}/confirm")
  public ApiResponse<TradingReceipt> confirmClaim(HttpServletRequest r,@PathVariable String id,@RequestBody CheckoutDtos.ExpectedVersion input){
    return success(r,claimService.respond(tradingHttp.command(r,TradingOperation.confirmClaim),id,input.expectedVersion(),null));
  }
  /** 提交归还索赔申诉。 */
  @PostMapping("/api/me/claims/{id}/appeal")
  public ApiResponse<TradingReceipt> appealClaim(HttpServletRequest r,@PathVariable String id,@RequestBody IssueInput.Supplement input){
    return success(r,claimService.respond(tradingHttp.command(r,TradingOperation.appealClaim),id,input.expectedVersion(),input));
  }
}
