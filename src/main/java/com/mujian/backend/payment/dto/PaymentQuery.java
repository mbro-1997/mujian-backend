package com.mujian.backend.payment.dto;

import com.mujian.backend.common.api.PageQuery;

public record PaymentQuery(
    String status, String factoryId, String buyerUserId, String keyword, PageQuery page) {}
