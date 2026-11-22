package com.mujian.backend.account.vo;

import com.mujian.backend.web.response.account.ContextResponse;

/** 微信登录后的业务会话与账号上下文。 */
public record WechatLoginVO(String sessionToken, ContextResponse context, String noticeCode) {}
