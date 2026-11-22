package com.mujian.backend.custom.service;

import com.mujian.backend.common.support.SystemIdentity;
import com.mujian.backend.custom.mapper.CustomFulfillmentMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class CustomAfterSaleCompletion {
  private final CustomFulfillmentMapper customFulfillmentMapper;

  public CustomAfterSaleCompletion(CustomFulfillmentMapper customFulfillmentMapper) {
    this.customFulfillmentMapper = customFulfillmentMapper;
  }

  /** 订单已锁定。返工及退款各自保留原执行结果，最后完成的一方推进售后。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void reconcile(String order, LocalDateTime at) {
    CustomFulfillmentMapper.Sale sale = customFulfillmentMapper.activeSale(order);
    if (sale == null
        || sale.resolutionType() == null
        || sale.resolutionType().equals("platform_intervention")) return;
    boolean replacement = sale.resolutionType().equals("rework");
    if (replacement
        && (sale.replacementDeliveryId() == null
            || customFulfillmentMapper.selectCustomDeliveryRecordById(sale.replacementDeliveryId()).deliveredAt() == null)) return;
    if (sale.refundId() != null && !"success".equals(sale.refundStatus())) {
      if (replacement) customFulfillmentMapper.waitForRefund(order, at);
      return;
    }
    customFulfillmentMapper.markAfterSaleResolved(sale.id(), at);
    customFulfillmentMapper.closeCase(sale.caseId(), SystemIdentity.SYSTEM_USER_ID, sale.resolutionNote(), at);
    if ("full_refund".equals(sale.resolutionType())) customFulfillmentMapper.markCustomOrderCancelled(order, at);
    else customFulfillmentMapper.resumeCustomOrderAcceptance(order, replacement, at);
  }
}
