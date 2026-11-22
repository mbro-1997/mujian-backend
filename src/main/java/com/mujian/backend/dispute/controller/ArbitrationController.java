package com.mujian.backend.dispute.controller;

import com.mujian.backend.common.enums.OrderParticipantRole;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.dispute.dto.ArbitrationInput;
import com.mujian.backend.delivery.dto.IssueInput;
import com.mujian.backend.dispute.service.*;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.order.vo.TradingReceipt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
@RestController
public class ArbitrationController extends BaseController {
  private final Arbitrations cases;
  private final TradingHttp tradingHttp;
  public ArbitrationController(Arbitrations cases,TradingHttp tradingHttp){this.cases=cases;this.tradingHttp=tradingHttp;}
  /** 返回本人仲裁分页列表。 */
  @GetMapping("/api/me/arbitrations")
  public ApiResponse<PageResult<Arbitrations.Detail>> buyerPage(HttpServletRequest r,@RequestParam(required=false) String orderId,
      @RequestParam(required=false) String decisionStage,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize){
    return success(r,cases.page(tradingHttp.actor(r),OrderParticipantRole.buyer,orderId,decisionStage,PageQuery.of(page,pageSize)));
  }
  /** 返回本人仲裁详情。 */
  @GetMapping("/api/me/arbitrations/{id}")
  public ApiResponse<Arbitrations.Detail> buyerDetail(HttpServletRequest r,@PathVariable String id){return success(r,cases.read(tradingHttp.actor(r),OrderParticipantRole.buyer,id));}
  /** 登记补充说明和材料。 */
  @PostMapping("/api/me/arbitrations/{id}/supplement")
  public ApiResponse<TradingReceipt> buyerSupplement(HttpServletRequest r,@PathVariable String id,@RequestBody IssueInput.Supplement input){
    return success(r,cases.supplement(tradingHttp.command(r,TradingOperation.buyerArbitrationSupplement),id,OrderParticipantRole.buyer,input));
  }
  /** 返回工厂仲裁分页列表。 */
  @GetMapping("/api/me/factory-arbitrations")
  public ApiResponse<PageResult<Arbitrations.Detail>> factoryPage(HttpServletRequest r,@RequestParam(required=false) String orderId,
      @RequestParam(required=false) String decisionStage,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize){
    return success(r,cases.page(tradingHttp.actor(r),OrderParticipantRole.factory,orderId,decisionStage,PageQuery.of(page,pageSize)));
  }
  /** 返回工厂仲裁详情。 */
  @GetMapping("/api/me/factory-arbitrations/{id}")
  public ApiResponse<Arbitrations.Detail> factoryDetail(HttpServletRequest r,@PathVariable String id){return success(r,cases.read(tradingHttp.actor(r),OrderParticipantRole.factory,id));}
  /** 登记补充说明和材料。 */
  @PostMapping("/api/me/factory-arbitrations/{id}/supplement")
  public ApiResponse<TradingReceipt> factorySupplement(HttpServletRequest r,@PathVariable String id,@RequestBody IssueInput.Supplement input){
    return success(r,cases.supplement(tradingHttp.command(r,TradingOperation.factoryArbitrationSupplement),id,OrderParticipantRole.factory,input));
  }
  /** 返回管理端仲裁分页列表。 */
  @GetMapping("/api/admin/arbitrations")
  public ApiResponse<PageResult<Arbitrations.Detail>> adminPage(HttpServletRequest r,@RequestParam(required=false) String orderId,
      @RequestParam(required=false) String decisionStage,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize){
    return success(r,cases.page(tradingHttp.actor(r),OrderParticipantRole.admin,orderId,decisionStage,PageQuery.of(page,pageSize)));
  }
  /** 返回管理端仲裁详情。 */
  @GetMapping("/api/admin/arbitrations/{id}")
  public ApiResponse<Arbitrations.Detail> adminDetail(HttpServletRequest r,@PathVariable String id){return success(r,cases.read(tradingHttp.actor(r),OrderParticipantRole.admin,id));}
  /** 登记补充说明和材料。 */
  @PostMapping("/api/admin/arbitrations/{id}/supplement")
  public ApiResponse<TradingReceipt> adminSupplement(HttpServletRequest r,@PathVariable String id,@RequestBody IssueInput.Supplement input){
    return success(r,cases.supplement(tradingHttp.command(r,TradingOperation.adminArbitrationSupplement),id,OrderParticipantRole.admin,input));
  }
  /** 发起订单仲裁。 */
  @PostMapping("/api/admin/rental-orders/{id}/arbitrations")
  public ApiResponse<TradingReceipt> open(HttpServletRequest r,@PathVariable String id,@RequestBody ArbitrationInput.Open input){
    return success(r,cases.open(tradingHttp.command(r,TradingOperation.openArbitration),id,input));
  }
  /** 保存仲裁裁决草稿。 */
  @PostMapping("/api/admin/arbitrations/{id}/decision-draft")
  public ApiResponse<Arbitrations.Detail> draft(HttpServletRequest r,@PathVariable String id,@RequestBody ArbitrationInput input){
    return success(r,cases.draft(tradingHttp.command(r,TradingOperation.draftDecision),id,input));
  }
  /** 确认仲裁裁决。 */
  @PostMapping("/api/admin/arbitrations/{id}/decision-confirm")
  public ApiResponse<TradingReceipt> confirm(HttpServletRequest r,@PathVariable String id,@RequestBody ArbitrationInput.Confirm input){
    return success(r,cases.confirm(tradingHttp.command(r,TradingOperation.confirmDecision),id,input));
  }
  /** 要求相关方补交仲裁证据。 */
  @PostMapping("/api/admin/arbitrations/{id}/request-evidence")
  public ApiResponse<TradingReceipt> request(HttpServletRequest r,@PathVariable String id,@RequestBody ArbitrationInput.Request input){
    return success(r,cases.request(tradingHttp.command(r,TradingOperation.requestArbitrationEvidence),id,input));
  }
}
