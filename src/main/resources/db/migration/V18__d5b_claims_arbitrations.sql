CREATE TABLE order_claim (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `return_check_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `claim_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `submit_idempotency_key` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `claim_type` VARCHAR(32) NOT NULL,
  `claim_status` VARCHAR(32) NOT NULL,
  `submitted_by_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `submitted_role` VARCHAR(32) NOT NULL,
  `claimed_amount_fen` INT NULL,
  `approved_amount_fen` INT NULL,
  `fund_subject` VARCHAR(16) NOT NULL,
  `available_deposit_amount_fen_snapshot` INT NULL,
  `problem_summary` VARCHAR(500) NULL,
  `admin_review_status` VARCHAR(32) NOT NULL,
  `admin_review_remark` VARCHAR(500) NULL,
  `reviewed_by_admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `reviewed_at` DATETIME(3) NULL,
  `user_response_deadline_at` DATETIME(3) NULL,
  `withdrawn_at` DATETIME(3) NULL,
  `version` INT NOT NULL,
  `created_at` DATETIME(3) NOT NULL,
  `updated_at` DATETIME(3) NOT NULL,
  problem_type VARCHAR(32) NULL,
  affected_items_json JSON NULL,
  CHECK(claim_status IN ('draft','withdrawn') OR (problem_summary IS NOT NULL AND claimed_amount_fen IS NOT NULL AND problem_type IS NOT NULL AND affected_items_json IS NOT NULL)),
  CHECK(REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CHECK(version>=0),
  CHECK(claim_status IN ('draft','submitted','admin_reviewing','supplement_required','waiting_user_response','user_confirmed','user_appealed','auto_confirmed','executing','rejected','withdrawn','closed')),
  CHECK(fund_subject='deposit'),
  CHECK(claim_type<>'factory_claim' OR return_check_id IS NOT NULL),
  CHECK(claim_status<>'waiting_user_response' OR (approved_amount_fen IS NOT NULL AND user_response_deadline_at IS NOT NULL)),
  PRIMARY KEY(id),
  UNIQUE(claim_no),
  UNIQUE(submit_idempotency_key),
  KEY(return_check_id,claim_status),
  KEY(claim_status,user_response_deadline_at,id),
  CHECK(claimed_amount_fen>=0),
  CHECK(approved_amount_fen>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE claim_user_response (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `claim_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `buyer_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `response_type` VARCHAR(32) NOT NULL,
  `response_remark` VARCHAR(1000) NULL,
  `responded_at` DATETIME(3) NOT NULL,
  `created_at` DATETIME(3) NOT NULL,
  CHECK(REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CHECK(response_type IN ('confirm','appeal','timeout_auto_confirm')),
  PRIMARY KEY(id),
  UNIQUE(claim_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE arbitration_case (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `case_no` VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `claim_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `source_issue_report_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `case_type` VARCHAR(32) NOT NULL,
  `case_status` VARCHAR(32) NOT NULL,
  `version` INT NOT NULL,
  `opened_by_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `opened_role` VARCHAR(32) NOT NULL,
  `summary` VARCHAR(500) NOT NULL,
  `opened_at` DATETIME(3) NOT NULL,
  `closed_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL,
  `updated_at` DATETIME(3) NOT NULL,
  CHECK(REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CHECK(version>=0),
  CHECK(case_status IN ('open','evidence_review','waiting_evidence','decision_draft','decided','executing','execution_failed','closed','cancelled')),
  PRIMARY KEY(id),
  UNIQUE(case_no),
  UNIQUE(claim_id),
  UNIQUE(source_issue_report_id),
  KEY(case_status,updated_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE arbitration_evidence_link (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `arbitration_case_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `evidence_record_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `evidence_role` VARCHAR(32) NOT NULL,
  `remark` VARCHAR(255) NULL,
  `created_at` DATETIME(3) NOT NULL,
  CHECK(REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CHECK(evidence_role IN ('buyer','factory','system','admin')),
  PRIMARY KEY(id),
  UNIQUE(arbitration_case_id,evidence_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE arbitration_decision (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `arbitration_case_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `decision_status` VARCHAR(32) NOT NULL,
  `confirm_request_id` VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `confirmed_case_guard` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin GENERATED ALWAYS AS (CASE WHEN decision_status='confirmed' THEN arbitration_case_id END) STORED,
  `responsibility_party` VARCHAR(32) NOT NULL,
  `rent_deduction_amount_fen` INT NOT NULL,
  `confirmed_loss_amount_fen` INT NOT NULL,
  `deposit_deduction_amount_fen` INT NOT NULL,
  `excess_compensation_amount_fen` INT NOT NULL,
  `compensation_receivable_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `refund_amount_fen` INT NOT NULL,
  `inventory_result` VARCHAR(64) NULL,
  `credit_event_required` TINYINT NOT NULL,
  `decision_summary` VARCHAR(1000) NOT NULL,
  `decided_by_admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `decided_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL,
  `updated_at` DATETIME(3) NOT NULL,
  stock_outcomes_json JSON NOT NULL,
  refund_components_json JSON NOT NULL,
  CHECK(credit_event_required=0),
  CHECK(excess_compensation_amount_fen=confirmed_loss_amount_fen-deposit_deduction_amount_fen),
  CHECK(REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CHECK(decision_status IN ('draft','confirmed','cancelled')),
  CHECK(decision_status<>'confirmed' OR (confirm_request_id IS NOT NULL AND decided_at IS NOT NULL)),
  PRIMARY KEY(id),
  UNIQUE(confirm_request_id),
  UNIQUE(confirmed_case_guard),
  CHECK(rent_deduction_amount_fen>=0),
  CHECK(confirmed_loss_amount_fen>=0),
  CHECK(deposit_deduction_amount_fen>=0),
  CHECK(excess_compensation_amount_fen>=0),
  CHECK(refund_amount_fen>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE platform_intervention_record (
  `id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `rental_order_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `related_claim_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `intervention_type` VARCHAR(32) NOT NULL,
  `intervention_status` VARCHAR(32) NOT NULL,
  `summary` VARCHAR(1000) NOT NULL,
  `compensation_type` VARCHAR(32) NULL,
  `compensation_amount_fen` INT NULL,
  `handled_by_admin_user_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `resolved_at` DATETIME(3) NULL,
  `created_at` DATETIME(3) NOT NULL,
  `updated_at` DATETIME(3) NOT NULL,
  CHECK(REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CHECK(intervention_status IN ('open','processing','resolved','closed')),
  PRIMARY KEY(id),
  KEY(rental_order_id,intervention_status,created_at),
  CHECK(compensation_amount_fen>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE order_claim ADD FOREIGN KEY (rental_order_id) REFERENCES rental_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_claim ADD FOREIGN KEY (return_check_id) REFERENCES order_return_check(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_claim ADD FOREIGN KEY (submitted_by_user_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_claim ADD FOREIGN KEY (reviewed_by_admin_user_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE claim_user_response ADD FOREIGN KEY (claim_id) REFERENCES order_claim(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE claim_user_response ADD FOREIGN KEY (rental_order_id) REFERENCES rental_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE claim_user_response ADD FOREIGN KEY (buyer_user_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE arbitration_case ADD FOREIGN KEY (rental_order_id) REFERENCES rental_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE arbitration_case ADD FOREIGN KEY (claim_id) REFERENCES order_claim(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE arbitration_case ADD FOREIGN KEY (source_issue_report_id) REFERENCES order_issue_report(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE arbitration_case ADD FOREIGN KEY (opened_by_user_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE arbitration_evidence_link ADD FOREIGN KEY (arbitration_case_id) REFERENCES arbitration_case(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE arbitration_evidence_link ADD FOREIGN KEY (evidence_record_id) REFERENCES order_evidence_record(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE arbitration_decision ADD FOREIGN KEY (arbitration_case_id) REFERENCES arbitration_case(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE arbitration_decision ADD FOREIGN KEY (rental_order_id) REFERENCES rental_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE arbitration_decision ADD FOREIGN KEY (compensation_receivable_id) REFERENCES order_compensation_receivable(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE arbitration_decision ADD FOREIGN KEY (decided_by_admin_user_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE platform_intervention_record ADD FOREIGN KEY (rental_order_id) REFERENCES rental_order(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE platform_intervention_record ADD FOREIGN KEY (related_claim_id) REFERENCES order_claim(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE platform_intervention_record ADD FOREIGN KEY (handled_by_admin_user_id) REFERENCES sys_user(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
ALTER TABLE order_return_check ADD remaining_close_ms BIGINT NULL, ADD CHECK (remaining_close_ms BETWEEN 0 AND 259200000);
ALTER TABLE order_evidence_record ADD FOREIGN KEY (claim_id) REFERENCES order_claim(id) ON DELETE RESTRICT;
