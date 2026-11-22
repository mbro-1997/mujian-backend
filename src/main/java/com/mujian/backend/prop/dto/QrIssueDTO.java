package com.mujian.backend.prop.dto;

public record QrIssueDTO(
    String targetType, String targetId, String supersedesQrId, String reason) {}
