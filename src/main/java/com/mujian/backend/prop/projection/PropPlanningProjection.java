package com.mujian.backend.prop.projection;

/** 批量返回道具所属工厂及当前公开性，供方案清单可信快照使用。 */
public record PropPlanningProjection(
    String skuId, String factoryId, String factoryName, boolean publicVisible) {}
