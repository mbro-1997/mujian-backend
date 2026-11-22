package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.*;
import java.sql.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.LinkedHashMap;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class D2CMigrationTest {
  /** 装配隔离的测试配置。 */
  Properties config(String key, String kind) throws Exception {
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(Path.of(Objects.requireNonNull(System.getenv(key))))) {
      p.load(in);
    }
    assertTrue(
        p.getProperty("spring.datasource.url")
            .matches(
                "jdbc:mysql://127\\.0\\.0\\.1:3306/darkmujian_d1_"
                    + kind
                    + "_[a-zA-Z0-9_]+(?:\\?.*)?"));
    return p;
  }

  /** 执行用例指定的迁移。 */
  org.flywaydb.core.api.configuration.FluentConfiguration fly(Properties p) {
    return Flyway.configure()
        .dataSource(
            p.getProperty("spring.datasource.url"),
            p.getProperty("spring.datasource.username"),
            p.getProperty("spring.datasource.password"))
        .cleanDisabled(true);
  }

  /** 连接测试实例。 */
  Connection connect(Properties p) throws Exception {
    return DriverManager.getConnection(
        p.getProperty("spring.datasource.url"),
        p.getProperty("spring.datasource.username"),
        p.getProperty("spring.datasource.password"));
  }

  /** 核对指定条件的记录数量。 */
  long count(Statement s, String sql) throws Exception {
    try (ResultSet r = s.executeQuery(sql)) {
      r.next();
      return r.getLong(1);
    }
  }

  /** 检查预期的空结果。 */
  void empty(Properties p) throws Exception {
    try (Connection c = connect(p);
        Statement s = c.createStatement()) {
      assertEquals(
          0,
          count(s, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"),
          "Only new databases; never clear/reuse populated databases");
    }
  }

  /** 核对本场景的业务结果。 */
  void verify(Properties p) throws Exception {
    JsonNode dictionary =
        new ObjectMapper()
            .readTree(getClass().getResourceAsStream("/contracts/d2c.dictionary.json"));
    int columns = 0, fks = 0;
    try (Connection c = connect(p);
        Statement s = c.createStatement()) {
      for (JsonNode t : dictionary.path("tables")) {
        String name = t.path("name").asString();
        int n = 0;
        try (ResultSet r =
            s.executeQuery(
                "SELECT COLUMN_NAME,IS_NULLABLE,EXTRA FROM information_schema.COLUMNS WHERE"
                    + " TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"
                    + name
                    + "' ORDER BY ORDINAL_POSITION")) {
          while (r.next()) {
            JsonNode f = t.path("fields").get(n++);
            assertEquals(f.path("field").asString(), r.getString(1));
            assertEquals(f.path("nullable").asBoolean() ? "YES" : "NO", r.getString(2));
            columns++;
          }
        }
        assertEquals(t.path("fields").size(), n);
        fks +=
            count(
                s,
                "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE"
                    + " CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='"
                    + name
                    + "' AND DELETE_RULE='RESTRICT' AND UPDATE_RULE='RESTRICT'");
      }
      assertEquals(255, columns);
      assertEquals(49, fks);
      assertEquals(
          13,
          count(
              s,
              "SELECT COUNT(*) FROM option_dictionary WHERE option_group IN"
                  + " ('prop_sku_style-style','prop_sku-fire_resistant')"));
      // Strict seed collision and semantic dictionary codes: no INSERT IGNORE, no mistaken UUID
      // constraint.
      assertThrows(
          SQLException.class,
          () ->
              s.executeUpdate(
                  "INSERT INTO option_dictionary SELECT * FROM option_dictionary WHERE"
                      + " option_code='modern' AND option_group='prop_sku_style-style'"));
      assertEquals(
          1, count(s, "SELECT COUNT(*) FROM option_dictionary WHERE option_code='special_asset'"));
      assertThrows(
          SQLException.class,
          () ->
              s.executeUpdate(
                  "INSERT INTO"
                      + " prop_sku_stock(id,sku_id,total_quantity,rentable_base_quantity,available_quantity,locked_quantity,renting_quantity,lost_quantity,scrapped_quantity,created_at,updated_at)"
                      + " VALUES(REPLACE(UUID(),'-',''),'aaaaaaaaaaaa4aaa8aaaaaaaaaaaaaaa',-1,-1,-1,0,0,0,0,NOW(3),NOW(3))"));
    }
  }

  /** 验证全新迁移的表及字段数。 */
  @Test
  void freshTenMigrationsFifteenTablesAnd255Columns() throws Exception {
    Properties p = config("MJ_TEST_CONFIG", "test");
    empty(p);
    Flyway f = fly(p).load();
    assertEquals(10, f.migrate().migrationsExecuted);
    f.validate();
    assertEquals(0, f.migrate().migrationsExecuted);
    verify(p);
  }

  /** 检查从 V8 升级保留原记录与迁移历史。 */
  @Test
  void v8UpgradePreservesHistoryAndExistingRows() throws Exception {
    Properties p = config("MJ_COMPAT_CONFIG", "compat");
    empty(p);
    assertEquals(8, fly(p).target("8").load().migrate().migrationsExecuted);
    LinkedHashMap<String, Integer> history = new LinkedHashMap<String, Integer>();
    long oldUsers, oldOptions;
    try (Connection c = connect(p);
        Statement s = c.createStatement()) {
      try (ResultSet r =
          s.executeQuery(
              "SELECT version,checksum FROM flyway_schema_history WHERE success=1 ORDER BY"
                  + " installed_rank")) {
        while (r.next()) history.put(r.getString(1), r.getInt(2));
      }
      oldUsers = count(s, "SELECT COUNT(*) FROM sys_user");
      oldOptions = count(s, "SELECT COUNT(*) FROM option_dictionary");
    }
    Flyway f = fly(p).load();
    assertEquals(2, f.migrate().migrationsExecuted);
    f.validate();
    assertEquals(0, f.migrate().migrationsExecuted);
    try (Connection c = connect(p);
        Statement s = c.createStatement()) {
      try (ResultSet r =
          s.executeQuery(
              "SELECT version,checksum FROM flyway_schema_history WHERE installed_rank<=8 ORDER BY"
                  + " installed_rank")) {
        while (r.next()) assertEquals(history.get(r.getString(1)), r.getInt(2));
      }
      assertEquals(oldUsers, count(s, "SELECT COUNT(*) FROM sys_user"));
      assertEquals(oldOptions + 13, count(s, "SELECT COUNT(*) FROM option_dictionary"));
    }
    verify(p);
  }
}
