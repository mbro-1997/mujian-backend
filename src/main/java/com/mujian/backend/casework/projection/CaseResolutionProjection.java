package com.mujian.backend.casework.projection;

public record CaseResolutionProjection(
    String caseType, String caseStatus, int version, String sourceType, String sourceId,
    String beneficiary) {}
