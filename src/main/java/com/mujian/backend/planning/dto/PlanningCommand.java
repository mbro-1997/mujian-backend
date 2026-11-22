package com.mujian.backend.planning.dto;

import com.mujian.backend.common.enums.PlanningOperation;

/** 已绑定用户、精确路由和规范正文摘要的请求，不含可伪造的归属入参。 */
public record PlanningCommand(
    String actor, String requestId, PlanningOperation operation, String hash) {}
