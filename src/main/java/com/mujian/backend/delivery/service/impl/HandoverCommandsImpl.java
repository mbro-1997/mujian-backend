package com.mujian.backend.delivery.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.mapper.*;
import com.mujian.backend.delivery.policy.*;
import com.mujian.backend.delivery.service.*;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.prop.service.FulfillmentInventory;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 原订单根内并发裁决出库/取消和人工/自动确认。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class HandoverCommandsImpl implements HandoverCommands {
  private final FulfillmentAccess fulfillmentAccess;
  private final TradingRoots tradingRoots;
  private final TradingOperations tradingOperations;
  private final FulfillmentProtection fulfillmentProtection;
  private final OutboundRoot outboundRoot;
  private final RentalOrderMapper rentalOrderMapper;
  private final HandoverMapper handoverMapper;
  private final FulfillmentMapper fulfillmentMapper;
  private final FulfillmentInventory fulfillmentInventory;
  private final FulfillmentEvents fulfillmentEvents;
  private final Clock clock;

  /** 复用库存和订单锁，不在事务内调用外部存储或支付。 */
  public HandoverCommandsImpl(
      FulfillmentAccess fulfillmentAccess,
      TradingRoots tradingRoots,
      TradingOperations tradingOperations,
      FulfillmentProtection fulfillmentProtection,
      OutboundRoot outboundRoot,
      RentalOrderMapper rentalOrderMapper,
      HandoverMapper handoverMapper,
      FulfillmentMapper fulfillmentMapper,
      FulfillmentInventory fulfillmentInventory,
      FulfillmentEvents fulfillmentEvents,
      Clock clock) {
    this.fulfillmentAccess = fulfillmentAccess;
    this.tradingRoots = tradingRoots;
    this.tradingOperations = tradingOperations;
    this.fulfillmentProtection = fulfillmentProtection;
    this.outboundRoot = outboundRoot;
    this.rentalOrderMapper = rentalOrderMapper;
    this.handoverMapper = handoverMapper;
    this.fulfillmentMapper = fulfillmentMapper;
    this.fulfillmentInventory = fulfillmentInventory;
    this.fulfillmentEvents = fulfillmentEvents;
    this.clock = clock;
  }

  /** 所有实体写入失败均回滚至原出库草稿。 */
  @Override
  public TradingReceipt dispatch(TradingCommand command, String orderId, int version) {
    if (command.operation() != TradingOperation.confirmOutbound) throw ApiFailure.invalid();
    RentalOrderDO order = fulfillmentAccess.order(command.actor(), orderId, true, null);
    tradingRoots.lock(order.checkoutBatchId());
    TradingReceipt tradingReceipt = tradingOperations.replay(command);
    if (tradingReceipt != null) return tradingReceipt;
    fulfillmentProtection.requireNormal(orderId);
    OrderOutboundDO outbound = outboundRoot.pending(orderId, command.actor(), version);
    if (handoverMapper.hasIncompleteOutbound(orderId))
      throw conflict(FulfillmentReason.CHECK_INCOMPLETE.name());
    int photos = handoverMapper.countOutboundPhotos(orderId);
    if (photos < 3) throw conflict(FulfillmentReason.OUTBOUND_PHOTOS_REQUIRED.name());
    LocalDateTime at = now(clock);
    FulfillmentTime.ReceiptWindow window =
        FulfillmentTime.receipt(TransportServiceMode.valueOf(outbound.outboundServiceMode()), at);
    OrderReceiptDO receipt =
        new OrderReceiptDO(
            Ids.next(),
            orderId,
            outbound.outboundTransportLegId(),
            order.buyerUserId(),
            ReceiptCheckStatus.pending.name(),
            window.startAt(),
            window.deadlineAt(),
            null,
            0,
            at,
            at);
    requireOne(handoverMapper.dispatchLeg(outbound.outboundTransportLegId(), orderId, at));
    requireOne(
        handoverMapper.completeOutbound(outbound.id(), version, photos, command.actor(), at));
    requireOne(
        handoverMapper.changeOrderState(
            orderId, TradingState.accepted.name(), TradingState.fulfilling.name(), at));
    requireOne(handoverMapper.dispatchDelivery(receipt, at));
    handoverMapper.insertReceipt(receipt);
    fulfillmentInventory.transfer(
        order,
        fulfillmentMapper.selectCheckItemsByOrder(orderId, false),
        command.actor(),
        FulfillmentRole.factory.name(),
        true);
    fulfillmentEvents.append(
        "rental:" + orderId + ":outbound",
        orderId,
        outbound.outboundTransportLegId(),
        TradingResource.order_outbound_record.name(),
        outbound.id(),
        FulfillmentEventType.outbound_completed,
        at,
        command.actor(),
        FulfillmentRole.factory,
        "工厂已完成道具出库交接");
    return tradingOperations.record(
        command, TradingResource.order_outbound_record.name(), outbound.id(), version + 1);
  }

  /** 主动收货不以预计开始时间阻止用户，已进入暂停或问题时拒绝。 */
  @Override
  public TradingReceipt receive(TradingCommand command, String orderId, int version) {
    if (command.operation() != TradingOperation.confirmReceipt) throw ApiFailure.invalid();
    RentalOrderDO order = fulfillmentAccess.order(command.actor(), orderId, false, null);
    tradingRoots.lock(order.checkoutBatchId());
    TradingReceipt tradingReceipt = tradingOperations.replay(command);
    if (tradingReceipt != null) return tradingReceipt;
    fulfillmentProtection.requireNormal(orderId);
    OrderReceiptDO receipt = fulfillmentMapper.selectOrderReceiptDOByOrder(orderId);
    if (receipt == null) throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    if (receipt.version() != version) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    finish(receipt, command.actor(), false, now(clock));
    return tradingOperations.record(
        command, TradingResource.order_receipt_check.name(), receipt.id(), version + 1);
  }

  /** 任意重启后读取原到期事实，门禁未就绪不推进。 */
  @Override
  public boolean receiveDue(String orderId) {
    RentalOrderDO order = rentalOrderMapper.selectOrderById(orderId);
    if (order == null) return false;
    tradingRoots.lock(order.checkoutBatchId());
    if (!fulfillmentProtection.writable(fulfillmentProtection.facts(orderId))) return false;
    OrderReceiptDO receipt = fulfillmentMapper.selectOrderReceiptDOByOrder(orderId);
    LocalDateTime at = now(clock);
    if (receipt == null
        || !ReceiptCheckStatus.pending.name().equals(receipt.checkStatus())
        || receipt.deadlineAt() == null
        || at.isBefore(receipt.deadlineAt())) return false;
    finish(receipt, SystemIdentity.SYSTEM_USER_ID, true, at);
    return true;
  }

  /** 运输与核验共用原事务，操作者区分本人和系统。 */
  private void finish(OrderReceiptDO receipt, String actor, boolean automatic, LocalDateTime at) {
    String orderId = receipt.rentalOrderId();
    requireOne(handoverMapper.completeLeg(receipt.outboundTransportLegId(), orderId, at));
    requireOne(
        handoverMapper.confirmReceipt(
            receipt.id(),
            receipt.version(),
            (automatic ? ReceiptCheckStatus.auto_confirmed : ReceiptCheckStatus.confirmed).name(),
            at));
    requireOne(handoverMapper.receiveDelivery(orderId, at));
    fulfillmentEvents.append(
        "rental:" + orderId + ":receipt",
        orderId,
        receipt.outboundTransportLegId(),
        TradingResource.order_receipt_check.name(),
        receipt.id(),
        automatic
            ? FulfillmentEventType.receipt_auto_confirmed
            : FulfillmentEventType.receipt_confirmed,
        at,
        actor,
        automatic ? FulfillmentRole.system : FulfillmentRole.buyer,
        automatic ? "收货核验期已到，系统确认收货" : "买家已确认收货");
  }

  /** 单向条件更新不匹配即返回冲突，上层事务撤回已写事实。 */
  private void requireOne(int rows) {
    if (rows != 1) throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
  }
}
