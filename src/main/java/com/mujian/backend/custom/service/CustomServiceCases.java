package com.mujian.backend.custom.service;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.payment.domain.ChannelSuborderDO;

import static com.mujian.backend.common.support.D2Values.*;
import static com.mujian.backend.custom.service.CustomRequirements.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.custom.dto.CustomForms.*;
import com.mujian.backend.custom.mapper.*;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.TradingOperations;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CustomServiceCases {
  private final CustomOrderMapper customOrderMapper;
  private final CustomFulfillmentMapper customFulfillmentMapper;
  private final CustomPurchase customPurchase;
  private final CustomMaterials customMaterials;
  private final CustomCases customCases;
  private final CustomAfterSaleCompletion customAfterSaleCompletion;
  private final CustomFunds customFunds;
  private final PaymentOrderMapper paymentOrderMapper;
  private final VerificationAccess verificationAccess;
  private final TradingOperations tradingOperations;
  private final ObjectMapper json;
  private final Clock clock;
  private final CustomContextMapper customContextMapper;

  public CustomServiceCases(
      CustomContextMapper customContextMapper,
      CustomOrderMapper customOrderMapper,
      CustomFulfillmentMapper customFulfillmentMapper,
      CustomPurchase customPurchase,
      CustomMaterials customMaterials,
      CustomCases customCases,
      CustomAfterSaleCompletion customAfterSaleCompletion,
      CustomFunds customFunds,
      PaymentOrderMapper paymentOrderMapper,
      VerificationAccess verificationAccess,
      TradingOperations tradingOperations,
      ObjectMapper json,
      Clock clock) {
    this.customContextMapper = customContextMapper;
    this.customOrderMapper = customOrderMapper;
    this.customFulfillmentMapper = customFulfillmentMapper;
    this.customPurchase = customPurchase;
    this.customMaterials = customMaterials;
    this.customCases = customCases;
    this.customAfterSaleCompletion = customAfterSaleCompletion;
    this.customFunds = customFunds;
    this.paymentOrderMapper = paymentOrderMapper;
    this.verificationAccess = verificationAccess;
    this.tradingOperations = tradingOperations;
    this.json = json;
    this.clock = clock;
  }

  /** 核对交付前是否允许提交当前操作。 */
  private void beforeDelivery(CustomOrderMapper.Order o) {
    require(
        o.deliveryId() == null && Set.of("producing", "ready_for_delivery").contains(o.state()));
  }

  /** 保存延期申请草稿。 */
  public TradingReceipt extensionDraft(TradingCommand tradingCommand, String id, ExtensionInput extensionInput) {
    tradingOperations.lock(tradingCommand.actor());
    CustomOrderMapper.Order customOrderOrder = customPurchase.owned(tradingCommand.actor(), true, id, true);
    // 锁内优先返回原命令的回执，避免再次推进状态或占用资金。
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    version(customOrderOrder.version(), extensionInput.expectedVersion());
    beforeDelivery(customOrderOrder);
    require(customFulfillmentMapper.activeExtension(id) == null);
    if (!extensionInput.requestedDate().isAfter(customOrderOrder.promisedDate())) throw ApiFailure.invalid();
    LocalDateTime at = now(clock);
    String draft = customFulfillmentMapper.draftExtension(id);
    if (draft == null) draft = Ids.next();
    customFulfillmentMapper.insertCustomOrderExtensionRequestDraft(
        draft,
        id,
        tradingCommand.actor() + ":" + tradingCommand.requestId(),
        extensionInput,
        json.writeValueAsString(customMaterials.keys(tradingCommand.actor(), id, "extension", extensionInput.evidenceIds())),
        at);
    customOrderMapper.updateCustomOrderState(id, customOrderOrder.state(), at);
    return tradingOperations.record(
        tradingCommand, "custom_order_extension_request", draft, customFulfillmentMapper.selectCustomOrderExtensionRequestById(draft).version());
  }

  /** 提交延期申请进入审核。 */
  public TradingReceipt submitExtension(TradingCommand tradingCommand, String id, int expected) {
    tradingOperations.lock(tradingCommand.actor());
    CustomFulfillmentMapper.Extension extension = customFulfillmentMapper.selectCustomOrderExtensionRequestById(id);
    if (extension == null) throw missing();
    CustomOrderMapper.Order customOrderOrder = customPurchase.owned(tradingCommand.actor(), true, extension.orderId(), true);
    // 锁内优先返回原命令的回执，避免再次推进状态或占用资金。
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    CustomFulfillmentMapper.Extension lockedExtension = customFulfillmentMapper.selectCustomOrderExtensionRequestById(id);
    version(lockedExtension.version(), expected);
    beforeDelivery(customOrderOrder);
    require(
        lockedExtension.state().equals("draft")
            && lockedExtension.requestedDate().isAfter(customOrderOrder.promisedDate())
            && customFulfillmentMapper.activeExtension(customOrderOrder.id()) == null);
    LocalDateTime at = now(clock);
    customFulfillmentMapper.submitExtension(
        id, customCases.open(customOrderOrder, "custom_order_extension_request", id, at), customOrderOrder.promisedDate(), at);
    return tradingOperations.record(tradingCommand, "custom_order_extension_request", id, expected + 1);
  }

  /** 记录审核决定并推进对应业务状态。 */
  public TradingReceipt decide(TradingCommand tradingCommand, String id, ExtensionDecision extensionDecision) {
    tradingOperations.authorize(tradingCommand.actor(), tradingCommand.operation());
    tradingOperations.lock(tradingCommand.actor());
    CustomFulfillmentMapper.Extension extension = customFulfillmentMapper.selectCustomOrderExtensionRequestById(id);
    if (extension == null || extension.state().equals("draft")) throw missing();
    CustomOrderMapper.Order customOrderOrder = customOrderMapper.selectCustomOrderById(extension.orderId(), true);
    // 锁内优先返回原命令的回执，避免再次推进状态或占用资金。
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    CustomFulfillmentMapper.Extension lockedExtension = customFulfillmentMapper.selectCustomOrderExtensionRequestById(id);
    version(lockedExtension.version(), extensionDecision.expectedVersion());
    require(lockedExtension.state().equals("submitted"));
    LocalDateTime at = now(clock);
    if ("approved".equals(extensionDecision.decision())) {
      beforeDelivery(customOrderOrder);
      require(customOrderOrder.promisedDate().equals(lockedExtension.originalDate()));
      customFulfillmentMapper.updatePromisedCompletionDate(customOrderOrder.id(), lockedExtension.requestedDate(), at);
    }
    customFulfillmentMapper.updateCustomOrderExtensionRequestDecide(id, extensionDecision, at);
    customFulfillmentMapper.closeCase(lockedExtension.caseId(), tradingCommand.actor(), extensionDecision.note(), at);
    return tradingOperations.recordReason(
        tradingCommand, "custom_order_extension_request", id, lockedExtension.version() + 1, extensionDecision.note());
  }

  /** 登记待处理的问题及其来源。 */
  public TradingReceipt issue(TradingCommand tradingCommand, String id, Issue customIssue) {
    tradingOperations.lock(tradingCommand.actor());
    CustomOrderMapper.Order customOrderOrder = customPurchase.owned(tradingCommand.actor(), false, id, true);
    // 锁内优先返回原命令的回执，避免再次推进状态或占用资金。
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    version(customOrderOrder.version(), customIssue.expectedVersion());
    LocalDateTime at = now(clock);
    require(customOrderOrder.acceptsAfterSale(customIssue.deliveryId(), at) && customFulfillmentMapper.activeSale(id) == null);
    String sale = Ids.next();
    List<String> evidence = customMaterials.keys(tradingCommand.actor(), id, "after_sale", customIssue.evidenceIds());
    customFulfillmentMapper.insertCustomAfterSaleRequest(
        sale,
        id,
        tradingCommand.actor(),
        tradingCommand.actor() + ":" + tradingCommand.requestId(),
        customIssue,
        json.writeValueAsString(evidence),
        customCases.open(customOrderOrder, "custom_after_sale_request", sale, at),
        at);
    customFulfillmentMapper.pauseCustomOrderAcceptance(id, at);
    return tradingOperations.record(tradingCommand, "custom_after_sale_request", sale, 0);
  }

  /** 登记本次工厂说明。 */
  public TradingReceipt statement(TradingCommand tradingCommand, String id, StatementInput statementInput) {
    tradingOperations.lock(tradingCommand.actor());
    // 此次查询定位订单根，锁后再取可变的申请状态。
    CustomFulfillmentMapper.Sale customFulfillmentSale = customFulfillmentMapper.selectCustomAfterSaleRequestById(id);
    if (customFulfillmentSale == null) throw missing();
    customPurchase.owned(tradingCommand.actor(), true, customFulfillmentSale.orderId(), true);
    // 锁内优先返回原命令的回执，避免再次推进状态或占用资金。
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    CustomFulfillmentMapper.Sale sale = customFulfillmentMapper.selectCustomAfterSaleRequestById(id);
    version(sale.version(), statementInput.expectedVersion());
    require(Set.of("submitted", "processing", "waiting_factory").contains(sale.state()));
    List<String> keys = customMaterials.keys(tradingCommand.actor(), sale.orderId(), "delivery", statementInput.evidenceIds());
    LocalDateTime at = now(clock);
    customContextMapper.insertCustomAfterSaleFactoryStatement(
        Ids.next(),
        id,
        tradingCommand.actor(),
        text(statementInput.summary(), 1000),
        json.writeValueAsString(keys),
        at);
    customContextMapper.advanceSale(id, at);
    return tradingOperations.record(tradingCommand, "custom_after_sale_request", id, sale.version() + 1);
  }

  /** 开始处理已提交的申请。 */
  public TradingReceipt handle(TradingCommand tradingCommand, String id, int expected) {
    tradingOperations.lock(tradingCommand.actor());
    // 此次查询定位订单根，锁后再取可变的申请状态。
    CustomFulfillmentMapper.Sale customFulfillmentSale = customFulfillmentMapper.selectCustomAfterSaleRequestById(id);
    if (customFulfillmentSale == null) throw missing();
    customPurchase.owned(tradingCommand.actor(), true, customFulfillmentSale.orderId(), true);
    // 锁内优先返回原命令的回执，避免再次推进状态或占用资金。
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    // 锁住所属订单后重读售后申请，版本和可执行状态以此为准。
    CustomFulfillmentMapper.Sale lockedSale = customFulfillmentMapper.selectCustomAfterSaleRequestById(id);
    version(lockedSale.version(), expected);
    require("submitted".equals(lockedSale.state()));
    customFulfillmentMapper.processSale(id, now(clock));
    return tradingOperations.record(tradingCommand, "custom_after_sale_request", id, expected + 1);
  }

  /** 按当前事实确定处理结果。 */
  public TradingReceipt resolve(TradingCommand tradingCommand, String id, Resolution resolution) {
    tradingOperations.authorize(tradingCommand.actor(), tradingCommand.operation());
    long amount = (long) resolution.goodsRefundFen() + resolution.freightRefundFen();
    String type = resolution.resolutionType();
    if (amount > 0 || "full_refund".equals(type))
      verificationAccess.admin(tradingCommand.actor(), "admin:custom:refund-approve");
    tradingOperations.lock(tradingCommand.actor());
    // 此次查询定位订单根，锁后再取可变的申请状态。
    CustomFulfillmentMapper.Sale customFulfillmentSale = customFulfillmentMapper.selectCustomAfterSaleRequestById(id);
    if (customFulfillmentSale == null) throw missing();
    CustomOrderMapper.Order customOrderOrder = customOrderMapper.selectCustomOrderById(customFulfillmentSale.orderId(), true);
    // 锁内优先返回原命令的回执，避免再次推进状态或占用资金。
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    // 锁住所属订单后重读售后申请，版本和可执行状态以此为准。
    CustomFulfillmentMapper.Sale lockedSale = customFulfillmentMapper.selectCustomAfterSaleRequestById(id);
    version(lockedSale.version(), resolution.expectedVersion());
    require(
        Set.of("submitted", "processing").contains(lockedSale.state())
            && (lockedSale.resolutionType() == null || lockedSale.resolutionType().equals("platform_intervention")));
    ChannelSuborderDO channelSuborderDO = paymentOrderMapper.selectSubordersByPayment(customOrderOrder.paymentId(), true).get(0);
    CustomOrderMapper.Exposure available = customOrderMapper.selectRefundableCustomAmountsBySuborder(channelSuborderDO.id());
    if (amount > Integer.MAX_VALUE
        || resolution.goodsRefundFen() > available.goods()
        || resolution.freightRefundFen() > available.freight())
      throw conflict(BusinessFailureReason.REFUND_ALLOCATION_EXCEEDED.name());
    boolean full =
        resolution.goodsRefundFen() == available.goods()
            && resolution.freightRefundFen() == available.freight();
    if ("full_refund".equals(type) && (!full || available.pending() != 0 || amount == 0)
        || "partial_refund".equals(type) && (amount == 0 || full)
        || "rework".equals(type) && full && amount > 0
        || amount > 0 && !Set.of("rework", "full_refund", "partial_refund").contains(type))
      throw ApiFailure.invalid();
    LocalDateTime at = now(clock);
    // 在同一事务内按子单可退货款和运费预留，随后再登记处理结果。
    String refund =
        amount == 0
            ? null
            : customFunds.reserve(
                customOrderOrder,
                channelSuborderDO,
                tradingCommand.actor(),
                "custom_after_sale_request",
                id,
                "customer_service_refund",
                resolution.reason(),
                resolution.goodsRefundFen(),
                resolution.freightRefundFen(),
                at);
    String state =
        "rework".equals(type)
            ? "waiting_factory"
            : refund != null ? "refund_processing" : "processing";
    customFulfillmentMapper.updateCustomAfterSaleRequestResolve(id, resolution, state, refund, at);
    customAfterSaleCompletion.reconcile(customOrderOrder.id(), at);
    return tradingOperations.recordReason(
        tradingCommand, "custom_after_sale_request", id, customFulfillmentMapper.selectCustomAfterSaleRequestById(id).version(), resolution.reason());
  }
}
