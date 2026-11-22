package com.mujian.backend.infrastructure.persistence.record.account;

public record UserRecord(
    String id, String nickname, String userStatus, String registerStatus, String roleCode) {}
