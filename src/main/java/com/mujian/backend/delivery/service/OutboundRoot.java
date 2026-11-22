package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.delivery.domain.OrderOutboundDO;
import com.mujian.backend.delivery.mapper.FulfillmentMapper;
import com.mujian.backend.delivery.projection.FulfillmentMetadata;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 所有出库核对与证据共用唯一根，调用者先按TradingRoots加锁。 */
@Service
public class OutboundRoot {
  private final FulfillmentMapper fulfillmentMapper;
  private final RentalOrderMapper rentalOrderMapper;
  private final Clock clock;

  /** 复用订单根与统一时钟，草稿只在合法写命令中建立。 */
  public OutboundRoot(
      FulfillmentMapper fulfillmentMapper, RentalOrderMapper rentalOrderMapper, Clock clock) {
    this.fulfillmentMapper = fulfillmentMapper;
    this.rentalOrderMapper = rentalOrderMapper;
    this.clock = clock;
  }

  /** 在订单锁内校验状态、版本并按需建立草稿，失败由调用者事务回滚。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public OrderOutboundDO pending(String orderId, String actor, int expectedVersion) {
    RentalOrderDO rentalOrderDO = rentalOrderMapper.selectOrderById(orderId);
    if (rentalOrderDO == null) throw missing();
    if (!TradingState.accepted.name().equals(rentalOrderDO.orderStatus()))
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    OrderOutboundDO orderOutboundDO = fulfillmentMapper.selectOrderOutboundDOByOrder(orderId);
    if ((orderOutboundDO == null ? 0 : orderOutboundDO.version()) != expectedVersion)
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if (orderOutboundDO != null) {
      if (!OutboundStatus.pending.name().equals(orderOutboundDO.outboundStatus()))
        throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
      return orderOutboundDO;
    }
    LocalDateTime at = now(clock);
    rentalOrderMapper.readyTransportLegs(orderId, at);
    FulfillmentMetadata fulfillmentMetadata = fulfillmentMapper.selectMetadataByOrder(orderId);
    if (fulfillmentMetadata == null)
      throw new IllegalStateException("fulfillment transport missing");
    if (!TransportLegStatus.ready.name().equals(fulfillmentMetadata.outboundStatus()))
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    List<FulfillmentViews.CheckItem> items =
        fulfillmentMapper.selectCheckItemsByOrder(orderId, false);
    if (items.isEmpty()) throw new IllegalStateException("fulfillment items missing");
    orderOutboundDO =
        new OrderOutboundDO(
            Ids.next(),
            orderId,
            fulfillmentMetadata.outboundLegId(),
            rentalOrderDO.factoryId(),
            OutboundStatus.pending.name(),
            fulfillmentMetadata.outboundMode(),
            fulfillmentMetadata.transportRequirement(),
            0,
            actor,
            null,
            null,
            null,
            0,
            at,
            at);
    fulfillmentMapper.insertOutbound(orderOutboundDO);
    for (FulfillmentViews.CheckItem item : items)
      fulfillmentMapper.insertOutboundItem(
          Ids.next(), orderOutboundDO.id(), orderId, item.itemId(), at);
    return orderOutboundDO;
  }
}
