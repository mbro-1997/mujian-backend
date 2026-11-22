CREATE TABLE order_issue_report (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issue_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `submit_idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `receipt_check_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `reporter_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `reporter_role` VARCHAR(16) NOT NULL,
  `issue_scene` VARCHAR(16) NOT NULL,
  `primary_issue_type` VARCHAR(32) NOT NULL,
  `issue_summary` VARCHAR(1000) NOT NULL,
  `fulfillment_effect` VARCHAR(16) NOT NULL,
  `issue_status` VARCHAR(32) NOT NULL,
  `resolution_type` VARCHAR(32) NULL,
  `resolution_summary` VARCHAR(1000) NULL,
  `reported_at` DATETIME(3) NOT NULL,
  `resolved_at` DATETIME(3) NULL,
  `version` INT NOT NULL,
  `created_at` DATETIME(3) NOT NULL,
  `updated_at` DATETIME(3) NOT NULL,
  CHECK(fulfillment_effect IN ('continue','paused')),
  CHECK(issue_scene IN ('receipt','in_use')),
  CHECK(REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CHECK(issue_status IN ('submitted','processing','waiting_evidence','resolved','transferred_arbitration','closed')),
  CHECK(reporter_role='buyer'),
  CHECK(version>=0),
  PRIMARY KEY(id),
  UNIQUE(issue_no),
  UNIQUE(submit_idempotency_key),
  KEY(rental_order_id,issue_status,fulfillment_effect),
  KEY(reporter_user_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_issue_item (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issue_report_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `rental_order_item_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `sku_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `issue_type` VARCHAR(32) NOT NULL,
  `affected_quantity` INT NOT NULL,
  `item_summary` VARCHAR(500) NULL,
  `created_at` DATETIME(3) NOT NULL,
  CHECK(affected_quantity>0),
  CHECK(REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CHECK(issue_type IN ('not_as_described','quantity_mismatch','damaged','hidden_defect','other')),
  PRIMARY KEY(id),
  UNIQUE(issue_report_id,rental_order_item_id,issue_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE order_extension_request (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `buyer_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `factory_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `original_due_date` DATE NOT NULL,
  `requested_due_date` DATE NOT NULL,
  `extra_days` INT NOT NULL,
  `max_extra_days_snapshot` INT NULL,
  `a_class_limit_source_at` DATETIME(3) NULL,
  `extension_rent_amount_fen` INT NOT NULL,
  `extension_status` VARCHAR(32) NOT NULL,
  `factory_deadline_at` DATETIME(3) NULL,
  `factory_warning_at` DATETIME(3) NULL,
  `factory_handled_at` DATETIME(3) NULL,
  `payment_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `paid_at` DATETIME(3) NULL,
  `reject_reason` VARCHAR(255) NULL,
  `refund_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `effective_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL,
  `updated_at` DATETIME(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  application_remark VARCHAR(500) NULL,
  latest_paid_at DATETIME(3) NOT NULL,
  submitted_to_factory_at DATETIME(3) NULL,
  active_order_guard VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN extension_status IN ('pending_payment','pending_factory','factory_warning') THEN rental_order_id END) STORED,
  CHECK(extra_days BETWEEN 1 AND 120 AND requested_due_date=DATE_ADD(original_due_date, INTERVAL extra_days DAY)),
  CHECK(extension_status IN ('pending_payment','pending_factory','factory_warning','approved','rejected_refunding','rejected_refunded','payment_failed','cancelled')),
  CHECK(factory_deadline_at IS NULL OR (submitted_to_factory_at IS NOT NULL AND factory_deadline_at=DATE_ADD(submitted_to_factory_at,INTERVAL 24 HOUR))),
  CHECK(REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CHECK(version>=0),
  CHECK(extension_status NOT IN ('pending_factory','factory_warning','approved','rejected_refunding','rejected_refunded') OR (paid_at IS NOT NULL AND paid_at<=latest_paid_at AND submitted_to_factory_at IS NOT NULL AND factory_deadline_at IS NOT NULL)),
  PRIMARY KEY(id),
  UNIQUE(active_order_guard),
  UNIQUE(payment_order_id),
  KEY(extension_status,factory_deadline_at,id),
  KEY(factory_id,extension_status,created_at,id),
  KEY(buyer_user_id,created_at,id),
  CHECK(extension_rent_amount_fen>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE admin_case_note (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `admin_case_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `note_type` VARCHAR(32) NOT NULL,
  `note_content` VARCHAR(2000) NOT NULL,
  `contact_channel` VARCHAR(32) NULL,
  `created_by_admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_at` DATETIME(3) NOT NULL,
  `updated_at` DATETIME(3) NOT NULL,
  CHECK(REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CHECK(note_type IN ('internal_note','contact_record')),
  CHECK(note_type<>'contact_record' OR contact_channel IN ('phone','wechat','offline','other') AND contact_channel IS NOT NULL),
  PRIMARY KEY(id),
  KEY(admin_case_id,created_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE order_issue_report ADD FOREIGN KEY (rental_order_id) REFERENCES rental_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_issue_report ADD FOREIGN KEY (receipt_check_id) REFERENCES order_receipt_check(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_issue_report ADD FOREIGN KEY (reporter_user_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_issue_item ADD FOREIGN KEY (issue_report_id) REFERENCES order_issue_report(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_issue_item ADD FOREIGN KEY (rental_order_id) REFERENCES rental_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_issue_item ADD FOREIGN KEY (rental_order_item_id) REFERENCES rental_order_item(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_issue_item ADD FOREIGN KEY (sku_id) REFERENCES prop_sku(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_extension_request ADD FOREIGN KEY (rental_order_id) REFERENCES rental_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_extension_request ADD FOREIGN KEY (buyer_user_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_extension_request ADD FOREIGN KEY (factory_id) REFERENCES factory_profile(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_extension_request ADD FOREIGN KEY (payment_order_id) REFERENCES payment_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_extension_request ADD FOREIGN KEY (refund_order_id) REFERENCES refund_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE admin_case_note ADD FOREIGN KEY (admin_case_id) REFERENCES admin_case(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE admin_case_note ADD FOREIGN KEY (created_by_admin_user_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_receipt_check ADD remaining_check_ms BIGINT NULL, ADD CHECK (remaining_check_ms BETWEEN 0 AND 43200000);
ALTER TABLE order_evidence_record ADD FOREIGN KEY (issue_report_id) REFERENCES order_issue_report(id) ON DELETE RESTRICT;
ALTER TABLE payment_order ADD FOREIGN KEY (related_extension_request_id) REFERENCES order_extension_request(id) ON DELETE RESTRICT;
ALTER TABLE refund_order DROP CHECK d3b_12_c9, ADD CONSTRAINT ck_d5_refund_type CHECK (refund_type IN ('user_cancel_before_accept','user_cancel_rule','factory_manual_reject','factory_timeout_reject','extension_rejected','extension_cancelled','deposit_release','freight_pool_release','freight_cancel_release','arbitration_refund','customer_service_refund','platform_adjustment','unformed_batch_refund'));


INSERT INTO admin_menu(id,parent_id,menu_name,menu_type,permission_code,visible,enabled,sort_no,created_at,updated_at)
VALUES ('56ff053693fe41e5aa0cf4f8cbbb8a73','74e689a6ad9649f88f984533bff4b945','追加联系记录','button',
        'admin:case:contact-record',1,1,111,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
INSERT INTO admin_role_menu(id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at)
VALUES ('4aa413ccffbd4ea2baa10f6b213c9ede','534505ce87e4482c94be274e2d506b6c','56ff053693fe41e5aa0cf4f8cbbb8a73',
        '3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);

-- V16.3 已发生但未结的风险补齐管理入口，主来源仍引用原风险。
INSERT INTO admin_case(id,case_no,active_case_key,case_type,case_status,priority,title,rental_order_id,
  buyer_user_id,factory_id,created_by_type,created_by_admin_user_id,version,created_at,updated_at)
SELECT r.id,CONCAT('RISK',r.id),CONCAT('fulfillment_risk:',r.id),'fulfillment_risk','pending','P2','逾期影响后续订单',
  o.id,o.buyer_user_id,o.factory_id,'system','3e8c4a5e468449709003045cd6dfca76',0,r.created_at,r.updated_at
FROM order_fulfillment_risk r JOIN rental_order o ON o.id=r.source_rental_order_id
WHERE r.risk_status IN ('pending','processing') AND NOT EXISTS
  (SELECT 1 FROM admin_case_relation l WHERE l.relation_type='fulfillment_risk' AND l.relation_id=r.id AND l.is_primary=1);
INSERT INTO admin_case_relation(id,admin_case_id,relation_type,relation_id,relation_role,is_primary,created_at)
SELECT r.id,c.id,'fulfillment_risk',r.id,'source',1,c.created_at FROM order_fulfillment_risk r
JOIN admin_case c ON c.id=r.id AND c.case_no=CONCAT('RISK',r.id);
INSERT INTO admin_case_relation(id,admin_case_id,relation_type,relation_id,relation_role,is_primary,created_at)
SELECT LOWER(CONCAT(HEX(RANDOM_BYTES(6)),'4',SUBSTR(HEX(RANDOM_BYTES(2)),2),'8',SUBSTR(HEX(RANDOM_BYTES(8)),2))),
  c.id,'rental_order',IF(k.primary_order=1,r.source_rental_order_id,r.affected_rental_order_id),
  IF(k.primary_order=1,'source','affected'),0,c.created_at FROM order_fulfillment_risk r
JOIN admin_case c ON c.id=r.id AND c.case_no=CONCAT('RISK',r.id) CROSS JOIN (SELECT 1 primary_order UNION ALL SELECT 0) k;
