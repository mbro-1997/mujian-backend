package com.mujian.backend.verification.dto;

import java.time.LocalDateTime;

public record VerificationFilter(
    String status,
    String keyword,
    boolean identifier,
    LocalDateTime from,
    LocalDateTime to,
    int size,
    long offset,
    LocalDateTime now) {
  /** 校验并构造本类型所需数据。 */
  public static VerificationFilter of(
      String status,
      String keyword,
      LocalDateTime from,
      LocalDateTime to,
      int page,
      int size,
      LocalDateTime now) {
    String k = keyword == null ? null : keyword.trim();
    if (k != null && k.isEmpty()) k = null;
    boolean identifier = k != null && k.matches("(?:RV|SV)?[0-9a-f]{32}");
    if (k != null && !identifier)
      k = "%" + k.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    return new VerificationFilter(
        status, k, identifier, from, to, size, (long) (page - 1) * size, now);
  }
}
