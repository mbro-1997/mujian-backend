package com.mujian.backend.review.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.review.dto.ReviewInput;
import com.mujian.backend.review.service.OrderReviewService;
import com.mujian.backend.review.vo.ReviewViews.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class OrderReviewController extends BaseController {
  private final OrderReviewService orderReviewService;
  private final TradingHttp tradingHttp;

  /** 所有入口从会话取得身份。 */
  public OrderReviewController(OrderReviewService orderReviewService, TradingHttp tradingHttp) {
    this.orderReviewService = orderReviewService;
    this.tradingHttp = tradingHttp;
  }

  /** 从租赁或定制订单进入评价。 */
  @GetMapping("/review-orders/{id}/eligibility")
  public ApiResponse<Entry> entry(
      HttpServletRequest r, @PathVariable String id, @RequestParam String businessType) {
    return success(r, orderReviewService.entry(tradingHttp.actor(r), businessType, id));
  }

  /** 按当前方向保存真实评价。 */
  @PostMapping("/order-reviews")
  public ApiResponse<Review> submit(HttpServletRequest r, @RequestBody ReviewInput input) {
    return success(r, orderReviewService.submit(tradingHttp.actor(r), input));
  }

  /** 支持本人或工厂管理页按筛选分页。 */
  @GetMapping("/order-reviews")
  public ApiResponse<PageResult<Review>> page(
      HttpServletRequest r,
      @RequestParam(required = false) String businessType,
      @RequestParam(required = false) String direction,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        r,
        orderReviewService.page(
            tradingHttp.actor(r), businessType, direction, PageQuery.of(page, pageSize)));
  }

  /** 读取本次评价与奖励当前状态。 */
  @GetMapping("/order-reviews/{id}")
  public ApiResponse<Review> detail(HttpServletRequest r, @PathVariable String id) {
    return success(r, orderReviewService.detail(tradingHttp.actor(r), id));
  }
}
