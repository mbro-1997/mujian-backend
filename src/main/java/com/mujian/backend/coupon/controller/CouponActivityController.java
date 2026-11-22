package com.mujian.backend.coupon.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.coupon.service.CouponActivityService;
import com.mujian.backend.coupon.vo.ActivityViews.*;
import com.mujian.backend.order.service.TradingHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/coupon-activities")
public class CouponActivityController extends BaseController {
  private final CouponActivityService couponActivityService;
  private final TradingHttp tradingHttp;

  /** 为首页及券包提供同一套接口。 */
  public CouponActivityController(
      CouponActivityService couponActivityService, TradingHttp tradingHttp) {
    this.couponActivityService = couponActivityService;
    this.tradingHttp = tradingHttp;
  }

  /** 分页发现尚未领取的活动。 */
  @GetMapping
  public ApiResponse<PageResult<Offer>> offers(
      HttpServletRequest r,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        r, couponActivityService.offers(tradingHttp.actor(r), PageQuery.of(page, pageSize)));
  }

  /** 领取本账号本活动的一张券。 */
  @PostMapping("/{activityId}/claim")
  public ApiResponse<Claim> claim(HttpServletRequest r, @PathVariable String activityId) {
    return success(r, couponActivityService.claim(tradingHttp.actor(r), activityId));
  }
}
