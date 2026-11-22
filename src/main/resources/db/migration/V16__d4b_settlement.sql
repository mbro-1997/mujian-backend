-- D4-B：依据 C-03 与正式设计拆分；只追加迁移，默认不开放真实履约。

CREATE TABLE `order_transport_expense_statement` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '费用申报 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `statement_version` INT NOT NULL COMMENT '申报版本',
  `outbound_transport_leg_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '去程运输段 ID',
  `return_transport_leg_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '回程运输段 ID',
  `outbound_execution_source` VARCHAR(32) NULL DEFAULT NULL COMMENT '去程实际执行来源',
  `return_execution_source` VARCHAR(32) NULL DEFAULT NULL COMMENT '回程实际执行来源',
  `outbound_carrier_name` VARCHAR(128) NULL DEFAULT NULL COMMENT '去程第三方承运方',
  `outbound_carrier_order_no` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '去程第三方订单号',
  `return_carrier_name` VARCHAR(128) NULL DEFAULT NULL COMMENT '回程第三方承运方',
  `return_carrier_order_no` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '回程第三方订单号',
  `outbound_declared_base_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '去程申报基础运费',
  `outbound_declared_extra_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '去程申报附加费',
  `outbound_declared_total_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '去程申报合计',
  `return_declared_base_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '回程申报基础运费',
  `return_declared_extra_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '回程申报附加费',
  `return_declared_total_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '回程申报合计',
  `declared_total_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '往返申报总额',
  `extra_fee_detail_json` JSON NULL DEFAULT NULL COMMENT '去回程附加费明细',
  `evidence_count` INT NOT NULL DEFAULT 0 COMMENT '有效费用凭证数量',
  `review_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审核状态',
  `outbound_approved_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '去程核准运费',
  `return_approved_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '回程核准运费',
  `approved_total_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '往返核准总额',
  `prepaid_pool_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '运费暂收池金额快照',
  `online_settle_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '线上工厂运费应结',
  `over_budget_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '超预算金额',
  `refundable_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '用户可退运费',
  `review_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '实际审核管理员用户 ID',
  `review_reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '审核/退回说明',
  `submitted_at` DATETIME(3) NULL DEFAULT NULL COMMENT '提交时间',
  `reviewed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '审核时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  `active_order_guard` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN review_status IN ('draft','submitted','reviewing','approved') THEN rental_order_id ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_11_i1` (`rental_order_id`,`statement_version`),
  KEY `d4_11_i2` (`review_status`,`submitted_at`),
  KEY `d4_11_i3` (`rental_order_id`,`review_status`,`statement_version`),
  UNIQUE KEY `d4_11_i4` (`active_order_guard`),
  CONSTRAINT `d4_11_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_11_c2` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_11_c3` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_11_c4` CHECK (COALESCE((statement_version>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c5` CHECK (COALESCE(((outbound_transport_leg_id IS NULL OR REGEXP_LIKE(outbound_transport_leg_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_11_c6` CHECK (COALESCE(((return_transport_leg_id IS NULL OR REGEXP_LIKE(return_transport_leg_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_11_c7` CHECK (COALESCE(((outbound_execution_source IS NULL OR outbound_execution_source IN ('factory_vehicle','third_party'))),FALSE)=TRUE),
  CONSTRAINT `d4_11_c8` CHECK (COALESCE(((return_execution_source IS NULL OR return_execution_source IN ('factory_vehicle','third_party'))),FALSE)=TRUE),
  CONSTRAINT `d4_11_c9` CHECK (COALESCE((outbound_declared_base_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c10` CHECK (COALESCE((outbound_declared_extra_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c11` CHECK (COALESCE((outbound_declared_total_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c12` CHECK (COALESCE((return_declared_base_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c13` CHECK (COALESCE((return_declared_extra_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c14` CHECK (COALESCE((return_declared_total_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c15` CHECK (COALESCE((declared_total_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c16` CHECK (COALESCE((evidence_count>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c17` CHECK (COALESCE((review_status IN ('draft','submitted','reviewing','approved','returned','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d4_11_c18` CHECK (COALESCE((outbound_approved_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c19` CHECK (COALESCE((return_approved_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c20` CHECK (COALESCE((approved_total_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c21` CHECK (COALESCE((prepaid_pool_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c22` CHECK (COALESCE((online_settle_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c23` CHECK (COALESCE((over_budget_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c24` CHECK (COALESCE((refundable_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c25` CHECK (COALESCE(((review_user_id IS NULL OR REGEXP_LIKE(review_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_11_c26` CHECK (COALESCE((version>=0),FALSE)=TRUE),
  CONSTRAINT `d4_11_c27` CHECK (COALESCE((declared_total_amount_fen=outbound_declared_total_amount_fen+return_declared_total_amount_fen AND outbound_declared_total_amount_fen=outbound_declared_base_amount_fen+outbound_declared_extra_amount_fen AND return_declared_total_amount_fen=return_declared_base_amount_fen+return_declared_extra_amount_fen),FALSE)=TRUE),
  CONSTRAINT `d4_11_c28` CHECK (COALESCE((approved_total_amount_fen=outbound_approved_amount_fen+return_approved_amount_fen),FALSE)=TRUE),
  CONSTRAINT `d4_11_c29` CHECK (COALESCE((review_status<>'approved' OR (approved_total_amount_fen=declared_total_amount_fen AND online_settle_amount_fen=LEAST(approved_total_amount_fen,prepaid_pool_amount_fen) AND over_budget_amount_fen=GREATEST(approved_total_amount_fen-prepaid_pool_amount_fen,0) AND refundable_amount_fen=GREATEST(prepaid_pool_amount_fen-approved_total_amount_fen,0))),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `factory_settlement_batch` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算批次 ID',
  `settlement_batch_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算批次号',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `settlement_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算状态',
  `settlement_channel` VARCHAR(32) NOT NULL COMMENT '结算渠道',
  `order_count` INT NOT NULL DEFAULT 0 COMMENT '业务订单数量',
  `custom_goods_settle_base_amount_fen` BIGINT NOT NULL DEFAULT 0 COMMENT '定制商品结算基数合计',
  `rent_settle_base_amount_fen` BIGINT NOT NULL DEFAULT 0 COMMENT '租金结算基数合计',
  `platform_commission_amount_fen` BIGINT NOT NULL DEFAULT 0 COMMENT '平台佣金合计',
  `deposit_compensation_amount_fen` BIGINT NOT NULL DEFAULT 0 COMMENT '押金赔付合计',
  `freight_reimbursement_amount_fen` BIGINT NOT NULL DEFAULT 0 COMMENT '线上运费结算合计',
  `factory_settle_amount_fen` BIGINT NOT NULL DEFAULT 0 COMMENT '工厂应结金额合计',
  `settlement_period_start` DATETIME(3) NOT NULL COMMENT '结算周期开始时间',
  `settlement_period_end` DATETIME(3) NOT NULL COMMENT '结算周期结束时间',
  `generated_at` DATETIME(3) NOT NULL COMMENT '生成时间',
  `settled_at` DATETIME(3) NULL DEFAULT NULL COMMENT '结算完成时间',
  `fail_reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '失败原因',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_13_i1` (`settlement_batch_no`),
  KEY `d4_13_i2` (`factory_id`,`settlement_status`,`generated_at`),
  UNIQUE KEY `d4_13_i3` (`factory_id`,`settlement_period_start`,`settlement_period_end`),
  CONSTRAINT `d4_13_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_13_c2` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_13_c3` CHECK (COALESCE((settlement_status IN ('pending','processing','settled','failed','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d4_13_c4` CHECK (COALESCE((settlement_channel IN ('wechat_platform_collection')),FALSE)=TRUE),
  CONSTRAINT `d4_13_c5` CHECK (COALESCE((order_count>=0),FALSE)=TRUE),
  CONSTRAINT `d4_13_c6` CHECK (COALESCE((custom_goods_settle_base_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_13_c7` CHECK (COALESCE((rent_settle_base_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_13_c8` CHECK (COALESCE((platform_commission_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_13_c9` CHECK (COALESCE((deposit_compensation_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_13_c10` CHECK (COALESCE((freight_reimbursement_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_13_c11` CHECK (COALESCE((factory_settle_amount_fen>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `factory_settlement_detail` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算明细 ID',
  `settlement_batch_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算批次 ID',
  `source_settlement_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算依据唯一键',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `payment_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '代表来源支付单 ID',
  `source_allocation_snapshot_json` JSON NOT NULL COMMENT '本明细资金来源固化快照',
  `initial_rent_paid_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '首次实付租金',
  `shared_coupon_discount_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '共同承担优惠券分摊',
  `extension_rent_paid_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '延期实付租金',
  `rent_paid_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '实付租金合计',
  `rent_refunded_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '本明细来源已退租金',
  `rent_deducted_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '已扣租金',
  `rent_settlement_adjustment_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '租金结算调整金额',
  `rent_settlement_adjustment_reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '租金结算调整原因',
  `rent_settle_base_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '租金结算基数',
  `commission_rate_bps` INT NOT NULL COMMENT '平台佣金比例 BP',
  `platform_commission_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '平台佣金',
  `factory_rent_income_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '工厂租金收入',
  `deposit_deducted_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '本明细已扣押金',
  `deposit_compensation_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '押金赔付给工厂金额',
  `excess_compensation_paid_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '本明细超押金赔付已缴',
  `freight_approved_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '去回程核准运费合计',
  `freight_refunded_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '已退用户运费',
  `freight_reimbursement_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '结算给工厂的线上运费',
  `factory_settle_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '工厂应结金额',
  `detail_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '明细状态',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_14_i1` (`source_settlement_key`),
  KEY `d4_14_i2` (`settlement_batch_id`,`detail_status`),
  KEY `d4_14_i3` (`rental_order_id`),
  KEY `d4_14_i4` (`factory_id`,`detail_status`,`created_at`),
  CONSTRAINT `d4_14_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_14_c2` CHECK (COALESCE((REGEXP_LIKE(settlement_batch_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_14_c3` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_14_c4` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_14_c5` CHECK (COALESCE((REGEXP_LIKE(payment_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_14_c6` CHECK (COALESCE((initial_rent_paid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c7` CHECK (COALESCE((shared_coupon_discount_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c8` CHECK (COALESCE((extension_rent_paid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c9` CHECK (COALESCE((rent_paid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c10` CHECK (COALESCE((rent_refunded_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c11` CHECK (COALESCE((rent_deducted_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c12` CHECK (COALESCE((rent_settlement_adjustment_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c13` CHECK (COALESCE((rent_settle_base_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c14` CHECK (COALESCE((commission_rate_bps>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c15` CHECK (COALESCE((platform_commission_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c16` CHECK (COALESCE((factory_rent_income_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c17` CHECK (COALESCE((deposit_deducted_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c18` CHECK (COALESCE((deposit_compensation_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c19` CHECK (COALESCE((excess_compensation_paid_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c20` CHECK (COALESCE((freight_approved_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c21` CHECK (COALESCE((freight_refunded_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c22` CHECK (COALESCE((freight_reimbursement_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c23` CHECK (COALESCE((factory_settle_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_14_c24` CHECK (COALESCE((detail_status IN ('pending','included','settled','adjusted','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d4_14_c25` CHECK (COALESCE((commission_rate_bps=2000 AND rent_paid_amount_fen=initial_rent_paid_amount_fen+extension_rent_paid_amount_fen AND rent_settle_base_amount_fen=rent_paid_amount_fen-rent_refunded_amount_fen-rent_settlement_adjustment_amount_fen AND platform_commission_amount_fen=FLOOR(CAST(rent_settle_base_amount_fen AS DECIMAL(30,0))*commission_rate_bps/10000) AND factory_rent_income_amount_fen=rent_settle_base_amount_fen-platform_commission_amount_fen AND factory_settle_amount_fen=factory_rent_income_amount_fen+deposit_compensation_amount_fen+excess_compensation_paid_amount_fen+freight_reimbursement_amount_fen),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `platform_commission_record` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '佣金记录 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁订单 ID',
  `settlement_detail_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁结算明细 ID',
  `custom_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '定制订单 ID',
  `custom_settlement_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '定制结算明细 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `commission_base_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '佣金计算基数',
  `commission_rate_bps` INT NOT NULL COMMENT '佣金比例 BP',
  `commission_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '佣金金额',
  `commission_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '佣金状态',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `d4_15_i1` (`rental_order_id`),
  UNIQUE KEY `d4_15_i2` (`settlement_detail_id`),
  UNIQUE KEY `d4_15_i3` (`custom_settlement_id`),
  KEY `d4_15_i4` (`settlement_detail_id`),
  KEY `d4_15_i5` (`factory_id`,`commission_status`,`created_at`),
  CONSTRAINT `d4_15_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_15_c2` CHECK (COALESCE(((rental_order_id IS NULL OR REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_15_c3` CHECK (COALESCE(((settlement_detail_id IS NULL OR REGEXP_LIKE(settlement_detail_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_15_c4` CHECK (COALESCE(((custom_order_id IS NULL OR REGEXP_LIKE(custom_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_15_c5` CHECK (COALESCE(((custom_settlement_id IS NULL OR REGEXP_LIKE(custom_settlement_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_15_c6` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_15_c7` CHECK (COALESCE((commission_base_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_15_c8` CHECK (COALESCE((commission_rate_bps>=0),FALSE)=TRUE),
  CONSTRAINT `d4_15_c9` CHECK (COALESCE((commission_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_15_c10` CHECK (COALESCE((commission_status IN ('pending','confirmed','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d4_15_c11` CHECK (COALESCE(((rental_order_id IS NOT NULL AND settlement_detail_id IS NOT NULL AND custom_order_id IS NULL AND custom_settlement_id IS NULL) OR (rental_order_id IS NULL AND settlement_detail_id IS NULL AND custom_order_id IS NOT NULL AND custom_settlement_id IS NOT NULL)),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `factory_settlement_payment` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算付款 ID',
  `settlement_payment_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算付款编号',
  `settlement_batch_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算批次 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `payment_attempt_no` INT NOT NULL COMMENT '付款尝试序号',
  `payment_method` VARCHAR(32) NOT NULL COMMENT '付款方式',
  `payable_amount_fen` BIGINT NOT NULL DEFAULT 0 COMMENT '应付金额',
  `actual_paid_amount_fen` BIGINT NULL DEFAULT NULL COMMENT '实际付款金额',
  `payment_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '付款状态',
  `receiver_name_snapshot` VARCHAR(128) NOT NULL COMMENT '收款方名称快照',
  `receiver_account_masked_snapshot` VARCHAR(128) NOT NULL COMMENT '收款账号脱敏快照',
  `factory_wechat_account_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂微信二级商户账户 ID',
  `external_transaction_no` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '外部付款流水号',
  `payment_voucher_storage_key` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '渠道凭证存储 key',
  `idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '付款幂等键',
  `fail_reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '失败原因',
  `operator_admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '操作管理员 ID',
  `requested_at` DATETIME(3) NULL DEFAULT NULL COMMENT '发起付款时间',
  `paid_at` DATETIME(3) NULL DEFAULT NULL COMMENT '付款成功时间',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  `success_batch_guard` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN payment_status='success' THEN settlement_batch_id ELSE NULL END) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_16_i1` (`settlement_payment_no`),
  UNIQUE KEY `d4_16_i2` (`idempotency_key`),
  UNIQUE KEY `d4_16_i3` (`settlement_batch_id`,`payment_attempt_no`),
  KEY `d4_16_i4` (`payment_status`,`updated_at`),
  KEY `d4_16_i5` (`factory_id`,`paid_at`),
  UNIQUE KEY `d4_16_i6` (`success_batch_guard`),
  CONSTRAINT `d4_16_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_16_c2` CHECK (COALESCE((REGEXP_LIKE(settlement_batch_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_16_c3` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_16_c4` CHECK (COALESCE((payment_attempt_no>=0),FALSE)=TRUE),
  CONSTRAINT `d4_16_c5` CHECK (COALESCE((payment_method IN ('wechat_platform_collection')),FALSE)=TRUE),
  CONSTRAINT `d4_16_c6` CHECK (COALESCE((payable_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_16_c7` CHECK (COALESCE(((actual_paid_amount_fen IS NULL OR actual_paid_amount_fen>=0)),FALSE)=TRUE),
  CONSTRAINT `d4_16_c8` CHECK (COALESCE((payment_status IN ('pending','processing','success','failed','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d4_16_c9` CHECK (COALESCE((REGEXP_LIKE(factory_wechat_account_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_16_c10` CHECK (COALESCE(((operator_admin_user_id IS NULL OR REGEXP_LIKE(operator_admin_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_16_c11` CHECK (COALESCE((payment_status<>'success' OR (actual_paid_amount_fen IS NOT NULL AND actual_paid_amount_fen=payable_amount_fen AND paid_at IS NOT NULL)),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `wechat_profit_sharing_order` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行单 ID',
  `settlement_batch_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '结算批次 ID',
  `settlement_detail_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁结算明细 ID',
  `custom_settlement_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '定制结算明细 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁订单 ID',
  `custom_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '定制订单 ID',
  `payment_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实际来源支付主单 ID',
  `payment_channel_suborder_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实际来源渠道子单 ID',
  `factory_wechat_account_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原收款微信账户 ID',
  `execution_type` VARCHAR(32) NOT NULL COMMENT '渠道执行类型',
  `business_idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本地执行幂等键',
  `channel_out_order_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '商户分佣金请求号',
  `finish_out_order_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '商户解冻请求号',
  `commission_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '分佣金阶段状态',
  `unfreeze_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '解冻阶段状态',
  `commission_retry_count` INT NOT NULL DEFAULT 0 COMMENT '分佣金重试次数',
  `unfreeze_retry_count` INT NOT NULL DEFAULT 0 COMMENT '解冻重试次数',
  `channel_finish_no` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '渠道解冻单号',
  `exception_code` VARCHAR(64) NULL DEFAULT NULL COMMENT '渠道异常标识',
  `alert_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '预警处理状态',
  `retry_of_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '原失败执行 ID',
  `platform_commission_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '本来源应分平台佣金',
  `factory_unlock_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '本来源预计工厂解冻金额',
  `actual_commission_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '已确认佣金金额',
  `actual_unlock_amount_fen` INT NOT NULL DEFAULT 0 COMMENT '已确认解冻金额',
  `sharing_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '执行状态',
  `channel_sharing_no` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '微信分账单号',
  `channel_result_snapshot_json` JSON NULL DEFAULT NULL COMMENT '渠道结果快照',
  `next_attempt_at` DATETIME(3) NULL DEFAULT NULL COMMENT '下次查询或执行时间',
  `lease_until` DATETIME(3) NULL DEFAULT NULL COMMENT '外部调用租约截止',
  `last_queried_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近查询时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '并发版本',
  `requested_at` DATETIME(3) NULL DEFAULT NULL COMMENT '请求时间',
  `success_at` DATETIME(3) NULL DEFAULT NULL COMMENT '成功时间',
  `failed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '失败时间',
  `fail_reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '失败原因',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_17_i1` (`business_idempotency_key`),
  UNIQUE KEY `d4_17_i2` (`channel_out_order_no`),
  UNIQUE KEY `d4_17_i3` (`finish_out_order_no`),
  UNIQUE KEY `d4_17_i4` (`channel_sharing_no`),
  UNIQUE KEY `d4_17_i5` (`payment_channel_suborder_id`),
  KEY `d4_17_i6` (`payment_channel_suborder_id`,`sharing_status`),
  KEY `d4_17_i7` (`settlement_batch_id`,`sharing_status`),
  KEY `d4_17_i8` (`settlement_detail_id`,`rental_order_id`),
  KEY `d4_17_i9` (`custom_settlement_id`,`custom_order_id`),
  KEY `d4_17_i10` (`next_attempt_at`,`id`),
  CONSTRAINT `d4_17_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_17_c2` CHECK (COALESCE((REGEXP_LIKE(settlement_batch_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_17_c3` CHECK (COALESCE(((settlement_detail_id IS NULL OR REGEXP_LIKE(settlement_detail_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_17_c4` CHECK (COALESCE(((custom_settlement_id IS NULL OR REGEXP_LIKE(custom_settlement_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_17_c5` CHECK (COALESCE(((rental_order_id IS NULL OR REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_17_c6` CHECK (COALESCE(((custom_order_id IS NULL OR REGEXP_LIKE(custom_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_17_c7` CHECK (COALESCE((REGEXP_LIKE(payment_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_17_c8` CHECK (COALESCE((REGEXP_LIKE(payment_channel_suborder_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_17_c9` CHECK (COALESCE((REGEXP_LIKE(factory_wechat_account_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_17_c10` CHECK (COALESCE((execution_type IN ('share_then_finish','finish_only')),FALSE)=TRUE),
  CONSTRAINT `d4_17_c11` CHECK (COALESCE((commission_status IN ('pending','processing','success','failed','not_required')),FALSE)=TRUE),
  CONSTRAINT `d4_17_c12` CHECK (COALESCE((unfreeze_status IN ('blocked','pending','processing','success','failed')),FALSE)=TRUE),
  CONSTRAINT `d4_17_c13` CHECK (COALESCE((commission_retry_count>=0),FALSE)=TRUE),
  CONSTRAINT `d4_17_c14` CHECK (COALESCE((unfreeze_retry_count>=0),FALSE)=TRUE),
  CONSTRAINT `d4_17_c15` CHECK (COALESCE((alert_status IN ('none','open','resolved')),FALSE)=TRUE),
  CONSTRAINT `d4_17_c16` CHECK (COALESCE(((retry_of_id IS NULL OR REGEXP_LIKE(retry_of_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_17_c17` CHECK (COALESCE((platform_commission_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_17_c18` CHECK (COALESCE((factory_unlock_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_17_c19` CHECK (COALESCE((actual_commission_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_17_c20` CHECK (COALESCE((actual_unlock_amount_fen>=0),FALSE)=TRUE),
  CONSTRAINT `d4_17_c21` CHECK (COALESCE((sharing_status IN ('pending','processing','success','failed','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d4_17_c22` CHECK (COALESCE((version>=0),FALSE)=TRUE),
  CONSTRAINT `d4_17_c23` CHECK (COALESCE(((rental_order_id IS NOT NULL AND settlement_detail_id IS NOT NULL AND custom_order_id IS NULL AND custom_settlement_id IS NULL) OR (rental_order_id IS NULL AND settlement_detail_id IS NULL AND custom_order_id IS NOT NULL AND custom_settlement_id IS NOT NULL)),FALSE)=TRUE),
  CONSTRAINT `d4_17_c24` CHECK (COALESCE(((execution_type='finish_only' AND platform_commission_amount_fen=0 AND commission_status='not_required' AND channel_out_order_no IS NULL) OR (execution_type='share_then_finish' AND platform_commission_amount_fen>0 AND channel_out_order_no IS NOT NULL)),FALSE)=TRUE),
  CONSTRAINT `d4_17_c25` CHECK (COALESCE((sharing_status<>'success' OR (commission_status IN ('success','not_required') AND unfreeze_status='success' AND actual_commission_amount_fen=platform_commission_amount_fen AND actual_unlock_amount_fen=factory_unlock_amount_fen)),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `order_evidence_record` ADD CONSTRAINT `d4_9_f4` FOREIGN KEY (`transport_expense_statement_id`) REFERENCES `order_transport_expense_statement` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_transport_expense_statement` ADD CONSTRAINT `d4_11_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_transport_expense_statement` ADD CONSTRAINT `d4_11_f2` FOREIGN KEY (`outbound_transport_leg_id`) REFERENCES `order_transport_leg` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_transport_expense_statement` ADD CONSTRAINT `d4_11_f3` FOREIGN KEY (`return_transport_leg_id`) REFERENCES `order_transport_leg` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_transport_expense_statement` ADD CONSTRAINT `d4_11_f4` FOREIGN KEY (`review_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_transport_expense_statement` ADD CONSTRAINT `d4_11_f5` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_settlement_batch` ADD CONSTRAINT `d4_13_f1` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_settlement_detail` ADD CONSTRAINT `d4_14_f1` FOREIGN KEY (`settlement_batch_id`) REFERENCES `factory_settlement_batch` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_settlement_detail` ADD CONSTRAINT `d4_14_f2` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_settlement_detail` ADD CONSTRAINT `d4_14_f3` FOREIGN KEY (`payment_order_id`) REFERENCES `payment_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_settlement_detail` ADD CONSTRAINT `d4_14_f4` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `platform_commission_record` ADD CONSTRAINT `d4_15_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `platform_commission_record` ADD CONSTRAINT `d4_15_f2` FOREIGN KEY (`settlement_detail_id`) REFERENCES `factory_settlement_detail` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `platform_commission_record` ADD CONSTRAINT `d4_15_f3` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_settlement_payment` ADD CONSTRAINT `d4_16_f1` FOREIGN KEY (`settlement_batch_id`) REFERENCES `factory_settlement_batch` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_settlement_payment` ADD CONSTRAINT `d4_16_f2` FOREIGN KEY (`factory_wechat_account_id`) REFERENCES `factory_wechat_merchant_account` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_settlement_payment` ADD CONSTRAINT `d4_16_f3` FOREIGN KEY (`operator_admin_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_settlement_payment` ADD CONSTRAINT `d4_16_f4` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `wechat_profit_sharing_order` ADD CONSTRAINT `d4_17_f1` FOREIGN KEY (`settlement_batch_id`) REFERENCES `factory_settlement_batch` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `wechat_profit_sharing_order` ADD CONSTRAINT `d4_17_f2` FOREIGN KEY (`settlement_detail_id`) REFERENCES `factory_settlement_detail` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `wechat_profit_sharing_order` ADD CONSTRAINT `d4_17_f3` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `wechat_profit_sharing_order` ADD CONSTRAINT `d4_17_f4` FOREIGN KEY (`payment_order_id`) REFERENCES `payment_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `wechat_profit_sharing_order` ADD CONSTRAINT `d4_17_f5` FOREIGN KEY (`payment_channel_suborder_id`) REFERENCES `payment_channel_suborder` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `wechat_profit_sharing_order` ADD CONSTRAINT `d4_17_f6` FOREIGN KEY (`factory_wechat_account_id`) REFERENCES `factory_wechat_merchant_account` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE admin_operation_audit ADD UNIQUE KEY uk_d4_audit_request (admin_user_id,operation_code,target_type,target_id,idempotency_key);
