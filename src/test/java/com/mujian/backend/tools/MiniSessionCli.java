package com.mujian.backend.tools;

import com.mujian.backend.MujianBackendApplication;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.common.security.SessionService.Handle;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.account.mapper.AccountMapper;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Properties;
import org.springframework.boot.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 只位于 test 源集，不进入生产 jar；不提供 HTTP 注入。 */
public final class MiniSessionCli {
  /** 启动本测试命令行入口。 */
  public static void main(String[] args) throws Exception {
    if (args.length != 2) throw new IllegalArgumentException("需要本机 dev/test 配置和凭据输出文件路径");
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(Path.of(args[0]))) {
      p.load(in);
    }
    if (!Set.of("development", "test").contains(p.getProperty("mj.environment"))
        || !p.getProperty("spring.datasource.url", "").startsWith("jdbc:mysql://127.0.0.1:"))
      throw new IllegalStateException("仅允许本机 dev/test");
    Path output = Path.of(args[1]);
    if (Files.exists(output)) throw new IllegalStateException("输出文件已存在，拒绝覆盖凭据");
    SpringApplication app = new SpringApplication(MujianBackendApplication.class);
    app.setWebApplicationType(WebApplicationType.NONE);
    try (ConfigurableApplicationContext c =
        app.run(
            "--spring.config.additional-location=" + Path.of(args[0]).toAbsolutePath().toUri(),
            "--spring.profiles.active=test-tools")) {
      AccountMapper users = c.getBean(AccountMapper.class);
      JdbcTemplate jdbc = c.getBean(JdbcTemplate.class);
      String id = Ids.next();
      new TransactionTemplate(c.getBean(PlatformTransactionManager.class))
          .executeWithoutResult(
              tx -> {
                LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
                users.insertAdminUser(id, "D1测试账号", now);
                users.insertUserRole(Ids.next(), id, "demander", now);
                jdbc.update("UPDATE sys_user SET register_status='demander_bound' WHERE id=?", id);
              });
      Handle session = c.getBean(SessionService.class).create(id, "miniapp");
      Files.createFile(
          output,
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      Files.writeString(
          output, "{\"userId\":\"" + id + "\",\"token\":\"" + session.session().getId() + "\"}");
      System.out.println("测试会话已保存到指定本机文件；未打印凭据。仅开发者工具本地注入。");
    }
  }
}
