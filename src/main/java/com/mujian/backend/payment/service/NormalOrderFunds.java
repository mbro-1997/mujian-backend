package com.mujian.backend.payment.service;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.delivery.policy.FulfillmentProtection;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.service.TradingRoots;
import com.mujian.backend.payment.domain.*;
import com.mujian.backend.payment.dto.CashComponents;
import com.mujian.backend.payment.mapper.*;
import com.mujian.backend.payment.policy.RefundIdentity;
import com.mujian.backend.payment.projection.RefundExposureProjection;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 退押与运费只在同订单、同原实收分摊上建立，渠道由原退款执行器恢复。 */
@Service
public class NormalOrderFunds {
  private final NormalFundsMapper normalFundsMapper;
  private final RefundMapper refundMapper;
  private final RefundObligations refundObligations;
  private final FulfillmentProtection fulfillmentProtection;
  private final Clock clock;

  /** 领域金额/来源验证集中处理，外部退款不在交易中发生。 */
  public NormalOrderFunds(
      NormalFundsMapper normalFundsMapper,
      RefundMapper refundMapper,
      RefundObligations refundObligations,
      FulfillmentProtection fulfillmentProtection,
      Clock clock) {
    this.normalFundsMapper = normalFundsMapper;
    this.refundMapper = refundMapper;
    this.refundObligations = refundObligations;
    this.fulfillmentProtection = fulfillmentProtection;
    this.clock = clock;
  }

  /** 核验关闭后保留未扣/未退押金，0元只核实原分摊。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<String> deposit(
      TradingRoots.Locked locked, RentalOrderDO order, String checkId, String actor) {
    List<String> ids =
        reserve(
            locked,
            order,
            checkId,
            actor,
            true,
            order.depositPaidAmountFen() - order.depositDeductedAmountFen());
    normalFundsMapper.updateDepositResponsibility(order.id(), now(clock));
    return ids;
  }

  /** 申报已经原额核准后，仅就本单实际应退余额建立义务。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public List<String> freight(
      TradingRoots.Locked locked,
      RentalOrderDO order,
      String statementId,
      String actor,
      int refundable) {
    return reserve(locked, order, statementId, actor, false, refundable);
  }

  /** D3租赁初付按每厂单独原子单，核对支付、分摊、订单三方及保持额度。 */
  private List<String> reserve(
      TradingRoots.Locked locked,
      RentalOrderDO order,
      String sourceId,
      String actor,
      boolean deposit,
      int target) {
    fulfillmentProtection.requireNormal(order.id());
    List<NormalFundsMapper.Allocation> allocations =
        normalFundsMapper.selectAllocations(order.id());
    if (allocations.size() != 1 || target < 0) throw inconsistent();
    NormalFundsMapper.Allocation allocation = allocations.get(0);
    ChannelSuborderDO channelSuborderDO =
        locked.suborders().stream()
            .filter(s -> s.id().equals(allocation.suborderId()) && order.id().equals(s.rentalOrderId()))
            .findFirst()
            .orElseThrow(this::inconsistent);
    CashComponents original = new CashComponents(allocation.rent(), allocation.deposit(), allocation.freight());
    if (allocation.other() != 0
        || allocation.total() != original.totalFen()
        || allocation.total() != channelSuborderDO.paidAmountFen()
        || !allocation.paymentId().equals(channelSuborderDO.paymentOrderId())
        || !TradingState.success.name().equals(channelSuborderDO.paymentStatus())
        || !order.factoryId().equals(channelSuborderDO.factoryId())
        ) throw inconsistent();
    String
        type =
            (deposit
                    ? RefundObligationSource.return_check
                    : RefundObligationSource.expense_statement)
                .name(),
        reason =
            (deposit ? TradingRefundType.deposit_release : TradingRefundType.freight_pool_release)
                .name();
    RefundOrderDO existing =
        refundMapper.selectRefundBySourceKey(RefundIdentity.key(type, sourceId, channelSuborderDO.id(), reason));
    RefundExposureProjection exposure = refundMapper.selectRefundExposureBySuborder(channelSuborderDO.id());
    int prior = Math.toIntExact(deposit ? exposure.depositFen() : exposure.freightFen());
    if (existing != null)
      prior -= deposit ? existing.depositRefundAmountFen() : existing.freightRefundAmountFen();
    int amount = target - prior;
    if (amount < 0 || target > (deposit ? allocation.deposit() : allocation.freight())) throw inconsistent();
    String id =
        refundObligations.reserve(
            actor,
            TradingResource.rental_order.name(),
            order.id(),
            type,
            sourceId,
            reason,
            deposit ? "正常归还退押金" : "运费核准后退还余额",
            channelSuborderDO,
            original,
            new CashComponents(0, deposit ? amount : 0, deposit ? 0 : amount));
    return id == null ? List.of() : List.of(id);
  }

  /** 仅在持有原订单根且实际争议保护齐备时更新最终完成。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void reconcile(String orderId) {
    if (fulfillmentProtection.writable(fulfillmentProtection.facts(orderId))) {
      normalFundsMapper.settleNoPool(orderId, now(clock));
      normalFundsMapper.tryComplete(orderId, now(clock));
    }
  }

  /** 在订单锁内核对已保存的关闭步骤。 */
  public boolean requiresClosureRepair(String orderId) {
    return normalFundsMapper.requiresClosureRepair(orderId);
  }

  /** 来源缺失不能解释为无需处理。 */
  private ApiFailure inconsistent() {
    return new ApiFailure(
        500, D2Codes.CODE_DATA_INTEGRITY_ERROR, FulfillmentReason.FULFILLMENT_DATA_INCONSISTENT.name());
  }
}
