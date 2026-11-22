CREATE TABLE order_claim_stock_result (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  claim_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  rental_order_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  rental_order_item_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  original_reservation_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  sn_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  quantity INT NOT NULL,
  CHECK(quantity>0 AND (sn_id IS NULL OR quantity=1)),
  outcome VARCHAR(12) NOT NULL CHECK(outcome IN ('intact','repair','lost','scrap','buyout')),
  source_type VARCHAR(16) NOT NULL CHECK(source_type IN ('factory','arbitration')),
  decision_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  replaces_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  result_status VARCHAR(16) NOT NULL CHECK(result_status IN ('recorded','repair_pending','applied','replaced')),
  released_reservation_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
  recorded_by VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  remark VARCHAR(500) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  applied_at DATETIME(3),
  FOREIGN KEY(claim_id) REFERENCES order_claim(id),
  FOREIGN KEY(rental_order_id) REFERENCES rental_order(id),
  FOREIGN KEY(rental_order_item_id) REFERENCES rental_order_item(id),
  FOREIGN KEY(sn_id) REFERENCES prop_sn_instance(id),
  FOREIGN KEY(recorded_by) REFERENCES sys_user(id),
  FOREIGN KEY(decision_id) REFERENCES arbitration_decision(id),
  FOREIGN KEY(replaces_id) REFERENCES order_claim_stock_result(id),
  UNIQUE(replaces_id),
  KEY(claim_id,result_status,id)
);
ALTER TABLE order_return_check DROP CHECK d4_6_c12,
  ADD CONSTRAINT ck_d5_return_close_fact CHECK(
    (closed_at IS NULL AND check_status NOT IN ('no_issue','auto_closed','admin_closed')) OR
    (closed_at IS NOT NULL AND closed_by_user_id IS NOT NULL AND
      (check_status IN ('no_issue','auto_closed','admin_closed') OR (check_status='claim_submitted' AND claim_created=1))));
