package com.mujian.backend.factory.domain;

public record FactoryChangeSnapshot(
    int schema_version,
    FactorySnapshot before,
    FactorySnapshot proposed,
    String base_profile_version,
    String previous_application_id,
    Object phone_verification) {}
