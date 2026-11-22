package com.mujian.backend.common.support;

import com.mujian.backend.MujianBackendApplication;
import com.mujian.backend.application.service.admin.AdminInitializationService;
import java.io.Console;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Properties;
import org.springframework.boot.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** 独立 CLI，无 HTTP 注册入口；密码从 Console 或权限受限的外部配置读取。 */
public final class AdminInitializeCli {
  /** 从命令行参数启动本工具。 */
  public static void main(String[] args) throws Exception {
    if (args.length < 1 || args.length > 2)
      throw new IllegalArgumentException("需要运行配置路径，以及可选的初始化秘密文件路径");
    String username, password;
    if (args.length == 2) {
      Properties p = new Properties();
      try (InputStream in = Files.newInputStream(Path.of(args[1]))) {
        p.load(in);
      }
      username = p.getProperty("username");
      password = p.getProperty("password");
    } else {
      Console c = System.console();
      if (c == null) throw new IllegalStateException("需要安全终端或秘密文件");
      username = c.readLine("管理员用户名: ");
      char[] secret = c.readPassword("初始密码: ");
      password = new String(secret);
      java.util.Arrays.fill(secret, '\0');
    }
    SpringApplication app = new SpringApplication(MujianBackendApplication.class);
    app.setWebApplicationType(WebApplicationType.NONE);
    try (ConfigurableApplicationContext ctx =
        app.run(
            "--spring.config.additional-location=" + Path.of(args[0]).toAbsolutePath().toUri())) {
      System.out.println(
          ctx.getBean(AdminInitializationService.class).initialize(username, password));
    }
  }
}
