package com.mujian.backend.payment.service;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import org.springframework.stereotype.Service;

/** 外部退款调用只消费数据库领取的原义务，所有重试先查原号。 */
@Service
public class RefundExecution {
  private final RefundWorkTransaction refundWorkTransaction;
  private final PaymentGateway paymentGateway;
  private final UnformedBatchRefunds unformedBatchRefunds;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public RefundExecution(
      RefundWorkTransaction refundWorkTransaction,
      PaymentGateway paymentGateway,
      UnformedBatchRefunds unformedBatchRefunds) {
    this.refundWorkTransaction = refundWorkTransaction;
    this.paymentGateway = paymentGateway;
    this.unformedBatchRefunds = unformedBatchRefunds;
  }

  /** 沿原退款号提交或查询，异常保留原现金额度和义务。 */
  public void execute(String refundId) {
    RefundWorkTransaction.Ticket ticket = refundWorkTransaction.claim(refundId);
    if (ticket == null) return;
    try {
      PaymentGateway.RefundFact refundFact;
      if (ticket.refund().attemptCount() == 0) refundFact = paymentGateway.refund(ticket.request());
      else {
        refundFact = paymentGateway.queryRefund(ticket.request());
        if (refundFact.notFound()) refundFact = paymentGateway.refund(ticket.request());
      }
      String batchId = refundWorkTransaction.observe(refundId, refundFact);
      refundWorkTransaction.finish(ticket, null);
      if(batchId!=null) unformedBatchRefunds.reconcile(batchId);
    } catch (ApiFailure failure) {
      refundWorkTransaction.finish(ticket, failure.reason);
    }
  }
}
