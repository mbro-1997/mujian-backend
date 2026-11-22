package com.mujian.backend.delivery.dto;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.api.PageQuery;
import com.mujian.backend.common.enums.ReturnCheckStatus;
import java.time.LocalDate;

/** 管理端筛选只作用于授权后的真实记录。 */
public final class MonitoringQuery {
  /** 仅承载本领域的结构定义或纯计算。 */
  private MonitoringQuery() {}

  public record Warnings(
      ReturnCheckStatus status,
      String factoryId,
      String keyword,
      LocalDate fromDate,
      LocalDate toDate,
      PageQuery page) {
    public Warnings {
      if ((fromDate == null) != (toDate == null) || fromDate != null && fromDate.isAfter(toDate))
        throw ApiFailure.invalid();
    }
  }

  public record Risks(
      String status, String sourceOrderId, String affectedOrderId, PageQuery page) {}
}
