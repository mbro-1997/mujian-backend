package com.mujian.backend.infrastructure.persistence.record.account;
import java.time.LocalDateTime;
public record RuleRecord(String id,String ruleCode,String ruleTitle,String ruleVersion,String contentHash,LocalDateTime effectiveAt,String contentText,String ruleStatus) {}
