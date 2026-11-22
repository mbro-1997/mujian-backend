-- D2-B设计交付；未执行。执行者转为新的Flyway迁移；不得改V1～V6或清空darkmujian。

-- 全部CREATE之后才加FK，以处理入驻/主体循环引用。无DROP/TRUNCATE/DELETE。

CREATE TABLE `option_dictionary` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '选项 ID',
  `option_group` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '选项组',
  `option_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '选项编码',
  `option_label` VARCHAR(64) NOT NULL COMMENT '选项中文名',
  `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序',
  `enabled` TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用',
  `remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `b1_group_code` (`option_group`, `option_code`),
  KEY `b1_choices` (`option_group`, `enabled`, `sort_order`, `id`),
  CONSTRAINT `ck_b1_1` CHECK (enabled IN (0,1)),
  CONSTRAINT `ck_b1_2` CHECK (sort_order>=0),
  CONSTRAINT `ck_b1_3` CHECK (CHAR_LENGTH(option_group)>0 AND CHAR_LENGTH(option_code)>0 AND CHAR_LENGTH(option_label)>0),
  CONSTRAINT `ck_b1_4` CHECK (`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `factory_invite_code` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邀请码 ID',
  `code_hash` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邀请码哈希',
  `create_request_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '生成请求编号',
  `request_hash` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '生成内容摘要',
  `code_suffix` VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邀请码后缀',
  `invite_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邀请码状态',
  `expire_at` DATETIME(3) NOT NULL COMMENT '过期时间',
  `created_by_admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '创建管理员 ID',
  `locked_by_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '锁定用户 ID',
  `locked_at` DATETIME(3) NULL DEFAULT NULL COMMENT '锁定时间',
  `used_by_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '使用用户 ID',
  `used_at` DATETIME(3) NULL DEFAULT NULL COMMENT '使用时间',
  `revoked_by_admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '作废管理员 ID',
  `revoked_at` DATETIME(3) NULL DEFAULT NULL COMMENT '作废时间',
  `remark` VARCHAR(255) NULL DEFAULT NULL COMMENT '备注',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `b2_code` (`code_hash`),
  UNIQUE KEY `b2_request` (`created_by_admin_user_id`, `create_request_id`),
  KEY `b2_owner` (`locked_by_user_id`, `invite_status`),
  KEY `b2_queue` (`invite_status`, `created_at`, `id`),
  CONSTRAINT `ck_b2_1` CHECK (invite_status IN ('unused','locked','used','revoked')),
  CONSTRAINT `ck_b2_2` CHECK (expire_at>created_at),
  CONSTRAINT `ck_b2_3` CHECK (CHAR_LENGTH(code_suffix)=4),
  CONSTRAINT `ck_b2_4` CHECK (REGEXP_LIKE(code_hash,'^[0-9a-f]{64}$','c')),
  CONSTRAINT `ck_b2_5` CHECK ((locked_by_user_id IS NULL AND locked_at IS NULL) OR (locked_by_user_id IS NOT NULL AND locked_at IS NOT NULL)),
  CONSTRAINT `ck_b2_6` CHECK ((invite_status='used' AND used_by_user_id IS NOT NULL AND used_at IS NOT NULL AND locked_by_user_id IS NOT NULL AND used_by_user_id=locked_by_user_id) OR (invite_status<>'used' AND used_by_user_id IS NULL AND used_at IS NULL)),
  CONSTRAINT `ck_b2_7` CHECK ((invite_status='revoked' AND revoked_by_admin_user_id IS NOT NULL AND revoked_at IS NOT NULL) OR (invite_status<>'revoked' AND revoked_by_admin_user_id IS NULL AND revoked_at IS NULL)),
  CONSTRAINT `ck_b2_8` CHECK (invite_status<>'locked' OR locked_by_user_id IS NOT NULL),
  CONSTRAINT `ck_b2_9` CHECK (invite_status<>'unused' OR locked_by_user_id IS NULL),
  CONSTRAINT `ck_b2_10` CHECK (`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b2_11` CHECK (REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')),
  CONSTRAINT `ck_b2_13` CHECK (`created_by_admin_user_id` IS NULL OR REGEXP_LIKE(`created_by_admin_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b2_14` CHECK (`locked_by_user_id` IS NULL OR REGEXP_LIKE(`locked_by_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b2_15` CHECK (`used_by_user_id` IS NULL OR REGEXP_LIKE(`used_by_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b2_16` CHECK (`revoked_by_admin_user_id` IS NULL OR REGEXP_LIKE(`revoked_by_admin_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `factory_onboarding_audit` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审核单 ID',
  `application_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '入驻申请编号',
  `submit_request_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '提交请求编号',
  `request_hash` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '提交内容摘要',
  `accepted_rule_document_ids_json` JSON NOT NULL COMMENT '本次确认工厂规则版本',
  `previous_application_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '上次驳回申请',
  `applicant_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申请用户 ID',
  `invite_code_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '邀请码 ID',
  `factory_name` VARCHAR(128) NOT NULL COMMENT '工厂名称',
  `factory_avatar_url` VARCHAR(500) NOT NULL COMMENT '工厂头像',
  `unified_social_credit_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '统一社会信用代码',
  `business_license_url` VARCHAR(500) NOT NULL COMMENT '营业执照',
  `factory_photo_urls` JSON NOT NULL COMMENT '厂房照片',
  `contact_name` VARCHAR(64) NOT NULL COMMENT '联系人姓名',
  `contact_phone` VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '联系人手机号',
  `factory_address` VARCHAR(255) NOT NULL COMMENT '工厂地址',
  `main_business` VARCHAR(255) NULL DEFAULT NULL COMMENT '主营道具类型',
  `worker_count` INT NOT NULL COMMENT '提交时工人数量',
  `specialty_category_ids_json` JSON NOT NULL COMMENT '提交时擅长品类ID',
  `specialty_category_names_snapshot_json` JSON NOT NULL COMMENT '擅长品类名称快照',
  `case_materials_json` JSON NOT NULL COMMENT '提交时案例资料',
  `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '备注说明',
  `audit_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审核状态',
  `pending_applicant_guard` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN audit_status='pending' THEN applicant_user_id ELSE NULL END) STORED COMMENT '待审核申请人唯一守卫',
  `audit_remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '审核备注',
  `reject_reason` VARCHAR(500) NULL DEFAULT NULL COMMENT '驳回原因',
  `reviewed_by_admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '审核管理员 ID',
  `submitted_at` DATETIME(3) NOT NULL COMMENT '提交时间',
  `reviewed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '审核时间',
  `created_factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '创建的工厂 ID',
  `version` INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `b3_number` (`application_no`),
  UNIQUE KEY `b3_request` (`applicant_user_id`, `submit_request_id`),
  UNIQUE KEY `b3_pending` (`pending_applicant_guard`),
  KEY `b3_queue` (`audit_status`, `submitted_at`, `id`),
  KEY `b3_owner` (`applicant_user_id`, `submitted_at`, `id`),
  KEY `b3_invite` (`invite_code_id`),
  CONSTRAINT `ck_b3_1` CHECK (audit_status IN ('pending','approved','rejected','cancelled')),
  CONSTRAINT `ck_b3_2` CHECK (version>=0),
  CONSTRAINT `ck_b3_3` CHECK (worker_count>=0),
  CONSTRAINT `ck_b3_4` CHECK (JSON_TYPE(factory_photo_urls)='ARRAY' AND JSON_LENGTH(factory_photo_urls) BETWEEN 1 AND 3),
  CONSTRAINT `ck_b3_5` CHECK (JSON_TYPE(specialty_category_ids_json)='ARRAY' AND JSON_LENGTH(specialty_category_ids_json) BETWEEN 1 AND 100),
  CONSTRAINT `ck_b3_6` CHECK (JSON_TYPE(specialty_category_names_snapshot_json)='ARRAY' AND JSON_LENGTH(specialty_category_names_snapshot_json)=JSON_LENGTH(specialty_category_ids_json)),
  CONSTRAINT `ck_b3_7` CHECK (JSON_TYPE(case_materials_json)='ARRAY' AND JSON_LENGTH(case_materials_json)<=10),
  CONSTRAINT `ck_b3_8` CHECK (JSON_TYPE(accepted_rule_document_ids_json)='ARRAY' AND JSON_LENGTH(accepted_rule_document_ids_json)=2),
  CONSTRAINT `ck_b3_9` CHECK ((audit_status='approved' AND created_factory_id IS NOT NULL) OR (audit_status<>'approved' AND created_factory_id IS NULL)),
  CONSTRAINT `ck_b3_10` CHECK ((audit_status='pending' AND reviewed_by_admin_user_id IS NULL AND reviewed_at IS NULL AND version=0) OR (audit_status<>'pending' AND reviewed_by_admin_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND version>=1 AND reviewed_at>=submitted_at)),
  CONSTRAINT `ck_b3_11` CHECK ((audit_status='rejected' AND reject_reason IS NOT NULL AND CHAR_LENGTH(TRIM(reject_reason))>0) OR (audit_status<>'rejected' AND reject_reason IS NULL)),
  CONSTRAINT `ck_b3_12` CHECK (created_at=submitted_at AND updated_at>=created_at),
  CONSTRAINT `ck_b3_13` CHECK (`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b3_14` CHECK (REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')),
  CONSTRAINT `ck_b3_15` CHECK (`previous_application_id` IS NULL OR REGEXP_LIKE(`previous_application_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b3_16` CHECK (`applicant_user_id` IS NULL OR REGEXP_LIKE(`applicant_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b3_17` CHECK (`invite_code_id` IS NULL OR REGEXP_LIKE(`invite_code_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b3_20` CHECK (`pending_applicant_guard` IS NULL OR REGEXP_LIKE(`pending_applicant_guard`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b3_21` CHECK (`reviewed_by_admin_user_id` IS NULL OR REGEXP_LIKE(`reviewed_by_admin_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b3_22` CHECK (`created_factory_id` IS NULL OR REGEXP_LIKE(`created_factory_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `factory_profile` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `owner_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '主账号用户 ID',
  `factory_name` VARCHAR(128) NOT NULL COMMENT '工厂名称',
  `factory_avatar_url` VARCHAR(500) NOT NULL COMMENT '工厂头像',
  `unified_social_credit_code` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '统一社会信用代码',
  `business_license_url` VARCHAR(500) NOT NULL COMMENT '营业执照',
  `factory_photo_urls` JSON NOT NULL COMMENT '厂房照片',
  `contact_name` VARCHAR(64) NOT NULL COMMENT '联系人姓名',
  `contact_phone` VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '联系人手机号',
  `factory_address` VARCHAR(255) NOT NULL COMMENT '工厂地址',
  `main_business` VARCHAR(255) NULL DEFAULT NULL COMMENT '主营道具类型',
  `worker_count` INT NOT NULL COMMENT '工人数量',
  `case_materials_json` JSON NOT NULL COMMENT '完整服务案例',
  `specialty_category_ids_json` JSON NOT NULL COMMENT '当前擅长品类ID',
  `specialty_category_names_snapshot_json` JSON NOT NULL COMMENT '擅长品类展示',
  `profile_version` BIGINT NOT NULL DEFAULT 1 COMMENT '生效资料版本',
  `remark` VARCHAR(500) NULL DEFAULT NULL COMMENT '工厂资料备注',
  `factory_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂状态',
  `approved_audit_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '通过的审核单 ID',
  `approved_by_admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '审核管理员 ID',
  `approved_at` DATETIME(3) NULL DEFAULT NULL COMMENT '审核通过时间',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `b4_owner` (`owner_user_id`),
  UNIQUE KEY `b4_credit` (`unified_social_credit_code`),
  KEY `b4_queue` (`factory_status`, `created_at`, `id`),
  CONSTRAINT `ck_b4_1` CHECK (factory_status IN ('active','disabled','suspended')),
  CONSTRAINT `ck_b4_2` CHECK (profile_version>=1),
  CONSTRAINT `ck_b4_3` CHECK (worker_count>=0),
  CONSTRAINT `ck_b4_4` CHECK (JSON_TYPE(factory_photo_urls)='ARRAY' AND JSON_LENGTH(factory_photo_urls) BETWEEN 1 AND 3),
  CONSTRAINT `ck_b4_5` CHECK (JSON_TYPE(specialty_category_ids_json)='ARRAY' AND JSON_LENGTH(specialty_category_ids_json) BETWEEN 1 AND 100),
  CONSTRAINT `ck_b4_6` CHECK (JSON_TYPE(specialty_category_names_snapshot_json)='ARRAY' AND JSON_LENGTH(specialty_category_names_snapshot_json)=JSON_LENGTH(specialty_category_ids_json)),
  CONSTRAINT `ck_b4_7` CHECK (JSON_TYPE(case_materials_json)='ARRAY' AND JSON_LENGTH(case_materials_json)<=10),
  CONSTRAINT `ck_b4_8` CHECK ((approved_audit_id IS NULL AND approved_by_admin_user_id IS NULL AND approved_at IS NULL) OR (approved_audit_id IS NOT NULL AND approved_by_admin_user_id IS NOT NULL AND approved_at IS NOT NULL)),
  CONSTRAINT `ck_b4_9` CHECK (`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b4_10` CHECK (`owner_user_id` IS NULL OR REGEXP_LIKE(`owner_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b4_13` CHECK (`approved_audit_id` IS NULL OR REGEXP_LIKE(`approved_audit_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b4_14` CHECK (`approved_by_admin_user_id` IS NULL OR REGEXP_LIKE(`approved_by_admin_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `factory_profile_change_audit` (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审核 ID',
  `application_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '变更申请编号',
  `submit_request_id` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '提交请求编号',
  `request_hash` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '提交内容摘要',
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '工厂 ID',
  `applicant_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '申请人用户 ID',
  `change_payload_json` JSON NOT NULL COMMENT '完整申请快照',
  `audit_status` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT '审核状态',
  `reject_reason` VARCHAR(255) NULL DEFAULT NULL COMMENT '驳回原因',
  `reviewed_by_admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL DEFAULT NULL COMMENT '审核管理员 ID',
  `submitted_at` DATETIME(3) NOT NULL COMMENT '提交时间',
  `reviewed_at` DATETIME(3) NULL DEFAULT NULL COMMENT '审核时间',
  `version` INT NOT NULL DEFAULT 0 COMMENT '申请审核版本',
  `pending_factory_guard` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN audit_status='pending' THEN factory_id ELSE NULL END) STORED COMMENT '同厂待审唯一守卫',
  `created_at` DATETIME(3) NOT NULL COMMENT '创建时间',
  `updated_at` DATETIME(3) NOT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `b5_number` (`application_no`),
  UNIQUE KEY `b5_request` (`factory_id`, `submit_request_id`),
  UNIQUE KEY `b5_pending` (`pending_factory_guard`),
  KEY `b5_owner` (`factory_id`, `audit_status`, `submitted_at`, `id`),
  KEY `b5_queue` (`audit_status`, `submitted_at`, `id`),
  CONSTRAINT `ck_b5_1` CHECK (audit_status IN ('pending','approved','rejected','cancelled')),
  CONSTRAINT `ck_b5_2` CHECK (version>=0),
  CONSTRAINT `ck_b5_3` CHECK (JSON_TYPE(change_payload_json)='OBJECT' AND JSON_CONTAINS_PATH(change_payload_json,'all','$.schema_version','$.before','$.proposed','$.base_profile_version','$.previous_application_id','$.phone_verification')=1),
  CONSTRAINT `ck_b5_4` CHECK ((audit_status='pending' AND reviewed_by_admin_user_id IS NULL AND reviewed_at IS NULL AND version=0) OR (audit_status<>'pending' AND reviewed_by_admin_user_id IS NOT NULL AND reviewed_at IS NOT NULL AND version>=1 AND reviewed_at>=submitted_at)),
  CONSTRAINT `ck_b5_5` CHECK ((audit_status='rejected' AND reject_reason IS NOT NULL AND CHAR_LENGTH(TRIM(reject_reason))>0) OR (audit_status<>'rejected' AND reject_reason IS NULL)),
  CONSTRAINT `ck_b5_6` CHECK (created_at=submitted_at AND updated_at>=created_at),
  CONSTRAINT `ck_b5_7` CHECK (`id` IS NULL OR REGEXP_LIKE(`id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b5_8` CHECK (REGEXP_LIKE(request_hash,'^[0-9a-f]{64}$','c')),
  CONSTRAINT `ck_b5_9` CHECK (`factory_id` IS NULL OR REGEXP_LIKE(`factory_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b5_10` CHECK (`applicant_user_id` IS NULL OR REGEXP_LIKE(`applicant_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b5_12` CHECK (`reviewed_by_admin_user_id` IS NULL OR REGEXP_LIKE(`reviewed_by_admin_user_id`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT `ck_b5_13` CHECK (`pending_factory_guard` IS NULL OR REGEXP_LIKE(`pending_factory_guard`,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `factory_invite_code` ADD CONSTRAINT `fk_b2_created_by_admin_usr` FOREIGN KEY (`created_by_admin_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_invite_code` ADD CONSTRAINT `fk_b2_locked_by_usr` FOREIGN KEY (`locked_by_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_invite_code` ADD CONSTRAINT `fk_b2_used_by_usr` FOREIGN KEY (`used_by_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_invite_code` ADD CONSTRAINT `fk_b2_revoked_by_admin_usr` FOREIGN KEY (`revoked_by_admin_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_onboarding_audit` ADD CONSTRAINT `fk_b3_applicant_usr` FOREIGN KEY (`applicant_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_onboarding_audit` ADD CONSTRAINT `fk_b3_invite_code_id` FOREIGN KEY (`invite_code_id`) REFERENCES `factory_invite_code` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_onboarding_audit` ADD CONSTRAINT `fk_b3_previous_application_id` FOREIGN KEY (`previous_application_id`) REFERENCES `factory_onboarding_audit` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_onboarding_audit` ADD CONSTRAINT `fk_b3_reviewed_by_admin_usr` FOREIGN KEY (`reviewed_by_admin_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_onboarding_audit` ADD CONSTRAINT `fk_b3_created_factory_id` FOREIGN KEY (`created_factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_profile` ADD CONSTRAINT `fk_b4_owner_usr` FOREIGN KEY (`owner_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_profile` ADD CONSTRAINT `fk_b4_approved_audit_id` FOREIGN KEY (`approved_audit_id`) REFERENCES `factory_onboarding_audit` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_profile` ADD CONSTRAINT `fk_b4_approved_by_admin_usr` FOREIGN KEY (`approved_by_admin_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_profile_change_audit` ADD CONSTRAINT `fk_b5_factory_id` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_profile_change_audit` ADD CONSTRAINT `fk_b5_applicant_usr` FOREIGN KEY (`applicant_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

ALTER TABLE `factory_profile_change_audit` ADD CONSTRAINT `fk_b5_reviewed_by_admin_usr` FOREIGN KEY (`reviewed_by_admin_user_id`) REFERENCES `sys_user` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- 前置：查询admin_case.factory_id非空值是否均可关联新工厂；发现孤立值报错，不删数据。

ALTER TABLE `admin_case` ADD CONSTRAINT `fk_b_case_factory` FOREIGN KEY (`factory_id`) REFERENCES `factory_profile` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT;

-- 保留旧域约束，只增加B审核领域的不变量，不删除旧CHECK。
ALTER TABLE factory_onboarding_audit ADD CONSTRAINT ck_b_credit_onboarding CHECK (REGEXP_LIKE(unified_social_credit_code,'^[0-9A-Z]{18}$','c'));
ALTER TABLE factory_profile ADD CONSTRAINT ck_b_credit_profile CHECK (REGEXP_LIKE(unified_social_credit_code,'^[0-9A-Z]{18}$','c'));
ALTER TABLE option_dictionary ADD CONSTRAINT ck_b_option_ascii CHECK (REGEXP_LIKE(option_code,'^[!-~]{1,32}$','c'));
ALTER TABLE admin_case ADD CONSTRAINT ck_b_case CHECK (case_type NOT IN ('factory_onboarding_review','factory_profile_change_review') OR (rental_order_id IS NULL AND buyer_user_id IS NOT NULL AND case_status IN ('pending','closed')));
ALTER TABLE admin_case_action ADD CONSTRAINT ck_b_action CHECK (action_type NOT IN ('approve_factory_onboarding','reject_factory_onboarding','approve_factory_profile_change','reject_factory_profile_change') OR (action_status='succeeded' AND target_module='01' AND target_id IS NOT NULL AND request_payload_json IS NOT NULL AND result_payload_json IS NOT NULL AND failure_code IS NULL AND failure_message IS NULL AND executed_at IS NOT NULL AND parent_action_id IS NULL AND planned_rent_refund_amount_fen IS NULL AND planned_deposit_refund_amount_fen IS NULL AND planned_freight_refund_amount_fen IS NULL));

-- B来源关系跨案件唯一，既有affected/result关系仍可多处引用。
ALTER TABLE admin_case_relation ADD COLUMN factory_source_guard VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN relation_type IN ('factory_onboarding_audit','factory_profile_change_audit') AND relation_role='source' AND is_primary=1 THEN CONCAT(relation_type,':',relation_id) ELSE NULL END) STORED, ADD UNIQUE KEY b_source_once(factory_source_guard);
