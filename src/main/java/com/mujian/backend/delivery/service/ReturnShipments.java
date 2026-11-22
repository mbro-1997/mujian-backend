package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.dto.FulfillmentDtos;
import com.mujian.backend.delivery.mapper.*;
import com.mujian.backend.delivery.policy.*;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.TradingReceipt;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 用户送回及工厂实际开始取回的共同事务。 */
@Service
public class ReturnShipments {
  private final OverdueFees overdueFees;
  private final FulfillmentAccess fulfillmentAccess;
  private final TradingRoots tradingRoots;
  private final TradingOperations tradingOperations;
  private final FulfillmentProtection fulfillmentProtection;
  private final ReturnShipmentRoot returnShipmentRoot;
  private final ReturnShipmentMapper returnShipmentMapper;
  private final HandoverMapper handoverMapper;
  private final FulfillmentMapper fulfillmentMapper;
  private final FulfillmentEvents fulfillmentEvents;
  private final Clock clock;
  private final com.mujian.backend.order.service.OrderExtensions extensions;

  /** 沿用原订单锁和幂等回执，发出后不允许改写首次时刻。 */
  public ReturnShipments(
      FulfillmentAccess fulfillmentAccess,
      TradingRoots tradingRoots,
      TradingOperations tradingOperations,
      FulfillmentProtection fulfillmentProtection,
      ReturnShipmentRoot returnShipmentRoot,
      ReturnShipmentMapper returnShipmentMapper,
      HandoverMapper handoverMapper,
      FulfillmentMapper fulfillmentMapper,
      FulfillmentEvents fulfillmentEvents,
      Clock clock, com.mujian.backend.order.service.OrderExtensions extensions,OverdueFees overdueFees) {
    this.overdueFees=overdueFees;
    this.extensions=extensions;
    this.fulfillmentAccess = fulfillmentAccess;
    this.tradingRoots = tradingRoots;
    this.tradingOperations = tradingOperations;
    this.fulfillmentProtection = fulfillmentProtection;
    this.returnShipmentRoot = returnShipmentRoot;
    this.returnShipmentMapper = returnShipmentMapper;
    this.handoverMapper = handoverMapper;
    this.fulfillmentMapper = fulfillmentMapper;
    this.fulfillmentEvents = fulfillmentEvents;
    this.clock = clock;
  }

