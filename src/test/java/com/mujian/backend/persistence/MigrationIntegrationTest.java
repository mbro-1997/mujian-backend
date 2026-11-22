package com.mujian.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MJ_TEST_CONFIG", matches = ".+")
class MigrationIntegrationTest {
  /** 执行真实 MySQL 迁移并核对结构。 */
  @Test
  void migrateRealMysqlAndVerifyStructure() throws Exception {
    com.mujian.backend.support.TestDatabase.prepare();
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(Path.of(System.getenv("MJ_TEST_CONFIG")))) {
      p.load(in);
    }
    String url = p.getProperty("spring.datasource.url");
    assertTrue(url.startsWith("jdbc:mysql://127.0.0.1:3306/darkmujian_d1_test_"));
    String user = p.getProperty("spring.datasource.username"),
        password = p.getProperty("spring.datasource.password");
    Flyway flyway =
        Flyway.configure()
            .dataSource(url, user, password)
            .locations("classpath:db/migration")
            .cleanDisabled(true)
            .load();
    flyway.migrate();
    assertEquals(0, flyway.migrate().migrationsExecuted);
    flyway.validate();
    try (Connection c = DriverManager.getConnection(url, user, password);
        Statement s = c.createStatement()) {
      try (ResultSet r =
          s.executeQuery(
              "SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND"
                  + " TABLE_NAME<>'flyway_schema_history'")) {
        r.next();
        assertEquals(272, r.getInt(1));
      }
      try (ResultSet r =
          s.executeQuery(
              "SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE"
                  + " CONSTRAINT_SCHEMA=DATABASE() AND UPDATE_RULE='RESTRICT' AND"
                  + " DELETE_RULE='RESTRICT'")) {
        r.next();
        assertEquals(32, r.getInt(1));
      }
      try (ResultSet r =
          s.executeQuery(
              "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND"
                  + " TABLE_NAME<>'flyway_schema_history'")) {
        r.next();
        assertEquals(19, r.getInt(1));
      }
      try (ResultSet r =
          s.executeQuery(
              "SELECT nickname,user_status,register_status FROM sys_user WHERE"
                  + " id='3e8c4a5e468449709003045cd6dfca76'")) {
        assertTrue(r.next());
        assertEquals("自动操作", r.getString(1));
        assertEquals("disabled", r.getString(2));
        assertEquals("new", r.getString(3));
      }
      s.executeUpdate(
          "UPDATE admin_menu SET menu_name='测试维护值' WHERE id='d01c25de0fe44841babaf183bf3ff8b8'");
      try {
        assertEquals(0, flyway.migrate().migrationsExecuted);
        try (ResultSet r =
            s.executeQuery(
                "SELECT menu_name FROM admin_menu WHERE id='d01c25de0fe44841babaf183bf3ff8b8'")) {
          r.next();
          assertEquals("测试维护值", r.getString(1));
        }
      } finally {
        s.executeUpdate(
            "UPDATE admin_menu SET menu_name='菜单管理' WHERE id='d01c25de0fe44841babaf183bf3ff8b8'");
      }
      System.out.println("DB-01 seed repeat preserves maintained value PASS");
      System.out.println(
          "DB-01/02 structure PASS: MySQL 8.4; 19 tables / 272 columns / 32 RESTRICT FKs; repeat"
              + " migrate=0");
    }
  }
}
