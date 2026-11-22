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
public class CustomRequirementController extends BaseController {
  private final TradingHttp tradingHttp;
  private final CustomRequirements customRequirements;

  public CustomRequirementController(TradingHttp tradingHttp, CustomRequirements customRequirements) {
    this.tradingHttp = tradingHttp;
    this.customRequirements = customRequirements;
  }

  /** 返回本人定制需求分页列表。 */
  @GetMapping("/api/custom-requirements")
  public ApiResponse<PageResult<CustomRequirements.Summary>> listBuyerRequirements(
      HttpServletRequest r,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String status) {
    return success(
        r, customRequirements.page(tradingHttp.actor(r), false, status, PageQuery.of(page, pageSize)));
  }

  /** 创建买家定制需求草稿。 */
  @PostMapping("/api/custom-requirements")
  public ApiResponse<TradingReceipt> createCustomDraft(
      HttpServletRequest r, @RequestBody Draft input) {
    return success(
        r, customRequirements.save(tradingHttp.command(r, TradingOperation.createCustomDraft), null, input));
  }

  /** 返回本人定制需求详情。 */
  @GetMapping("/api/custom-requirements/{id}")
  public ApiResponse<CustomRequirements.Detail> getBuyerRequirement(
      HttpServletRequest r, @PathVariable String id) {
    return success(r, customRequirements.detail(tradingHttp.actor(r), false, id));
  }

  /** 返回工厂定制需求分页列表。 */
  @GetMapping("/api/me/factory/custom-requirements")
  public ApiResponse<PageResult<CustomRequirements.Summary>> listFactoryRequirements(
      HttpServletRequest r,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String status) {
    return success(r, customRequirements.page(tradingHttp.actor(r), true, status, PageQuery.of(page, pageSize)));
  }

  /** 返回工厂定制需求详情。 */
  @GetMapping("/api/me/factory/custom-requirements/{id}")
  public ApiResponse<CustomRequirements.Detail> getFactoryRequirement(
      HttpServletRequest r, @PathVariable String id) {
    return success(r, customRequirements.detail(tradingHttp.actor(r), true, id));
  }

  /** 保存可继续编辑的草稿。 */
  @PostMapping("/api/custom-requirements/{id}/draft")
  public ApiResponse<TradingReceipt> saveCustomDraft(
      HttpServletRequest r, @PathVariable String id, @RequestBody Draft input) {
    return success(
        r, customRequirements.save(tradingHttp.command(r, TradingOperation.saveCustomDraft), id, input));
  }

  /** 提交申请进入审核。 */
  @PostMapping("/api/custom-requirements/{id}/submit")
  public ApiResponse<TradingReceipt> submitCustomRequirement(
      HttpServletRequest r, @PathVariable String id, @RequestBody Action input) {
    return success(
        r,
        customRequirements.command(
            tradingHttp.command(r, TradingOperation.submitCustomRequirement),
            id,
            input.expectedVersion(),
            null));
  }

  /** 撤回尚可撤回的申请。 */
  @PostMapping("/api/custom-requirements/{id}/withdraw")
  public ApiResponse<TradingReceipt> withdrawCustomRequirement(
      HttpServletRequest r, @PathVariable String id, @RequestBody Action input) {
    return success(
        r,
        customRequirements.command(
            tradingHttp.command(r, TradingOperation.withdrawCustomRequirement),
            id,
            input.expectedVersion(),
            null));
  }

  /** 请求工厂修改定制报价。 */
  @PostMapping("/api/custom-requirements/{id}/request-revision")
  public ApiResponse<TradingReceipt> requestQuoteRevision(
      HttpServletRequest r, @PathVariable String id, @RequestBody NoteAction input) {
    return success(
        r,
        customRequirements.command(
            tradingHttp.command(r, TradingOperation.requestQuoteRevision),
            id,
            input.expectedVersion(),
            input.reason()));
  }

  /** 开始评估买家提交的定制需求。 */
  @PostMapping("/api/me/factory/custom-requirements/{id}/start-review")
  public ApiResponse<TradingReceipt> startCustomReview(
      HttpServletRequest r, @PathVariable String id, @RequestBody Action input) {
    return success(
        r,
        customRequirements.command(
            tradingHttp.command(r, TradingOperation.startCustomReview),
            id,
            input.expectedVersion(),
            null));
  }

  /** 工厂拒绝承接定制需求。 */
  @PostMapping("/api/me/factory/custom-requirements/{id}/decline")
  public ApiResponse<TradingReceipt> declineCustomRequirement(
      HttpServletRequest r, @PathVariable String id, @RequestBody NoteAction input) {
    return success(
        r,
        customRequirements.command(
            tradingHttp.command(r, TradingOperation.declineCustomRequirement),
            id,
            input.expectedVersion(),
            input.reason()));
  }

  /** 保存定制报价草稿。 */
  @PostMapping("/api/me/factory/custom-requirements/{id}/quote-draft")
  public ApiResponse<TradingReceipt> saveCustomQuote(
      HttpServletRequest r, @PathVariable String id, @RequestBody QuoteInput input) {
    return success(
        r,
        customRequirements.quoteCommand(
            tradingHttp.command(r, TradingOperation.saveCustomQuote),
            id,
            input.expectedVersion(),
            input.terms(),
            null));
  }

  /** 发布定制报价。 */
  @PostMapping("/api/me/factory/custom-requirements/{id}/publish-quote")
  public ApiResponse<TradingReceipt> publishCustomQuote(
      HttpServletRequest r, @PathVariable String id, @RequestBody QuoteAction input) {
    return success(
        r,
        customRequirements.quoteCommand(
            tradingHttp.command(r, TradingOperation.publishCustomQuote),
            id,
            input.expectedVersion(),
            null,
            input.quoteId()));
  }

  /** 请求工厂为已过期的报价重新报价。 */
  @PostMapping("/api/custom-requirements/{id}/request-requote")
  public ApiResponse<TradingReceipt> requestExpiredRequote(
      HttpServletRequest r, @PathVariable String id, @RequestBody Action input) {
    return success(
        r,
        customRequirements.command(
            tradingHttp.command(r, TradingOperation.requestExpiredRequote),
            id,
            input.expectedVersion(),
            null));
  }
}
