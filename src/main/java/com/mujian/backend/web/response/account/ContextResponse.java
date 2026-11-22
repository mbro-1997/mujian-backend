package com.mujian.backend.web.response.account;

public record ContextResponse(
    String userId,
    String nickname,
    String userStatus,
    String roleCode,
    String registrationStage,
    String accessMode,
    String nextAction) {}
