package com.mujian.backend.customer.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.customer.service.ServiceContextService;
import com.mujian.backend.customer.vo.ServiceViews.*;
import com.mujian.backend.order.service.TradingHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me")
public class ServiceContextController extends BaseController {
  private final ServiceContextService serviceContextService;
  private final TradingHttp tradingHttp;

  /** 所有来源按当前会话服务。 */
  public ServiceContextController(
      ServiceContextService serviceContextService, TradingHttp tradingHttp) {
    this.serviceContextService = serviceContextService;
    this.tradingHttp = tradingHttp;
  }

  /** 客服中心列出可咨询对象。 */
  @GetMapping("/service-sources")
  public ApiResponse<PageResult<ServiceSource>> sources(
      HttpServletRequest r,
      @RequestParam(required = false) String sourceType,
      @RequestParam(required = false) String sourceId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        r,
        serviceContextService.sources(
            tradingHttp.actor(r), sourceType, sourceId, PageQuery.of(page, pageSize)));
  }

  /** 原生客服前准备一次性上下文，不表示已发送。 */
  @PostMapping("/service-contexts")
  public ApiResponse<ContextCard> prepare(
      HttpServletRequest r,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody SourceInput input) {
    return success(r, serviceContextService.prepare(tradingHttp.actor(r), key, input.source()));
  }

  /** 读取本人历史上下文，不返回令牌。 */
  @GetMapping("/service-contexts/{id}")
  public ApiResponse<Context> detail(HttpServletRequest r, @PathVariable String id) {
    return success(r, serviceContextService.detail(tradingHttp.actor(r), id));
  }
}
