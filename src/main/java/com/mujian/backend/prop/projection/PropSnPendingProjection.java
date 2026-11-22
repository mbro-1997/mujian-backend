package com.mujian.backend.prop.projection;

/** 实物及其当前待审申请的只读对应关系。 */
public record PropSnPendingProjection(String snId, String auditId) {}
