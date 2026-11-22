package com.mujian.backend.address.vo;


/** 收货地址专属响应，保持既有JSON字段与序列化。 */
public final class AddressViews {
  /** 仅作为相关record的命名空间，不创建包装实例。 */
  private AddressViews() {}

  public record Address(
      String id,
      String receiverName,
      String phone,
      String regionText,
      String detailAddress,
      boolean isDefault,
      int version,
      String createdAt,
      String updatedAt) {}
}
