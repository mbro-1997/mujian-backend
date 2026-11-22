package com.mujian.backend.payment.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.payment.domain.PaymentNotificationDO;
import com.mujian.backend.payment.mapper.*;
import java.time.Clock;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 可信入箱与业务恢复解耦；进程退出后无需原HTTP请求仍能处理。 */
@Service
public class PaymentNotificationRecovery {
  private final PaymentGateway paymentGateway;
  private final PaymentWorkTransaction paymentWorkTransaction;
  private final RefundWorkTransaction refundWorkTransaction;
  private final PaymentOrderMapper paymentOrderMapper;
  private final PaymentNotificationMapper paymentNotificationMapper;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 装配本业务依赖，复用既有权限、持久化及统一时钟。 */
  public PaymentNotificationRecovery(
      PaymentGateway paymentGateway,
      PaymentWorkTransaction paymentWorkTransaction,
      RefundWorkTransaction refundWorkTransaction,
      PaymentOrderMapper paymentOrderMapper,
      PaymentNotificationMapper paymentNotificationMapper,
      ObjectMapper objectMapper,
      Clock clock) {
    this.paymentGateway = paymentGateway;
    this.paymentWorkTransaction = paymentWorkTransaction;
    this.refundWorkTransaction = refundWorkTransaction;
    this.paymentOrderMapper = paymentOrderMapper;
    this.paymentNotificationMapper = paymentNotificationMapper;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 从持久记录接续原义务，单项失败留待原号重试。 */
  public void recover(PaymentNotificationDO paymentNotificationDONotification) {
    try {
      PaymentNotificationInbox.Envelope envelope =
          objectMapper.readValue(
              paymentNotificationDONotification.rawNotifyJson(),
              PaymentNotificationInbox.Envelope.class);
      if (envelope.schemaVersion() != 1
          || !paymentNotificationDONotification.channelEventId().equals(envelope.eventId())
          || !paymentNotificationDONotification.eventType().equals(envelope.eventType()))
        throw new IllegalStateException("notification envelope mismatch");
      PaymentGateway.Notification notificationFact =
          paymentGateway.restoreVerifiedNotification(
              paymentNotificationDONotification.notifyType(),
              envelope.serial(),
              objectMapper.writeValueAsString(envelope.encryptedNotification()));
      if (!notificationFact.eventId().equals(paymentNotificationDONotification.channelEventId())
          || !notificationFact.eventType().equals(paymentNotificationDONotification.eventType()))
        throw new IllegalStateException("notification source mismatch");
      if ("payment".equals(paymentNotificationDONotification.notifyType()))
        paymentWorkTransaction.observe(
            paymentOrderMapper
                .selectPaymentById(paymentNotificationDONotification.paymentOrderId(), false)
                .fundingKey(),
            notificationFact.payment(),
            false);
      else
        refundWorkTransaction.observe(
            paymentNotificationDONotification.refundOrderId(), notificationFact.refund());
      paymentNotificationMapper.markNotificationProcessed(
          paymentNotificationDONotification.id(), "processed", null, now(clock));
    } catch (RuntimeException failure) {
      paymentNotificationMapper.markNotificationProcessed(
          paymentNotificationDONotification.id(),
          TradingState.failed.name(),
          "NOTIFICATION_RECOVERY_PENDING",
          now(clock));
    }
  }
}
