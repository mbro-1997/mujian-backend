package com.mujian.backend.delivery.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.mapper.FulfillmentMapper;
import com.mujian.backend.delivery.policy.FulfillmentProtection;
import com.mujian.backend.delivery.projection.FulfillmentMetadata;
import com.mujian.backend.delivery.service.*;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.payment.mapper.RefundMapper;
import com.mujian.backend.delivery.service.OrderDisputeContext;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 子根独立版本和资金进度并列返回，零行只投影上下文。 */
@Service
public class FulfillmentReaderImpl implements FulfillmentReader {
  private final FulfillmentAccess fulfillmentAccess;
  private final FulfillmentMapper fulfillmentMapper;
  private final FulfillmentProtection fulfillmentProtection;
  private final RefundMapper refundMapper;
  private final OrderDisputeContext orderDisputeContext;
  private final Clock clock;

  /** 只复用既有权限、原退款查询和统一时钟。 */
  public FulfillmentReaderImpl(
      FulfillmentAccess fulfillmentAccess,
      FulfillmentMapper fulfillmentMapper,
      FulfillmentProtection fulfillmentProtection,
      RefundMapper refundMapper,
      OrderDisputeContext orderDisputeContext,
      Clock clock) {
    this.orderDisputeContext = orderDisputeContext;
    this.fulfillmentAccess = fulfillmentAccess;
    this.fulfillmentMapper = fulfillmentMapper;
    this.fulfillmentProtection = fulfillmentProtection;
    this.refundMapper = refundMapper;
    this.clock = clock;
  }

