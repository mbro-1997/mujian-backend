package com.mujian.backend.address.dto;


/** 收货地址专属请求，保持既有JSON字段与序列化。 */
public final class AddressDtos {
  /** 仅作为相关record的命名空间，不创建包装实例。 */
  private AddressDtos() {}

  public record CreateAddress(
      String receiverName,
      String phone,
      String regionText,
      String detailAddress,
      boolean isDefault) {}

  public record UpdateAddress(
      Integer expectedVersion,
      String receiverName,
      String phone,
      String regionText,
      String detailAddress,
      boolean isDefault) {}
}
