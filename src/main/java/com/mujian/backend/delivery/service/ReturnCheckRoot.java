package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.mapper.*;
import com.mujian.backend.delivery.policy.FulfillmentTime;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import com.mujian.backend.order.domain.RentalOrderDO;
import java.time.*;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 预计到期、首次实物到厂和管理员到厂共用唯一的核验处置根。 */
@Service
public class ReturnCheckRoot {
  private final OverdueFees overdueFees;
  private final ReturnCheckMapper returnCheckMapper;
  private final FulfillmentMapper fulfillmentMapper;
  private final HandoverMapper handoverMapper;
  private final FulfillmentEvents fulfillmentEvents;
  private final com.mujian.backend.delivery.mapper.IssuePlanMapper issuePlanMapper;
  private final Clock clock;
  private final com.mujian.backend.order.service.OrderExtensions extensions;

  /** 原交易根先锁定，核验建立后再处理库存实物。 */
  public ReturnCheckRoot(
      ReturnCheckMapper returnCheckMapper,
      FulfillmentMapper fulfillmentMapper,
      HandoverMapper handoverMapper,
      FulfillmentEvents fulfillmentEvents,
      Clock clock, com.mujian.backend.delivery.mapper.IssuePlanMapper issuePlanMapper, com.mujian.backend.order.service.OrderExtensions extensions, OverdueFees overdueFees) {
    this.overdueFees=overdueFees;
    this.extensions=extensions;
    this.issuePlanMapper=issuePlanMapper;
    this.returnCheckMapper = returnCheckMapper;
    this.fulfillmentMapper = fulfillmentMapper;
    this.handoverMapper = handoverMapper;
    this.fulfillmentEvents = fulfillmentEvents;
    this.clock = clock;
  }

  /** 无真实到厂时只能在已发出预计起点后创建；输入0和GET不能启动。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public OrderReturnCheckDO open(
      RentalOrderDO order, String actor, FulfillmentRole role, int version, boolean physical) {
    OrderReturnCheckDO current = returnCheckMapper.lockCheck(order.id());
    if ((current == null ? 0 : current.version()) != version)
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    LocalDateTime at = now(clock);
    if (physical) extensions.endForRecovery(order.id(),at);
    if (current != null) {
      requireOpen(current);
      if (physical && current.checkStartAt().isAfter(at)) {
        returnCheckMapper.updateReturnCheckStartAndDeadlines(current.id(), at, source(role).name(), at);
        return returnCheckMapper.lockCheck(order.id());
      }
      return current;
    }
    OrderReceiptDO receipt = fulfillmentMapper.selectOrderReceiptDOByOrder(order.id());
    boolean ordinaryReceipt=receipt!=null && Set.of(ReceiptCheckStatus.confirmed.name(),ReceiptCheckStatus.auto_confirmed.name()).contains(receipt.checkStatus());
    boolean recovering=issuePlanMapper.selectActive(order.id(),null,true)!=null && issuePlanMapper.selectIssuePlanDispatched(order.id()) && !issuePlanMapper.physicallyReceived(order.id());
    if (!ordinaryReceipt && !recovering)
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    OrderReturnShipmentDO shipment =
        fulfillmentMapper.selectOrderReturnShipmentDOByOrder(order.id());
    LocalDateTime planned = shipment == null ? null : shipment.expectedCheckStartAt();
    if (!physical && (planned == null || at.isBefore(planned)))
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    boolean arrived = physical && (planned == null || at.isBefore(planned));
    ReturnCheckStartSource startSource =
        arrived
            ? source(role)
            : TransportServiceMode.factory_pickup.name().equals(shipment.returnMethod())
                ? ReturnCheckStartSource.expected_arrival
                : ReturnCheckStartSource.sent_12h;
    FulfillmentTime.ReturnWindow window = FulfillmentTime.returning(arrived ? at : planned);
    OrderReturnCheckDO orderReturnCheckDO =
        new OrderReturnCheckDO(
            Ids.next(),
            order.id(),
            order.factoryId(),
            shipment == null ? null : shipment.id(),
            ReturnCheckStatus.pending.name(),
            startSource.name(),
            window.startAt(),
            window.warningAt(),
            recovering ? null : window.autoCloseAt(),
            null,
            null,
            null,
            0,
            null,
            null,
            null,
            null,
            0,
            at,
            at);
    returnCheckMapper.insertCheck(orderReturnCheckDO);
    if (at.isAfter(order.currentDueDate().plusDays(1).atStartOfDay()))
      overdueFees.trackLateReturn(order.id());
    for (FulfillmentViews.CheckItem item :
        fulfillmentMapper.selectCheckItemsByOrder(order.id(), false)) {
      if (!item.complete()) throw new IllegalStateException("dispatched item incomplete");
      returnCheckMapper.insertItem(
          Ids.next(), orderReturnCheckDO.id(), item.itemId(), item.expectedQuantity(), at);
    }
    if (handoverMapper.changeOrderState(
            order.id(), TradingState.fulfilling.name(), TradingState.return_checking.name(), at)
        != 1) throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    fulfillmentEvents.append(
        "rental:" + order.id() + ":return-check",
        order.id(),
        fulfillmentMapper.selectMetadataByOrder(order.id()).returnLegId(),
        TradingResource.order_return_check.name(),
        orderReturnCheckDO.id(),
        FulfillmentEventType.return_check_started,
        window.startAt(),
        actor,
        role,
        "归还核验期已开始");
    return orderReturnCheckDO;
  }

  /** 关闭或生效索赔不可再走普通核对。 */
  public static void requireOpen(OrderReturnCheckDO orderReturnCheckDO) {
    if (orderReturnCheckDO.claimCreated() == 1
        || ReturnCheckStatus.claim_submitted.name().equals(orderReturnCheckDO.checkStatus()))
      throw conflict(FulfillmentReason.RETURN_CLAIM_EXISTS.name());
    if (orderReturnCheckDO.closedAt() != null) throw conflict(FulfillmentReason.RETURN_ALREADY_CLOSED.name());
  }

  /** 人工到厂区分工厂实物核对和管理员确认。 */
  private ReturnCheckStartSource source(FulfillmentRole role) {
    return role == FulfillmentRole.admin
        ? ReturnCheckStartSource.admin_confirm
        : ReturnCheckStartSource.factory_scan;
  }
}
