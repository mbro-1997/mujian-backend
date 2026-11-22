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
public class CustomServiceCaseController extends BaseController {
  private final TradingHttp tradingHttp;
  private final CustomOrderReader customOrderReader;
  private final CustomServiceCases customServiceCases;

  public CustomServiceCaseController(
      TradingHttp tradingHttp, CustomOrderReader customOrderReader, CustomServiceCases customServiceCases) {
    this.tradingHttp = tradingHttp;
    this.customOrderReader = customOrderReader;
    this.customServiceCases = customServiceCases;
  }

  /** 登记工厂对定制售后的说明。 */
  @PostMapping("/api/me/factory/custom-after-sales/{id}/statements")
  public ApiResponse<TradingReceipt> statement(
      HttpServletRequest r, @PathVariable String id, @RequestBody StatementInput input) {
    return success(
        r,
        customServiceCases.statement(
            tradingHttp.command(r, TradingOperation.submitCustomAfterSaleStatement), id, input));
  }

  /** 提交定制售后申请。 */
  @PostMapping("/api/custom-orders/{id}/after-sales")
  public ApiResponse<TradingReceipt> reportCustomAfterSale(
      HttpServletRequest r, @PathVariable String id, @RequestBody Issue input) {
    return success(
        r, customServiceCases.issue(tradingHttp.command(r, TradingOperation.reportCustomAfterSale), id, input));
  }

  /** 保存定制延期申请。 */
  @PostMapping("/api/me/factory/custom-orders/{id}/extension-drafts")
  public ApiResponse<TradingReceipt> saveCustomExtension(
      HttpServletRequest r, @PathVariable String id, @RequestBody ExtensionInput input) {
    return success(
        r, customServiceCases.extensionDraft(tradingHttp.command(r, TradingOperation.saveCustomExtension), id, input));
  }

  /** 提交申请进入审核。 */
  @PostMapping("/api/me/factory/custom-extensions/{id}/submit")
  public ApiResponse<TradingReceipt> submitCustomExtension(
      HttpServletRequest r, @PathVariable String id, @RequestBody Action input) {
    return success(
        r,
        customServiceCases.submitExtension(
            tradingHttp.command(r, TradingOperation.submitCustomExtension), id, input.expectedVersion()));
  }

  /** 开始处理定制售后申请。 */
  @PostMapping("/api/me/factory/custom-after-sales/{id}/start")
  public ApiResponse<TradingReceipt> startAfterSaleHandling(
      HttpServletRequest r, @PathVariable String id, @RequestBody Action input) {
    return success(
        r,
        customServiceCases.handle(
            tradingHttp.command(r, TradingOperation.startAfterSaleHandling), id, input.expectedVersion()));
  }

  /** 返回管理端定制售后分页列表。 */
  @GetMapping("/api/admin/custom-after-sales")
  public ApiResponse<PageResult<CustomOrderReader.SaleView>> listAdminAfterSale(
      HttpServletRequest r,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String status) {
    return success(r, customOrderReader.sales(tradingHttp.actor(r), status, PageQuery.of(page, pageSize)));
  }

  /** 返回管理端定制售后详情。 */
  @GetMapping("/api/admin/custom-after-sales/{id}")
  public ApiResponse<CustomOrderReader.SaleView> getAdminAfterSale(
      HttpServletRequest r, @PathVariable String id) {
    return success(r, customOrderReader.adminSale(tradingHttp.actor(r), id));
  }

  /** 返回管理端定制售后分页列表。 */
  @GetMapping("/api/admin/custom-extensions")
  public ApiResponse<PageResult<CustomOrderReader.ExtensionView>> listAdminExtension(
      HttpServletRequest r,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize,
      @RequestParam(required = false) String status) {
    return success(r, customOrderReader.extensions(tradingHttp.actor(r), status, PageQuery.of(page, pageSize)));
  }

  /** 返回管理端定制售后详情。 */
  @GetMapping("/api/admin/custom-extensions/{id}")
  public ApiResponse<CustomOrderReader.ExtensionView> getAdminExtension(
      HttpServletRequest r, @PathVariable String id) {
    return success(r, customOrderReader.adminExtension(tradingHttp.actor(r), id));
  }

  /** 审核工厂提交的定制延期申请。 */
  @PostMapping("/api/admin/custom-extensions/{id}/decision")
  public ApiResponse<TradingReceipt> decideCustomExtension(
      HttpServletRequest r, @PathVariable String id, @RequestBody ExtensionDecision input) {
    return success(
        r, customServiceCases.decide(tradingHttp.command(r, TradingOperation.decideCustomExtension), id, input));
  }

  /** 登记定制售后的处理结论。 */
  @PostMapping("/api/admin/custom-after-sales/{id}/resolution")
  public ApiResponse<TradingReceipt> resolveCustomAfterSale(
      HttpServletRequest r, @PathVariable String id, @RequestBody Resolution input) {
    return success(
        r, customServiceCases.resolve(tradingHttp.command(r, TradingOperation.resolveCustomAfterSale), id, input));
  }
}
