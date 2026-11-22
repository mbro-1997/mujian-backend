-- D3-B交易迁移；在V12基础上追加，正式设计来源C-D3-B数据设计.sql。

-- 仅新增/增量；禁止改V1-V12、清库。MySQL 8.4；所有金额为分。

CREATE TABLE `checkout_batch` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算批次 ID',
  `batch_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算批次号',
  `source_checklist_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源清单 ID',
  `buyer_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁方用户 ID',
  `batch_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '批次状态',
  `submit_idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '提交支付幂等键',
  `active_checklist_guard` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN guard_released_at IS NULL AND batch_status IN ('pending_payment','payment_exception','refund_processing') THEN source_checklist_id ELSE NULL END) STORED COMMENT '活动清单批次守卫',
  `exception_reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '支付异常原因',
  `recovery_mode` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '成单恢复方式',
  `exception_resolved_at` DATETIME(3) NULL DEFAULT NULL COMMENT '异常处理完成时间',
  `rental_start_date` DATE NOT NULL COMMENT '租期开始日期',
  `rental_end_date` DATE NOT NULL COMMENT '租期结束日期',
  `rental_days` INT NOT NULL COMMENT '首次租赁天数',
  `transport_notice_snapshot` VARCHAR(1000) NULL DEFAULT NULL COMMENT '运输强提示快照',
  `rule_snapshot_json` JSON NULL DEFAULT NULL COMMENT '规则快照 JSON',
  `rent_payable_amount_fen` INT NOT NULL COMMENT '应付租金合计',
  `coupon_discount_amount_fen` INT NOT NULL COMMENT '优惠券减免租金合计',
  `deposit_original_amount_fen` INT NOT NULL COMMENT '原始押金合计',
  `deposit_waived_amount_fen` INT NOT NULL COMMENT '信用减免押金合计',
  `deposit_payable_amount_fen` INT NOT NULL COMMENT '应付押金合计',
  `service_fee_fen` INT NOT NULL COMMENT '服务费',
  `freight_prepaid_amount_fen` INT NOT NULL COMMENT '运费暂收合计',
  `other_discount_amount_fen` INT NOT NULL COMMENT '其他优惠减免',
  `payable_amount_fen` INT NOT NULL COMMENT '应付总额',
  `hold_expire_at` DATETIME(3) NOT NULL COMMENT '支付短锁过期时间',
  `submitted_at` DATETIME(3) NOT NULL COMMENT '提交结算时间',
  `paid_at` DATETIME(3) NULL DEFAULT NULL COMMENT '支付成功时间',
  `closed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '关闭时间',
  `expired_at` DATETIME(3) NULL DEFAULT NULL COMMENT '批次过期时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  `request_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原提交摘要',
  `guard_released_at` DATETIME(3) NULL DEFAULT NULL COMMENT '清单守卫永久释放时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_1_i1` (`batch_no`),
  UNIQUE KEY `d3b_1_i2` (`source_checklist_id`,`submit_idempotency_key`),
  UNIQUE KEY `d3b_1_i3` (`active_checklist_guard`),
  KEY `d3b_1_i4` (`source_checklist_id`),
  KEY `d3b_1_i5` (`buyer_user_id`,`batch_status`,`created_at`),
  KEY `d3b_1_i6` (`batch_status`,`hold_expire_at`),
  CONSTRAINT `d3b_1_c1` CHECK (COALESCE((rental_days BETWEEN 1 AND 120 AND rental_start_date<=rental_end_date),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c2` CHECK (COALESCE((deposit_payable_amount_fen=deposit_original_amount_fen-deposit_waived_amount_fen AND payable_amount_fen=rent_payable_amount_fen-coupon_discount_amount_fen+deposit_payable_amount_fen+service_fee_fen+freight_prepaid_amount_fen-other_discount_amount_fen),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c3` CHECK (COALESCE((payable_amount_fen>=1 AND coupon_discount_amount_fen<=rent_payable_amount_fen),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c4` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c5` CHECK (COALESCE((REGEXP_LIKE(source_checklist_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c6` CHECK (COALESCE((REGEXP_LIKE(buyer_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c7` CHECK (COALESCE((batch_status IN ('pending_payment','paid','payment_exception','refund_processing','refunded_closed','expired','closed')),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c8` CHECK (COALESCE(((active_checklist_guard IS NULL OR REGEXP_LIKE(active_checklist_guard,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c9` CHECK (COALESCE(((recovery_mode IS NULL OR recovery_mode IN ('recover_orders','refund_batch'))),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c10` CHECK (COALESCE((rent_payable_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c11` CHECK (COALESCE((coupon_discount_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c12` CHECK (COALESCE((deposit_original_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c13` CHECK (COALESCE((deposit_waived_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c14` CHECK (COALESCE((deposit_payable_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c15` CHECK (COALESCE((service_fee_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c16` CHECK (COALESCE((freight_prepaid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c17` CHECK (COALESCE((other_discount_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c18` CHECK (COALESCE((payable_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_1_c19` CHECK (COALESCE((version>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `checkout_batch_item_snapshot` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '快照明细 ID',
  `checkout_batch_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算批次 ID',
  `source_checklist_item_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源清单明细 ID',
  `source_checklist_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源清单 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `factory_name_snapshot` VARCHAR(128) NOT NULL COMMENT '工厂名称快照',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `prop_name_snapshot` VARCHAR(128) NOT NULL COMMENT '道具名称快照',
  `image_url_snapshot` VARCHAR(512) NULL DEFAULT NULL COMMENT '主图快照',
  `asset_type_snapshot` VARCHAR(32) NOT NULL COMMENT '资产类型快照',
  `type_label_snapshot` VARCHAR(64) NULL DEFAULT NULL COMMENT '类别中文快照',
  `style_labels_snapshot` VARCHAR(255) NULL DEFAULT NULL COMMENT '风格中文快照',
  `quantity` INT NOT NULL COMMENT '数量',
  `rental_start_date` DATE NOT NULL COMMENT '租期开始日期',
  `rental_end_date` DATE NOT NULL COMMENT '租期结束日期',
  `rental_days` INT NOT NULL COMMENT '租赁天数',
  `daily_rent_price_fen` INT NOT NULL COMMENT '日租金快照',
  `rent_amount_fen` INT NOT NULL COMMENT '租金小计',
  `coupon_discount_amount_fen` INT NOT NULL COMMENT '优惠券分摊',
  `rent_cash_payable_amount_fen` INT NOT NULL COMMENT '租金现金应付',
  `deposit_unit_amount_fen` INT NOT NULL COMMENT '单件押金快照',
  `deposit_original_amount_fen` INT NOT NULL COMMENT '原始押金小计',
  `deposit_waived_amount_fen` INT NOT NULL COMMENT '信用减免押金小计',
  `deposit_payable_amount_fen` INT NOT NULL COMMENT '应付押金小计',
  `checkout_batch_transport_snapshot_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '按工厂运输快照 ID',
  `transport_requirement_snapshot` VARCHAR(1000) NULL DEFAULT NULL COMMENT '运输要求快照',
  `item_snapshot_json` JSON NULL DEFAULT NULL COMMENT '明细扩展快照 JSON',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `d3b_2_i1` (`checkout_batch_id`),
  KEY `d3b_2_i2` (`checkout_batch_id`,`factory_id`),
  KEY `d3b_2_i3` (`source_checklist_item_id`),
  KEY `d3b_2_i4` (`sku_id`),
  UNIQUE KEY `d3b_2_i5` (`checkout_batch_id`,`source_checklist_item_id`),
  CONSTRAINT `d3b_2_c1` CHECK (COALESCE((quantity>0 AND rental_days BETWEEN 1 AND 120 AND coupon_discount_amount_fen<=rent_amount_fen AND rent_cash_payable_amount_fen=rent_amount_fen-coupon_discount_amount_fen),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c2` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c3` CHECK (COALESCE((REGEXP_LIKE(checkout_batch_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c4` CHECK (COALESCE((REGEXP_LIKE(source_checklist_item_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c5` CHECK (COALESCE((REGEXP_LIKE(source_checklist_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c6` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c7` CHECK (COALESCE((REGEXP_LIKE(sku_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c8` CHECK (COALESCE((quantity>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c9` CHECK (COALESCE((daily_rent_price_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c10` CHECK (COALESCE((rent_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c11` CHECK (COALESCE((coupon_discount_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c12` CHECK (COALESCE((rent_cash_payable_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c13` CHECK (COALESCE((deposit_unit_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c14` CHECK (COALESCE((deposit_original_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c15` CHECK (COALESCE((deposit_waived_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c16` CHECK (COALESCE((deposit_payable_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_2_c17` CHECK (COALESCE((REGEXP_LIKE(checkout_batch_transport_snapshot_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `checkout_batch_transport_snapshot` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运输结算快照 ID',
  `checkout_batch_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算批次 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `outbound_service_mode` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '去程服务',
  `return_service_mode` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '回程服务',
  `factory_address_snapshot` VARCHAR(512) NOT NULL COMMENT '工厂地址快照',
  `receiver_address_snapshot` VARCHAR(512) NOT NULL COMMENT '用户收货/使用地址快照',
  `outbound_vehicle_tier_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '去程系统匹配车型档位 ID',
  `return_vehicle_tier_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '回程系统匹配车型档位 ID',
  `outbound_distance_meter` INT NULL DEFAULT NULL COMMENT '去程驾车距离米',
  `return_distance_meter` INT NULL DEFAULT NULL COMMENT '回程驾车距离米',
  `outbound_guide_min_amount_fen` INT NOT NULL COMMENT '去程指导价下限',
  `outbound_guide_max_amount_fen` INT NOT NULL COMMENT '去程指导价上限',
  `return_guide_min_amount_fen` INT NOT NULL COMMENT '回程指导价下限',
  `return_guide_max_amount_fen` INT NOT NULL COMMENT '回程指导价上限',
  `outbound_prepaid_amount_fen` INT NOT NULL COMMENT '去程预收分配',
  `return_prepaid_amount_fen` INT NOT NULL COMMENT '回程预收分配',
  `freight_prepaid_amount_fen` INT NOT NULL COMMENT '本工厂订单运费暂收合计',
  `route_pricing_snapshot_json` JSON NOT NULL COMMENT '路线和计价完整快照',
  `transport_requirement_snapshot` VARCHAR(2000) NULL DEFAULT NULL COMMENT '本工厂订单运输要求汇总快照',
  `buyer_remark_snapshot` VARCHAR(255) NULL DEFAULT NULL COMMENT '本工厂用户备注快照',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_3_i1` (`checkout_batch_id`,`factory_id`),
  KEY `d3b_3_i2` (`checkout_batch_id`),
  CONSTRAINT `d3b_3_c1` CHECK (COALESCE((freight_prepaid_amount_fen=outbound_prepaid_amount_fen+return_prepaid_amount_fen),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c2` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c3` CHECK (COALESCE((REGEXP_LIKE(checkout_batch_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c4` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c5` CHECK (COALESCE((outbound_service_mode IN ('factory_delivery','factory_assisted_call','self_pickup')),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c6` CHECK (COALESCE((return_service_mode IN ('user_return','factory_pickup')),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c7` CHECK (COALESCE(((outbound_vehicle_tier_id IS NULL OR REGEXP_LIKE(outbound_vehicle_tier_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c8` CHECK (COALESCE(((return_vehicle_tier_id IS NULL OR REGEXP_LIKE(return_vehicle_tier_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c9` CHECK (COALESCE((outbound_distance_meter IS NULL OR outbound_distance_meter>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c10` CHECK (COALESCE((return_distance_meter IS NULL OR return_distance_meter>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c11` CHECK (COALESCE((outbound_guide_min_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c12` CHECK (COALESCE((outbound_guide_max_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c13` CHECK (COALESCE((return_guide_min_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c14` CHECK (COALESCE((return_guide_max_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c15` CHECK (COALESCE((outbound_prepaid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c16` CHECK (COALESCE((return_prepaid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_3_c17` CHECK (COALESCE((freight_prepaid_amount_fen>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_order` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '支付单 ID',
  `payment_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '支付单号',
  `request_idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建支付幂等键',
  `checkout_batch_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '首次清单结算批次 ID',
  `payment_scene` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '支付场景',
  `related_extension_request_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关联延期申请 ID',
  `related_compensation_receivable_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关联待缴赔付款 ID',
  `buyer_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '支付用户 ID',
  `pay_channel` VARCHAR(32) NOT NULL COMMENT '支付渠道',
  `channel_order_mode` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道下单模式',
  `channel_app_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '渠道 AppID',
  `channel_mch_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '平台/服务商商户号',
  `channel_notify_url` VARCHAR(512) NULL DEFAULT NULL COMMENT '渠道支付通知地址',
  `channel_prepay_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '渠道预支付 ID',
  `channel_combine_out_trade_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '微信合单商户订单号',
  `payer_openid` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '支付人 OpenID',
  `channel_trade_state` VARCHAR(32) NULL DEFAULT NULL COMMENT '渠道结果汇总',
  `payment_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '支付状态',
  `payable_amount_fen` INT NOT NULL COMMENT '应付金额',
  `paid_amount_fen` INT NOT NULL COMMENT '实付金额',
  `refunded_amount_fen` INT NOT NULL COMMENT '已成功退款金额',
  `expire_at` DATETIME(3) NOT NULL COMMENT '支付过期时间',
  `paid_at` DATETIME(3) NULL DEFAULT NULL COMMENT '支付成功时间',
  `closed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '关闭时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  `next_attempt_at` DATETIME(3) NULL DEFAULT NULL COMMENT '下次恢复检查时间',
  `last_checked_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最后渠道核对时间',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '渠道执行次数',
  `last_error_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最近稳定渠道错误码',
  `close_requested_at` DATETIME(3) NULL DEFAULT NULL COMMENT '取消或到期关单意图时间',
  `close_reason` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关单意图原因',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_4_i1` (`payment_no`),
  UNIQUE KEY `d3b_4_i2` (`request_idempotency_key`),
  UNIQUE KEY `d3b_4_i3` (`pay_channel`,`channel_combine_out_trade_no`),
  KEY `d3b_4_i4` (`checkout_batch_id`,`payment_status`),
  KEY `d3b_4_i5` (`next_attempt_at`,`id`),
  UNIQUE KEY `d3b_4_i6` (`checkout_batch_id`),
  CONSTRAINT `d3b_4_c1` CHECK (COALESCE(((payment_scene='initial_order' AND checkout_batch_id IS NOT NULL AND related_extension_request_id IS NULL AND related_compensation_receivable_id IS NULL) OR (payment_scene='extension_rent' AND checkout_batch_id IS NULL AND related_extension_request_id IS NOT NULL AND related_compensation_receivable_id IS NULL) OR (payment_scene='compensation_supplement' AND checkout_batch_id IS NULL AND related_extension_request_id IS NULL AND related_compensation_receivable_id IS NOT NULL) OR (payment_scene='custom_order_full_payment' AND checkout_batch_id IS NULL AND related_extension_request_id IS NULL AND related_compensation_receivable_id IS NULL)),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c2` CHECK (COALESCE((payable_amount_fen>=1 AND paid_amount_fen<=payable_amount_fen AND refunded_amount_fen<=paid_amount_fen),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c3` CHECK (COALESCE(((close_requested_at IS NULL AND close_reason IS NULL) OR (close_requested_at IS NOT NULL AND close_reason IS NOT NULL)),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c4` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c5` CHECK (COALESCE(((checkout_batch_id IS NULL OR REGEXP_LIKE(checkout_batch_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c6` CHECK (COALESCE((payment_scene IN ('initial_order','extension_rent','compensation_supplement','custom_order_full_payment')),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c7` CHECK (COALESCE(((related_extension_request_id IS NULL OR REGEXP_LIKE(related_extension_request_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c8` CHECK (COALESCE(((related_compensation_receivable_id IS NULL OR REGEXP_LIKE(related_compensation_receivable_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c9` CHECK (COALESCE((REGEXP_LIKE(buyer_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c10` CHECK (COALESCE((channel_order_mode IN ('combine_jsapi')),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c11` CHECK (COALESCE((payment_status IN ('pending','paying','success','partial_received','failed','expired','closed')),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c12` CHECK (COALESCE((payable_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c13` CHECK (COALESCE((paid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c14` CHECK (COALESCE((refunded_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c15` CHECK (COALESCE((version>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c16` CHECK (COALESCE((attempt_count>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_4_c17` CHECK (COALESCE(((close_reason IS NULL OR close_reason IN ('user_cancel','expired'))),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_channel_suborder` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道子单 ID',
  `payment_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '平台支付单 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `factory_wechat_account_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂微信二级商户账户 ID',
  `sub_mchid_snapshot` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '微信二级商户号快照',
  `channel_sub_order_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '微信渠道子单商户单号',
  `channel_sub_transaction_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '微信渠道子单交易号',
  `channel_suborder_amount_fen` INT NOT NULL COMMENT '渠道子单金额',
  `channel_suborder_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道子单状态',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁订单 ID',
  `custom_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '定制订单 ID',
  `channel_description` VARCHAR(127) NOT NULL COMMENT '子单商品描述',
  `profit_sharing_flag` TINYINT NOT NULL COMMENT '冻结待分账标识',
  `payment_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '子单支付结果',
  `channel_trade_state` VARCHAR(32) NULL DEFAULT NULL COMMENT '子单渠道状态快照',
  `paid_amount_fen` INT NOT NULL COMMENT '子单已付金额',
  `refunded_amount_fen` INT NOT NULL COMMENT '子单成功退款累计',
  `paid_at` DATETIME(3) NULL DEFAULT NULL COMMENT '子单支付成功时间',
  `closed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '子单关闭时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '并发版本',
  `freeze_expire_at` DATETIME(3) NULL DEFAULT NULL COMMENT '渠道冻结到期时间',
  `freeze_alert_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '冻结期限预警状态',
  `freeze_alert_at` DATETIME(3) NULL DEFAULT NULL COMMENT '首次预警时间',
  `freeze_alert_reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '冻结预警说明',
  `channel_unfrozen_at` DATETIME(3) NULL DEFAULT NULL COMMENT '渠道实际解冻时间',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_5_i1` (`channel_sub_order_no`),
  UNIQUE KEY `d3b_5_i2` (`payment_order_id`,`factory_id`),
  KEY `d3b_5_i3` (`factory_wechat_account_id`,`channel_suborder_status`),
  KEY `d3b_5_i4` (`rental_order_id`,`payment_status`),
  KEY `d3b_5_i5` (`custom_order_id`,`payment_status`),
  UNIQUE KEY `d3b_5_i6` (`channel_sub_transaction_id`),
  KEY `d3b_5_i7` (`channel_suborder_status`,`freeze_expire_at`),
  CONSTRAINT `d3b_5_c1` CHECK (COALESCE((channel_suborder_amount_fen>=1 AND profit_sharing_flag=1 AND paid_amount_fen<=channel_suborder_amount_fen AND refunded_amount_fen<=paid_amount_fen AND (rental_order_id IS NULL OR custom_order_id IS NULL)),FALSE)=TRUE),
  CONSTRAINT `d3b_5_c2` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_5_c3` CHECK (COALESCE((REGEXP_LIKE(payment_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_5_c4` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_5_c5` CHECK (COALESCE((REGEXP_LIKE(factory_wechat_account_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_5_c6` CHECK (COALESCE((channel_suborder_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_5_c7` CHECK (COALESCE((channel_suborder_status IN ('pending','paid_frozen','partially_refunded','fully_refunded','sharing','settled','closed')),FALSE)=TRUE),
  CONSTRAINT `d3b_5_c8` CHECK (COALESCE(((rental_order_id IS NULL OR REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_5_c9` CHECK (COALESCE(((custom_order_id IS NULL OR REGEXP_LIKE(custom_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_5_c10` CHECK (COALESCE((payment_status IN ('pending','paying','success','failed','expired','closed')),FALSE)=TRUE),
  CONSTRAINT `d3b_5_c11` CHECK (COALESCE((paid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_5_c12` CHECK (COALESCE((refunded_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_5_c13` CHECK (COALESCE((version>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_5_c14` CHECK (COALESCE((freeze_alert_status IN ('none','open','resolved')),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `rental_order` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '订单 ID',
  `order_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '订单号',
  `checkout_batch_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源结算批次 ID',
  `payment_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源支付单 ID',
  `source_checklist_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源清单 ID',
  `buyer_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁方用户 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `order_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '订单状态',
  `accept_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '接单状态',
  `refund_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '退款状态',
  `transport_notice_snapshot` VARCHAR(1000) NULL DEFAULT NULL COMMENT '运输强提示快照',
  `rental_start_date` DATE NOT NULL COMMENT '租期开始日期',
  `rental_end_date` DATE NOT NULL COMMENT '原始租期结束日期',
  `current_due_date` DATE NOT NULL COMMENT '当前有效归还日期',
  `rental_days` INT NOT NULL COMMENT '租赁天数',
  `rent_original_amount_fen` INT NOT NULL COMMENT '原始租金',
  `coupon_discount_amount_fen` INT NOT NULL COMMENT '优惠券分摊',
  `other_rent_discount_amount_fen` INT NOT NULL COMMENT '其他租金优惠',
  `rent_payable_amount_fen` INT NOT NULL COMMENT '应付租金',
  `rent_paid_amount_fen` INT NOT NULL COMMENT '累计现金实付租金',
  `rent_refunded_amount_fen` INT NOT NULL COMMENT '已退租金',
  `rent_deducted_amount_fen` INT NOT NULL COMMENT '已扣租金',
  `deposit_original_amount_fen` INT NOT NULL COMMENT '原始押金',
  `deposit_waived_amount_fen` INT NOT NULL COMMENT '信用减免押金',
  `deposit_payable_amount_fen` INT NOT NULL COMMENT '应付押金',
  `deposit_paid_amount_fen` INT NOT NULL COMMENT '实付押金',
  `deposit_refunded_amount_fen` INT NOT NULL COMMENT '已退押金',
  `deposit_deducted_amount_fen` INT NOT NULL COMMENT '已扣押金',
  `deposit_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '押金状态',
  `service_fee_fen` INT NOT NULL COMMENT '服务费',
  `freight_prepaid_paid_amount_fen` INT NOT NULL COMMENT '初始运费暂收实付',
  `freight_refunded_amount_fen` INT NOT NULL COMMENT '已退运费累计',
  `total_payable_amount_fen` INT NOT NULL COMMENT '首次应付合计',
  `total_paid_amount_fen` INT NOT NULL COMMENT '累计现金实收合计',
  `total_refunded_amount_fen` INT NOT NULL COMMENT '累计成功已退',
  `total_deducted_amount_fen` INT NOT NULL COMMENT '已扣合计',
  `receiver_name_snapshot` VARCHAR(64) NULL DEFAULT NULL COMMENT '收货人快照',
  `receiver_phone_snapshot` VARCHAR(20) NULL DEFAULT NULL COMMENT '收货手机号快照',
  `region_text_snapshot` VARCHAR(128) NULL DEFAULT NULL COMMENT '所在地区快照',
  `detail_address_snapshot` VARCHAR(255) NULL DEFAULT NULL COMMENT '详细地址快照',
  `address_full_text_snapshot` VARCHAR(512) NULL DEFAULT NULL COMMENT '完整地址快照',
  `buyer_remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '用户备注',
  `usage_scene` VARCHAR(255) NULL DEFAULT NULL COMMENT '使用场景',
  `accept_deadline_at` DATETIME(3) NOT NULL COMMENT '接单截止时间',
  `accepted_at` DATETIME(3) NULL DEFAULT NULL COMMENT '接单时间',
  `rejected_at` DATETIME(3) NULL DEFAULT NULL COMMENT '拒绝时间',
  `reject_type` VARCHAR(32) NULL DEFAULT NULL COMMENT '拒绝类型',
  `reject_reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '拒绝原因',
  `cancel_type` VARCHAR(32) NULL DEFAULT NULL COMMENT '取消类型',
  `cancel_reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '取消原因',
  `cancelled_at` DATETIME(3) NULL DEFAULT NULL COMMENT '取消时间',
  `paid_at` DATETIME(3) NOT NULL COMMENT '支付成功时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_6_i1` (`order_no`),
  UNIQUE KEY `d3b_6_i2` (`checkout_batch_id`,`factory_id`),
  KEY `d3b_6_i3` (`buyer_user_id`,`order_status`,`created_at`),
  KEY `d3b_6_i4` (`factory_id`,`accept_status`,`accept_deadline_at`),
  KEY `d3b_6_i5` (`checkout_batch_id`),
  KEY `d3b_6_i6` (`source_checklist_id`),
  KEY `d3b_6_i7` (`factory_id`,`created_at`,`id`),
  KEY `d3b_6_i8` (`buyer_user_id`,`created_at`,`id`),
  CONSTRAINT `d3b_6_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c2` CHECK (COALESCE((REGEXP_LIKE(checkout_batch_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c3` CHECK (COALESCE((REGEXP_LIKE(payment_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c4` CHECK (COALESCE((REGEXP_LIKE(source_checklist_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c5` CHECK (COALESCE((REGEXP_LIKE(buyer_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c6` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c7` CHECK (COALESCE((order_status IN ('pending_accept','accepted','manual_rejected','timeout_rejected','user_cancelled','fulfilling','return_checking','completed','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c8` CHECK (COALESCE((accept_status IN ('pending_accept','accepted','manual_rejected','timeout_rejected','cancelled_before_accept')),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c9` CHECK (COALESCE((refund_status IN ('none','processing','partial_refunded','full_refunded','failed')),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c10` CHECK (COALESCE((rent_original_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c11` CHECK (COALESCE((coupon_discount_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c12` CHECK (COALESCE((other_rent_discount_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c13` CHECK (COALESCE((rent_payable_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c14` CHECK (COALESCE((rent_paid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c15` CHECK (COALESCE((rent_refunded_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c16` CHECK (COALESCE((rent_deducted_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c17` CHECK (COALESCE((deposit_original_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c18` CHECK (COALESCE((deposit_waived_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c19` CHECK (COALESCE((deposit_payable_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c20` CHECK (COALESCE((deposit_paid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c21` CHECK (COALESCE((deposit_refunded_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c22` CHECK (COALESCE((deposit_deducted_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c23` CHECK (COALESCE((deposit_status IN ('unpaid','paid','frozen_by_claim','partially_deducted','refunding','refunded','settled')),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c24` CHECK (COALESCE((service_fee_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c25` CHECK (COALESCE((freight_prepaid_paid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c26` CHECK (COALESCE((freight_refunded_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c27` CHECK (COALESCE((total_payable_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c28` CHECK (COALESCE((total_paid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c29` CHECK (COALESCE((total_refunded_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c30` CHECK (COALESCE((total_deducted_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_6_c31` CHECK (COALESCE((version>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `rental_order_delivery` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '配送记录 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `checkout_batch_transport_snapshot_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源运输结算快照 ID',
  `outbound_service_mode` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '去程服务',
  `return_service_mode` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '回程服务',
  `delivery_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '交接配送状态',
  `receiver_name_snapshot` VARCHAR(64) NULL DEFAULT NULL COMMENT '收货人快照',
  `receiver_phone_snapshot` VARCHAR(20) NULL DEFAULT NULL COMMENT '收货手机号快照',
  `region_text_snapshot` VARCHAR(128) NULL DEFAULT NULL COMMENT '所在地区快照',
  `detail_address_snapshot` VARCHAR(255) NULL DEFAULT NULL COMMENT '详细地址快照',
  `address_full_text_snapshot` VARCHAR(512) NULL DEFAULT NULL COMMENT '完整地址快照',
  `transport_requirement_snapshot` VARCHAR(1000) NULL DEFAULT NULL COMMENT '运输要求快照',
  `transport_notice_snapshot` VARCHAR(1000) NULL DEFAULT NULL COMMENT '运输强提示快照',
  `factory_dispatch_at` DATETIME(3) NULL DEFAULT NULL COMMENT '工厂出库/发出时间',
  `receipt_check_start_at` DATETIME(3) NULL DEFAULT NULL COMMENT '收货核验开始时间',
  `receipt_check_deadline_at` DATETIME(3) NULL DEFAULT NULL COMMENT '收货核验截止时间',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_7_i1` (`rental_order_id`),
  KEY `d3b_7_i2` (`factory_id`,`delivery_status`),
  KEY `d3b_7_i3` (`delivery_status`,`receipt_check_deadline_at`),
  CONSTRAINT `d3b_7_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_7_c2` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_7_c3` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_7_c4` CHECK (COALESCE((REGEXP_LIKE(checkout_batch_transport_snapshot_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_7_c5` CHECK (COALESCE((outbound_service_mode IN ('factory_delivery','factory_assisted_call','self_pickup')),FALSE)=TRUE),
  CONSTRAINT `d3b_7_c6` CHECK (COALESCE((return_service_mode IN ('user_return','factory_pickup')),FALSE)=TRUE),
  CONSTRAINT `d3b_7_c7` CHECK (COALESCE((delivery_status IN ('pending_outbound','dispatched','receipt_checking','received_confirmed','cancelled')),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `rental_order_freight_account` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运费账户 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `freight_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运费账户状态',
  `outbound_prepaid_amount_fen` INT NOT NULL COMMENT '去程初始预收',
  `return_prepaid_amount_fen` INT NOT NULL COMMENT '回程初始预收',
  `initial_prepaid_amount_fen` INT NOT NULL COMMENT '初始预收合计',
  `outbound_approved_amount_fen` INT NOT NULL COMMENT '去程核准运费',
  `return_approved_amount_fen` INT NOT NULL COMMENT '回程核准运费',
  `approved_total_amount_fen` INT NOT NULL COMMENT '核准运费合计',
  `online_settle_amount_fen` INT NOT NULL COMMENT '线上工厂运费应结',
  `over_budget_amount_fen` INT NOT NULL COMMENT '超预算金额',
  `refunded_amount_fen` INT NOT NULL COMMENT '已成功退运费累计',
  `refundable_amount_fen` INT NOT NULL COMMENT '当前可退净余额',
  `settled_at` DATETIME(3) NULL DEFAULT NULL COMMENT '运费结清时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_8_i1` (`rental_order_id`),
  KEY `d3b_8_i2` (`factory_id`,`freight_status`,`updated_at`),
  CONSTRAINT `d3b_8_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c2` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c3` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c4` CHECK (COALESCE((freight_status IN ('prepaid','reviewing','refundable','refunding','settled','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c5` CHECK (COALESCE((outbound_prepaid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c6` CHECK (COALESCE((return_prepaid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c7` CHECK (COALESCE((initial_prepaid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c8` CHECK (COALESCE((outbound_approved_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c9` CHECK (COALESCE((return_approved_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c10` CHECK (COALESCE((approved_total_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c11` CHECK (COALESCE((online_settle_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c12` CHECK (COALESCE((over_budget_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c13` CHECK (COALESCE((refunded_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c14` CHECK (COALESCE((refundable_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_8_c15` CHECK (COALESCE((version>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `rental_order_item` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '明细 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `checkout_batch_item_snapshot_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源结算快照明细 ID',
  `source_checklist_item_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '来源清单明细 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `prop_name_snapshot` VARCHAR(128) NOT NULL COMMENT '道具名称快照',
  `image_url_snapshot` VARCHAR(512) NULL DEFAULT NULL COMMENT '主图快照',
  `asset_type_snapshot` VARCHAR(32) NOT NULL COMMENT '资产类型快照',
  `quantity` INT NOT NULL COMMENT '数量',
  `daily_rent_price_fen` INT NOT NULL COMMENT '日租金快照',
  `deposit_unit_amount_fen` INT NOT NULL COMMENT '单件押金快照',
  `rent_amount_fen` INT NOT NULL COMMENT '明细租金',
  `coupon_discount_amount_fen` INT NOT NULL COMMENT '明细优惠券分摊',
  `rent_cash_paid_amount_fen` INT NOT NULL COMMENT '明细租金现金实付',
  `deposit_original_amount_fen` INT NOT NULL COMMENT '明细原始押金',
  `deposit_waived_amount_fen` INT NOT NULL COMMENT '明细信用减免押金',
  `deposit_payable_amount_fen` INT NOT NULL COMMENT '明细应付押金',
  `transport_requirement_snapshot` VARCHAR(1000) NULL DEFAULT NULL COMMENT '运输要求快照',
  `item_snapshot_json` JSON NULL DEFAULT NULL COMMENT '明细快照 JSON',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_9_i1` (`checkout_batch_item_snapshot_id`),
  CONSTRAINT `d3b_9_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c2` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c3` CHECK (COALESCE((REGEXP_LIKE(checkout_batch_item_snapshot_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c4` CHECK (COALESCE(((source_checklist_item_id IS NULL OR REGEXP_LIKE(source_checklist_item_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c5` CHECK (COALESCE((REGEXP_LIKE(sku_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c6` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c7` CHECK (COALESCE((quantity>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c8` CHECK (COALESCE((daily_rent_price_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c9` CHECK (COALESCE((deposit_unit_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c10` CHECK (COALESCE((rent_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c11` CHECK (COALESCE((coupon_discount_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c12` CHECK (COALESCE((rent_cash_paid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c13` CHECK (COALESCE((deposit_original_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c14` CHECK (COALESCE((deposit_waived_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_9_c15` CHECK (COALESCE((deposit_payable_amount_fen>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_allocation` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分摊 ID',
  `payment_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '支付主单 ID',
  `payment_channel_suborder_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原渠道子单 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁订单 ID',
  `custom_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '定制订单 ID',
  `rent_allocated_amount_fen` INT NOT NULL COMMENT '租金现金实付',
  `coupon_discount_allocated_fen` INT NOT NULL COMMENT '优惠券分摊',
  `deposit_allocated_amount_fen` INT NOT NULL COMMENT '押金现金实付',
  `service_fee_allocated_fen` INT NOT NULL COMMENT '服务费实付',
  `freight_prepaid_allocated_fen` INT NOT NULL COMMENT '运费现金实付',
  `compensation_allocated_amount_fen` INT NOT NULL COMMENT '赔付补缴现金实付',
  `custom_goods_allocated_amount_fen` INT NOT NULL COMMENT '定制商品现金实付',
  `total_allocated_amount_fen` INT NOT NULL COMMENT '现金分摊合计',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_10_i1` (`payment_channel_suborder_id`),
  CONSTRAINT `d3b_10_c1` CHECK (COALESCE(((rental_order_id IS NOT NULL)+(custom_order_id IS NOT NULL)=1 AND total_allocated_amount_fen=rent_allocated_amount_fen+deposit_allocated_amount_fen+service_fee_allocated_fen+freight_prepaid_allocated_fen+compensation_allocated_amount_fen+custom_goods_allocated_amount_fen),FALSE)=TRUE),
  CONSTRAINT `d3b_10_c2` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_10_c3` CHECK (COALESCE((REGEXP_LIKE(payment_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_10_c4` CHECK (COALESCE((REGEXP_LIKE(payment_channel_suborder_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_10_c5` CHECK (COALESCE(((rental_order_id IS NULL OR REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_10_c6` CHECK (COALESCE(((custom_order_id IS NULL OR REGEXP_LIKE(custom_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_10_c7` CHECK (COALESCE((rent_allocated_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_10_c8` CHECK (COALESCE((coupon_discount_allocated_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_10_c9` CHECK (COALESCE((deposit_allocated_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_10_c10` CHECK (COALESCE((service_fee_allocated_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_10_c11` CHECK (COALESCE((freight_prepaid_allocated_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_10_c12` CHECK (COALESCE((compensation_allocated_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_10_c13` CHECK (COALESCE((custom_goods_allocated_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_10_c14` CHECK (COALESCE((total_allocated_amount_fen>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_price_adjustment` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '金额调整 ID',
  `checkout_batch_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算批次 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁订单 ID',
  `adjustment_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调整类型',
  `fund_component` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资金科目',
  `adjustment_direction` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调整方向',
  `amount_fen` INT NOT NULL COMMENT '调整金额',
  `source_type` VARCHAR(32) NOT NULL COMMENT '来源类型',
  `source_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '来源 ID',
  `adjustment_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调整状态',
  `snapshot_json` JSON NULL DEFAULT NULL COMMENT '来源快照 JSON',
  `remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '优惠归属工厂',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_11_i1` (`checkout_batch_id`,`factory_id`,`source_type`,`source_id`),
  CONSTRAINT `d3b_11_c1` CHECK (COALESCE((amount_fen>0),FALSE)=TRUE),
  CONSTRAINT `d3b_11_c2` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_11_c3` CHECK (COALESCE((REGEXP_LIKE(checkout_batch_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_11_c4` CHECK (COALESCE(((rental_order_id IS NULL OR REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_11_c5` CHECK (COALESCE((adjustment_type IN ('credit_waiver','coupon','promotion','manual_adjustment','system_adjustment')),FALSE)=TRUE),
  CONSTRAINT `d3b_11_c6` CHECK (COALESCE((fund_component IN ('rent','deposit','service_fee','freight','total')),FALSE)=TRUE),
  CONSTRAINT `d3b_11_c7` CHECK (COALESCE((adjustment_direction IN ('discount','increase')),FALSE)=TRUE),
  CONSTRAINT `d3b_11_c8` CHECK (COALESCE((amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_11_c9` CHECK (COALESCE(((source_id IS NULL OR REGEXP_LIKE(source_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_11_c10` CHECK (COALESCE((adjustment_status IN ('applied','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d3b_11_c11` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `refund_order` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '退款单 ID',
  `refund_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '退款单号',
  `request_idempotency_key` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '业务幂等键',
  `biz_order_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '退款业务归属类型',
  `biz_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '退款业务归属ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁订单 ID',
  `payment_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原支付主单 ID',
  `payment_channel_suborder_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原渠道子单 ID',
  `refund_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '退款类型',
  `refund_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '退款状态',
  `pay_channel` VARCHAR(32) NOT NULL COMMENT '退款渠道',
  `channel_transaction_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道支付订单号',
  `channel_out_trade_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道商户订单号',
  `channel_out_refund_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道商户退款单号',
  `channel_refund_no` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '渠道退款号',
  `rent_refund_amount_fen` INT NOT NULL COMMENT '退租金金额',
  `deposit_refund_amount_fen` INT NOT NULL COMMENT '退押金金额',
  `service_fee_refund_fen` INT NOT NULL COMMENT '退服务费金额',
  `freight_refund_amount_fen` INT NOT NULL COMMENT '退运费金额',
  `compensation_refund_amount_fen` INT NOT NULL COMMENT '退补缴赔付',
  `custom_goods_refund_amount_fen` INT NOT NULL COMMENT '退定制商品金额',
  `refund_amount_fen` INT NOT NULL COMMENT '退款合计',
  `refund_reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '退款原因',
  `requested_by` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '真实操作人或固定系统用户',
  `requested_at` DATETIME(3) NOT NULL COMMENT '发起时间',
  `success_at` DATETIME(3) NULL DEFAULT NULL COMMENT '成功时间',
  `failed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '失败时间',
  `fail_reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '失败原因',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  `next_attempt_at` DATETIME(3) NULL DEFAULT NULL COMMENT '下次恢复检查时间',
  `last_checked_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最后渠道核对时间',
  `attempt_count` INT NOT NULL DEFAULT 0 COMMENT '渠道执行次数',
  `last_error_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最近稳定渠道错误码',
  `version` INT NOT NULL DEFAULT 0 COMMENT '条件更新版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_12_i1` (`request_idempotency_key`),
  UNIQUE KEY `d3b_12_i2` (`refund_no`),
  UNIQUE KEY `d3b_12_i3` (`pay_channel`,`channel_out_refund_no`),
  KEY `d3b_12_i4` (`payment_channel_suborder_id`,`refund_status`),
  KEY `d3b_12_i5` (`next_attempt_at`,`id`),
  KEY `d3b_12_i6` (`biz_order_type`,`biz_order_id`,`refund_status`),
  CONSTRAINT `d3b_12_c1` CHECK (COALESCE((refund_amount_fen>0 AND refund_amount_fen=rent_refund_amount_fen+deposit_refund_amount_fen+service_fee_refund_fen+freight_refund_amount_fen+compensation_refund_amount_fen+custom_goods_refund_amount_fen),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c2` CHECK (COALESCE(((biz_order_type='rental_order' AND rental_order_id=biz_order_id) OR (biz_order_type IN ('checkout_batch','custom_order') AND rental_order_id IS NULL)),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c3` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c4` CHECK (COALESCE((biz_order_type IN ('rental_order','custom_order','checkout_batch')),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c5` CHECK (COALESCE((REGEXP_LIKE(biz_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c6` CHECK (COALESCE(((rental_order_id IS NULL OR REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c7` CHECK (COALESCE((REGEXP_LIKE(payment_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c8` CHECK (COALESCE((REGEXP_LIKE(payment_channel_suborder_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c9` CHECK (COALESCE((refund_type IN ('user_cancel_before_accept','user_cancel_rule','factory_manual_reject','factory_timeout_reject','extension_rejected','deposit_release','freight_pool_release','freight_cancel_release','arbitration_refund','customer_service_refund','platform_adjustment','unformed_batch_refund')),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c10` CHECK (COALESCE((refund_status IN ('pending','processing','success','failed','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c11` CHECK (COALESCE((rent_refund_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c12` CHECK (COALESCE((deposit_refund_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c13` CHECK (COALESCE((service_fee_refund_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c14` CHECK (COALESCE((freight_refund_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c15` CHECK (COALESCE((compensation_refund_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c16` CHECK (COALESCE((custom_goods_refund_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c17` CHECK (COALESCE((refund_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c18` CHECK (COALESCE((REGEXP_LIKE(requested_by,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c19` CHECK (COALESCE((attempt_count>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_12_c20` CHECK (COALESCE((version>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_deduction` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '扣款 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `deduction_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '扣款类型',
  `fund_component` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资金科目',
  `deduction_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '扣款状态',
  `amount_fen` INT NOT NULL COMMENT '扣款金额',
  `calculation_base_amount_fen` INT NULL DEFAULT NULL COMMENT '扣款计算基数',
  `calculation_rate_bps` INT NULL DEFAULT NULL COMMENT '扣款比例 BP',
  `calculation_snapshot_json` JSON NULL DEFAULT NULL COMMENT '扣款计算快照',
  `source_type` VARCHAR(32) NOT NULL COMMENT '来源类型',
  `source_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '来源 ID',
  `reason` VARCHAR(255) NOT NULL COMMENT '扣款原因',
  `evidence_summary` VARCHAR(512) NULL DEFAULT NULL COMMENT '证据摘要',
  `created_by` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '真实操作人或固定系统用户',
  `confirmed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '确认时间',
  `cancelled_at` DATETIME(3) NULL DEFAULT NULL COMMENT '取消时间',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_13_i1` (`rental_order_id`,`deduction_type`,`source_type`,`source_id`),
  CONSTRAINT `d3b_13_c1` CHECK (COALESCE((amount_fen>0),FALSE)=TRUE),
  CONSTRAINT `d3b_13_c2` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_13_c3` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_13_c4` CHECK (COALESCE((deduction_type IN ('cancellation_fee','damage','lost','overdue','cleaning','claim','arbitration','platform_adjustment')),FALSE)=TRUE),
  CONSTRAINT `d3b_13_c5` CHECK (COALESCE((fund_component IN ('rent','deposit','service_fee')),FALSE)=TRUE),
  CONSTRAINT `d3b_13_c6` CHECK (COALESCE((deduction_status IN ('pending','confirmed','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d3b_13_c7` CHECK (COALESCE((amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_13_c8` CHECK (COALESCE((calculation_base_amount_fen IS NULL OR calculation_base_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_13_c9` CHECK (COALESCE(((source_id IS NULL OR REGEXP_LIKE(source_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_13_c10` CHECK (COALESCE((REGEXP_LIKE(created_by,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_compensation_receivable` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '待缴赔付款 ID',
  `receivable_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '待缴赔付款编号',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源租赁订单 ID',
  `buyer_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '待缴用户 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '收款工厂 ID',
  `source_type` VARCHAR(32) NOT NULL COMMENT '生效来源类型',
  `source_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '生效来源 ID',
  `confirmed_loss_amount_fen` INT NOT NULL COMMENT '已生效损失金额',
  `available_deposit_amount_fen` INT NOT NULL COMMENT '生效时剩余可用押金',
  `deposit_deducted_amount_fen` INT NOT NULL COMMENT '本次实际扣押金',
  `receivable_amount_fen` INT NOT NULL COMMENT '应缴赔付金额',
  `paid_amount_fen` INT NOT NULL COMMENT '已支付金额',
  `waived_amount_fen` INT NOT NULL COMMENT '已核销减免金额',
  `receivable_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '待缴状态',
  `current_payment_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '当前支付单 ID',
  `effective_at` DATETIME(3) NOT NULL COMMENT '赔付生效时间',
  `paid_at` DATETIME(3) NULL DEFAULT NULL COMMENT '缴清时间',
  `waived_at` DATETIME(3) NULL DEFAULT NULL COMMENT '核销减免时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_14_i1` (`receivable_no`),
  UNIQUE KEY `d3b_14_i2` (`source_type`,`source_id`),
  KEY `d3b_14_i3` (`buyer_user_id`,`receivable_status`,`created_at`),
  KEY `d3b_14_i4` (`rental_order_id`,`created_at`),
  CONSTRAINT `d3b_14_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_14_c2` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_14_c3` CHECK (COALESCE((REGEXP_LIKE(buyer_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_14_c4` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_14_c5` CHECK (COALESCE((REGEXP_LIKE(source_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_14_c6` CHECK (COALESCE((confirmed_loss_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_14_c7` CHECK (COALESCE((available_deposit_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_14_c8` CHECK (COALESCE((deposit_deducted_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_14_c9` CHECK (COALESCE((receivable_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_14_c10` CHECK (COALESCE((paid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_14_c11` CHECK (COALESCE((waived_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_14_c12` CHECK (COALESCE((receivable_status IN ('pending','paying','paid','waived','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d3b_14_c13` CHECK (COALESCE(((current_payment_order_id IS NULL OR REGEXP_LIKE(current_payment_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_14_c14` CHECK (COALESCE((version>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `payment_channel_notify_log` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '通知日志 ID',
  `notify_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '通知类型',
  `pay_channel` VARCHAR(32) NOT NULL COMMENT '支付渠道',
  `channel_event_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道通知事件 ID',
  `payment_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '支付单 ID',
  `refund_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '退款单 ID',
  `wechat_profit_sharing_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '分账执行 ID',
  `channel_out_trade_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '渠道商户订单号',
  `channel_transaction_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '渠道支付订单号',
  `channel_out_refund_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '渠道商户退款单号',
  `channel_refund_no` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '渠道退款单号',
  `event_type` VARCHAR(64) NULL DEFAULT NULL COMMENT '事件类型',
  `signature_valid` TINYINT NOT NULL COMMENT '验签是否通过',
  `process_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '处理状态',
  `raw_notify_json` JSON NULL DEFAULT NULL COMMENT '原始通知 JSON',
  `received_at` DATETIME(3) NOT NULL COMMENT '接收时间',
  `processed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '处理时间',
  `process_message` VARCHAR(255) NULL DEFAULT NULL COMMENT '处理说明',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_15_i1` (`pay_channel`,`channel_event_id`),
  KEY `d3b_15_i2` (`payment_order_id`,`process_status`,`received_at`),
  KEY `d3b_15_i3` (`refund_order_id`,`process_status`,`received_at`),
  CONSTRAINT `d3b_15_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_15_c2` CHECK (COALESCE((notify_type IN ('payment','refund','profit_sharing')),FALSE)=TRUE),
  CONSTRAINT `d3b_15_c3` CHECK (COALESCE(((payment_order_id IS NULL OR REGEXP_LIKE(payment_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_15_c4` CHECK (COALESCE(((refund_order_id IS NULL OR REGEXP_LIKE(refund_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_15_c5` CHECK (COALESCE(((wechat_profit_sharing_order_id IS NULL OR REGEXP_LIKE(wechat_profit_sharing_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_15_c6` CHECK (COALESCE((process_status IN ('received','processed','ignored','failed')),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `factory_wechat_merchant_account` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂微信账户 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `channel_provider` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '渠道提供方',
  `applyment_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '微信进件申请单号',
  `sub_mchid` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '微信二级商户号',
  `subject_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主体类型',
  `subject_name_snapshot` VARCHAR(128) NOT NULL COMMENT '收款主体名称快照',
  `account_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '账户状态',
  `profit_sharing_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分账能力状态',
  `settlement_account_masked` VARCHAR(128) NULL DEFAULT NULL COMMENT '结算账户脱敏摘要',
  `channel_status_snapshot_json` JSON NULL DEFAULT NULL COMMENT '渠道状态快照',
  `verified_at` DATETIME(3) NULL DEFAULT NULL COMMENT '验证通过时间',
  `last_synced_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近渠道同步时间',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '并发版本',
  `onboarding_request_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '平台进件业务编号',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_16_i1` (`factory_id`),
  UNIQUE KEY `d3b_16_i2` (`sub_mchid`),
  KEY `d3b_16_i3` (`account_status`,`profit_sharing_status`,`updated_at`),
  UNIQUE KEY `d3b_16_i4` (`onboarding_request_no`),
  CONSTRAINT `d3b_16_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_16_c2` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_16_c3` CHECK (COALESCE((channel_provider IN ('wechat_platform_collection')),FALSE)=TRUE),
  CONSTRAINT `d3b_16_c4` CHECK (COALESCE((subject_type IN ('SUBJECT_TYPE_ENTERPRISE','SUBJECT_TYPE_INDIVIDUAL')),FALSE)=TRUE),
  CONSTRAINT `d3b_16_c5` CHECK (COALESCE((account_status IN ('not_applied','reviewing','need_action','active','rejected','frozen','closed')),FALSE)=TRUE),
  CONSTRAINT `d3b_16_c6` CHECK (COALESCE((profit_sharing_status IN ('unavailable','pending','active','restricted')),FALSE)=TRUE),
  CONSTRAINT `d3b_16_c7` CHECK (COALESCE((version>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `transport_vehicle_tier` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '车型档位 ID',
  `tier_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '车型档位编码',
  `tier_name` VARCHAR(64) NOT NULL COMMENT '车型档位名称',
  `cargo_length_mm` INT NOT NULL COMMENT '车厢有效长度毫米',
  `cargo_width_mm` INT NOT NULL COMMENT '车厢有效宽度毫米',
  `cargo_height_mm` INT NOT NULL COMMENT '车厢有效高度毫米',
  `effective_volume_cm3` BIGINT NOT NULL COMMENT '有效容积立方厘米',
  `safe_payload_gram` BIGINT NOT NULL COMMENT '安全载重克',
  `sort_no` INT NOT NULL COMMENT '档位排序',
  `tier_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '档位状态',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '维护版本',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_17_i1` (`tier_code`),
  KEY `d3b_17_i2` (`tier_status`,`sort_no`),
  CONSTRAINT `d3b_17_c1` CHECK (COALESCE((cargo_length_mm>0 AND cargo_width_mm>0 AND cargo_height_mm>0 AND effective_volume_cm3>0 AND safe_payload_gram>0),FALSE)=TRUE),
  CONSTRAINT `d3b_17_c2` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_17_c3` CHECK (COALESCE((tier_status IN ('enabled','disabled')),FALSE)=TRUE),
  CONSTRAINT `d3b_17_c4` CHECK (COALESCE((version>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `transport_pricing_rule` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '价格规则 ID',
  `rule_version` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规则版本',
  `city_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '城市编码',
  `vehicle_tier_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '车型档位 ID',
  `distance_min_meter` INT NOT NULL COMMENT '距离下限米',
  `distance_max_meter` INT NULL DEFAULT NULL COMMENT '距离上限米',
  `guide_min_amount_fen` INT NOT NULL COMMENT '指导价下限',
  `guide_max_amount_fen` INT NOT NULL COMMENT '指导价上限',
  `own_vehicle_settle_amount_fen` INT NOT NULL COMMENT '自有车辆标准结算价',
  `extra_km_amount_fen` INT NULL DEFAULT NULL COMMENT '超区间每公里价格',
  `allowed_extra_fee_json` JSON NULL DEFAULT NULL COMMENT '可核销附加费规则',
  `rule_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规则状态',
  `effective_at` DATETIME(3) NOT NULL COMMENT '生效时间',
  `expire_at` DATETIME(3) NULL DEFAULT NULL COMMENT '失效时间',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '维护版本',
  `included_distance_meter` INT NOT NULL COMMENT '基础价包含距离米',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_18_i1` (`rule_version`,`city_code`,`vehicle_tier_id`,`distance_min_meter`,`distance_max_meter`),
  KEY `d3b_18_i2` (`city_code`,`vehicle_tier_id`,`rule_status`,`effective_at`),
  CONSTRAINT `d3b_18_c1` CHECK (COALESCE(((distance_max_meter IS NULL OR distance_min_meter<distance_max_meter) AND (expire_at IS NULL OR effective_at<expire_at) AND guide_min_amount_fen<=guide_max_amount_fen),FALSE)=TRUE),
  CONSTRAINT `d3b_18_c2` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_18_c3` CHECK (COALESCE((REGEXP_LIKE(vehicle_tier_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_18_c4` CHECK (COALESCE((distance_min_meter>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_18_c5` CHECK (COALESCE((distance_max_meter IS NULL OR distance_max_meter>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_18_c6` CHECK (COALESCE((guide_min_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_18_c7` CHECK (COALESCE((guide_max_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_18_c8` CHECK (COALESCE((own_vehicle_settle_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_18_c9` CHECK (COALESCE((extra_km_amount_fen IS NULL OR extra_km_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_18_c10` CHECK (COALESCE((rule_status IN ('draft','active','expired')),FALSE)=TRUE),
  CONSTRAINT `d3b_18_c11` CHECK (COALESCE((version>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_18_c12` CHECK (COALESCE((included_distance_meter>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_transport_leg` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运输段 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `rental_order_delivery_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '订单配送摘要 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `leg_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运输段类型',
  `service_mode` VARCHAR(32) NOT NULL COMMENT '服务方式',
  `charge_mode` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '收费方式',
  `leg_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '运输段状态',
  `from_address_snapshot` VARCHAR(512) NULL DEFAULT NULL COMMENT '起点地址快照',
  `from_longitude` DECIMAL(10,6) NULL DEFAULT NULL COMMENT '起点经度',
  `from_latitude` DECIMAL(10,6) NULL DEFAULT NULL COMMENT '起点纬度',
  `to_address_snapshot` VARCHAR(512) NULL DEFAULT NULL COMMENT '终点地址快照',
  `to_longitude` DECIMAL(10,6) NULL DEFAULT NULL COMMENT '终点经度',
  `to_latitude` DECIMAL(10,6) NULL DEFAULT NULL COMMENT '终点纬度',
  `driving_distance_meter` INT NULL DEFAULT NULL COMMENT '驾车距离米',
  `map_provider` VARCHAR(32) NULL DEFAULT NULL COMMENT '地图提供方',
  `map_calculated_at` DATETIME(3) NULL DEFAULT NULL COMMENT '距离计算时间',
  `total_volume_cm3` BIGINT NULL DEFAULT NULL COMMENT '订单包装估算总体积',
  `total_weight_gram` BIGINT NULL DEFAULT NULL COMMENT '订单总重量克',
  `max_item_length_mm` INT NULL DEFAULT NULL COMMENT '最大单件长度毫米',
  `max_item_width_mm` INT NULL DEFAULT NULL COMMENT '最大单件宽度毫米',
  `max_item_height_mm` INT NULL DEFAULT NULL COMMENT '最大单件高度毫米',
  `matched_vehicle_tier_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '系统匹配车型档位 ID',
  `pricing_rule_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '命中价格规则 ID',
  `pricing_snapshot_json` JSON NULL DEFAULT NULL COMMENT '车型距离计价快照',
  `guide_min_amount_fen` INT NOT NULL COMMENT '本段指导价下限',
  `guide_max_amount_fen` INT NOT NULL COMMENT '本段指导价上限',
  `prepaid_allocated_amount_fen` INT NOT NULL COMMENT '本段分配预收金额',
  `execution_source` VARCHAR(32) NULL DEFAULT NULL COMMENT '实际执行来源',
  `approved_base_amount_fen` INT NOT NULL COMMENT '核准基础运输费',
  `approved_extra_amount_fen` INT NOT NULL COMMENT '核准附加费',
  `approved_total_amount_fen` INT NOT NULL COMMENT '本段核准总运费',
  `dispatched_at` DATETIME(3) NULL DEFAULT NULL COMMENT '发出/开始取回时间',
  `completed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '本段完成时间',
  `cancelled_at` DATETIME(3) NULL DEFAULT NULL COMMENT '取消时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_19_i1` (`rental_order_id`,`leg_type`),
  KEY `d3b_19_i2` (`factory_id`,`leg_status`,`updated_at`),
  KEY `d3b_19_i3` (`leg_status`,`dispatched_at`),
  CONSTRAINT `d3b_19_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c2` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c3` CHECK (COALESCE((REGEXP_LIKE(rental_order_delivery_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c4` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c5` CHECK (COALESCE((leg_type IN ('outbound','return')),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c6` CHECK (COALESCE((charge_mode IN ('freight_pool','cod','none')),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c7` CHECK (COALESCE((leg_status IN ('selected','ready','dispatched','completed','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c8` CHECK (COALESCE((driving_distance_meter IS NULL OR driving_distance_meter>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c9` CHECK (COALESCE(((matched_vehicle_tier_id IS NULL OR REGEXP_LIKE(matched_vehicle_tier_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c10` CHECK (COALESCE(((pricing_rule_id IS NULL OR REGEXP_LIKE(pricing_rule_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c11` CHECK (COALESCE((guide_min_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c12` CHECK (COALESCE((guide_max_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c13` CHECK (COALESCE((prepaid_allocated_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c14` CHECK (COALESCE((approved_base_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c15` CHECK (COALESCE((approved_extra_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c16` CHECK (COALESCE((approved_total_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_19_c17` CHECK (COALESCE((version>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coupon_scope_target` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '范围目标 ID',
  `coupon_template_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '券模板 ID',
  `scope_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '范围类型',
  `target_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标业务 ID',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_20_i1` (`coupon_template_id`,`scope_type`,`target_id`),
  KEY `d3b_20_i2` (`scope_type`,`target_id`,`coupon_template_id`),
  CONSTRAINT `d3b_20_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_20_c2` CHECK (COALESCE((REGEXP_LIKE(coupon_template_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_20_c3` CHECK (COALESCE((scope_type IN ('factory')),FALSE)=TRUE),
  CONSTRAINT `d3b_20_c4` CHECK (COALESCE((REGEXP_LIKE(target_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_coupon` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户券 ID',
  `user_coupon_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户券编号',
  `template_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '模板 ID',
  `user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户 ID',
  `issue_source_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发放来源类型',
  `issue_source_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发放来源 ID',
  `issue_request_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '发放幂等键',
  `coupon_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户券状态',
  `coupon_type_snapshot` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '优惠类型快照',
  `face_amount_fen_snapshot` INT NULL DEFAULT NULL COMMENT '券面金额快照',
  `discount_rate_bps_snapshot` INT NULL DEFAULT NULL COMMENT '租金减免比例快照',
  `threshold_rent_amount_fen_snapshot` INT NOT NULL COMMENT '使用门槛快照',
  `cost_bearer_snapshot` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '成本承担快照',
  `valid_from` DATETIME(3) NOT NULL COMMENT '生效时间',
  `valid_until` DATETIME(3) NOT NULL COMMENT '到期时间',
  `locked_until` DATETIME(3) NULL DEFAULT NULL COMMENT '锁定截止时间',
  `used_at` DATETIME(3) NULL DEFAULT NULL COMMENT '使用成功时间',
  `returned_at` DATETIME(3) NULL DEFAULT NULL COMMENT '返券时间',
  `invalid_reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '作废原因',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_21_i1` (`user_coupon_no`),
  UNIQUE KEY `d3b_21_i2` (`issue_request_id`),
  KEY `d3b_21_i3` (`user_id`,`coupon_status`,`valid_until`),
  KEY `d3b_21_i4` (`coupon_status`,`valid_until`),
  CONSTRAINT `d3b_21_c1` CHECK (COALESCE((valid_from<valid_until AND ((coupon_type_snapshot='fixed_amount' AND face_amount_fen_snapshot>0 AND discount_rate_bps_snapshot IS NULL) OR (coupon_type_snapshot='percentage' AND face_amount_fen_snapshot IS NULL AND discount_rate_bps_snapshot BETWEEN 1 AND 10000))),FALSE)=TRUE),
  CONSTRAINT `d3b_21_c2` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_21_c3` CHECK (COALESCE((REGEXP_LIKE(template_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_21_c4` CHECK (COALESCE((REGEXP_LIKE(user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_21_c5` CHECK (COALESCE((issue_source_type IN ('event_reward','admin_compensation','manual_operation','conditional_claim')),FALSE)=TRUE),
  CONSTRAINT `d3b_21_c6` CHECK (COALESCE((REGEXP_LIKE(issue_source_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_21_c7` CHECK (COALESCE((coupon_status IN ('available','locked','used','returned','expired','invalid')),FALSE)=TRUE),
  CONSTRAINT `d3b_21_c8` CHECK (COALESCE((coupon_type_snapshot IN ('fixed_amount','percentage')),FALSE)=TRUE),
  CONSTRAINT `d3b_21_c9` CHECK (COALESCE((face_amount_fen_snapshot IS NULL OR face_amount_fen_snapshot>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_21_c10` CHECK (COALESCE((threshold_rent_amount_fen_snapshot>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_21_c11` CHECK (COALESCE((cost_bearer_snapshot IN ('shared_by_settlement_ratio')),FALSE)=TRUE),
  CONSTRAINT `d3b_21_c12` CHECK (COALESCE((version>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coupon_usage` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '使用记录 ID',
  `usage_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '使用记录编号',
  `user_coupon_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户券 ID',
  `user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户 ID',
  `checkout_batch_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算批次 ID',
  `payment_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '支付单 ID',
  `eligible_rent_amount_fen` INT NOT NULL COMMENT '总可优惠租金',
  `discount_amount_fen` INT NOT NULL COMMENT '实际优惠金额',
  `cost_bearer_snapshot` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '成本承担快照',
  `usage_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '使用状态',
  `lock_expire_at` DATETIME(3) NOT NULL COMMENT '锁定到期时间',
  `used_at` DATETIME(3) NULL DEFAULT NULL COMMENT '核销时间',
  `released_at` DATETIME(3) NULL DEFAULT NULL COMMENT '释放时间',
  `return_confirmed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '全退返券确认时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_22_i1` (`usage_no`),
  UNIQUE KEY `d3b_22_i2` (`checkout_batch_id`),
  KEY `d3b_22_i3` (`user_coupon_id`,`usage_status`),
  KEY `d3b_22_i4` (`payment_order_id`),
  KEY `d3b_22_i5` (`usage_status`,`lock_expire_at`),
  CONSTRAINT `d3b_22_c1` CHECK (COALESCE((discount_amount_fen>0 AND discount_amount_fen<=eligible_rent_amount_fen),FALSE)=TRUE),
  CONSTRAINT `d3b_22_c2` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_22_c3` CHECK (COALESCE((REGEXP_LIKE(user_coupon_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_22_c4` CHECK (COALESCE((REGEXP_LIKE(user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_22_c5` CHECK (COALESCE((REGEXP_LIKE(checkout_batch_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_22_c6` CHECK (COALESCE(((payment_order_id IS NULL OR REGEXP_LIKE(payment_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_22_c7` CHECK (COALESCE((eligible_rent_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_22_c8` CHECK (COALESCE((discount_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_22_c9` CHECK (COALESCE((cost_bearer_snapshot IN ('shared_by_settlement_ratio')),FALSE)=TRUE),
  CONSTRAINT `d3b_22_c10` CHECK (COALESCE((usage_status IN ('locked','used','released','returned','invalid')),FALSE)=TRUE),
  CONSTRAINT `d3b_22_c11` CHECK (COALESCE((version>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coupon_allocation` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分摊 ID',
  `coupon_usage_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '使用记录 ID',
  `checkout_batch_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算批次 ID',
  `checkout_batch_item_snapshot_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算明细快照 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁订单 ID',
  `rental_order_item_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁订单明细 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `eligible_rent_amount_fen` INT NOT NULL COMMENT '本明细可优惠租金',
  `allocated_discount_amount_fen` INT NOT NULL COMMENT '本明细优惠金额',
  `cost_bearer_snapshot` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '成本承担快照',
  `allocation_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分摊状态',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_23_i1` (`coupon_usage_id`,`checkout_batch_item_snapshot_id`),
  KEY `d3b_23_i2` (`rental_order_id`,`allocation_status`),
  KEY `d3b_23_i3` (`rental_order_item_id`),
  KEY `d3b_23_i4` (`factory_id`,`allocation_status`),
  CONSTRAINT `d3b_23_c1` CHECK (COALESCE((allocated_discount_amount_fen<=eligible_rent_amount_fen),FALSE)=TRUE),
  CONSTRAINT `d3b_23_c2` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_23_c3` CHECK (COALESCE((REGEXP_LIKE(coupon_usage_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_23_c4` CHECK (COALESCE((REGEXP_LIKE(checkout_batch_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_23_c5` CHECK (COALESCE((REGEXP_LIKE(checkout_batch_item_snapshot_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_23_c6` CHECK (COALESCE(((rental_order_id IS NULL OR REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_23_c7` CHECK (COALESCE(((rental_order_item_id IS NULL OR REGEXP_LIKE(rental_order_item_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_23_c8` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_23_c9` CHECK (COALESCE((REGEXP_LIKE(sku_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_23_c10` CHECK (COALESCE((eligible_rent_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_23_c11` CHECK (COALESCE((allocated_discount_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_23_c12` CHECK (COALESCE((cost_bearer_snapshot IN ('shared_by_settlement_ratio')),FALSE)=TRUE),
  CONSTRAINT `d3b_23_c13` CHECK (COALESCE((allocation_status IN ('locked','effective','voided','returned')),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `coupon_operation_log` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '流水 ID',
  `operation_request_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作幂等键',
  `user_coupon_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '用户券 ID',
  `coupon_usage_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '使用记录 ID',
  `operation_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作类型',
  `from_status` VARCHAR(16) NULL DEFAULT NULL COMMENT '原状态',
  `to_status` VARCHAR(16) NOT NULL COMMENT '新状态',
  `operator_type` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作方类型',
  `operator_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '真实操作人或固定系统用户',
  `biz_type` VARCHAR(32) NULL DEFAULT NULL COMMENT '来源业务类型',
  `biz_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '来源业务 ID',
  `reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '操作原因',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_24_i1` (`operation_request_id`),
  KEY `d3b_24_i2` (`user_coupon_id`,`created_at`),
  KEY `d3b_24_i3` (`biz_type`,`biz_id`),
  CONSTRAINT `d3b_24_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_24_c2` CHECK (COALESCE((REGEXP_LIKE(user_coupon_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_24_c3` CHECK (COALESCE(((coupon_usage_id IS NULL OR REGEXP_LIKE(coupon_usage_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_24_c4` CHECK (COALESCE((operation_type IN ('issued','locked','used','released','returned','expired','invalidated')),FALSE)=TRUE),
  CONSTRAINT `d3b_24_c5` CHECK (COALESCE((operator_type IN ('system','user','admin')),FALSE)=TRUE),
  CONSTRAINT `d3b_24_c6` CHECK (COALESCE((REGEXP_LIKE(operator_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_24_c7` CHECK (COALESCE(((biz_id IS NULL OR REGEXP_LIKE(biz_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `trading_operation_record` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '回执ID',
  `actor_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人',
  `request_id` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '客户端UUID v4',
  `operation_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作编码',
  `request_hash` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '规范请求摘要',
  `resource_type` VARCHAR(32) NOT NULL COMMENT '资源类型',
  `resource_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资源ID',
  `result_json` JSON NOT NULL COMMENT '原结果白名单快照',
  `created_at` DATETIME(3) NOT NULL COMMENT '成功提交时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_25_i1` (`actor_user_id`,`request_id`),
  KEY `d3b_25_i2` (`resource_type`,`resource_id`),
  CONSTRAINT `d3b_25_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_25_c2` CHECK (COALESCE((REGEXP_LIKE(actor_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_25_c3` CHECK (COALESCE((REGEXP_LIKE(resource_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_reservation_history` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '占用 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `sn_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'SN ID',
  `quantity` INT NOT NULL COMMENT '占用数量',
  `checkout_batch_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '来源结算批次 ID',
  `checkout_batch_item_snapshot_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '来源结算明细快照 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁订单 ID',
  `rental_order_item_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁订单明细 ID',
  `source_type` VARCHAR(32) NULL DEFAULT NULL COMMENT '通用来源类型',
  `source_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '通用来源 ID',
  `rental_start_date` DATE NOT NULL COMMENT '租期开始日期',
  `rental_end_date` DATE NOT NULL COMMENT '租期结束日期',
  `occupy_start_date` DATE NOT NULL COMMENT '实际占用开始日期',
  `occupy_end_date` DATE NOT NULL COMMENT '实际占用结束日期',
  `reservation_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '占用状态',
  `expires_at` DATETIME(3) NULL DEFAULT NULL COMMENT '过期时间',
  `idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '占用幂等键',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  `origin_reservation_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原占用ID',
  `archive_batch_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '归档批次',
  `archived_at` DATETIME(3) NOT NULL COMMENT '归档时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d3b_26_i1` (`origin_reservation_id`),
  UNIQUE KEY `d3b_26_i2` (`idempotency_key`),
  KEY `d3b_26_i3` (`rental_order_id`,`id`),
  KEY `d3b_26_i4` (`sku_id`,`occupy_start_date`,`occupy_end_date`),
  CONSTRAINT `d3b_26_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_26_c2` CHECK (COALESCE((REGEXP_LIKE(sku_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d3b_26_c3` CHECK (COALESCE(((sn_id IS NULL OR REGEXP_LIKE(sn_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_26_c4` CHECK (COALESCE((quantity>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_26_c5` CHECK (COALESCE(((checkout_batch_id IS NULL OR REGEXP_LIKE(checkout_batch_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_26_c6` CHECK (COALESCE(((checkout_batch_item_snapshot_id IS NULL OR REGEXP_LIKE(checkout_batch_item_snapshot_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_26_c7` CHECK (COALESCE(((rental_order_id IS NULL OR REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_26_c8` CHECK (COALESCE(((rental_order_item_id IS NULL OR REGEXP_LIKE(rental_order_item_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_26_c9` CHECK (COALESCE(((source_id IS NULL OR REGEXP_LIKE(source_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d3b_26_c10` CHECK (COALESCE((reservation_status IN ('released','expired','cancelled','completed')),FALSE)=TRUE),
  CONSTRAINT `d3b_26_c11` CHECK (COALESCE((version>=0),FALSE)=TRUE),
  CONSTRAINT `d3b_26_c12` CHECK (COALESCE((REGEXP_LIKE(origin_reservation_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `checkout_batch` ADD CONSTRAINT `d3b_1_f1` FOREIGN KEY (`source_checklist_id`) REFERENCES `rental_checklist` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `checkout_batch` ADD CONSTRAINT `d3b_1_f2` FOREIGN KEY (`buyer_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `checkout_batch_item_snapshot` ADD CONSTRAINT `d3b_2_f1` FOREIGN KEY (`checkout_batch_id`) REFERENCES `checkout_batch` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `checkout_batch_item_snapshot` ADD CONSTRAINT `d3b_2_f2` FOREIGN KEY (`source_checklist_id`) REFERENCES `rental_checklist` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `checkout_batch_item_snapshot` ADD CONSTRAINT `d3b_2_f3` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `checkout_batch_item_snapshot` ADD CONSTRAINT `d3b_2_f4` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `checkout_batch_item_snapshot` ADD CONSTRAINT `d3b_2_f5` FOREIGN KEY (`checkout_batch_transport_snapshot_id`) REFERENCES `checkout_batch_transport_snapshot` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `checkout_batch_transport_snapshot` ADD CONSTRAINT `d3b_3_f1` FOREIGN KEY (`checkout_batch_id`) REFERENCES `checkout_batch` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `checkout_batch_transport_snapshot` ADD CONSTRAINT `d3b_3_f2` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `checkout_batch_transport_snapshot` ADD CONSTRAINT `d3b_3_f3` FOREIGN KEY (`outbound_vehicle_tier_id`) REFERENCES `transport_vehicle_tier` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `checkout_batch_transport_snapshot` ADD CONSTRAINT `d3b_3_f4` FOREIGN KEY (`return_vehicle_tier_id`) REFERENCES `transport_vehicle_tier` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `payment_order` ADD CONSTRAINT `d3b_4_f1` FOREIGN KEY (`checkout_batch_id`) REFERENCES `checkout_batch` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `payment_order` ADD CONSTRAINT `d3b_4_f2` FOREIGN KEY (`related_compensation_receivable_id`) REFERENCES `order_compensation_receivable` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `payment_order` ADD CONSTRAINT `d3b_4_f3` FOREIGN KEY (`buyer_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `payment_channel_suborder` ADD CONSTRAINT `d3b_5_f1` FOREIGN KEY (`payment_order_id`) REFERENCES `payment_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `payment_channel_suborder` ADD CONSTRAINT `d3b_5_f2` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `payment_channel_suborder` ADD CONSTRAINT `d3b_5_f3` FOREIGN KEY (`factory_wechat_account_id`) REFERENCES `factory_wechat_merchant_account` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `payment_channel_suborder` ADD CONSTRAINT `d3b_5_f4` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order` ADD CONSTRAINT `d3b_6_f1` FOREIGN KEY (`checkout_batch_id`) REFERENCES `checkout_batch` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order` ADD CONSTRAINT `d3b_6_f2` FOREIGN KEY (`payment_order_id`) REFERENCES `payment_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order` ADD CONSTRAINT `d3b_6_f3` FOREIGN KEY (`source_checklist_id`) REFERENCES `rental_checklist` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order` ADD CONSTRAINT `d3b_6_f4` FOREIGN KEY (`buyer_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order` ADD CONSTRAINT `d3b_6_f5` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order_delivery` ADD CONSTRAINT `d3b_7_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order_delivery` ADD CONSTRAINT `d3b_7_f2` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order_delivery` ADD CONSTRAINT `d3b_7_f3` FOREIGN KEY (`checkout_batch_transport_snapshot_id`) REFERENCES `checkout_batch_transport_snapshot` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order_freight_account` ADD CONSTRAINT `d3b_8_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order_freight_account` ADD CONSTRAINT `d3b_8_f2` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order_item` ADD CONSTRAINT `d3b_9_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order_item` ADD CONSTRAINT `d3b_9_f2` FOREIGN KEY (`checkout_batch_item_snapshot_id`) REFERENCES `checkout_batch_item_snapshot` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order_item` ADD CONSTRAINT `d3b_9_f3` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `rental_order_item` ADD CONSTRAINT `d3b_9_f4` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `payment_allocation` ADD CONSTRAINT `d3b_10_f1` FOREIGN KEY (`payment_order_id`) REFERENCES `payment_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `payment_allocation` ADD CONSTRAINT `d3b_10_f2` FOREIGN KEY (`payment_channel_suborder_id`) REFERENCES `payment_channel_suborder` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `payment_allocation` ADD CONSTRAINT `d3b_10_f3` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_price_adjustment` ADD CONSTRAINT `d3b_11_f1` FOREIGN KEY (`checkout_batch_id`) REFERENCES `checkout_batch` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_price_adjustment` ADD CONSTRAINT `d3b_11_f2` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_price_adjustment` ADD CONSTRAINT `d3b_11_f3` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `refund_order` ADD CONSTRAINT `d3b_12_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `refund_order` ADD CONSTRAINT `d3b_12_f2` FOREIGN KEY (`payment_order_id`) REFERENCES `payment_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `refund_order` ADD CONSTRAINT `d3b_12_f3` FOREIGN KEY (`payment_channel_suborder_id`) REFERENCES `payment_channel_suborder` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `refund_order` ADD CONSTRAINT `d3b_12_f4` FOREIGN KEY (`requested_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_deduction` ADD CONSTRAINT `d3b_13_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_deduction` ADD CONSTRAINT `d3b_13_f2` FOREIGN KEY (`created_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_compensation_receivable` ADD CONSTRAINT `d3b_14_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_compensation_receivable` ADD CONSTRAINT `d3b_14_f2` FOREIGN KEY (`buyer_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_compensation_receivable` ADD CONSTRAINT `d3b_14_f3` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_compensation_receivable` ADD CONSTRAINT `d3b_14_f4` FOREIGN KEY (`current_payment_order_id`) REFERENCES `payment_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `payment_channel_notify_log` ADD CONSTRAINT `d3b_15_f1` FOREIGN KEY (`payment_order_id`) REFERENCES `payment_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `payment_channel_notify_log` ADD CONSTRAINT `d3b_15_f2` FOREIGN KEY (`refund_order_id`) REFERENCES `refund_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_wechat_merchant_account` ADD CONSTRAINT `d3b_16_f1` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `transport_pricing_rule` ADD CONSTRAINT `d3b_18_f1` FOREIGN KEY (`vehicle_tier_id`) REFERENCES `transport_vehicle_tier` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_transport_leg` ADD CONSTRAINT `d3b_19_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_transport_leg` ADD CONSTRAINT `d3b_19_f2` FOREIGN KEY (`rental_order_delivery_id`) REFERENCES `rental_order_delivery` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_transport_leg` ADD CONSTRAINT `d3b_19_f3` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_transport_leg` ADD CONSTRAINT `d3b_19_f4` FOREIGN KEY (`matched_vehicle_tier_id`) REFERENCES `transport_vehicle_tier` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_transport_leg` ADD CONSTRAINT `d3b_19_f5` FOREIGN KEY (`pricing_rule_id`) REFERENCES `transport_pricing_rule` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_scope_target` ADD CONSTRAINT `d3b_20_f1` FOREIGN KEY (`coupon_template_id`) REFERENCES `coupon_template` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_scope_target` ADD CONSTRAINT `d3b_20_f2` FOREIGN KEY (`target_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `user_coupon` ADD CONSTRAINT `d3b_21_f1` FOREIGN KEY (`template_id`) REFERENCES `coupon_template` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `user_coupon` ADD CONSTRAINT `d3b_21_f2` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_usage` ADD CONSTRAINT `d3b_22_f1` FOREIGN KEY (`user_coupon_id`) REFERENCES `user_coupon` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_usage` ADD CONSTRAINT `d3b_22_f2` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_usage` ADD CONSTRAINT `d3b_22_f3` FOREIGN KEY (`checkout_batch_id`) REFERENCES `checkout_batch` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_usage` ADD CONSTRAINT `d3b_22_f4` FOREIGN KEY (`payment_order_id`) REFERENCES `payment_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_allocation` ADD CONSTRAINT `d3b_23_f1` FOREIGN KEY (`coupon_usage_id`) REFERENCES `coupon_usage` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_allocation` ADD CONSTRAINT `d3b_23_f2` FOREIGN KEY (`checkout_batch_id`) REFERENCES `checkout_batch` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_allocation` ADD CONSTRAINT `d3b_23_f3` FOREIGN KEY (`checkout_batch_item_snapshot_id`) REFERENCES `checkout_batch_item_snapshot` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_allocation` ADD CONSTRAINT `d3b_23_f4` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_allocation` ADD CONSTRAINT `d3b_23_f5` FOREIGN KEY (`rental_order_item_id`) REFERENCES `rental_order_item` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_allocation` ADD CONSTRAINT `d3b_23_f6` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_allocation` ADD CONSTRAINT `d3b_23_f7` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_operation_log` ADD CONSTRAINT `d3b_24_f1` FOREIGN KEY (`user_coupon_id`) REFERENCES `user_coupon` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_operation_log` ADD CONSTRAINT `d3b_24_f2` FOREIGN KEY (`coupon_usage_id`) REFERENCES `coupon_usage` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `coupon_operation_log` ADD CONSTRAINT `d3b_24_f3` FOREIGN KEY (`operator_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `trading_operation_record` ADD CONSTRAINT `d3b_25_f1` FOREIGN KEY (`actor_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE prop_sku ADD COLUMN packaging_factor_bps INT NULL DEFAULT NULL COMMENT '包装体积系数BP；旧数据未知，不自动填值', ADD CONSTRAINT ck_d3b_pack CHECK (packaging_factor_bps IS NULL OR packaging_factor_bps BETWEEN 10000 AND 1000000);

ALTER TABLE inventory_reservation ADD CONSTRAINT fk_d3b_res_checkout_batch_id FOREIGN KEY (`checkout_batch_id`) REFERENCES `checkout_batch` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE inventory_reservation ADD CONSTRAINT fk_d3b_res_checkout_batch_item_snapshot_id FOREIGN KEY (`checkout_batch_item_snapshot_id`) REFERENCES `checkout_batch_item_snapshot` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE inventory_reservation ADD CONSTRAINT fk_d3b_res_rental_order_id FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE inventory_reservation ADD CONSTRAINT fk_d3b_res_rental_order_item_id FOREIGN KEY (`rental_order_item_id`) REFERENCES `rental_order_item` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- 案件/流水指向占用的关联不得增加仅指热表的物理FK。归档复制、核对、删热行同事务；history不参与实时库存。

-- 保留既有权限格式，同时允许本批契约确定的交易权限；不重命名权限。
ALTER TABLE admin_menu DROP CHECK ck_admin_menu_9, ADD CONSTRAINT ck_admin_menu_9 CHECK (permission_code IS NULL OR REGEXP_LIKE(permission_code, '^admin:[a-z0-9-]+:[a-z0-9-]+$','c') OR permission_code IN ('admin:trade:order:read','admin:trade:payment:read','admin:trade:refund:read','admin:trade:merchant:read','admin:trade:merchant:sync','admin:trade:transport:manage','admin:trade:operation:read'));

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('47e212cb420e4e088f19479f27c73852',NULL,'交易管理','directory',NULL,NULL,NULL,1,1,100,CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'),CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('624787fc56f14562b4f57d7b216e2104','534505ce87e4482c94be274e2d506b6c','47e212cb420e4e088f19479f27c73852','3e8c4a5e468449709003045cd6dfca76',CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('868b1814bc864f0c9106733d3f60b09d','47e212cb420e4e088f19479f27c73852','租赁订单','menu','admin:trade:order:read','/trading/orders','trading/RentalOrders',1,1,101,CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'),CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('742b4838cf6d4b6b91f5d01347369851','534505ce87e4482c94be274e2d506b6c','868b1814bc864f0c9106733d3f60b09d','3e8c4a5e468449709003045cd6dfca76',CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('2c9291a26a5b4f06a0057438d4d0dedd','47e212cb420e4e088f19479f27c73852','支付明细','menu','admin:trade:payment:read','/trading/payments','trading/Payments',1,1,102,CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'),CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('e5fe2b62155c46269064e7a2c1e5b4b5','534505ce87e4482c94be274e2d506b6c','2c9291a26a5b4f06a0057438d4d0dedd','3e8c4a5e468449709003045cd6dfca76',CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('c02d3bfe83924cc3ba3ea791885f65b3','47e212cb420e4e088f19479f27c73852','退款查询','menu','admin:trade:refund:read','/trading/refunds','trading/Refunds',1,1,103,CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'),CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('229a75f2bcf24b90be7d82779aad5c24','534505ce87e4482c94be274e2d506b6c','c02d3bfe83924cc3ba3ea791885f65b3','3e8c4a5e468449709003045cd6dfca76',CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('d443c60f3a2c47a38f4e391e7ca8b604','47e212cb420e4e088f19479f27c73852','工厂收款账户','menu','admin:trade:merchant:read','/trading/merchants','trading/Merchants',1,1,104,CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'),CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('66a92e34967649779d33c998a1cb36cb','534505ce87e4482c94be274e2d506b6c','d443c60f3a2c47a38f4e391e7ca8b604','3e8c4a5e468449709003045cd6dfca76',CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('6218d56853c746a6824ee49b4ef0fd2c','d443c60f3a2c47a38f4e391e7ca8b604','收款状态同步','button','admin:trade:merchant:sync',NULL,NULL,1,1,105,CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'),CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('a8671bbcc0ae46959c053a2b4478c5bc','534505ce87e4482c94be274e2d506b6c','6218d56853c746a6824ee49b4ef0fd2c','3e8c4a5e468449709003045cd6dfca76',CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('0993c75d28ad4cc7b5a9a6682bf3cb33','47e212cb420e4e088f19479f27c73852','车型与运价','menu','admin:trade:transport:manage','/trading/transport','trading/Transport',1,1,106,CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'),CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('b257c0d19ee440e89408f278fdf95e2f','534505ce87e4482c94be274e2d506b6c','0993c75d28ad4cc7b5a9a6682bf3cb33','3e8c4a5e468449709003045cd6dfca76',CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('48125f306e2d4abba5ff22e3386cc199','47e212cb420e4e088f19479f27c73852','恢复本人交易操作','button','admin:trade:operation:read',NULL,NULL,1,1,107,CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'),CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('e425e58d5ad548019c41ee6f44bfedcc','534505ce87e4482c94be274e2d506b6c','48125f306e2d4abba5ff22e3386cc199','3e8c4a5e468449709003045cd6dfca76',CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00'));
