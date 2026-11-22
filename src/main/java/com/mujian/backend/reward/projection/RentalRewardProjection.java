package com.mujian.backend.reward.projection;

import java.time.LocalDateTime;

/** 固定首次事件及其原规则模板，供短事务履行和到期判断。 */
public record RentalRewardProjection(
    String id,
    String userId,
    String userStatus,
    com.mujian.backend.common.enums.FirstRewardEvent eventType,
    String templateId,
    String rewardStatus,
    String templateStatus,
    String validityType,
    Integer validDays,
    LocalDateTime validStartAt,
    LocalDateTime validEndAt) {}
