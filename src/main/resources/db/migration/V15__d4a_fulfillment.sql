-- D4-A：依据 C-03 与正式设计拆分；只追加迁移，默认不开放真实履约。

CREATE TABLE `order_outbound_record` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '出库记录 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `outbound_transport_leg_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '去程运输段 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `outbound_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '出库状态',
  `outbound_service_mode` VARCHAR(32) NOT NULL COMMENT '去程服务方式',
  `transport_requirement_snapshot` VARCHAR(1000) NULL DEFAULT NULL COMMENT '运输要求快照',
  `outbound_photo_count` INT NOT NULL DEFAULT 0 COMMENT '出库照片数量',
  `operator_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '操作工厂账号 ID',
  `outbound_at` DATETIME(3) NULL DEFAULT NULL COMMENT '完成出库时间',
  `dispatch_at` DATETIME(3) NULL DEFAULT NULL COMMENT '发出/交接时间',
  `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '备注',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_1_i1` (`rental_order_id`),
  KEY `d4_1_i2` (`factory_id`,`outbound_status`,`updated_at`),
  CONSTRAINT `d4_1_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_1_c2` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_1_c3` CHECK (COALESCE((REGEXP_LIKE(outbound_transport_leg_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_1_c4` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_1_c5` CHECK (COALESCE((outbound_status IN ('pending','completed','cancelled')),FALSE)=TRUE),
  CONSTRAINT `d4_1_c6` CHECK (COALESCE((outbound_service_mode IN ('factory_delivery','factory_assisted_call','self_pickup')),FALSE)=TRUE),
  CONSTRAINT `d4_1_c7` CHECK (COALESCE((outbound_photo_count>=0),FALSE)=TRUE),
  CONSTRAINT `d4_1_c8` CHECK (COALESCE(((operator_user_id IS NULL OR REGEXP_LIKE(operator_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_1_c9` CHECK (COALESCE((version>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_outbound_item_check` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '出库明细核验 ID',
  `outbound_record_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '出库记录 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `rental_order_item_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单明细 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `asset_type` VARCHAR(16) NOT NULL COMMENT '资产类型',
  `expected_quantity` INT NOT NULL COMMENT '应核验数量',
  `checked_quantity` INT NOT NULL DEFAULT 0 COMMENT '已核验数量',
  `check_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '核验状态',
  `operator_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最近操作人 ID',
  `completed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '完成时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_2_i1` (`outbound_record_id`,`rental_order_item_id`),
  KEY `d4_2_i2` (`outbound_record_id`,`check_status`),
  CONSTRAINT `d4_2_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_2_c2` CHECK (COALESCE((REGEXP_LIKE(outbound_record_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_2_c3` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_2_c4` CHECK (COALESCE((REGEXP_LIKE(rental_order_item_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_2_c5` CHECK (COALESCE((REGEXP_LIKE(sku_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_2_c6` CHECK (COALESCE((asset_type IN ('A_ASSET','B_CONSUMABLE')),FALSE)=TRUE),
  CONSTRAINT `d4_2_c7` CHECK (COALESCE((expected_quantity>=0),FALSE)=TRUE),
  CONSTRAINT `d4_2_c8` CHECK (COALESCE((checked_quantity>=0),FALSE)=TRUE),
  CONSTRAINT `d4_2_c9` CHECK (COALESCE((check_status IN ('pending','partial','completed')),FALSE)=TRUE),
  CONSTRAINT `d4_2_c10` CHECK (COALESCE(((operator_user_id IS NULL OR REGEXP_LIKE(operator_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_2_c11` CHECK (COALESCE((version>=0),FALSE)=TRUE),
  CONSTRAINT `d4_2_c12` CHECK (COALESCE((expected_quantity>0 AND checked_quantity<=expected_quantity),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_outbound_scan_record` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '出库扫码记录 ID',
  `scan_request_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '扫码请求幂等号',
  `outbound_record_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '出库记录 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `rental_order_item_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单明细 ID',
  `prop_qr_code_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '道具二维码 ID',
  `prop_sn_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'A 类 SN ID',
  `operator_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂扫码账号 ID',
  `scanned_at` DATETIME(3) NOT NULL COMMENT '扫码成功时间',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_3_i1` (`scan_request_id`),
  UNIQUE KEY `d4_3_i2` (`outbound_record_id`,`prop_sn_id`),
  KEY `d4_3_i3` (`outbound_record_id`,`rental_order_item_id`,`scanned_at`),
  CONSTRAINT `d4_3_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_3_c2` CHECK (COALESCE((REGEXP_LIKE(outbound_record_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_3_c3` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_3_c4` CHECK (COALESCE((REGEXP_LIKE(rental_order_item_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_3_c5` CHECK (COALESCE((REGEXP_LIKE(prop_qr_code_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_3_c6` CHECK (COALESCE((REGEXP_LIKE(prop_sn_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_3_c7` CHECK (COALESCE((REGEXP_LIKE(operator_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_receipt_check` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '收货核验 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `outbound_transport_leg_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '去程运输段 ID',
  `buyer_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁方用户 ID',
  `check_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '核验状态',
  `start_at` DATETIME(3) NULL DEFAULT NULL COMMENT '核验开始时间',
  `deadline_at` DATETIME(3) NULL DEFAULT NULL COMMENT '核验截止时间',
  `confirmed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '确认时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_4_i1` (`rental_order_id`),
  KEY `d4_4_i2` (`check_status`,`deadline_at`),
  CONSTRAINT `d4_4_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_4_c2` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_4_c3` CHECK (COALESCE((REGEXP_LIKE(outbound_transport_leg_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_4_c4` CHECK (COALESCE((REGEXP_LIKE(buyer_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_4_c5` CHECK (COALESCE((check_status IN ('pending','confirmed','problem_reported','auto_confirmed')),FALSE)=TRUE),
  CONSTRAINT `d4_4_c6` CHECK (COALESCE((version>=0),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_return_shipment` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '归还发出 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `return_transport_leg_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '回程运输段 ID',
  `buyer_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁方用户 ID',
  `operator_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实际操作用户 ID',
  `operator_role` VARCHAR(16) NOT NULL COMMENT '操作角色',
  `shipment_status` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '归还发出状态',
  `return_method` VARCHAR(32) NOT NULL COMMENT '归还方式',
  `tracking_no` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '物流/运输凭证号',
  `carrier_name` VARCHAR(128) NULL DEFAULT NULL COMMENT '承运方名称',
  `sent_at` DATETIME(3) NULL DEFAULT NULL COMMENT '用户送回发出/工厂开始取回时间',
  `expected_arrival_at` DATETIME(3) NULL DEFAULT NULL COMMENT '预计到厂时间',
  `expected_check_start_at` DATETIME(3) NULL DEFAULT NULL COMMENT '预计核验时间',
  `packaging_photo_count` INT NOT NULL DEFAULT 0 COMMENT '包装照片数量',
  `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '备注',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_5_i1` (`rental_order_id`),
  KEY `d4_5_i2` (`expected_check_start_at`),
  CONSTRAINT `d4_5_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_5_c2` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_5_c3` CHECK (COALESCE((REGEXP_LIKE(return_transport_leg_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_5_c4` CHECK (COALESCE((REGEXP_LIKE(buyer_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_5_c5` CHECK (COALESCE((REGEXP_LIKE(operator_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_5_c6` CHECK (COALESCE((operator_role IN ('buyer','factory','admin')),FALSE)=TRUE),
  CONSTRAINT `d4_5_c7` CHECK (COALESCE((shipment_status IN ('draft','sent')),FALSE)=TRUE),
  CONSTRAINT `d4_5_c8` CHECK (COALESCE((return_method IN ('user_return','factory_pickup')),FALSE)=TRUE),
  CONSTRAINT `d4_5_c9` CHECK (COALESCE((packaging_photo_count>=0),FALSE)=TRUE),
  CONSTRAINT `d4_5_c10` CHECK (COALESCE((version>=0),FALSE)=TRUE),
  CONSTRAINT `d4_5_c11` CHECK (COALESCE(((shipment_status='draft' AND expected_check_start_at IS NULL) OR (shipment_status='sent' AND sent_at IS NOT NULL AND expected_check_start_at IS NOT NULL AND (return_method='user_return' OR (expected_arrival_at IS NOT NULL AND expected_arrival_at>=sent_at)))),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_return_check` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '归还核验 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `return_shipment_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '归还发出记录 ID',
  `check_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '核验状态',
  `check_start_source` VARCHAR(32) NOT NULL COMMENT '核验触发来源',
  `check_start_at` DATETIME(3) NOT NULL COMMENT '核验开始时间',
  `warning_at` DATETIME(3) NULL DEFAULT NULL COMMENT '12 小时预警时间',
  `auto_close_at` DATETIME(3) NULL DEFAULT NULL COMMENT '72 小时自动关闭时间',
  `checked_at` DATETIME(3) NULL DEFAULT NULL COMMENT '工厂提交核验结果时间',
  `operator_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '工厂操作人 ID',
  `problem_summary` VARCHAR(500) NULL DEFAULT NULL COMMENT '问题摘要',
  `claim_created` TINYINT NOT NULL DEFAULT 0 COMMENT '是否已成功创建索赔',
  `close_reason_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '关闭原因编码',
  `close_reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '关闭原因说明',
  `closed_by_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关闭操作用户 ID',
  `closed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '核验关闭时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_6_i1` (`rental_order_id`),
  KEY `d4_6_i2` (`check_status`,`warning_at`),
  KEY `d4_6_i3` (`check_status`,`auto_close_at`),
  CONSTRAINT `d4_6_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_6_c2` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_6_c3` CHECK (COALESCE((REGEXP_LIKE(factory_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_6_c4` CHECK (COALESCE(((return_shipment_id IS NULL OR REGEXP_LIKE(return_shipment_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_6_c5` CHECK (COALESCE((check_status IN ('pending','warning','no_issue','claim_submitted','auto_closed','admin_closed')),FALSE)=TRUE),
  CONSTRAINT `d4_6_c6` CHECK (COALESCE((check_start_source IN ('sent_12h','expected_arrival','factory_scan','admin_confirm')),FALSE)=TRUE),
  CONSTRAINT `d4_6_c7` CHECK (COALESCE(((operator_user_id IS NULL OR REGEXP_LIKE(operator_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_6_c8` CHECK (COALESCE((claim_created IN (0,1)),FALSE)=TRUE),
  CONSTRAINT `d4_6_c9` CHECK (COALESCE(((close_reason_code IS NULL OR close_reason_code IN ('factory_no_issue','timeout_no_claim','admin_force_close'))),FALSE)=TRUE),
  CONSTRAINT `d4_6_c10` CHECK (COALESCE(((closed_by_user_id IS NULL OR REGEXP_LIKE(closed_by_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_6_c11` CHECK (COALESCE((version>=0),FALSE)=TRUE),
  CONSTRAINT `d4_6_c12` CHECK (COALESCE(((check_status IN ('no_issue','auto_closed','admin_closed') AND closed_at IS NOT NULL AND closed_by_user_id IS NOT NULL) OR (check_status NOT IN ('no_issue','auto_closed','admin_closed') AND closed_at IS NULL)),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_return_item_check` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '归还明细核验ID',
  `return_check_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '归还核验ID',
  `rental_order_item_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '订单明细ID',
  `expected_quantity` INT NOT NULL COMMENT '应归还数量',
  `checked_quantity` INT NOT NULL DEFAULT 0 COMMENT '已核对数量',
  `version` INT NOT NULL DEFAULT 0 COMMENT '并发版本',
  `operator_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最近操作者',
  `completed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '核对完成时间',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_7_i1` (`return_check_id`,`rental_order_item_id`),
  CONSTRAINT `d4_7_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_7_c2` CHECK (COALESCE((REGEXP_LIKE(return_check_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_7_c3` CHECK (COALESCE((REGEXP_LIKE(rental_order_item_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_7_c4` CHECK (COALESCE((expected_quantity>=0),FALSE)=TRUE),
  CONSTRAINT `d4_7_c5` CHECK (COALESCE((checked_quantity>=0),FALSE)=TRUE),
  CONSTRAINT `d4_7_c6` CHECK (COALESCE((version>=0),FALSE)=TRUE),
  CONSTRAINT `d4_7_c7` CHECK (COALESCE(((operator_user_id IS NULL OR REGEXP_LIKE(operator_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_7_c8` CHECK (COALESCE((expected_quantity>0 AND checked_quantity<=expected_quantity),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_return_scan_record` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '归还扫描ID',
  `scan_request_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本次扫码请求号',
  `return_check_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '归还核验ID',
  `rental_order_item_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '订单明细ID',
  `prop_qr_code_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '本次有效码ID',
  `prop_sn_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实物ID',
  `operator_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作者',
  `scanned_at` DATETIME(3) NOT NULL COMMENT '扫码时间',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_8_i1` (`scan_request_id`),
  UNIQUE KEY `d4_8_i2` (`return_check_id`,`prop_sn_id`),
  KEY `d4_8_i3` (`return_check_id`,`rental_order_item_id`,`scanned_at`),
  CONSTRAINT `d4_8_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_8_c2` CHECK (COALESCE((REGEXP_LIKE(return_check_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_8_c3` CHECK (COALESCE((REGEXP_LIKE(rental_order_item_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_8_c4` CHECK (COALESCE((REGEXP_LIKE(prop_qr_code_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_8_c5` CHECK (COALESCE((REGEXP_LIKE(prop_sn_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_8_c6` CHECK (COALESCE((REGEXP_LIKE(operator_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_evidence_record` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '证据 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `rental_order_item_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁订单明细 ID',
  `transport_leg_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '运输段 ID',
  `transport_expense_statement_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '往返费用申报 ID',
  `issue_report_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '问题上报 ID',
  `return_check_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '归还核验 ID',
  `claim_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '索赔 ID',
  `evidence_node` VARCHAR(32) NOT NULL COMMENT '证据节点',
  `evidence_type` VARCHAR(32) NOT NULL COMMENT '证据类型',
  `evidence_scene` VARCHAR(32) NULL DEFAULT NULL COMMENT '证据场景',
  `file_url` VARCHAR(512) NULL DEFAULT NULL COMMENT '旧地址兼容位',
  `file_storage_key` VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '对象存储 key',
  `upload_receipt_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '原上传凭证ID',
  `file_version_id` VARCHAR(256) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'COS对象版本',
  `draft_removed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '草稿移除时间',
  `text_content` VARCHAR(1000) NULL DEFAULT NULL COMMENT '文本说明',
  `uploaded_by_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '上传用户 ID',
  `uploaded_role` VARCHAR(32) NOT NULL COMMENT '上传角色',
  `captured_at` DATETIME(3) NULL DEFAULT NULL COMMENT '拍摄/发生时间',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_9_i1` (`upload_receipt_id`),
  KEY `d4_9_i2` (`rental_order_id`,`evidence_node`,`created_at`),
  KEY `d4_9_i3` (`issue_report_id`,`created_at`),
  KEY `d4_9_i4` (`return_check_id`,`created_at`),
  KEY `d4_9_i5` (`claim_id`,`created_at`),
  CONSTRAINT `d4_9_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_9_c2` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_9_c3` CHECK (COALESCE(((rental_order_item_id IS NULL OR REGEXP_LIKE(rental_order_item_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_9_c4` CHECK (COALESCE(((transport_leg_id IS NULL OR REGEXP_LIKE(transport_leg_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_9_c5` CHECK (COALESCE(((transport_expense_statement_id IS NULL OR REGEXP_LIKE(transport_expense_statement_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_9_c6` CHECK (COALESCE(((issue_report_id IS NULL OR REGEXP_LIKE(issue_report_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_9_c7` CHECK (COALESCE(((return_check_id IS NULL OR REGEXP_LIKE(return_check_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_9_c8` CHECK (COALESCE(((claim_id IS NULL OR REGEXP_LIKE(claim_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_9_c9` CHECK (COALESCE((evidence_node IN ('transport_quote','transport_expense','outbound','handover','receipt_check','in_use_report','return_sent','return_check','claim','arbitration')),FALSE)=TRUE),
  CONSTRAINT `d4_9_c10` CHECK (COALESCE((evidence_type IN ('photo','video','text','file','scan')),FALSE)=TRUE),
  CONSTRAINT `d4_9_c11` CHECK (COALESCE(((upload_receipt_id IS NULL OR REGEXP_LIKE(upload_receipt_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_9_c12` CHECK (COALESCE((REGEXP_LIKE(uploaded_by_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_9_c13` CHECK (COALESCE((uploaded_role IN ('buyer','factory','admin','system')),FALSE)=TRUE),
  CONSTRAINT `d4_9_c14` CHECK (COALESCE(((file_storage_key IS NULL AND upload_receipt_id IS NULL) OR (file_storage_key IS NOT NULL AND upload_receipt_id IS NOT NULL)),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_fulfillment_event` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件 ID',
  `event_idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '事件幂等键',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '租赁订单 ID',
  `transport_leg_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '运输段 ID',
  `source_type` VARCHAR(64) NULL DEFAULT NULL COMMENT '来源对象类型',
  `source_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '来源对象 ID',
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
  `event_time` DATETIME(3) NOT NULL COMMENT '事件时间',
  `operator_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '操作人 ID',
  `operator_role` VARCHAR(32) NOT NULL COMMENT '操作角色',
  `processed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '后续事件处理完成时间',
  `event_payload_json` JSON NULL DEFAULT NULL COMMENT '事件载荷',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_10_i1` (`event_idempotency_key`),
  KEY `d4_10_i2` (`rental_order_id`,`event_time`),
  KEY `d4_10_i3` (`source_type`,`source_id`,`event_type`),
  KEY `d4_10_i4` (`event_type`,`processed_at`,`event_time`,`id`),
  CONSTRAINT `d4_10_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_10_c2` CHECK (COALESCE((REGEXP_LIKE(rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_10_c3` CHECK (COALESCE(((transport_leg_id IS NULL OR REGEXP_LIKE(transport_leg_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_10_c4` CHECK (COALESCE(((source_id IS NULL OR REGEXP_LIKE(source_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_10_c5` CHECK (COALESCE(((operator_user_id IS NULL OR REGEXP_LIKE(operator_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_10_c6` CHECK (COALESCE((operator_role IN ('buyer','factory','admin','system')),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `order_fulfillment_risk` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '风险 ID',
  `source_rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源逾期订单',
  `risk_idempotency_key` CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '影响关系去重键',
  `risk_type` VARCHAR(32) NOT NULL COMMENT '风险类型',
  `source_reservation_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '来源库存占用 ID',
  `affected_rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '受影响租赁订单 ID',
  `affected_order_item_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '受影响订单明细 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `sn_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'SN ID',
  `risk_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '风险状态',
  `suggested_action` VARCHAR(64) NULL DEFAULT NULL COMMENT '建议处理动作',
  `admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '处理管理员 ID',
  `resolution_type` VARCHAR(64) NULL DEFAULT NULL COMMENT '处理结果',
  `resolution_remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '处理备注',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `resolved_at` DATETIME(3) NULL DEFAULT NULL COMMENT '解决时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `d4_12_i1` (`risk_idempotency_key`),
  KEY `d4_12_i2` (`risk_status`,`created_at`),
  KEY `d4_12_i3` (`affected_rental_order_id`,`risk_status`),
  KEY `d4_12_i4` (`sku_id`,`sn_id`,`risk_status`),
  CONSTRAINT `d4_12_c1` CHECK (COALESCE((REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_12_c2` CHECK (COALESCE((REGEXP_LIKE(source_rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_12_c3` CHECK (COALESCE((risk_type IN ('overdue_impact')),FALSE)=TRUE),
  CONSTRAINT `d4_12_c4` CHECK (COALESCE(((source_reservation_id IS NULL OR REGEXP_LIKE(source_reservation_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_12_c5` CHECK (COALESCE((REGEXP_LIKE(affected_rental_order_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_12_c6` CHECK (COALESCE(((affected_order_item_id IS NULL OR REGEXP_LIKE(affected_order_item_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_12_c7` CHECK (COALESCE((REGEXP_LIKE(sku_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE)=TRUE),
  CONSTRAINT `d4_12_c8` CHECK (COALESCE(((sn_id IS NULL OR REGEXP_LIKE(sn_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE),
  CONSTRAINT `d4_12_c9` CHECK (COALESCE((risk_status IN ('pending','processing','resolved','closed')),FALSE)=TRUE),
  CONSTRAINT `d4_12_c10` CHECK (COALESCE(((admin_user_id IS NULL OR REGEXP_LIKE(admin_user_id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))),FALSE)=TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `order_outbound_record` ADD CONSTRAINT `d4_1_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_record` ADD CONSTRAINT `d4_1_f2` FOREIGN KEY (`outbound_transport_leg_id`) REFERENCES `order_transport_leg` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_record` ADD CONSTRAINT `d4_1_f3` FOREIGN KEY (`operator_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_record` ADD CONSTRAINT `d4_1_f4` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_item_check` ADD CONSTRAINT `d4_2_f1` FOREIGN KEY (`outbound_record_id`) REFERENCES `order_outbound_record` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_item_check` ADD CONSTRAINT `d4_2_f2` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_item_check` ADD CONSTRAINT `d4_2_f3` FOREIGN KEY (`rental_order_item_id`) REFERENCES `rental_order_item` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_item_check` ADD CONSTRAINT `d4_2_f4` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_item_check` ADD CONSTRAINT `d4_2_f5` FOREIGN KEY (`operator_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_scan_record` ADD CONSTRAINT `d4_3_f1` FOREIGN KEY (`outbound_record_id`) REFERENCES `order_outbound_record` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_scan_record` ADD CONSTRAINT `d4_3_f2` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_scan_record` ADD CONSTRAINT `d4_3_f3` FOREIGN KEY (`rental_order_item_id`) REFERENCES `rental_order_item` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_scan_record` ADD CONSTRAINT `d4_3_f4` FOREIGN KEY (`prop_qr_code_id`) REFERENCES `prop_qr_code` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_scan_record` ADD CONSTRAINT `d4_3_f5` FOREIGN KEY (`prop_sn_id`) REFERENCES `prop_sn_instance` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_outbound_scan_record` ADD CONSTRAINT `d4_3_f6` FOREIGN KEY (`operator_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_receipt_check` ADD CONSTRAINT `d4_4_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_receipt_check` ADD CONSTRAINT `d4_4_f2` FOREIGN KEY (`outbound_transport_leg_id`) REFERENCES `order_transport_leg` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_receipt_check` ADD CONSTRAINT `d4_4_f3` FOREIGN KEY (`buyer_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_shipment` ADD CONSTRAINT `d4_5_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_shipment` ADD CONSTRAINT `d4_5_f2` FOREIGN KEY (`return_transport_leg_id`) REFERENCES `order_transport_leg` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_shipment` ADD CONSTRAINT `d4_5_f3` FOREIGN KEY (`buyer_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_shipment` ADD CONSTRAINT `d4_5_f4` FOREIGN KEY (`operator_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_check` ADD CONSTRAINT `d4_6_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_check` ADD CONSTRAINT `d4_6_f2` FOREIGN KEY (`return_shipment_id`) REFERENCES `order_return_shipment` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_check` ADD CONSTRAINT `d4_6_f3` FOREIGN KEY (`operator_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_check` ADD CONSTRAINT `d4_6_f4` FOREIGN KEY (`closed_by_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_check` ADD CONSTRAINT `d4_6_f5` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_item_check` ADD CONSTRAINT `d4_7_f1` FOREIGN KEY (`return_check_id`) REFERENCES `order_return_check` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_item_check` ADD CONSTRAINT `d4_7_f2` FOREIGN KEY (`rental_order_item_id`) REFERENCES `rental_order_item` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_item_check` ADD CONSTRAINT `d4_7_f3` FOREIGN KEY (`operator_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_scan_record` ADD CONSTRAINT `d4_8_f1` FOREIGN KEY (`return_check_id`) REFERENCES `order_return_check` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_scan_record` ADD CONSTRAINT `d4_8_f2` FOREIGN KEY (`rental_order_item_id`) REFERENCES `rental_order_item` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_scan_record` ADD CONSTRAINT `d4_8_f3` FOREIGN KEY (`prop_qr_code_id`) REFERENCES `prop_qr_code` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_scan_record` ADD CONSTRAINT `d4_8_f4` FOREIGN KEY (`prop_sn_id`) REFERENCES `prop_sn_instance` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_return_scan_record` ADD CONSTRAINT `d4_8_f5` FOREIGN KEY (`operator_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_evidence_record` ADD CONSTRAINT `d4_9_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_evidence_record` ADD CONSTRAINT `d4_9_f2` FOREIGN KEY (`rental_order_item_id`) REFERENCES `rental_order_item` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_evidence_record` ADD CONSTRAINT `d4_9_f3` FOREIGN KEY (`transport_leg_id`) REFERENCES `order_transport_leg` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_evidence_record` ADD CONSTRAINT `d4_9_f5` FOREIGN KEY (`return_check_id`) REFERENCES `order_return_check` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_evidence_record` ADD CONSTRAINT `d4_9_f6` FOREIGN KEY (`uploaded_by_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_fulfillment_event` ADD CONSTRAINT `d4_10_f1` FOREIGN KEY (`rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_fulfillment_event` ADD CONSTRAINT `d4_10_f2` FOREIGN KEY (`transport_leg_id`) REFERENCES `order_transport_leg` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_fulfillment_event` ADD CONSTRAINT `d4_10_f3` FOREIGN KEY (`operator_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_fulfillment_risk` ADD CONSTRAINT `d4_12_f1` FOREIGN KEY (`source_rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_fulfillment_risk` ADD CONSTRAINT `d4_12_f2` FOREIGN KEY (`affected_rental_order_id`) REFERENCES `rental_order` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_fulfillment_risk` ADD CONSTRAINT `d4_12_f3` FOREIGN KEY (`affected_order_item_id`) REFERENCES `rental_order_item` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_fulfillment_risk` ADD CONSTRAINT `d4_12_f4` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_fulfillment_risk` ADD CONSTRAINT `d4_12_f5` FOREIGN KEY (`sn_id`) REFERENCES `prop_sn_instance` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `order_fulfillment_risk` ADD CONSTRAINT `d4_12_f6` FOREIGN KEY (`admin_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;
