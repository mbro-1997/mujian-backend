package com.mujian.backend.payment.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.order.dto.CheckoutDtos;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.payment.service.*;
import com.mujian.backend.payment.vo.MerchantAccountVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class MerchantController extends BaseController {
  private final TradingHttp tradingHttp;
  private final MerchantReader merchantReader;
  private final MerchantCommands merchantCommands;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public MerchantController(
      TradingHttp tradingHttp, MerchantReader merchantReader, MerchantCommands merchantCommands) {
    this.tradingHttp = tradingHttp;
    this.merchantReader = merchantReader;
    this.merchantCommands = merchantCommands;
  }

  /** 按本人归属读取分页订单，状态保持服务器事实。 */
  @GetMapping("/api/me/factory-wechat-account")
  public ApiResponse<MerchantAccountVO> mine(HttpServletRequest request) {
    return success(request, merchantReader.owned(tradingHttp.actor(request)));
  }

  /** 只使用获准渠道能力发起进件，缺接入能力明确不可用。 */
  @PostMapping("/api/me/factory-wechat-account/onboarding")
  public ApiResponse<MerchantAccountVO> onboarding(
      HttpServletRequest request, @RequestBody CheckoutDtos.ExpectedVersion expectedVersionInput) {
    return success(
        request,
        merchantCommands.onboarding(
            tradingHttp.command(request, TradingOperation.startFactoryMerchantOnboarding),
            expectedVersionInput.expectedVersion()));
  }

  /** 按可信原进件号查询并合并状态，不接受客户端传入渠道成功。 */
  @PostMapping("/api/me/factory-wechat-account/sync")
  public ApiResponse<MerchantAccountVO> sync(HttpServletRequest request) {
    return success(
        request,
        merchantCommands.sync(
            tradingHttp.command(request, TradingOperation.syncFactoryMerchantAccount), null));
  }

  /** 先按授权范围筛选统计，再读取当前页业务数据。 */
  @GetMapping("/api/admin/factory-wechat-accounts")
  public ApiResponse<PageResult<MerchantAccountVO>> page(
      HttpServletRequest request,
      @RequestParam(required = false) String factoryId,
      @RequestParam(required = false) String accountStatus,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer pageSize) {
    return success(
        request,
        merchantReader.page(
            tradingHttp.actor(request), factoryId, accountStatus, PageQuery.of(page, pageSize)));
  }

  /** 按对象归属或对应菜单读取原业务详情。 */
  @GetMapping("/api/admin/factory-wechat-accounts/{factoryId}")
  public ApiResponse<MerchantAccountVO> detail(
      HttpServletRequest request, @PathVariable String factoryId) {
    return success(request, merchantReader.admin(tradingHttp.actor(request), factoryId));
  }

  /** 管理操作同样按可信原号同步，不提供手工激活收款能力。 */
  @PostMapping("/api/admin/factory-wechat-accounts/{factoryId}/sync")
  public ApiResponse<MerchantAccountVO> adminSync(
      HttpServletRequest request, @PathVariable String factoryId) {
    return success(
        request,
        merchantCommands.sync(
            tradingHttp.command(request, TradingOperation.adminSyncMerchantAccount), factoryId));
  }
}
