package com.mujian.backend.order.controller;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.order.dto.CheckoutDtos;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class CheckoutBatchController extends BaseController {
  private final TradingHttp tradingHttp;
  private final CheckoutSubmission checkoutSubmission;
  private final CheckoutBatchReader checkoutBatchReader;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public CheckoutBatchController(
      TradingHttp tradingHttp,
      CheckoutSubmission checkoutSubmission,
      CheckoutBatchReader checkoutBatchReader) {
    this.tradingHttp = tradingHttp;
    this.checkoutSubmission = checkoutSubmission;
    this.checkoutBatchReader = checkoutBatchReader;
  }

  /** 恢复原请求或一次提交原报价，渠道创建发生在本地事务之后。 */
  @PostMapping("/api/me/checkout-batches")
  public ApiResponse<BatchViews.Submission> submit(
      HttpServletRequest request, @RequestBody CheckoutDtos.Submit submitInput) {
    return success(
        request,
        checkoutSubmission.submit(
            tradingHttp.command(request, TradingOperation.submitCheckout), submitInput));
  }

  /** 按买家归属返回原批次事实及可执行下一步。 */
  @GetMapping("/api/me/checkout-batches/{id}")
  public ApiResponse<BatchViews.Batch> read(HttpServletRequest request, @PathVariable String id) {
    return success(request, checkoutBatchReader.read(tradingHttp.actor(request), id));
  }

  /** 继续原批次付款，不延长原截止或另建支付号。 */
  @PostMapping("/api/me/checkout-batches/{id}/pay")
  public ApiResponse<BatchViews.Launch> launch(
      HttpServletRequest request,
      @PathVariable String id,
      @RequestBody CheckoutDtos.ExpectedVersion expectedVersionInput) {
    return success(
        request,
        checkoutSubmission.launch(
            tradingHttp.command(request, TradingOperation.launchCheckoutPayment),
            id,
            expectedVersionInput));
  }

  /** 保存原来源的取消意图，未知渠道结果继续原号恢复。 */
  @PostMapping("/api/me/checkout-batches/{id}/cancel")
  public ApiResponse<TradingReceipt> cancel(
      HttpServletRequest request,
      @PathVariable String id,
      @RequestBody CheckoutDtos.ExpectedVersion expectedVersionInput) {
    return success(
        request,
        checkoutSubmission.cancel(
            tradingHttp.command(request, TradingOperation.cancelCheckoutPayment),
            id,
            expectedVersionInput));
  }
}
