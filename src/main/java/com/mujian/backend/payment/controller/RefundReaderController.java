package com.mujian.backend.payment.controller;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.payment.service.RefundReader;
import com.mujian.backend.payment.vo.RefundViews;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class RefundReaderController extends BaseController {
  private final TradingHttp tradingHttp;
  private final RefundReader refundReader;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public RefundReaderController(TradingHttp tradingHttp, RefundReader refundReader) {
    this.tradingHttp = tradingHttp;
    this.refundReader = refundReader;
  }

  /** 按对象归属或对应菜单读取原业务详情。 */
  @GetMapping("/api/me/refunds/{id}")
  public ApiResponse<RefundViews.Refund> detail(
      HttpServletRequest request, @PathVariable String id) {
    return success(request, refundReader.owned(tradingHttp.actor(request), id));
  }
}
