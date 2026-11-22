package com.mujian.backend.payment.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.delivery.policy.FulfillmentProtection;
import com.mujian.backend.payment.domain.*;
import com.mujian.backend.payment.dto.CashComponents;
import com.mujian.backend.payment.mapper.RefundMapper;
import com.mujian.backend.payment.policy.RefundIdentity;
import com.mujian.backend.payment.projection.RefundExposureProjection;
import java.time.*;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 各退款来源共用科目额度保留和稳定义务键；调用方已持有原支付及业务根锁。 */
@Service
public class RefundObligations {
  private final RefundMapper refundMapper;
  private final Clock clock;
  private final FulfillmentProtection fulfillmentProtection;

  /** 退款金额仅来自已核对现金分摊，不接受前端声明实收。 */
  public RefundObligations(
      RefundMapper refundMapper, Clock clock, FulfillmentProtection fulfillmentProtection) {
    this.refundMapper = refundMapper;
    this.clock = clock;
    this.fulfillmentProtection = fulfillmentProtection;
  }

  /** 零退款不产生渠道资金单；非零额度与取消/拒绝/批次退款决策同事务。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public String reserve(
      String actor,
      String businessType,
      String businessId,
      String reason,
      String explanation,
      ChannelSuborderDO channelSuborderDOSuborder,
      CashComponents cashComponentsOriginal,
      CashComponents cashComponentsRefund) {
    return reserve(
        actor,
        businessType,
        businessId,
        businessType,
        businessId,
        reason,
        explanation,
        channelSuborderDOSuborder,
        cashComponentsOriginal,
        cashComponentsRefund);
  }

  /** 正常退押和运费与旧退款共用额度及执行器，义务来源与订单归属分别校验。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public String reserve(
      String actor,
      String businessType,
      String businessId,
      String sourceType,
      String sourceId,
      String reason,
      String explanation,
      ChannelSuborderDO channelSuborderDOSuborder,
      CashComponents cashComponentsOriginal,
      CashComponents cashComponentsRefund) {
    boolean dispute="order_claim".equals(sourceType) && "deposit_release".equals(reason)
        || "arbitration_decision".equals(sourceType) && "arbitration_refund".equals(reason);
    boolean normal = !dispute && (
        TradingRefundType.deposit_release.name().equals(reason)
            || TradingRefundType.freight_pool_release.name().equals(reason));
    if(dispute){
      if(!"rental_order".equals(businessType) || !businessId.equals(channelSuborderDOSuborder.rentalOrderId())
          || !refundMapper.selectNormalRefundSourceExists(sourceType,sourceId,businessId)
          || "order_claim".equals(sourceType) && (cashComponentsRefund.rentFen()!=0 || cashComponentsRefund.freightFen()!=0))
        throw new IllegalStateException("invalid dispute refund source");
    }else if (normal) {
      if (!TradingResource.rental_order.name().equals(businessType)
          || !businessId.equals(channelSuborderDOSuborder.rentalOrderId())
          || !refundMapper.selectNormalRefundSourceExists(sourceType, sourceId, businessId)
          || cashComponentsRefund.rentFen() != 0
          || TradingRefundType.deposit_release.name().equals(reason)
              && cashComponentsRefund.freightFen() != 0
          || TradingRefundType.freight_pool_release.name().equals(reason)
              && cashComponentsRefund.depositFen() != 0)
        throw new IllegalStateException("invalid normal refund source");
    } else if ("extension_request".equals(sourceType) && Set.of("extension_rejected","extension_cancelled").contains(reason)) {
      if (!"rental_order".equals(businessType) || !businessId.equals(channelSuborderDOSuborder.rentalOrderId())
          || !refundMapper.extensionSuborderMatches(sourceId,channelSuborderDOSuborder.id(),businessId)) throw new IllegalStateException("invalid extension refund source");
    } else if ("admin_case_action".equals(sourceType) && "customer_service_refund".equals(reason)) {
      if (!"rental_order".equals(businessType) || !businessId.equals(channelSuborderDOSuborder.rentalOrderId())
          || !refundMapper.selectNormalRefundSourceExists(sourceType,sourceId,businessId))
        throw new IllegalStateException("invalid issue refund source");
    } else if (!businessType.equals(sourceType) || !businessId.equals(sourceId)) {
      throw new IllegalStateException("invalid original refund source");
    }
    // 零金额也校验来源种类，不能用零金额绕过正式来源边界。
    String key = RefundIdentity.key(sourceType, sourceId, channelSuborderDOSuborder.id(), reason);
    if (cashComponentsRefund.rentFen() < 0
        || cashComponentsRefund.depositFen() < 0
        || cashComponentsRefund.freightFen() < 0
        || cashComponentsOriginal.rentFen() < 0
        || cashComponentsOriginal.depositFen() < 0
        || cashComponentsOriginal.freightFen() < 0)
      throw new IllegalStateException("invalid refund components");
    if (cashComponentsRefund.totalFen() == 0) {
      if (normal) fulfillmentProtection.requireNormal(businessId);
      return null;
    }
    if (!TradingState.success.name().equals(channelSuborderDOSuborder.paymentStatus())
        || channelSuborderDOSuborder.paidAmountFen() != cashComponentsOriginal.totalFen()
        || channelSuborderDOSuborder.channelSubTransactionId() == null)
      throw new IllegalStateException("refund source not received");
    RefundOrderDO refundOrderDOExisting = refundMapper.selectRefundBySourceKey(key);
    if (refundOrderDOExisting != null) {
      if (refundOrderDOExisting.rentRefundAmountFen() != cashComponentsRefund.rentFen()
          || refundOrderDOExisting.depositRefundAmountFen() != cashComponentsRefund.depositFen()
          || refundOrderDOExisting.freightRefundAmountFen() != cashComponentsRefund.freightFen())
        throw new IllegalStateException("refund obligation changed");
      return refundOrderDOExisting.id();
    }
    if (normal) fulfillmentProtection.requireNormal(businessId);
    if (Set.of(PaymentSuborderStatus.sharing.name(), PaymentSuborderStatus.settled.name())
        .contains(channelSuborderDOSuborder.channelSuborderStatus()))
      throw new IllegalStateException("refund source already in settlement");
    RefundExposureProjection refundExposureProjectionExposure =
        refundMapper.selectRefundExposureBySuborder(channelSuborderDOSuborder.id());
    if (refundExposureProjectionExposure.rentFen() + cashComponentsRefund.rentFen()
            > cashComponentsOriginal.rentFen()
        || refundExposureProjectionExposure.depositFen() + cashComponentsRefund.depositFen()
            > cashComponentsOriginal.depositFen()
        || refundExposureProjectionExposure.freightFen() + cashComponentsRefund.freightFen()
            > cashComponentsOriginal.freightFen()
        || refundExposureProjectionExposure.totalFen() + cashComponentsRefund.totalFen()
            > channelSuborderDOSuborder.paidAmountFen())
      throw new IllegalStateException("refund exceeds original cash");
    String id = Ids.next();
    LocalDateTime at = now(clock);
    refundMapper.insertRefund(
        new RefundOrderDO(
            id,
            id,
            key,
            businessType,
            businessId,
            TradingResource.rental_order.name().equals(businessType) ? businessId : null,
            channelSuborderDOSuborder.paymentOrderId(),
            channelSuborderDOSuborder.id(),
            reason,
            TradingState.pending.name(),
            channelSuborderDOSuborder.channelSubTransactionId(),
            channelSuborderDOSuborder.channelSubOrderNo(),
            id,
            null,
            0,
            cashComponentsRefund.rentFen(),
            cashComponentsRefund.depositFen(),
            cashComponentsRefund.freightFen(),
            cashComponentsRefund.totalFen(),
            explanation,
            actor,
            at,
            null,
            null,
            null,
            at,
            at,
            at,
            null,
            0,
            null,
            0));
    return id;
  }
}
