package com.mujian.backend.order.dto;

import com.mujian.backend.common.api.PageQuery;
import java.time.LocalDate;

/** 同一列表与统计范围，业务身份由服务端注入。 */
public record OrderQuery(
    String buyerId,
    String factoryId,
    String view,
    String keyword,
    LocalDate startDate,
    LocalDate endDate,
    PageQuery page) {}
