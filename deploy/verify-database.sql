-- 只读核验指定开发库；本脚本不创建、清空或修改任何数据库对象。
SELECT VERSION() AS server_version, DATABASE() AS database_name,
       @@character_set_server AS server_charset, @@collation_server AS server_collation;
SELECT SCHEMA_NAME, DEFAULT_CHARACTER_SET_NAME, DEFAULT_COLLATION_NAME
FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = DATABASE();
SELECT TABLE_NAME, TABLE_TYPE, TABLE_COLLATION
FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE() ORDER BY TABLE_NAME;
SET @history_query = IF(EXISTS(SELECT 1 FROM information_schema.TABLES
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'flyway_schema_history'),
  'SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success FROM flyway_schema_history ORDER BY installed_rank',
  'SELECT ''ABSENT'' AS flyway_history');
PREPARE read_history FROM @history_query;
EXECUTE read_history;
DEALLOCATE PREPARE read_history;
