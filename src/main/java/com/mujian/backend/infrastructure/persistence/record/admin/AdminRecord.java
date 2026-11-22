package com.mujian.backend.infrastructure.persistence.record.admin;

public record AdminRecord(
    String id,
    String userId,
    String username,
    String passwordHash,
    boolean enabled,
    String nickname,
    String userStatus,
    String roleCode) {}
