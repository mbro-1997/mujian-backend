package com.mujian.backend.infrastructure.persistence.record.admin;

public record MenuRecord(
    String id,
    String parentId,
    String menuName,
    String menuType,
    String permissionCode,
    String routePath,
    String componentPath,
    String iconName,
    boolean visible,
    boolean enabled,
    int sortNo) {}
