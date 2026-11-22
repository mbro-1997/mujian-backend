package com.mujian.backend.delivery.dto;

import com.mujian.backend.casework.dto.CaseContact.Reference;
import com.mujian.backend.payment.dto.CashComponents;
import java.util.List;

/** 来源和金额分别按 08 方案、04 原支付分摊验证。 */
public record IssuePlan(int expectedVersion,String direction,String solutionSummary,Agreement agreement,
    List<Reference> references,ServiceRefund serviceRefund,String recovery,String freightResponsibility,
    CashComponents refundComponents,String depositReason,String refundBasis,String goodsLocation) {
  public record Agreement(String summary,String confirmedAt,List<String> evidenceIds) {}
  public record ServiceRefund(CashComponents components,String basis) {}
  /** 判断当前方案是否要求终止订单。 */
  public boolean terminates() { return "terminate".equals(direction); }
  /** 汇总本次方案计划处置的现金金额。 */
  public CashComponents plannedCash() {
    if (terminates()) return refundComponents;
    return serviceRefund==null ? new CashComponents(0,0,0) : serviceRefund.components();
  }
}
