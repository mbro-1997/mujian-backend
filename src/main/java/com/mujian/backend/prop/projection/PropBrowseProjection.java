package com.mujian.backend.prop.projection;

/** 联合查询结果，不冒充prop_sku整表实体，也不直接返回前端。 */
public record PropBrowseProjection(
    String skuId,
    String propName,
    String assetType,
    String imageKey,
    String typeCode,
    int dailyRentPriceFen,
    int depositAmountFen,
    String factoryId,
    String factoryName) {}
