package com.mujian.backend.delivery.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.delivery.dto.*;
import com.mujian.backend.delivery.service.TransportExpenses;
import com.mujian.backend.delivery.vo.ExpenseViews;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.order.vo.TradingReceipt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class ExpenseController extends BaseController {
  private final TransportExpenses transportExpenses;
  private final TradingHttp tradingHttp;

  /** 装配费用核销入口所需依赖。 */
  public ExpenseController(TransportExpenses transportExpenses, TradingHttp tradingHttp) {
    this.transportExpenses = transportExpenses;
    this.tradingHttp = tradingHttp;
  }

  /** 按工厂归属读取两段冻结费用与最新申报。 */
  @GetMapping("/api/me/factory-orders/{orderId}/transport-expense")
  public ApiResponse<ExpenseViews.Context> context(
      HttpServletRequest request, @PathVariable String orderId) {
    return success(request, transportExpenses.context(tradingHttp.actor(request), orderId));
  }

  /** 保存草稿或以退回版本为依据建立新版本。 */
  @PostMapping("/api/me/factory-orders/{orderId}/transport-expense/save")
  public ApiResponse<TradingReceipt> save(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestBody ExpenseDtos.Save input) {
    return success(
        request,
        transportExpenses.save(
            tradingHttp.command(request, TradingOperation.saveExpense), orderId, input));
  }

  /** 重新校验两段计价、运输完成和凭证后提交。 */
  @PostMapping("/api/me/factory-orders/{orderId}/transport-expense/{statementId}/submit")
  public ApiResponse<TradingReceipt> submit(
      HttpServletRequest request,
      @PathVariable String orderId,
      @PathVariable String statementId,
      @RequestBody FulfillmentDtos.ExpectedVersion input) {
    return success(
        request,
        transportExpenses.submit(
            tradingHttp.command(request, TradingOperation.submitExpense),
            orderId,
            statementId,
            input.expectedVersion()));
  }

  /** 只分页展示本厂订单的费用历史。 */
  @GetMapping("/api/me/factory-orders/{orderId}/transport-expenses")
  public ApiResponse<PageResult<ExpenseViews.Expense>> factoryHistory(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        transportExpenses.page(
            tradingHttp.actor(request),
            new ExpenseDtos.Query(orderId, null, null, null, null, PageQuery.of(page, pageSize)),
            false));
  }

  /** 按当前工厂归属查看指定记录与实际结果。 */
  @GetMapping("/api/me/factory-orders/{orderId}/transport-expenses/{statementId}")
  public ApiResponse<ExpenseViews.Expense> factoryDetail(
      HttpServletRequest request, @PathVariable String orderId, @PathVariable String statementId) {
    return success(
        request, transportExpenses.detail(tradingHttp.actor(request), orderId, statementId, false));
  }

  /** 按费用读取权限查询指定订单的历史。 */
  @GetMapping("/api/admin/rental-orders/{orderId}/transport-expenses")
  public ApiResponse<PageResult<ExpenseViews.Expense>> adminHistory(
      HttpServletRequest request,
      @PathVariable String orderId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        transportExpenses.page(
            tradingHttp.actor(request),
            new ExpenseDtos.Query(orderId, null, null, null, null, PageQuery.of(page, pageSize)),
            true));
  }

  /** 按管理读取权限展示指定记录与追溯事实。 */
  @GetMapping("/api/admin/rental-orders/{orderId}/transport-expenses/{statementId}")
  public ApiResponse<ExpenseViews.Expense> adminDetail(
      HttpServletRequest request, @PathVariable String orderId, @PathVariable String statementId) {
    return success(
        request, transportExpenses.detail(tradingHttp.actor(request), orderId, statementId, true));
  }

  /** 按管理筛选条件读取跨工厂费用记录。 */
  @GetMapping("/api/admin/transport-expenses")
  public ApiResponse<PageResult<ExpenseViews.Expense>> adminPage(
      HttpServletRequest request,
      @RequestParam(required = false) TransportExpenseStatus status,
      @RequestParam(required = false) String factoryId,
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) Boolean overBudget,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        transportExpenses.page(
            tradingHttp.actor(request),
            new ExpenseDtos.Query(
                null, status, factoryId, keyword, overBudget, PageQuery.of(page, pageSize)),
            true));
  }

  /** 在原版本上互斥地批准或退回，批准后按原支付来源处理余额。 */
  @PostMapping("/api/admin/rental-orders/{orderId}/transport-expenses/{statementId}/review")
  public ApiResponse<TradingReceipt> review(
      HttpServletRequest request,
      @PathVariable String orderId,
      @PathVariable String statementId,
      @RequestBody ExpenseDtos.Review input) {
    return success(
        request,
        transportExpenses.review(
            tradingHttp.command(request, TradingOperation.reviewExpense),
            orderId,
            statementId,
            input));
  }
}
