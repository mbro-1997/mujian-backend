package com.mujian.backend.coupon.controller;

import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.coupon.service.CouponReader;
import com.mujian.backend.coupon.vo.CouponViews.*;
import com.mujian.backend.order.service.TradingHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class CouponController extends BaseController {
  private final TradingHttp tradingHttp;
  private final CouponReader couponReader;

  /** 装配本人券查询入口。 */
  public CouponController(TradingHttp tradingHttp, CouponReader couponReader) {
    this.tradingHttp = tradingHttp;
    this.couponReader = couponReader;
  }

  /** 分页及可用/不可用全量计数。 */
  @GetMapping("/api/me/coupons")
  public ApiResponse<CouponPage> list(
      HttpServletRequest r,
      @RequestParam(defaultValue = "available") String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return new ApiResponse<>(
        D2Codes.CODE_OK,
        "成功",
        couponReader.page(tradingHttp.actor(r), status, PageQuery.of(page, pageSize)),
        (String) r.getAttribute("traceId"));
  }

  /** 本人单券详情。 */
  @GetMapping("/api/me/coupons/{id}")
  public ApiResponse<Coupon> detail(HttpServletRequest r, @PathVariable String id) {
    return new ApiResponse<>(
        D2Codes.CODE_OK,
        "成功",
        couponReader.detail(tradingHttp.actor(r), id),
        (String) r.getAttribute("traceId"));
  }

  /** 已使用及返还记录，不把未付释放伪装成使用。 */
  @GetMapping("/api/me/coupon-use-records")
  public ApiResponse<PageResult<UseRecord>> records(
      HttpServletRequest r,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return new ApiResponse<>(
        D2Codes.CODE_OK,
        "成功",
        couponReader.uses(tradingHttp.actor(r), PageQuery.of(page, pageSize)),
        (String) r.getAttribute("traceId"));
  }
}
