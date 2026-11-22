CREATE TABLE customer_service_session(
 id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,session_no VARCHAR(64) NOT NULL UNIQUE,channel_type VARCHAR(32) NOT NULL CHECK(channel_type='wechat_customer_service'),
 channel_session_key VARCHAR(128),channel_user_key VARCHAR(128) NOT NULL,user_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,active_role VARCHAR(16) NOT NULL,
 factory_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,session_status VARCHAR(32) NOT NULL CHECK(session_status IN ('active','waiting_user','closed')),
 last_event_at DATETIME(3),closed_at DATETIME(3),created_at DATETIME(3) NOT NULL,updated_at DATETIME(3) NOT NULL,
 CHECK((active_role='buyer' AND factory_id IS NULL) OR (active_role='factory' AND factory_id IS NOT NULL)),
 FOREIGN KEY(user_id) REFERENCES sys_user(id),FOREIGN KEY(factory_id) REFERENCES factory_profile(id),
 KEY(channel_type,channel_session_key),KEY(user_id,active_role,last_event_at),KEY(session_status,last_event_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customer_service_context(
 id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,context_request_id VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL UNIQUE,session_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
 user_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,active_role VARCHAR(16) NOT NULL CHECK(active_role IN ('buyer','factory')),source_module VARCHAR(8) NOT NULL,
 source_type VARCHAR(32) NOT NULL,source_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,title_snapshot VARCHAR(128) NOT NULL,status_snapshot VARCHAR(64),
 summary_snapshot VARCHAR(500),return_page_path VARCHAR(255) NOT NULL,context_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
 context_status VARCHAR(32) NOT NULL CHECK(context_status IN ('created','sent','linked_case','resolved','expired')),admin_case_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
 expire_at DATETIME(3) NOT NULL,sent_at DATETIME(3),resolved_at DATETIME(3),created_at DATETIME(3) NOT NULL,updated_at DATETIME(3) NOT NULL,
 FOREIGN KEY(session_id) REFERENCES customer_service_session(id),FOREIGN KEY(user_id) REFERENCES sys_user(id),FOREIGN KEY(admin_case_id) REFERENCES admin_case(id),
 KEY(source_type,source_id,context_status),KEY(session_id,created_at),KEY(admin_case_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customer_service_event_log(
 id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY,channel_event_id VARCHAR(128) COLLATE utf8mb4_0900_bin NOT NULL UNIQUE,session_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin,
 event_type VARCHAR(32) NOT NULL,direction VARCHAR(16),message_type VARCHAR(16),content_summary VARCHAR(500),payload_digest VARCHAR(128) NOT NULL,
 payload_storage_key VARCHAR(255),process_status VARCHAR(16) NOT NULL CHECK(process_status IN ('received','processed','ignored','failed')),
 process_message VARCHAR(255),event_at DATETIME(3) NOT NULL,created_at DATETIME(3) NOT NULL,
 FOREIGN KEY(session_id) REFERENCES customer_service_session(id),KEY(session_id,event_at),KEY(process_status,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
