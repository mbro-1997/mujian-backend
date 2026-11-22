package com.mujian.backend.payment.mapper;

import com.mujian.backend.payment.domain.PaymentNotificationDO;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface PaymentNotificationMapper {
  /** 入箱成功后独立恢复；失败保留加密原文供下一轮重试。 */
  List<PaymentNotificationDO> selectPendingNotifications(@Param("before") LocalDateTime before);

  /** 原通知业务事实合并后标记处理完成。 */
  void markNotificationProcessed(
      @Param("id") String id,
      @Param("status") String status,
      @Param("message") String message,
      @Param("now") LocalDateTime now);

  /** 渠道事件唯一，重复通知不覆盖首份已验证原文。 */
  void insertNotification(@Param("notification") PaymentNotificationDO paymentNotificationDO);

  /** 按事件号确认重复通知仍属于原事件。 */
  PaymentNotificationDO selectNotificationByEvent(@Param("event") String event);
}
