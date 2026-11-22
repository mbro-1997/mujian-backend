INSERT INTO admin_menu
  (id,parent_id,menu_name,menu_type,route_path,component_path,permission_code,visible,enabled,sort_no,created_at,updated_at)
VALUES
  ('ca7993d120d64c27aa3df639a11409e2','47e212cb420e4e088f19479f27c73852','订单问题','menu',
   '/fulfillment/issues','fulfillment/OrderIssues','admin:receipt-issue:read',1,1,108,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR),
  ('dfd27acb3c3045a893f42003add4e98f','ca7993d120d64c27aa3df639a11409e2','协商与补证','button',
   NULL,NULL,'admin:receipt-issue:resolve',1,1,109,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
INSERT INTO admin_role_menu(id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at)
VALUES
  ('75d6cc215e7e4a9babdcd2d4fda4c7fb','534505ce87e4482c94be274e2d506b6c','ca7993d120d64c27aa3df639a11409e2','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR),
  ('774595205cc84b5ea17444561163b25a','534505ce87e4482c94be274e2d506b6c','dfd27acb3c3045a893f42003add4e98f','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
