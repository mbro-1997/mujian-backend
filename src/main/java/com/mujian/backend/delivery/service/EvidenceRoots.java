package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.mapper.*;
import com.mujian.backend.delivery.projection.FulfillmentMetadata;
import com.mujian.backend.order.domain.RentalOrderDO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 节点版本属于业务根，同一次绑定只操作一个根。 */
@Service
public class EvidenceRoots {
  public record Root(
      TradingResource type, String id, int version, String legId, boolean removable) {}

  private final OutboundRoot outboundRoot;
  private final ReturnShipmentRoot returnShipmentRoot;
  private final FulfillmentMapper fulfillmentMapper;
  private final EvidenceMapper evidenceMapper;

  /** 按节点复用业务草稿，不为读取凭证新建业务记录。 */
  public EvidenceRoots(
      OutboundRoot outboundRoot,
      ReturnShipmentRoot returnShipmentRoot,
      FulfillmentMapper fulfillmentMapper,
      EvidenceMapper evidenceMapper) {
    this.outboundRoot = outboundRoot;
    this.returnShipmentRoot = returnShipmentRoot;
    this.fulfillmentMapper = fulfillmentMapper;
    this.evidenceMapper = evidenceMapper;
  }

  /** 上传与绑定使用同一套节点角色/执行方权限。 */
  public void authorize(String orderId, EvidenceNode node, FulfillmentRole role) {
    if (node == null || !node.permits(role)) throw ApiFailure.invalid();
    if (node == EvidenceNode.return_sent
        && ReturnShipmentRoot.expectedRole(
                fulfillmentMapper.selectMetadataByOrder(orderId).returnMode())
            != role) throw ApiFailure.invalid();
  }

  /** 在订单根锁下重新取得业务版本、可移除状态和实际关联运输段。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public Root resolve(
      RentalOrderDO order,
      String actor,
      FulfillmentRole role,
      EvidenceNode node,
      String statementId,
      String legId,
      int version) {
    authorize(order.id(), node, role);
    if (node != EvidenceNode.transport_expense && (statementId != null || legId != null))
      throw ApiFailure.invalid();
    FulfillmentMetadata meta = fulfillmentMapper.selectMetadataByOrder(order.id());
    Root root =
        switch (node) {
          case outbound, handover -> {
            OrderOutboundDO orderOutboundDO = fulfillmentMapper.selectOrderOutboundDOByOrder(order.id());
            if (orderOutboundDO == null || OutboundStatus.pending.name().equals(orderOutboundDO.outboundStatus()))
              orderOutboundDO = outboundRoot.pending(order.id(), actor, version);
            if (OutboundStatus.cancelled.name().equals(orderOutboundDO.outboundStatus())) throw changed();
            yield new Root(
                TradingResource.order_outbound_record,
                orderOutboundDO.id(),
                orderOutboundDO.version(),
                meta.outboundLegId(),
                OutboundStatus.pending.name().equals(orderOutboundDO.outboundStatus()));
          }
          case in_use_report,claim,arbitration -> throw changed(); // 使用中证据在问题提交事务绑定。
          case receipt_check -> {
            OrderReceiptDO orderReceiptDO = fulfillmentMapper.selectOrderReceiptDOByOrder(order.id());
            if (orderReceiptDO == null) throw changed();
            yield new Root(
                TradingResource.order_receipt_check,
                orderReceiptDO.id(),
                orderReceiptDO.version(),
                orderReceiptDO.outboundTransportLegId(),
                false);
          }
          case return_sent -> {
            OrderReturnShipmentDO orderReturnShipmentDO =
                fulfillmentMapper.selectOrderReturnShipmentDOByOrder(order.id());
            if (orderReturnShipmentDO == null || ReturnShipmentStatus.draft.name().equals(orderReturnShipmentDO.shipmentStatus()))
              orderReturnShipmentDO = returnShipmentRoot.draft(order, actor, role, version);
            yield new Root(
                TradingResource.order_return_shipment,
                orderReturnShipmentDO.id(),
                orderReturnShipmentDO.version(),
                orderReturnShipmentDO.returnTransportLegId(),
                ReturnShipmentStatus.draft.name().equals(orderReturnShipmentDO.shipmentStatus()));
          }
          case return_check -> {
            OrderReturnCheckDO orderReturnCheckDO = fulfillmentMapper.selectOrderReturnCheckDOByOrder(order.id());
            if (orderReturnCheckDO == null) throw changed();
            yield new Root(
                TradingResource.order_return_check,
                orderReturnCheckDO.id(),
                orderReturnCheckDO.version(),
                meta.returnLegId(),
                false);
          }
          case transport_expense -> {
            if (statementId == null
                || legId == null
                || !(legId.equals(meta.outboundLegId()) || legId.equals(meta.returnLegId())))
              throw ApiFailure.invalid();
            EvidenceMapper.Statement statement =
                evidenceMapper.selectStatement(order.id(), statementId);
            if (statement == null) throw missing();
            if (!"draft".equals(statement.status())) throw changed();
            yield new Root(
                TradingResource.order_transport_expense_statement,
                statement.id(),
                statement.version(),
                legId,
                true);
          }
        };
    if (root.version() != version) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    return root;
  }

  /** 节点不存在或阶段已变时要求重新取上下文。 */
  private ApiFailure changed() {
    return conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
  }
}
