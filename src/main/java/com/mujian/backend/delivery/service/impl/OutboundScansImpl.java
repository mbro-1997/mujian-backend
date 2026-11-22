package com.mujian.backend.delivery.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.dto.FulfillmentDtos;
import com.mujian.backend.delivery.mapper.FulfillmentMapper;
import com.mujian.backend.delivery.policy.FulfillmentProtection;
import com.mujian.backend.delivery.service.*;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.prop.domain.*;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 原订单分配、当前有效码及成功扫描唯一约束共同保护核对数量。 */
@Service
public class OutboundScansImpl implements OutboundScans {
  private final FulfillmentAccess fulfillmentAccess;
  private final TradingRoots tradingRoots;
  private final TradingOperations tradingOperations;
  private final FulfillmentQr fulfillmentQr;
  private final FulfillmentProtection fulfillmentProtection;
  private final OutboundRoot outboundRoot;
  private final FulfillmentMapper fulfillmentMapper;
  private final Clock clock;

  /** 沿用交易锁序、扫码替换链和库存分配，不另选实物。 */
  public OutboundScansImpl(
      FulfillmentAccess fulfillmentAccess,
      TradingRoots tradingRoots,
      TradingOperations tradingOperations,
      FulfillmentQr fulfillmentQr,
      FulfillmentProtection fulfillmentProtection,
      OutboundRoot outboundRoot,
      FulfillmentMapper fulfillmentMapper,
      Clock clock) {
    this.fulfillmentAccess = fulfillmentAccess;
    this.tradingRoots = tradingRoots;
    this.tradingOperations = tradingOperations;
    this.fulfillmentQr = fulfillmentQr;
    this.fulfillmentProtection = fulfillmentProtection;
    this.outboundRoot = outboundRoot;
    this.fulfillmentMapper = fulfillmentMapper;
    this.clock = clock;
  }

  /** 原请求结果优先于当前版本，重复SN新请求返回独立冻结回执但不增加进度。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public FulfillmentViews.ScanResult scan(
      TradingCommand command, String orderId, FulfillmentDtos.ScanInput scanInput) {
    if (command.operation() != TradingOperation.scanOutbound) throw ApiFailure.invalid();
    RentalOrderDO order = fulfillmentAccess.order(command.actor(), orderId, true, null);
    tradingRoots.lock(order.checkoutBatchId());
    TradingReceipt tradingReceipt = tradingOperations.replay(command);
    if (tradingReceipt != null) return result(tradingReceipt, tradingOperations.replayScan(command));
    fulfillmentProtection.requireNormal(orderId);
    OrderOutboundDO root = outboundRoot.pending(orderId, command.actor(), scanInput.expectedVersion());
    FulfillmentQr.Assigned assigned = fulfillmentQr.resolve(order, scanInput.code(), false);
    boolean duplicate = fulfillmentMapper.hasOutboundScan(root.id(), assigned.snId());
    int version = root.version();
    if (!duplicate) {
      fulfillmentMapper.insertOutboundScan(
          new OutboundScanDO(
              Ids.next(),
              command.requestId(),
              root.id(),
              orderId,
              assigned.itemId(),
              assigned.qrId(),
              assigned.snId(),
              command.actor(),
              now(clock)));
      if (fulfillmentMapper.incrementOutboundQuantity(
              root.id(), assigned.itemId(), command.actor(), now(clock))
          != 1)
        throw new ApiFailure(
            500, D2Codes.CODE_DATA_INTEGRITY_ERROR, FulfillmentReason.FULFILLMENT_DATA_INCONSISTENT.name());
      if (fulfillmentMapper.advanceOutboundVersion(root.id(), version, command.actor(), now(clock))
          != 1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
      version++;
    }
    FulfillmentViews.CheckItem checked = null;
    for (FulfillmentViews.CheckItem item :
        fulfillmentMapper.selectCheckItemsByOrder(orderId, false))
      if (item.itemId().equals(assigned.itemId())) checked = item;
    if (checked == null) throw new IllegalStateException("scan item missing");
    TradingOperations.ScanSnapshot snapshot =
        new TradingOperations.ScanSnapshot(
            duplicate,
            checked.itemId(),
            checked.checkedQuantity(),
            checked.expectedQuantity(),
            version);
    TradingReceipt receipt =
        tradingOperations.record(
            command, TradingResource.order_outbound_record.name(), root.id(), version, snapshot);
    return result(receipt, snapshot);
  }

  /** 同一回执中的数量及重复标记全部来自当时持久结果。 */
  private FulfillmentViews.ScanResult result(
      TradingReceipt receipt, TradingOperations.ScanSnapshot snapshot) {
    return new FulfillmentViews.ScanResult(
        receipt,
        snapshot.duplicate(),
        snapshot.itemId(),
        snapshot.checkedQuantity(),
        snapshot.expectedQuantity(),
        snapshot.version());
  }

  /** 数据库分页与计数共用原订单条件，历史订单仍可查看。 */
  @Override
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public FulfillmentViews.ScanPage page(String actor, String orderId, PageQuery pageQuery) {
    fulfillmentAccess.order(actor, orderId, true, null);
    return new FulfillmentViews.ScanPage(
        fulfillmentMapper.selectOutboundScans(orderId, pageQuery),
        pageQuery.page(),
        pageQuery.pageSize(),
        Long.toString(fulfillmentMapper.countOutboundScans(orderId)));
  }
}
