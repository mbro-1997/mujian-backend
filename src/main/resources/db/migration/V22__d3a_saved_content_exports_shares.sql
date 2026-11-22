-- 2026-11-18 已确认：旧分享无历史快照，统一失效；不伪造签发时内容。
-- 本批仅在隔离库验证；共享库应用及运行切包另行安排。
CREATE TABLE scheme_share_snapshot (
  id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  scheme_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  content_version INT NOT NULL,
  hide_price TINYINT NOT NULL,
  snapshot_json JSON NOT NULL,
  created_at DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY a_share_snapshot_content (scheme_id,content_version,hide_price),
  CONSTRAINT fk_share_snapshot_scheme FOREIGN KEY (scheme_id) REFERENCES scheme(id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT ck_share_snapshot_values CHECK (content_version>=1 AND hide_price IN (0,1)),
  CONSTRAINT ck_share_snapshot_id CHECK (REGEXP_LIKE(id,'^[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}$','c')),
  CONSTRAINT ck_share_snapshot_hidden CHECK (hide_price=0 OR JSON_CONTAINS_PATH(snapshot_json,'one','$.items[*].display.prices')=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE scheme_share
  ADD COLUMN snapshot_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER scheme_id,
  ADD COLUMN request_id VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER snapshot_id,
  DROP INDEX a_share_scheme,
  ADD KEY a_share_scheme (scheme_id,created_at,id),
  ADD UNIQUE KEY a_share_request (created_by,request_id),
  ADD CONSTRAINT fk_share_snapshot FOREIGN KEY (snapshot_id) REFERENCES scheme_share_snapshot(id) ON DELETE RESTRICT ON UPDATE RESTRICT;
UPDATE scheme_share SET share_status='expired', expires_at=COALESCE(expires_at,CURRENT_TIMESTAMP(3)), updated_at=CURRENT_TIMESTAMP(3);
ALTER TABLE scheme_share
  MODIFY expires_at DATETIME(3) NOT NULL,
  ADD CONSTRAINT ck_share_link_snapshot CHECK (
    (snapshot_id IS NULL AND request_id IS NULL AND share_status='expired') OR
    (snapshot_id IS NOT NULL AND request_id IS NOT NULL AND expires_at=DATE_ADD(created_at,INTERVAL 7 DAY)
     AND REGEXP_LIKE(request_id,'^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$','c')));

ALTER TABLE scheme_export DROP CHECK ck_d3a_4_3;
UPDATE scheme_export SET file_expire_at=DATE_ADD(generated_at,INTERVAL 5 MINUTE) WHERE export_status='success';
ALTER TABLE scheme_export
  DROP INDEX a_export_cache,
  ADD CONSTRAINT ck_d3a_4_3 CHECK (COALESCE(
    ((export_status='success' AND local_file_key IS NOT NULL AND generated_at IS NOT NULL AND file_expire_at=DATE_ADD(generated_at,INTERVAL 5 MINUTE))
    OR (export_status IN ('processing','failed') AND local_file_key IS NULL AND generated_at IS NULL AND file_expire_at IS NULL)),FALSE)=TRUE);
