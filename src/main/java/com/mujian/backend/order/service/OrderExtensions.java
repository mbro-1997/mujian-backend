package com.mujian.backend.order.service;

import com.mujian.backend.common.enums.BusinessFailureReason;
import com.mujian.backend.common.enums.TradingReason;

import com.mujian.backend.common.enums.ExtensionReason;

import com.mujian.backend.delivery.service.DisputeGuard;
import com.mujian.backend.order.domain.RentalOrderDO;
import com.mujian.backend.payment.domain.PaymentOrderDO;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.support.*;
import com.mujian.backend.common.enums.TradingOperation;
import com.mujian.backend.delivery.service.OrderDisputeContext;
import com.mujian.backend.order.dto.TradingCommand;
import com.mujian.backend.order.mapper.*;
import com.mujian.backend.order.mapper.ExtensionRequestMapper.Row;
import com.mujian.backend.order.policy.TradingTime;
import com.mujian.backend.order.vo.TradingReceipt;
import com.mujian.backend.payment.domain.ChannelSuborderDO;
import com.mujian.backend.payment.dto.CashComponents;
import com.mujian.backend.payment.mapper.*;
import com.mujian.backend.payment.service.RefundObligations;
import com.mujian.backend.prop.domain.InventoryReservationDO;
import com.mujian.backend.prop.service.TradingReservations;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(isolation=Isolation.READ_COMMITTED)
public class OrderExtensions {
  public record Days(int expectedVersion,int extraDays,String remark) {}
  private final ExtensionRequestMapper extensionRequestMapper;
  private final OrderExtensionMapper orderExtensionMapper;
  private final OrderExtensionQuote pricing;
  private final RentalOrderAccess rentalOrderAccess;
  private final RentalOrderMapper rentalOrderMapper;
  private final TradingRoots tradingRoots;
  private final TradingOperations tradingOperations;
  private final TradingReservations tradingReservations;
  private final PaymentOrderMapper paymentOrderMapper;
  private final RefundObligations obligations;
  private final RefundMapper refundMapper;
  private final Clock clock;
  private final com.mujian.backend.factory.service.FactoryAccess factoryAccess;
  private final com.mujian.backend.delivery.service.DisputeGuard disputes;

  public OrderExtensions(ExtensionRequestMapper extensionRequestMapper,OrderExtensionMapper orderExtensionMapper,OrderExtensionQuote pricing,RentalOrderAccess rentalOrderAccess,
      RentalOrderMapper rentalOrderMapper,TradingRoots tradingRoots,TradingOperations tradingOperations,TradingReservations tradingReservations,PaymentOrderMapper paymentOrderMapper,
      RefundObligations obligations,RefundMapper refundMapper,Clock clock,com.mujian.backend.factory.service.FactoryAccess factoryAccess,com.mujian.backend.delivery.service.DisputeGuard disputes) {
    this.factoryAccess=factoryAccess;this.disputes=disputes;this.extensionRequestMapper=extensionRequestMapper;this.orderExtensionMapper=orderExtensionMapper;this.pricing=pricing;this.rentalOrderAccess=rentalOrderAccess;this.rentalOrderMapper=rentalOrderMapper;this.tradingRoots=tradingRoots;
    this.tradingOperations=tradingOperations;this.tradingReservations=tradingReservations;this.paymentOrderMapper=paymentOrderMapper;this.obligations=obligations;this.refundMapper=refundMapper;this.clock=clock;
  }

