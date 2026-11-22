package com.mujian.backend.tools;

import com.mujian.backend.MujianBackendApplication;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.common.security.SessionService.Handle;
import com.mujian.backend.common.support.Ids;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import org.springframework.boot.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** 真实开发工具十二状态验收夹具，仅测试源集；凭据只写 0600 本机文件。 */
public final class MiniContextFixturesCli {
  /** 启动本测试命令行入口。 */
  public static void main(String[] args) throws Exception {
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(Path.of(args[0]))) {
      p.load(in);
    }
    if (!Set.of("development", "test").contains(p.getProperty("mj.environment"))
        || !p.getProperty("spring.datasource.url", "").startsWith("jdbc:mysql://127.0.0.1:"))
      throw new IllegalStateException("仅限本机 dev/test");
    Path output = Path.of(args[1]);
    if (Files.exists(output)) throw new IllegalStateException("拒绝覆盖凭据");
    SpringApplication app = new SpringApplication(MujianBackendApplication.class);
    app.setWebApplicationType(WebApplicationType.NONE);
    try (ConfigurableApplicationContext context =
        app.run(
            "--spring.config.additional-location=" + Path.of(args[0]).toAbsolutePath().toUri(),
            "--spring.profiles.active=test-tools")) {
      JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
      SessionService sessions = context.getBean(SessionService.class);
      ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
      for (String status : List.of("active", "disabled"))
        for (String stage :
            List.of(
                "new",
                "demander_bound",
                "factory_invite_locked",
                "factory_pending",
                "factory_rejected",
                "factory_bound")) {
          String id = Ids.next();
          jdbc.update(
              "INSERT INTO sys_user(id,nickname,user_status,register_status,created_at,updated_at)"
                  + " VALUES(?,'D1整改验收账号',?,?,NOW(3),NOW(3))",
              id,
              status,
              stage);
          String role =
              stage.equals("demander_bound")
                  ? "demander"
                  : stage.equals("factory_bound") ? "supplier" : null;
          if (role != null)
            jdbc.update(
                "INSERT INTO sys_user_role(id,user_id,role_code,enabled,created_at,updated_at)"
                    + " VALUES(?,?,?,1,NOW(3),NOW(3))",
                Ids.next(),
                id,
                role);
          Handle handle = sessions.create(id, "miniapp");
          list.add(
              Map.of("userId", id, "token", handle.session().getId(), "status", status, "stage", stage));
        }
      Files.createFile(
          output,
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      Files.writeString(
          output,
          context.getBean(tools.jackson.databind.ObjectMapper.class).writeValueAsString(list));
      System.out.println("12 个本机验收上下文已写入秘密文件；验收后注销会话并删除专属账号。");
    }
  }
}
