package com.mujian.backend.payment.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.payment.domain.PaymentNotificationDO;
import com.mujian.backend.payment.mapper.*;
import java.time.Clock;
import org.slf4j.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

/** 持久任务定时接续，外部渠道未就绪也不阻塞其他模块启动。 */
@Service
public class TradingRecovery {
  private static final Logger LOG = LoggerFactory.getLogger(TradingRecovery.class);
  private final PaymentNotificationMapper paymentNotificationMapper;
  private final PaymentNotificationRecovery paymentNotificationRecovery;
  private final PaymentOrderMapper paymentOrderMapper;
  private final PaymentExecution paymentExecution;
  private final RefundMapper refundMapper;
  private final RefundExecution refundExecution;
  private final Clock clock;
  private final com.mujian.backend.custom.mapper.CustomOrderMapper customOrderMapper;
  private final com.mujian.backend.custom.service.CustomFunds customFunds;
  private final Environment environment;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public TradingRecovery(
      PaymentNotificationMapper paymentNotificationMapper,
      PaymentNotificationRecovery paymentNotificationRecovery,
      PaymentOrderMapper paymentOrderMapper,
      PaymentExecution paymentExecution,
      RefundMapper refundMapper,
      RefundExecution refundExecution,
      Clock clock,
      Environment environment,com.mujian.backend.custom.mapper.CustomOrderMapper customOrderMapper,com.mujian.backend.custom.service.CustomFunds customFunds) {
    this.customOrderMapper=customOrderMapper;this.customFunds=customFunds;
    this.paymentNotificationMapper = paymentNotificationMapper;
    this.paymentNotificationRecovery = paymentNotificationRecovery;
    this.paymentOrderMapper = paymentOrderMapper;
    this.paymentExecution = paymentExecution;
    this.refundMapper = refundMapper;
    this.refundExecution = refundExecution;
    this.clock = clock;
    this.environment = environment;
  }

  /** 单项异常仅保留该任务，后续退款和支付仍继续。 */
  public void recover() {
    try {
      for (PaymentNotificationDO paymentNotificationDONotification :
          paymentNotificationMapper.selectPendingNotifications(now(clock).minusMinutes(5)))
        paymentNotificationRecovery.recover(paymentNotificationDONotification);
    } catch (RuntimeException failure) {
      LOG.warn("event=trading_notification_scan_failed");
    }
    try {
      for (String batchId : paymentOrderMapper.selectDuePaymentBatches(now(clock)))
        try {
          paymentExecution.execute(batchId);
        } catch (RuntimeException failure) {
          LOG.warn("event=trading_payment_recovery_pending batchId={}", batchId);
        }
    } catch (RuntimeException failure) {
      LOG.warn("event=trading_payment_scan_failed");
    }
    try {for(String id:customOrderMapper.selectCustomOrderExpired(now(clock))) customFunds.closeExpired(id);}
    catch(RuntimeException failure){LOG.warn("event=custom_payment_close_pending");}
    try {
      for (String refundId : refundMapper.selectDueRefundIds(now(clock)))
        try {
          refundExecution.execute(refundId);
        } catch (RuntimeException failure) {
          LOG.warn("event=trading_refund_recovery_pending refundId={}", refundId);
        }
    } catch (RuntimeException failure) {
      LOG.warn("event=trading_refund_scan_failed");
    }
  }
}
