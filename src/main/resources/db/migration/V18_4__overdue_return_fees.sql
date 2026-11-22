CREATE TABLE rental_overdue_fee (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  rental_order_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
  due_date DATE NOT NULL, fee_status VARCHAR(24) NOT NULL,
  reported_method VARCHAR(24), reported_at DATETIME(3), reported_basis VARCHAR(500),
  reported_by VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin, disputed BOOLEAN NOT NULL DEFAULT FALSE,
  handover_method VARCHAR(24), handover_at DATETIME(3), excluded_ms BIGINT, charge_days INT,
  item_snapshot JSON, rent_fen INT, liability_fen INT, rent_offset_fen INT, liability_offset_fen INT,
  review_basis VARCHAR(500), reviewed_by VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  unreported_buyer_fault BOOLEAN, effective_at DATETIME(3), version INT NOT NULL,
  created_at DATETIME(3) NOT NULL, updated_at DATETIME(3) NOT NULL,
  FOREIGN KEY (rental_order_id) REFERENCES rental_order(id),
  CHECK (fee_status IN ('awaiting_handover','pending_review','effective')),
  CHECK (fee_status!='effective' OR (handover_at IS NOT NULL AND effective_at IS NOT NULL AND item_snapshot IS NOT NULL
    AND excluded_ms IS NOT NULL AND excluded_ms>=0 AND charge_days IS NOT NULL AND charge_days>=0
    AND rent_offset_fen IS NOT NULL AND rent_fen IS NOT NULL AND rent_offset_fen BETWEEN 0 AND rent_fen
    AND liability_offset_fen IS NOT NULL AND liability_fen IS NOT NULL AND liability_offset_fen BETWEEN 0 AND liability_fen))
) ENGINE=InnoDB;

ALTER TABLE factory_settlement_detail
  DROP CHECK d4_14_c25,
  ADD overdue_rent_amount_fen INT NOT NULL DEFAULT 0,
  ADD overdue_commission_amount_fen INT NOT NULL DEFAULT 0,
  ADD CONSTRAINT ck_d5_overdue_settle CHECK (
    commission_rate_bps=2000 AND overdue_rent_amount_fen>=0 AND overdue_commission_amount_fen BETWEEN 0 AND overdue_rent_amount_fen
    AND rent_paid_amount_fen=initial_rent_paid_amount_fen+extension_rent_paid_amount_fen+overdue_rent_amount_fen
    AND rent_settle_base_amount_fen=rent_paid_amount_fen-rent_refunded_amount_fen-rent_settlement_adjustment_amount_fen
    AND platform_commission_amount_fen=FLOOR((rent_settle_base_amount_fen-overdue_rent_amount_fen)/5)+overdue_commission_amount_fen
    AND factory_rent_income_amount_fen=rent_settle_base_amount_fen-platform_commission_amount_fen
    AND factory_settle_amount_fen=factory_rent_income_amount_fen+deposit_compensation_amount_fen+excess_compensation_paid_amount_fen+freight_reimbursement_amount_fen);
