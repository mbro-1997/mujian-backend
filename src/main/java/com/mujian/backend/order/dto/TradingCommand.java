package com.mujian.backend.order.dto;

import com.mujian.backend.common.enums.TradingOperation;

/** 会话身份与统一边界生成的命令摘要，不接收客户端actor字段。 */
public record TradingCommand(
    String actor, String requestId, TradingOperation operation, String requestHash) {}
