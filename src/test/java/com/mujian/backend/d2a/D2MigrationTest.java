package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.*;
import java.sql.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.ArrayList;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Uses only the two explicitly allocated test databases; never clean or drop. */
class D2MigrationTest {
  /** 验证 D1 升级保留数据及校验和。 */
  @Test
  void d1UpgradeRetainsDataAndChecksums() throws Exception {
    Properties p = new Properties();
    try (InputStream in =
        Files.newInputStream(Path.of(Objects.requireNonNull(System.getenv("MJ_COMPAT_CONFIG"))))) {
      p.load(in);
    }
    String url = p.getProperty("spring.datasource.url");
    assertTrue(
        url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:3306/darkmujian_d1_compat_[a-zA-Z0-9_]+(?:\\?.*)?"));
    FluentConfiguration configuration =
        Flyway.configure()
            .dataSource(
                url,
                p.getProperty("spring.datasource.username"),
                p.getProperty("spring.datasource.password"))
            .cleanDisabled(true);
    try (Connection c =
            DriverManager.getConnection(
                url,
                p.getProperty("spring.datasource.username"),
                p.getProperty("spring.datasource.password"));
        Statement s = c.createStatement()) {
      boolean empty;
      try (ResultSet r =
          s.executeQuery(
              "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()")) {
        r.next();
        empty = r.getInt(1) == 0;
      }
      if (empty) configuration.target("3").load().migrate();
      ArrayList<String> before = new ArrayList<String>();
      try (ResultSet r =
          s.executeQuery(
              "SELECT version,checksum FROM flyway_schema_history WHERE version IN ('1','2','3')"
                  + " ORDER BY version")) {
        while (r.next()) before.add(r.getString(1) + ":" + r.getInt(2));
      }
      assertEquals(3, before.size());
      s.executeUpdate(
          "UPDATE admin_menu SET menu_name='D2升级保留标记' WHERE id='d01c25de0fe44841babaf183bf3ff8b8'");
      Flyway full = configuration.target("latest").load();
      MigrateResult migrated = full.migrate();
      if (empty) assertEquals(3, migrated.migrationsExecuted);
      assertEquals(0, full.migrate().migrationsExecuted);
      full.validate();
      ArrayList<String> after = new ArrayList<String>();
      try (ResultSet r =
          s.executeQuery(
              "SELECT version,checksum FROM flyway_schema_history WHERE version IN ('1','2','3')"
                  + " ORDER BY version")) {
        while (r.next()) after.add(r.getString(1) + ":" + r.getInt(2));
      }
      assertEquals(before, after);
      try (ResultSet r =
          s.executeQuery(
              "SELECT menu_name FROM admin_menu WHERE id='d01c25de0fe44841babaf183bf3ff8b8'")) {
        r.next();
        assertEquals("D2升级保留标记", r.getString(1));
      }
      System.out.println(
          "D2 MIGRATION: "
              + (empty ? "fresh D1 -> D2 upgrade" : "repeat upgrade")
              + "; D1 checksums/data retained; repeat=0");
    }
  }

  /** 对照冻结字典检查字段及数据库约束。 */
  @Test
  void all212ColumnsAndConstraintsMatchFrozenDictionary() throws Exception {
    Properties p = com.mujian.backend.support.TestDatabase.prepare();
    ObjectMapper json = new ObjectMapper();
    JsonNode dict = json.readTree(getClass().getResourceAsStream("/contracts/d2a.dictionary.json"));
    int columns = 0, fks = 0, checks = 0;
    try (Connection c =
        DriverManager.getConnection(
            p.getProperty("spring.datasource.url"),
            p.getProperty("spring.datasource.username"),
            p.getProperty("spring.datasource.password"))) {
      for (JsonNode table : dict.path("tables")) {
        String name = table.path("table").asString();
        ArrayList<String> actual = new ArrayList<String>();
        try (PreparedStatement s =
            c.prepareStatement(
                "SELECT COLUMN_NAME,IS_NULLABLE,EXTRA FROM information_schema.COLUMNS WHERE"
                    + " TABLE_SCHEMA=DATABASE() AND TABLE_NAME=? ORDER BY ORDINAL_POSITION")) {
          s.setString(1, name);
          try (ResultSet r = s.executeQuery()) {
            while (r.next()) {
              JsonNode spec = table.path("columns").get(actual.size());
              assertEquals(spec.path("name").asString(), r.getString(1), name);
              assertEquals(
                  spec.path("nullable").asBoolean() ? "YES" : "NO",
                  r.getString(2),
                  name + "." + r.getString(1));
              assertEquals(!spec.path("generated").isNull(), r.getString(3).contains("GENERATED"));
              actual.add(r.getString(1));
            }
          }
        }
        assertEquals(table.path("columns").size(), actual.size(), name);
        columns += actual.size();
        for (String kind : List.of("CHECK", "FOREIGN KEY")) {
          try (PreparedStatement s =
              c.prepareStatement(
                  "SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE"
                      + " CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME=? AND CONSTRAINT_TYPE=?")) {
            s.setString(1, name);
            s.setString(2, kind);
            try (ResultSet r = s.executeQuery()) {
              r.next();
              int n = table.path(kind.equals("CHECK") ? "checks" : "foreignKeys").size();
              assertEquals(n, r.getInt(1), name + kind);
              if (kind.equals("CHECK")) checks += n;
              else fks += n;
            }
          }
        }
      }
    }
    assertEquals(212, columns);
    assertEquals(22, fks);
    System.out.println(
        "D2 DICTIONARY: 12 tables, "
            + columns
            + " columns, "
            + fks
            + " FKs, "
            + checks
            + " CHECK definitions matched");
  }
}