  /** 同一只读事务内装配真实节点；管理员不获得工厂扫码或买家确认动作。 */
  @Override
  @Transactional(readOnly = true)
  public FulfillmentViews.Fulfillment read(
      String actor, String orderId, boolean supplier, boolean admin) {
    RentalOrderDO rentalOrderDO =
        fulfillmentAccess.order(actor, orderId, supplier, admin ? "admin:trade:order:read" : null);
    FulfillmentMetadata fulfillmentMetadata = fulfillmentMapper.selectMetadataByOrder(orderId);
    if (fulfillmentMetadata == null)
      throw new IllegalStateException("fulfillment transport missing");
    OrderOutboundDO orderOutboundDO = fulfillmentMapper.selectOrderOutboundDOByOrder(orderId);
    OrderReceiptDO orderReceiptDO = fulfillmentMapper.selectOrderReceiptDOByOrder(orderId);
    OrderReturnShipmentDO orderReturnShipmentDO =
        fulfillmentMapper.selectOrderReturnShipmentDOByOrder(orderId);
    OrderReturnCheckDO orderReturnCheckDO =
        fulfillmentMapper.selectOrderReturnCheckDOByOrder(orderId);
    List<FulfillmentViews.CheckItem> outboundItems =
        fulfillmentMapper.selectCheckItemsByOrder(orderId, false);
    List<FulfillmentViews.CheckItem> returnItems =
        fulfillmentMapper.selectCheckItemsByOrder(orderId, true);
    DisputeGuard.Facts facts = fulfillmentProtection.facts(orderId);
    boolean normal = fulfillmentProtection.writable(facts);
    LocalDateTime at = now(clock);
    List<String> outboundActions = new ArrayList<>();
    if (normal
        && supplier
        && !admin
        && TradingState.accepted.name().equals(rentalOrderDO.orderStatus())
        && (orderOutboundDO == null
            || OutboundStatus.pending.name().equals(orderOutboundDO.outboundStatus()))) {
      outboundActions.add(FulfillmentAction.save.name());
      outboundActions.add(FulfillmentAction.scan.name());
      outboundActions.add(FulfillmentAction.set_quantity.name());
      outboundActions.add(FulfillmentAction.confirm.name());
    }
    if (normal
        && supplier
        && !admin
        && orderOutboundDO != null
        && OutboundStatus.completed.name().equals(orderOutboundDO.outboundStatus()))
      outboundActions.add(FulfillmentAction.append_evidence.name());
    List<String> receiptActions = new ArrayList<>();
    if (normal
        && !supplier
        && !admin
        && orderReceiptDO != null
        && ReceiptCheckStatus.pending.name().equals(orderReceiptDO.checkStatus()))
      receiptActions.add(FulfillmentAction.confirm.name());
    if (!facts.ready() && !admin) receiptActions.add(FulfillmentAction.contact_support.name());
    boolean received =
        orderReceiptDO != null
            && Set.of(ReceiptCheckStatus.confirmed.name(), ReceiptCheckStatus.auto_confirmed.name())
                .contains(orderReceiptDO.checkStatus());
    boolean terminating=fulfillmentProtection.terminationReturnAllowed(facts) && orderOutboundDO!=null
        && orderOutboundDO.outboundAt()!=null && !TradingState.cancelled.name().equals(rentalOrderDO.orderStatus());
    boolean returning=(normal && received) || terminating;
    List<String> shipmentActions = new ArrayList<>();
    if (returning
        && !admin
        && orderReturnCheckDO == null
        && (orderReturnShipmentDO == null
            || ReturnShipmentStatus.draft.name().equals(orderReturnShipmentDO.shipmentStatus()))) {
      if (supplier
          && TransportServiceMode.factory_pickup.name().equals(fulfillmentMetadata.returnMode()))
        shipmentActions.add(FulfillmentAction.start_pickup.name());
      if (!supplier
          && TransportServiceMode.user_return.name().equals(fulfillmentMetadata.returnMode())) {
        shipmentActions.add(FulfillmentAction.save.name());
        shipmentActions.add(FulfillmentAction.send.name());
      }
    }
    List<String> refundIds = refundMapper.selectRefundIdsByOrder(orderId);
    List<String> returnActions = new ArrayList<>();
    boolean closed = orderReturnCheckDO != null && orderReturnCheckDO.closedAt() != null;
    if (returning
        && supplier
        && !admin
        && !closed
        && (orderReturnCheckDO == null || orderReturnCheckDO.claimCreated() == 0)) {
      returnActions.add(FulfillmentAction.scan.name());
      returnActions.add(FulfillmentAction.set_quantity.name());
      if (orderReturnCheckDO != null && !orderReturnCheckDO.checkStartAt().isAfter(at))
        returnActions.add(terminating?"confirm_termination":FulfillmentAction.confirm_no_issue.name());
    }
    if (normal && supplier && !admin && orderReturnCheckDO!=null && !closed && orderReturnCheckDO.autoCloseAt()!=null
        && !at.isBefore(orderReturnCheckDO.checkStartAt()) && at.isBefore(orderReturnCheckDO.autoCloseAt())) returnActions.add("create_claim");
    com.mujian.backend.delivery.vo.DisputeContext disputes=orderDisputeContext.read(rentalOrderDO,supplier,admin);
    boolean mayClose=admin && fulfillmentAccess.mayCloseReturn(actor);
    if (fulfillmentProtection.readyForFee(facts) && disputes.overdueFee()!=null) {
      if (mayClose && !"effective".equals(disputes.overdueFee().status()))
        returnActions.add(FulfillmentAction.assess_overdue_fee.name());
      if (!closed && !admin && supplier && "awaiting_handover".equals(disputes.overdueFee().status())
          && orderReturnShipmentDO!=null && "sent".equals(orderReturnShipmentDO.shipmentStatus())
          && "factory_pickup".equals(fulfillmentMetadata.returnMode()))
        shipmentActions.add(FulfillmentAction.confirm_pickup_handover.name());
    }
    if (normal
        && admin
        && received
        && !closed
        && (orderReturnCheckDO == null || orderReturnCheckDO.claimCreated() == 0)
        && mayClose) {
      if (orderReturnCheckDO == null || orderReturnCheckDO.checkStartAt().isAfter(at))
        returnActions.add(FulfillmentAction.admin_confirm_arrival.name());
      if (orderReturnCheckDO != null && !orderReturnCheckDO.checkStartAt().isAfter(at))
        returnActions.add(FulfillmentAction.admin_close.name());
    }
    if (!refundIds.isEmpty()) returnActions.add(FulfillmentAction.view_refunds.name());
    if (!facts.ready() && !admin) returnActions.add(FulfillmentAction.contact_support.name());
    return new FulfillmentViews.Fulfillment(
        orderId,
        time(at),
        new FulfillmentViews.Outbound(
            orderOutboundDO == null ? null : orderOutboundDO.id(),
            orderId,
            orderOutboundDO == null ? 0 : orderOutboundDO.version(),
            orderOutboundDO == null
                ? OutboundStatus.pending.name()
                : orderOutboundDO.outboundStatus(),
            fulfillmentMetadata.outboundMode(),
            outboundItems,
            orderOutboundDO == null ? 0 : orderOutboundDO.outboundPhotoCount(),
            orderOutboundDO == null ? null : orderOutboundDO.remark(),
            orderOutboundDO == null ? null : time(orderOutboundDO.outboundAt()),
            orderOutboundDO == null ? null : time(orderOutboundDO.dispatchAt()),
            List.copyOf(outboundActions)),
        new FulfillmentViews.ReceiptCheck(
            orderReceiptDO == null ? null : orderReceiptDO.id(),
            orderId,
            orderReceiptDO == null ? 0 : orderReceiptDO.version(),
            orderReceiptDO == null
                ? FulfillmentStage.not_dispatched.name()
                : orderReceiptDO.checkStatus(),
            orderReceiptDO == null ? null : time(orderReceiptDO.startAt()),
            orderReceiptDO == null ? null : time(orderReceiptDO.deadlineAt()),
            orderReceiptDO == null ? null : time(orderReceiptDO.confirmedAt()),
            facts.paused(),
            List.copyOf(receiptActions)),
        new FulfillmentViews.ReturnShipment(
            orderReturnShipmentDO == null ? null : orderReturnShipmentDO.id(),
            orderId,
            orderReturnShipmentDO == null ? 0 : orderReturnShipmentDO.version(),
            fulfillmentMetadata.returnMode(),
            orderReturnShipmentDO == null
                ? FulfillmentStage.not_started.name()
                : orderReturnShipmentDO.shipmentStatus(),
            orderReturnShipmentDO == null ? null : time(orderReturnShipmentDO.sentAt()),
            orderReturnShipmentDO == null ? null : time(orderReturnShipmentDO.expectedArrivalAt()),
            orderReturnShipmentDO == null
                ? null
                : time(orderReturnShipmentDO.expectedCheckStartAt()),
            orderReturnShipmentDO == null ? null : orderReturnShipmentDO.trackingNo(),
            orderReturnShipmentDO == null ? null : orderReturnShipmentDO.carrierName(),
            orderReturnShipmentDO == null ? null : orderReturnShipmentDO.remark(),
            orderReturnShipmentDO == null ? 0 : orderReturnShipmentDO.packagingPhotoCount(),
            rentalOrderDO.transportNoticeSnapshot(),
            List.copyOf(shipmentActions)),
        new FulfillmentViews.ReturnCheck(
            orderReturnCheckDO == null ? null : orderReturnCheckDO.id(),
            orderId,
            orderReturnCheckDO == null ? 0 : orderReturnCheckDO.version(),
            orderReturnCheckDO == null
                ? FulfillmentStage.not_started.name()
                : orderReturnCheckDO.checkStatus(),
            orderReturnCheckDO == null ? null : time(orderReturnCheckDO.checkStartAt()),
            orderReturnCheckDO == null ? null : time(orderReturnCheckDO.warningAt()),
            orderReturnCheckDO == null ? null : time(orderReturnCheckDO.autoCloseAt()),
            orderReturnCheckDO == null ? null : time(orderReturnCheckDO.closedAt()),
            orderReturnCheckDO == null ? null : time(orderReturnCheckDO.checkedAt()),
            orderReturnCheckDO == null ? null : orderReturnCheckDO.closeReasonCode(),
            orderReturnCheckDO != null && orderReturnCheckDO.claimCreated() == 1,
            returnItems,
            fulfillmentMetadata.inventoryReleased(),
            fulfillmentMetadata.depositStatus(),
            refundIds,
            List.copyOf(returnActions)),
        orderOutboundDO != null
            && orderOutboundDO.outboundAt() != null
            && !closed
            && rentalOrderDO.currentDueDate().isBefore(at.toLocalDate()),
        facts.ready(),
        disputes);
  }
}
