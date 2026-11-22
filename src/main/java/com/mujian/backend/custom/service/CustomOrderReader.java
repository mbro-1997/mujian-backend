package com.mujian.backend.custom.service;

import com.mujian.backend.custom.dto.CustomForms;
import com.mujian.backend.payment.domain.ChannelSuborderDO;
import com.mujian.backend.payment.domain.PaymentOrderDO;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.custom.dto.CustomForms.EvidenceRef;
import com.mujian.backend.custom.mapper.*;
import com.mujian.backend.custom.mapper.CustomFulfillmentMapper.*;
import com.mujian.backend.custom.mapper.CustomOrderMapper.Order;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.material.service.VerificationAccess;
import com.mujian.backend.order.service.TradingAccess;
import com.mujian.backend.order.vo.BatchViews;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class CustomOrderReader {
  public record Summary(
      String id,
      String number,
      int version,
      String requirementId,
      String factoryId,
      String state,
      int totalFen,
      String updatedAt) {}

  public record DeliveryView(
      String id,
      int deliveryVersion,
      String mode,
      String state,
      String pickupRegisteredAt,
      String deliveredAt,
      List<EvidenceRef> evidence,
      String remark) {}

  public record ExtensionView(
      String id,
      String orderId,
      int version,
      String state,
      LocalDate originalDate,
      LocalDate requestedDate,
      String reason,
      List<EvidenceRef> evidence,
      String decisionNote) {}

  public record SaleView(
      String id,
      String orderId,
      int version,
      String state,
      String deliveryId,
      String problem,
      List<EvidenceRef> evidence,
      String requestedSolution,
      String resolutionType,
      String resolutionNote,
      int goodsRefundFen,
      int freightRefundFen,
      int goodsAvailableFen,
      int freightAvailableFen,
      String refundId,
      String replacementDeliveryId,
      String issueType) {}

  public record Detail(
      String id,
      String number,
      int version,
      String requirementId,
      String factoryId,
      String state,
      int totalFen,
      String updatedAt,
      CustomRequirements.PublishedQuote quote,
      String addressSnapshot,
      LocalDate promisedDate,
      BatchViews.Payment payment,
      String productionStartedAt,
      String currentDeliveryId,
      List<DeliveryView> deliveries,
      String acceptanceState,
      String acceptanceDeadlineAt,
      Integer remainingSeconds,
      List<ExtensionView> extensions,
      List<SaleView> afterSales,
      int refundedFen,
      List<String> allowedActions,
      List<CustomOrderMapper.OrderItem> items) {}

  private final CustomOrderMapper customOrderMapper;
  private final CustomFulfillmentMapper customFulfillmentMapper;
  private final CustomRequirementMapper customRequirementMapper;
  private final CustomRequirements customRequirements;
  private final CustomPurchase customPurchase;
  private final CustomMaterials customMaterials;
  private final PaymentOrderMapper paymentOrderMapper;
  private final VerificationAccess verificationAccess;
  private final TradingAccess tradingAccess;
  private final FactoryAccess factoryAccess;
  private final Clock clock;

  public CustomOrderReader(
      CustomOrderMapper customOrderMapper,
      CustomFulfillmentMapper customFulfillmentMapper,
      CustomRequirementMapper customRequirementMapper,
      CustomRequirements customRequirements,
      CustomPurchase customPurchase,
      CustomMaterials customMaterials,
      PaymentOrderMapper paymentOrderMapper,
      VerificationAccess verificationAccess,
      TradingAccess tradingAccess,
      FactoryAccess factoryAccess,
      Clock clock) {
    this.customOrderMapper = customOrderMapper;
    this.customFulfillmentMapper = customFulfillmentMapper;
    this.customRequirementMapper = customRequirementMapper;
    this.customRequirements = customRequirements;
    this.customPurchase = customPurchase;
    this.customMaterials = customMaterials;
    this.paymentOrderMapper = paymentOrderMapper;
    this.verificationAccess = verificationAccess;
    this.tradingAccess = tradingAccess;
    this.factoryAccess = factoryAccess;
    this.clock = clock;
  }

  /** 按当前身份和筛选条件返回分页摘要。 */
  public PageResult<Summary> page(String actor, boolean factory, String status, PageQuery page) {
    String f = factory ? customRequirements.factory(actor) : null;
    if (!factory) tradingAccess.buyer(actor);
    List<CustomOrderReader.Summary> items =
        customOrderMapper.selectCustomOrderPageByQuery(actor, f, status, page).stream()
            .map(
                o ->
                    new Summary(
                        o.id(),
                        o.number(),
                        o.version(),
                        o.requirementId(),
                        o.factoryId(),
                        o.state(),
                        o.totalFen(),
                        time(o.updatedAt())))
            .toList();
    return PageResult.of(items, customOrderMapper.selectCustomOrderCountByQuery(actor, f, status), page);
  }

  /** 读取延期申请及当前可用操作。 */
  private ExtensionView extension(Extension e, Map<String, EvidenceRef> files) {
    return new ExtensionView(
        e.id(),
        e.orderId(),
        e.version(),
        e.state(),
        e.originalDate(),
        e.requestedDate(),
        e.reason(),
        customMaterials.references(e.evidence(), files),
        e.decisionNote());
  }

  /** 读取售后申请及当前可用操作。 */
  private SaleView sale(
      Sale s, Map<String, EvidenceRef> files, CustomOrderMapper.Exposure available) {
    return new SaleView(
        s.id(),
        s.orderId(),
        s.version(),
        s.state(),
        s.deliveryId(),
        s.problem(),
        customMaterials.references(s.evidence(), files),
        s.requestedSolution(),
        s.resolutionType(),
        s.resolutionNote(),
        s.goodsRefundFen(),
        s.freightRefundFen(),
        available.goods(),
        available.freight(),
        s.refundId(),
        s.replacementDeliveryId(),
        s.issueType());
  }

  /** 计算货款与运费各自可退款额。 */
  private CustomOrderMapper.Exposure exposure(Order o) {
    ChannelSuborderDO channelSuborderDO = paymentOrderMapper.selectSubordersByPayment(o.paymentId(), false).get(0);
    CustomOrderMapper.Exposure exposure = customOrderMapper.selectRefundableCustomAmountsBySuborder(channelSuborderDO.id());
    return exposure == null ? new CustomOrderMapper.Exposure(0, 0, 0) : exposure;
  }

  /** 组装当前身份有权查看的明细与操作。 */
  public Detail detail(String actor, boolean factory, String id) {
    CustomOrderMapper.Order customOrderOrder = customPurchase.owned(actor, factory, id, false);
    Map<String,CustomForms.EvidenceRef> files = customMaterials.index(id);
    PaymentOrderDO paymentOrderDO = paymentOrderMapper.selectPaymentById(customOrderOrder.paymentId(), false);
    BatchViews.Payment payment =
        new BatchViews.Payment(
            paymentOrderDO.id(),
            paymentOrderDO.paymentNo(),
            paymentOrderDO.paymentStatus(),
            paymentOrderDO.payableAmountFen(),
            paymentOrderDO.paidAmountFen(),
            paymentOrderDO.refundedAmountFen(),
            time(paymentOrderDO.expireAt()),
            time(paymentOrderDO.paidAt()),
            time(paymentOrderDO.lastCheckedAt()));
    List<CustomOrderReader.DeliveryView> deliveries =
        customFulfillmentMapper.selectDeliveriesByCustomOrder(id).stream()
            .map(
                d ->
                    new DeliveryView(
                        d.id(),
                        d.deliveryVersion(),
                        d.mode(),
                        d.state(),
                        time(d.pickupRegisteredAt()),
                        time(d.deliveredAt()),
                        customMaterials.references(d.evidence(), files),
                        d.remark()))
            .toList();
    List<CustomOrderReader.ExtensionView> extensions = customFulfillmentMapper.selectExtensionsByCustomOrder(id, factory).stream().map(e -> extension(e, files)).toList();
    CustomOrderMapper.Exposure available = exposure(customOrderOrder);
    List<CustomOrderReader.SaleView> sales = customFulfillmentMapper.selectAfterSalesByCustomOrder(id).stream().map(s -> sale(s, files, available)).toList();
    ArrayList<String> actions = new ArrayList<String>();
    boolean pickup = "buyer_pickup".equals(customOrderOrder.deliveryMode());
    LocalDateTime at = now(clock);
    if (factory) {
      switch (customOrderOrder.state()) {
        case "paid_waiting_production" -> actions.add("startCustomProduction");
        case "producing" -> actions.add("readyCustomDelivery");
        case "ready_for_delivery" ->
            actions.add(pickup ? "registerCustomPickup" : "dispatchCustomDelivery");
        case "delivering" -> {
          if (!pickup) actions.add("deliverCustomOrder");
        }
        case "after_sale" -> {
          CustomFulfillmentMapper.Sale active = customFulfillmentMapper.activeSale(id);
          if (active != null) {
            if (active.state().equals("submitted")) actions.add("startAfterSaleHandling");
            if ("rework".equals(active.resolutionType()) && active.replacementDeliveryId() == null)
              actions.add(pickup ? "registerCustomPickup" : "deliverCustomOrder");
          }
        }
      }
      if (Set.of("producing", "ready_for_delivery").contains(customOrderOrder.state())
          && customFulfillmentMapper.activeExtension(id) == null) {
        actions.add("saveCustomExtension");
        if (customFulfillmentMapper.draftExtension(id) != null) actions.add("submitCustomExtension");
      }
    } else
      switch (customOrderOrder.state()) {
        case "pending_payment" -> {
          actions.add("getCustomPayment");
          if (at.isBefore(customOrderOrder.quoteDeadline())
              && CustomFunds.endedWithoutValidPayment(paymentOrderDO)
              && "active".equals(factoryAccess.context(actor, false).userStatus()))
            actions.add("launchCustomPayment");
        }
        case "paid_waiting_production" -> {
          if (paymentOrderDO.paidAt() != null && at.isBefore(paymentOrderDO.paidAt().plusHours(2)))
            actions.add("cancelBeforeProduction");
        }
        case "delivering" -> {
          if (pickup
              && !deliveries.isEmpty()
              && deliveries.get(0).state().equals("awaiting_pickup_confirmation"))
            actions.add("confirmCustomPickup");
        }
        case "pending_acceptance" -> {
          actions.add("acceptCustomDelivery");
          if (customOrderOrder.acceptsAfterSale(customOrderOrder.deliveryId(), at)) actions.add("reportCustomAfterSale");
        }
      }
    CustomRequirementMapper.Quote customRequirementQuote =
        customRequirementMapper.selectQuotesByRequirement(customOrderOrder.requirementId(), false).stream()
            .filter(q -> q.id().equals(customOrderOrder.quoteId()))
            .findFirst()
            .orElseThrow();
    String address = customOrderOrder.address();
    if (factory && address != null)
      address = address.replaceAll("(?<![0-9])([0-9]{3})[0-9]{4}([0-9]{4})(?![0-9])", "$1****$2");
    return new Detail(
        id,
        customOrderOrder.number(),
        customOrderOrder.version(),
        customOrderOrder.requirementId(),
        customOrderOrder.factoryId(),
        customOrderOrder.state(),
        customOrderOrder.totalFen(),
        time(customOrderOrder.updatedAt()),
        customRequirements.quote(customRequirementQuote),
        address,
        customOrderOrder.promisedDate(),
        payment,
        time(customOrderOrder.startedAt()),
        customOrderOrder.deliveryId(),
        deliveries,
        customOrderOrder.acceptance(),
        time(customOrderOrder.deadline()),
        customOrderOrder.remaining(),
        extensions,
        sales,
        customOrderOrder.refundedFen(),
        actions,
        customOrderMapper.selectCustomOrderItemsByOrderId(id));
  }

  /** 核对审核权限后读取延期详情。 */
  public ExtensionView adminExtension(String actor, String id) {
    verificationAccess.admin(actor, "admin:custom:extension-read");
    CustomFulfillmentMapper.Extension extension = customFulfillmentMapper.selectCustomOrderExtensionRequestById(id);
    if (extension == null || extension.state().equals("draft")) throw missing();
    return extension(extension, customMaterials.index(extension.orderId()));
  }

  /** 核对审核权限后读售后详情。 */
  public SaleView adminSale(String actor, String id) {
    verificationAccess.admin(actor, "admin:custom:after-sale-read");
    CustomFulfillmentMapper.Sale customFulfillmentSale = customFulfillmentMapper.selectCustomAfterSaleRequestById(id);
    if (customFulfillmentSale == null) throw missing();
    return sale(customFulfillmentSale, customMaterials.index(customFulfillmentSale.orderId()), exposure(customOrderMapper.selectCustomOrderById(customFulfillmentSale.orderId(), false)));
  }

  /** 分页读取当前范围内的延期申请。 */
  public PageResult<ExtensionView> extensions(String actor, String status, PageQuery page) {
    verificationAccess.admin(actor, "admin:custom:extension-read");
    List<CustomFulfillmentMapper.Extension> records = customFulfillmentMapper.extensionsPage(status, page);
    Map<String,Map<String,CustomForms.EvidenceRef>> files = customMaterials.indexes(records.stream().map(Extension::orderId).distinct().toList());
    return PageResult.of(
        records.stream().map(e -> extension(e, files.getOrDefault(e.orderId(), Map.of()))).toList(),
        customFulfillmentMapper.extensionsCount(status),
        page);
  }

  /** 分页读取当前范围内的售后申请。 */
  public PageResult<SaleView> sales(String actor, String status, PageQuery page) {
    verificationAccess.admin(actor, "admin:custom:after-sale-read");
    List<CustomFulfillmentMapper.Sale> records = customFulfillmentMapper.salesPage(status, page);
    List<String> ids = records.stream().map(Sale::orderId).distinct().toList();
    Map<String,Map<String,CustomForms.EvidenceRef>> files = customMaterials.indexes(ids);
    Map<String, CustomOrderMapper.Exposure> available = new HashMap<>();
    if (!ids.isEmpty()) {
      for (CustomOrderMapper.OrderExposure orderExposure : customOrderMapper.pageExposures(ids))
        available.put(
            orderExposure.orderId(),
            new CustomOrderMapper.Exposure(orderExposure.goods(), orderExposure.freight(), orderExposure.pending()));
    }
    CustomOrderMapper.Exposure zero = new CustomOrderMapper.Exposure(0, 0, 0);
    return PageResult.of(
        records.stream()
            .map(
                s ->
                    sale(
                        s,
                        files.getOrDefault(s.orderId(), Map.of()),
                        available.getOrDefault(s.orderId(), zero)))
            .toList(),
        customFulfillmentMapper.salesCount(status),
        page);
  }
}
