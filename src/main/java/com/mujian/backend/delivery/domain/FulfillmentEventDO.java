package com.mujian.backend.delivery.domain;

import java.time.LocalDateTime;

/** 只保存本地已发生事实及唯一业务键，事件摘要可按订单追溯。 */
public record FulfillmentEventDO(
    String id,
    String key,
    String orderId,
    String legId,
    String sourceType,
    String sourceId,
    String type,
    LocalDateTime time,
    String actor,
    String role,
    String summary) {}
