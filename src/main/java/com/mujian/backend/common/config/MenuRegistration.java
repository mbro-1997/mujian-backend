package com.mujian.backend.common.config;

/** 编译期声明可执行页面，数据库仅保存标识，不决定类名或加载地址。 */
public record MenuRegistration(String routePath, String componentPath) {}
