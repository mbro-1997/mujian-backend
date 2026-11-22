package com.mujian.backend.order.controller;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.TradingReceipt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class TradingOperationController extends BaseController {
  private final TradingHttp tradingHttp;
  private final TradingOperations tradingOperations;

  /** 复用统一身份和持久回执。 */
  public TradingOperationController(TradingHttp tradingHttp, TradingOperations tradingOperations) {
    this.tradingHttp = tradingHttp;
    this.tradingOperations = tradingOperations;
  }

  /** 恢复小程序本人原交易命令。 */
  @GetMapping("/api/me/trading-operations/by-request/{requestId}")
  public ApiResponse<TradingReceipt> recover(
      HttpServletRequest request, @PathVariable String requestId) {
    return success(
        request, tradingOperations.recover(tradingHttp.actor(request), requestId, false));
  }

  /** 管理恢复同时校验恢复权限和原动作权限。 */
  @GetMapping("/api/admin/trading-operations/by-request/{requestId}")
  public ApiResponse<TradingReceipt> recoverAdmin(
      HttpServletRequest request, @PathVariable String requestId) {
    return success(request, tradingOperations.recover(tradingHttp.actor(request), requestId, true));
  }
}
