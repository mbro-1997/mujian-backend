package com.mujian.backend.prop.service;

import com.mujian.backend.factory.projection.FactorySourceProjection;
import com.mujian.backend.prop.projection.PropFactoryProjection;

/** 工厂模块既有返回类型仅在这里转换；工厂业务与SQL不变。 */
public final class PropFactoryGateway {
  /** 限制工具类实例化，统一通过静态方法使用。 */
  private PropFactoryGateway() {}

  /** 把既有工厂资料行转换为道具模块使用的明确投影。 */
  public static PropFactoryProjection project(FactorySourceProjection factorySourceProjection) {
    return factorySourceProjection == null
        ? null
        : new PropFactoryProjection(
            factorySourceProjection.getId(),
            factorySourceProjection.getOwnerUserId(),
            factorySourceProjection.getFactoryStatus(),
            factorySourceProjection.getFactoryName(),
            factorySourceProjection.getFactoryAvatarUrl());
  }
}
