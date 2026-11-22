package com.mujian.backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.MujianBackendApplication;
import com.mujian.backend.application.service.admin.AdminAccessService;
import com.mujian.backend.application.service.admin.AdminInitializationService;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.config.MenuRegistry;
import com.mujian.backend.common.security.*;
import com.mujian.backend.common.security.LoginRateLimiter;
import com.mujian.backend.common.security.LoginRateLimiter.Attempt;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.common.security.SessionService.Handle;
import com.mujian.backend.common.support.*;
import java.io.InputStream;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.*;
import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.MappedStatement;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.session.data.redis.RedisIndexedSessionRepository;
import tools.jackson.databind.*;
import tools.jackson.databind.JsonNode;

@EnabledIfEnvironmentVariable(named = "MJ_TEST_CONFIG", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiSessionIntegrationTest {
  ConfigurableApplicationContext context;
  JdbcTemplate jdbc;
  ObjectMapper json;
  SessionService sessions;
  String base, password, username = "d1.integration.admin";
  final Probe probe = new Probe();

  /** 创建本次测试客户端。 */
  HttpClient client() {
    return HttpClient.newBuilder()
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
        .build();
  }

  /** 初始化本组隔离依赖。 */
  @BeforeAll
  void start() throws Exception {
    com.mujian.backend.support.TestDatabase.prepare();
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(Path.of(System.getenv("MJ_TEST_CONFIG")))) {
      p.load(in);
    }
    assertTrue(p.getProperty("spring.datasource.url").contains("/darkmujian_d1_test_"));
    context =
        new SpringApplication(MujianBackendApplication.class)
            .run(
                "--spring.config.additional-location="
                    + Path.of(System.getenv("MJ_TEST_CONFIG")).toUri(),
                "--server.port=0",
                "--mj.prop.qr-worker-enabled=false",
                "--mj.environment=test");
    context
        .getBean(org.apache.ibatis.session.SqlSessionFactory.class)
        .getConfiguration()
        .addInterceptor(probe);
    jdbc = context.getBean(JdbcTemplate.class);
    json = context.getBean(ObjectMapper.class);
    sessions = context.getBean(SessionService.class);
    base = "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    // 测试账号只存在可销毁库，允许每次唯一名称，避免修改先前账号密码。
    username = "test." + Ids.next();
    password = UUID.randomUUID().toString();
    context.getBean(AdminInitializationService.class).initialize(username, password);
  }

  /** 释放本组创建的资源。 */
  @AfterAll
  void stop() {
    if (context != null) {
      try {
        com.mujian.backend.support.TestDatabase.removeAdmin(jdbc, username);
      } finally {
        context.close();
      }
    }
  }

  /** 发送请求并收集测试响应。 */
  JsonNode request(
      HttpClient c, String method, String path, String body, String csrf, String bearer, int status)
      throws Exception {
    Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(8));
    if (csrf != null) b.header("X-CSRF-Token", csrf);
    if (bearer != null) b.header("Authorization", "Bearer " + bearer);
    if (body != null) b.header("Content-Type", "application/json");
    b.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    HttpResponse<String> response = c.send(b.build(), HttpResponse.BodyHandlers.ofString());
    JsonNode node = json.readTree(response.body());
    assertEquals(
        status,
        response.statusCode(),
        () -> path + " reason=" + node.path("reasonCode").asString());
    assertEquals("no-store", response.headers().firstValue("cache-control").orElse(""));
    assertEquals(
        response.headers().firstValue("x-request-id").orElse(""), node.path("traceId").asString());
    assertTrue(
        node.path("traceId").asString().matches("[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}"));
    if (status == 429) assertEquals("900", response.headers().firstValue("Retry-After").orElse(""));
    if (status == 405) assertEquals("GET", response.headers().firstValue("Allow").orElse(""));
    if (status != 200) assertTrue(node.get("data").isNull());
    else assertEquals("OK", node.path("code").asString());
    if (System.getenv("MJ_EVIDENCE_DIR") != null) {
      JsonNode recorded = node.deepCopy();
      if (path.endsWith("/auth/csrf") && status == 200)
        ((tools.jackson.databind.node.ObjectNode) recorded.get("data"))
            .put("csrfToken", "REDACTED");
      Files.writeString(
          Path.of(System.getenv("MJ_EVIDENCE_DIR"), "api-responses.jsonl"),
          json.writeValueAsString(
                  Map.of(
                      "method",
                      method,
                      "path",
                      path.split("\\?", 2)[0],
                      "status",
                      status,
                      "body",
                      recorded))
              + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    }
    assertFalse(response.body().contains("passwordHash"));
    assertFalse(response.body().contains(password));
    return node;
  }

  /** 取得后台命令的防伪凭据。 */
  String csrf(HttpClient c) throws Exception {
    return request(c, "GET", "/api/admin/auth/csrf", null, null, null, 200)
        .path("data")
        .path("csrfToken")
        .asString();
  }

  /** 从当前隔离测试会话取到实际图码。测试答案不通过 HTTP 返回。 */
  String loginBody(HttpClient c,String name,String secret) throws Exception {
    JsonNode captcha=request(c,"GET","/api/admin/auth/captcha",null,null,null,200).path("data");
    String cookie=((CookieManager)c.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream().filter(x->x.getName().equals(sessions.cookieName())).findFirst().orElseThrow().getValue();
    String pair=context.getBean(StringRedisTemplate.class).opsForValue().get("mj:test:admin-captcha:"+cookie);
    assertNotNull(pair);
    return json.writeValueAsString(Map.of("username",name,"password",secret,"captchaId",captcha.get("captchaId").asString(),"captchaAnswer",pair.substring(33)));
  }

  /** 通过登录入口建立测试会话。 */
  String login(HttpClient c) throws Exception {
    String token = csrf(c);
    String old =
        ((CookieManager) c.cookieHandler().orElseThrow())
            .getCookieStore()
            .getCookies()
            .get(0)
            .getValue();
    request(
        c,
        "POST",
        "/api/admin/auth/login",
        loginBody(c,username,password),
        token,
        null,
        200);
    String fresh =
        ((CookieManager) c.cookieHandler().orElseThrow())
            .getCookieStore()
            .getCookies()
            .get(0)
            .getValue();
    assertNotEquals(old, fresh);
    assertNull(sessions.load(old));
    return csrf(c);
  }

  /** 验证真实 Cookie、CSRF、账号上下文及拒绝分支。 */
  @Test
  void eightOperationsRealCookieCsrfContextAndRejections() throws Exception {
    clearHttpBudget();
    HttpClient c = client();
    request(c, "GET", "/api/admin/me", null, null, null, 401);
    String token = csrf(c);
    request(
        c,
        "POST",
        "/api/admin/auth/login",
        loginBody(c,username,password),
        null,
        null,
        403);
    token = login(c);
    JsonNode me = request(c, "GET", "/api/admin/me", null, null, null, 200);
    String user = me.path("data").path("userId").asString();
    request(c, "GET", "/api/admin/menus", null, null, null, 200);
    request(c, "GET", "/api/admin/system/menus", null, null, null, 200);
    request(c, "GET", "/api/admin/me?unknown=1", null, null, null, 400);
    request(c, "GET", "/api/admin/me", "{}", null, null, 400);
    request(c, "POST", "/api/admin/me", null, null, null, 405);
    request(c, "GET", "/api/not-found", null, null, null, 404);
    request(c, "POST", "/api/admin/auth/login", "{bad", token, null, 400);
    request(
        c,
        "POST",
        "/api/admin/auth/login",
        "{\"username\":\"abc\",\"password\":\"a\",\"extra\":1}",
        token,
        null,
        400);
    request(c, "POST", "/api/admin/auth/login", "x".repeat(8193), token, null, 400);
    request(
        c,
        "POST",
        "/api/admin/auth/login",
        loginBody(c,username,"密".repeat(25)),
        token,
        null,
        400);
    HttpResponse<String> wrongType =
        c.send(
            HttpRequest.newBuilder(URI.create(base + "/api/admin/auth/login"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(400, wrongType.statusCode());
    HttpResponse<String> absentType =
        c.send(
            HttpRequest.newBuilder(URI.create(base + "/api/admin/auth/login"))
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(400, absentType.statusCode());
    request(c, "POST", "/api/admin/auth/logout", null, "wrong-csrf", null, 403);
    // visible 只影响导航，不撤销权限；菜单禁用下一请求立即生效。
    jdbc.update("UPDATE admin_menu SET visible=0 WHERE id=?", SystemIdentity.MENU_LIST_ID);
    try {
      request(c, "GET", "/api/admin/system/menus", null, null, null, 200);
    } finally {
      jdbc.update("UPDATE admin_menu SET visible=1 WHERE id=?", SystemIdentity.MENU_LIST_ID);
    }
    jdbc.update("UPDATE admin_menu SET enabled=0 WHERE id=?", SystemIdentity.SYSTEM_MENU_ID);
    try {
      request(c, "GET", "/api/admin/system/menus", null, null, null, 403);
    } finally {
      jdbc.update("UPDATE admin_menu SET enabled=1 WHERE id=?", SystemIdentity.SYSTEM_MENU_ID);
    }
    HttpClient disabledClient = client();
    login(disabledClient);
    jdbc.update("UPDATE admin_account SET enabled=0 WHERE username=?", username);
    try {
      request(disabledClient, "GET", "/api/admin/me", null, null, null, 403);
      request(disabledClient, "GET", "/api/admin/me", null, null, null, 401);
    } finally {
      jdbc.update("UPDATE admin_account SET enabled=1 WHERE username=?", username);
    }
    Handle cross = sessions.create(user, "miniapp");
    String cookieName = sessions.cookieName();
    HttpClient bad = HttpClient.newHttpClient();
    HttpResponse<String> raw =
        bad.send(
            HttpRequest.newBuilder(URI.create(base + "/api/admin/me"))
                .header("Cookie", cookieName + "=" + cross.session().getId())
                .GET()
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(403, raw.statusCode());
    assertEquals("CLIENT_TYPE_MISMATCH", json.readTree(raw.body()).path("reasonCode").asString());
    sessions.revoke(cross.session().getId());
    jdbc.update(
        "UPDATE admin_role SET enabled=0 WHERE id=?", SystemIdentity.PLATFORM_ADMIN_ROLE_ID);
    try {
      request(c, "GET", "/api/admin/me", null, null, null, 403);
      request(c, "POST", "/api/admin/auth/logout", null, token, null, 200);
    } finally {
      jdbc.update(
          "UPDATE admin_role SET enabled=1 WHERE id=?", SystemIdentity.PLATFORM_ADMIN_ROLE_ID);
    }
    request(c, "GET", "/api/admin/me", null, null, null, 401);
    String mini = Ids.next();
    jdbc.update(
        "INSERT INTO sys_user(id,nickname,user_status,register_status,created_at,updated_at)"
            + " VALUES(?,'D1测试','active','new',NOW(3),NOW(3))",
        mini);
    try {
      String[] stages = {
        "new",
        "demander_bound",
        "factory_invite_locked",
        "factory_pending",
        "factory_rejected",
        "factory_bound"
      };
      for (String status : List.of("active", "disabled"))
        for (String stage : stages) {
          jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", mini);
          String role =
              stage.equals("demander_bound")
                  ? "demander"
                  : stage.equals("factory_bound") ? "supplier" : null;
          if (role != null)
            jdbc.update(
                "INSERT INTO sys_user_role(id,user_id,role_code,enabled,created_at,updated_at)"
                    + " VALUES(?,?,?,1,NOW(3),NOW(3))",
                Ids.next(),
                mini,
                role);
          jdbc.update(
              "UPDATE sys_user SET user_status=?,register_status=? WHERE id=?",
              status,
              stage,
              mini);
          Handle handle = sessions.create(mini, "miniapp");
          JsonNode result =
              request(client(), "GET", "/api/me/context", null, null, handle.session().getId(), 200);
          assertEquals(
              status.equals("active") ? "normal" : "restricted",
              result.path("data").path("accessMode").asString());
          request(client(), "POST", "/api/auth/logout", null, null, handle.session().getId(), 200);
          assertNull(sessions.load(handle.session().getId()));
        }
      jdbc.update("UPDATE sys_user SET user_status='locked' WHERE id=?", mini);
      Handle h = sessions.create(mini, "miniapp");
      request(client(), "GET", "/api/me/context", null, null, h.session().getId(), 403);
      sessions.revoke(h.session().getId());
      jdbc.update(
          "UPDATE sys_user SET user_status='active',register_status='new' WHERE id=?", mini);
      h = sessions.create(mini, "miniapp");
      request(client(), "GET", "/api/me/context", null, null, h.session().getId(), 500);
      sessions.revoke(h.session().getId());
    } finally {
      jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", mini);
      jdbc.update("DELETE FROM sys_user WHERE id=?", mini);
    }
    request(client(), "POST", "/api/auth/logout", null, null, null, 200);
    Handle system = sessions.create(SystemIdentity.SYSTEM_USER_ID, "miniapp");
    request(client(), "GET", "/api/me/context", null, null, system.session().getId(), 403);
    sessions.revoke(system.session().getId());
    System.out.println(
        "AUTH/API PASS: all 8 operations, real Cookie/CSRF rotation, menu revocation, 12 contexts,"
            + " cross-client rejection, strict inputs, trace/no-store");
  }

  /** 用真实 Redis 检查会话连续性、并发及迟到回写。 */
  @Test
  void realRedisBoundariesContinuityConcurrencyAndLateWrites() throws Exception {
    RedisIndexedSessionRepository repo = context.getBean(RedisIndexedSessionRepository.class);
    long now = System.currentTimeMillis();
    for (String type : List.of("admin", "miniapp")) {
      long limit = type.equals("admin") ? 14_400_000L : 2_592_000_000L;
      SessionService start =
          new SessionService(
              repo,
              Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC),
              "test",
              context.getEnvironment().getRequiredProperty("spring.session.data.redis.namespace"));
      Handle handle = start.create(Ids.next(), type);
      assertNotNull(
          new SessionService(
                  repo,
                  Clock.fixed(Instant.ofEpochMilli(now + limit - 1), ZoneOffset.UTC),
                  "test",
                  context
                      .getEnvironment()
                      .getRequiredProperty("spring.session.data.redis.namespace"))
              .load(handle.session().getId()));
      assertNull(
          new SessionService(
                  repo,
                  Clock.fixed(Instant.ofEpochMilli(now + limit), ZoneOffset.UTC),
                  "test",
                  context
                      .getEnvironment()
                      .getRequiredProperty("spring.session.data.redis.namespace"))
              .load(handle.session().getId()));
      assertNull(
          new SessionService(
                  repo,
                  Clock.fixed(Instant.ofEpochMilli(now + limit + 1), ZoneOffset.UTC),
                  "test",
                  context
                      .getEnvironment()
                      .getRequiredProperty("spring.session.data.redis.namespace"))
              .load(handle.session().getId()));
      Handle expired = handle;
      assertThrows(ApiFailure.class, () -> start.touch(expired));
      assertNull(repo.findById(handle.session().getId()));
      handle = start.create(Ids.next(), type);
      for (int i = 1; i <= 5; i++) {
        SessionService active =
            new SessionService(
                repo,
                Clock.fixed(Instant.ofEpochMilli(now + i * (limit - 1000)), ZoneOffset.UTC),
                "test",
                context
                    .getEnvironment()
                    .getRequiredProperty("spring.session.data.redis.namespace"));
        active.touch(handle);
        assertNotNull(active.load(handle.session().getId()));
      }
      start.revoke(handle.session().getId());
    }
    Handle background = sessions.create(Ids.next(), "admin");
    StringRedisTemplate redis =
        context.getBean(org.springframework.data.redis.core.StringRedisTemplate.class);
    String backgroundKey = "mujian:test:session:sessions:" + background.session().getId();
    long ttl = redis.getExpire(backgroundKey, TimeUnit.MILLISECONDS);
    for (int i = 0; i < 5; i++) assertNotNull(sessions.load(background.session().getId()));
    assertTrue(redis.getExpire(backgroundKey, TimeUnit.MILLISECONDS) <= ttl);
    sessions.revoke(background.session().getId());
    Handle h = sessions.create(Ids.next(), "admin");
    ExecutorService pool = Executors.newFixedThreadPool(8);
    ArrayList<Callable<Void>> jobs = new ArrayList<Callable<Void>>();
    for (int i = 1; i <= 40; i++) {
      final int n = i;
      jobs.add(
          () -> {
            new SessionService(
                    repo,
                    Clock.fixed(Instant.ofEpochMilli(now + n * 1000L), ZoneOffset.UTC),
                    "test",
                    context
                        .getEnvironment()
                        .getRequiredProperty("spring.session.data.redis.namespace"))
                .touch(h);
            return null;
          });
    }
    for (Future<Void> f : pool.invokeAll(jobs)) f.get();
    pool.shutdown();
    assertEquals(
        now + 40_000L, (Long) repo.findById(h.session().getId()).getAttribute("lastInteractiveAt"));
    sessions.revoke(h.session().getId());
    assertThrows(ApiFailure.class, () -> sessions.touch(h));
    assertNull(repo.findById(h.session().getId()));
    SessionService raceStart =
        new SessionService(
            repo,
            Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC),
            "test",
            context.getEnvironment().getRequiredProperty("spring.session.data.redis.namespace"));
    Handle race = raceStart.create(Ids.next(), "admin");
    SessionService expiredService =
        new SessionService(
            repo,
            Clock.fixed(Instant.ofEpochMilli(now + 14_400_000L), ZoneOffset.UTC),
            "test",
            context.getEnvironment().getRequiredProperty("spring.session.data.redis.namespace"));
    ExecutorService racePool = Executors.newFixedThreadPool(8);
    ArrayList<Callable<Void>> raceJobs = new ArrayList<Callable<Void>>();
    for (int i = 0; i < 40; i++) {
      final int action = i % 3;
      raceJobs.add(
          () -> {
            if (action == 0) sessions.revoke(race.session().getId());
            else if (action == 1) assertNull(expiredService.load(race.session().getId()));
            else assertThrows(ApiFailure.class, () -> expiredService.touch(race));
            return null;
          });
    }
    for (Future<Void> f : racePool.invokeAll(raceJobs)) f.get();
    racePool.shutdown();
    assertNull(repo.findById(race.session().getId()));
    Handle a = sessions.create("device-user", "admin");
    Handle other = sessions.create("device-user", "admin");
    sessions.revoke(a.session().getId());
    assertNotNull(sessions.load(other.session().getId()));
    sessions.revoke(other.session().getId());
    System.out.println(
        "SESSION PASS: real Redis threshold -1/exact/+1, background reads do not extend TTL,"
            + " continuous >8h/>90d, monotonic 40-way updates, logout late touch rejected, device"
            + " isolation");
  }

  @org.apache.ibatis.plugin.Intercepts({
    @org.apache.ibatis.plugin.Signature(
        type = org.apache.ibatis.executor.Executor.class,
        method = "query",
        args = {
          org.apache.ibatis.mapping.MappedStatement.class,
          Object.class,
          org.apache.ibatis.session.RowBounds.class,
          org.apache.ibatis.session.ResultHandler.class
        }),
    @org.apache.ibatis.plugin.Signature(
        type = org.apache.ibatis.executor.Executor.class,
        method = "update",
        args = {org.apache.ibatis.mapping.MappedStatement.class, Object.class})
  })
  static class Probe implements org.apache.ibatis.plugin.Interceptor {
    final java.util.concurrent.atomic.AtomicInteger queries =
        new java.util.concurrent.atomic.AtomicInteger();
    volatile boolean failBinding;

    /** 对命中的映射调用进行观测或故障注入。 */
    public Object intercept(org.apache.ibatis.plugin.Invocation invocation) throws Throwable {
      MappedStatement statement =
          (org.apache.ibatis.mapping.MappedStatement) invocation.getArgs()[0];
      if (failBinding && statement.getId().endsWith(".bindRole"))
        throw new IllegalStateException("隔离测试主动注入事务末尾失败");
      if (invocation.getMethod().getName().equals("query")) queries.incrementAndGet();
      return invocation.proceed();
    }
  }

  /** 验证初始化回滚、身份竞争、限流及查询成本。 */
  @Test
  void initializationRollbackConcurrentIdentityRateLimitsAndQueryCost() throws Exception {
    AdminInitializationService adminInitializationService = context.getBean(AdminInitializationService.class);
    String rollbackName = "rollback." + Ids.next();
    probe.failBinding = true;
    try {
      assertThrows(RuntimeException.class, () -> adminInitializationService.initialize(rollbackName, password));
    } finally {
      probe.failBinding = false;
    }
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_user WHERE nickname=?", Integer.class, rollbackName));
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_account WHERE username=?", Integer.class, rollbackName));
    String original =
        jdbc.queryForObject(
            "SELECT password_hash FROM admin_account WHERE username=?", String.class, username);
    adminInitializationService.initialize(username, "DifferentStrongPassword_OnlyForRepeat");
    assertTrue(
        original.equals(
            jdbc.queryForObject(
                "SELECT password_hash FROM admin_account WHERE username=?",
                String.class,
                username)));
    String identity = Ids.next();
    jdbc.update(
        "INSERT INTO sys_user(id,nickname,user_status,register_status,created_at,updated_at)"
            + " VALUES(?,'并发测试','active','new',NOW(3),NOW(3))",
        identity);
    CyclicBarrier gate = new CyclicBarrier(2);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    DataSource ds = context.getBean(javax.sql.DataSource.class);
    ArrayList<Future<Boolean>> futures = new ArrayList<Future<Boolean>>();
    for (String role : List.of("demander", "supplier"))
      futures.add(
          pool.submit(
              () -> {
                try (Connection c = ds.getConnection()) {
                  c.setAutoCommit(false);
                  gate.await();
                  try (PreparedStatement q =
                      c.prepareStatement(
                          "INSERT INTO"
                              + " sys_user_role(id,user_id,role_code,enabled,created_at,updated_at)"
                              + " VALUES(?,?,?,1,NOW(3),NOW(3))")) {
                    q.setString(1, Ids.next());
                    q.setString(2, identity);
                    q.setString(3, role);
                    q.executeUpdate();
                    c.commit();
                    return true;
                  } catch (java.sql.SQLException e) {
                    c.rollback();
                    assertTrue(e.getErrorCode() == 1062 || e.getErrorCode() == 1213);
                    return false;
                  }
                }
              }));
    int winners = 0;
    for (Future<Boolean> f : futures) if (f.get(10, TimeUnit.SECONDS)) winners++;
    pool.shutdown();
    assertEquals(1, winners);
    assertEquals(
        1,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM sys_user_role WHERE user_id=? AND enabled=1",
            Integer.class,
            identity));
    jdbc.update("DELETE FROM sys_user_role WHERE user_id=?", identity);
    jdbc.update("DELETE FROM sys_user WHERE id=?", identity);
    HttpClient client = client();
    login(client);
    int before = probe.queries.get();
    request(client, "GET", "/api/admin/me", null, null, null, 200);
    assertEquals(4, probe.queries.get() - before);
    String accountId =
        jdbc.queryForObject(
            "SELECT id FROM admin_account WHERE username=?", String.class, username);
    List<Map<String, Object>> plan =
        jdbc.queryForList(
            "EXPLAIN SELECT DISTINCT rm.admin_menu_id FROM admin_account_role ar JOIN admin_role r"
                + " ON r.id=ar.admin_role_id AND r.enabled=1 JOIN admin_role_menu rm ON"
                + " rm.admin_role_id=r.id WHERE ar.admin_account_id=?",
            accountId);
    System.out.println("PERF EXPLAIN " + plan);
    long start = System.nanoTime();
    new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)
        .matches(password, original);
    System.out.println(
        "PERF BCrypt cost12 verify ms="
            + (System.nanoTime() - start) / 1_000_000
            + "; admin/me SQL count=4");
    jdbc.update(
        "UPDATE sys_user SET user_status='active' WHERE id=?", SystemIdentity.SYSTEM_USER_ID);
    try {
      assertThrows(
          IllegalStateException.class,
          () ->
              context
                  .getBean(com.mujian.backend.common.config.StartupVerification.class)
                  .run(new org.springframework.boot.DefaultApplicationArguments()));
    } finally {
      jdbc.update(
          "UPDATE sys_user SET user_status='disabled' WHERE id=?", SystemIdentity.SYSTEM_USER_ID);
    }
    System.out.println(
        "DB-03/04 PASS: concurrent identity exactly one winner; initialization tail failure rolls"
            + " back all DML; repeated init preserves hash; startup rejects altered system seed."
            + " RATE PASS: account/IP 5 failures, success does not clear IP.");
  }

  /** 复位隔离的 HTTP 限流预算。 */
  private void clearHttpBudget() throws Exception {
    // 只清本测试进程使用的 test 命名空间回环 IP；不触碰 development/production。
    String digest =
        java.util.HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest("127.0.0.1".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    StringRedisTemplate redis =
        context.getBean(org.springframework.data.redis.core.StringRedisTemplate.class);
    redis.delete(
        List.of(
            "mujian:test:{login-budget}:ip:failed:" + digest,
            "mujian:test:{login-budget}:ip:inflight:" + digest));
  }

  /** 区分密码错误与身份无权限。 */
  @Test
  void correctPasswordWithoutRoleIs403ButWrongPasswordIs401() throws Exception {
    clearHttpBudget();
    String account =
        jdbc.queryForObject(
            "SELECT id FROM admin_account WHERE username=?", String.class, username);
    Map<String, Object> binding =
        jdbc.queryForMap("SELECT * FROM admin_account_role WHERE admin_account_id=?", account);
    try {
      for (boolean disabled : List.of(false, true)) {
        if (disabled)
          jdbc.update(
              "UPDATE admin_role SET enabled=0 WHERE id=?", SystemIdentity.PLATFORM_ADMIN_ROLE_ID);
        else jdbc.update("DELETE FROM admin_account_role WHERE admin_account_id=?", account);
        try {
          HttpClient c = client();
          String token = csrf(c);
          assertEquals(
              "PERMISSION_DENIED",
              request(
                      c,
                      "POST",
                      "/api/admin/auth/login",
                      loginBody(c,username,password),
                      token,
                      null,
                      403)
                  .path("reasonCode")
                  .asString());
          assertEquals(
              "CREDENTIALS_INVALID",
              request(
                      c,
                      "POST",
                      "/api/admin/auth/login",
                      loginBody(c,username,"wrong-password"),
                      token,
                      null,
                      401)
                  .path("reasonCode")
                  .asString());
        } finally {
          if (disabled)
            jdbc.update(
                "UPDATE admin_role SET enabled=1 WHERE id=?",
                SystemIdentity.PLATFORM_ADMIN_ROLE_ID);
          else
            jdbc.update(
                "INSERT INTO"
                    + " admin_account_role(id,admin_account_id,admin_role_id,granted_by_admin_user_id,granted_at,created_at)"
                    + " VALUES(?,?,?,?,?,?)",
                binding.get("id"),
                account,
                binding.get("admin_role_id"),
                binding.get("granted_by_admin_user_id"),
                binding.get("granted_at"),
                binding.get("created_at"));
        }
      }
    } finally {
      clearHttpBudget();
    }
    System.out.println(
        "R03 PASS: missing role / disabled role correct password =>403 PERMISSION_DENIED; wrong"
            + " password =>401 CREDENTIALS_INVALID");
  }

  /** 验证并发预算、HTTP 取消与窗口到期。 */
  @Test
  void atomicBudgetParallelHttpCancellationAndExpiry() throws Exception {
    LoginRateLimiter limiter = context.getBean(LoginRateLimiter.class);
    for (boolean sameAccount : List.of(true, false)) {
      String subject = "budget." + Ids.next(), ip = "ip-" + Ids.next();
      ExecutorService pool = Executors.newFixedThreadPool(12);
      CyclicBarrier gate = new CyclicBarrier(12);
      CopyOnWriteArrayList<Attempt> accepted =
          new java.util.concurrent.CopyOnWriteArrayList<LoginRateLimiter.Attempt>();
      AtomicInteger rejected = new java.util.concurrent.atomic.AtomicInteger();
      ArrayList<Callable<Void>> jobs = new ArrayList<Callable<Void>>();
      for (int i = 0; i < 12; i++) {
        final int n = i;
        jobs.add(
            () -> {
              gate.await();
              try {
                accepted.add(
                    limiter.reserve(
                        sameAccount ? subject : subject + n, sameAccount ? ip + n : ip));
              } catch (ApiFailure failure) {
                assertEquals(429, failure.status);
                rejected.incrementAndGet();
              }
              return null;
            });
      }
      try {
        for (Future<Void> f : pool.invokeAll(jobs)) f.get();
        assertEquals(5, accepted.size());
        assertEquals(7, rejected.get());
      } finally {
        accepted.forEach(LoginRateLimiter.Attempt::close);
        pool.shutdownNow();
      }
      try (Attempt recovered = limiter.reserve(subject, ip)) {
        recovered.success();
      }
    }
    String subject = "settle." + Ids.next(), ip = "ip-" + Ids.next();
    for (int i = 0; i < 4; i++)
      try (Attempt a = limiter.reserve(subject, ip)) {
        a.failure();
      }
    try (Attempt a = limiter.reserve(subject, ip)) {
      a.success();
    }
    try (Attempt a = limiter.reserve(subject, ip)) {
      a.failure();
    }
    assertEquals(
        429,
        assertThrows(ApiFailure.class, () -> limiter.reserve("other." + Ids.next(), ip)).status);
    // 模拟请求中断：close 释放两侧，连续异常不累积失败。
    for (int i = 0; i < 12; i++)
      try (Attempt a = limiter.reserve("cancel." + subject, "cancel." + ip)) {}
    StringRedisTemplate redis =
        context.getBean(org.springframework.data.redis.core.StringRedisTemplate.class);
    String expiredSubject = "expired." + Ids.next(), expiredIp = "expired." + Ids.next();
    Attempt late = limiter.reserve(expiredSubject, expiredIp);
    for (String key : redis.keys("mujian:test:{login-budget}:*inflight:*")) {
      // 只使本次 subject/IP 的预占过期，绝不改失败窗口或生产阈值。
      for (String value : List.of(expiredSubject, expiredIp)) {
        String hash =
            java.util.HexFormat.of()
                .formatHex(
                    java.security.MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        if (key.endsWith(hash))
          for (String member : redis.opsForZSet().range(key, 0, -1))
            redis.opsForZSet().add(key, member, 1);
      }
    }
    assertEquals(500, assertThrows(ApiFailure.class, late::success).status);
    try (Attempt recovered = limiter.reserve(expiredSubject, expiredIp)) {
      recovered.success();
    }
    clearHttpBudget();
    ExecutorService pool = Executors.newFixedThreadPool(12);
    CyclicBarrier gate = new CyclicBarrier(12);
    ArrayList<Callable<Integer>> jobs = new ArrayList<Callable<Integer>>();
    String unknown = "http." + Ids.next();
    for (int i = 0; i < 12; i++) {
      HttpClient c = client();
      String token = csrf(c);
      String loginAttempt=loginBody(c,unknown,"wrong-password");
      jobs.add(
          () -> {
            gate.await();
            HttpResponse<String> response =
                c.send(
                    HttpRequest.newBuilder(URI.create(base + "/api/admin/auth/login"))
                        .header("Content-Type", "application/json")
                        .header("X-CSRF-Token", token)
                        .POST(
                            HttpRequest.BodyPublishers.ofString(
                                loginAttempt))
                        .build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonNode body = json.readTree(response.body());
            int status = response.statusCode();
            assertTrue(status == 401 || status == 429);
            if (System.getenv("MJ_EVIDENCE_DIR") != null)
              synchronized (this) {
                Files.writeString(
                    Path.of(System.getenv("MJ_EVIDENCE_DIR"), "api-responses.jsonl"),
                    json.writeValueAsString(
                            Map.of(
                                "method",
                                "POST",
                                "path",
                                "/api/admin/auth/login",
                                "status",
                                status,
                                "body",
                                body))
                        + "\n",
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
              }
            return status;
          });
    }
    try {
      List<Future<Integer>> results = pool.invokeAll(jobs);
      int admitted = 0, rejected = 0;
      for (Future<Integer> f : results) {
        if (f.get() == 401) admitted++;
        else rejected++;
      }
      assertEquals(5, admitted);
      assertEquals(7, rejected);
    } finally {
      pool.shutdownNow();
      clearHttpBudget();
    }
    System.out.println(
        "R04 PASS: 12 parallel same account / shared IP / real HTTP each admitted=5 rejected=7;"
            + " cancellation, expiry recovery, late completion rejection; success preserves IP"
            + " failures");
  }

  /** 核对通用菜单约束和显式路由登记。 */
  @Test
  void genericMenuConstraintAndExplicitRegistration() throws Exception {
    // 复制实际注册项，测试扩展不进入生产白名单。
    List<com.mujian.backend.common.config.MenuRegistration> menuRegistrationList =
        new ArrayList<>(
            context
                .getBeansOfType(com.mujian.backend.common.config.MenuRegistration.class)
                .values());
    menuRegistrationList.add(
        new com.mujian.backend.common.config.MenuRegistration(
            "/review/placeholder", "review/Placeholder"));
    MenuRegistry registry = new MenuRegistry(menuRegistrationList);
    AdminAccessService adminAccessService =
        new com.mujian.backend.application.service.admin.AdminAccessService(
            context.getBean(
                com.mujian.backend.infrastructure.persistence.mapper.admin.AdminMapper.class),
            registry);
    String user =
        jdbc.queryForObject(
            "SELECT user_id FROM admin_account WHERE username=?", String.class, username);
    String id = Ids.next(), binding = Ids.next();
    jdbc.update(
        "INSERT INTO"
            + " admin_menu(id,parent_id,menu_name,menu_type,permission_code,route_path,component_path,visible,enabled,sort_no,created_at,updated_at)"
            + " VALUES(?,?,?,'menu','admin:review:read','/review/placeholder','review/Placeholder',1,1,20,NOW(3),NOW(3))",
        id,
        SystemIdentity.SYSTEM_MENU_ID,
        "隔离测试注册页");
    try {
      jdbc.update(
          "INSERT INTO admin_role_menu(id,admin_role_id,admin_menu_id,created_at)"
              + " VALUES(?,?,?,NOW(3))",
          binding,
          SystemIdentity.PLATFORM_ADMIN_ROLE_ID,
          id);
      assertTrue(
          adminAccessService.load(user).navigation().stream()
              .flatMap(m -> m.children().stream())
              .anyMatch(m -> id.equals(m.id())));
      assertThrows(
          ApiFailure.class,
          () ->
              context
                  .getBean(com.mujian.backend.application.service.admin.AdminAccessService.class)
                  .load(user));
      for (String unsafe :
          List.of(
              "https://evil.example",
              "javascript:alert",
              "/system/../evil",
              "//evil",
              "/review?q=1"))
        assertThrows(
            org.springframework.dao.DataAccessException.class,
            () -> jdbc.update("UPDATE admin_menu SET route_path=? WHERE id=?", unsafe, id));
      for (String unsafe :
          List.of("../evil", "https://evil", "review/../Evil", "review/Example.vue"))
        assertThrows(
            org.springframework.dao.DataAccessException.class,
            () -> jdbc.update("UPDATE admin_menu SET component_path=? WHERE id=?", unsafe, id));
    } finally {
      jdbc.update("DELETE FROM admin_role_menu WHERE id=?", binding);
      jdbc.update("DELETE FROM admin_menu WHERE id=?", id);
    }
    assertEquals(
        0,
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM admin_role_menu WHERE id=?", Integer.class, binding));
    assertEquals(
        0, jdbc.queryForObject("SELECT COUNT(*) FROM admin_menu WHERE id=?", Integer.class, id));
    assertFalse(
        context.getBean(MenuRegistry.class).contains("/review/placeholder", "review/Placeholder"));
    System.out.println(
        "R02 PASS: second registered page accepted without CHECK change; production registry"
            + " rejects unknown page; unsafe external/script/traversal/component paths rejected by"
            + " real MySQL");
  }
}
