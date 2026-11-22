package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.*;
import java.sql.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.ArrayList;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 新分配的空隔离库；不清库、不覆盖已有库。已有V6库只升级并验证校验和及保留标记。 */
class D2BMigrationTest {
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

  /** 打开隔离库连接。 */
  Connection connection(Properties p) throws Exception {
    return DriverManager.getConnection(
        p.getProperty("spring.datasource.url"),
        p.getProperty("spring.datasource.username"),
        p.getProperty("spring.datasource.password"));
  }

  /** 构造测试迁移执行器。 */
  org.flywaydb.core.api.configuration.FluentConfiguration flyway(Properties p) {
    return Flyway.configure()
        .dataSource(
            p.getProperty("spring.datasource.url"),
            p.getProperty("spring.datasource.username"),
            p.getProperty("spring.datasource.password"))
        .cleanDisabled(true);
  }

  /** 核对指定条件的记录数量。 */
  long count(Statement s, String sql) throws Exception {
    try (ResultSet r = s.executeQuery(sql)) {
      r.next();
      return r.getLong(1);
    }
  }

  /** 检查全新库迁移所得表和字段。 */
  @Test
  void freshEightMigrationsAndFiveTables98Columns() throws Exception {
    Properties p = config("MJ_TEST_CONFIG", "test");
    try (Connection c = connection(p);
        Statement s = c.createStatement()) {
      assertEquals(
          0,
          count(s, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"),
          "本项只接受全新空测试库，禁止清空重用");
    }
    Flyway f = flyway(p).load();
    assertEquals(8, f.migrate().migrationsExecuted);
    f.validate();
    assertEquals(0, f.migrate().migrationsExecuted);
    JsonNode dictionary =
        new ObjectMapper()
            .readTree(getClass().getResourceAsStream("/contracts/d2b.dictionary.json"));
    int columns = 0, indexes = 0, fks = 0;
    try (Connection c = connection(p);
        Statement s = c.createStatement()) {
      for (JsonNode table : dictionary.path("tables")) {
        String name = table.path("name").asString();
        try (ResultSet rs =
            s.executeQuery(
                "SELECT COLUMN_NAME,IS_NULLABLE,EXTRA FROM information_schema.COLUMNS WHERE"
                    + " TABLE_SCHEMA=DATABASE() AND TABLE_NAME='"
                    + name
                    + "' ORDER BY ORDINAL_POSITION")) {
          int n = 0;
          while (rs.next()) {
            JsonNode field = table.path("fields").get(n++);
            assertEquals(field.path("field").asString(), rs.getString(1));
            assertEquals(field.path("nullable").asBoolean() ? "YES" : "NO", rs.getString(2));
            assertEquals(
                !field.path("generatedSql").isNull(), rs.getString(3).contains("GENERATED"));
            columns++;
          }
          assertEquals(table.path("fields").size(), n);
        }
        for (JsonNode index : table.path("indexes")) {
          ArrayList<String> actual = new ArrayList<String>();
          boolean unique = false;
          try (ResultSet rs =
              s.executeQuery(
                  "SELECT INDEX_NAME,NON_UNIQUE,GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS"
                      + " cols FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND"
                      + " TABLE_NAME='"
                      + name
                      + "' GROUP BY INDEX_NAME,NON_UNIQUE")) {
            String cols =
                String.join(",", new ObjectMapper().convertValue(index.get(2), String[].class));
            boolean found = false;
            while (rs.next())
              if (rs.getString(3).equals(cols)) {
                assertEquals(index.get(0).asString().equals("U") ? 0 : 1, rs.getInt(2));
                found = true;
              }
            assertTrue(found, name + " " + index);
          }
          indexes++;
        }
        fks +=
            count(
                s,
                "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE"
                    + " CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME='"
                    + name
                    + "' AND CONSTRAINT_TYPE='FOREIGN KEY'");
      }
      assertEquals(98, columns);
      assertEquals(20, indexes);
      assertEquals(15, fks);
      assertEquals(
          1,
          count(
              s,
              "SELECT COUNT(*) FROM information_schema.KEY_COLUMN_USAGE WHERE"
                  + " TABLE_SCHEMA=DATABASE() AND TABLE_NAME='admin_case' AND"
                  + " COLUMN_NAME='factory_id' AND REFERENCED_TABLE_NAME='factory_profile'"));
      assertEquals(
          12,
          count(s, "SELECT COUNT(*) FROM option_dictionary WHERE option_group='prop_sku-type'"));
      assertEquals(10, count(s, "SELECT COUNT(*) FROM option_dictionary WHERE enabled=1"));
      assertEquals(
          2,
          count(
              s,
              "SELECT COUNT(*) FROM platform_rule_document WHERE rule_scope='supplier' AND"
                  + " rule_status='active'"));
      assertThrows(
          SQLException.class,
          () -> s.executeUpdate("UPDATE option_dictionary SET enabled=2 LIMIT 1"));
      assertThrows(
          SQLException.class,
          () -> s.executeUpdate("UPDATE option_dictionary SET option_code='' LIMIT 1"));
    }
    System.out.println(
        "B FRESH: V1-V8, repeat=0, 5 tables/98 columns/20 declared indexes/16 FKs including"
            + " admin_case, seeds/constraints verified");
  }

  /** 验证从 V6 升级保留既有记录。 */
  @Test
  void upgradeFromV6RetainsHistoryAndData() throws Exception {
    Properties p = config("MJ_COMPAT_CONFIG", "compat");
    try (Connection c = connection(p);
        Statement s = c.createStatement()) {
      assertEquals(
          0,
          count(s, "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()"));
    }
    assertEquals(6, flyway(p).target("6").load().migrate().migrationsExecuted);
    ArrayList<String> before = new ArrayList<String>();
    try (Connection c = connection(p);
        Statement s = c.createStatement()) {
      try (ResultSet rs =
          s.executeQuery(
              "SELECT version,checksum FROM flyway_schema_history ORDER BY installed_rank")) {
        while (rs.next()) before.add(rs.getString(1) + ":" + rs.getString(2));
      }
      s.executeUpdate(
          "UPDATE admin_menu SET menu_name='B升级保留标记' WHERE id='d01c25de0fe44841babaf183bf3ff8b8'");
    }
    Flyway full = flyway(p).load();
    assertEquals(2, full.migrate().migrationsExecuted);
    assertEquals(0, full.migrate().migrationsExecuted);
    full.validate();
    try (Connection c = connection(p);
        Statement s = c.createStatement()) {
      ArrayList<String> after = new ArrayList<String>();
      try (ResultSet rs =
          s.executeQuery(
              "SELECT version,checksum FROM flyway_schema_history WHERE CAST(version AS"
                  + " UNSIGNED)<=6 ORDER BY installed_rank")) {
        while (rs.next()) after.add(rs.getString(1) + ":" + rs.getString(2));
      }
      assertEquals(before, after);
      assertEquals(1, count(s, "SELECT COUNT(*) FROM admin_menu WHERE menu_name='B升级保留标记'"));
    }
    System.out.println("B UPGRADE: V6 -> V8; V1-V6 checksums and existing data retained; repeat=0");
  }
}
