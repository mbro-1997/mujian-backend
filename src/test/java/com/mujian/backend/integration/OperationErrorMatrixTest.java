package com.mujian.backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.MujianBackendApplication;
import com.mujian.backend.application.service.admin.AdminInitializationService;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.common.security.SessionService.Handle;
import com.mujian.backend.common.support.*;
import com.mujian.backend.integration.ServiceFailureIntegrationTest.Proxy;
import com.mujian.backend.support.TestDatabase;
import java.net.URI;
import java.net.http.*;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@EnabledIfEnvironmentVariable(named = "MJ_TEST_CONFIG", matches = ".+")
class OperationErrorMatrixTest {
  static final List<String> PATHS =
      List.of(
          "/api/admin/auth/csrf",
          "/api/admin/auth/login",
          "/api/admin/me",
          "/api/admin/menus",
          "/api/admin/system/menus",
          "/api/admin/auth/logout",
          "/api/me/context",
          "/api/auth/logout");
  static final List<String> ADMIN_READ =
      List.of("/api/admin/me", "/api/admin/menus", "/api/admin/system/menus");
  final HttpClient client = HttpClient.newHttpClient();
  final List<SessionService.Handle> handles = new ArrayList<>();
  final Map<String, String> csrfTokens = new HashMap<>();
  final Map<String,Map<String,String>> challenges=new HashMap<>();
  StringRedisTemplate fixtureRedis;
  SessionService sessions;
  ObjectMapper json;
  String base, username, password, user, mini;

  /** 定位待测试的方法或请求方式。 */
  String method(String path) {
    return path.endsWith("/login") || path.endsWith("/logout") ? "POST" : "GET";
  }

  /** 处理测试收到的请求。 */
  SessionService.Handle handle(String path) throws Exception {
    Handle handle =
        sessions.create(
            path.endsWith("/auth/login")?null:path.startsWith("/api/admin/") ? user : mini,
            path.startsWith("/api/admin/") ? "admin" : "miniapp");
    handles.add(handle);
    if (path.startsWith("/api/admin/")) {
      HttpResponse<String> response =
          client.send(
              HttpRequest.newBuilder(URI.create(base + "/api/admin/auth/csrf"))
                  .header("Cookie", sessions.cookieName() + "=" + handle.session().getId())
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode());
      csrfTokens.put(
          handle.session().getId(),
          json.readTree(response.body()).path("data").path("csrfToken").asString());
    }
    if(path.endsWith("/auth/login")) {
      HttpResponse<String> response=client.send(HttpRequest.newBuilder(URI.create(base+"/api/admin/auth/captcha")).header("Cookie",sessions.cookieName()+"="+handle.session().getId()).GET().build(),HttpResponse.BodyHandlers.ofString());
      assertEquals(200,response.statusCode());
      String answer=fixtureRedis.opsForValue().get("mj:test:admin-captcha:"+handle.session().getId());
      challenges.put(handle.session().getId(),Map.of("captchaId",json.readTree(response.body()).at("/data/captchaId").asString(),"captchaAnswer",answer.substring(33)));
    }
    return handle;
  }

  /** 执行请求并检查预期响应。 */
  void call(String path, SessionService.Handle handle, String trigger, int status, String reason)
      throws Exception {
    call(path, method(path), handle, trigger, status, reason, false);
  }

  /** 执行请求并检查预期响应。 */
  void call(
      String path,
      String method,
      SessionService.Handle handle,
      String trigger,
      int status,
      String reason,
      boolean badCsrf)
      throws Exception {
    Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(12));
    if (handle != null) {
      if (path.startsWith("/api/admin/"))
        b.header("Cookie", sessions.cookieName() + "=" + handle.session().getId());
      else b.header("Authorization", "Bearer " + handle.session().getId());
    }
    if (handle != null && path.startsWith("/api/admin/"))
      b.header(
          "X-CSRF-Token",
          badCsrf
              ? "invalid"
              : csrfTokens.getOrDefault(
                  handle.session().getId(), "not-needed-before-client-type-check"));
    HashMap<String,String> loginFields=new HashMap<String,String>(handle==null?Map.of():challenges.getOrDefault(handle.session().getId(),Map.of()));loginFields.putIfAbsent("captchaId",Ids.next());loginFields.putIfAbsent("captchaAnswer","wrong");loginFields.put("username",username);loginFields.put("password",password);
    String body =
        path.split("\\?", 2)[0].endsWith("/login")
            ? json.writeValueAsString(loginFields)
            : null;
    if (body != null) b.header("Content-Type", "application/json");
    b.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    HttpResponse<String> response = client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    JsonNode node = json.readTree(response.body());
    assertEquals(
        status,
        response.statusCode(),
        () -> path + " " + trigger + " " + node.path("reasonCode").asString());
    if (status != 200) assertEquals(reason, node.path("reasonCode").asString());
    assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
    assertEquals(
        node.path("traceId").asString(), response.headers().firstValue("X-Request-Id").orElse(""));
    if (status != 200) assertTrue(node.path("data").isNull());
    if (path.endsWith("/csrf") && status == 200)
      ((ObjectNode) node.path("data")).put("csrfToken", "REDACTED");
    if (System.getenv("MJ_EVIDENCE_DIR") != null)
      Files.writeString(
          Path.of(System.getenv("MJ_EVIDENCE_DIR"), "api-responses.jsonl"),
          json.writeValueAsString(
                  Map.of(
                      "method",
                      method,
                      "path",
                      path.split("\\?", 2)[0],
                      "trigger",
                      trigger,
                      "status",
                      status,
                      "body",
                      node))
              + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
  }

