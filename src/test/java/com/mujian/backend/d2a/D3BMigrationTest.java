package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.support.TestDatabase;
import java.sql.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** 只允许既有显式隔离库，验证V13/V14升级和关键增量。 */
class D3BMigrationTest {
  /** 在隔离数据库执行升级。 */
  @Test
  void upgradeIsolatedDatabase() throws Exception {
    Properties properties = TestDatabase.prepare();
    try (Connection connection =
            DriverManager.getConnection(
                properties.getProperty("spring.datasource.url"),
                properties.getProperty("spring.datasource.username"),
                properties.getProperty("spring.datasource.password"));
        Statement statement = connection.createStatement()) {
      try (ResultSet resultSet =
          statement.executeQuery(
              "SELECT COUNT(*) FROM flyway_schema_history WHERE version='13' AND success=1")) {
        assertTrue(resultSet.next());
        assertEquals(1, resultSet.getInt(1));
      }

      try (ResultSet result =
          statement.executeQuery(
              "SELECT COUNT(*) FROM flyway_schema_history WHERE version='14' AND success=1")) {
        assertTrue(result.next());
        assertEquals(1, result.getInt(1));
      }
      try (ResultSet result =
          statement.executeQuery(
              "SELECT COUNT(*) FROM platform_rule_document WHERE"
                  + " rule_code='rental_order_cancellation_refund_rule' AND rule_status='active'"
                  + " AND is_required=0 AND force_reaccept=0")) {
        assertTrue(result.next());
        assertEquals(1, result.getInt(1));
      }
      try (ResultSet resultSet =
          statement.executeQuery(
              "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND"
                  + " TABLE_NAME IN"
                  + " ('checkout_batch','rental_order','payment_order','refund_order','user_coupon','transport_vehicle_tier','inventory_reservation_history')")) {
        assertTrue(resultSet.next());
        assertEquals(7, resultSet.getInt(1));
      }
      try (ResultSet resultSet =
          statement.executeQuery(
              "SELECT IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND"
                  + " TABLE_NAME='prop_sku' AND COLUMN_NAME='packaging_factor_bps'")) {
        assertTrue(resultSet.next());
        assertEquals("YES", resultSet.getString(1));
      }
    }
  }
}
