package com.mujian.backend.prop.projection;

/** 工厂模块既有能力的只读边界；不迁移工厂业务。 */
public record PropFactoryProjection(
    String id,
    String ownerUserId,
    String factoryStatus,
    String factoryName,
    String factoryAvatarUrl) {
  /** 读取记录标识，供查询结果映射使用。 */
  public String getId() {
    return id;
  }

  /** 读取ownerUserId，供查询结果映射使用。 */
  public String getOwnerUserId() {
    return ownerUserId;
  }

  /** 读取factoryStatus，供查询结果映射使用。 */
  public String getFactoryStatus() {
    return factoryStatus;
  }

  /** 读取factoryName，供查询结果映射使用。 */
  public String getFactoryName() {
    return factoryName;
  }

  /** 读取factoryAvatarUrl，供查询结果映射使用。 */
  public String getFactoryAvatarUrl() {
    return factoryAvatarUrl;
  }
}
