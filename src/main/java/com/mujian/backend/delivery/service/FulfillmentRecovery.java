package com.mujian.backend.delivery.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.delivery.mapper.HandoverMapper;
import java.time.Clock;
import java.util.List;
import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 订单事实与截止存在库中，进程重启后从到期集合重新领取。 */
@Service
@ConditionalOnProperty(name = "mj.fulfillment.enabled", havingValue = "true")
public class FulfillmentRecovery {
  private static final Logger LOG = LoggerFactory.getLogger(FulfillmentRecovery.class);
  private final HandoverMapper handoverMapper;
  private final HandoverCommands handoverCommands;
  private final Clock clock;
  private final OverdueOrders overdueOrders;
  private final com.mujian.backend.delivery.mapper.OverdueMapper overdueMapper;
  private String overdueCursor = "", eventCursor = "", riskCursor = "";
  private final ReceiptTimerRecovery receiptTimerRecovery;
  private final com.mujian.backend.order.mapper.ExtensionRequestMapper extensionRequestMapper;
  private final com.mujian.backend.order.service.OrderExtensions extensions;
  private String extensionCursor="",claimCursor="",frozenClaimCursor="";
  private final com.mujian.backend.dispute.service.ClaimService claimService;
  private final com.mujian.backend.dispute.mapper.ClaimMapper claimMapper;
  private final com.mujian.backend.dispute.service.ClaimStock claimStock;
  private final com.mujian.backend.dispute.mapper.ClaimStockMapper claimStockMapper;
  private String stockCursor="",arbitrationCursor="";
  private final com.mujian.backend.dispute.service.ArbitrationRecovery arbitration;
  private final com.mujian.backend.dispute.mapper.ArbitrationMapper arbitrationMapper;
  private String frozenCursor = "";
  private String receiptCursor = "", returnCursor = "", completionCursor = "";
  private final com.mujian.backend.delivery.mapper.ReturnCheckMapper returnCheckMapper;
  private final com.mujian.backend.payment.mapper.NormalFundsMapper normalFundsMapper;
  private final ReturnClosure returnClosure;

  /** 游标只用于分批公平性，不承担完成标记。 */
  public FulfillmentRecovery(
      ReceiptTimerRecovery receiptTimerRecovery,
      HandoverMapper handoverMapper,
      HandoverCommands handoverCommands,
      Clock clock,
      com.mujian.backend.delivery.mapper.ReturnCheckMapper returnCheckMapper,
      com.mujian.backend.payment.mapper.NormalFundsMapper normalFundsMapper,
      ReturnClosure returnClosure,
      OverdueOrders overdueOrders,
      com.mujian.backend.delivery.mapper.OverdueMapper overdueMapper,com.mujian.backend.order.mapper.ExtensionRequestMapper extensionRequestMapper,com.mujian.backend.order.service.OrderExtensions extensions,com.mujian.backend.dispute.service.ClaimService claimService,com.mujian.backend.dispute.mapper.ClaimMapper claimMapper,com.mujian.backend.dispute.service.ClaimStock claimStock,com.mujian.backend.dispute.mapper.ClaimStockMapper claimStockMapper, com.mujian.backend.dispute.service.ArbitrationRecovery arbitration,com.mujian.backend.dispute.mapper.ArbitrationMapper arbitrationMapper) {
    this.arbitration=arbitration;this.arbitrationMapper=arbitrationMapper;
    this.claimStock=claimStock;this.claimStockMapper=claimStockMapper;
    this.claimService=claimService;this.claimMapper=claimMapper;
    this.extensionRequestMapper=extensionRequestMapper;this.extensions=extensions;
    this.receiptTimerRecovery = receiptTimerRecovery;
    this.overdueOrders = overdueOrders;
    this.overdueMapper = overdueMapper;
    this.returnCheckMapper = returnCheckMapper;
    this.normalFundsMapper = normalFundsMapper;
    this.returnClosure = returnClosure;
    this.handoverMapper = handoverMapper;
    this.handoverCommands = handoverCommands;
    this.clock = clock;
  }

  /** 每批最多一百条，各订单独立事务。按库内原截止时间恢复收货，不重新启动计时。 */
  @Scheduled(
      fixedDelayString = "${mj.fulfillment.recovery-delay-ms:60000}",
      initialDelayString = "${mj.fulfillment.recovery-delay-ms:60000}")
  public synchronized void recoverReceipts() {
    List<String> extensionIds=extensionRequestMapper.selectOrderExtensionRequestDueForProcessing(now(clock),extensionCursor);
    for(String id:extensionIds) {
      try { extensions.recover(id); }
      catch(RuntimeException failure) {LOG.warn("延期审批恢复待重试 id={}",id,failure);}
    }
    extensionCursor=extensionIds.size()==100?extensionIds.get(99):"";
    List<String> frozen = receiptTimerRecovery.candidates(frozenCursor);
    for (String orderId : frozen) {
      try {
        receiptTimerRecovery.restore(orderId);
      } catch (RuntimeException failure) {
        LOG.warn("恢复暂停核验未完成 orderId={}", orderId, failure);
      }
    }
    frozenCursor = frozen.size() == 100 ? frozen.get(frozen.size() - 1) : "";
    List<String> due = handoverMapper.selectDueReceipts(now(clock), receiptCursor);
    for (String orderId : due) {
      try {
        handoverCommands.receiveDue(orderId);
      } catch (RuntimeException failure) {
        LOG.warn("收货到期恢复未完成 orderId={}", orderId, failure);
      }
    }
    receiptCursor = due.size() == 100 ? due.get(due.size() - 1) : "";
  }

