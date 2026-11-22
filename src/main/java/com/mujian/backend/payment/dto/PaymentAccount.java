package com.mujian.backend.payment.dto;

/** 服务端支付渠道配置摘要，既有支付恢复必须使用原单保存值。 */
public record PaymentAccount(String appId, String mchId, String notifyUrl) {}
