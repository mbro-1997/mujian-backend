ALTER TABLE order_claim ADD evidence_reference_json JSON NULL;
INSERT INTO admin_menu(id,parent_id,menu_name,menu_type,route_path,component_path,permission_code,visible,enabled,sort_no,created_at,updated_at) VALUES
('45b1a9dc25f3412d9b65ca24bf7bcfbc','47e212cb420e4e088f19479f27c73852','延期申请','menu','/fulfillment/extensions','fulfillment/Extensions','admin:extension:read',1,1,110,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR),
('e3d9d7d6316f49ee88828009cb682b6c','47e212cb420e4e088f19479f27c73852','工厂索赔','menu','/fulfillment/claims','fulfillment/Claims','admin:claim:read',1,1,112,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR),
('934513928c6a4532b0ad646819aee07b','e3d9d7d6316f49ee88828009cb682b6c','工厂索赔处理','button',NULL,NULL,'admin:claim:review',1,1,113,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR),
('16ab897302924c96ac8b03801b51095c','47e212cb420e4e088f19479f27c73852','仲裁案件','menu','/fulfillment/arbitrations','fulfillment/Arbitrations','admin:arbitration:read',1,1,114,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR),
('d75308ac63d647ff880085cda0424e63','16ab897302924c96ac8b03801b51095c','仲裁案件处理','button',NULL,NULL,'admin:arbitration:decide',1,1,115,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR),
('cce4b0f240ab45e9966d3ef3555df00a','47e212cb420e4e088f19479f27c73852','赔付款','menu','/fulfillment/receivables','fulfillment/Receivables','admin:compensation:read',1,1,116,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
INSERT INTO admin_role_menu(id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES
('3d227b63e80245f9a136cdb30eeb5a0e','534505ce87e4482c94be274e2d506b6c','45b1a9dc25f3412d9b65ca24bf7bcfbc','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR),
('57f4547a4dd544d1ba18f6a978325018','534505ce87e4482c94be274e2d506b6c','e3d9d7d6316f49ee88828009cb682b6c','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR),
('f7e09e917ceb405396f51bc47264bfa5','534505ce87e4482c94be274e2d506b6c','934513928c6a4532b0ad646819aee07b','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR),
('5a8e53ec5b7d4ec392de456a3fdbd71e','534505ce87e4482c94be274e2d506b6c','16ab897302924c96ac8b03801b51095c','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR),
('38129cb3983b4e7cb4193d148c0c9890','534505ce87e4482c94be274e2d506b6c','d75308ac63d647ff880085cda0424e63','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR),
('62dce594bcd54e13950221a09ca10406','534505ce87e4482c94be274e2d506b6c','cce4b0f240ab45e9966d3ef3555df00a','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
