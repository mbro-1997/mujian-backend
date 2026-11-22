package com.mujian.backend.delivery.domain;

import java.time.LocalDateTime;

/** 本单成功扫描事实，同一SN只记录一次。 */
public record OutboundScanDO(
    String id,
    String requestId,
    String rootId,
    String orderId,
    String itemId,
    String qrId,
    String snId,
    String actor,
    LocalDateTime at) {}