  /** 通过实际 HTTP 和业务状态验证可达错误。 */
  @Test
  void everyReachableOperationErrorUsesRealHttpAndState() throws Exception {
    Properties properties = TestDatabase.prepare();
    username = "matrix." + Ids.next();
    password = UUID.randomUUID().toString();
    int mysqlPort=URI.create(properties.getProperty("spring.datasource.url").substring(5)).getPort();
    try (Proxy mysql = new ServiceFailureIntegrationTest.Proxy(mysqlPort);
        Proxy redisProxy = new ServiceFailureIntegrationTest.Proxy(Integer.parseInt(properties.getProperty("spring.data.redis.port","6379")))) {
      String url =
          properties.getProperty("spring.datasource.url").replace(":"+mysqlPort+"/", ":" + mysql.port + "/")
              + "&connectTimeout=1000&socketTimeout=1000";
      try (ConfigurableApplicationContext context =
          new SpringApplication(MujianBackendApplication.class)
              .run(
                  "--spring.config.additional-location="
                      + Path.of(System.getenv("MJ_TEST_CONFIG")).toUri(),
                  "--spring.datasource.url=" + url,
                  "--spring.data.redis.port=" + redisProxy.port,
                  "--spring.data.redis.timeout=1s",
                  "--spring.data.redis.connect-timeout=1s",
                  "--spring.datasource.hikari.connection-timeout=1000",
                  "--spring.datasource.hikari.minimum-idle=0",
                  "--server.port=0",
                  "--mj.environment=test")) {
        sessions = context.getBean(SessionService.class);
        json = context.getBean(ObjectMapper.class);
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        StringRedisTemplate redis = context.getBean(StringRedisTemplate.class); fixtureRedis=redis;
        base =
            "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
        context.getBean(AdminInitializationService.class).initialize(username, password);
        user =
            jdbc.queryForObject(
                "SELECT user_id FROM admin_account WHERE username=?", String.class, username);
        mini = Ids.next();
        jdbc.update(
            "INSERT INTO sys_user(id,nickname,user_status,register_status,created_at,updated_at)"
                + " VALUES(?,'矩阵测试','active','new',NOW(3),NOW(3))",
            mini);
        try {
          for (String path : PATHS) {
            call(path + "?unknown=1", method(path), null, "未知查询参数", 400, "REQUEST_INVALID", false);
            call(
                path,
                path.equals("/api/admin/system/menus")?"DELETE":method(path).equals("GET") ? "POST" : "GET",
                null,
                "错误方法",
                405,
                "METHOD_NOT_ALLOWED",
                false);
            Handle cross = sessions.create(user, path.startsWith("/api/admin/") ? "miniapp" : "admin");
            handles.add(cross);
            call(path, cross, "反向客户端凭据", 403, "CLIENT_TYPE_MISMATCH");
            Handle broken = handle(path);
            redis
                .opsForHash()
                .put(
                    "mujian:test:session:sessions:" + broken.session().getId(),
                    "sessionAttr:clientType",
                    "1");
            call(path, broken, "真实Redis会话属性类型损坏", 500, "UNEXPECTED_ERROR");
          }
          for (String path :
              List.of(
                  "/api/admin/me",
                  "/api/admin/menus",
                  "/api/admin/system/menus",
                  "/api/admin/auth/logout",
                  "/api/me/context")) call(path, null, "无凭据", 401, "LOGIN_REQUIRED");
          call("/api/admin/auth/login", null, "无匿名CSRF会话", 403, "CSRF_INVALID");
          call(
              "/api/admin/auth/logout",
              "POST",
              handle("/api/admin/auth/logout"),
              "错误CSRF",
              403,
              "CSRF_INVALID",
              true);
          ArrayList<String> accessPaths = new ArrayList<>(ADMIN_READ);
          accessPaths.add("/api/admin/auth/login");
          for (String path : accessPaths) {
            jdbc.update("UPDATE admin_account SET enabled=0 WHERE username=?", username);
            try {
              call(path, handle(path), "管理员停用", 403, "ACCOUNT_ACTION_RESTRICTED");
            } finally {
              jdbc.update("UPDATE admin_account SET enabled=1 WHERE username=?", username);
            }
            jdbc.update(
                "UPDATE admin_role SET enabled=0 WHERE id=?",
                SystemIdentity.PLATFORM_ADMIN_ROLE_ID);
            try {
              call(path, handle(path), "角色停用", 403, "PERMISSION_DENIED");
            } finally {
              jdbc.update(
                  "UPDATE admin_role SET enabled=1 WHERE id=?",
                  SystemIdentity.PLATFORM_ADMIN_ROLE_ID);
            }
            jdbc.update(
                "UPDATE admin_menu SET component_path='review/Unknown' WHERE id=?",
                SystemIdentity.MENU_LIST_ID);
            try {
              call(path, handle(path), "数据库菜单未注册", 500, "ACCOUNT_CONTEXT_INCONSISTENT");
            } finally {
              jdbc.update(
                  "UPDATE admin_menu SET component_path='system/MenuReadOnly' WHERE id=?",
                  SystemIdentity.MENU_LIST_ID);
            }
          }
          jdbc.update("UPDATE sys_user SET user_status='locked' WHERE id=?", mini);
          try {
            call(
                "/api/me/context",
                handle("/api/me/context"),
                "账号locked",
                403,
                "ACCOUNT_ACTION_RESTRICTED");
          } finally {
            jdbc.update("UPDATE sys_user SET user_status='active' WHERE id=?", mini);
          }
          jdbc.update("UPDATE sys_user SET register_status='factory_bound' WHERE id=?", mini);
          try {
            call(
                "/api/me/context",
                handle("/api/me/context"),
                "阶段与角色不一致",
                500,
                "ACCOUNT_CONTEXT_INCONSISTENT");
          } finally {
            jdbc.update("UPDATE sys_user SET register_status='new' WHERE id=?", mini);
          }
          LinkedHashMap<String, Handle> prepared = new LinkedHashMap<String, SessionService.Handle>();
          for (String path : PATHS) prepared.put(path, handle(path));
          mysql.cut();
          try {
            for (String path :
                List.of(
                    "/api/admin/auth/login",
                    "/api/admin/me",
                    "/api/admin/menus",
                    "/api/admin/system/menus",
                    "/api/me/context"))
              call(path, prepared.get(path), "真实MySQL TCP断开", 503, "DATABASE_UNAVAILABLE");
          } finally {
            mysql.start();
          }
          redisProxy.cut();
          try {
            for (String path : PATHS)
              call(path, prepared.get(path), "真实Redis TCP断开", 503, "SESSION_STORE_UNAVAILABLE");
          } finally {
            redisProxy.start();
          }
          // Lettuce 断线重连采用退避，恢复验收等待真实连接可用，不假设端口重开即连接完成。
          org.awaitility.Awaitility.await()
              .atMost(Duration.ofSeconds(25))
              .ignoreExceptions()
              .until(() -> sessions.load(prepared.get("/api/admin/me").session().getId()) != null);
          // 连接恢复后使用原有凭据重试只读请求，拒绝客户端身份回退。
          call("/api/admin/me", prepared.get("/api/admin/me"), "MySQL/Redis恢复", 200, "OK");
          call("/api/me/context", prepared.get("/api/me/context"), "MySQL/Redis恢复", 200, "OK");
        } finally {
          for (Handle handle : handles) sessions.revoke(handle.session().getId());
          TestDatabase.removeAdmin(jdbc, username);
          jdbc.update("DELETE FROM sys_user WHERE id=?", mini);
        }
        System.out.println(
            "ERROR MATRIX PASS: per-operation real HTTP invalid/method/client/500;"
                + " authorization/state errors; all reachable DB/Redis disconnects and recovery");
      }
    }
  }
}
