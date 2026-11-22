package com.mujian.backend.support;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

/** 每个测试套件自行迁移，不依赖 Maven 类执行顺序或手工管理员。 */
public final class TestDatabase {
  private TestDatabase() {}

  /** 准备本用例的前置条件。 */
  public static Properties prepare() throws Exception {
    String config = System.getenv("MJ_TEST_CONFIG");
    if (config == null || config.isBlank())
      throw new IllegalStateException("NOT RUN: 未配置 MJ_TEST_CONFIG");
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(Path.of(config))) {
      properties.load(input);
    }
    String url = properties.getProperty("spring.datasource.url", "");
    if (!url.matches("jdbc:mysql://127\\.0\\.0\\.1:3306/darkmujian_d1_test_[a-zA-Z0-9_]+(?:\\?.*)?")
        && !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:(?:3330[789]|33317)/darkmujian_d1_test_d4(?:_v163)?(?:\\?.*)?")) {
      throw new IllegalArgumentException("集成测试只允许本机隔离库");
    }
    Flyway flyway =
        Flyway.configure()
            .dataSource(
                url,
                properties.getProperty("spring.datasource.username"),
                properties.getProperty("spring.datasource.password"))
            .target(properties.getProperty("spring.flyway.target", "latest"))
            .cleanDisabled(true)
            .load();
    flyway.migrate();
    flyway.validate();
    return properties;
  }

  /** 移除测试管理员授权。 */
  public static void removeAdmin(JdbcTemplate jdbc, String username) {
    List<String> ids =
        jdbc.queryForList(
            "SELECT user_id FROM admin_account WHERE username=?", String.class, username);
    for (String user : ids) {
      jdbc.update(
          "DELETE ar FROM admin_account_role ar JOIN admin_account a ON a.id=ar.admin_account_id"
              + " WHERE a.user_id=?",
          user);
      jdbc.update("DELETE FROM admin_account WHERE user_id=?", user);
      jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", user);
      jdbc.update("DELETE FROM sys_user WHERE id=?", user);
    }
  }
}
