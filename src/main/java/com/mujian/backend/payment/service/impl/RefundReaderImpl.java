package com.mujian.backend.payment.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.BatchViews;
import com.mujian.backend.payment.domain.PaymentOrderDO;
import com.mujian.backend.payment.domain.RefundOrderDO;
import com.mujian.backend.payment.mapper.*;
import com.mujian.backend.payment.projection.RefundCouponReturnProjection;
import com.mujian.backend.payment.service.*;
import com.mujian.backend.payment.vo.RefundViews;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 原买家可持续查询历史退款，后续账号受限不改变资金归属。 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RefundReaderImpl implements RefundReader {
  private final TradingAccess tradingAccess;
  private final RefundMapper refundMapper;
  private final PaymentOrderMapper paymentOrderMapper;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public RefundReaderImpl(
      TradingAccess tradingAccess,
      RefundMapper refundMapper,
      PaymentOrderMapper paymentOrderMapper) {
    this.tradingAccess = tradingAccess;
    this.refundMapper = refundMapper;
    this.paymentOrderMapper = paymentOrderMapper;
  }

  /** 核对本人或本工厂归属，合法历史不因新增交易限制而丢失。 */
  public RefundViews.Refund owned(String actor, String id) {
    tradingAccess.buyer(actor);
    RefundOrderDO refundOrderDORefund = refundMapper.selectRefundById(id, false);
    if (refundOrderDORefund == null) throw missing();
    PaymentOrderDO paymentOrderDO =
        paymentOrderMapper.selectPaymentById(refundOrderDORefund.paymentOrderId(), false);
    if (paymentOrderDO == null || !paymentOrderDO.buyerUserId().equals(actor)) throw missing();
    return authorized(refundOrderDORefund);
  }

  /** 仅供外层已授权的管理查询使用，不接受HTTP传入buyer。 */
  public RefundViews.Refund authorized(RefundOrderDO refundOrderDO) {
    return authorizedList(List.of(refundOrderDO)).get(0);
  }

  /** 一次读取全部原支付返券事实，列表行数不增加数据库往返。 */
  public List<RefundViews.Refund> authorizedList(List<RefundOrderDO> refundOrderDOList) {
    if (refundOrderDOList.isEmpty()) return List.of();
    Set<String> paymentIds = new TreeSet<>();
    for (RefundOrderDO refundOrderDO : refundOrderDOList)
      paymentIds.add(refundOrderDO.paymentOrderId());
    Map<String, BatchViews.CouponReturn> couponReturnMapCouponReturns = new HashMap<>();
    for (RefundCouponReturnProjection refundCouponReturnProjection :
        refundMapper.selectCouponReturnsByPayments(paymentIds)) {
      String status =
          refundCouponReturnProjection.userCouponId() == null
              ? TradingState.none.name()
              : TradingState.returned.name().equals(refundCouponReturnProjection.usageStatus())
                  ? TradingState.returned.name()
                  : TradingState.not_returned.name();
      couponReturnMapCouponReturns.put(
          refundCouponReturnProjection.paymentId(),
          new BatchViews.CouponReturn(
              status,
              refundCouponReturnProjection.userCouponId(),
              time(refundCouponReturnProjection.returnedAt()),
              time(refundCouponReturnProjection.validUntil())));
    }
    List<RefundViews.Refund> refundListResult = new ArrayList<>();
    for (RefundOrderDO refundOrderDORefund : refundOrderDOList) {
      BatchViews.CouponReturn couponReturn =
          couponReturnMapCouponReturns.get(refundOrderDORefund.paymentOrderId());
      if (couponReturn == null) throw new IllegalStateException("refund original payment missing");
      refundListResult.add(
          new RefundViews.Refund(
              couponReturn,
              refundOrderDORefund.id(),
              refundOrderDORefund.refundNo(),
              refundOrderDORefund.bizOrderType(),
              refundOrderDORefund.bizOrderId(),
              refundOrderDORefund.paymentOrderId(),
              refundOrderDORefund.paymentChannelSuborderId(),
              refundOrderDORefund.refundType(),
              refundOrderDORefund.refundStatus(),
              "custom_order".equals(refundOrderDORefund.bizOrderType())?refundOrderDORefund.customGoodsRefundAmountFen():null,
              refundOrderDORefund.rentRefundAmountFen(),
              refundOrderDORefund.depositRefundAmountFen(),
              refundOrderDORefund.freightRefundAmountFen(),
              refundOrderDORefund.refundAmountFen(),
              refundOrderDORefund.refundReason(),
              time(refundOrderDORefund.requestedAt()),
              time(refundOrderDORefund.successAt()),
              refundOrderDORefund.lastErrorCode()));
    }
    return List.copyOf(refundListResult);
  }
}
