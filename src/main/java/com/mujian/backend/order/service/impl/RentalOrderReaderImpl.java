package com.mujian.backend.order.service.impl;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.delivery.service.OrderDisputeContext;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.delivery.mapper.OverdueMapper;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.order.domain.*;
import com.mujian.backend.order.domain.CheckoutBatchDO;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.domain.TradingSnapshots;
import com.mujian.backend.order.dto.OrderQuery;
import com.mujian.backend.order.mapper.*;
import com.mujian.backend.order.projection.OrderMetadataProjection;
import com.mujian.backend.order.projection.OrderOverviewProjection;
import com.mujian.backend.order.projection.RentalOrderListProjection;
import com.mujian.backend.delivery.service.DisputeGuard;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.*;
import com.mujian.backend.order.vo.CheckoutViews;
import com.mujian.backend.payment.mapper.RefundMapper;
import com.mujian.backend.prop.service.*;
import java.time.*;
import java.util.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 私有订单一致读取，不缓存资金状态；历史图片按已获权快照续签。 */
@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class RentalOrderReaderImpl implements RentalOrderReader {
  private final RentalOrderAccess rentalOrderAccess;
  private final TradingAccess tradingAccess;
  private final FactoryAccess factoryAccess;
  private final RentalOrderMapper rentalOrderMapper;
  private final CheckoutBatchMapper checkoutBatchMapper;
  private final RefundMapper refundMapper;
  private final TradeSnapshotReader tradeSnapshotReader;
  private final com.mujian.backend.delivery.policy.FulfillmentProtection fulfillmentProtection;
  private final OverdueMapper overdueMapper;
  private final ObjectMapper objectMapper;
  private final OrderDisputeContext orderDisputeContext;
  private final Clock clock;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public RentalOrderReaderImpl(
      RentalOrderAccess rentalOrderAccess,
      TradingAccess tradingAccess,
      FactoryAccess factoryAccess,
      RentalOrderMapper rentalOrderMapper,
      CheckoutBatchMapper checkoutBatchMapper,
      RefundMapper refundMapper,
      TradeSnapshotReader tradeSnapshotReader,
      OverdueMapper overdueMapper,
      com.mujian.backend.delivery.policy.FulfillmentProtection fulfillmentProtection,
      ObjectMapper objectMapper,
      OrderDisputeContext orderDisputeContext,
      Clock clock) {
    this.orderDisputeContext = orderDisputeContext;
    this.rentalOrderAccess = rentalOrderAccess;
    this.tradingAccess = tradingAccess;
    this.factoryAccess = factoryAccess;
    this.rentalOrderMapper = rentalOrderMapper;
    this.checkoutBatchMapper = checkoutBatchMapper;
    this.refundMapper = refundMapper;
    this.tradeSnapshotReader = tradeSnapshotReader;
    this.overdueMapper = overdueMapper;
    this.fulfillmentProtection = fulfillmentProtection;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 从当前会话读取工厂，拒绝通过参数伪造工厂归属。 */
  public String supplierFactory(String actor) {
    if (!D2Codes.CODE_SUPPLIER.equals(factoryAccess.context(actor, false).roleCode()))
      throw ApiFailure.denied(TradingReason.SUPPLIER_ROLE_REQUIRED.name());
    return factoryAccess.profile(actor, false, false).getId();
  }

  /** 先按授权范围筛选统计，再读取当前页业务数据。 */
  public PageResult<OrderViews.Summary> page(
      String actor, boolean supplier, String view, String keyword, PageQuery pageQueryPage) {
    String factoryId = null;
    if (supplier) factoryId = supplierFactory(actor);
    else tradingAccess.buyer(actor);
    return pageForRole(
        new OrderQuery(
            supplier ? null : actor, factoryId, view, keyword, null, null, pageQueryPage),
        supplier,
        false);
  }

  /** 管理调用者在外层核对菜单权限，业务列表和统计复用相同筛选。 */
  public PageResult<OrderViews.Summary> readPage(OrderQuery orderQuery, boolean supplier) {
    return pageForRole(orderQuery, supplier, true);
  }

  /** 按角色范围分页读取订单并生成可用操作。 */
  private PageResult<OrderViews.Summary> pageForRole(
      OrderQuery orderQuery, boolean supplier, boolean adminView) {
    if (!Set.of(
            "all",
            TradingState.pending_accept.name(),
            TradingState.accepted.name(),
            TradingState.cancelled.name(),
            "refund_exception", "exceptions",
            "factory_pending",
            "pending_outbound",
            "pending_receipt",
            "renting",
            "overdue",
            "return_in_transit",
            "return_checking",
            "return_closed",
            "completed")
        .contains(orderQuery.view())) throw ApiFailure.invalid();
    Optional<Set<String>> exclusions =
        "factory_pending".equals(orderQuery.view())
            ? fulfillmentProtection.blockedOrders(orderQuery.buyerId(), orderQuery.factoryId())
            : Optional.<Set<String>>empty();
    LocalDateTime at = now(clock);
    boolean ready = exclusions.isPresent();
    Set<String> blocked = exclusions.orElse(Set.of());
    List<RentalOrderListProjection> orderList =
        rentalOrderMapper.selectOrdersByQuery(orderQuery, at, ready, blocked);
    Map<String, OrderMetadataProjection> orderMetadataProjectionMapMetadata = new HashMap<>();
    List<String> orderIds = new ArrayList<>();
    for (RentalOrderListProjection orderRow : orderList) orderIds.add(orderRow.id());
    if (!orderIds.isEmpty())
      for (OrderMetadataProjection orderMetadataProjectionItem :
          rentalOrderMapper.selectOrderMetadata(
              orderIds, at, supplier && !adminView && rentalOrderMapper.hasSettlementTables()))
        orderMetadataProjectionMapMetadata.put(
            orderMetadataProjectionItem.orderId(), orderMetadataProjectionItem);
    Map<String, DisputeGuard.Facts> pageFacts = adminView ? Map.of() : fulfillmentProtection.pageFacts(orderIds);
    List<OrderViews.Summary> summaryListResult = new ArrayList<>();
    for (RentalOrderListProjection orderRow : orderList)
      summaryListResult.add(
          summary(
              orderRow,
              orderMetadataProjectionMapMetadata.get(orderRow.id()),
              supplier,
              adminView, pageFacts.getOrDefault(orderRow.id(), DisputeGuard.UNKNOWN)));
    return PageResult.of(
        summaryListResult,
        rentalOrderMapper.countOrdersByQuery(orderQuery, at, ready, blocked),
        orderQuery.page());
  }

  /** 按对象归属或对应菜单读取原业务详情。 */
  public OrderViews.Detail detail(String actor, String id, boolean supplier) {
    return detailForRole(rentalOrderAccess.owned(actor, id, supplier, false), supplier, false);
  }

  /** 获权后从原批次快照取明细、配送、规则，当前SKU下架不抹去历史。 */
  public OrderViews.Detail authorizedDetail(RentalOrderDO rentalOrderDO, boolean supplier) {
    return detailForRole(rentalOrderDO, supplier, true);
  }

  /** 按角色权限读取订单明细与可用操作。 */
  private OrderViews.Detail detailForRole(
      RentalOrderDO rentalOrderDO, boolean supplier, boolean adminView) {
    CheckoutBatchDO checkoutBatchDOBatch =
        checkoutBatchMapper.selectBatchById(rentalOrderDO.checkoutBatchId(), false);
    OrderMetadataProjection orderMetadataProjectionMetadata =
        rentalOrderMapper
            .selectOrderMetadata(
                List.of(rentalOrderDO.id()),
                now(clock),
                supplier && !adminView && rentalOrderMapper.hasSettlementTables())
            .get(0);
    CheckoutViews.FactoryQuote factoryQuoteGroup =
        tradeSnapshotReader.groups(checkoutBatchDOBatch.id(), rentalOrderDO.factoryId()).get(0);
    TradingSnapshots.Rule rule =
        objectMapper.readValue(
            checkoutBatchDOBatch.ruleSnapshotJson(), TradingSnapshots.Rule.class);
    // 旧单快照可能缺失，返回契约规定的完整性错误，避免把损坏的收货信息交给履约页。
    if (!StringUtils.hasText(rentalOrderDO.regionTextSnapshot())
        || !StringUtils.hasText(rentalOrderDO.detailAddressSnapshot())
        || !StringUtils.hasText(rentalOrderDO.receiverNameSnapshot())
        || !StringUtils.hasText(rentalOrderDO.receiverPhoneSnapshot()))
      throw new ApiFailure(500, D2Codes.CODE_DATA_INTEGRITY_ERROR, TradingReason.TRADE_DATA_INCONSISTENT.name());
    return new OrderViews.Detail(
        summary(RentalOrderListProjection.from(rentalOrderDO), orderMetadataProjectionMetadata, supplier, adminView,
            adminView ? DisputeGuard.UNKNOWN : fulfillmentProtection.facts(rentalOrderDO.id())),
        checkoutBatchDOBatch.id(),
        rentalOrderDO.paymentOrderId(),
        factoryQuoteGroup.items(),
        new OrderViews.Receiver(
            rentalOrderDO.receiverNameSnapshot(),
            rentalOrderDO.receiverPhoneSnapshot(),
            rentalOrderDO.regionTextSnapshot(),
            rentalOrderDO.detailAddressSnapshot()),
        rentalOrderDO.buyerRemark(),
        factoryQuoteGroup.outbound(),
        factoryQuoteGroup.returnRoute(),
        rentalOrderDO.transportNoticeSnapshot(),
        time(rentalOrderDO.acceptedAt()),
        rentalOrderDO.rejectReason(),
        rentalOrderDO.cancelReason(),
        refundMapper.selectRefundIdsByOrder(rentalOrderDO.id()),
        overdueMapper.hasActiveRisk(rentalOrderDO.id()),
        new OrderViews.CancellationRule(
            rule.cancellationRule(),
            rule.cancellationRuleVersion(),
            rule.cancellationDocumentId(),
            48,
            24,
            3000,
            6000),
        orderDisputeContext.read(rentalOrderDO, supplier, adminView));
  }

  /** 统计本工厂全部待办与退款异常，不截成首页展示条数。 */
  public OrderViews.Overview overview(String actor) {
    OrderOverviewProjection orderOverviewProjectionOverview =
        rentalOrderMapper.selectFactoryOrderOverview(supplierFactory(actor), now(clock));
    return new OrderViews.Overview(
        Long.toString(orderOverviewProjectionOverview.pendingAcceptCount()),
        Long.toString(orderOverviewProjectionOverview.acceptedCount()),
        Long.toString(orderOverviewProjectionOverview.refundExceptionCount()),
        Long.toString(orderOverviewProjectionOverview.allOrderCount()),
        time(now(clock)),
        Long.toString(orderOverviewProjectionOverview.pendingOutboundCount()),
        Long.toString(orderOverviewProjectionOverview.returnCheckingCount()), Long.toString(orderOverviewProjectionOverview.exceptionOrderCount()));
  }

  /** 按原订单、退款与配送事实派生展示状态和当前动作。 */
  private OrderViews.Summary summary(
      RentalOrderListProjection rentalOrderListProjection,
      OrderMetadataProjection orderMetadataProjectionMetadata,
      boolean supplier,
      boolean adminView, DisputeGuard.Facts facts) {
    if (orderMetadataProjectionMetadata == null)
      throw new IllegalStateException("order display source missing");
    List<String> actions = new ArrayList<>();
    if (supplier
        && TradingState.pending_accept.name().equals(rentalOrderListProjection.orderStatus())
        && now(clock).isBefore(rentalOrderListProjection.acceptDeadlineAt())) {
      actions.add(TradingNextAction.accept.name());
      actions.add(TradingNextAction.reject.name());
    }
    if (!supplier
        && Set.of(TradingState.pending_accept.name(), TradingState.accepted.name())
            .contains(rentalOrderListProjection.orderStatus())
        && TradingState.pending_outbound
            .name()
            .equals(orderMetadataProjectionMetadata.deliveryStatus()))
      actions.add(TradingNextAction.cancel.name());
    if (!TradingState.none.name().equals(rentalOrderListProjection.refundStatus()))
      actions.add(TradingNextAction.view_refunds.name());
    String display =
        TradingState.processing.name().equals(rentalOrderListProjection.refundStatus())
            ? TradingState.refunding.name()
            : TradingState.failed.name().equals(rentalOrderListProjection.refundStatus())
                ? TradingState.refund_failed.name()
                : Set.of(TradingState.partial_refunded.name(), TradingState.full_refunded.name())
                        .contains(rentalOrderListProjection.refundStatus())
                    ? TradingState.refunded.name()
                    : TradingState.accepted.name().equals(rentalOrderListProjection.orderStatus())
                        ? TradingState.pending_outbound.name()
                        : rentalOrderListProjection.orderStatus();
    String stage = orderMetadataProjectionMetadata.fulfillmentStage();
    if (TradingState.completed.name().equals(rentalOrderListProjection.orderStatus()))
      display = TradingState.completed.name();
    else if (!Set.of("not_started", "cancelled", "pending_outbound").contains(stage))
      display = stage;
    String pausedScene = orderMetadataProjectionMetadata.pausedIssueScene();
    if (pausedScene != null) {
      display = "receipt".equals(pausedScene) ? "receipt_issue_paused" : "in_use_issue_paused";
      actions.remove(TradingNextAction.cancel.name());
    }
    if (!supplier && !adminView && Set.of("accepted", "fulfilling").contains(rentalOrderListProjection.orderStatus())
        && Set.of("pending_outbound","pending_receipt","receipt_checking","renting","overdue").contains(stage))
      actions.add("report_issue");
    boolean admin = adminView;
    if (admin) actions.clear();
    if (!admin && !Set.of("not_started", "cancelled").contains(stage)) {
      if (!facts.ready()) actions.add("contact_support");
      if ("return_closed".equals(stage)) actions.add("view_return_result");
      else if (fulfillmentProtection.writable(facts)) {
        switch (stage) {
          case "pending_outbound" -> {
            if (supplier) actions.add("outbound");
          }
          case "pending_receipt", "receipt_checking" -> {
            if (!supplier) actions.add("confirm_receipt");
          }
          case "renting", "overdue" -> {
            if (supplier && "factory_pickup".equals(orderMetadataProjectionMetadata.returnMode()))
              actions.add("start_pickup");
            if (!supplier && "user_return".equals(orderMetadataProjectionMetadata.returnMode()))
              actions.add("return_shipment");
            if (supplier) actions.add("return_check");
          }
          case "return_in_transit", "return_checking" -> {
            if (supplier) actions.add("return_check");
          }
        }
      }
    }
    if (supplier && !admin) {
      if (orderMetadataProjectionMetadata.expenseRequired()
          && Set.of("accepted", "fulfilling", "return_checking", "completed")
              .contains(rentalOrderListProjection.orderStatus())) actions.add("transport_expense");
      if (orderMetadataProjectionMetadata.settledBatch()) actions.add("view_settlement");
    }
    OrderMetadataProjection metadata=orderMetadataProjectionMetadata;
    if (metadata.arbitrationActive()) display="arbitration_processing";
    else if (supplier || admin ? metadata.claimActive() : metadata.buyerClaimActive()) display="claim_processing";
    if (!admin){
      if (!supplier && metadata.issueExists()) actions.add("view_issues");
      if (supplier ? metadata.factoryExtensionExists() : metadata.extensionExists()) actions.add("view_extension");
      if (supplier ? metadata.claimExists() : metadata.buyerClaimExists()) actions.add("view_claim");
      if (metadata.arbitrationExists()) actions.add("view_arbitration");
      if (metadata.debtExists()) actions.add("view_receivable");
      if (supplier && metadata.claimStockWork() && !actions.contains("return_check")) actions.add("return_check");
      if (supplier && metadata.claimWindow() && actions.contains("return_check")) actions.add("create_claim");
      if (!supplier && "renting".equals(stage) && !metadata.pendingExtension() && now(clock).isBefore(OrderDisputeContext.latestPaidAt(rentalOrderListProjection.currentDueDate()).plusNanos(1))
          && fulfillmentProtection.writable(facts)) actions.add("request_extension");
      if (metadata.arbitrationActive() || metadata.claimActive()) actions.remove("report_issue");
    }
    if (orderMetadataProjectionMetadata.terminationDisplayStatus() != null) {
      display = orderMetadataProjectionMetadata.terminationDisplayStatus();
      actions.removeAll(Set.of("accept","reject","cancel","outbound","confirm_receipt","report_issue","request_extension","return_check","return_shipment","start_pickup"));
      if (!admin && !"terminated".equals(display) && fulfillmentProtection.terminationReturnAllowed(facts)) {
        if (supplier) actions.add("return_check");
        if ("termination_pending".equals(display)) {
          if (supplier && "factory_pickup".equals(orderMetadataProjectionMetadata.returnMode())) actions.add("start_pickup");
          if (!supplier && "user_return".equals(orderMetadataProjectionMetadata.returnMode())) actions.add("return_shipment");
        }
      }
    }
    CheckoutViews.Amounts amounts =
        new CheckoutViews.Amounts(
            rentalOrderListProjection.rentOriginalAmountFen(),
            rentalOrderListProjection.couponDiscountAmountFen(),
            rentalOrderListProjection.rentPaidAmountFen(),
            rentalOrderListProjection.depositPaidAmountFen(),
            rentalOrderListProjection.freightPrepaidPaidAmountFen(),
            rentalOrderListProjection.totalPaidAmountFen());
    return new OrderViews.Summary(
        rentalOrderListProjection.id(),
        rentalOrderListProjection.orderNo(),
        rentalOrderListProjection.factoryId(),
        orderMetadataProjectionMetadata.factoryName(),
        rentalOrderListProjection.orderStatus(),
        rentalOrderListProjection.acceptStatus(),
        rentalOrderListProjection.refundStatus(),
        display,
        rentalOrderListProjection.version(),
        rentalOrderListProjection.rentalStartDate().toString(),
        rentalOrderListProjection.rentalEndDate().toString(),
        amounts,
        rentalOrderListProjection.totalPaidAmountFen(),
        rentalOrderListProjection.totalRefundedAmountFen(),
        rentalOrderListProjection.totalDeductedAmountFen(),
        time(rentalOrderListProjection.acceptDeadlineAt()),
        time(rentalOrderListProjection.createdAt()),
        List.copyOf(actions),
        stage,
        time(orderMetadataProjectionMetadata.nextDeadlineAt()));
  }
}