  /** 扫描到期核验和已关闭但仍缺步骤的订单。 */
  @Scheduled(
      fixedDelayString = "${mj.fulfillment.recovery-delay-ms:60000}",
      initialDelayString = "${mj.fulfillment.recovery-delay-ms:60000}")
  public synchronized void recoverReturns() {
    List<String> expiredClaims=claimMapper.selectClaimIdsPastResponseDeadline(now(clock),claimCursor);
    for(String id:expiredClaims){try{claimService.expire(id);}catch(RuntimeException failure){LOG.warn("索赔回应到期处理待重试 id={}",id,failure);}}
    claimCursor=expiredClaims.size()==100?expiredClaims.get(99):"";
    List<String> cases=arbitrationMapper.selectArbitrationCasePending(arbitrationCursor);
    for(String id:cases){
      try{for(int step=0;step<100 && arbitration.next(id);step++){ /* 原步骤独立提交 */ }}
      catch(RuntimeException failure){LOG.warn("仲裁原步骤待恢复 id={}",id,failure);arbitration.failed(id);}
    }
    arbitrationCursor=cases.size()==100?cases.get(99):"";
    List<String> stockClaims=claimStockMapper.selectClaimIdsForRecovery(stockCursor);
    for(String id:stockClaims){
      try{for(int i=0;i<100 && claimStock.applyNext(id);i++){ /* 每一笔单独提交 */ }}
      catch(RuntimeException failure){LOG.warn("索赔实物处理待重试 id={}",id,failure);}
    }
    stockCursor=stockClaims.size()==100?stockClaims.get(99):"";
    List<String> frozenClaims=claimMapper.selectFrozenReturnCheckOrderIds(frozenClaimCursor);
    for(String order:frozenClaims){try{claimService.restore(order);}catch(RuntimeException failure){LOG.warn("索赔解除核验恢复待重试 id={}",order,failure);}}
    frozenClaimCursor=frozenClaims.size()==100?frozenClaims.get(99):"";
    List<String> due = returnCheckMapper.selectDueOrders(now(clock), returnCursor);
    for (String orderId : due)
      try {
        returnClosure.recover(orderId);
      } catch (RuntimeException failure) {
        LOG.warn("归还到期恢复未完成 orderId={}", orderId, failure);
      }
    returnCursor = due.size() == 100 ? due.get(99) : "";
    List<String> closed = normalFundsMapper.selectCompletionCandidates(completionCursor);
    for (String orderId : closed)
      try {
        returnClosure.reconcile(orderId);
      } catch (RuntimeException failure) {
        LOG.warn("订单完成判定未完成 orderId={}", orderId, failure);
      }
    completionCursor = closed.size() == 100 ? closed.get(99) : "";
  }

  /** 转换逾期占用并继续消费此前尚未处理的风险事件。 */
  @Scheduled(
      fixedDelayString = "${mj.fulfillment.recovery-delay-ms:60000}",
      initialDelayString = "${mj.fulfillment.recovery-delay-ms:60000}")
  public synchronized void recoverOverdue() {
    List<String> due = overdueMapper.selectDue(now(clock).toLocalDate(), overdueCursor);
    for (String order : due)
      try {
        overdueOrders.transition(order);
      } catch (RuntimeException failure) {
        LOG.warn("逾期占用转换未完成 orderId={}", order, failure);
      }
    overdueCursor = due.size() == 100 ? due.get(99) : "";
    List<String> events = overdueMapper.selectPending(eventCursor);
    for (String event : events)
      try {
        overdueOrders.consume(event);
      } catch (RuntimeException failure) {
        LOG.warn("逾期影响检查未完成 eventId={}", event, failure);
      }
    eventCursor = events.size() == 100 ? events.get(99) : "";
    List<String> risks=overdueMapper.selectActiveRiskIds(riskCursor);
    for (String riskId : risks) {
      try {
        overdueOrders.reconcileRisk(riskId);
      } catch (RuntimeException failure) {
        LOG.warn("风险解除核验未完成 riskId={}",riskId,failure);
      }
    }
    riskCursor=risks.size()==100 ? risks.get(99) : "";
  }
}
