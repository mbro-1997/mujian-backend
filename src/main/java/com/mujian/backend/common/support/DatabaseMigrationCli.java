package com.mujian.backend.common.support;

import java.io.InputStream;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import org.flywaydb.core.Flyway;

/** 显式迁移入口，凭据仅来自本机配置文件；运行进程不持有 DDL 权限。 */
public final class DatabaseMigrationCli {
  /** 从命令行参数启动本工具。 */
  public static void main(String[] args) throws Exception {
    if (args.length != 1) throw new IllegalArgumentException("需要本机迁移配置路径");
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(Path.of(args[0]))) {
      p.load(in);
    }
    String url = p.getProperty("spring.datasource.url");
    String user = p.getProperty("spring.datasource.username");
    String password = p.getProperty("spring.datasource.password");
    try (Connection c = DriverManager.getConnection(url, user, password);
        Statement s = c.createStatement();
        ResultSet r = s.executeQuery("SELECT VERSION(), DATABASE()")) {
      r.next();
      if (!r.getString(1).startsWith("8.4.")
          || !("darkmujian".equals(r.getString(2))
              || r.getString(2).startsWith("darkmujian_d1_test_")))
        throw new IllegalStateException("版本或目标库不符合 D1");
    }
    Flyway f =
        Flyway.configure()
            .dataSource(url, user, password)
            .cleanDisabled(true)
            .locations("classpath:db/migration")
            .load();
    f.migrate();
    f.validate();
    System.out.println("D1 migration validated");
  }
}
