package com.mujian.backend.payment.dto;

import com.mujian.backend.common.api.PageQuery;

public record RefundQuery(String status, String businessType, String factoryId, PageQuery page) {}
