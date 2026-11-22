package com.mujian.backend.order.controller;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.coupon.vo.CouponViews;
import com.mujian.backend.order.dto.CheckoutDtos.QuoteInput;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.CheckoutViews.Quote;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class CheckoutQuoteController extends BaseController {
  private final TradingHttp tradingHttp;
  private final CheckoutQuotation checkoutQuotation;

  /** 复用交易身份、统一请求校验与公共报价。 */
  public CheckoutQuoteController(TradingHttp tradingHttp, CheckoutQuotation checkoutQuotation) {
    this.tradingHttp = tradingHttp;
    this.checkoutQuotation = checkoutQuotation;
  }

  /** 当前清单报价只读，不生成批次或库存占用。 */
  @PostMapping("/api/me/checkout-quotes")
  public ApiResponse<Quote> quote(HttpServletRequest request, @RequestBody QuoteInput quoteInput) {
    return success(request, checkoutQuotation.quote(tradingHttp.actor(request), quoteInput));
  }

  /** 候选券使用与报价相同的运费和金额基础。 */
  @PostMapping("/api/me/checkout-coupons")
  public ApiResponse<CouponViews.Choices> coupons(
      HttpServletRequest request, @RequestBody QuoteInput quoteInput) {
    return success(request, checkoutQuotation.coupons(tradingHttp.actor(request), quoteInput));
  }
}
