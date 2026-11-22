package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.mapper.*;
import com.mujian.backend.delivery.projection.FulfillmentMetadata;
import com.mujian.backend.order.domain.RentalOrderDO;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 归还信息和包装照片共享真实归还草稿与根版本。 */
@Service
public class ReturnShipmentRoot {
  private final FulfillmentMapper fulfillmentMapper;
  private final ReturnShipmentMapper returnShipmentMapper;
  private final com.mujian.backend.delivery.mapper.IssuePlanMapper issuePlanMapper;
  private final Clock clock;

  /** 草稿只从合法写命令创建。 */
  public ReturnShipmentRoot(
      FulfillmentMapper fulfillmentMapper, ReturnShipmentMapper returnShipmentMapper, Clock clock, com.mujian.backend.delivery.mapper.IssuePlanMapper issuePlanMapper) {
    this.issuePlanMapper=issuePlanMapper;
    this.fulfillmentMapper = fulfillmentMapper;
    this.returnShipmentMapper = returnShipmentMapper;
    this.clock = clock;
  }

  /** 验证原订单已收货、执行方和回程原选择，草稿不启动归还窗口。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public OrderReturnShipmentDO draft(
      RentalOrderDO order, String actor, FulfillmentRole role, int version) {
    FulfillmentMetadata meta = fulfillmentMapper.selectMetadataByOrder(order.id());
    OrderReceiptDO receipt = fulfillmentMapper.selectOrderReceiptDOByOrder(order.id());
    boolean ordinaryReceipt=receipt!=null && Set.of(ReceiptCheckStatus.confirmed.name(),ReceiptCheckStatus.auto_confirmed.name()).contains(receipt.checkStatus());
    boolean recovering=issuePlanMapper.selectActive(order.id(),null,true)!=null && issuePlanMapper.selectIssuePlanDispatched(order.id()) && !issuePlanMapper.physicallyReceived(order.id());
    if (mapperHasCheck(order.id()) || meta==null || !ordinaryReceipt && !recovering)
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    if (!expectedRole(meta.returnMode()).equals(role))
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    OrderReturnShipmentDO orderReturnShipmentDO = fulfillmentMapper.selectOrderReturnShipmentDOByOrder(order.id());
    if ((orderReturnShipmentDO == null ? 0 : orderReturnShipmentDO.version()) != version) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    if (orderReturnShipmentDO != null && !ReturnShipmentStatus.draft.name().equals(orderReturnShipmentDO.shipmentStatus()))
      throw conflict(FulfillmentReason.RETURN_ALREADY_SENT.name());
    if (!TransportLegStatus.ready.name().equals(meta.returnStatus()))
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    if (orderReturnShipmentDO != null) return orderReturnShipmentDO;
    LocalDateTime at = now(clock);
    orderReturnShipmentDO =
        new OrderReturnShipmentDO(
            Ids.next(),
            order.id(),
            meta.returnLegId(),
            order.buyerUserId(),
            actor,
            role.name(),
            ReturnShipmentStatus.draft.name(),
            meta.returnMode(),
            null,
            null,
            null,
            null,
            null,
            0,
            null,
            0,
            at,
            at);
    returnShipmentMapper.insertDraft(orderReturnShipmentDO);
    return orderReturnShipmentDO;
  }

  /** 实际到厂后禁止补写新的发出事实。 */
  private boolean mapperHasCheck(String orderId) {
    return fulfillmentMapper.selectOrderReturnCheckDOByOrder(orderId) != null;
  }

  /** 原回程执行方由冻结运输方式唯一决定。 */
  public static FulfillmentRole expectedRole(String mode) {
    return switch (TransportServiceMode.valueOf(mode)) {
      case user_return -> FulfillmentRole.buyer;
      case factory_pickup -> FulfillmentRole.factory;
      default -> throw new IllegalStateException("invalid return service mode");
    };
  }
}
