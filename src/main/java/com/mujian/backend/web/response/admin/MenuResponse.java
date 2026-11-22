package com.mujian.backend.web.response.admin;

import java.util.List;

public record MenuResponse(
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
    int sortNo,
    List<MenuResponse> children) {}
