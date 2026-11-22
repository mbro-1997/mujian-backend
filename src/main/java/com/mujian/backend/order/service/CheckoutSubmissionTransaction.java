package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.coupon.domain.CouponUsageDO;
import com.mujian.backend.coupon.dto.CouponWrites;
import com.mujian.backend.coupon.mapper.*;
import com.mujian.backend.coupon.projection.*;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.mapper.TransportMapper;
import com.mujian.backend.factory.mapper.FactoryProfileMapper;
import com.mujian.backend.factory.projection.FactorySourceProjection;
import com.mujian.backend.factory.service.FactoryAccess;
import com.mujian.backend.order.domain.*;
import com.mujian.backend.order.dto.*;
import com.mujian.backend.order.mapper.CheckoutBatchMapper;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.payment.domain.*;
import com.mujian.backend.payment.dto.PaymentAccount;
import com.mujian.backend.payment.mapper.PaymentOrderMapper;
import com.mujian.backend.planning.domain.RentalChecklistDO;
import com.mujian.backend.planning.mapper.ChecklistMapper;
import com.mujian.backend.prop.dto.*;
import com.mujian.backend.prop.service.TradingReservations;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.ObjectMapper;

/** 交易提交原子边界；只做短事务，不持有数据库锁调用外部渠道。 */
@Service
public class CheckoutSubmissionTransaction {
  private final FactoryAccess factoryAccess;
  private final FactoryProfileMapper factoryProfileMapper;
  private final TradingOperations tradingOperations;
  private final TradingAccess tradingAccess;
  private final ChecklistMapper checklistMapper;
  private final TransportMapper transportMapper;
  private final TradingReservations tradingReservations;
  private final CouponMapper couponMapper;
  private final CouponUsageMapper couponUsageMapper;
  private final CheckoutQuotation checkoutQuotation;
  private final CheckoutBatchMapper checkoutBatchMapper;
  private final PaymentOrderMapper paymentOrderMapper;
  private final TradeRuleSnapshots tradeRuleSnapshots;
  private final LoginMapper loginMapper;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 沿已有根锁、库存算法和显式Mapper组装同一事务。 */
  public CheckoutSubmissionTransaction(
      FactoryAccess factoryAccess,
      FactoryProfileMapper factoryProfileMapper,
      TradingOperations tradingOperations,
      TradingAccess tradingAccess,
      ChecklistMapper checklistMapper,
      TransportMapper transportMapper,
      TradingReservations tradingReservations,
      CouponMapper couponMapper,
      CouponUsageMapper couponUsageMapper,
      CheckoutQuotation checkoutQuotation,
      CheckoutBatchMapper checkoutBatchMapper,
      PaymentOrderMapper paymentOrderMapper,
      TradeRuleSnapshots tradeRuleSnapshots,
      LoginMapper loginMapper,
      ObjectMapper objectMapper,
      Clock clock) {
    this.factoryAccess = factoryAccess;
    this.factoryProfileMapper = factoryProfileMapper;
    this.tradingOperations = tradingOperations;
    this.tradingAccess = tradingAccess;
    this.checklistMapper = checklistMapper;
    this.transportMapper = transportMapper;
    this.tradingReservations = tradingReservations;
    this.couponMapper = couponMapper;
    this.couponUsageMapper = couponUsageMapper;
    this.checkoutQuotation = checkoutQuotation;
    this.checkoutBatchMapper = checkoutBatchMapper;
    this.paymentOrderMapper = paymentOrderMapper;
    this.tradeRuleSnapshots = tradeRuleSnapshots;
    this.loginMapper = loginMapper;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 重放优先，然后按用户→工厂→清单→车型规则→SKU库存→券锁序一次提交。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public TradingReceipt submit(
      TradingCommand tradingCommand,
      CheckoutQuoteData checkoutQuoteData,
      PaymentAccount paymentAccount) {
    List<String> users = new ArrayList<>();
    users.add(tradingCommand.actor());
    for (CheckoutQuoteData.Factory factory : checkoutQuoteData.factories())
      users.add(factory.ownerUserId());
    factoryAccess.lock(users.toArray(String[]::new));
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    tradingAccess.newTrade(tradingCommand.actor());
    LocalDateTime at = now(clock);
    if (!at.isBefore(checkoutQuoteData.expiresAt()))
      throw conflict(TradingReason.QUOTE_EXPIRED.name());
    List<CheckoutQuoteData.Factory> factoryListFactories =
        new ArrayList<>(checkoutQuoteData.factories());
    factoryListFactories.sort(Comparator.comparing(CheckoutQuoteData.Factory::factoryId));
    for (CheckoutQuoteData.Factory factory : factoryListFactories) {
      FactorySourceProjection factorySourceProjection =
          factoryProfileMapper.selectFactoryProfileByOwner(factory.ownerUserId(), true);
      if (factorySourceProjection == null
          || !factory.factoryId().equals(factorySourceProjection.getId()))
        throw conflict(TradingReason.QUOTE_CHANGED.name());
    }
    RentalChecklistDO rentalChecklistDO =
        checklistMapper.selectOwnedChecklistById(
            tradingCommand.actor(), checkoutQuoteData.input().checklistId(), true);
    if (rentalChecklistDO == null) throw missing();
    if ("checkout_processing".equals(rentalChecklistDO.checklistStatus()))
      throw conflict(TradingReason.CHECKLIST_PAYMENT_IN_PROGRESS.name());
    if (rentalChecklistDO.version() != checkoutQuoteData.input().expectedVersion())
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    Set<String> vehicles = new TreeSet<>(), rules = new TreeSet<>();
    List<ReservationItem> reservationItemListItems = new ArrayList<>();
    for (CheckoutQuoteData.Factory factory : factoryListFactories) {
      for (CheckoutQuoteData.Leg leg : List.of(factory.outbound(), factory.returnLeg()))
        if (leg.vehicle() != null) {
          vehicles.add(leg.vehicle().id());
          rules.add(leg.rule().id());
        }
      for (CheckoutQuoteData.Item item : factory.items())
        reservationItemListItems.add(
            new ReservationItem(item.snapshot().skuId(), item.snapshotItemId(), item.quantity()));
    }
    for (String vehicle : vehicles)
      if (transportMapper.selectVehicleById(vehicle, true) == null)
        throw conflict(TradingReason.QUOTE_CHANGED.name());
    for (String rule : rules)
      if (transportMapper.selectPriceRuleById(rule, true) == null)
        throw conflict(TradingReason.QUOTE_CHANGED.name());
    List<ReservationAssignment> reservationAssignmentListAssignments =
        tradingReservations.allocate(
            reservationItemListItems, checkoutQuoteData.startDate(), checkoutQuoteData.endDate());
    Map<String, List<String>> snIds = new HashMap<>();
    for (ReservationAssignment reservationAssignmentAssignment :
        reservationAssignmentListAssignments)
      snIds.put(
          reservationAssignmentAssignment.snapshotItemId(),
          reservationAssignmentAssignment.snIds());
    CouponProjection couponProjection = null;
    if (checkoutQuoteData.input().couponId() != null) {
      couponProjection =
          couponMapper.selectCouponByUserAndId(
              tradingCommand.actor(), checkoutQuoteData.input().couponId(), true);
      if (couponProjection == null
          || couponProjection.version() != checkoutQuoteData.couponVersion())
        throw conflict(TradingReason.COUPON_UNAVAILABLE.name());
    }
    // 已验签路线仍在五分钟内；计算入口在confirmed分支仅复核数据库，不重新请求地图。
    checkoutQuotation.calculate(
        tradingCommand.actor(), checkoutQuoteData.input(), checkoutQuoteData);
    String openid =
        loginMapper.selectWechatOpenIdByUserAndApp(tradingCommand.actor(), paymentAccount.appId());
    if (openid == null) throw ApiFailure.denied(D2Codes.CODE_ACCOUNT_ACTION_RESTRICTED);
    String batchId = Ids.next(), paymentId = Ids.next();
    LocalDateTime expiry = at.plusMinutes(10);
    TradingSnapshots.Rule ruleSnapshot =
        tradeRuleSnapshots.capture(
            checkoutQuoteData.input().couponId(),
            couponProjection == null ? null : couponProjection.templateVersion(),
            at);
    checkoutBatchMapper.insertBatch(
        new CheckoutWrites.Batch(
            batchId,
            paymentId,
            tradingCommand.requestId(),
            tradingCommand.requestHash(),
            checkoutQuoteData,
            objectMapper.writeValueAsString(ruleSnapshot),
            "预收运费按指导上限收取，实际运输费用按履约核算。",
            at,
            expiry));
    paymentOrderMapper.insertPayment(
        new PaymentOrderDO(
            paymentId,
            paymentId,
            "initial:" + batchId,
            batchId,
            tradingCommand.actor(),
            paymentAccount.appId(),
            paymentAccount.mchId(),
            paymentAccount.notifyUrl(),
            null,
            paymentId,
            openid,
            null,
            TradingState.pending.name(),
            checkoutQuoteData.amounts().totalFen(),
            0,
            0,
            expiry,
            null,
            null,
            0,
            at,
            at,
            at,
            null,
            0,
            null,
            null,
            null));
    for (CheckoutQuoteData.Factory factory : factoryListFactories) {
      String suborderId = Ids.next(), transportId = Ids.next();
      paymentOrderMapper.insertSuborder(
          new ChannelSuborderDO(
              suborderId,
              paymentId,
              factory.factoryId(),
              factory.merchantId(),
              factory.subMchid(),
              suborderId,
              null,
              factory.amounts().totalFen(),
              TradingState.pending.name(),
              null,
              "幕间道具租赁",
              true,
              TradingState.pending.name(),
              null,
              0,
              0,
              null,
              null,
              0,
              null,
              TradingState.none.name(),
              at,
              at));
      String regionCode = null;
      if (factory.outbound().route() != null)
        regionCode = factory.outbound().route().to().regionCode();
      else if (factory.returnLeg().route() != null)
        regionCode = factory.returnLeg().route().from().regionCode();
      TradingSnapshots.Receiver receiver =
          new TradingSnapshots.Receiver(
              checkoutQuoteData.address().receiverName(),
              checkoutQuoteData.address().phone(),
              regionCode,
              checkoutQuoteData.address().regionText(),
              checkoutQuoteData.address().detailAddress(),
              checkoutQuoteData.address().id(),
              checkoutQuoteData.address().version());
      TradingSnapshots.Routes routesSnapshot =
          new TradingSnapshots.Routes(
              1, receiver, factory.profileVersion(), factory.outbound(), factory.returnLeg());
      List<String> requirements = new ArrayList<>();
      for (CheckoutQuoteData.Item item : factory.items())
        if (item.transportRequirement() != null
            && !requirements.contains(item.transportRequirement()))
          requirements.add(item.transportRequirement());
      String requirement = String.join("；", requirements);
      if (requirement.codePointCount(0, requirement.length()) > 2000)
        requirement = requirement.substring(0, requirement.offsetByCodePoints(0, 2000));
      checkoutBatchMapper.insertTransportSnapshot(
          new CheckoutWrites.Transport(
              transportId,
              batchId,
              factory,
              receiver.regionText() + receiver.detailAddress(),
              objectMapper.writeValueAsString(routesSnapshot),
              requirement.isEmpty() ? null : requirement,
              at));
      for (CheckoutQuoteData.Item item : factory.items()) {
        String image =
            item.snapshot().imageKeys().isEmpty() ? null : item.snapshot().imageKeys().get(0);
        TradingSnapshots.Item itemSnapshot =
            new TradingSnapshots.Item(
                1,
                image,
                item.snapshot().lengthCm(),
                item.snapshot().widthCm(),
                item.snapshot().heightCm(),
                item.snapshot().weightKg(),
                item.packagingFactorBps(),
                item.skuVersion(),
                snIds.get(item.snapshotItemId()));
        checkoutBatchMapper.insertItemSnapshot(
            new CheckoutWrites.Item(
                batchId,
                rentalChecklistDO.id(),
                transportId,
                factory,
                item,
                checkoutQuoteData,
                image,
                String.join("、", item.snapshot().styleLabels()),
                objectMapper.writeValueAsString(itemSnapshot),
                at));
      }
    }
    tradingReservations.hold(
        batchId,
        reservationAssignmentListAssignments,
        checkoutQuoteData.startDate(),
        checkoutQuoteData.endDate(),
        expiry);
    if (couponProjection != null) {
      Set<String> eligibleFactories = new HashSet<>();
      if ("factory".equals(couponProjection.scopeType()))
        for (CouponScopeProjection couponScopeProjectionScope :
            couponMapper.selectScopesByTemplateIds(List.of(couponProjection.templateId())))
          eligibleFactories.add(couponScopeProjectionScope.factoryId());
      int eligibleRent = 0;
      for (CheckoutQuoteData.Factory factory : factoryListFactories)
        if ("all".equals(couponProjection.scopeType())
            || eligibleFactories.contains(factory.factoryId()))
          eligibleRent = Math.addExact(eligibleRent, factory.amounts().rentOriginalFen());
      if (couponUsageMapper.lockCoupon(
              couponProjection.id(), couponProjection.version(), at, expiry)
          != 1) throw conflict(TradingReason.COUPON_UNAVAILABLE.name());
      String usageId = Ids.next();
      couponUsageMapper.insertUsage(
          new CouponUsageDO(
              usageId,
              usageId,
              couponProjection.id(),
              tradingCommand.actor(),
              batchId,
              paymentId,
              eligibleRent,
              checkoutQuoteData.amounts().couponDiscountFen(),
              couponProjection.costBearerSnapshot(),
              TradingState.locked.name(),
              expiry,
              null,
              null,
              null,
              0,
              at,
              at));
      for (CheckoutQuoteData.Factory factory : factoryListFactories) {
        List<String> snapshotItemIds = new ArrayList<>();
        boolean eligible =
            "all".equals(couponProjection.scopeType())
                || eligibleFactories.contains(factory.factoryId());
        for (CheckoutQuoteData.Item item : factory.items()) {
          snapshotItemIds.add(item.snapshotItemId());
          couponUsageMapper.insertAllocation(
              new CouponWrites.Allocation(
                  Ids.next(),
                  usageId,
                  batchId,
                  item.snapshotItemId(),
                  factory.factoryId(),
                  item.snapshot().skuId(),
                  eligible ? item.rentOriginalFen() : 0,
                  item.couponDiscountFen(),
                  at));
        }
        if (factory.amounts().couponDiscountFen() > 0) {
          CouponWrites.AdjustmentSnapshot adjustmentSnapshot =
              new CouponWrites.AdjustmentSnapshot(
                  1,
                  "user_coupon",
                  couponProjection.id(),
                  couponProjection.templateVersion(),
                  factory.factoryId(),
                  List.copyOf(snapshotItemIds),
                  couponProjection.scopeType(),
                  couponProjection.thresholdRentAmountFenSnapshot(),
                  factory.amounts().couponDiscountFen());
          couponUsageMapper.insertPriceAdjustment(
              new CouponWrites.Adjustment(
                  Ids.next(),
                  batchId,
                  factory.factoryId(),
                  couponProjection.id(),
                  factory.amounts().couponDiscountFen(),
                  objectMapper.writeValueAsString(adjustmentSnapshot),
                  at));
        }
      }
      couponUsageMapper.insertOperationLog(
          new CouponWrites.Log(
              Ids.next(),
              "lock:" + batchId,
              couponProjection.id(),
              usageId,
              TradingState.locked.name(),
              couponProjection.couponStatus(),
              TradingState.locked.name(),
              tradingCommand.actor(),
              batchId,
              "提交结算锁券",
              at));
    }
    if (checkoutBatchMapper.markChecklistProcessing(
            tradingCommand.actor(),
            rentalChecklistDO.id(),
            rentalChecklistDO.version(),
            batchId,
            at)
        != 1) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    return tradingOperations.record(
        tradingCommand, TradingResource.checkout_batch.name(), batchId, 0);
  }
}
