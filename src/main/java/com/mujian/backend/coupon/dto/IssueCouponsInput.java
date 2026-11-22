package com.mujian.backend.coupon.dto;

import java.util.List;

public record IssueCouponsInput(
    String mode,
    String templateId,
    int quantityPerUser,
    String reason,
    List<String> userIds,
    Audience audienceFilter) {
  public record Audience(String businessRole, String accountStatus) {}
}