  /** 校验来源与版本并提交申请。 */
  public TradingReceipt submit(TradingCommand tradingCommand,String orderId,Days days) {
    RentalOrderDO rentalOrderDO=rentalOrderAccess.owned(tradingCommand.actor(),orderId,false,true);
    tradingRoots.lock(rentalOrderDO.checkoutBatchId());
    TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);
    if(tradingReceipt!=null)return tradingReceipt;
    if(extensionRequestMapper.selectActiveExtensionIdByOrder(orderId)!=null)throw conflict(BusinessFailureReason.EXTENSION_PENDING.name());
    List<InventoryReservationDO> originals=originals(orderId);
    OrderExtensionQuote.Quote quote=pricing.quote(tradingCommand.actor(),orderId,days.extraDays());
    if(quote.orderVersion()!=days.expectedVersion())throw conflict(D2Codes.CODE_VERSION_CONFLICT);
    LocalDateTime at=now(clock);
    LocalDateTime cutoff=OrderDisputeContext.latestPaidAt(quote.originalDueDate());
    LocalDateTime expiry=TradingTime.paymentExpiry(at);
    if(expiry.isAfter(cutoff))expiry=cutoff;
    if(!TradingTime.canCreatePayment(expiry,at))throw conflict(TradingReason.PAYMENT_WINDOW_EXPIRED.name());
    String ext=Ids.next(),payment=Ids.next();
    if(originals.isEmpty() || extensionRequestMapper.insertOrderExtensionRequest(ext,quote,days.remark(),at)!=1)throw conflict(ExtensionReason.EXTENSION_WINDOW_CLOSED.name());
    if(extensionRequestMapper.insertPaymentOrder(ext,payment,"extension:"+tradingCommand.requestId(),expiry,at)!=1 || extensionRequestMapper.insertPaymentChannelSuborder(ext,payment,Ids.next(),at)!=1)
      throw new IllegalStateException("延期缺少原付款来源");
    extensionRequestMapper.bindExtensionPayment(ext,payment);
    for(InventoryReservationDO inventoryReservationDO:originals)if(extensionRequestMapper.insertInventoryReservationHold(Ids.next(),ext,inventoryReservationDO.getId(),expiry,at)!=1)throw conflict(ExtensionReason.EXTENSION_DAYS_EXCEEDED.name());
    return tradingOperations.record(tradingCommand,"order_extension_request",ext,0);
  }

  /** 读取原单关联的履约与付款记录。 */
  private List<InventoryReservationDO> originals(String orderId) {
    RentalOrderDO order=rentalOrderMapper.selectOrderById(orderId);
    return tradingReservations.lockExisting(order.checkoutBatchId()).stream().filter(r->orderId.equals(r.getRentalOrderId())
        && !"extension_request".equals(r.getSourceType()) && "renting".equals(r.getReservationStatus())).toList();
  }

  /** 核验请求并记录可回放的执行结果。 */
  public TradingReceipt command(TradingCommand tradingCommand,String id,int version,String reason) {
    ExtensionRequestMapper.Row extensionRequestRow=extensionRequestMapper.selectExtensionById(id);
    if(extensionRequestRow==null)throw missing();
    boolean factory=tradingCommand.operation()==TradingOperation.approveExtension || tradingCommand.operation()==TradingOperation.rejectExtension;
    RentalOrderDO order=rentalOrderAccess.owned(tradingCommand.actor(),extensionRequestRow.orderId(),factory,true);
    if(factory && extensionRequestRow.deadlineAt()==null)throw missing();
    tradingRoots.lock(order.checkoutBatchId());
    TradingReceipt tradingReceipt=tradingOperations.replay(tradingCommand);
    if(tradingReceipt!=null)return tradingReceipt;
    ExtensionRequestMapper.Row extensionRequestRowRow=extensionRequestMapper.selectExtensionById(id);
    LocalDateTime at=now(clock);
    if(tradingCommand.operation()==TradingOperation.payExtension) {
      factoryAccess.context(tradingCommand.actor(),true);
      if(!"pending_payment".equals(extensionRequestRowRow.status()))throw conflict(BusinessFailureReason.EXTENSION_ALREADY_HANDLED.name());
      if(extensionRequestRowRow.version()!=version)throw conflict(D2Codes.CODE_VERSION_CONFLICT);
      PaymentOrderDO payment=paymentOrderMapper.selectPaymentById(extensionRequestRowRow.paymentId(),true);
      if(!at.isBefore(payment.expireAt()) || payment.closeRequestedAt()!=null)throw conflict(TradingReason.PAYMENT_WINDOW_EXPIRED.name());
      if(!eligible(extensionRequestRowRow,at))throw conflict(ExtensionReason.EXTENSION_WINDOW_CLOSED.name());
    } else {
      if(!waiting(extensionRequestRowRow) || !at.isBefore(extensionRequestRowRow.deadlineAt()))throw conflict(BusinessFailureReason.EXTENSION_ALREADY_HANDLED.name());
      if(extensionRequestRowRow.version()!=version)throw conflict(D2Codes.CODE_VERSION_CONFLICT);
      handle(extensionRequestRowRow,tradingCommand.actor(),tradingCommand.operation()==TradingOperation.approveExtension,tradingCommand.operation()==TradingOperation.rejectExtension,reason,at);
    }
    return tradingOperations.recordReason(tradingCommand,"order_extension_request",id,extensionRequestMapper.selectExtensionById(id).version(),reason);
  }

  /** 判断申请是否仍在等待处理。 */
  private boolean waiting(Row row){return Set.of("pending_factory","factory_warning").contains(row.status());}

  /** 校验订单当前是否符合延期条件。 */
  private boolean eligible(Row row,LocalDateTime now) {
    RentalOrderDO order=rentalOrderMapper.selectOrderById(row.orderId());
    OrderExtensionMapper.QuoteBasis basis=orderExtensionMapper.selectExtensionQuoteBasis(row.orderId(),now.toLocalDate());
    DisputeGuard.Facts facts=disputes.inspect(order.id());
    return facts.ready() && !facts.paused() && !facts.claimActive() && !facts.terminationActive() && order.currentDueDate().equals(row.originalDueDate()) && basis.renting()
        && ChronoUnit.DAYS.between(order.rentalStartDate(),row.requestedDueDate())<120
        && (basis.snLimitDays()==null || row.extraDays()<=basis.snLimitDays());
  }

  /** 收款落账与业务处理使用原订单和渠道子单锁。按渠道成功时刻判截止。 */
  public void observePayment(String paymentId,ChannelSuborderDO channelSuborderDO,LocalDateTime at) {
    String id=extensionRequestMapper.selectOrderExtensionRequestByPaymentId(paymentId);
    if(id==null)throw new IllegalStateException("独立支付缺少延期来源");
    Row extensionRequestRow=extensionRequestMapper.selectExtensionById(id);
    if("closed".equals(channelSuborderDO.paymentStatus()) && "pending_payment".equals(extensionRequestRow.status())) {
      extensionRequestMapper.transitionHolds(id,"held","released",at);
      extensionRequestMapper.updateExtensionStatusAndRefund(id,"payment_failed","原渠道已确认关单",null,at);return;
    }
    if(!"success".equals(channelSuborderDO.paymentStatus()))return;
    if(extensionRequestMapper.insertPaymentAllocation(Ids.next(),id,at)==0)return;
    extensionRequestMapper.recordCash(extensionRequestRow.orderId(),channelSuborderDO.paidAmountFen(),at);
    List<InventoryReservationDO> originals=originals(extensionRequestRow.orderId());
    boolean acceptable="pending_payment".equals(extensionRequestRow.status()) && !channelSuborderDO.paidAt().isAfter(extensionRequestRow.latestPaidAt())
        && !channelSuborderDO.paidAt().isAfter(paymentOrderMapper.selectPaymentById(paymentId,false).expireAt()) && !originals.isEmpty() && eligible(extensionRequestRow,at);
    if(acceptable && extensionRequestMapper.transitionHolds(id,"held","paid_locked",at)==originals.size()) {
      if(extensionRequestMapper.updateOrderExtensionRequestPaid(id,channelSuborderDO.paidAt(),at)!=1)throw conflict(BusinessFailureReason.EXTENSION_ALREADY_HANDLED.name());
    } else {
      extensionRequestMapper.transitionHolds(id,"held","released",at);
      cancel(extensionRequestRow,channelSuborderDO,false,"实付超窗或订单已不能延期",SystemIdentity.SYSTEM_USER_ID,at);
    }
  }

  /** 为重复执行确定已登记的截止时间。 */
  public void endForRecovery(String orderId,LocalDateTime at) {
    String id=extensionRequestMapper.selectActiveExtensionIdByOrder(orderId);
    if(id==null)return;
    Row extensionRequestRow=extensionRequestMapper.selectExtensionById(id);
    if(waiting(extensionRequestRow))handle(extensionRequestRow,SystemIdentity.SYSTEM_USER_ID,false,false,"订单进入实物回收",at);
    else if("pending_payment".equals(extensionRequestRow.status()))paymentOrderMapper.requestClose(extensionRequestRow.paymentId(),"user_cancel",at);
  }

  /** 开始处理已提交的申请。 */
  private void handle(Row row,String actor,boolean approve,boolean reject,String reason,LocalDateTime at) {
    List<InventoryReservationDO> originals=originals(row.orderId());
    if(approve && eligible(row,at) && !originals.isEmpty()) {
      if(extensionRequestMapper.mergeDates(row,at)!=originals.size() || extensionRequestMapper.effectiveDate(row,at)!=1
          || extensionRequestMapper.transitionHolds(row.id(),"paid_locked","released",at)!=originals.size())throw conflict(BusinessFailureReason.EXTENSION_ALREADY_HANDLED.name());
      extensionRequestMapper.updateExtensionStatusAndRefund(row.id(),"approved",null,null,at);
    } else {
      ChannelSuborderDO channelSuborderDO=paymentOrderMapper.selectSubordersByPayment(row.paymentId(),true).get(0);
      cancel(row,channelSuborderDO,reject,reason==null?(approve?"订单已不能延期":"买家取消延期"):reason,actor,at);
    }
  }

  /** 按申请现状取消未完成的延期操作。 */
  private void cancel(Row row,ChannelSuborderDO sub,boolean rejected,String reason,String actor,LocalDateTime at) {
    CashComponents cash=new CashComponents(sub.paidAmountFen(),0,0);
    String refund=obligations.reserve(actor,"rental_order",row.orderId(),"extension_request",row.id(),
        rejected?"extension_rejected":"extension_cancelled",reason,sub,cash,cash);
    extensionRequestMapper.transitionHolds(row.id(),"paid_locked","released",at);
    extensionRequestMapper.updateExtensionStatusAndRefund(row.id(),rejected?"rejected_refunding":"cancelled",reason,refund,at);
    refundMapper.refreshOrderRefunds(row.orderId(),at);
  }

  /** 从已提交的原请求继续处理。 */
  public void recover(String id) {
    ExtensionRequestMapper.Row extensionRequestRow=extensionRequestMapper.selectExtensionById(id);
    if(extensionRequestRow==null)return;
    tradingRoots.lock(rentalOrderMapper.selectOrderById(extensionRequestRow.orderId()).checkoutBatchId());
    ExtensionRequestMapper.Row extensionRequestRowRow=extensionRequestMapper.selectExtensionById(id);LocalDateTime at=now(clock);
    if(!waiting(extensionRequestRowRow))return;
    if(!at.isBefore(extensionRequestRowRow.deadlineAt()))handle(extensionRequestRowRow,SystemIdentity.SYSTEM_USER_ID,true,false,null,at);
    else if("pending_factory".equals(extensionRequestRowRow.status()) && !at.isBefore(extensionRequestRowRow.warningAt()))extensionRequestMapper.updateExtensionStatusAndRefund(id,"factory_warning",null,null,at);
  }
}
