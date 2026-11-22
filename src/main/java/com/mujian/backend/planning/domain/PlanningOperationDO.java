package com.mujian.backend.planning.domain;

import java.time.LocalDateTime;

/** 仅承接对应持久化事实，不直接作为接口响应。 */
public record PlanningOperationDO(
    String id,
    String userId,
    String requestId,
    String operationCode,
    String requestHash,
    String resourceType,
    String resourceId,
    String resultJson,
    LocalDateTime createdAt) {}
