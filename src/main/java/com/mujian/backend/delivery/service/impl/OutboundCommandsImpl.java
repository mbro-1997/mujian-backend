package com.mujian.backend.delivery.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.delivery.domain.OrderOutboundDO;
import com.mujian.backend.delivery.dto.FulfillmentDtos;
import com.mujian.backend.delivery.mapper.FulfillmentMapper;
import com.mujian.backend.delivery.policy.FulfillmentProtection;
import com.mujian.backend.delivery.service.*;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.TradingOperations;
import com.mujian.backend.order.service.TradingRoots;
import com.mujian.backend.order.vo.TradingReceipt;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 出库草稿与B类数量在同一根版本内写入，不覆盖其他执行者已确认的事实。 */
@Service
public class OutboundCommandsImpl implements OutboundCommands {
  private final FulfillmentAccess fulfillmentAccess;
  private final TradingRoots tradingRoots;
  private final TradingOperations tradingOperations;
  private final FulfillmentProtection fulfillmentProtection;
  private final OutboundRoot outboundRoot;
  private final FulfillmentMapper fulfillmentMapper;
  private final Clock clock;

  /** 复用原交易锁序及幂等记录，不另建出库回执。 */
  public OutboundCommandsImpl(
      FulfillmentAccess fulfillmentAccess,
      TradingRoots tradingRoots,
      TradingOperations tradingOperations,
      FulfillmentProtection fulfillmentProtection,
      OutboundRoot outboundRoot,
      FulfillmentMapper fulfillmentMapper,
      Clock clock) {
    this.fulfillmentAccess = fulfillmentAccess;
    this.tradingRoots = tradingRoots;
    this.tradingOperations = tradingOperations;
    this.fulfillmentProtection = fulfillmentProtection;
    this.outboundRoot = outboundRoot;
    this.fulfillmentMapper = fulfillmentMapper;
    this.clock = clock;
  }

  /** 归属鉴权先于回执读取，重放先于版本与当前开放状态。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt save(
      TradingCommand tradingCommand, String orderId, FulfillmentDtos.OutboundDraft outboundDraft) {
    if (tradingCommand.operation() != TradingOperation.saveOutbound) throw ApiFailure.invalid();
    RentalOrderDO rentalOrderDO =
        fulfillmentAccess.order(tradingCommand.actor(), orderId, true, null);
    tradingRoots.lock(rentalOrderDO.checkoutBatchId());
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    fulfillmentProtection.requireNormal(orderId);
    OrderOutboundDO orderOutboundDO =
        outboundRoot.pending(orderId, tradingCommand.actor(), outboundDraft.expectedVersion());
    if (fulfillmentMapper.saveOutboundRemark(
            orderOutboundDO.id(),
            outboundDraft.expectedVersion(),
            outboundDraft.remark(),
            tradingCommand.actor(),
            now(clock))
        != 1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    return tradingOperations.record(
        tradingCommand,
        TradingResource.order_outbound_record.name(),
        orderOutboundDO.id(),
        outboundDraft.expectedVersion() + 1);
  }

  /** 本单明细归属、B类型和总量上界均在根锁内重新核对。 */
  @Override
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt quantity(
      TradingCommand tradingCommand, String orderId, FulfillmentDtos.QuantityInput quantityInput) {
    if (tradingCommand.operation() != TradingOperation.setOutboundQuantity)
      throw ApiFailure.invalid();
    RentalOrderDO rentalOrderDO =
        fulfillmentAccess.order(tradingCommand.actor(), orderId, true, null);
    tradingRoots.lock(rentalOrderDO.checkoutBatchId());
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    fulfillmentProtection.requireNormal(orderId);
    FulfillmentViews.CheckItem selected = null;
    for (FulfillmentViews.CheckItem item :
        fulfillmentMapper.selectCheckItemsByOrder(orderId, false))
      if (item.itemId().equals(quantityInput.itemId())) selected = item;
    if (selected == null) throw missing();
    if (!PropAssetType.B_CONSUMABLE.name().equals(selected.assetType())
        || quantityInput.quantity() < 0
        || quantityInput.quantity() > selected.expectedQuantity()) throw ApiFailure.invalid();
    OrderOutboundDO orderOutboundDO =
        outboundRoot.pending(orderId, tradingCommand.actor(), quantityInput.expectedVersion());
    if (fulfillmentMapper.setOutboundQuantity(
                orderOutboundDO.id(),
                quantityInput.itemId(),
                quantityInput.quantity(),
                tradingCommand.actor(),
                now(clock))
            != 1
        || fulfillmentMapper.advanceOutboundVersion(
                orderOutboundDO.id(), quantityInput.expectedVersion(), tradingCommand.actor(), now(clock))
            != 1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    return tradingOperations.record(
        tradingCommand,
        TradingResource.order_outbound_record.name(),
        orderOutboundDO.id(),
        quantityInput.expectedVersion() + 1);
  }
}
