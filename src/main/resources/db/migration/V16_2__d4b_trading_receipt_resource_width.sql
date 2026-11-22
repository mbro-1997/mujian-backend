-- C-02 的费用申报回执资源名长 33 字符；扩容以保存完整契约值和原有回执。
ALTER TABLE trading_operation_record MODIFY resource_type VARCHAR(64) NOT NULL COMMENT '资源类型';
