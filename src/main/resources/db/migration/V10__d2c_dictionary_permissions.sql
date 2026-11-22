-- Strict inserts deliberately fail on seed id/code/permission conflict.
SET @d2c_time=CONVERT_TZ(UTC_TIMESTAMP(3),'+00:00','+08:00');
INSERT INTO option_dictionary (id,option_group,option_code,option_label,sort_order,enabled,remark,created_at,updated_at) VALUES ('3b61e27d4ff842b09ac571453da28624','prop_sku_style-style','chinese_classical','中式/古典',1,1,NULL,@d2c_time,@d2c_time);

INSERT INTO option_dictionary (id,option_group,option_code,option_label,sort_order,enabled,remark,created_at,updated_at) VALUES ('9a436c1f84694606bf23a7c4375e1099','prop_sku_style-style','western_classical','西洋/古典',2,1,NULL,@d2c_time,@d2c_time);

INSERT INTO option_dictionary (id,option_group,option_code,option_label,sort_order,enabled,remark,created_at,updated_at) VALUES ('67fed139dd5a4e97b4eec938be95f062','prop_sku_style-style','retro','复古',3,1,NULL,@d2c_time,@d2c_time);

INSERT INTO option_dictionary (id,option_group,option_code,option_label,sort_order,enabled,remark,created_at,updated_at) VALUES ('30b48157fae34f35a08871d08d54e58a','prop_sku_style-style','modern','现代',4,1,NULL,@d2c_time,@d2c_time);

INSERT INTO option_dictionary (id,option_group,option_code,option_label,sort_order,enabled,remark,created_at,updated_at) VALUES ('1273b34ea6424e05a663a41497fde424','prop_sku_style-style','industrial_ruins','工业/废墟',5,1,NULL,@d2c_time,@d2c_time);

INSERT INTO option_dictionary (id,option_group,option_code,option_label,sort_order,enabled,remark,created_at,updated_at) VALUES ('4881bad503b9407e95a16467ec2018ab','prop_sku_style-style','ins_healing','网红/治愈',6,1,NULL,@d2c_time,@d2c_time);

INSERT INTO option_dictionary (id,option_group,option_code,option_label,sort_order,enabled,remark,created_at,updated_at) VALUES ('ff847d73114a426e8cad504bcb89f510','prop_sku_style-style','childlike_dreamy','童趣/梦幻',7,1,NULL,@d2c_time,@d2c_time);

INSERT INTO option_dictionary (id,option_group,option_code,option_label,sort_order,enabled,remark,created_at,updated_at) VALUES ('c51e82dfce2c43a492cc6e3627885052','prop_sku_style-style','sci_fi_avant_garde','科幻/先锋',8,1,NULL,@d2c_time,@d2c_time);

INSERT INTO option_dictionary (id,option_group,option_code,option_label,sort_order,enabled,remark,created_at,updated_at) VALUES ('e0211248e6244ba9bd09b963a0bd26c5','prop_sku_style-style','natural_exotic','自然原野/异域',9,1,NULL,@d2c_time,@d2c_time);

INSERT INTO option_dictionary (id,option_group,option_code,option_label,sort_order,enabled,remark,created_at,updated_at) VALUES ('5c857426ae5741f799c6fef4c2010e59','prop_sku_style-style','special_asset','特殊资产',10,1,NULL,@d2c_time,@d2c_time);

INSERT INTO option_dictionary (id,option_group,option_code,option_label,sort_order,enabled,remark,created_at,updated_at) VALUES ('6620fc08998942dfb2c842c6058901b1','prop_sku-fire_resistant','yes','是',11,1,NULL,@d2c_time,@d2c_time);

INSERT INTO option_dictionary (id,option_group,option_code,option_label,sort_order,enabled,remark,created_at,updated_at) VALUES ('5faa978a143f4d65aeabb9420718920c','prop_sku-fire_resistant','no','否',12,1,NULL,@d2c_time,@d2c_time);

