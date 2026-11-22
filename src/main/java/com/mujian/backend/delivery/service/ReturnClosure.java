package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.casework.domain.AdminCaseDO;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.delivery.domain.OrderReturnCheckDO;
import com.mujian.backend.delivery.mapper.*;
import com.mujian.backend.delivery.policy.FulfillmentProtection;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.mapper.RentalOrderMapper;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.payment.service.NormalOrderFunds;
import com.mujian.backend.prop.service.*;
import java.time.*;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 唯一订单处置锁内提交关闭、退押义务、库存与事件，渠道到账另行恢复。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ReturnClosure {
  private final OverdueFees overdueFees;
  private final FulfillmentAccess fulfillmentAccess;
  private final TradingRoots tradingRoots;
  private final TradingOperations tradingOperations;
  private final FulfillmentProtection fulfillmentProtection;
  private final ReturnCheckRoot returnCheckRoot;
  private final ReturnCheckMapper returnCheckMapper;
  private final FulfillmentMapper fulfillmentMapper;
  private final RentalOrderMapper rentalOrderMapper;
  private final ReturnCasework returnCasework;
  private final TradingReservations tradingReservations;
  private final FulfillmentInventory fulfillmentInventory;
  private final NormalOrderFunds normalOrderFunds;
  private final FulfillmentEvents fulfillmentEvents;
  private final IssuePlanMapper issuePlanMapper;
  private final IssuePlans issuePlans;
  private final Clock clock;

  /** 共享原锁序与唯一资金执行器，所有本地步骤在一个事务内。 */
  public ReturnClosure(
      FulfillmentAccess fulfillmentAccess,
      TradingRoots tradingRoots,
      TradingOperations tradingOperations,
      FulfillmentProtection fulfillmentProtection,
      ReturnCheckRoot returnCheckRoot,
      ReturnCheckMapper returnCheckMapper,
      FulfillmentMapper fulfillmentMapper,
      RentalOrderMapper rentalOrderMapper,
      ReturnCasework returnCasework,
      TradingReservations tradingReservations,
      FulfillmentInventory fulfillmentInventory,
      NormalOrderFunds normalOrderFunds,
      FulfillmentEvents fulfillmentEvents,
      Clock clock,IssuePlanMapper issuePlanMapper,IssuePlans issuePlans,OverdueFees overdueFees) {
    this.overdueFees=overdueFees;
    this.issuePlanMapper=issuePlanMapper;
    this.issuePlans=issuePlans;
    this.fulfillmentAccess = fulfillmentAccess;
    this.tradingRoots = tradingRoots;
    this.tradingOperations = tradingOperations;
    this.fulfillmentProtection = fulfillmentProtection;
    this.returnCheckRoot = returnCheckRoot;
    this.returnCheckMapper = returnCheckMapper;
    this.fulfillmentMapper = fulfillmentMapper;
    this.rentalOrderMapper = rentalOrderMapper;
    this.returnCasework = returnCasework;
    this.tradingReservations = tradingReservations;
    this.fulfillmentInventory = fulfillmentInventory;
    this.normalOrderFunds = normalOrderFunds;
    this.fulfillmentEvents = fulfillmentEvents;
    this.clock = clock;
  }

  /** 只按已确认方案收本单实物，复用实际核对与库存流水。 */
  public FulfillmentViews.CloseResult confirmTermination(TradingCommand tradingCommand,String orderId,int version) {
    RentalOrderDO order=fulfillmentAccess.order(tradingCommand.actor(),orderId,true,null);
    tradingRoots.lock(order.checkoutBatchId());
    TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);
    if (tradingReceipt!=null) return result(tradingReceipt,tradingOperations.replayClose(tradingCommand));
    IssuePlanMapper.Action plan=issuePlanMapper.selectActive(orderId,null,true);
    if (!fulfillmentProtection.requireReturn(orderId) || plan==null || !"executing".equals(plan.status()) || !issuePlanMapper.selectIssuePlanDispatched(orderId))
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    OrderReturnCheckDO check=returnCheckMapper.lockCheck(orderId);
    if (check==null) throw conflict(FulfillmentReason.CHECK_INCOMPLETE.name());
    ReturnCheckRoot.requireOpen(check);
    if (check.version()!=version) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    List<FulfillmentViews.CheckItem> items=fulfillmentMapper.selectCheckItemsByOrder(orderId,true);
    if (items.isEmpty() || items.stream().anyMatch(item->!item.complete())) throw conflict(FulfillmentReason.CHECK_INCOMPLETE.name());
    LocalDateTime at=now(clock);
    String reason="工厂已按终止方案逐项核对并入库";
    if (returnCheckMapper.closeReturnCheck(check.id(),version,"no_issue","factory_no_issue",reason,tradingCommand.actor(),at)!=1
        || !returnCheckMapper.returnLegComplete(orderId) && returnCheckMapper.finishReturnLeg(orderId,at)!=1)
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    fulfillmentInventory.transfer(order,items,tradingCommand.actor(),FulfillmentRole.factory.name(),false);
    fulfillmentEvents.append("return:"+check.id()+":closed",orderId,fulfillmentMapper.selectMetadataByOrder(orderId).returnLegId(),
        "order_return_check",check.id(),FulfillmentEventType.return_check_closed,at,tradingCommand.actor(),FulfillmentRole.factory,reason);
    issuePlans.reconcileLocked(plan,tradingCommand.actor(),at);
    TradingOperations.CloseSnapshot snapshot=new TradingOperations.CloseSnapshot(check.id(),plan.id(),issuePlanMapper.selectPlannedRefundsByAction(plan.id()).stream().map(IssuePlanMapper.Refund::refundId).toList());
    return result(tradingOperations.recordClose(tradingCommand,"order_return_check",check.id(),version+1,snapshot,reason),snapshot);
  }

  /** 工厂无异常必须齐全；管理员规则关闭保留理由和四步审计。 */
  public FulfillmentViews.CloseResult confirm(
      TradingCommand tradingCommand, String orderId, int version, String reason) {
    boolean admin = tradingCommand.operation() == TradingOperation.adminCloseReturn;
    if (!admin && tradingCommand.operation() != TradingOperation.confirmReturn) throw ApiFailure.invalid();
    RentalOrderDO rentalOrderDO =
        fulfillmentAccess.order(tradingCommand.actor(), orderId, !admin, tradingCommand.operation().permission());
    TradingRoots.Locked locked = tradingRoots.lock(rentalOrderDO.checkoutBatchId());
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return result(tradingReceipt, tradingOperations.replayClose(tradingCommand));
    fulfillmentProtection.requireNormal(orderId);
    OrderReturnCheckDO check = returnCheckMapper.lockCheck(orderId);
    if (check == null) throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    ReturnCheckRoot.requireOpen(check);
    if (check.version() != version) throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    TradingOperations.CloseSnapshot close =
        execute(
            locked,
            rentalOrderMapper.selectOrderById(orderId),
            check,
            tradingCommand.actor(),
            admin ? FulfillmentRole.admin : FulfillmentRole.factory,
            admin ? reason : "工厂已逐项核对，无异常入库",
            tradingCommand.requestId());
    return result(
        tradingOperations.recordClose(
            tradingCommand, TradingResource.order_return_check.name(), check.id(), version + 1, close, reason),
        close);
  }

  /** 预警、72小时规则关闭和预计启动均以库中业务时钟为准。 */
  public void recover(String orderId) {
    RentalOrderDO rentalOrderDO = rentalOrderMapper.selectOrderById(orderId);
    if (rentalOrderDO == null) return;
    TradingRoots.Locked locked = tradingRoots.lock(rentalOrderDO.checkoutBatchId());
    if (!fulfillmentProtection.writable(fulfillmentProtection.facts(orderId))) return;
    RentalOrderDO order = rentalOrderMapper.selectOrderById(orderId);
    OrderReturnCheckDO check = returnCheckMapper.lockCheck(orderId);
    String actor = SystemIdentity.SYSTEM_USER_ID;
    LocalDateTime at = now(clock);
    if (check == null) check = returnCheckRoot.open(order, actor, FulfillmentRole.system, 0, false);
    if (check.closedAt() != null
        || check.claimCreated() == 1
        || ReturnCheckStatus.claim_submitted.name().equals(check.checkStatus())) return;
    if (!at.isBefore(check.warningAt()) && returnCheckMapper.markReturnCheckWarned(check.id(), at) == 1) {
      returnCasework.find(order, check, true, at);
      fulfillmentEvents.append(
          "return:" + check.id() + ":warning",
          orderId,
          null,
          TradingResource.order_return_check.name(),
          check.id(),
          FulfillmentEventType.return_check_warning,
          at,
          actor,
          FulfillmentRole.system,
          "工厂尚未完成归还核验");
      check = returnCheckMapper.lockCheck(orderId);
    }
    if (check.autoCloseAt()!=null && !at.isBefore(check.autoCloseAt()))
      execute(
          locked,
          order,
          check,
          actor,
          FulfillmentRole.system,
          "归还核验期已满，未发起索赔",
          "return:" + check.id() + ":auto-close");
  }

  /** 退款事实先于库存释放，预先按全局顺序加锁，释放仅限原订单。 */
  private TradingOperations.CloseSnapshot execute(
      TradingRoots.Locked locked,
      RentalOrderDO order,
      OrderReturnCheckDO check,
      String actor,
      FulfillmentRole role,
      String reason,
      String key) {
    fulfillmentProtection.requireNormal(order.id());
    overdueFees.requireResolved(order.id());
    List<FulfillmentViews.CheckItem> items =
        fulfillmentMapper.selectCheckItemsByOrder(order.id(), true);
    if (role == FulfillmentRole.factory
        && (items.isEmpty() || items.stream().anyMatch(item -> !item.complete())))
      throw conflict(FulfillmentReason.CHECK_INCOMPLETE.name());
    LocalDateTime at = now(clock);

    ReturnCheckStatus status =
        role == FulfillmentRole.factory
            ? ReturnCheckStatus.no_issue
            : role == FulfillmentRole.admin
                ? ReturnCheckStatus.admin_closed
                : ReturnCheckStatus.auto_closed;
    String code =
        switch (role) {
          case factory -> "factory_no_issue";
          case admin -> "admin_force_close";
          case system -> "timeout_no_claim";
          default -> throw ApiFailure.invalid();
        };
    if (returnCheckMapper.closeReturnCheck(check.id(), check.version(), status.name(), code, reason, actor, at)
        != 1) throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    return finishClosed(locked, order, check, items, actor, role, reason, key, at);
  }

  /** 共享即时关闭与持久恢复的资金、库存、事件和案件步骤。校验时间保留实际关闭时刻。 */
  private TradingOperations.CloseSnapshot finishClosed(
      TradingRoots.Locked locked,
      RentalOrderDO order,
      OrderReturnCheckDO check,
      List<FulfillmentViews.CheckItem> items,
      String actor,
      FulfillmentRole role,
      String reason,
      String key,
      LocalDateTime at) {
    AdminCaseDO adminCaseDO = returnCasework.find(order, check, role != FulfillmentRole.factory, at);
    if (!returnCheckMapper.returnLegComplete(order.id())
        && returnCheckMapper.finishReturnLeg(order.id(), at) != 1)
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    List<String> recordedEvents = returnCasework.recordedEvents(adminCaseDO, check.id());
    String leg = fulfillmentMapper.selectMetadataByOrder(order.id()).returnLegId();
    String closedEvent =
        fulfillmentEvents.restore(
            recordedEvents.isEmpty() ? null : recordedEvents.get(0),
            "return:" + check.id() + ":closed",
            order.id(),
            leg,
            TradingResource.order_return_check.name(),
            check.id(),
            FulfillmentEventType.return_check_closed,
            at,
            actor,
            role,
            reason);
    tradingReservations.lockExisting(order.checkoutBatchId());
    List<String> refundIds = normalOrderFunds.deposit(locked, order, check.id(), actor);
    fulfillmentInventory.transfer(order, items, actor, role.name(), false);
    String completedEvent =
        fulfillmentEvents.restore(
            recordedEvents.isEmpty() ? null : recordedEvents.get(1),
            "return:" + check.id() + ":complete",
            order.id(),
            leg,
            TradingResource.order_return_check.name(),
            check.id(),
            FulfillmentEventType.return_close_completed,
            at,
            actor,
            role,
            "核验关闭，押金责任已处理，库存已释放");
    List<ReturnCasework.Step> steps =
        List.of(
            new ReturnCasework.Step(
                "05", "close_return_check", "order_return_check", check.id(), List.of(check.id())),
            new ReturnCasework.Step("04", "release_deposit", "rental_order", order.id(), refundIds),
            new ReturnCasework.Step(
                "02",
                "release_inventory",
                "rental_order",
                order.id(),
                returnCheckMapper.releasedIds(order.id())),
            new ReturnCasework.Step(
                "05",
                "record_return_event",
                "order_fulfillment_event",
                completedEvent,
                List.of(closedEvent, completedEvent)));
    String action =
        returnCasework.finish(
            adminCaseDO, new ReturnCasework.Closure(check.id(), reason, steps), actor, key, role, at);
    normalOrderFunds.reconcile(order.id());
    return new TradingOperations.CloseSnapshot(check.id(), action, refundIds);
  }

  /** 只返回已经提交的本地事实；到账进度在原退款详情另外读取。 */
  private FulfillmentViews.CloseResult result(
      TradingReceipt receipt, TradingOperations.CloseSnapshot close) {
    return new FulfillmentViews.CloseResult(
        receipt,
        close.checkId(),
        close.actionId(),
        true,
        !close.refundIds().isEmpty(),
        true,
        true,
        close.refundIds());
  }

  /** 门禁恢复/服务重启后补评估已关闭订单的全部责任。 */
  public void reconcile(String orderId) {
    RentalOrderDO order = rentalOrderMapper.selectOrderById(orderId);
    if (order == null) return;
    TradingRoots.Locked locked = tradingRoots.lock(order.checkoutBatchId());
    if (!fulfillmentProtection.writable(fulfillmentProtection.facts(orderId))) return;
    OrderReturnCheckDO check = returnCheckMapper.lockCheck(orderId);
    if (check == null || check.closedAt() == null) return;
    if(check.claimCreated()!=0){normalOrderFunds.reconcile(orderId);return;}
    if (!normalOrderFunds.requiresClosureRepair(orderId)) {
      normalOrderFunds.reconcile(orderId);
      return;
    }
    FulfillmentRole role =
        switch (ReturnCheckStatus.valueOf(check.checkStatus())) {
          case no_issue -> FulfillmentRole.factory;
          case admin_closed -> FulfillmentRole.admin;
          case auto_closed -> FulfillmentRole.system;
          default -> throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
        };
    finishClosed(
        locked,
        rentalOrderMapper.selectOrderById(orderId),
        check,
        fulfillmentMapper.selectCheckItemsByOrder(orderId, true),
        check.closedByUserId(),
        role,
        check.closeReason(),
        "return:" + check.id() + ":restore",
        check.closedAt());
  }
}
