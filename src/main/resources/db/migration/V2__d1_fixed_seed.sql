-- 固定系统身份不是管理员，不设置密码或登录身份。仅版本化写入，不覆盖用户修改。
SET @seed_time = CONVERT_TZ(UTC_TIMESTAMP(3), '+00:00', '+08:00');
INSERT INTO sys_user (id,nickname,avatar_url,phone,phone_verified,phone_source,phone_verified_at,user_status,register_status,created_at,updated_at)
VALUES ('3e8c4a5e468449709003045cd6dfca76','自动操作',NULL,NULL,0,NULL,NULL,'disabled','new',@seed_time,@seed_time);
INSERT INTO admin_role (id,role_code,role_name,role_sort,built_in,enabled,remark,created_by_admin_user_id,created_at,updated_at)
VALUES ('534505ce87e4482c94be274e2d506b6c','platform_admin','平台管理员',0,1,1,NULL,'3e8c4a5e468449709003045cd6dfca76',@seed_time,@seed_time);
INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,icon_name,visible,enabled,sort_no,created_at,updated_at)
VALUES ('62132b6e3e5f4bb9bf5cc0b6c9904dbd',NULL,'系统管理','directory',NULL,NULL,NULL,NULL,1,1,10,@seed_time,@seed_time),
('d01c25de0fe44841babaf183bf3ff8b8','62132b6e3e5f4bb9bf5cc0b6c9904dbd','菜单管理','menu','admin:menu:manage','/system/menus','system/MenuReadOnly',NULL,1,1,10,@seed_time,@seed_time);
INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at)
VALUES ('c2a390e71866492799e0bacb1ea83c2b','534505ce87e4482c94be274e2d506b6c','62132b6e3e5f4bb9bf5cc0b6c9904dbd','3e8c4a5e468449709003045cd6dfca76',@seed_time),
('8da5a0e705e24b3f8c0f5bcb39169939','534505ce87e4482c94be274e2d506b6c','d01c25de0fe44841babaf183bf3ff8b8','3e8c4a5e468449709003045cd6dfca76',@seed_time);