  /** 有效草稿不产生事件或预计核验时刻，用户实际发出须给可追溯的凭证或交接说明。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt buyer(
      TradingCommand command, String orderId, FulfillmentDtos.ShipmentInput shipmentInput) {
    boolean send = command.operation() == TradingOperation.sendReturnShipment;
    if (!send && command.operation() != TradingOperation.saveReturnShipment)
      throw ApiFailure.invalid();
    RentalOrderDO order = fulfillmentAccess.order(command.actor(), orderId, false, null);
    tradingRoots.lock(order.checkoutBatchId());
    TradingReceipt tradingReceipt = tradingOperations.replay(command);
    if (tradingReceipt != null) return tradingReceipt;
    fulfillmentProtection.requireReturn(orderId);
    OrderReturnShipmentDO draft =
        returnShipmentRoot.draft(
            order, command.actor(), FulfillmentRole.buyer, shipmentInput.expectedVersion());
    LocalDateTime at = now(clock), sent = parseTime(shipmentInput.sentAt()), start = null;
    if (send) {
      if (shipmentInput.trackingNo() == null && shipmentInput.remark() == null) throw ApiFailure.invalid();
      OrderReceiptDO receipt = fulfillmentMapper.selectOrderReceiptDOByOrder(orderId);
      LocalDateTime earliest=receipt==null ? null : receipt.confirmedAt();
      if (earliest==null) {
        OrderOutboundDO outbound=fulfillmentMapper.selectOrderOutboundDOByOrder(orderId);
        if (outbound!=null) earliest=outbound.dispatchAt()==null?outbound.outboundAt():outbound.dispatchAt();
      }
      start = FulfillmentTime.userReturn(earliest, sent, at).startAt();
    }
    TradingReceipt receiptResult=save(
        command,
        draft,
        shipmentInput.trackingNo(),
        shipmentInput.carrierName(),
        shipmentInput.remark(),
        sent,
        null,
        start,
        at,
        FulfillmentRole.buyer);
    if(send) {
      String method=shipmentInput.handoverMethod()!=null?shipmentInput.handoverMethod():shipmentInput.trackingNo()!=null?ReturnHandoverMethod.carrier.name():null;
      if(method==null && Boolean.TRUE.equals(shipmentInput.disputed())
          || ReturnHandoverMethod.self_delivery.name().equals(method) && (shipmentInput.remark()==null || shipmentInput.remark().isBlank()))
        throw ApiFailure.invalid();
      if(method!=null)overdueFees.report(orderId,method,sent,shipmentInput.remark()!=null?shipmentInput.remark():shipmentInput.trackingNo(),
          Boolean.TRUE.equals(shipmentInput.disputed()),command.actor());
    }
    return receiptResult;
  }

  /** 工厂按服务端实际时刻开始取回，同次填写预计到厂时间。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt pickup(
      TradingCommand command, String orderId, FulfillmentDtos.PickupInput pickupInput) {
    if (command.operation() != TradingOperation.startFactoryPickup) throw ApiFailure.invalid();
    RentalOrderDO order = fulfillmentAccess.order(command.actor(), orderId, true, null);
    tradingRoots.lock(order.checkoutBatchId());
    TradingReceipt tradingReceipt = tradingOperations.replay(command);
    if (tradingReceipt != null) return tradingReceipt;
    fulfillmentProtection.requireReturn(orderId);
    OrderReturnShipmentDO draft =
        returnShipmentRoot.draft(
            order, command.actor(), FulfillmentRole.factory, pickupInput.expectedVersion());
    LocalDateTime at = now(clock), arrival = parseTime(pickupInput.expectedArrivalAt());
    return save(
        command,
        draft,
        null,
        null,
        pickupInput.remark(),
        at,
        arrival,
        FulfillmentTime.pickup(at, arrival).startAt(),
        at,
        FulfillmentRole.factory);
  }

  /** 在本厂订单根锁内确认实际接走，保留幂等回执。 */
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public TradingReceipt handover(TradingCommand tradingCommand,String orderId,OverdueFees.Pickup pickup) {
    RentalOrderDO order=fulfillmentAccess.order(tradingCommand.actor(),orderId,true,null);
    tradingRoots.lock(order.checkoutBatchId());
    TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);
    if(tradingReceipt!=null)return tradingReceipt;
    fulfillmentProtection.requireReady(orderId);
    int version=overdueFees.pickup(orderId,pickup,tradingCommand.actor());
    return tradingOperations.recordReason(tradingCommand,"rental_order",orderId,version,pickup.basis());
  }

  /** 获授权的管理员锁单核定费用并记录操作。 */
  @Transactional(isolation=Isolation.READ_COMMITTED)
  public TradingReceipt assess(TradingCommand tradingCommand,String orderId,OverdueFees.Review review) {
    RentalOrderDO order=fulfillmentAccess.order(tradingCommand.actor(),orderId,false,tradingCommand.operation().permission());
    tradingRoots.lock(order.checkoutBatchId());
    TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);
    if(tradingReceipt!=null)return tradingReceipt;
    fulfillmentProtection.requireReady(orderId);
    int version=overdueFees.assess(orderId,review,tradingCommand.actor());
    return tradingOperations.recordReason(tradingCommand,"rental_order",orderId,version,review.basis());
  }

  /** 字符串由契约校验后转换到统一上海时区。 */
  public static LocalDateTime parseTime(String text) {
    return text == null
        ? null
        : OffsetDateTime.parse(text).atZoneSameInstant(ZONE).toLocalDateTime();
  }

  /** 写回原归还根，只有正式发出时才推进回程段并留下事件。 */
  private TradingReceipt save(
      TradingCommand tradingCommand,
      OrderReturnShipmentDO draft,
      String tracking,
      String carrier,
      String remark,
      LocalDateTime sent,
      LocalDateTime arrival,
      LocalDateTime start,
      LocalDateTime at,
      FulfillmentRole role) {
    if(start!=null) extensions.endForRecovery(draft.rentalOrderId(),at);
    OrderReturnShipmentDO updated =
        new OrderReturnShipmentDO(
            draft.id(),
            draft.rentalOrderId(),
            draft.returnTransportLegId(),
            draft.buyerUserId(),
            tradingCommand.actor(),
            role.name(),
            (start == null ? ReturnShipmentStatus.draft : ReturnShipmentStatus.sent).name(),
            draft.returnMethod(),
            tracking,
            carrier,
            sent,
            arrival,
            start,
            draft.packagingPhotoCount(),
            remark,
            draft.version() + 1,
            draft.createdAt(),
            at);
    if (returnShipmentMapper.updateOrderReturnShipment(updated, draft.version()) != 1)
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if (start != null) {
      if (handoverMapper.dispatchLeg(draft.returnTransportLegId(), draft.rentalOrderId(), sent)
          != 1) throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
      fulfillmentEvents.append(
          "rental:" + draft.rentalOrderId() + ":return-sent",
          draft.rentalOrderId(),
          draft.returnTransportLegId(),
          TradingResource.order_return_shipment.name(),
          draft.id(),
          role == FulfillmentRole.factory
              ? FulfillmentEventType.pickup_started
              : FulfillmentEventType.return_sent,
          sent,
          tradingCommand.actor(),
          role,
          role == FulfillmentRole.factory ? "工厂已开始取回道具" : "买家已发出归还道具");
    }
    return tradingOperations.record(
        tradingCommand, TradingResource.order_return_shipment.name(), draft.id(), updated.version());
  }
}
