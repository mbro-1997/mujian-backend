package com.mujian.backend.delivery.controller;

import com.mujian.backend.common.api.ApiResponse;
import com.mujian.backend.common.base.BaseController;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.delivery.dto.IssueInput;
import com.mujian.backend.delivery.service.OrderIssues;
import com.mujian.backend.delivery.service.IssueEvidenceReview;
import com.mujian.backend.order.service.TradingHttp;
import com.mujian.backend.order.vo.TradingReceipt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

@RestController
public class OrderIssueController extends BaseController {
  private final com.mujian.backend.delivery.service.IssuePlans issuePlans;
  private final OrderIssues orderIssues;
  private final IssueEvidenceReview review;
  private final TradingHttp tradingHttp;

  /** 将问题提交接入本人交易会话。 */
  public OrderIssueController(OrderIssues orderIssues,TradingHttp tradingHttp, IssueEvidenceReview review, com.mujian.backend.delivery.service.IssuePlans issuePlans) {
    this.issuePlans=issuePlans;
    this.review=review;
    this.orderIssues=orderIssues;
    this.tradingHttp=tradingHttp;
  }

  /** 逐项记录收货或使用中问题。 */
  @PostMapping("/api/me/rental-orders/{orderId}/issues")
  public ApiResponse<TradingReceipt> report(HttpServletRequest request,@PathVariable String orderId,@RequestBody IssueInput input) {
    return success(request,orderIssues.submit(tradingHttp.command(request,TradingOperation.submitOrderIssue),orderId,input));
  }

  /** 登记履约异常的处理结果。 */
  @PostMapping("/api/admin/issues/{id}/resolution")
  public ApiResponse<TradingReceipt> resolve(HttpServletRequest request,@PathVariable String id,
      @RequestBody com.mujian.backend.delivery.dto.IssuePlan input) {
    return success(request,issuePlans.confirm(tradingHttp.command(request,TradingOperation.resolveOrderIssue),id,input));
  }

  /** 平台要求补证。 */
  @PostMapping("/api/admin/issues/{id}/request-evidence")
  public ApiResponse<TradingReceipt> requestEvidence(HttpServletRequest request,@PathVariable String id,@RequestBody IssueInput.Reason input) {
    return success(request,review.request(tradingHttp.command(request,TradingOperation.requestIssueEvidence),id,input));
  }

  /** 本人根据原问题追加材料。 */
  @PostMapping("/api/me/issues/{id}/supplement")
  public ApiResponse<TradingReceipt> supplement(HttpServletRequest request,@PathVariable String id,@RequestBody IssueInput.Supplement input) {
    return success(request,review.supplement(tradingHttp.command(request,TradingOperation.supplementOrderIssue),id,input));
  }
}
