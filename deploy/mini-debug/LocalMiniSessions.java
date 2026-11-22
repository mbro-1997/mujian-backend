package devtools;

import com.mujian.backend.common.config.SessionConfiguration;
import com.mujian.backend.common.security.SessionService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Map;
import java.util.HexFormat;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.security.D2RateLimiter;
import com.mujian.backend.infrastructure.wechat.OfficialWechatGateway;
import tools.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/** 本机开发工具进程。仅加载 JDBC 与正式会话配置，不启动业务控制器、迁移或业务定时任务。 */
@Configuration(proxyBeanMethods = false)
@Import({SessionConfiguration.class, SessionService.class, D2RateLimiter.class})
@ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
    DataRedisAutoConfiguration.class})
public class LocalMiniSessions {
  private static final Map<String,String> USERS = Map.of(
      "demander", "c98593fa57de414899a803765679bca6",
      "supplier", "4abdbdde15d34eebbe3b9cecfe9247a2");

  public static void main(String[] args) throws Exception {
    String pairing = Files.readString(Path.of(args[0])).trim();
    if (!pairing.matches("[0-9a-f]{64}")) throw new IllegalStateException("无效的本机配对密钥");
    var app = new SpringApplication(LocalMiniSessions.class);
    app.setWebApplicationType(WebApplicationType.NONE);
    app.addInitializers(c -> {
      var env = c.getEnvironment();
      if (!"development".equals(env.getProperty("mj.environment"))
          || !env.getRequiredProperty("spring.datasource.url").matches("jdbc:mysql://127\\.0\\.0\\.1:\\d+/darkmujian(?:\\?.*)?")
          || !"127.0.0.1".equals(env.getProperty("spring.data.redis.host"))
          || env.containsProperty("spring.data.redis.url"))
        throw new IllegalStateException("仅支持本机 darkmujian 开发环境");
    });
    var ctx = app.run("--spring.profiles.active=local", "--spring.main.banner-mode=off");
    var db = ctx.getBean(JdbcTemplate.class);
    var sessions = ctx.getBean(SessionService.class);
    InetAddress bind = InetAddress.getByName(args[1]);
    int port = Integer.parseInt(args[2]);
    boolean mobile = !bind.isLoopbackAddress();
    if (mobile && (!bind.isSiteLocalAddress() || NetworkInterface.getByInetAddress(bind) == null || args.length != 4))
      throw new IllegalArgumentException("手机调试要求本机局域网地址和开发者授权");
    var limiter = ctx.getBean(D2RateLimiter.class);
    var env = ctx.getEnvironment();
    String appId = env.getProperty("mj.wechat.app-id", "");
    var wechat = new OfficialWechatGateway(appId, env.getProperty("mj.wechat.app-secret", ""),
        new ObjectMapper(), limiter);
    String approved = mobile ? approvedDeveloper(Path.of(args[3]), wechat, appId) : "";
    String hostHeader = bind.getHostAddress() + ":" + port;
    var server = HttpServer.create(new InetSocketAddress(bind, port), 0);
    server.createContext("/", x -> {
      try (x) {
        String auth = x.getRequestHeaders().getFirst("Authorization");
        if (!hostHeader.equals(x.getRequestHeaders().getFirst("Host"))) { reply(x, 403, "{}"); return; }
        String path = x.getRequestURI().getPath();
        boolean localKey = auth != null && MessageDigest.isEqual(bytes("Bearer " + pairing), bytes(auth));
        if ("/ready".equals(path) && "GET".equals(x.getRequestMethod())) {
          reply(x, localKey ? 200 : 403, localKey ? "{\"ready\":true}" : "{}"); return;
        }
        String role = path.startsWith("/sessions/") ? path.substring(10) : "";
        String user = USERS.get(role);
        if (user == null || !"POST".equals(x.getRequestMethod())) { reply(x,404,"{}"); return; }
        try {
          if (mobile) {
            if (auth == null || !auth.matches("WeChat [A-Za-z0-9_-]{8,256}")) { reply(x,403,"{}"); return; }
            limiter.check("mini-debug-login", x.getRemoteAddress().getAddress().getHostAddress(), 30);
            String subject = fingerprint(appId, wechat.exchangeOpenId(auth.substring(7)));
            if (!MessageDigest.isEqual(bytes(approved), bytes(subject))) { reply(x,403,"{}"); return; }
          } else if (!localKey) { reply(x,403,"{}"); return; }
          String stage = role.equals("supplier") ? "factory_bound" : "demander_bound";
          Integer matches = db.queryForObject("""
              SELECT COUNT(*) FROM sys_user u JOIN sys_user_role r ON r.user_id=u.id AND r.enabled=1
              WHERE u.id=? AND u.register_status=? AND u.user_status='active' AND r.role_code=?
                AND NOT EXISTS (SELECT 1 FROM admin_account a WHERE a.user_id=u.id)
                AND (SELECT COUNT(*) FROM sys_user_role t WHERE t.user_id=u.id AND t.enabled=1)=1
                AND (?='demander' OR EXISTS (
                  SELECT 1 FROM factory_profile f JOIN factory_onboarding_audit a ON a.id=f.approved_audit_id
                  WHERE f.owner_user_id=u.id AND a.applicant_user_id=u.id AND f.factory_status='active'
                    AND a.audit_status='approved'))
              """,Integer.class,user,stage,role,role);
          if (!Integer.valueOf(1).equals(matches)) { reply(x,409,"{}"); return; }
          String token = sessions.create(user, "miniapp").session().getId();
          reply(x,200,"{\"token\":\"" + token + "\",\"userId\":\"" + user + "\"}");
        } catch (ApiFailure failure) { reply(x, failure.status == 400 ? 403 : failure.status, "{}"); }
        catch (RuntimeException e) { reply(x,503,"{}"); }
      }
    });
    server.start();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
  }
  private static String approvedDeveloper(Path file, OfficialWechatGateway wechat, String appId) throws Exception {
    if (!Files.exists(file)) {
      // 首次授权只从本机启动命令的管道取得开发工具 wx.login 码。
      String code = new java.io.BufferedReader(new java.io.InputStreamReader(System.in)).readLine();
      if (code == null || !code.matches("[A-Za-z0-9_-]{8,256}")) throw new IllegalArgumentException("缺少开发者登录码");
      Files.writeString(file, fingerprint(appId, wechat.exchangeOpenId(code)), java.nio.file.StandardOpenOption.CREATE_NEW);
    }
    String subject = Files.readString(file).trim();
    if (!subject.matches("[0-9a-f]{64}")) throw new IllegalStateException("开发者授权文件无效");
    return subject;
  }

  private static String fingerprint(String appId, String openId) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes(appId + ":" + openId)));
    } catch (java.security.NoSuchAlgorithmException impossible) { throw new AssertionError(impossible); }
  }
  private static byte[] bytes(String s) { return s.getBytes(StandardCharsets.UTF_8); }
  private static void reply(HttpExchange x,int status,String json) throws IOException {
    byte[] body = bytes(json);
    x.getResponseHeaders().set("Cache-Control","no-store");
    x.getResponseHeaders().set("Content-Type","application/json");
    x.sendResponseHeaders(status,body.length);
    x.getResponseBody().write(body);
  }
}
