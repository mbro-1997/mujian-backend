package com.mujian.backend.persistence;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.support.Ids;
import java.io.InputStream;
import java.nio.file.*;
import java.sql.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.time.LocalDateTime;
import java.util.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "MJ_TEST_CONFIG", matches = ".+")
class ConstraintIntegrationTest {
  String url, user, password;
  Map<String, LinkedHashMap<String, Object>> base = new LinkedHashMap<>();

  /** 发起本场景的业务申请。 */
  Connection open() throws Exception {
    return DriverManager.getConnection(url, user, password);
  }

  /** 取出供断言的数据库记录。 */
  LinkedHashMap<String, Object> row(Object... kv) {
    LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
    return m;
  }

  /** 插入隔离测试数据。 */
  void insert(Connection c, String table, Map<String, Object> row) throws SQLException {
    try (PreparedStatement s =
        c.prepareStatement(
            "INSERT INTO "
                + table
                + " ("
                + String.join(",", row.keySet())
                + ") VALUES ("
                + String.join(",", Collections.nCopies(row.size(), "?"))
                + ")")) {
      int i = 1;
      for (Object v : row.values()) s.setObject(i++, v);
      s.executeUpdate();
    }
  }

  /** 创建独立的测试副本。 */
  LinkedHashMap<String, Object> copy(String table) {
    LinkedHashMap<String, Object> m = new LinkedHashMap<>(base.get(table));
    m.put("id", Ids.next());
    return m;
  }

