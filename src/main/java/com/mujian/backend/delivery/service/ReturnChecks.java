package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.*;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.*;
import com.mujian.backend.delivery.domain.*;
import com.mujian.backend.delivery.dto.FulfillmentDtos;
import com.mujian.backend.delivery.mapper.*;
import com.mujian.backend.delivery.policy.FulfillmentProtection;
import com.mujian.backend.delivery.vo.FulfillmentViews;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.service.*;
import com.mujian.backend.order.vo.TradingReceipt;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** 工厂归还核对与管理员实际到厂入口，均在原交易根及核验处置锁内执行。 */
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ReturnChecks {
  private final FulfillmentAccess fulfillmentAccess;
  private final TradingRoots tradingRoots;
  private final TradingOperations tradingOperations;
  private final FulfillmentProtection fulfillmentProtection;
  private final ReturnCheckRoot returnCheckRoot;
  private final ReturnCheckMapper returnCheckMapper;
  private final FulfillmentMapper fulfillmentMapper;
  private final FulfillmentQr fulfillmentQr;
  private final Clock clock;

  /** 出入库共用订单原分配、扫码和幂等结果。 */
  public ReturnChecks(
      FulfillmentAccess fulfillmentAccess,
      TradingRoots tradingRoots,
      TradingOperations tradingOperations,
      FulfillmentProtection fulfillmentProtection,
      ReturnCheckRoot returnCheckRoot,
      ReturnCheckMapper returnCheckMapper,
      FulfillmentMapper fulfillmentMapper,
      FulfillmentQr fulfillmentQr,
      Clock clock) {
    this.fulfillmentAccess = fulfillmentAccess;
    this.tradingRoots = tradingRoots;
    this.tradingOperations = tradingOperations;
    this.fulfillmentProtection = fulfillmentProtection;
    this.returnCheckRoot = returnCheckRoot;
    this.returnCheckMapper = returnCheckMapper;
    this.fulfillmentMapper = fulfillmentMapper;
    this.fulfillmentQr = fulfillmentQr;
    this.clock = clock;
  }

  /** 锁前鉴权、锁后回执，任何失败由事务撤回首次创建。 */
  private RentalOrderDO lock(TradingCommand tradingCommand, String orderId) {
    RentalOrderDO order =
        fulfillmentAccess.order(tradingCommand.actor(), orderId, true, tradingCommand.operation().permission());
    tradingRoots.lock(order.checkoutBatchId());
    return order;
  }

  /** 读本单的核对明细，跨单ID不泄露信息。 */
  private FulfillmentViews.CheckItem item(String orderId, String itemId) {
    return fulfillmentMapper.selectCheckItemsByOrder(orderId, true).stream()
        .filter(row -> row.itemId().equals(itemId))
        .findFirst()
        .orElseThrow(() -> missing());
  }

  /** B数量为本次总量，首次正数表示实际到厂。 */
  public TradingReceipt quantity(
      TradingCommand tradingCommand, String orderId, FulfillmentDtos.QuantityInput quantityInput) {
    if (tradingCommand.operation() != TradingOperation.setReturnQuantity) throw ApiFailure.invalid();
    RentalOrderDO order = lock(tradingCommand, orderId);
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    fulfillmentProtection.requireReturn(orderId);
    FulfillmentViews.CheckItem item = item(orderId, quantityInput.itemId());
    if (!PropAssetType.B_CONSUMABLE.name().equals(item.assetType())
        || quantityInput.quantity() < 0
        || quantityInput.quantity() > item.expectedQuantity()) throw ApiFailure.invalid();
    if (quantityInput.quantity() == 0 && fulfillmentMapper.selectOrderReturnCheckDOByOrder(orderId) == null)
      throw conflict(FulfillmentReason.FULFILLMENT_STATE_CHANGED.name());
    OrderReturnCheckDO check =
        returnCheckRoot.open(
            order,
            tradingCommand.actor(),
            FulfillmentRole.factory,
            quantityInput.expectedVersion(),
            quantityInput.quantity() > 0);
    set(check, item.itemId(), quantityInput.quantity(), tradingCommand.actor());
    return tradingOperations.record(
        tradingCommand, TradingResource.order_return_check.name(), check.id(), check.version() + 1);
  }

  /** 现行替换码可核对同一实际出库SN，重复命令按原进度返回。 */
  public FulfillmentViews.ScanResult scan(
      TradingCommand tradingCommand, String orderId, FulfillmentDtos.ScanInput scanInput) {
    if (tradingCommand.operation() != TradingOperation.scanReturn) throw ApiFailure.invalid();
    RentalOrderDO order = lock(tradingCommand, orderId);
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return result(tradingReceipt, tradingOperations.replayScan(tradingCommand));
    fulfillmentProtection.requireReturn(orderId);
    OrderReturnCheckDO check =
        returnCheckRoot.open(
            order, tradingCommand.actor(), FulfillmentRole.factory, scanInput.expectedVersion(), true);
    FulfillmentQr.Assigned assigned = fulfillmentQr.resolve(order, scanInput.code(), true);
    boolean duplicate = returnCheckMapper.hasScan(check.id(), assigned.snId());
    FulfillmentViews.CheckItem item = item(orderId, assigned.itemId());
    int count = item.checkedQuantity(), version = check.version();
    if (!duplicate) {
      returnCheckMapper.insertScan(
          new OutboundScanDO(
              Ids.next(),
              tradingCommand.requestId(),
              check.id(),
              orderId,
              item.itemId(),
              assigned.qrId(),
              assigned.snId(),
              tradingCommand.actor(),
              now(clock)));
      set(check, item.itemId(), ++count, tradingCommand.actor());
      version++;
    }
    TradingOperations.ScanSnapshot snapshot =
        new TradingOperations.ScanSnapshot(
            duplicate, item.itemId(), count, item.expectedQuantity(), version);
    return result(
        tradingOperations.record(
            tradingCommand, TradingResource.order_return_check.name(), check.id(), version, snapshot),
        snapshot);
  }

  /** 同一根版本下同步修改明细，失败不保存部分进度。 */
  private void set(OrderReturnCheckDO check, String item, int quantity, String actor) {
    if (returnCheckMapper.setQuantity(check.id(), item, quantity, actor, now(clock)) != 1
        || returnCheckMapper.advanceReturnCheckVersion(check.id(), check.version(), actor, now(clock)) != 1)
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
  }

  /** 从冻结回执读取原扫码结果。 */
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

  /** 管理员记录实际到厂，说明保存在独立操作审计。 */
  public TradingReceipt arrival(
      TradingCommand tradingCommand, String orderId, FulfillmentDtos.AdminReason adminReason) {
    if (tradingCommand.operation() != TradingOperation.adminConfirmArrival) throw ApiFailure.invalid();
    RentalOrderDO order = lock(tradingCommand, orderId);
    TradingReceipt tradingReceipt = tradingOperations.replay(tradingCommand);
    if (tradingReceipt != null) return tradingReceipt;
    fulfillmentProtection.requireNormal(orderId);
    OrderReturnCheckDO check =
        returnCheckRoot.open(
            order, tradingCommand.actor(), FulfillmentRole.admin, adminReason.expectedVersion(), true);
    if (returnCheckMapper.advanceReturnCheckVersion(check.id(), check.version(), tradingCommand.actor(), now(clock)) != 1)
      throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    return tradingOperations.recordReason(
        tradingCommand,
        TradingResource.order_return_check.name(),
        check.id(),
        check.version() + 1,
        adminReason.reason());
  }

  /** 工厂只读取本单的成功核验实物分页。 */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public FulfillmentViews.ScanPage scans(String actor, String orderId, PageQuery page) {
    fulfillmentAccess.order(actor, orderId, true, null);
    return new FulfillmentViews.ScanPage(
        returnCheckMapper.selectScans(orderId, page),
        page.page(),
        page.pageSize(),
        Long.toString(returnCheckMapper.countScans(orderId)));
  }
}
