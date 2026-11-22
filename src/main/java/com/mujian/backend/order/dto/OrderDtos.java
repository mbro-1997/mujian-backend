package com.mujian.backend.order.dto;

public final class OrderDtos {
  /** 禁止实例化此静态结构容器。 */
  private OrderDtos() {}

  public record Reject(int expectedVersion, String reason) {}

  public record Cancel(String confirmToken, String reason) {}
}
