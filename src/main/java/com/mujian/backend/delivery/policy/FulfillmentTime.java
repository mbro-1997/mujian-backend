package com.mujian.backend.delivery.policy;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.FulfillmentReason;
import com.mujian.backend.common.enums.TransportServiceMode;
import java.time.LocalDateTime;

/** 已确认的6/12/72小时窗口；所有时间来自调用者统一时钟和持久事实。 */
public final class FulfillmentTime {
  public record ReceiptWindow(LocalDateTime startAt, LocalDateTime deadlineAt) {}

  public record ReturnWindow(
      LocalDateTime startAt, LocalDateTime warningAt, LocalDateTime autoCloseAt) {}

  /** 纯规则容器不持有调度或存储状态。 */
  private FulfillmentTime() {}

  /** 配送和代叫共用6小时，自提从交接开始，均允许另行提前确认。 */
  public static ReceiptWindow receipt(
      TransportServiceMode transportServiceMode, LocalDateTime dispatchedAt) {
    LocalDateTime startAt =
        switch (transportServiceMode) {
          case self_pickup -> dispatchedAt;
          case factory_delivery, factory_assisted_call -> dispatchedAt.plusHours(6);
          default -> throw new IllegalArgumentException("outbound service required");
        };
    return new ReceiptWindow(startAt, startAt.plusHours(12));
  }

  /** 用户实际发出不能早于收货或晚于服务端当前时刻。 */
  public static ReturnWindow userReturn(
      LocalDateTime receivedAt, LocalDateTime sentAt, LocalDateTime now) {
    if (receivedAt == null || sentAt == null || sentAt.isBefore(receivedAt) || sentAt.isAfter(now))
      throw conflict(FulfillmentReason.RETURN_TIME_INVALID.name());
    return returning(sentAt.plusHours(12));
  }

  /** 工厂取回的预计到厂由本次命令提供，不读取虚构预约时间。 */
  public static ReturnWindow pickup(LocalDateTime startedAt, LocalDateTime expectedArrivalAt) {
    if (expectedArrivalAt == null || expectedArrivalAt.isBefore(startedAt))
      throw conflict(FulfillmentReason.RETURN_TIME_INVALID.name());
    return returning(expectedArrivalAt);
  }

  /** 窗口只可被真实早到事实提前，调度重启不能延长既有截止。 */
  public static ReturnWindow returning(LocalDateTime startAt) {
    return new ReturnWindow(startAt, startAt.plusHours(12), startAt.plusHours(72));
  }

  /** 真实到厂只提前尚未开始的窗口，重复到厂保留原起点。 */
  public static ReturnWindow arrival(LocalDateTime plannedStartAt, LocalDateTime arrivedAt) {
    return returning(
        plannedStartAt == null || arrivedAt.isBefore(plannedStartAt) ? arrivedAt : plannedStartAt);
  }
}
