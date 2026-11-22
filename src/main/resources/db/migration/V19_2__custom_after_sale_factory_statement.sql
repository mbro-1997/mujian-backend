CREATE TABLE custom_after_sale_factory_statement (
 id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
 after_sale_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 operator_user_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 summary VARCHAR(1000) NOT NULL,
 evidence_json JSON NOT NULL,
 created_at DATETIME(3) NOT NULL,
 KEY idx_statement_sale (after_sale_id,created_at),
 FOREIGN KEY (after_sale_id) REFERENCES custom_after_sale_request(id),
 FOREIGN KEY (operator_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
