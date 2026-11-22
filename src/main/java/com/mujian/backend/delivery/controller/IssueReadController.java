package com.mujian.backend.delivery.controller;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.delivery.service.IssueReader;
import com.mujian.backend.delivery.vo.IssueView;
import com.mujian.backend.order.service.TradingHttp;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class IssueReadController extends BaseController {
  private final IssueReader issueReader;
  private final TradingHttp tradingHttp;

  public IssueReadController(IssueReader issueReader,TradingHttp tradingHttp) {
    this.issueReader=issueReader;
    this.tradingHttp=tradingHttp;
  }

  /** 来源路径已由权限边界匹配，用户不可传入角色或本人 ID。 */
  @GetMapping({"/api/me/issues","/api/admin/issues"})
  public ApiResponse<PageResult<IssueView>> list(HttpServletRequest request,@RequestParam(required=false) String orderId,
      @RequestParam(required=false) String status,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) {
    return success(request,issueReader.page(tradingHttp.actor(request),request.getRequestURI().startsWith("/api/admin/"),
        orderId,status,PageQuery.of(page,pageSize)));
  }

  /** 返回本人履约异常详情。 */
  @GetMapping({"/api/me/issues/{id}","/api/admin/issues/{id}"})
  public ApiResponse<IssueView> detail(HttpServletRequest request,@PathVariable String id) {
    return success(request,issueReader.detail(tradingHttp.actor(request),request.getRequestURI().startsWith("/api/admin/"),id));
  }
}
