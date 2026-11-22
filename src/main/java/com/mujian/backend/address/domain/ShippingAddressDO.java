package com.mujian.backend.address.domain;

import java.time.LocalDateTime;

/** 仅承接对应持久化事实，不直接作为接口响应。 */
public record ShippingAddressDO(
    String id,
    String userId,
    String receiverName,
    String phone,
    String regionText,
    String detailAddress,
    boolean isDefault,
    String addressStatus,
    int version,
    String defaultUserGuard,
    LocalDateTime createdAt,
    LocalDateTime updatedAt) {}
