package com.mujian.backend.verification.vo;

/** 提交或恢复原申请的固定响应。 */
public record VerificationSubmitVO<T>(String outcome, T application) {}
