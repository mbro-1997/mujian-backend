package com.mujian.backend.planning.domain;

import com.mujian.backend.planning.vo.PlanningViews.Prices;
import java.util.List;

/** 加入时冻结的可信商品展示数据，存储键只在服务端使用。 */
public record SnapshotV1(
    int schemaVersion,
    String skuId,
    String factoryId,
    String factoryName,
    String name,
    String assetType,
    String typeCode,
    String typeLabel,
    List<String> styleCodes,
    List<String> styleLabels,
    String sizeDesc,
    String lengthCm,
    String widthCm,
    String heightCm,
    String weightKg,
    String materialDesc,
    List<String> imageKeys,
    @com.fasterxml.jackson.annotation.JsonInclude(
            com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
        Prices prices) {}
