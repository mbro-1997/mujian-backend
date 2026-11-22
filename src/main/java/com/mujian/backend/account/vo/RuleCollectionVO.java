package com.mujian.backend.account.vo;

import java.util.List;

/** 注册规则集合。 */
public record RuleCollectionVO(String scene, List<RuleSummaryVO> rules) {}
