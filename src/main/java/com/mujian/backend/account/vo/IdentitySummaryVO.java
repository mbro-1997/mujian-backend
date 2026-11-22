package com.mujian.backend.account.vo;

/** 已绑定身份摘要，不暴露完整手机号或微信标识。 */
public record IdentitySummaryVO(
    String phoneMasked, boolean wechatBound, String roleCode, String userStatus) {}
