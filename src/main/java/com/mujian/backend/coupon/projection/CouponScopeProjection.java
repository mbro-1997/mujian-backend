package com.mujian.backend.coupon.projection;

/** 批量模板范围查询，不逐券读取工厂集合。 */
public record CouponScopeProjection(String templateId, String factoryId) {}
