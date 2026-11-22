-- 通用字段约束不绑定当前种子；可执行页面仍须通过服务端与前端编译注册表。
ALTER TABLE admin_menu
    DROP CHECK ck_admin_menu_12,
    ADD CONSTRAINT ck_admin_menu_12 CHECK (
        (menu_type <> 'menu' OR (route_path IS NOT NULL AND component_path IS NOT NULL))
        AND (route_path IS NULL OR REGEXP_LIKE(route_path, '^(/[a-z][a-z0-9-]*)+$', 'c'))
        AND (component_path IS NULL OR REGEXP_LIKE(component_path, '^[A-Za-z][A-Za-z0-9]*(/[A-Za-z][A-Za-z0-9]*)+$', 'c'))
    );
