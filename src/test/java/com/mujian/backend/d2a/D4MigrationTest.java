package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

/** 独立迁移实例验证D3升级、A/B依赖及重复启动，不触碰共享数据库。 */
class D4MigrationTest {
  /** 先保留D3基线，再分别升级两批，验证费用外键延后建立。 */
  @Test
  void stagedUpgradeAndFreshInstall() throws Exception {
    for (String kind : new String[] {"integration", "compatibility"}) {
      Properties properties = new Properties();
      try (InputStream inputStream =
          Files.newInputStream(
              Path.of(System.getenv("MJ_TEST_CONFIG")).resolveSibling(kind + ".properties"))) {
        properties.load(inputStream);
      }
      String url = properties.getProperty("spring.datasource.url");
      assertTrue(
          url.matches(
              "jdbc:mysql://127\\.0\\.0\\.1:33308/darkmujian_d1_(test|compat)_d4(?:_v163)?\\?.*"));
      String username = properties.getProperty("spring.datasource.username");
      String password = properties.getProperty("spring.datasource.password");
      try (Connection connection = DriverManager.getConnection(url, username, password);
          Statement statement = connection.createStatement()) {
        try (ResultSet resultSet =
            statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")) {
          assertTrue(resultSet.next());
          assertEquals(0, resultSet.getInt(1), "仅允许新建隔离库；不清空既有数据");
        }
        D3BQuoteTest fixture = null;
        String historical = null;
        int paid = 0;
        if (kind.equals("integration")) {
          assertEquals("14", properties.getProperty("spring.flyway.target"));
          fixture = new D3BQuoteTest();
          fixture.start();
          historical = fixture.paidOrder();
          paid =
              fixture.f.db.queryForObject(
                  "SELECT total_paid_amount_fen FROM rental_order WHERE id=?",
                  Integer.class,
                  historical);
          assertTrue(paid > 0);
          assertEquals(
              0,
              fixture.f.db.queryForObject(
                  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND"
                      + " table_name='order_return_check'",
                  Integer.class));
          Flyway.configure()
              .dataSource(url, username, password)
              .cleanDisabled(true)
              .target("14")
              .load()
              .migrate();
          Flyway.configure()
              .dataSource(url, username, password)
              .cleanDisabled(true)
              .target("15.1")
              .load()
              .migrate();
          try (ResultSet resultSet =
              statement.executeQuery(
                  "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND"
                      + " table_name='order_transport_expense_statement'")) {
            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt(1));
          }
          try (ResultSet resultSet =
              statement.executeQuery(
                  "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE"
                      + " constraint_schema=DATABASE() AND constraint_name='d4_9_f4'")) {
            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt(1));
          }
        }
        Flyway flyway =
            Flyway.configure().dataSource(url, username, password).cleanDisabled(true).load();
        assertEquals(kind.equals("integration") ? 4 : 20, flyway.migrate().migrationsExecuted);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
        try (ResultSet resultSet =
            statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.referential_constraints WHERE"
                    + " constraint_schema=DATABASE() AND constraint_name='d4_9_f4'")) {
          assertTrue(resultSet.next());
          assertEquals(1, resultSet.getInt(1));
        }
        try (ResultSet resultSet =
            statement.executeQuery(
                "SELECT COUNT(*) FROM information_schema.table_constraints WHERE"
                    + " constraint_schema=DATABASE() AND constraint_type='CHECK' AND"
                    + " constraint_name LIKE 'd4_%'")) {
          assertTrue(resultSet.next());
          assertTrue(resultSet.getInt(1) > 150);
        }
        if (fixture != null) {
          assertEquals(
              paid,
              fixture.f.db.queryForObject(
                  "SELECT total_paid_amount_fen FROM rental_order WHERE id=?",
                  Integer.class,
                  historical));
          assertEquals(
              1,
              fixture.f.db.queryForObject(
                  "SELECT COUNT(*) FROM payment_allocation WHERE rental_order_id=? AND"
                      + " total_allocated_amount_fen=?",
                  Integer.class,
                  historical,
                  paid));
          assertEquals(
              2,
              fixture.f.db.queryForObject(
                  "SELECT SUM(quantity) FROM inventory_reservation WHERE rental_order_id=? AND"
                      + " reservation_status='paid_locked'",
                  Integer.class,
                  historical));
          assertEquals(
              0,
              fixture.f.db.queryForObject(
                  "SELECT COUNT(*) FROM order_receipt_check WHERE rental_order_id=?",
                  Integer.class,
                  historical));
          fixture.stop();
        }
        assertThrows(
            SQLException.class,
            () ->
                statement.executeUpdate(
                    "INSERT INTO"
                        + " order_receipt_check(id,rental_order_id,outbound_transport_leg_id,buyer_user_id,check_status,created_at,updated_at)"
                        + " VALUES('10000000000040008000000000000001','10000000000040008000000000000002','10000000000040008000000000000003','10000000000040008000000000000004','pending',NOW(3),NOW(3))"));
      }
    }
  }
}