INSERT INTO option_dictionary (id,option_group,option_code,option_label,sort_order,enabled,remark,created_at,updated_at) VALUES ('d9e95e40ccc3485e8d45a653f62b4956','prop_sku-fire_resistant','sprayable','可喷阻燃液',13,1,NULL,@d2c_time,@d2c_time);

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('0ccee910e32f42ef8bd9688029a67b68',NULL,'道具与库存','directory',NULL,'/props',NULL,1,1,70,@d2c_time,@d2c_time);

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('94d23a4546924c6a87e974daac1de5f8','534505ce87e4482c94be274e2d506b6c','0ccee910e32f42ef8bd9688029a67b68','3e8c4a5e468449709003045cd6dfca76',@d2c_time);

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('6ec658581e43482db1b0d8d15125de4f','0ccee910e32f42ef8bd9688029a67b68','道具与库存审核','menu','admin:prop-review:read','/props/reviews','props/PropReviews',1,1,1,@d2c_time,@d2c_time);

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('fa908ffa88a14509bc8de856b79bc90c','534505ce87e4482c94be274e2d506b6c','6ec658581e43482db1b0d8d15125de4f','3e8c4a5e468449709003045cd6dfca76',@d2c_time);

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('ba7ce6acad564dd29d94ddd8d6c63926','6ec658581e43482db1b0d8d15125de4f','道具与库存审核 review','button','admin:prop-review:review',NULL,NULL,1,1,2,@d2c_time,@d2c_time);

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('80819c1b57694ce38228701863f20617','534505ce87e4482c94be274e2d506b6c','ba7ce6acad564dd29d94ddd8d6c63926','3e8c4a5e468449709003045cd6dfca76',@d2c_time);

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('3add6f88cf5c41de9dd6598cb6f44ac7','6ec658581e43482db1b0d8d15125de4f','道具与库存审核 sensitive','button','admin:prop-review:sensitive',NULL,NULL,1,1,3,@d2c_time,@d2c_time);

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('9fe141f402234899bcef6ca481d7bcf9','534505ce87e4482c94be274e2d506b6c','3add6f88cf5c41de9dd6598cb6f44ac7','3e8c4a5e468449709003045cd6dfca76',@d2c_time);

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('49e1029e58ba4b10ba4a28e3f5ed38e9','0ccee910e32f42ef8bd9688029a67b68','道具资产与租期','menu','admin:prop-asset:read','/props/assets','props/PropAssets',1,1,11,@d2c_time,@d2c_time);

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('13c4a7d5af6d4493ba349e41d1ebd6f1','534505ce87e4482c94be274e2d506b6c','49e1029e58ba4b10ba4a28e3f5ed38e9','3e8c4a5e468449709003045cd6dfca76',@d2c_time);

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('b00756de0369487ba8ab6661e0e6bd83','49e1029e58ba4b10ba4a28e3f5ed38e9','道具资产与租期 sensitive','button','admin:prop-asset:sensitive',NULL,NULL,1,1,12,@d2c_time,@d2c_time);

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('4d317edddf884a859bfd145996719120','534505ce87e4482c94be274e2d506b6c','b00756de0369487ba8ab6661e0e6bd83','3e8c4a5e468449709003045cd6dfca76',@d2c_time);

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('b03340d8be624fdd90277d0fa556c4fc','0ccee910e32f42ef8bd9688029a67b68','二维码异常','menu','admin:prop-qr:read','/props/qr-exceptions','props/QrExceptions',1,1,21,@d2c_time,@d2c_time);

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('90a73ff54e6545d49739b84899b3a9d8','534505ce87e4482c94be274e2d506b6c','b03340d8be624fdd90277d0fa556c4fc','3e8c4a5e468449709003045cd6dfca76',@d2c_time);

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('42e44a9eb8d94c9fa961cec1bdad8d59','b03340d8be624fdd90277d0fa556c4fc','二维码异常 manage','button','admin:prop-qr:manage',NULL,NULL,1,1,22,@d2c_time,@d2c_time);

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('3f5708da1f004f81acd90696c55e55a3','534505ce87e4482c94be274e2d506b6c','42e44a9eb8d94c9fa961cec1bdad8d59','3e8c4a5e468449709003045cd6dfca76',@d2c_time);

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('92cfb8f75004467ab1bae51567fede32','b03340d8be624fdd90277d0fa556c4fc','二维码异常 review','button','admin:prop-qr:review',NULL,NULL,1,1,23,@d2c_time,@d2c_time);

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('1c134b0996b64e519dc57ee75c59e1b3','534505ce87e4482c94be274e2d506b6c','92cfb8f75004467ab1bae51567fede32','3e8c4a5e468449709003045cd6dfca76',@d2c_time);

INSERT INTO admin_menu (id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at) VALUES ('cabe4b7fdd204a67a95643743a1e1bef','b03340d8be624fdd90277d0fa556c4fc','二维码异常 sensitive','button','admin:prop-qr:sensitive',NULL,NULL,1,1,24,@d2c_time,@d2c_time);

INSERT INTO admin_role_menu (id,admin_role_id,admin_menu_id,created_by_admin_user_id,created_at) VALUES ('7b825e8a87234775ab115a7bb4ca5d92','534505ce87e4482c94be274e2d506b6c','cabe4b7fdd204a67a95643743a1e1bef','3e8c4a5e468449709003045cd6dfca76',@d2c_time);
