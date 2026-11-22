package com.mujian.backend.account.projection;

/** 提交时冻结的已发布业务规则，只读所需文档标识、版本及内容摘要。 */
public record TradeRuleProjection(
    String id, String ruleCode, String ruleVersion, String contentHash) {}
