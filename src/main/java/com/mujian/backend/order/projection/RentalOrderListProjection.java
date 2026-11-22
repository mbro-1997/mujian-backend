package com.mujian.backend.order.projection;

import java.time.*;
import com.mujian.backend.order.domain.RentalOrderDO;

/** 列表摘要、金额及动作所需的订单字段。 */
public record RentalOrderListProjection(
    String id,
    String orderNo,
    String factoryId,
    String orderStatus,
    String acceptStatus,
    String refundStatus,
    LocalDate rentalStartDate,
    LocalDate rentalEndDate,
    LocalDate currentDueDate,
    int rentOriginalAmountFen,
    int couponDiscountAmountFen,
    int rentPaidAmountFen,
    int depositPaidAmountFen,
    int freightPrepaidPaidAmountFen,
    int totalPaidAmountFen,
    int totalRefundedAmountFen,
    int totalDeductedAmountFen,
    LocalDateTime acceptDeadlineAt,
    int version,
    LocalDateTime createdAt) {
  /** 详情沿用同一摘要转换。 */
  public static RentalOrderListProjection from(RentalOrderDO order) {
    return new RentalOrderListProjection(
        order.id(),
        order.orderNo(),
        order.factoryId(),
        order.orderStatus(),
        order.acceptStatus(),
        order.refundStatus(),
        order.rentalStartDate(),
        order.rentalEndDate(),
        order.currentDueDate(),
        order.rentOriginalAmountFen(),
        order.couponDiscountAmountFen(),
        order.rentPaidAmountFen(),
        order.depositPaidAmountFen(),
        order.freightPrepaidPaidAmountFen(),
        order.totalPaidAmountFen(),
        order.totalRefundedAmountFen(),
        order.totalDeductedAmountFen(),
        order.acceptDeadlineAt(),
        order.version(),
        order.createdAt());
  }
}
