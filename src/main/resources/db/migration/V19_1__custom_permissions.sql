INSERT INTO admin_menu(id,parent_id,menu_name,menu_type,route_path,component_path,permission_code,visible,enabled,sort_no,created_at,updated_at)
VALUES ('f4ab9f20975a48f3b83fc4d3eaa3063c','47e212cb420e4e088f19479f27c73852','定制交付延期','menu','/custom/extensions','custom/Extensions','admin:custom:extension-read',1,1,122,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
INSERT INTO admin_role_menu(id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at)
VALUES ('a36d47e6836e4bd089671e729630be08','534505ce87e4482c94be274e2d506b6c','f4ab9f20975a48f3b83fc4d3eaa3063c','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
INSERT INTO admin_menu(id,parent_id,menu_name,menu_type,route_path,component_path,permission_code,visible,enabled,sort_no,created_at,updated_at)
VALUES ('8acfa5b94d0e4f8cbf259ff98d97b3c0','f4ab9f20975a48f3b83fc4d3eaa3063c','审核定制延期','button',NULL,NULL,'admin:custom:extension-review',1,1,123,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
INSERT INTO admin_role_menu(id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at)
VALUES ('6bd5d276e93640e386c018018fdd6d27','534505ce87e4482c94be274e2d506b6c','8acfa5b94d0e4f8cbf259ff98d97b3c0','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
INSERT INTO admin_menu(id,parent_id,menu_name,menu_type,route_path,component_path,permission_code,visible,enabled,sort_no,created_at,updated_at)
VALUES ('666f18d132244d279275202ef692f22c','47e212cb420e4e088f19479f27c73852','定制售后','menu','/custom/after-sales','custom/AfterSales','admin:custom:after-sale-read',1,1,124,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
INSERT INTO admin_role_menu(id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at)
VALUES ('69748a004d4347ffbe2189399568395a','534505ce87e4482c94be274e2d506b6c','666f18d132244d279275202ef692f22c','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
INSERT INTO admin_menu(id,parent_id,menu_name,menu_type,route_path,component_path,permission_code,visible,enabled,sort_no,created_at,updated_at)
VALUES ('3e9f4eb955a24d91b532e35d180e224f','666f18d132244d279275202ef692f22c','办理定制售后','button',NULL,NULL,'admin:custom:after-sale-resolve',1,1,125,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
INSERT INTO admin_role_menu(id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at)
VALUES ('bf6d59f6e3e34798bf2400e98673aad7','534505ce87e4482c94be274e2d506b6c','3e9f4eb955a24d91b532e35d180e224f','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
INSERT INTO admin_menu(id,parent_id,menu_name,menu_type,route_path,component_path,permission_code,visible,enabled,sort_no,created_at,updated_at)
VALUES ('3cfcabc8862b476eb320b88ab1ca47a9','666f18d132244d279275202ef692f22c','核准定制退款','button',NULL,NULL,'admin:custom:refund-approve',1,1,126,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
INSERT INTO admin_role_menu(id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at)
VALUES ('9a555b4216ee4bf19da74f948e17fcd6','534505ce87e4482c94be274e2d506b6c','3cfcabc8862b476eb320b88ab1ca47a9','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
