CREATE TABLE order_review (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  business_type VARCHAR(32) NOT NULL,
  business_order_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  reviewer_user_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  reviewer_role VARCHAR(32) NOT NULL,
  target_type VARCHAR(32) NOT NULL,
  target_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  tag_json JSON,
  content VARCHAR(1000),
  review_status VARCHAR(32) NOT NULL CHECK (review_status IN ('published','hidden','deleted')),
  created_at DATETIME(3) NOT NULL,
  updated_at DATETIME(3) NOT NULL,
  CHECK (business_type IN ('rental_order','custom_order')),
  CHECK ((reviewer_role='buyer' AND target_type='factory') OR (reviewer_role='factory' AND target_type='user')),
  FOREIGN KEY (reviewer_user_id) REFERENCES sys_user(id),
  UNIQUE KEY uk_order_review_business_role (business_type,business_order_id,reviewer_role),
  KEY idx_order_review_target (target_type,target_id,review_status,created_at),
  KEY idx_order_review_reviewer (reviewer_user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE coupon_distribution (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,
  request_id VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_bin NOT NULL UNIQUE,
  request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  template_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  mode VARCHAR(24) NOT NULL,
  config_json JSON NOT NULL,
  target_count BIGINT,
  issued_quantity BIGINT NOT NULL DEFAULT 0 CHECK (issued_quantity>=0),
  starts_at DATETIME(3),
  ends_at DATETIME(3),
  status VARCHAR(16) NOT NULL,
  version INT NOT NULL DEFAULT 0 CHECK (version>=0),
  created_by_admin_user_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  created_at DATETIME(3) NOT NULL,
  published_at DATETIME(3),
  completed_at DATETIME(3),
  FOREIGN KEY (template_id) REFERENCES coupon_template(id),
  FOREIGN KEY (created_by_admin_user_id) REFERENCES sys_user(id),
  CHECK ((mode='conditional_claim' AND starts_at IS NOT NULL AND ends_at IS NOT NULL AND starts_at<ends_at
    AND target_count IS NULL AND status IN ('draft','active','completed','cancelled'))
    OR (mode IN ('selected_users','all_users') AND status='completed' AND starts_at IS NULL AND ends_at IS NULL
    AND target_count IS NOT NULL AND target_count>0 AND issued_quantity>=target_count
    AND completed_at IS NOT NULL AND published_at IS NOT NULL)),
  KEY idx_distribution_discovery (status,mode,ends_at),
  KEY idx_distribution_template (template_id,created_at),
  KEY idx_distribution_creator (created_by_admin_user_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
