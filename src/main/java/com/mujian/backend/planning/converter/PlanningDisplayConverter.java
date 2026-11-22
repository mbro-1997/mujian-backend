package com.mujian.backend.planning.converter;

import com.mujian.backend.planning.domain.SnapshotV1;
import com.mujian.backend.planning.vo.PlanningViews.*;
import com.mujian.backend.prop.vo.PublicImageViewVO;

/** 纯展示转换；签名由获权服务提前提供。 */
public final class PlanningDisplayConverter {
  private PlanningDisplayConverter() {}

  /** 本人/展示价格模式保留冻结价格。 */
  public static PricedDisplay priced(
      SnapshotV1 snapshotV1, PublicImageViewVO publicImageViewVOImage) {
    return new PricedDisplay(
        snapshotV1.skuId(),
        snapshotV1.factoryId(),
        snapshotV1.factoryName(),
        snapshotV1.name(),
        snapshotV1.assetType(),
        snapshotV1.typeCode(),
        snapshotV1.typeLabel(),
        snapshotV1.styleCodes(),
        snapshotV1.styleLabels(),
        snapshotV1.sizeDesc(),
        snapshotV1.lengthCm(),
        snapshotV1.widthCm(),
        snapshotV1.heightCm(),
        snapshotV1.weightKg(),
        snapshotV1.materialDesc(),
        publicImageViewVOImage,
        snapshotV1.prices());
  }

  /** 隐藏模式没有任何价格属性。 */
  public static HiddenDisplay hidden(
      SnapshotV1 snapshotV1, PublicImageViewVO publicImageViewVOImage) {
    return new HiddenDisplay(
        snapshotV1.skuId(),
        snapshotV1.factoryId(),
        snapshotV1.factoryName(),
        snapshotV1.name(),
        snapshotV1.assetType(),
        snapshotV1.typeCode(),
        snapshotV1.typeLabel(),
        snapshotV1.styleCodes(),
        snapshotV1.styleLabels(),
        snapshotV1.sizeDesc(),
        snapshotV1.lengthCm(),
        snapshotV1.widthCm(),
        snapshotV1.heightCm(),
        snapshotV1.weightKg(),
        snapshotV1.materialDesc(),
        publicImageViewVOImage);
  }
}
