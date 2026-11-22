CREATE TABLE custom_requirement (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  requirement_no VARCHAR(64) NOT NULL,
  submit_idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  buyer_user_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  factory_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  requirement_status VARCHAR(32) NOT NULL,
  requirement_title VARCHAR(128),
  usage_scene VARCHAR(255),
  expected_completion_date DATE,
  budget_range_fen JSON,
  delivery_city VARCHAR(64),
  delivery_preference VARCHAR(32),
  remark VARCHAR(1000),
  response_deadline_at DATETIME(3),
  version INT NOT NULL,
  submitted_at DATETIME(3),
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  latest_reason VARCHAR(1000),
  PRIMARY KEY (id),
  UNIQUE KEY uk_custom_requirement_no (requirement_no),
  UNIQUE KEY uk_custom_requirement_submit (submit_idempotency_key),
  KEY idx_custom_requirement_buyer (buyer_user_id, requirement_status, updated_at),
  KEY idx_custom_requirement_factory (factory_id, requirement_status, response_deadline_at),
  CHECK (version >= 0),
  CHECK (budget_range_fen IS NULL OR (JSON_TYPE(budget_range_fen)='ARRAY'
    AND JSON_LENGTH(budget_range_fen)=2
    AND (JSON_TYPE(JSON_EXTRACT(budget_range_fen,'$[0]'))='NULL' OR (JSON_TYPE(JSON_EXTRACT(budget_range_fen,'$[0]'))='INTEGER' AND JSON_EXTRACT(budget_range_fen,'$[0]') >= CAST(0 AS JSON) AND JSON_EXTRACT(budget_range_fen,'$[0]') <= CAST(2147483647 AS JSON)))
    AND (JSON_TYPE(JSON_EXTRACT(budget_range_fen,'$[1]'))='NULL' OR (JSON_TYPE(JSON_EXTRACT(budget_range_fen,'$[1]'))='INTEGER' AND JSON_EXTRACT(budget_range_fen,'$[1]') >= CAST(0 AS JSON) AND JSON_EXTRACT(budget_range_fen,'$[1]') <= CAST(2147483647 AS JSON)))
    AND (JSON_TYPE(JSON_EXTRACT(budget_range_fen,'$[0]'))='INTEGER' OR JSON_TYPE(JSON_EXTRACT(budget_range_fen,'$[1]'))='INTEGER')
    AND (JSON_TYPE(JSON_EXTRACT(budget_range_fen,'$[0]'))='NULL' OR JSON_TYPE(JSON_EXTRACT(budget_range_fen,'$[1]'))='NULL' OR JSON_EXTRACT(budget_range_fen,'$[0]') <= JSON_EXTRACT(budget_range_fen,'$[1]')))),
  UNIQUE (id,factory_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE custom_requirement_item (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  requirement_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_sku_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  item_title VARCHAR(128),
  quantity INT,
  dimension_requirement VARCHAR(500),
  material_requirement VARCHAR(500),
  style_requirement VARCHAR(500),
  acceptance_notes VARCHAR(1000),
  sort_no INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_custom_requirement_item_sort (requirement_id, sort_no),
  KEY idx_custom_requirement_item_source (source_sku_id),
  CHECK (quantity > 0),
  CHECK (sort_no >= 0),
  UNIQUE (id,requirement_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE custom_requirement_attachment (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  requirement_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  requirement_item_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  attachment_type VARCHAR(16) NOT NULL,
  storage_key VARCHAR(255) NOT NULL,
  file_name VARCHAR(255),
  sort_no INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  material_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (id),
  CHECK (sort_no >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE custom_quote (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  quote_no VARCHAR(64) NOT NULL,
  requirement_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  factory_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  quote_version INT NOT NULL,
  quote_status VARCHAR(32) NOT NULL,
  custom_goods_amount_fen INT NOT NULL,
  delivery_fee_fen INT NOT NULL,
  total_amount_fen INT NOT NULL,
  production_days INT NOT NULL,
  promised_completion_date DATE NOT NULL,
  spec_snapshot_json JSON NOT NULL,
  quote_remark VARCHAR(1000),
  valid_until DATETIME(3),
  published_at DATETIME(3),
  accepted_at DATETIME(3),
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_custom_quote_no (quote_no),
  UNIQUE KEY uk_custom_quote_version (requirement_id, quote_version),
  KEY idx_custom_quote_active (requirement_id, quote_status, valid_until),
  CHECK (custom_goods_amount_fen >= 0),
  CHECK (delivery_fee_fen >= 0),
  CHECK (total_amount_fen >= 0),
  CHECK (production_days > 0),
  UNIQUE (id,requirement_id,factory_id),
  CHECK (total_amount_fen=custom_goods_amount_fen+delivery_fee_fen)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE custom_order (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  custom_order_no VARCHAR(64) NOT NULL,
  requirement_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  accepted_quote_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  current_delivery_record_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  acceptance_status VARCHAR(16) NOT NULL,
  acceptance_paused_at DATETIME(3),
  acceptance_remaining_seconds INT,
  buyer_user_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  factory_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payment_order_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  order_status VARCHAR(32) NOT NULL,
  custom_goods_original_amount_fen INT NOT NULL,
  custom_goods_discount_amount_fen INT NOT NULL,
  custom_goods_paid_amount_fen INT NOT NULL,
  delivery_fee_paid_fen INT NOT NULL,
  total_paid_amount_fen INT NOT NULL,
  refunded_amount_fen INT NOT NULL,
  delivery_mode VARCHAR(32) NOT NULL,
  delivery_address_snapshot VARCHAR(500),
  promised_completion_date DATE NOT NULL,
  production_started_at DATETIME(3),
  delivered_at DATETIME(3),
  acceptance_deadline_at DATETIME(3),
  accepted_at DATETIME(3),
  completed_at DATETIME(3),
  commission_rate_bps_snapshot INT NOT NULL,
  version INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_custom_order_no (custom_order_no),
  UNIQUE KEY uk_custom_order_requirement (requirement_id),
  KEY idx_custom_order_buyer (buyer_user_id, order_status, updated_at),
  KEY idx_custom_order_factory (factory_id, order_status, promised_completion_date),
  CHECK (custom_goods_original_amount_fen >= 0),
  CHECK (custom_goods_discount_amount_fen >= 0),
  CHECK (custom_goods_paid_amount_fen >= 0),
  CHECK (delivery_fee_paid_fen >= 0),
  CHECK (total_paid_amount_fen >= 0),
  CHECK (refunded_amount_fen >= 0),
  CHECK (commission_rate_bps_snapshot BETWEEN 0 AND 10000),
  CHECK (version >= 0),
  UNIQUE (id,factory_id),
  UNIQUE (id,buyer_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE custom_order_item (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  custom_order_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  source_quote_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  item_title VARCHAR(128) NOT NULL,
  quantity INT NOT NULL,
  unit_price_fen INT NOT NULL,
  item_amount_fen INT NOT NULL,
  spec_snapshot_json JSON NOT NULL,
  acceptance_standard VARCHAR(2000) NOT NULL,
  source_requirement_snapshot_json JSON NOT NULL,
  sort_no INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  source_requirement_item_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  PRIMARY KEY (id),
  KEY idx_custom_order_item_order (custom_order_id, sort_no),
  KEY idx_custom_order_item_quote (source_quote_id),
  CHECK (quantity > 0),
  CHECK (unit_price_fen >= 0),
  CHECK (item_amount_fen >= 0),
  CHECK (sort_no >= 0),
  UNIQUE (custom_order_id,source_requirement_item_id),
  CHECK (item_amount_fen=CAST(unit_price_fen AS SIGNED)*quantity)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE custom_delivery_record (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  custom_order_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  delivery_version INT NOT NULL,
  delivery_request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  delivery_mode VARCHAR(32) NOT NULL,
  delivery_status VARCHAR(32) NOT NULL,
  receiver_name_snapshot VARCHAR(64),
  receiver_phone_snapshot VARCHAR(20),
  delivery_address_snapshot VARCHAR(500),
  dispatch_at DATETIME(3),
  delivered_at DATETIME(3),
  acceptance_deadline_at DATETIME(3),
  pickup_registered_at DATETIME(3),
  handover_code VARCHAR(64),
  evidence_json JSON NOT NULL,
  remark VARCHAR(1000),
  version INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  operator_user_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  order_item_ids_json JSON NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_custom_delivery_version (custom_order_id, delivery_version),
  KEY idx_custom_delivery_status (delivery_status, updated_at),
  CHECK (version >= 0),
  UNIQUE (delivery_request_id),
  UNIQUE (id,custom_order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE custom_order_extension_request (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  extension_request_no VARCHAR(64) NOT NULL,
  submit_idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  custom_order_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  factory_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  original_promised_date DATE NOT NULL,
  requested_promised_date DATE NOT NULL,
  request_reason VARCHAR(1000) NOT NULL,
  evidence_json JSON,
  request_status VARCHAR(32) NOT NULL,
  admin_case_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  resolution_summary VARCHAR(1000),
  submitted_at DATETIME(3),
  resolved_at DATETIME(3),
  version INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  pending_order_guard VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (IF(request_status='submitted',custom_order_id,NULL)) STORED,
  PRIMARY KEY (id),
  UNIQUE KEY uk_custom_extension_no (extension_request_no),
  UNIQUE KEY uk_custom_extension_submit (submit_idempotency_key),
  KEY idx_custom_extension_order (custom_order_id, request_status, created_at),
  KEY idx_custom_extension_case (admin_case_id),
  CHECK (version >= 0),
  UNIQUE (pending_order_guard)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE custom_after_sale_request (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  after_sale_no VARCHAR(64) NOT NULL,
  submit_idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  custom_order_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  buyer_user_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  after_sale_type VARCHAR(32) NOT NULL,
  problem_summary VARCHAR(2000) NOT NULL,
  evidence_json JSON NOT NULL,
  requested_solution VARCHAR(32) NOT NULL,
  after_sale_status VARCHAR(32) NOT NULL,
  source_delivery_record_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  resolution_delivery_record_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  active_order_guard VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (IF(after_sale_status IN ('submitted','processing','waiting_factory','waiting_user','refund_processing'),custom_order_id,NULL)) STORED,
  resolution_type VARCHAR(32),
  approved_refund_amount_fen INT NOT NULL,
  refund_order_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  admin_case_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  resolution_summary VARCHAR(2000),
  submitted_at DATETIME(3) NOT NULL,
  resolved_at DATETIME(3),
  version INT NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_custom_after_sale_no (after_sale_no),
  UNIQUE KEY uk_custom_after_sale_submit (submit_idempotency_key),
  KEY idx_custom_after_sale_order (custom_order_id, after_sale_status, updated_at),
  KEY idx_custom_after_sale_case (admin_case_id),
  CHECK (approved_refund_amount_fen >= 0),
  CHECK (version >= 0),
  UNIQUE (active_order_guard)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE custom_order_settlement (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  custom_order_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  factory_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  settlement_batch_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  goods_paid_amount_fen INT NOT NULL,
  goods_refunded_amount_fen INT NOT NULL,
  goods_settlement_adjustment_fen INT NOT NULL,
  goods_settle_base_amount_fen INT NOT NULL,
  commission_rate_bps INT NOT NULL,
  platform_commission_amount_fen INT NOT NULL,
  factory_goods_income_fen INT NOT NULL,
  delivery_fee_settle_fen INT NOT NULL,
  factory_settle_amount_fen INT NOT NULL,
  settlement_status VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_custom_order_settlement (custom_order_id),
  KEY idx_custom_settlement_batch (settlement_batch_id, settlement_status),
  KEY idx_custom_settlement_factory (factory_id, settlement_status, created_at),
  CHECK (goods_paid_amount_fen >= 0),
  CHECK (goods_refunded_amount_fen >= 0),
  CHECK (goods_settlement_adjustment_fen >= 0),
  CHECK (goods_settle_base_amount_fen >= 0),
  CHECK (commission_rate_bps BETWEEN 0 AND 10000),
  CHECK (platform_commission_amount_fen >= 0),
  CHECK (factory_goods_income_fen >= 0),
  CHECK (delivery_fee_settle_fen >= 0),
  CHECK (factory_settle_amount_fen >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE custom_material (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  owner_user_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  requirement_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  custom_order_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  purpose VARCHAR(16) NOT NULL,
  upload_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  storage_key VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  attachment_type VARCHAR(16) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  UNIQUE (owner_user_id,upload_key),
  UNIQUE (storage_key),
  CHECK ((purpose='requirement' AND requirement_id IS NOT NULL AND custom_order_id IS NULL)
    OR (purpose IN ('delivery','extension','after_sale') AND custom_order_id IS NOT NULL AND requirement_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE custom_requirement
  ADD FOREIGN KEY (buyer_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (factory_id) REFERENCES factory_profile (id) ON DELETE RESTRICT;

ALTER TABLE custom_requirement_item
  ADD FOREIGN KEY (requirement_id) REFERENCES custom_requirement (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (source_sku_id) REFERENCES prop_sku (id) ON DELETE RESTRICT;

ALTER TABLE custom_requirement_attachment
  ADD FOREIGN KEY (requirement_id) REFERENCES custom_requirement (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (material_id) REFERENCES custom_material (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (requirement_item_id,requirement_id) REFERENCES custom_requirement_item (id,requirement_id) ON DELETE RESTRICT;

ALTER TABLE custom_quote
  ADD FOREIGN KEY (requirement_id) REFERENCES custom_requirement (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (factory_id) REFERENCES factory_profile (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (requirement_id,factory_id) REFERENCES custom_requirement (id,factory_id) ON DELETE RESTRICT;

ALTER TABLE custom_order
  ADD FOREIGN KEY (requirement_id) REFERENCES custom_requirement (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (accepted_quote_id) REFERENCES custom_quote (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (buyer_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (factory_id) REFERENCES factory_profile (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (payment_order_id) REFERENCES payment_order (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (current_delivery_record_id,id) REFERENCES custom_delivery_record (id,custom_order_id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (accepted_quote_id,requirement_id,factory_id) REFERENCES custom_quote (id,requirement_id,factory_id) ON DELETE RESTRICT;

ALTER TABLE custom_order_item
  ADD FOREIGN KEY (custom_order_id) REFERENCES custom_order (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (source_quote_id) REFERENCES custom_quote (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (source_requirement_item_id) REFERENCES custom_requirement_item (id) ON DELETE RESTRICT;

ALTER TABLE custom_delivery_record
  ADD FOREIGN KEY (custom_order_id) REFERENCES custom_order (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (operator_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT;

ALTER TABLE custom_order_extension_request
  ADD FOREIGN KEY (custom_order_id) REFERENCES custom_order (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (factory_id) REFERENCES factory_profile (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (admin_case_id) REFERENCES admin_case (id) ON DELETE RESTRICT;

ALTER TABLE custom_after_sale_request
  ADD FOREIGN KEY (custom_order_id) REFERENCES custom_order (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (buyer_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (refund_order_id) REFERENCES refund_order (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (admin_case_id) REFERENCES admin_case (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (source_delivery_record_id,custom_order_id) REFERENCES custom_delivery_record (id,custom_order_id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (resolution_delivery_record_id,custom_order_id) REFERENCES custom_delivery_record (id,custom_order_id) ON DELETE RESTRICT;

ALTER TABLE custom_order_settlement
  ADD FOREIGN KEY (custom_order_id) REFERENCES custom_order (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (factory_id) REFERENCES factory_profile (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (settlement_batch_id) REFERENCES factory_settlement_batch (id) ON DELETE RESTRICT;

ALTER TABLE custom_material
  ADD FOREIGN KEY (owner_user_id) REFERENCES sys_user (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (requirement_id) REFERENCES custom_requirement (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (custom_order_id) REFERENCES custom_order (id) ON DELETE RESTRICT;

ALTER TABLE payment_channel_suborder ADD FOREIGN KEY (custom_order_id) REFERENCES custom_order (id) ON DELETE RESTRICT;

ALTER TABLE payment_allocation ADD FOREIGN KEY (custom_order_id) REFERENCES custom_order (id) ON DELETE RESTRICT;

ALTER TABLE platform_commission_record ADD FOREIGN KEY (custom_order_id) REFERENCES custom_order (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (custom_settlement_id) REFERENCES custom_order_settlement (id) ON DELETE RESTRICT;

ALTER TABLE wechat_profit_sharing_order ADD FOREIGN KEY (custom_order_id) REFERENCES custom_order (id) ON DELETE RESTRICT,
  ADD FOREIGN KEY (custom_settlement_id) REFERENCES custom_order_settlement (id) ON DELETE RESTRICT;
