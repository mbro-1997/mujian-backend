-- C structure; applied predecessors remain immutable.
CREATE TABLE `prop_sku` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属工厂 ID',
  `sku_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU 编码',
  `prop_name` VARCHAR(128) NOT NULL COMMENT '道具名称',
  `asset_type` VARCHAR(32) NOT NULL COMMENT '资产类型',
  `image_url` VARCHAR(512) NOT NULL COMMENT '主图 URL',
  `type_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '类别编码',
  `size_desc` VARCHAR(128) NOT NULL COMMENT '尺寸描述',
  `length_cm` DECIMAL(10,2) NOT NULL COMMENT '长，单位 cm',
  `width_cm` DECIMAL(10,2) NOT NULL COMMENT '宽，单位 cm',
  `height_cm` DECIMAL(10,2) NOT NULL COMMENT '高，单位 cm',
  `material_desc` VARCHAR(128) NOT NULL COMMENT '材质',
  `fire_resistant_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '是否阻燃编码',
  `weight_kg` DECIMAL(10,2) NOT NULL COMMENT '重量，单位 kg',
  `transport_suggestion` VARCHAR(255) NOT NULL COMMENT '运输建议',
  `daily_rent_price_fen` INT NOT NULL COMMENT '日租金，单位分',
  `deposit_amount_fen` INT NOT NULL COMMENT '押金，单位分',
  `sku_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU 状态',
  `fill_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '资料填充状态',
  `remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '当前资料版本',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c1_code` (`sku_code`),
  KEY `c1_owner` (`factory_id`,`sku_status`,`updated_at`,`id`),
  KEY `c1_browse` (`sku_status`,`type_code`,`created_at`,`id`),
  CONSTRAINT `ck_c1_1` CHECK (COALESCE((daily_rent_price_fen>0 AND deposit_amount_fen>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c1_2` CHECK (COALESCE((length_cm>0 AND width_cm>0 AND height_cm>0 AND weight_kg>0),FALSE) = TRUE),
  CONSTRAINT `ck_c1_3` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c1_4` CHECK (COALESCE((`factory_id` IS NULL OR REGEXP_LIKE(`factory_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c1_5` CHECK (COALESCE((`asset_type` IS NULL OR `asset_type` IN ('A_ASSET','B_CONSUMABLE')),FALSE) = TRUE),
  CONSTRAINT `ck_c1_6` CHECK (COALESCE((`sku_status` IS NULL OR `sku_status` IN ('draft','offline','online','disabled')),FALSE) = TRUE),
  CONSTRAINT `ck_c1_7` CHECK (COALESCE((`fill_status` IS NULL OR `fill_status` IN ('pending_fill','filled')),FALSE) = TRUE),
  CONSTRAINT `ck_c1_8` CHECK (COALESCE((`version`>=0),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prop_image` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '图片 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `image_url` VARCHAR(512) NOT NULL COMMENT '图片 URL',
  `sort_order` INT NOT NULL COMMENT '排序',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c2_position` (`sku_id`,`sort_order`),
  CONSTRAINT `ck_c2_1` CHECK (COALESCE((sort_order BETWEEN 0 AND 3),FALSE) = TRUE),
  CONSTRAINT `ck_c2_2` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c2_3` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prop_sku_audit` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审核 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `applicant_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申请人用户 ID',
  `action_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申请动作',
  `before_sku_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '申请前 SKU 状态',
  `target_sku_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '审核通过后的目标 SKU 状态',
  `before_snapshot_json` JSON NULL DEFAULT NULL COMMENT '提交前 SKU 快照',
  `proposed_snapshot_json` JSON NOT NULL COMMENT '拟生效 SKU 快照',
  `initial_sn_quantity` INT NULL DEFAULT NULL COMMENT 'A 类初始实物件数',
  `initial_stock_quantity` INT NULL DEFAULT NULL COMMENT 'B 类初始库存数量',
  `audit_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审核状态',
  `pending_sku_guard` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN audit_status='pending' THEN sku_id ELSE NULL END) STORED COMMENT '待审核 SKU 唯一守卫',
  `apply_remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '申请说明',
  `audit_remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '审核备注',
  `reject_reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '驳回原因',
  `reviewer_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '审核管理员 ID',
  `submitted_at` DATETIME(3) NOT NULL COMMENT '提交时间',
  `reviewed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '审核时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `request_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '提交请求编号',
  `request_hash` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '提交摘要',
  `previous_audit_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '上一驳回或撤回申请',
  `base_sku_version` INT NOT NULL DEFAULT 0 COMMENT '提交时SKU资料版本',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c3_request` (`applicant_user_id`,`request_id`),
  UNIQUE KEY `c3_pending` (`pending_sku_guard`),
  KEY `c3_queue` (`audit_status`,`submitted_at`,`id`),
  KEY `c3_history` (`sku_id`,`submitted_at`,`id`),
  CONSTRAINT `ck_c3_1` CHECK (COALESCE(((action_type='create' AND ((initial_sn_quantity>0 AND initial_stock_quantity IS NULL) OR (initial_stock_quantity>0 AND initial_sn_quantity IS NULL))) OR (action_type<>'create' AND initial_sn_quantity IS NULL AND initial_stock_quantity IS NULL)),FALSE) = TRUE),
  CONSTRAINT `ck_c3_2` CHECK (COALESCE((JSON_TYPE(proposed_snapshot_json)='OBJECT' AND JSON_CONTAINS_PATH(proposed_snapshot_json,'all','$.schema_version','$.fields','$.asset_type')=1),FALSE) = TRUE),
  CONSTRAINT `ck_c3_3` CHECK (COALESCE(((audit_status='pending' AND reviewed_at IS NULL AND reviewer_id IS NULL AND version=0) OR (audit_status<>'pending' AND reviewed_at IS NOT NULL AND reviewer_id IS NOT NULL AND version>=1)),FALSE) = TRUE),
  CONSTRAINT `ck_c3_4` CHECK (COALESCE(((audit_status='rejected' AND reject_reason IS NOT NULL AND CHAR_LENGTH(TRIM(reject_reason))>0) OR (audit_status<>'rejected' AND reject_reason IS NULL)),FALSE) = TRUE),
  CONSTRAINT `ck_c3_5` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c3_6` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c3_7` CHECK (COALESCE((`applicant_user_id` IS NULL OR REGEXP_LIKE(`applicant_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c3_8` CHECK (COALESCE((`action_type` IS NULL OR `action_type` IN ('create','update','up','down','disable','restore')),FALSE) = TRUE),
  CONSTRAINT `ck_c3_9` CHECK (COALESCE((`before_sku_status` IS NULL OR `before_sku_status` IN ('draft','offline','online','disabled')),FALSE) = TRUE),
  CONSTRAINT `ck_c3_10` CHECK (COALESCE((`target_sku_status` IS NULL OR `target_sku_status` IN ('draft','offline','online','disabled')),FALSE) = TRUE),
  CONSTRAINT `ck_c3_11` CHECK (COALESCE((`audit_status` IS NULL OR `audit_status` IN ('pending','approved','rejected','cancelled')),FALSE) = TRUE),
  CONSTRAINT `ck_c3_12` CHECK (COALESCE((`pending_sku_guard` IS NULL OR REGEXP_LIKE(`pending_sku_guard`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c3_13` CHECK (COALESCE((`reviewer_id` IS NULL OR REGEXP_LIKE(`reviewer_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c3_14` CHECK (COALESCE((`version`>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c3_15` CHECK (COALESCE((`request_id` IS NULL OR REGEXP_LIKE(`request_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c3_16` CHECK (COALESCE((request_hash IS NULL OR REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c3_17` CHECK (COALESCE((`previous_audit_id` IS NULL OR REGEXP_LIKE(`previous_audit_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c3_18` CHECK (COALESCE((`base_sku_version`>=0),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prop_sku_style` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '记录 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `style_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '风格编码',
  `sort_order` INT NOT NULL COMMENT '排序',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c4_code` (`sku_id`,`style_code`),
  UNIQUE KEY `c4_position` (`sku_id`,`sort_order`),
  KEY `c4_filter` (`style_code`,`sku_id`),
  CONSTRAINT `ck_c4_1` CHECK (COALESCE((sort_order BETWEEN 0 AND 2),FALSE) = TRUE),
  CONSTRAINT `ck_c4_2` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c4_3` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prop_sn_instance` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SN ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '所属 SKU ID',
  `source_adjustment_audit_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源实物调整审核 ID',
  `sn_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SN 编号',
  `qr_code_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '二维码业务编号',
  `instance_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实物状态',
  `current_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '当前关联订单 ID',
  `status_remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '状态备注',
  `status_changed_at` DATETIME(3) NOT NULL COMMENT '状态变更时间',
  `status_changed_by` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '状态变更操作人',
  `remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT 'SN状态版本',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c5_code` (`sn_code`),
  UNIQUE KEY `c5_qr` (`qr_code_id`),
  KEY `c5_pool` (`sku_id`,`instance_status`,`id`),
  KEY `c5_source` (`source_adjustment_audit_id`),
  CONSTRAINT `ck_c5_1` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c5_2` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c5_3` CHECK (COALESCE((`source_adjustment_audit_id` IS NULL OR REGEXP_LIKE(`source_adjustment_audit_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c5_4` CHECK (COALESCE((`instance_status` IS NULL OR `instance_status` IN ('pending_label','idle','locked','renting','repairing','lost','scrapped','retired')),FALSE) = TRUE),
  CONSTRAINT `ck_c5_5` CHECK (COALESCE((`current_order_id` IS NULL OR REGEXP_LIKE(`current_order_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c5_6` CHECK (COALESCE((`status_changed_by` IS NULL OR REGEXP_LIKE(`status_changed_by`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c5_7` CHECK (COALESCE((`version`>=0),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prop_sn_adjustment_audit` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审核 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `adjustment_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调整类型',
  `increase_scene` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '增加场景',
  `requested_quantity` INT NOT NULL COMMENT '变化件数',
  `audit_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审核状态',
  `applicant_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申请人用户 ID',
  `reviewer_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '审核管理员 ID',
  `apply_remark` VARCHAR(255) NOT NULL COMMENT '申请说明',
  `audit_remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '审核备注',
  `reject_reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '驳回原因',
  `submitted_at` DATETIME(3) NOT NULL COMMENT '提交时间',
  `reviewed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '审核时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `request_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '提交请求编号',
  `request_hash` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '提交摘要',
  `related_sku_audit_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '首次来源SKU审核',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c6_request` (`applicant_user_id`,`request_id`),
  UNIQUE KEY `c6_initial` (`related_sku_audit_id`),
  KEY `c6_queue` (`audit_status`,`submitted_at`,`id`),
  KEY `c6_owner` (`sku_id`,`audit_status`,`id`),
  CONSTRAINT `ck_c6_1` CHECK (COALESCE((requested_quantity>0),FALSE) = TRUE),
  CONSTRAINT `ck_c6_2` CHECK (COALESCE(((adjustment_type='increase' AND increase_scene IS NOT NULL) OR (adjustment_type<>'increase' AND increase_scene IS NULL)),FALSE) = TRUE),
  CONSTRAINT `ck_c6_3` CHECK (COALESCE(((increase_scene='initial' AND related_sku_audit_id IS NOT NULL AND request_id IS NULL AND request_hash IS NULL AND audit_status='approved') OR (COALESCE(increase_scene,'')<>'initial' AND related_sku_audit_id IS NULL AND request_id IS NOT NULL AND request_hash IS NOT NULL)),FALSE) = TRUE),
  CONSTRAINT `ck_c6_4` CHECK (COALESCE(((audit_status='pending' AND reviewed_at IS NULL AND reviewer_id IS NULL AND version=0) OR (audit_status<>'pending' AND reviewed_at IS NOT NULL AND reviewer_id IS NOT NULL AND version>=1)),FALSE) = TRUE),
  CONSTRAINT `ck_c6_5` CHECK (COALESCE(((audit_status='rejected' AND reject_reason IS NOT NULL AND CHAR_LENGTH(TRIM(reject_reason))>0) OR (audit_status<>'rejected' AND reject_reason IS NULL)),FALSE) = TRUE),
  CONSTRAINT `ck_c6_6` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c6_7` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c6_8` CHECK (COALESCE((`factory_id` IS NULL OR REGEXP_LIKE(`factory_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c6_9` CHECK (COALESCE((`adjustment_type` IS NULL OR `adjustment_type` IN ('increase','decrease','repair','abnormal_decrease')),FALSE) = TRUE),
  CONSTRAINT `ck_c6_10` CHECK (COALESCE((`increase_scene` IS NULL OR `increase_scene` IN ('initial','additional')),FALSE) = TRUE),
  CONSTRAINT `ck_c6_11` CHECK (COALESCE((`audit_status` IS NULL OR `audit_status` IN ('pending','approved','rejected','cancelled')),FALSE) = TRUE),
  CONSTRAINT `ck_c6_12` CHECK (COALESCE((`applicant_user_id` IS NULL OR REGEXP_LIKE(`applicant_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c6_13` CHECK (COALESCE((`reviewer_id` IS NULL OR REGEXP_LIKE(`reviewer_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c6_14` CHECK (COALESCE((`version`>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c6_15` CHECK (COALESCE((`request_id` IS NULL OR REGEXP_LIKE(`request_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c6_16` CHECK (COALESCE((request_hash IS NULL OR REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c6_17` CHECK (COALESCE((`related_sku_audit_id` IS NULL OR REGEXP_LIKE(`related_sku_audit_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prop_sn_adjustment_item` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '明细 ID',
  `adjustment_audit_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '实物调整审核 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `sn_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SN ID',
  `sn_code_snapshot` VARCHAR(64) NOT NULL COMMENT 'SN 编号快照',
  `before_instance_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调整前实物状态',
  `target_instance_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标实物状态',
  `item_remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '明细说明',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c7_item` (`adjustment_audit_id`,`sn_id`),
  KEY `c7_conflict` (`sn_id`,`adjustment_audit_id`),
  CONSTRAINT `ck_c7_1` CHECK (COALESCE((before_instance_status<>target_instance_status),FALSE) = TRUE),
  CONSTRAINT `ck_c7_2` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c7_3` CHECK (COALESCE((`adjustment_audit_id` IS NULL OR REGEXP_LIKE(`adjustment_audit_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c7_4` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c7_5` CHECK (COALESCE((`sn_id` IS NULL OR REGEXP_LIKE(`sn_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c7_6` CHECK (COALESCE((`before_instance_status` IS NULL OR `before_instance_status` IN ('pending_label','idle','locked','renting','repairing','lost','scrapped','retired')),FALSE) = TRUE),
  CONSTRAINT `ck_c7_7` CHECK (COALESCE((`target_instance_status` IS NULL OR `target_instance_status` IN ('retired','repairing','lost','scrapped')),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prop_instance_status_log` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '日志 ID',
  `prop_instance_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SN ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `sn_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'SN 编号快照',
  `qr_code_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '二维码编号快照',
  `from_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '变更前状态',
  `to_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更后状态',
  `source_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更来源类型',
  `reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '变更原因',
  `operator_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人用户 ID',
  `operator_role` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人角色',
  `related_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关联订单 ID',
  `related_adjustment_audit_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关联实物调整审核 ID',
  `related_arbitration_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关联仲裁 ID',
  `business_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '效果唯一键',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c8_effect` (`business_key`),
  KEY `c8_history` (`prop_instance_id`,`created_at`,`id`),
  CONSTRAINT `ck_c8_1` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c8_2` CHECK (COALESCE((`prop_instance_id` IS NULL OR REGEXP_LIKE(`prop_instance_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c8_3` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c8_4` CHECK (COALESCE((`from_status` IS NULL OR `from_status` IN ('pending_label','idle','locked','renting','repairing','lost','scrapped','retired')),FALSE) = TRUE),
  CONSTRAINT `ck_c8_5` CHECK (COALESCE((`to_status` IS NULL OR `to_status` IN ('pending_label','idle','locked','renting','repairing','lost','scrapped','retired')),FALSE) = TRUE),
  CONSTRAINT `ck_c8_6` CHECK (COALESCE((`source_type` IS NULL OR `source_type` IN ('manual_adjustment','repair_complete','order_fulfillment','arbitration','system_correction','label_confirm')),FALSE) = TRUE),
  CONSTRAINT `ck_c8_7` CHECK (COALESCE((`operator_user_id` IS NULL OR REGEXP_LIKE(`operator_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c8_8` CHECK (COALESCE((`operator_role` IS NULL OR `operator_role` IN ('factory','admin','system')),FALSE) = TRUE),
  CONSTRAINT `ck_c8_9` CHECK (COALESCE((`related_order_id` IS NULL OR REGEXP_LIKE(`related_order_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c8_10` CHECK (COALESCE((`related_adjustment_audit_id` IS NULL OR REGEXP_LIKE(`related_adjustment_audit_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c8_11` CHECK (COALESCE((`related_arbitration_id` IS NULL OR REGEXP_LIKE(`related_arbitration_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prop_sku_stock` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '库存 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `total_quantity` INT NOT NULL COMMENT '总数量',
  `rentable_base_quantity` INT NOT NULL COMMENT '可租库存基数',
  `available_quantity` INT NOT NULL COMMENT '当前可用数量',
  `locked_quantity` INT NOT NULL COMMENT '已锁定数量',
  `renting_quantity` INT NOT NULL COMMENT '租赁中数量',
  `lost_quantity` INT NOT NULL COMMENT '丢失数量',
  `scrapped_quantity` INT NOT NULL COMMENT '报废数量',
  `last_counted_at` DATETIME(3) NULL DEFAULT NULL COMMENT '最近盘点时间',
  `last_counted_by` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '最近盘点人',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c9_sku` (`sku_id`),
  CONSTRAINT `ck_c9_1` CHECK (COALESCE((total_quantity>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c9_2` CHECK (COALESCE((rentable_base_quantity>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c9_3` CHECK (COALESCE((available_quantity>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c9_4` CHECK (COALESCE((locked_quantity>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c9_5` CHECK (COALESCE((renting_quantity>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c9_6` CHECK (COALESCE((lost_quantity>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c9_7` CHECK (COALESCE((scrapped_quantity>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c9_8` CHECK (COALESCE((rentable_base_quantity=total_quantity),FALSE) = TRUE),
  CONSTRAINT `ck_c9_9` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c9_10` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c9_11` CHECK (COALESCE((`last_counted_by` IS NULL OR REGEXP_LIKE(`last_counted_by`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prop_stock_adjustment_audit` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审核 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `asset_type` VARCHAR(32) NOT NULL COMMENT '资产类型',
  `adjustment_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '调整类型',
  `quantity_delta` INT NOT NULL COMMENT '变化数量',
  `abnormal_result` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '异常扣减结果',
  `before_total_quantity` INT NOT NULL COMMENT '调整前总数量',
  `before_available_quantity` INT NOT NULL COMMENT '调整前可用数量',
  `reason` VARCHAR(255) NOT NULL COMMENT '申请原因',
  `audit_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审核状态',
  `applicant_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申请人用户 ID',
  `reviewer_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '审核管理员 ID',
  `audit_remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '审核备注',
  `reject_reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '驳回原因',
  `related_movement_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关联库存流水 ID',
  `related_sku_audit_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '来源 SKU 审核 ID',
  `submitted_at` DATETIME(3) NOT NULL COMMENT '提交时间',
  `reviewed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '审核时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `request_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '提交请求编号',
  `request_hash` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '提交摘要',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c10_request` (`applicant_user_id`,`request_id`),
  UNIQUE KEY `c10_initial` (`related_sku_audit_id`),
  KEY `c10_queue` (`audit_status`,`submitted_at`,`id`),
  KEY `c10_owner` (`sku_id`,`submitted_at`,`id`),
  CONSTRAINT `ck_c10_1` CHECK (COALESCE(((adjustment_type='increase' AND quantity_delta>0) OR (adjustment_type<>'increase' AND quantity_delta<0)),FALSE) = TRUE),
  CONSTRAINT `ck_c10_2` CHECK (COALESCE(((adjustment_type='abnormal_decrease' AND abnormal_result IS NOT NULL) OR (adjustment_type<>'abnormal_decrease' AND abnormal_result IS NULL)),FALSE) = TRUE),
  CONSTRAINT `ck_c10_3` CHECK (COALESCE(((related_sku_audit_id IS NOT NULL AND adjustment_type='increase' AND audit_status='approved' AND request_id IS NULL AND request_hash IS NULL) OR (related_sku_audit_id IS NULL AND request_id IS NOT NULL AND request_hash IS NOT NULL)),FALSE) = TRUE),
  CONSTRAINT `ck_c10_4` CHECK (COALESCE((before_total_quantity>=0 AND before_available_quantity>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c10_5` CHECK (COALESCE(((audit_status='pending' AND reviewed_at IS NULL AND reviewer_id IS NULL AND version=0) OR (audit_status<>'pending' AND reviewed_at IS NOT NULL AND reviewer_id IS NOT NULL AND version>=1)),FALSE) = TRUE),
  CONSTRAINT `ck_c10_6` CHECK (COALESCE(((audit_status='rejected' AND reject_reason IS NOT NULL AND CHAR_LENGTH(TRIM(reject_reason))>0) OR (audit_status<>'rejected' AND reject_reason IS NULL)),FALSE) = TRUE),
  CONSTRAINT `ck_c10_7` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c10_8` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c10_9` CHECK (COALESCE((`asset_type` IS NULL OR `asset_type` IN ('B_CONSUMABLE')),FALSE) = TRUE),
  CONSTRAINT `ck_c10_10` CHECK (COALESCE((`adjustment_type` IS NULL OR `adjustment_type` IN ('increase','decrease','abnormal_decrease')),FALSE) = TRUE),
  CONSTRAINT `ck_c10_11` CHECK (COALESCE((`abnormal_result` IS NULL OR `abnormal_result` IN ('lost','scrapped')),FALSE) = TRUE),
  CONSTRAINT `ck_c10_12` CHECK (COALESCE((`audit_status` IS NULL OR `audit_status` IN ('pending','approved','rejected','cancelled')),FALSE) = TRUE),
  CONSTRAINT `ck_c10_13` CHECK (COALESCE((`applicant_user_id` IS NULL OR REGEXP_LIKE(`applicant_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c10_14` CHECK (COALESCE((`reviewer_id` IS NULL OR REGEXP_LIKE(`reviewer_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c10_15` CHECK (COALESCE((`related_movement_id` IS NULL OR REGEXP_LIKE(`related_movement_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c10_16` CHECK (COALESCE((`related_sku_audit_id` IS NULL OR REGEXP_LIKE(`related_sku_audit_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c10_17` CHECK (COALESCE((`version`>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c10_18` CHECK (COALESCE((`request_id` IS NULL OR REGEXP_LIKE(`request_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c10_19` CHECK (COALESCE((request_hash IS NULL OR REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prop_stock_movement` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '流水 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `movement_type` VARCHAR(32) NOT NULL COMMENT '流水类型',
  `source_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '来源类型',
  `quantity` INT NOT NULL COMMENT '变动数量',
  `before_quantity` INT NULL DEFAULT NULL COMMENT '变动前数量',
  `after_quantity` INT NULL DEFAULT NULL COMMENT '变动后数量',
  `before_stock_snapshot_json` JSON NOT NULL COMMENT '变动前库存快照',
  `after_stock_snapshot_json` JSON NOT NULL COMMENT '变动后库存快照',
  `related_audit_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关联库存调整审核 ID',
  `related_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关联订单 ID',
  `related_reservation_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关联库存占用 ID',
  `related_arbitration_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '关联仲裁 ID',
  `operator_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人用户 ID',
  `operator_role` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人角色',
  `remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '备注',
  `business_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '效果唯一键',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c11_effect` (`business_key`),
  UNIQUE KEY `c11_audit` (`related_audit_id`),
  KEY `c11_history` (`sku_id`,`created_at`,`id`),
  KEY `c11_reservation` (`related_reservation_id`),
  CONSTRAINT `ck_c11_1` CHECK (COALESCE((quantity<>0),FALSE) = TRUE),
  CONSTRAINT `ck_c11_2` CHECK (COALESCE((before_quantity IS NULL OR before_quantity>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c11_3` CHECK (COALESCE((after_quantity IS NULL OR after_quantity>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c11_4` CHECK (COALESCE((before_stock_snapshot_json IS NOT NULL AND after_stock_snapshot_json IS NOT NULL),FALSE) = TRUE),
  CONSTRAINT `ck_c11_5` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c11_6` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c11_7` CHECK (COALESCE((`movement_type` IS NULL OR `movement_type` IN ('outbound','return','lock','release','increase','decrease','abnormal_decrease','arbitration_decrease')),FALSE) = TRUE),
  CONSTRAINT `ck_c11_8` CHECK (COALESCE((`source_type` IS NULL OR `source_type` IN ('manual_adjustment','order_fulfillment','arbitration','system_correction')),FALSE) = TRUE),
  CONSTRAINT `ck_c11_9` CHECK (COALESCE((`related_audit_id` IS NULL OR REGEXP_LIKE(`related_audit_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c11_10` CHECK (COALESCE((`related_order_id` IS NULL OR REGEXP_LIKE(`related_order_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c11_11` CHECK (COALESCE((`related_reservation_id` IS NULL OR REGEXP_LIKE(`related_reservation_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c11_12` CHECK (COALESCE((`related_arbitration_id` IS NULL OR REGEXP_LIKE(`related_arbitration_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c11_13` CHECK (COALESCE((`operator_user_id` IS NULL OR REGEXP_LIKE(`operator_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c11_14` CHECK (COALESCE((`operator_role` IS NULL OR `operator_role` IN ('factory','admin','system')),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prop_qr_code` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '二维码记录 ID',
  `qr_code_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '二维码业务编号',
  `code_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '码类型',
  `target_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '指向类型',
  `target_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '指向对象 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'SKU ID',
  `supersedes_qr_code_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '替代的旧二维码 ID',
  `qr_scene` VARCHAR(128) NULL DEFAULT NULL COMMENT '小程序码 scene',
  `qr_page` VARCHAR(128) NULL DEFAULT NULL COMMENT '小程序码页面',
  `qr_content` VARCHAR(512) NULL DEFAULT NULL COMMENT '二维码内容',
  `resolve_url` VARCHAR(512) NULL DEFAULT NULL COMMENT '平台统一解析地址',
  `qr_image_url` VARCHAR(512) NULL DEFAULT NULL COMMENT '二维码图片 URL',
  `qr_image_storage_key` VARCHAR(512) NULL DEFAULT NULL COMMENT '二维码图片存储 key',
  `image_sha256` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '图片 SHA256',
  `status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '二维码状态',
  `active_sku_guard` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN status='bound' AND target_type='SKU' THEN target_id ELSE NULL END) STORED COMMENT 'B类活动码唯一保护',
  `active_sn_guard` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN status='bound' AND target_type='SN' THEN target_id ELSE NULL END) STORED COMMENT 'A 类活动二维码守卫',
  `created_by_admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '创建管理员 ID',
  `used_by_factory_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '使用工厂用户 ID',
  `used_at` DATETIME(3) NULL DEFAULT NULL COMMENT '使用时间',
  `revoked_by_admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '作废管理员 ID',
  `revoked_at` DATETIME(3) NULL DEFAULT NULL COMMENT '作废时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `image_generation_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'pending' COMMENT '码图片生成状态',
  `image_attempts` INT NOT NULL DEFAULT 0 COMMENT '自动生成尝试次数',
  `image_lease_until` DATETIME(3) NULL DEFAULT NULL COMMENT '生成租约截止',
  `image_next_retry_at` DATETIME(3) NULL DEFAULT NULL COMMENT '下次生成时间',
  `image_error_code` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '脱敏生成错误码',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c12_code` (`qr_code_id`),
  UNIQUE KEY `c12_sku` (`active_sku_guard`),
  UNIQUE KEY `c12_sn` (`active_sn_guard`),
  UNIQUE KEY `c12_replacement` (`supersedes_qr_code_id`),
  KEY `c12_generation` (`image_generation_status`,`image_next_retry_at`,`image_lease_until`),
  KEY `c12_owner` (`sku_id`,`status`,`id`),
  CONSTRAINT `ck_c12_1` CHECK (COALESCE(((status='bound' AND target_type IN ('SN','SKU') AND target_id IS NOT NULL AND sku_id IS NOT NULL) OR status<>'bound'),FALSE) = TRUE),
  CONSTRAINT `ck_c12_2` CHECK (COALESCE(((image_generation_status='ready' AND qr_image_storage_key IS NOT NULL AND image_sha256 IS NOT NULL) OR image_generation_status<>'ready'),FALSE) = TRUE),
  CONSTRAINT `ck_c12_3` CHECK (COALESCE(((image_generation_status='generating' AND image_lease_until IS NOT NULL) OR (image_generation_status<>'generating' AND image_lease_until IS NULL)),FALSE) = TRUE),
  CONSTRAINT `ck_c12_4` CHECK (COALESCE((image_attempts>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c12_5` CHECK (COALESCE((qr_image_url IS NULL),FALSE) = TRUE),
  CONSTRAINT `ck_c12_6` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c12_7` CHECK (COALESCE((`code_type` IS NULL OR `code_type` IN ('wechat_mini_code','standard_qr')),FALSE) = TRUE),
  CONSTRAINT `ck_c12_8` CHECK (COALESCE((`target_type` IS NULL OR `target_type` IN ('SN','SKU','PENDING')),FALSE) = TRUE),
  CONSTRAINT `ck_c12_9` CHECK (COALESCE((`target_id` IS NULL OR REGEXP_LIKE(`target_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c12_10` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c12_11` CHECK (COALESCE((`supersedes_qr_code_id` IS NULL OR REGEXP_LIKE(`supersedes_qr_code_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c12_12` CHECK (COALESCE((`status` IS NULL OR `status` IN ('unbound','bound','revoked')),FALSE) = TRUE),
  CONSTRAINT `ck_c12_13` CHECK (COALESCE((`active_sku_guard` IS NULL OR REGEXP_LIKE(`active_sku_guard`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c12_14` CHECK (COALESCE((`active_sn_guard` IS NULL OR REGEXP_LIKE(`active_sn_guard`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c12_15` CHECK (COALESCE((`created_by_admin_user_id` IS NULL OR REGEXP_LIKE(`created_by_admin_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c12_16` CHECK (COALESCE((`used_by_factory_user_id` IS NULL OR REGEXP_LIKE(`used_by_factory_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c12_17` CHECK (COALESCE((`revoked_by_admin_user_id` IS NULL OR REGEXP_LIKE(`revoked_by_admin_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c12_18` CHECK (COALESCE((`version`>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c12_19` CHECK (COALESCE((`image_generation_status` IS NULL OR `image_generation_status` IN ('pending','generating','ready','failed')),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prop_qr_exception_record` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '异常记录 ID',
  `exception_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '异常编号',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `sn_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'SN ID',
  `original_qr_code_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '原二维码记录 ID',
  `reported_by_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '报告人用户 ID',
  `exception_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '异常类型',
  `description` VARCHAR(500) NOT NULL COMMENT '异常说明',
  `evidence_urls_json` JSON NULL DEFAULT NULL COMMENT '证据对象键',
  `exception_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '异常状态',
  `active_original_qr_guard` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN exception_status='reported' THEN original_qr_code_id ELSE NULL END) STORED COMMENT '活动异常原码守卫',
  `resolution_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '处理类型',
  `replacement_qr_code_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '补发二维码 ID',
  `resolved_by_admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '处理管理员用户 ID',
  `resolution_reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '处理理由',
  `resolved_at` DATETIME(3) NULL DEFAULT NULL COMMENT '处理时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `request_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '登记请求编号',
  `request_hash` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '登记摘要',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c13_number` (`exception_no`),
  UNIQUE KEY `c13_request` (`reported_by_user_id`,`request_id`),
  UNIQUE KEY `c13_active` (`active_original_qr_guard`),
  KEY `c13_queue` (`exception_status`,`created_at`,`id`),
  CONSTRAINT `ck_c13_1` CHECK (COALESCE(((exception_status='reported' AND resolved_at IS NULL AND resolution_type IS NULL) OR (exception_status='resolved' AND resolved_at IS NOT NULL AND resolved_by_admin_user_id IS NOT NULL AND resolution_type IS NOT NULL) OR (exception_status='cancelled' AND resolved_at IS NOT NULL AND resolved_by_admin_user_id IS NOT NULL AND resolution_type IS NULL)),FALSE) = TRUE),
  CONSTRAINT `ck_c13_2` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c13_3` CHECK (COALESCE((`factory_id` IS NULL OR REGEXP_LIKE(`factory_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c13_4` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c13_5` CHECK (COALESCE((`sn_id` IS NULL OR REGEXP_LIKE(`sn_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c13_6` CHECK (COALESCE((`original_qr_code_id` IS NULL OR REGEXP_LIKE(`original_qr_code_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c13_7` CHECK (COALESCE((`reported_by_user_id` IS NULL OR REGEXP_LIKE(`reported_by_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c13_8` CHECK (COALESCE((`exception_type` IS NULL OR `exception_type` IN ('suspected_leak','wrong_binding','unreadable_untrusted','other')),FALSE) = TRUE),
  CONSTRAINT `ck_c13_9` CHECK (COALESCE((`exception_status` IS NULL OR `exception_status` IN ('reported','resolved','cancelled')),FALSE) = TRUE),
  CONSTRAINT `ck_c13_10` CHECK (COALESCE((`active_original_qr_guard` IS NULL OR REGEXP_LIKE(`active_original_qr_guard`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c13_11` CHECK (COALESCE((`resolution_type` IS NULL OR `resolution_type` IN ('disable_only','confirmed_normal','reissue')),FALSE) = TRUE),
  CONSTRAINT `ck_c13_12` CHECK (COALESCE((`replacement_qr_code_id` IS NULL OR REGEXP_LIKE(`replacement_qr_code_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c13_13` CHECK (COALESCE((`resolved_by_admin_user_id` IS NULL OR REGEXP_LIKE(`resolved_by_admin_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c13_14` CHECK (COALESCE((`version`>=0),FALSE) = TRUE),
  CONSTRAINT `ck_c13_15` CHECK (COALESCE((`request_id` IS NULL OR REGEXP_LIKE(`request_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c13_16` CHECK (COALESCE((request_hash IS NULL OR REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `prop_operation_record` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作ID',
  `actor_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作人',
  `request_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求编号',
  `request_hash` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '请求摘要',
  `operation_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '操作类型',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU',
  `target_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标类型',
  `target_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '目标ID',
  `before_snapshot_json` JSON NOT NULL COMMENT '操作前事实',
  `after_snapshot_json` JSON NOT NULL COMMENT '操作后事实',
  `result_json` JSON NOT NULL COMMENT '原命令返回结果',
  `created_at` DATETIME(3) NOT NULL COMMENT '成功提交时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `c14_request` (`actor_user_id`,`request_id`),
  KEY `c14_sku` (`sku_id`,`created_at`,`id`),
  CONSTRAINT `ck_c14_1` CHECK (COALESCE((JSON_TYPE(before_snapshot_json)='OBJECT' AND JSON_TYPE(after_snapshot_json)='OBJECT' AND JSON_TYPE(result_json)='OBJECT'),FALSE) = TRUE),
  CONSTRAINT `ck_c14_2` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c14_3` CHECK (COALESCE((`actor_user_id` IS NULL OR REGEXP_LIKE(`actor_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c14_4` CHECK (COALESCE((`request_id` IS NULL OR REGEXP_LIKE(`request_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c14_5` CHECK (COALESCE((request_hash IS NULL OR REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c14_6` CHECK (COALESCE((`operation_code` IS NULL OR `operation_code` IN ('edit_sku_options','label_confirm','repair_complete','cancel_application','issue_qr')),FALSE) = TRUE),
  CONSTRAINT `ck_c14_7` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c14_8` CHECK (COALESCE((`target_type` IS NULL OR `target_type` IN ('sku','sn','sku_audit','sn_adjustment','stock_adjustment','qr')),FALSE) = TRUE),
  CONSTRAINT `ck_c14_9` CHECK (COALESCE((`target_id` IS NULL OR REGEXP_LIKE(`target_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `inventory_reservation` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '占用 ID',
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'SKU ID',
  `sn_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT 'SN ID',
  `quantity` INT NOT NULL COMMENT '占用数量',
  `checkout_batch_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '来源结算批次 ID',
  `checkout_batch_item_snapshot_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '来源结算明细快照 ID',
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁订单 ID',
  `rental_order_item_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '租赁订单明细 ID',
  `source_type` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '通用来源类型',
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
  PRIMARY KEY (`id`),
  UNIQUE KEY `c15_effect` (`idempotency_key`),
  KEY `c15_range` (`sku_id`,`reservation_status`,`occupy_start_date`,`occupy_end_date`),
  KEY `c15_sn` (`sn_id`,`reservation_status`,`occupy_start_date`,`occupy_end_date`),
  CONSTRAINT `ck_c15_1` CHECK (COALESCE((quantity>0),FALSE) = TRUE),
  CONSTRAINT `ck_c15_2` CHECK (COALESCE((sn_id IS NULL OR quantity=1),FALSE) = TRUE),
  CONSTRAINT `ck_c15_3` CHECK (COALESCE((rental_start_date<=rental_end_date AND occupy_start_date<=occupy_end_date),FALSE) = TRUE),
  CONSTRAINT `ck_c15_4` CHECK (COALESCE(((source_type IS NULL AND source_id IS NULL) OR (source_type IS NOT NULL AND source_id IS NOT NULL)),FALSE) = TRUE),
  CONSTRAINT `ck_c15_5` CHECK (COALESCE((`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c15_6` CHECK (COALESCE((`sku_id` IS NULL OR REGEXP_LIKE(`sku_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c15_7` CHECK (COALESCE((`sn_id` IS NULL OR REGEXP_LIKE(`sn_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c15_8` CHECK (COALESCE((`checkout_batch_id` IS NULL OR REGEXP_LIKE(`checkout_batch_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c15_9` CHECK (COALESCE((`checkout_batch_item_snapshot_id` IS NULL OR REGEXP_LIKE(`checkout_batch_item_snapshot_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c15_10` CHECK (COALESCE((`rental_order_id` IS NULL OR REGEXP_LIKE(`rental_order_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c15_11` CHECK (COALESCE((`rental_order_item_id` IS NULL OR REGEXP_LIKE(`rental_order_item_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c15_12` CHECK (COALESCE((`source_type` IS NULL OR `source_type` IN ('checkout_batch','checkout_batch_item_snapshot','order','order_item','extension_request','system')),FALSE) = TRUE),
  CONSTRAINT `ck_c15_13` CHECK (COALESCE((`source_id` IS NULL OR REGEXP_LIKE(`source_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),FALSE) = TRUE),
  CONSTRAINT `ck_c15_14` CHECK (COALESCE((`reservation_status` IS NULL OR `reservation_status` IN ('held','paid_locked','renting','overdue_holding','disputed_holding','released','cancelled','expired','completed')),FALSE) = TRUE),
  CONSTRAINT `ck_c15_15` CHECK (COALESCE((`version`>=0),FALSE) = TRUE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `prop_sku` ADD CONSTRAINT `fk_c1_factory_id` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_image` ADD CONSTRAINT `fk_c2_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sku_audit` ADD CONSTRAINT `fk_c3_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sku_audit` ADD CONSTRAINT `fk_c3_applicant_usr` FOREIGN KEY (`applicant_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sku_audit` ADD CONSTRAINT `fk_c3_reviewer_id` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sku_audit` ADD CONSTRAINT `fk_c3_previous_audit_id` FOREIGN KEY (`previous_audit_id`) REFERENCES `prop_sku_audit` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sku_style` ADD CONSTRAINT `fk_c4_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sn_instance` ADD CONSTRAINT `fk_c5_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sn_instance` ADD CONSTRAINT `fk_c5_status_changed_by` FOREIGN KEY (`status_changed_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sn_instance` ADD CONSTRAINT `fk_c5_source_adjustment_audit_id` FOREIGN KEY (`source_adjustment_audit_id`) REFERENCES `prop_sn_adjustment_audit` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sn_instance` ADD CONSTRAINT `fk_c5_qr_code_id` FOREIGN KEY (`qr_code_id`) REFERENCES `prop_qr_code` (`qr_code_id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sn_adjustment_audit` ADD CONSTRAINT `fk_c6_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sn_adjustment_audit` ADD CONSTRAINT `fk_c6_factory_id` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sn_adjustment_audit` ADD CONSTRAINT `fk_c6_applicant_usr` FOREIGN KEY (`applicant_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sn_adjustment_audit` ADD CONSTRAINT `fk_c6_reviewer_id` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sn_adjustment_audit` ADD CONSTRAINT `fk_c6_related_sku_audit_id` FOREIGN KEY (`related_sku_audit_id`) REFERENCES `prop_sku_audit` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sn_adjustment_item` ADD CONSTRAINT `fk_c7_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sn_adjustment_item` ADD CONSTRAINT `fk_c7_adjustment_audit_id` FOREIGN KEY (`adjustment_audit_id`) REFERENCES `prop_sn_adjustment_audit` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sn_adjustment_item` ADD CONSTRAINT `fk_c7_sn_id` FOREIGN KEY (`sn_id`) REFERENCES `prop_sn_instance` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_instance_status_log` ADD CONSTRAINT `fk_c8_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_instance_status_log` ADD CONSTRAINT `fk_c8_operator_usr` FOREIGN KEY (`operator_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_instance_status_log` ADD CONSTRAINT `fk_c8_prop_instance_id` FOREIGN KEY (`prop_instance_id`) REFERENCES `prop_sn_instance` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_instance_status_log` ADD CONSTRAINT `fk_c8_related_adjustment_audit_id` FOREIGN KEY (`related_adjustment_audit_id`) REFERENCES `prop_sn_adjustment_audit` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sku_stock` ADD CONSTRAINT `fk_c9_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_sku_stock` ADD CONSTRAINT `fk_c9_last_counted_by` FOREIGN KEY (`last_counted_by`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_stock_adjustment_audit` ADD CONSTRAINT `fk_c10_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_stock_adjustment_audit` ADD CONSTRAINT `fk_c10_applicant_usr` FOREIGN KEY (`applicant_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_stock_adjustment_audit` ADD CONSTRAINT `fk_c10_reviewer_id` FOREIGN KEY (`reviewer_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_stock_adjustment_audit` ADD CONSTRAINT `fk_c10_related_sku_audit_id` FOREIGN KEY (`related_sku_audit_id`) REFERENCES `prop_sku_audit` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_stock_adjustment_audit` ADD CONSTRAINT `fk_c10_related_movement_id` FOREIGN KEY (`related_movement_id`) REFERENCES `prop_stock_movement` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_stock_movement` ADD CONSTRAINT `fk_c11_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_stock_movement` ADD CONSTRAINT `fk_c11_operator_usr` FOREIGN KEY (`operator_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_stock_movement` ADD CONSTRAINT `fk_c11_related_audit_id` FOREIGN KEY (`related_audit_id`) REFERENCES `prop_stock_adjustment_audit` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_qr_code` ADD CONSTRAINT `fk_c12_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_qr_code` ADD CONSTRAINT `fk_c12_created_by_admin_usr` FOREIGN KEY (`created_by_admin_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_qr_code` ADD CONSTRAINT `fk_c12_used_by_factory_usr` FOREIGN KEY (`used_by_factory_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_qr_code` ADD CONSTRAINT `fk_c12_revoked_by_admin_usr` FOREIGN KEY (`revoked_by_admin_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_qr_code` ADD CONSTRAINT `fk_c12_supersedes_qr_code_id` FOREIGN KEY (`supersedes_qr_code_id`) REFERENCES `prop_qr_code` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_qr_exception_record` ADD CONSTRAINT `fk_c13_factory_id` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_qr_exception_record` ADD CONSTRAINT `fk_c13_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_qr_exception_record` ADD CONSTRAINT `fk_c13_reported_by_usr` FOREIGN KEY (`reported_by_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_qr_exception_record` ADD CONSTRAINT `fk_c13_resolved_by_admin_usr` FOREIGN KEY (`resolved_by_admin_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_qr_exception_record` ADD CONSTRAINT `fk_c13_sn_id` FOREIGN KEY (`sn_id`) REFERENCES `prop_sn_instance` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_qr_exception_record` ADD CONSTRAINT `fk_c13_original_qr_code_id` FOREIGN KEY (`original_qr_code_id`) REFERENCES `prop_qr_code` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_qr_exception_record` ADD CONSTRAINT `fk_c13_replacement_qr_code_id` FOREIGN KEY (`replacement_qr_code_id`) REFERENCES `prop_qr_code` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_operation_record` ADD CONSTRAINT `fk_c14_actor_usr` FOREIGN KEY (`actor_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `prop_operation_record` ADD CONSTRAINT `fk_c14_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `inventory_reservation` ADD CONSTRAINT `fk_c15_sku_id` FOREIGN KEY (`sku_id`) REFERENCES `prop_sku` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `inventory_reservation` ADD CONSTRAINT `fk_c15_sn_id` FOREIGN KEY (`sn_id`) REFERENCES `prop_sn_instance` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE admin_case_relation ADD COLUMN prop_source_guard VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN relation_type IN ('prop_sku_audit','prop_sn_adjustment_audit','prop_stock_adjustment_audit','prop_qr_exception_record') AND relation_role='source' AND is_primary=1 THEN CONCAT(relation_type,':',relation_id) ELSE NULL END) STORED, ADD UNIQUE KEY c_prop_source_once(prop_source_guard);

ALTER TABLE admin_case ADD CONSTRAINT ck_c_case CHECK (case_type NOT IN ('prop_sku_review','prop_sku_status_review','stock_adjustment_review','qr_exception') OR (factory_id IS NOT NULL AND rental_order_id IS NULL AND case_status IN ('pending','closed')));

ALTER TABLE admin_case_action ADD CONSTRAINT ck_c_action CHECK (target_module<>'02' OR action_type NOT IN ('approve_prop_sku','reject_prop_sku','approve_prop_sn','reject_prop_sn','approve_prop_stock','reject_prop_stock','approve_prop_qr_exception','reject_prop_qr_exception') OR (action_status='succeeded' AND request_payload_json IS NOT NULL AND result_payload_json IS NOT NULL AND executed_at IS NOT NULL));

