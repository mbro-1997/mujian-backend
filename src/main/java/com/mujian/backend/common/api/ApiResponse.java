package com.mujian.backend.common.api;

/** 新类型化链路的成功响应，与现有ApiReply JSON结构相同。 */
public record ApiResponse<T>(String code, String message, T data, String traceId) {}
