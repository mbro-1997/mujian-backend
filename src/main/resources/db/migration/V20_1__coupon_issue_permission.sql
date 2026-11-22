INSERT INTO admin_menu(id,parent_id,menu_name,menu_type,route_path,component_path,permission_code,visible,enabled,sort_no,created_at,updated_at)
VALUES ('60dea946158e414aa9f5018827cbb390','47e212cb420e4e088f19479f27c73852','主动发放优惠券','menu','/operations/coupon-issue','operations/CouponIssue','admin:coupon:issue',1,1,130,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR,UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
INSERT INTO admin_role_menu(id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at)
VALUES ('41d51fe69435498da2b82d023366adf1','534505ce87e4482c94be274e2d506b6c','60dea946158e414aa9f5018827cbb390','3e8c4a5e468449709003045cd6dfca76',UTC_TIMESTAMP(3)+INTERVAL 8 HOUR);
