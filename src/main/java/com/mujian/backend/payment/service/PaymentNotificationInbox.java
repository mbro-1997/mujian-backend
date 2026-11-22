package com.mujian.backend.payment.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.payment.domain.*;
import com.mujian.backend.payment.mapper.*;
import com.mujian.backend.payment.policy.PaymentFactValidation;
import com.mujian.backend.payment.policy.RefundFactValidation;
import com.mujian.backend.payment.projection.RefundSourceProjection;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.*;

/** 验签后持久接收，再由统一恢复服务处理；数据库失败不得给渠道成功应答。 */
@Service
public class PaymentNotificationInbox {
  public record Envelope(
      int schemaVersion,
      String eventId,
      String eventType,
      String serial,
      LocalDateTime receivedAt,
      JsonNode encryptedNotification) {}

  private final PaymentOrderMapper paymentOrderMapper;
  private final RefundMapper refundMapper;
  private final PaymentNotificationMapper paymentNotificationMapper;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 原支付来源、不可变子单与已验签原文分开存储。 */
  public PaymentNotificationInbox(
      PaymentOrderMapper paymentOrderMapper,
      RefundMapper refundMapper,
      PaymentNotificationMapper paymentNotificationMapper,
      ObjectMapper objectMapper,
      Clock clock) {
    this.paymentOrderMapper = paymentOrderMapper;
    this.refundMapper = refundMapper;
    this.paymentNotificationMapper = paymentNotificationMapper;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 所有来源及金额核对成功后才占用事件唯一键，不会被伪造通知抢先污染。 */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public void receive(String kind, PaymentGateway.Notification notification) {
    String paymentId = null, refundId = null, outTradeNo = null, outRefundNo = null;
    if ("payment".equals(kind)) {
      PaymentGateway.PaymentFact paymentFact = notification.payment();
      if (paymentFact == null) throw ApiFailure.invalid();
      PaymentOrderDO paymentOrderDO =
          paymentOrderMapper.selectPaymentById(paymentFact.outTradeNo(), false);
      if (paymentOrderDO == null) throw ApiFailure.invalid();
      PaymentFactValidation.verify(
          paymentOrderDO,
          paymentOrderMapper.selectSubordersByPayment(paymentOrderDO.id(), false),
          paymentFact,
          false);
      paymentId = paymentOrderDO.id();
      outTradeNo = paymentFact.outTradeNo();
    } else {
      PaymentGateway.RefundFact refundFact = notification.refund();
      if (refundFact == null) throw ApiFailure.invalid();
      RefundSourceProjection refundSourceProjection =
          refundMapper.selectRefundSourceByChannelNo(refundFact.refundNo());
      RefundFactValidation.verify(refundSourceProjection, refundFact);
      paymentId = refundSourceProjection.paymentOrderId();
      refundId = refundSourceProjection.id();
      outTradeNo = refundFact.outTradeNo();
      outRefundNo = refundFact.refundNo();
    }
    LocalDateTime at = now(clock);
    Envelope envelope =
        new Envelope(
            1,
            notification.eventId(),
            notification.eventType(),
            notification.serial(),
            at,
            objectMapper.readTree(notification.encryptedJson()));
    PaymentNotificationDO paymentNotificationDO =
        new PaymentNotificationDO(
            Ids.next(),
            kind,
            notification.eventId(),
            paymentId,
            refundId,
            outTradeNo,
            outRefundNo,
            notification.eventType(),
            "received",
            objectMapper.writeValueAsString(envelope),
            at,
            null,
            null,
            at);
    paymentNotificationMapper.insertNotification(paymentNotificationDO);
    PaymentNotificationDO paymentNotificationDOStored =
        paymentNotificationMapper.selectNotificationByEvent(notification.eventId());
    if (!Objects.equals(paymentNotificationDOStored.paymentOrderId(), paymentId)
        || !Objects.equals(paymentNotificationDOStored.refundOrderId(), refundId)
        || !paymentNotificationDOStored.notifyType().equals(kind)
        || !paymentNotificationDOStored.eventType().equals(notification.eventType()))
      throw ApiFailure.invalid();
  }
}
