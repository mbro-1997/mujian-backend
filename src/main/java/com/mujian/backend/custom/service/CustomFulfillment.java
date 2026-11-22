package com.mujian.backend.custom.service;

import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.custom.service.CustomRequirements.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.custom.dto.CustomForms.*;
import com.mujian.backend.custom.mapper.*;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.TradingOperations;
import com.mujian.backend.order.vo.TradingReceipt;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CustomFulfillment {
  private final CustomOrderMapper customOrderMapper;
  private final CustomFulfillmentMapper customFulfillmentMapper;
  private final CustomPurchase customPurchase;
  private final CustomMaterials customMaterials;
  private final CustomAfterSaleCompletion customAfterSaleCompletion;
  private final TradingOperations tradingOperations;
  private final ObjectMapper json;
  private final Clock clock;

  public CustomFulfillment(
      CustomOrderMapper customOrderMapper,
      CustomFulfillmentMapper customFulfillmentMapper,
      CustomPurchase customPurchase,
      CustomMaterials customMaterials,
      CustomAfterSaleCompletion customAfterSaleCompletion,
      TradingOperations tradingOperations,
      ObjectMapper json,
      Clock clock) {
    this.customOrderMapper = customOrderMapper;
    this.customFulfillmentMapper = customFulfillmentMapper;
    this.customPurchase = customPurchase;
    this.customMaterials = customMaterials;
    this.customAfterSaleCompletion = customAfterSaleCompletion;
    this.tradingOperations = tradingOperations;
    this.json = json;
    this.clock = clock;
  }

  /** 登记定制生产进度。 */
  public TradingReceipt progress(TradingCommand tradingCommand, String id, int expected) {
    tradingOperations.lock(tradingCommand.actor());
    CustomOrderMapper.Order customOrderOrder = customPurchase.owned(tradingCommand.actor(), true, id, true);
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    version(customOrderOrder.version(), expected);
    String state;
    switch (tradingCommand.operation()) {
      case readyCustomDelivery -> {
        require("producing".equals(customOrderOrder.state()));
        state = "ready_for_delivery";
      }
      case dispatchCustomDelivery -> {
        require(
            "ready_for_delivery".equals(customOrderOrder.state()) && customOrderOrder.deliveryMode().equals("factory_delivery"));
        state = "delivering";
      }
      default -> throw ApiFailure.invalid();
    }
    customOrderMapper.updateCustomOrderState(id, state, now(clock));
    return tradingOperations.record(tradingCommand, "custom_order", id, expected + 1);
  }

  /** 登记本次交付和验收期限。 */
  public TradingReceipt deliver(
      TradingCommand tradingCommand, String id, DeliveryInput deliveryInput, boolean pickup) {
    tradingOperations.lock(tradingCommand.actor());
    CustomOrderMapper.Order customOrderOrder = customPurchase.owned(tradingCommand.actor(), true, id, true);
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    version(customOrderOrder.version(), deliveryInput.expectedVersion());
    require(pickup == customOrderOrder.deliveryMode().equals("buyer_pickup"));
    CustomFulfillmentMapper.Sale sale = customFulfillmentMapper.activeSale(id);
    boolean replacement = sale != null;
    if (replacement)
      require(
          "rework".equals(sale.resolutionType())
              && sale.replacementDeliveryId() == null
              && customOrderOrder.state().equals("after_sale"));
    else
      require(
          customOrderOrder.deliveryId() == null && customOrderOrder.state().equals(pickup ? "ready_for_delivery" : "delivering"));
    List<String> full = customOrderMapper.selectCustomOrderItemsByOrderId(id).stream().map(CustomOrderMapper.OrderItem::id).toList();
    if (full.size() != deliveryInput.orderItemIds().size()
        || !new HashSet<>(full).equals(new HashSet<>(deliveryInput.orderItemIds())))
      throw ApiFailure.invalid();
    LocalDateTime at = now(clock);
    LocalDateTime occurred = deliveryInput.occurredAt().atZoneSameInstant(ZONE).toLocalDateTime();
    if (occurred.isAfter(at)
        || occurred.isBefore(customOrderOrder.startedAt())
        || replacement && !occurred.isAfter(customFulfillmentMapper.selectCustomDeliveryRecordById(sale.deliveryId()).deliveredAt()))
      throw ApiFailure.invalid();
    List<String> keys = customMaterials.keys(tradingCommand.actor(), id, "delivery", deliveryInput.materialIds());
    String d = Ids.next();
    customFulfillmentMapper.insertCustomDeliveryRecord(
        d,
        id,
        tradingCommand.actor() + ":" + tradingCommand.requestId(),
        tradingCommand.actor(),
        pickup,
        json.writeValueAsString(keys),
        json.writeValueAsString(full),
        deliveryInput.remark(),
        occurred,
        at);
    customFulfillmentMapper.activateDelivery(id, d, at);
    if (replacement) {
      customFulfillmentMapper.markCustomDeliveryException(sale.deliveryId(), at);
      customFulfillmentMapper.updateCustomAfterSaleRequestReplacement(sale.id(), d, at);
      customAfterSaleCompletion.reconcile(id, at);
    }
    return tradingOperations.record(tradingCommand, "custom_delivery_record", d, customFulfillmentMapper.selectCustomDeliveryRecordById(d).deliveryVersion());
  }

  /** 核验当前版本后确认处理结果。 */
  public TradingReceipt confirm(
      TradingCommand tradingCommand, String id, DeliveryAction action, boolean pickup) {
    tradingOperations.lock(tradingCommand.actor());
    CustomOrderMapper.Order customOrderOrder = customPurchase.owned(tradingCommand.actor(), false, id, true);
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    require(action.deliveryId().equals(customOrderOrder.deliveryId()));
    CustomFulfillmentMapper.Delivery delivery = customFulfillmentMapper.selectCustomDeliveryRecordById(customOrderOrder.deliveryId());
    LocalDateTime at = now(clock);
    if (pickup && customOrderOrder.deliveryMode().equals("buyer_pickup") && delivery.deliveredAt() != null)
      return tradingOperations.record(tradingCommand, "custom_order", id, customOrderOrder.version());
    version(customOrderOrder.version(), action.expectedVersion());
    if (pickup) {
      require(customOrderOrder.state().equals("delivering") && delivery.state().equals("awaiting_pickup_confirmation"));
      require(customFulfillmentMapper.updateCustomDeliveryRecordPickup(delivery.id(), at) == 1);
      customFulfillmentMapper.activateDelivery(id, delivery.id(), at);
      customAfterSaleCompletion.reconcile(id, at);
    } else require(customFulfillmentMapper.completeCustomOrder(id, delivery.id(), at) > 0);
    return tradingOperations.record(tradingCommand, "custom_order", id, customOrderMapper.selectCustomOrderById(id, false).version());
  }

  /** 按到期时间执行自动验收。 */
  public boolean automatic(CustomFulfillmentMapper.Due task) {
    CustomOrderMapper.Order customOrderOrder = customOrderMapper.selectCustomOrderById(task.id(), true);
    LocalDateTime at = now(clock);
    if (customOrderOrder == null
        || !task.delivery().equals(customOrderOrder.deliveryId())
        || !"running".equals(customOrderOrder.acceptance())
        || customOrderOrder.deadline() == null
        || at.isBefore(customOrderOrder.deadline())
        || customFulfillmentMapper.selectCustomDeliveryRecordById(customOrderOrder.deliveryId()).deliveryVersion() != task.deliveryVersion()) return false;
    return customFulfillmentMapper.completeCustomOrder(customOrderOrder.id(), customOrderOrder.deliveryId(), at) > 0;
  }
}
