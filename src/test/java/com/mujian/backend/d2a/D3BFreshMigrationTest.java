package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** 使用明确指定的空兼容库，拒绝清空或覆盖已有库。 */
class D3BFreshMigrationTest {
  /** 验证新建与升级均可直接执行。 */
  @Test
  void freshAndUpgradeWithoutCleaning() throws Exception {
    Properties p = new Properties();
    try (java.io.InputStream input =
        Files.newInputStream(
            Path.of(Objects.requireNonNull(System.getenv("MJ_D3_FRESH_CONFIG"))))) {
      p.load(input);
    }
    String url = p.getProperty("spring.datasource.url");
    assertTrue(
        url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:3306/darkmujian_d1_compat_[a-zA-Z0-9_]+(?:\\?.*)?"));
    try (Connection connection =
            DriverManager.getConnection(
                url,
                p.getProperty("spring.datasource.username"),
                p.getProperty("spring.datasource.password"));
        Statement statement = connection.createStatement()) {
      try (ResultSet result =
          statement.executeQuery(
              "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) {
        assertTrue(result.next());
        assertEquals(0, result.getInt(1), "fresh isolated database required; never clean");
      }
    }
    Flyway.configure()
        .dataSource(
            url,
            p.getProperty("spring.datasource.username"),
            p.getProperty("spring.datasource.password"))
        .cleanDisabled(true)
        .target("12")
        .load()
        .migrate();
    Flyway flyway =
        Flyway.configure()
            .dataSource(
                url,
                p.getProperty("spring.datasource.username"),
                p.getProperty("spring.datasource.password"))
            .cleanDisabled(true)
            .load();
    assertEquals(2, flyway.migrate().migrationsExecuted);
    assertEquals(0, flyway.migrate().migrationsExecuted);
    flyway.validate();
    try (Connection connection =
            DriverManager.getConnection(
                url,
                p.getProperty("spring.datasource.username"),
                p.getProperty("spring.datasource.password"));
        Statement statement = connection.createStatement()) {
      connection.setAutoCommit(false);
      statement.executeUpdate(
          "INSERT INTO"
              + " transport_vehicle_tier(id,tier_code,tier_name,cargo_length_mm,cargo_width_mm,cargo_height_mm,effective_volume_cm3,safe_payload_gram,sort_no,tier_status,created_at,updated_at)"
              + " VALUES('10000000000040008000000000000001','isolated','隔离车型',1,1,1,1,1,0,'enabled',NOW(3),NOW(3))");
      statement.executeUpdate(
          "INSERT INTO"
              + " transport_pricing_rule(id,rule_version,city_code,vehicle_tier_id,distance_min_meter,distance_max_meter,guide_min_amount_fen,guide_max_amount_fen,own_vehicle_settle_amount_fen,extra_km_amount_fen,rule_status,effective_at,created_at,updated_at,included_distance_meter)"
              + " VALUES('10000000000040008000000000000002','isolated','310100','10000000000040008000000000000001',0,NULL,1,1,1,NULL,'draft',NOW(3),NOW(3),NOW(3),10000)");
      assertThrows(
          SQLException.class,
          () ->
              statement.executeUpdate(
                  "UPDATE transport_pricing_rule SET extra_km_amount_fen=-1 WHERE"
                      + " id='10000000000040008000000000000002'"));
      assertThrows(
          SQLException.class,
          () ->
              statement.executeUpdate(
                  "DELETE FROM transport_vehicle_tier WHERE"
                      + " id='10000000000040008000000000000001'"));
      connection.rollback();
    }
  }
}
