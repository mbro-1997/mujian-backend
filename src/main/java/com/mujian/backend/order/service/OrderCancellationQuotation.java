package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.order.domain.*;
import com.mujian.backend.order.vo.CancellationQuoteVO;
import org.springframework.transaction.annotation.*;

/** 租赁订单取消试算。 */
public interface OrderCancellationQuotation {
  /** 按指定订单版本计算取消费用及可退金额。 */
  CancellationQuoteVO quote(String actor, String orderId, int expectedVersion);

  /** 传入已加锁订单时只读取原规则快照，不调用任何外部服务。 */
  CancellationQuoteData data(RentalOrderDO rentalOrderDO);
}
