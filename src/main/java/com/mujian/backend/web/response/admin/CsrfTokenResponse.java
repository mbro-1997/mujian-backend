package com.mujian.backend.web.response.admin;

/** 当前管理会话的跨站请求校验令牌。 */
public record CsrfTokenResponse(String csrfToken) {}
