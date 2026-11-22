package com.mujian.backend.custom.service;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.support.D2Codes;

import com.mujian.backend.address.domain.ShippingAddressDO;
import com.mujian.backend.custom.dto.CustomForms;
import com.mujian.backend.payment.domain.ChannelSuborderDO;
import com.mujian.backend.payment.domain.PaymentOrderDO;
import com.mujian.backend.payment.dto.PaymentAccount;

import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.custom.service.CustomRequirements.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.address.mapper.AddressMapper;
import com.mujian.backend.common.api.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.custom.dto.CustomForms.*;
import com.mujian.backend.custom.mapper.*;
import com.mujian.backend.custom.mapper.CustomOrderMapper.Order;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.*;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import com.mujian.backend.payment.service.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
public class CustomPurchase {
  private final CustomRequirements customRequirements;
  private final CustomRequirementMapper customRequirementMapper;
  private final CustomOrderMapper customOrderMapper;
  private final TradingAccess tradingAccess;
  private final TradingOperations tradingOperations;
  private final AddressMapper addressMapper;
  private final LoginMapper loginMapper;
  private final PaymentOrderMapper paymentOrderMapper;
  private final PaymentGateway paymentGateway;
  private final CustomFunds customFunds;
  private final Clock clock;
  private final ObjectMapper json;

  public CustomPurchase(
      CustomRequirements customRequirements,
      CustomRequirementMapper customRequirementMapper,
      CustomOrderMapper customOrderMapper,
      TradingAccess tradingAccess,
      TradingOperations tradingOperations,
      AddressMapper addressMapper,
      LoginMapper loginMapper,
      PaymentOrderMapper paymentOrderMapper,
      PaymentGateway paymentGateway,
      CustomFunds customFunds,
      Clock clock,
      ObjectMapper json) {
    this.customRequirements = customRequirements;
    this.customRequirementMapper = customRequirementMapper;
    this.customOrderMapper = customOrderMapper;
    this.tradingAccess = tradingAccess;
    this.tradingOperations = tradingOperations;
    this.addressMapper = addressMapper;
    this.loginMapper = loginMapper;
    this.paymentOrderMapper = paymentOrderMapper;
    this.paymentGateway = paymentGateway;
    this.customFunds = customFunds;
    this.clock = clock;
    this.json = json;
  }

  /** 核对资源归属并按需锁定。 */
  public Order owned(String actor, boolean factory, String id, boolean lock) {
    String party = factory ? customRequirements.factory(actor) : actor;
    if (!factory) tradingAccess.buyer(actor);
    CustomOrderMapper.Order customOrderOrder = customOrderMapper.selectCustomOrderById(id, lock);
    if (customOrderOrder == null || !party.equals(factory ? customOrderOrder.factoryId() : customOrderOrder.buyerId())) throw missing();
    return customOrderOrder;
  }

  public record Attempt(String orderId, String paymentId) {}

  /** 校验当前事实并准备下一步操作。 */
  private Attempt prepare(TradingCommand cmd, Order o, LocalDateTime at) {
    if (!at.isBefore(o.quoteDeadline())) throw conflict(BusinessFailureReason.QUOTE_WINDOW_CLOSED.name());
    PaymentAccount account = paymentGateway.requireReady();
    String openid = loginMapper.selectWechatOpenIdByUserAndApp(cmd.actor(), account.appId());
    if (openid == null) throw ApiFailure.denied(D2Codes.CODE_ACCOUNT_ACTION_RESTRICTED);
    String payment = Ids.next();
    customOrderMapper.insertPaymentOrder(
        payment,
        cmd.actor(),
        cmd.actor() + ":" + cmd.requestId(),
        account,
        openid,
        o.totalFen(),
        at);
    if (customOrderMapper.insertPaymentChannelSuborder(Ids.next(), o.id(), payment, o.totalFen(), at) != 1)
      throw conflict(BusinessFailureReason.CUSTOM_STATE_CHANGED.name());
    customOrderMapper.bindCustomOrderPayment(o.id(), payment, at);
    tradingOperations.recordCustomPayment(cmd, o.id(), o.version() + 1, payment);
    return new Attempt(o.id(), payment);
  }

