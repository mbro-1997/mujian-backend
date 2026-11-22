package com.mujian.backend.payment.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.order.dto.OrderQuery;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.*;
import com.mujian.backend.payment.dto.*;
import com.mujian.backend.payment.mapper.*;
import com.mujian.backend.payment.vo.*;
import java.util.*;
import org.springframework.transaction.annotation.*;

/** 后台租赁订单、合并付款和退款追溯。 */
public interface AdminTradeReader {
  /** 按后台筛选条件分页查询租赁订单。 */
  PageResult<OrderViews.Summary> orders(String actor, OrderQuery orderQuery);

  /** 按订单标识读取后台可见的租赁详情。 */
  OrderViews.Detail order(String actor, String id, String trace);

  /** 分页查询合并付款记录。 */
  PageResult<BatchViews.Payment> payments(String actor, PaymentQuery paymentQuery);

  /** 查询付款明细及关联交易。 */
  PaymentTraceVO payment(String actor, String id, String trace);

  /** 分页查询退款记录。 */
  PageResult<RefundViews.Refund> refunds(String actor, RefundQuery refundQuery);

  /** 读取退款的支付来源和处理轨迹。 */
  RefundViews.Trace refund(String actor, String id, String trace);
}
