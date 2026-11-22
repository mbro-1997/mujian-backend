package com.mujian.backend.compatibility;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.MujianBackendApplication;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import org.springframework.session.data.redis.RedisIndexedSessionRepository.RedisSession;

/** 只接收明确命名的可销毁兼容库；不允许将开发库作为试迁移目标。 */
@EnabledIfEnvironmentVariable(named = "MJ_COMPAT_CONFIG", matches = ".+")
class FrameworkCompatibilityTest {
  @TempDir Path migrations;

  /** 验证 MySQL 迁移、Spring 装配和 Redis 往返。 */
  @Test
  void mysqlFlywayBootMyBatisAndRedisRoundTrip() throws Exception {
    Path configPath = Path.of(System.getenv("MJ_COMPAT_CONFIG")).toAbsolutePath();
    Properties config = new Properties();
    try (InputStream input = Files.newInputStream(configPath)) {
      config.load(input);
    }
    String url = config.getProperty("spring.datasource.url");
    assertTrue(
        url.startsWith("jdbc:mysql://127.0.0.1:3306/darkmujian_d1_compat_"), "兼容测试只允许本机命名的可销毁测试库");
    String user = config.getProperty("spring.datasource.username");
    String password = config.getProperty("spring.datasource.password");
    try (Connection connection = DriverManager.getConnection(url, user, password);
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery("SELECT VERSION(), DATABASE()")) {
      assertTrue(result.next());
      assertTrue(result.getString(1).startsWith("8.4."));
      assertTrue(result.getString(2).startsWith("darkmujian_d1_compat_"));
      System.out.println("COMPAT MySQL=" + result.getString(1) + "; isolated database verified");
    }
    Files.writeString(
        migrations.resolve("V1__compatibility_probe.sql"),
        "CREATE TABLE d1_compatibility_probe (id INT PRIMARY KEY);");
    Files.writeString(
        migrations.resolve("V2__remove_compatibility_probe.sql"),
        "DROP TABLE d1_compatibility_probe;");
    Flyway flyway =
        Flyway.configure()
            .dataSource(url, user, password)
            .locations("filesystem:" + migrations)
            .cleanDisabled(true)
            .load();
    flyway.migrate();
    assertEquals(0, flyway.migrate().migrationsExecuted);
    SpringApplication app = new SpringApplication(MujianBackendApplication.class);
    try (ConfigurableApplicationContext context =
        app.run(
            "--spring.config.additional-location=" + configPath.toUri(),
            "--server.port=0",
            "--spring.data.redis.port=6379",
            "--mj.environment=test",
            "--spring.profiles.active=compatibility")) {
      assertNotNull(context.getBean(SqlSessionFactory.class));
      RedisIndexedSessionRepository repository = context.getBean(RedisIndexedSessionRepository.class);
      RedisSession session = repository.createSession();
      session.setAttribute("compatibilityMarker", "d1-framework-only");
      repository.save(session);
      RedisSession saved = repository.findById(session.getId());
      assertNotNull(saved);
      assertEquals("d1-framework-only", saved.getAttribute("compatibilityMarker"));
      repository.deleteById(session.getId());
      assertNull(repository.findById(session.getId()));
      System.out.println(
          "COMPAT Boot/MyBatis startup; real Redis indexed session save/read/delete PASS");
    }
  }
}