  /** 按报价版本确认采购并建立订单。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Attempt accept(TradingCommand tradingCommand, String id, AcceptQuote acceptQuote) {
    tradingOperations.lock(tradingCommand.actor());
    CustomRequirementMapper.Requirement root = customRequirements.owned(tradingCommand.actor(), false, id, true);
    String prior = tradingOperations.replayPayment(tradingCommand);
    if (prior != null) return new Attempt(customRequirementMapper.orderId(id), prior);
    tradingAccess.newTrade(tradingCommand.actor());
    version(root.version(), acceptQuote.expectedVersion());
    require(root.state().equals("quoted"));
    LocalDateTime at = now(clock);
    CustomRequirementMapper.Quote quote =
        customRequirementMapper.selectQuotesByRequirement(id, false).stream()
            .filter(q -> q.state().equals("published") && q.id().equals(acceptQuote.quoteId()))
            .findFirst()
            .orElseThrow(() -> conflict(BusinessFailureReason.CUSTOM_STATE_CHANGED.name()));
    if (!at.isBefore(quote.validUntil())) throw conflict(BusinessFailureReason.QUOTE_WINDOW_CLOSED.name());
    CustomForms.QuoteTerms terms = json.readValue(quote.terms(), QuoteTerms.class);
    String address = null;
    if (terms.deliveryMode().equals("factory_delivery")) {
      if (acceptQuote.addressId() == null) throw ApiFailure.invalid();
      ShippingAddressDO shippingAddressDO = addressMapper.selectOwnedAddressById(tradingCommand.actor(), acceptQuote.addressId(), true);
      if (shippingAddressDO == null || !"active".equals(shippingAddressDO.addressStatus())) throw missing();
      address = shippingAddressDO.receiverName() + " " + shippingAddressDO.phone() + " " + shippingAddressDO.regionText() + " " + shippingAddressDO.detailAddress();
    } else if (acceptQuote.addressId() != null) throw ApiFailure.invalid();
    String order = Ids.next();
    customOrderMapper.insertCustomOrder(order, quote.id(), terms.deliveryMode(), address, at);
    Map<String, RequirementItem> source = new HashMap<>();
    for (CustomForms.RequirementItem requirementItem : customRequirements.form(root).items()) source.put(requirementItem.id(), requirementItem);
    int sort = 0;
    for (CustomForms.QuotedItem quotedItem : terms.items())
      customOrderMapper.freezeItem(
          Ids.next(),
          order,
          quote.id(),
          quotedItem,
          json.writeValueAsString(quotedItem),
          json.writeValueAsString(source.get(quotedItem.itemId())),
          sort++,
          at);
    customRequirementMapper.acceptCustomQuote(quote.id(), at);
    customRequirementMapper.retireDrafts(id, at);
    customRequirementMapper.transitionRequirementState(id, "converted", null, at);
    return prepare(tradingCommand, customOrderMapper.selectCustomOrderById(order, true), at);
  }

  /** 使用已确认的原业务记录继续付款。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public Attempt retry(TradingCommand tradingCommand, String id, int expected) {
    tradingOperations.lock(tradingCommand.actor());
    CustomOrderMapper.Order customOrderOrder = owned(tradingCommand.actor(), false, id, true);
    String prior = tradingOperations.replayPayment(tradingCommand);
    if (prior != null) return new Attempt(id, prior);
    tradingAccess.newTrade(tradingCommand.actor());
    require("pending_payment".equals(customOrderOrder.state()));
    version(customOrderOrder.version(), expected);
    PaymentOrderDO paymentOrderDO = paymentOrderMapper.selectPaymentById(customOrderOrder.paymentId(), true);
    if (!CustomFunds.endedWithoutValidPayment(paymentOrderDO)) {
      tradingOperations.recordCustomPayment(tradingCommand, id, customOrderOrder.version(), paymentOrderDO.id());
      return new Attempt(id, paymentOrderDO.id());
    }
    return prepare(tradingCommand, customOrderOrder, now(clock));
  }

  /** 校验付款状态后登记开始生产。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt production(TradingCommand tradingCommand, String id, int expected) {
    tradingOperations.lock(tradingCommand.actor());
    boolean factory = D2Codes.CODE_SUPPLIER.equals(tradingCommand.operation().role());
    CustomOrderMapper.Order customOrderOrder = owned(tradingCommand.actor(), factory, id, true);
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    version(customOrderOrder.version(), expected);
    require("paid_waiting_production".equals(customOrderOrder.state()));
    LocalDateTime at = now(clock);
    String next;
    if (factory) {
      next = "producing";
    } else {
      ChannelSuborderDO channelSuborderDO = paymentOrderMapper.selectSubordersByPayment(customOrderOrder.paymentId(), true).get(0);
      require(
          channelSuborderDO.paidAt() != null && at.isBefore(channelSuborderDO.paidAt().plusHours(2)) && customOrderOrder.startedAt() == null);
      customFunds.reserve(
          customOrderOrder,
          channelSuborderDO,
          tradingCommand.actor(),
          "custom_order",
          id,
          "user_cancel_before_accept",
          "生产前取消",
          customOrderOrder.goods(),
          customOrderOrder.freight(),
          at);
      next = "user_cancelled";
    }
    customOrderMapper.updateCustomOrderState(id, next, at);
    return tradingOperations.record(tradingCommand, "custom_order", id, expected + 1);
  }
}
