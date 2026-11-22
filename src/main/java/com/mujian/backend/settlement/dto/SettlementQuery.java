package com.mujian.backend.settlement.dto;

import static com.mujian.backend.common.support.D2Values.id;

import com.mujian.backend.common.api.*;
import java.time.*;
import java.util.Set;

public record SettlementQuery(
    String factory, String status, LocalDate fromDate, LocalDate toDate, PageQuery page) {
  public SettlementQuery {
    if (factory != null) id(factory);
    if (status != null && !Set.of("settled", "processing").contains(status))
      throw ApiFailure.invalid();
    if ((fromDate == null) != (toDate == null) || fromDate != null && fromDate.isAfter(toDate))
      throw ApiFailure.invalid();
  }

  /** 将请求的业务日期转换为上海日期筛选边界。 */
  public LocalDateTime from() {
    return fromDate == null ? null : fromDate.atStartOfDay();
  }

  /** 将请求的业务日期转换为上海日期筛选边界。 */
  public LocalDateTime until() {
    return toDate == null ? null : toDate.plusDays(1).atStartOfDay();
  }

  public record Acknowledge(String snapshotToken, String remark) {}
}