  /** 逐项检查非空、外键及唯一约束。 */
  @Test
  void everyCheckNullabilityForeignKeyAndUniqueness() throws Exception {
    com.mujian.backend.support.TestDatabase.prepare();
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(Path.of(System.getenv("MJ_TEST_CONFIG")))) {
      p.load(in);
    }
    url = p.getProperty("spring.datasource.url");
    user = p.getProperty("spring.datasource.username");
    password = p.getProperty("spring.datasource.password");
    assertTrue(url.contains("/darkmujian_d1_test_"));
    try (Connection c = open()) {
      c.setAutoCommit(false);
      LocalDateTime now = LocalDateTime.now();
      LinkedHashMap<String, Object> u =
          row(
              "id",
              Ids.next(),
              "nickname",
              "约束测试",
              "avatar_url",
              null,
              "phone",
              null,
              "phone_verified",
              0,
              "phone_source",
              null,
              "phone_verified_at",
              null,
              "user_status",
              "active",
              "register_status",
              "new",
              "created_at",
              now,
              "updated_at",
              now);
      base.put("sys_user", u);
      String user1 = Ids.next(), user2 = Ids.next(), user3 = Ids.next();
      for (String id : List.of(user1, user2, user3)) {
        LinkedHashMap<String, Object> x = copy("sys_user");
        x.put("id", id);
        insert(c, "sys_user", x);
      }
      base.put(
          "sys_user_role",
          row(
              "id",
              Ids.next(),
              "user_id",
              user1,
              "role_code",
              "admin",
              "enabled",
              0,
              "created_at",
              now,
              "updated_at",
              now));
      base.put(
          "admin_account",
          row(
              "id",
              Ids.next(),
              "user_id",
              user3,
              "username",
              "constraint." + Ids.next(),
              "password_hash",
              "$2a$12$" + "a".repeat(53),
              "enabled",
              0,
              "last_login_at",
              null,
              "created_at",
              now,
              "updated_at",
              now));
      LinkedHashMap<String, Object> parentAccount = copy("admin_account");
      parentAccount.put("user_id", user2);
      parentAccount.put("username", "parent." + Ids.next());
      insert(c, "admin_account", parentAccount);
      base.put(
          "admin_role",
          row(
              "id",
              Ids.next(),
              "role_code",
              "constraint_" + Ids.next(),
              "role_name",
              "约束角色",
              "role_sort",
              0,
              "built_in",
              0,
              "enabled",
              0,
              "remark",
              null,
              "created_by_admin_user_id",
              user1,
              "created_at",
              now,
              "updated_at",
              now));
      LinkedHashMap<String, Object> parentRole = copy("admin_role");
      parentRole.put("role_code", "parent_" + Ids.next());
      insert(c, "admin_role", parentRole);
      base.put(
          "admin_menu",
          row(
              "id",
              Ids.next(),
              "parent_id",
              null,
              "menu_name",
              "约束菜单",
              "menu_type",
              "menu",
              "permission_code",
              "admin:constraint:read",
              "route_path",
              "/system/menus",
              "component_path",
              "system/MenuReadOnly",
              "icon_name",
              null,
              "visible",
              0,
              "enabled",
              0,
              "sort_no",
              0,
              "created_at",
              now,
              "updated_at",
              now));
      LinkedHashMap<String, Object> parentMenu = copy("admin_menu");
      parentMenu.put("permission_code", "admin:parent:read");
      insert(c, "admin_menu", parentMenu);
      base.put(
          "admin_account_role",
          row(
              "id",
              Ids.next(),
              "admin_account_id",
              parentAccount.get("id"),
              "admin_role_id",
              parentRole.get("id"),
              "granted_by_admin_user_id",
              user1,
              "granted_at",
              now,
              "created_at",
              now));
      base.put(
          "admin_role_menu",
          row(
              "id",
              Ids.next(),
              "admin_role_id",
              parentRole.get("id"),
              "admin_menu_id",
              parentMenu.get("id"),
              "created_by_admin_user_id",
              user1,
              "created_at",
              now));
      int checks = 0, nulls = 0, fks = 0, unique = 0, restricts = 0;
      for (String table : base.keySet()) {
        Savepoint sp = c.setSavepoint();
        insert(c, table, copy(table));
        c.rollback(sp); // 先证明反例的基准行本身合法。
        ArrayList<String[]> constraints = new ArrayList<String[]>();
        try (PreparedStatement q =
            c.prepareStatement(
                "SELECT tc.CONSTRAINT_NAME,cc.CHECK_CLAUSE FROM"
                    + " information_schema.TABLE_CONSTRAINTS tc JOIN"
                    + " information_schema.CHECK_CONSTRAINTS cc ON"
                    + " cc.CONSTRAINT_SCHEMA=tc.CONSTRAINT_SCHEMA AND"
                    + " cc.CONSTRAINT_NAME=tc.CONSTRAINT_NAME WHERE tc.TABLE_SCHEMA=DATABASE() AND"
                    + " tc.TABLE_NAME=? AND tc.CONSTRAINT_TYPE='CHECK'")) {
          q.setString(1, table);
          try (ResultSet r = q.executeQuery()) {
            while (r.next()) constraints.add(new String[] {r.getString(1), r.getString(2)});
          }
        }
        for (String[] check : constraints) {
          LinkedHashMap<String, Object> bad = copy(table);
          String clause = check[1];
          Matcher match = java.util.regex.Pattern.compile("`([^`]+)`").matcher(clause);
          assertTrue(match.find());
          String col = match.group(1);
          if (clause.contains("phone_verified") && clause.contains("phone_source"))
            bad.put("phone_verified", 1);
          else if (clause.contains("menu_type") && clause.contains("permission_code")) {
            bad.put("menu_type", "button");
            bad.put("route_path", "javascript:bad");
          } else if (clause.contains("menu_type")
              && clause.contains("directory")
              && clause.contains("component_path")) {
            bad.put("menu_type", "directory");
          } else if (clause.contains("menu_type") && clause.contains("route_path")) {
            bad.put("route_path", "javascript:bad");
          } else if (clause.contains("parent_id") && clause.contains("<>"))
            bad.put("parent_id", bad.get("id"));
          else if (col.equals("id") || col.endsWith("_id")) bad.put(col, "not-a-uuid");
          else if (Set.of("enabled", "visible", "built_in", "phone_verified").contains(col))
            bad.put(col, 2);
          else if (Set.of("sort_no", "role_sort").contains(col)) bad.put(col, -1);
          else if (Set.of("nickname", "menu_name", "role_name").contains(col)) bad.put(col, "");
          else bad.put(col, "INVALID");
          sp = c.setSavepoint();
          SQLException ex = assertThrows(SQLException.class, () -> insert(c, table, bad), check[0]);
          assertEquals(3819, ex.getErrorCode(), check[0]);
          assertTrue(ex.getMessage().contains(check[0]), check[0]);
          c.rollback(sp);
          checks++;
        }
        try (PreparedStatement q =
            c.prepareStatement(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
                    + " AND TABLE_NAME=? AND IS_NULLABLE='NO' AND EXTRA NOT LIKE '%GENERATED%'")) {
          q.setString(1, table);
          try (ResultSet r = q.executeQuery()) {
            while (r.next()) {
              String col = r.getString(1);
              LinkedHashMap<String, Object> bad = copy(table);
              bad.put(col, null);
              sp = c.setSavepoint();
              assertEquals(
                  1048,
                  assertThrows(SQLException.class, () -> insert(c, table, bad)).getErrorCode());
              c.rollback(sp);
              nulls++;
            }
          }
        }
        try (PreparedStatement q =
            c.prepareStatement(
                "SELECT COLUMN_NAME,CONSTRAINT_NAME,REFERENCED_TABLE_NAME FROM"
                    + " information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND"
                    + " TABLE_NAME=? AND REFERENCED_TABLE_NAME IS NOT NULL")) {
          q.setString(1, table);
          try (ResultSet r = q.executeQuery()) {
            while (r.next()) {
              LinkedHashMap<String, Object> bad = copy(table);
              bad.put(r.getString(1), Ids.next());
              String name = r.getString(2);
              sp = c.setSavepoint();
              SQLException ex = assertThrows(SQLException.class, () -> insert(c, table, bad));
              assertEquals(1452, ex.getErrorCode());
              assertTrue(ex.getMessage().contains(name));
              c.rollback(sp);
              fks++;
              sp = c.setSavepoint();
              String parentTable = r.getString(3), fkColumn = r.getString(1);
              LinkedHashMap<String, Object> parent = copy(parentTable);
              insert(c, parentTable, parent);
              LinkedHashMap<String, Object> child = copy(table);
              child.put(fkColumn, parent.get("id"));
              if (parentTable.equals(table) && table.equals("admin_menu"))
                child.put("permission_code", "admin:child:read");
              insert(c, table, child);
              try (PreparedStatement delete = c.prepareStatement("DELETE FROM " + parentTable + " WHERE id=?")) {
                delete.setObject(1, parent.get("id"));
                SQLException denied = assertThrows(SQLException.class, () -> delete.executeUpdate());
                assertEquals(1451, denied.getErrorCode());
                assertTrue(denied.getMessage().contains(name));
              }
              c.rollback(sp);
              restricts++;
            }
          }
        }
        ArrayList<List<String>> indexes = new ArrayList<List<String>>();
        try (PreparedStatement q =
            c.prepareStatement(
                "SELECT GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) FROM"
                    + " information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND"
                    + " TABLE_NAME=? AND NON_UNIQUE=0 AND INDEX_NAME<>'PRIMARY' GROUP BY"
                    + " INDEX_NAME")) {
          q.setString(1, table);
          try (ResultSet r = q.executeQuery()) {
            while (r.next()) indexes.add(Arrays.asList(r.getString(1).split(",")));
          }
        }
        for (List<String> columns : indexes) {
          sp = c.setSavepoint();
          LinkedHashMap<String, Object> first = copy(table);
          if (columns.contains("enabled_user_guard")) first.put("enabled", 1);
          insert(c, table, first);
          LinkedHashMap<String, Object> duplicate = new LinkedHashMap<>(first);
          duplicate.put("id", Ids.next());
          if (table.equals("admin_account")) {
            if (columns.contains("username")) {
              duplicate.put("user_id", user1);
            } else duplicate.put("username", "other." + Ids.next());
          }
          if (columns.contains("enabled_user_guard")) duplicate.put("role_code", "demander");
          assertEquals(
              1062,
              assertThrows(SQLException.class, () -> insert(c, table, duplicate)).getErrorCode());
          c.rollback(sp);
          unique++;
        }
      }
      assertEquals(10, fks);
      assertEquals(10, restricts);
      assertEquals(8, unique);
      c.rollback();
      System.out.println(
          "DB counterexamples PASS: CHECK="
              + checks
              + ", NOT NULL="
              + nulls
              + ", orphan FK="
              + fks
              + ", referenced deletes="
              + restricts
              + ", UNIQUE="
              + unique
              + "; valid baseline verified before each family; all rolled back");
    }
  }
}
