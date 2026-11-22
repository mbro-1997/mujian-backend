package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.MujianBackendApplication;
import com.mujian.backend.account.mapper.LoginMapper;
import com.mujian.backend.account.service.*;
import com.mujian.backend.account.service.DemanderBindingService;
import com.mujian.backend.account.service.RuleAcceptanceService.AcceptedRule;
import com.mujian.backend.account.service.WechatPhoneLoginService;
import com.mujian.backend.application.service.admin.AdminInitializationService;
import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.config.StartupVerification;
import com.mujian.backend.common.security.D2RateLimiter;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.common.security.SessionService.Handle;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.infrastructure.crypto.VerificationCrypto;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore;
import com.mujian.backend.infrastructure.storage.PrivateMaterialStore.Stored;
import com.mujian.backend.infrastructure.wechat.WechatGateway;
import com.mujian.backend.infrastructure.wechat.WechatGateway.Identity;
import com.mujian.backend.infrastructure.wechat.WechatHttpFixture;
import com.mujian.backend.material.mapper.MaterialReferenceMapper;
import com.mujian.backend.material.service.*;
import com.mujian.backend.material.service.MaterialCleanupAudit;
import com.mujian.backend.material.service.VerificationMaterialCleanup;
import com.mujian.backend.material.service.VerificationMaterialCleanup.Report;
import com.mujian.backend.material.service.VerificationMaterials;
import com.mujian.backend.material.service.VerificationMaterials.Receipt;
import com.mujian.backend.realname.service.*;
import com.mujian.backend.reward.service.FirstRewardCaptureService;
import com.mujian.backend.student.service.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.io.ByteArrayOutputStream;
import java.net.*;
import java.net.ServerSocket;
import java.net.http.*;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.function.Executable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;
import tools.jackson.databind.JsonNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class D2ApplicationTest {
  ConfigurableApplicationContext app;
  Class<?>[] additionalSources = new Class<?>[0];
  JdbcTemplate db;
  ObjectMapper json;
  SessionService sessions;
  String base, admin, adminCookie, csrf;
  String lastReason;
  MutableClock clock;
  MemoryStore store;
  FakeWechat wx;
  List<RuleAcceptanceService.AcceptedRule> rules;
  final HttpClient http = HttpClient.newHttpClient();

  public static class MutableClock extends Clock {
    volatile Instant at = Instant.now();

    /** 返回测试时钟的时区。 */
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    /** 获取指定时区的测试时钟。 */
    public Clock withZone(ZoneId z) {
      return this;
    }

    /** 提供用例设定的精确时刻。 */
    public Instant instant() {
      return at;
    }
  }

  public static class FakeWechat implements WechatGateway {
    final Map<String, Identity> identities = new ConcurrentHashMap<>();
    volatile WechatGateway delegate;

    /** 返回测试用小程序码结果。 */
    public byte[] miniCode(String scene, String page, String environment) {
      if (delegate != null) return delegate.miniCode(scene, page, environment);
      throw unavailable("QR_GENERATION_UNAVAILABLE");
    }

    /** 模拟微信登录凭据交换。 */
    public Identity exchange(String a, String b) {
      if (delegate != null) return delegate.exchange(a, b);
      Identity identity = identities.get(a);
      if (identity == null) throw new ApiFailure(400, "INVALID_ARGUMENT", "WECHAT_CREDENTIAL_INVALID");
      return identity;
    }
  }

  public static class MemoryStore implements PrivateMaterialStore {
    final Map<String, Stored> objects = new ConcurrentHashMap<>();
    final Map<String, byte[]> payloads = new ConcurrentHashMap<>();
    java.util.function.BiFunction<String, Instant, String> visualSigner;
    MutableClock clock;
    boolean failHead = false, failDelete = false, failPut = false, failSign = false, policy = true;
    int signed = 0;

    MemoryStore(MutableClock mutableClock) {
      clock = mutableClock;
    }

    /** 在隔离存储写入测试对象。 */
    public void put(String key, byte[] b, String type, Map<String, String> meta) {
      if (failPut) throw unavailable("OBJECT_STORAGE_UNAVAILABLE");
      objects.put(key, new Stored(key, meta, clock.instant(), null));
      payloads.put(key, b.clone());
    }

    /** 读取测试对象元信息。 */
    public Stored head(String key) {
      if (failHead || !objects.containsKey(key)) throw unavailable("OBJECT_STORAGE_UNAVAILABLE");
      return objects.get(key);
    }

    /** 生成测试访问凭据。 */
    public String sign(String key, Instant expires) {
      if (failSign) throw unavailable("OBJECT_STORAGE_UNAVAILABLE");
      signed++;
      return visualSigner == null
          ? "https://example.invalid/material/" + Ids.next()
          : visualSigner.apply(key, expires);
    }

    /** 列出本用例可访问的对象。 */
    public Page list(String marker) {
      List<Stored> rows =
          objects.values().stream()
              .sorted(Comparator.comparing(Stored::key))
              .filter(o -> marker == null || o.key().compareTo(marker) > 0)
              .limit(2)
              .toList();
      return new Page(rows, rows.size() == 2 ? rows.get(1).key() : null);
    }

    /** 提供本夹具用于判定删除策略的结果。 */
    public boolean deletionPolicyVerified() {
      return policy;
    }

    /** 删除隔离夹具中的对象。 */
    public void delete(String key, String version) {
      if (failDelete) throw unavailable("OBJECT_STORAGE_UNAVAILABLE");
      objects.remove(key);
    }
  }

  public static class Fault {
    volatile String observedRequestPath;
    final List<String> preparedSql = new java.util.concurrent.CopyOnWriteArrayList<>();
    volatile String table, blockTable, inviteReadTarget;
    volatile int failOnInsert = 1;
    final java.util.concurrent.atomic.AtomicInteger insertAttempts =
        new java.util.concurrent.atomic.AtomicInteger();
    final java.util.concurrent.atomic.AtomicInteger sqlCalls =
        new java.util.concurrent.atomic.AtomicInteger();
    final java.util.concurrent.atomic.AtomicInteger settlementVisibilityReads = new java.util.concurrent.atomic.AtomicInteger();
    final java.util.concurrent.atomic.AtomicInteger settlementUnlockSums = new java.util.concurrent.atomic.AtomicInteger();
    final java.util.concurrent.atomic.AtomicInteger disputeFactReads = new java.util.concurrent.atomic.AtomicInteger();
    final java.util.concurrent.atomic.AtomicInteger adminPermissionReads =
        new java.util.concurrent.atomic.AtomicInteger();
    final java.util.concurrent.atomic.AtomicInteger batchCalls =
        new java.util.concurrent.atomic.AtomicInteger();
    final java.util.concurrent.atomic.AtomicInteger activeQrQueries =
        new java.util.concurrent.atomic.AtomicInteger();
    volatile CountDownLatch inviteReadEntered, inviteReadRelease;
    volatile CountDownLatch entered, release;

    /** 核对场景断言或前置约束。 */
    void check(String sql, JdbcTemplate jdbc) {
      if (blockTable != null && sql.startsWith("INSERT INTO " + blockTable + " ")) {
        entered.countDown();
        try {
          if (!release.await(10, TimeUnit.SECONDS))
            throw new IllegalStateException("fixture barrier timed out");
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }

      if (table != null
          && (sql.startsWith("INSERT INTO " + table + " ")
              || sql.startsWith("INSERT INTO " + table + "("))
          && (failOnInsert == 1 || insertAttempts.incrementAndGet() == failOnInsert))
        jdbc.execute("SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='D2 controlled fault'");
    }
  }

  @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods = false)
  static class Fakes {
    /** 装配测试用映射故障注入器。 */
    @Bean
    org.apache.ibatis.plugin.Interceptor mappingFault(Fault fault, JdbcTemplate jdbc) {
      return new MappingFaultInterceptor(fault, jdbc);
    }

    /** 返回可控制的故障注入状态。 */
    @Bean
    Fault fault() {
      return new Fault();
    }

    /** 装配注入故障的数据库操作对象。 */
    @Bean
    @Primary
    JdbcTemplate faultJdbc(javax.sql.DataSource ds, Fault fault) {
      return new JdbcTemplate(ds) {
        /** 执行场景指定更新并注入预期故障。 */
        @Override
        public int update(String sql, Object... args) {
          fault.check(sql, this);
          return super.update(sql, args);
        }
      };
    }

    /** 装配具名参数的故障注入对象。 */
    @Bean
    @Primary
    org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate faultNamed(
        JdbcTemplate jdbc, Fault fault) {
      return new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc) {
        /** 读取场景结果集并应用指定故障。 */
        @Override
        public List<Map<String, Object>> queryForList(String sql, Map<String, ?> p) {
          fault.sqlCalls.incrementAndGet();
          if (sql.contains("FROM prop_qr_code WHERE target_type=:type AND target_id IN (:ids)"))
            fault.activeQrQueries.incrementAndGet();
          List<Map<String, Object>> result = super.queryForList(sql, p);
          // Test-only deterministic interleaving after the unlocked invitation hint read.
          if (fault.inviteReadTarget != null
              && fault.inviteReadTarget.equals(p.get("id"))
              && sql.contains(" FROM factory_invite_code WHERE id=:id")
              && !sql.contains("FOR UPDATE")) {
            fault.inviteReadTarget = null;
            fault.inviteReadEntered.countDown();
            try {
              if (!fault.inviteReadRelease.await(10, TimeUnit.SECONDS))
                throw new IllegalStateException("invite barrier timeout");
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            }
          }
          return result;
        }

        /** 执行本用例的批量更新。 */
        @Override
        public int[] batchUpdate(String sql, Map<String, ?>[] p) {
          for (Map<String, ?> row : p) fault.check(sql, jdbc);
          fault.sqlCalls.incrementAndGet();
          fault.batchCalls.incrementAndGet();
          return super.batchUpdate(sql, p);
        }

        /** 读取单条记录并应用受控故障。 */
        @Override
        public <T> T queryForObject(String sql, Map<String, ?> p, Class<T> type) {
          fault.sqlCalls.incrementAndGet();
          return super.queryForObject(sql, p, type);
        }

        /** 执行场景指定更新并注入预期故障。 */
        @Override
        public int update(String sql, Map<String, ?> p) {
          fault.check(sql, jdbc);
          fault.sqlCalls.incrementAndGet();
          return super.update(sql, p);
        }
      };
    }

    /** 提供可调节的业务时钟。 */
    @Bean
    @Primary
    MutableClock testClock() {
      return new MutableClock();
    }

    /** 装配微信测试替身。 */
    @Bean
    @Primary
    FakeWechat fakeWechat() {
      return new FakeWechat();
    }

    /** 装配隔离的材料对象存储。 */
    @Bean
    @Primary
    MemoryStore memoryStore(MutableClock mutableClock) {
      return new MemoryStore(mutableClock);
    }
  }

  /** 初始化本组隔离依赖。 */
  @BeforeAll
  void start() throws Exception {
    start(0);
  }

  boolean manualShutdown;

  /** 初始化本组隔离依赖。 */
  void start(int port, String... extraArguments) throws Exception {
    Properties testProperties = com.mujian.backend.support.TestDatabase.prepare();
    String cryptoVersion =
        testProperties.getProperty("mj.verification.crypto.active-version", "test-v1");
    String configuredKey =
        testProperties.getProperty("mj.verification.crypto.keys." + cryptoVersion);
    if (configuredKey == null || configuredKey.isBlank())
      throw new IllegalStateException(
          "D2 requires a persistent test-only crypto key; use prepare-d2-tests.py for a fresh"
              + " isolated config. Never replace runtime keys.");
    SpringApplication application =
        new SpringApplication(MujianBackendApplication.class, Fakes.class);
    application.setRegisterShutdownHook(!manualShutdown);
    application.addPrimarySources(List.of(additionalSources));
    application.setDefaultProperties(
        Map.of(
            "mj.verification.crypto.active-version",
            cryptoVersion,
            "mj.verification.crypto.keys." + cryptoVersion,
            configuredKey));
    ArrayList<String> arguments =
        new ArrayList<>(
            List.of(
                "--spring.config.additional-location="
                    + Path.of(System.getenv("MJ_TEST_CONFIG")).toUri(),
                "--server.port=" + port,
                "--mj.environment=test"));
    arguments.addAll(List.of(extraArguments));
    app = application.run(arguments.toArray(String[]::new));
    db = app.getBean(JdbcTemplate.class);
    json = app.getBean(ObjectMapper.class);
    sessions = app.getBean(SessionService.class);
    clock = app.getBean(MutableClock.class);
    store = app.getBean(MemoryStore.class);
    wx = app.getBean(FakeWechat.class);
    base = "http://127.0.0.1:" + ((WebServerApplicationContext) app).getWebServer().getPort();
    String name = "d2." + Ids.next();
    app.getBean(AdminInitializationService.class).initialize(name, UUID.randomUUID().toString());
    admin =
        db.queryForObject("SELECT user_id FROM admin_account WHERE username=?", String.class, name);
    Handle handle = sessions.create(admin, "admin");
    adminCookie = "mj_admin_dev=" + handle.session().getId();
    csrf = call("GET", "/api/admin/auth/csrf", null, null, null, 200).path("csrfToken").asString();
    rules =
        db.query(
            "SELECT id,rule_version FROM platform_rule_document WHERE rule_status='active' AND"
                + " rule_scope='all_user' AND is_required=1 ORDER BY rule_code",
            (r, n) -> new RuleAcceptanceService.AcceptedRule(r.getString(1), r.getString(2)));
  }

  /** 释放本组创建的资源。 */
  @AfterAll
  void stop() {
    if (app != null) app.close();
  }

  final Set<String> createdUsers = new LinkedHashSet<>();
  final List<User> createdSessions = new ArrayList<>();
  final Map<String, Set<String>> cleanupSeeds = new LinkedHashMap<>();

  record User(String id, String token) {}

  /** 建立本场景使用的账号。 */
  User user() {
    String code = Ids.next();
    wx.identities.put(
        code,
        new WechatGateway.Identity(
            "test-app",
            code,
            null,
            "+8613" + String.format("%09d", Math.abs(code.hashCode()) % 1000000000L),
            "86"));
    JsonNode n =
        json.valueToTree(app.getBean(WechatPhoneLoginService.class).login(code, "fixture", rules));
    D2ApplicationTest.User user =
        new User(n.path("context").path("userId").asString(), n.path("sessionToken").asString());
    app.getBean(DemanderBindingService.class).bind(user.id());
    createdUsers.add(user.id());
    createdSessions.add(user);
    return user;
  }

  /** 执行请求并检查预期响应。 */
  JsonNode call(String method, String path, Object body, User user, String key, int status)
      throws Exception {
    Builder b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(15));
    if (path.startsWith("/api/admin/")) {
      b.header("Cookie", adminCookie);
      if (!method.equals("GET")) b.header("X-CSRF-Token", csrf);
    } else if (user != null) b.header("Authorization", "Bearer " + user.token());
    if (key != null) b.header("Idempotency-Key", key);
    if (body != null) b.header("Content-Type", "application/json");
    b.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    return received(
        method, path, http.send(b.build(), HttpResponse.BodyHandlers.ofString()), status);
  }

  /** 记录或检查已收到的回执。 */
  JsonNode received(String method, String path, HttpResponse<String> response, int status)
      throws Exception {
    JsonNode n = json.readTree(response.body());
    lastReason = n.path("reasonCode").asString();
    if (Boolean.getBoolean("mj.verify-d3b-http")) {
      com.mujian.backend.common.security.D2Contract contract =
          app.getBean(com.mujian.backend.common.security.D2Contract.class);
      com.mujian.backend.common.security.D2Contract.Route route =
          contract.route(path.split("\\?")[0], method);
      if (route != null) {
        JsonNode schema =
            route
                .operation()
                .path("responses")
                .path(Integer.toString(response.statusCode()))
                .path("content")
                .path("application/json")
                .path("schema");
        if (schema.toString().contains("D3B_")) {
          contract.validate(schema, n);
          System.out.println(
              "D3B_HTTP "
                  + route.operation().path("operationId").asString()
                  + " "
                  + response.statusCode()
                  + " "
                  + lastReason);
        }
      }
    }

    assertEquals(
        status,
        response.statusCode(),
        () ->
            method
                + " "
                + path
                + " "
                + n.path("code").asString()
                + "/"
                + n.path("reasonCode").asString());
    assertEquals("no-store", response.headers().firstValue("cache-control").orElse(""));
    if (status != 200) assertTrue(n.path("data").isNull());
    String evidence = System.getenv("MJ_EVIDENCE_DIR");
    if (evidence != null) {
      JsonNode redacted = n.deepCopy();
      if (redacted.path("data").hasNonNull("inviteCode"))
        ((tools.jackson.databind.node.ObjectNode) redacted.get("data"))
            .put("inviteCode", "2".repeat(20));
      if (redacted.path("data").has("csrfToken"))
        ((tools.jackson.databind.node.ObjectNode) redacted.get("data"))
            .put("csrfToken", "REDACTED");
      if (redacted.path("data").has("sessionToken"))
        ((tools.jackson.databind.node.ObjectNode) redacted.get("data"))
            .put("sessionToken", "00000000-0000-4000-8000-000000000000");
      Files.writeString(
          Path.of(evidence, System.getProperty("mj.http.evidence-file", "d2-http.jsonl")),
          json.writeValueAsString(
                  view(
                      "method",
                      method,
                      "path",
                      path,
                      "status",
                      status,
                      "body",
                      redacted,
                      "externalBoundary",
                      (!(wx.delegate
                              instanceof
                              com.mujian.backend.infrastructure.wechat.OfficialWechatGateway)
                          ? "fake WechatGateway/PrivateMaterialStore; real HTTP/MySQL/Redis"
                          : "OfficialWechatGateway real JDK HTTP to controlled loopback fixture;"
                              + " fictional credentials; fake COS; real MySQL/Redis; not natural"
                              + " WeChat failure")))
              + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    }
    return n.path("data");
  }

  /** 提供符合要求的测试图片。 */
  static byte[] png() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
    return out.toByteArray();
  }

  /** 上传场景所需材料。 */
  String upload(User user, String domain, String side) throws Exception {
    return upload(user, domain, side, 200);
  }

  /** 上传场景所需材料。 */
  String upload(User user, String domain, String side, int expectedStatus) throws Exception {
    return upload(user, domain, side, expectedStatus, domain + "-material-v1", png());
  }

  /** 上传场景所需材料。 */
  String upload(
      User user, String domain, String side, int expectedStatus, String version, byte[] bytes)
      throws Exception {
    String boundary = "D2" + Ids.next();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    LinkedHashMap<String, String> fields = new LinkedHashMap<String, String>();
    fields.put("consentAccepted", "true");
    fields.put("consentVersion", version);
    if (side != null) fields.put("side", side);
    for (Entry<String, String> e : fields.entrySet())
      out.write(
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\""
                  + e.getKey()
                  + "\"\r\n\r\n"
                  + e.getValue()
                  + "\r\n")
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    out.write(
        ("--"
                + boundary
                + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"fictional.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n")
            .getBytes());
    out.write(bytes);
    out.write(("\r\n--" + boundary + "--\r\n").getBytes());
    String path = "/api/me/" + domain + "-materials";
    HttpRequest req =
        HttpRequest.newBuilder(URI.create(base + path))
            .header("Authorization", "Bearer " + user.token())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
            .build();
    HttpResponse<String> response = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (expectedStatus == 429) {
      int wait = Integer.parseInt(response.headers().firstValue("Retry-After").orElseThrow());
      assertTrue(wait >= 1 && wait <= 60);
    }
    return received("POST", path, response, expectedStatus).path("uploadId").asString();
  }

  /** 构造符合当前场景的请求正文。 */
  Map<String, Object> body(User user, String d) throws Exception {
    Map<String, Object> b = view("consentAccepted", true, "consentVersion", d + "-material-v1");
    if (d.equals("realname"))
      b.putAll(
          view(
              "realName",
              "测试样本",
              "idCard",
              "11010519491231002X",
              "frontUploadId",
              upload(user, d, "front"),
              "backUploadId",
              upload(user, d, "back")));
    else
      b.putAll(
          view(
              "schoolName",
              "虚构测试学校",
              "materialUploadIds",
              List.of(upload(user, d, null), upload(user, d, null))));
    return b;
  }

  /** 构造审核请求正文。 */
  Map<String, Object> reviewBody(JsonNode detail, boolean approve, String d) {
    Map<String, Object> b =
        view(
            "caseId",
            detail.path("caseId").asString(),
            "expectedVersion",
            detail.path("version").asInt(),
            "expectedCaseVersion",
            detail.path("caseVersion").asInt(),
            "remark",
            approve ? "核验虚构夹具通过" : "请补充清晰材料");
    if (d.equals("student") && approve) b.put("validYears", 1);
    return b;
  }

  /** 覆盖认证申请驳回、重提、回放及首次事实。 */
  @Test
  void all23OperationsWithRejectionResubmissionReplayAndFirstFact() throws Exception {
    int rulesBefore = db.queryForObject("SELECT COUNT(*) FROM coupon_reward_rule", Integer.class);
    JsonNode collection = call("GET", "/api/public/rules?scene=register", null, null, null, 200);
    for (JsonNode r : collection.path("rules"))
      call("GET", "/api/public/rules/" + r.path("documentId").asString(), null, null, null, 200);
    String code = Ids.next();
    wx.identities.put(
        code,
        new WechatGateway.Identity(
            "test-app",
            code,
            null,
            "+861990000" + String.format("%04d", Math.abs(code.hashCode()) % 10000),
            "86"));
    JsonNode login =
        call(
            "POST",
            "/api/public/auth/wechat-phone-login",
            view(
                "loginCode",
                code,
                "phoneCode",
                "fixture",
                "consentAccepted",
                true,
                "acceptedRules",
                rules),
            null,
            null,
            200);
    D2ApplicationTest.User user =
        new User(
            login.path("context").path("userId").asString(), login.path("sessionToken").asString());
    call("POST", "/api/me/roles/demander", Map.of(), user, null, 200);
    call("GET", "/api/me/account-summary", null, user, null, 200);
    for (String d : List.of("student", "realname")) {
      String own = "/api/me/" + d + "-verifications",
          adminPath = "/api/admin/" + d + "-verifications";
      JsonNode initial = call("GET", own + "/current", null, user, null, 200);
      assertTrue(initial.path("canSubmit").asBoolean());
      if (d.equals("student"))
        assertEquals("unverified", initial.path("realnameStatus").asString());
      String key = Ids.next();
      Map<String, Object> body = body(user, d);
      JsonNode submitted = call("POST", own, body, user, key, 200);
      assertEquals("created", submitted.path("outcome").asString());
      String id = submitted.path("application").path("id").asString();
      call("GET", own + "/by-request/" + key, null, user, null, 200);
      assertEquals("replayed", call("POST", own, body, user, key, 200).path("outcome").asString());
      assertEquals(
          "existing_pending",
          call("POST", own, body, user, Ids.next(), 200).path("outcome").asString());
      LinkedHashMap<String, Object> changed = new LinkedHashMap<>(body);
      changed.put(d.equals("realname") ? "realName" : "schoolName", "变更样本");
      call("POST", own, changed, user, key, 409);
      call(
          "GET",
          adminPath + "?status=pending&page=1&pageSize=20&keyword=" + user.id(),
          null,
          null,
          null,
          200);
      JsonNode detail = call("GET", adminPath + "/" + id, null, null, null, 200);
      assertFalse(detail.toString().contains("11010519491231002X"));
      call("POST", adminPath + "/" + id + "/sensitive-view", Map.of(), null, null, 200);
      String reject = Ids.next();
      Map<String, Object> rejectBody = reviewBody(detail, false, d);
      JsonNode rejected =
          call("POST", adminPath + "/" + id + "/reject", rejectBody, null, reject, 200);
      assertEquals(
          rejected, call("POST", adminPath + "/" + id + "/reject", rejectBody, null, reject, 200));
      assertEquals("replayed", call("POST", own, body, user, key, 200).path("outcome").asString());
      JsonNode again = call("POST", own, body(user, d), user, Ids.next(), 200);
      String second = again.path("application").path("id").asString();
      assertNotEquals(id, second);
      detail = call("GET", adminPath + "/" + second, null, null, null, 200);
      Map<String, Object> approvedBody = reviewBody(detail, true, d);
      String approve = Ids.next();
      JsonNode approved =
          call("POST", adminPath + "/" + second + "/approve", approvedBody, null, approve, 200);
      assertEquals(
          approved,
          call("POST", adminPath + "/" + second + "/approve", approvedBody, null, approve, 200));
      call("GET", own + "/current", null, user, null, 200);
    }
    assertEquals(
        1,
        db.queryForObject(
            "SELECT COUNT(*) FROM coupon_reward_record WHERE user_id=? AND"
                + " reward_status='not_eligible' AND ineligible_reason='NO_ACTIVE_RULE'",
            Integer.class,
            user.id()));
    assertEquals(
        rulesBefore, db.queryForObject("SELECT COUNT(*) FROM coupon_reward_rule", Integer.class));
  }

  /** 验证注册规则变动时新增账号回滚。 */
  @Test
  void ruleChangeRollsBackNewAccount() {
    String code = Ids.next();
    wx.identities.put(
        code, new WechatGateway.Identity("test-app", code, null, "+8618800000001", "86"));
    ArrayList<AcceptedRule> invalid = new ArrayList<>(rules);
    invalid.set(0, new RuleAcceptanceService.AcceptedRule(rules.get(0).documentId(), "stale"));
    ApiFailure apiFailure =
        assertThrows(
            ApiFailure.class,
            () -> app.getBean(WechatPhoneLoginService.class).login(code, "fixture", invalid));
    assertEquals("RULE_VERSION_CHANGED", apiFailure.reason);
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM user_login_identity WHERE openid=?", Integer.class, code));
  }

  /** 让同一申请的相反审核决定实际竞争。 */
  @Test
  void oppositeReviewsActuallyRaceOnSameApplication() throws Exception {
    String secondName = "d2." + Ids.next();
    app.getBean(AdminInitializationService.class)
        .initialize(secondName, UUID.randomUUID().toString());
    String secondAdmin =
        db.queryForObject(
            "SELECT user_id FROM admin_account WHERE username=?", String.class, secondName);
    for (String d : List.of("realname", "student")) {
      D2ApplicationTest.User user = user();
      JsonNode made =
          call("POST", "/api/me/" + d + "-verifications", body(user, d), user, Ids.next(), 200);
      String id = made.path("application").path("id").asString();
      JsonNode detail =
          call("GET", "/api/admin/" + d + "-verifications/" + id, null, null, null, 200);
      CyclicBarrier barrier = new CyclicBarrier(2);
      ExecutorService pool = Executors.newFixedThreadPool(2);
      try {
        ArrayList<Future<String>> tasks = new ArrayList<Future<String>>();
        for (boolean approve : List.of(true, false))
          tasks.add(
              pool.submit(
                  () -> {
                    barrier.await();
                    try {
                      if (d.equals("realname"))
                        app.getBean(RealnameReviewService.class)
                            .review(
                                approve ? admin : secondAdmin,
                                id,
                                Ids.next(),
                                approve,
                                new RealnameReviewService.ReviewCommand(
                                    detail.path("caseId").asString(), 0, 0, "并发审核", null),
                                Ids.next());
                      else
                        app.getBean(StudentReviewService.class)
                            .review(
                                approve ? admin : secondAdmin,
                                id,
                                Ids.next(),
                                approve,
                                new StudentReviewService.ReviewCommand(
                                    detail.path("caseId").asString(),
                                    0,
                                    0,
                                    "并发审核",
                                    approve ? 2 : null),
                                Ids.next());
                      return "OK";
                    } catch (ApiFailure e) {
                      return e.reason;
                    }
                  }));
        HashSet<String> outcomes = new HashSet<String>();
        for (Future<String> f : tasks) outcomes.add(f.get(15, TimeUnit.SECONDS));
        assertEquals(Set.of("OK", d.toUpperCase() + "_ALREADY_REVIEWED"), outcomes);
        assertEquals(
            1,
            db.queryForObject(
                "SELECT COUNT(*) FROM admin_case_action WHERE target_id=?", Integer.class, id));
      } finally {
        pool.shutdownNow();
      }
    }
  }

  /** 核对认证有效期及加密数据隔离。 */
  @Test
  void expiryCalendarBoundaryAndCryptographicIsolation() {
    LocalDateTime leap = LocalDateTime.of(2024, 2, 29, 10, 20, 30, 123000000);
    for (int years = 1; years <= 5; years++) {
      LocalDateTime expiry =
          com.mujian.backend.student.policy.StudentValidityPolicy.expiry(leap, years);
      assertEquals(leap.plusYears(years), expiry);
      assertEquals(
          "approved",
          com.mujian.backend.student.policy.StudentValidityPolicy.status(
              "approved", expiry, expiry.minusNanos(1)));
      assertEquals(
          "expired",
          com.mujian.backend.student.policy.StudentValidityPolicy.status(
              "approved", expiry, expiry));
    }
    VerificationCrypto crypto =
        app.getBean(com.mujian.backend.infrastructure.crypto.VerificationCrypto.class);
    String id = Ids.next();
    byte[] cipher = crypto.encrypt(id, "real_name_ciphertext", crypto.version(), "虚构样本");
    assertEquals("虚构样本", crypto.decrypt(id, "real_name_ciphertext", crypto.version(), cipher));
    assertThrows(
        ApiFailure.class,
        () -> crypto.decrypt(Ids.next(), "real_name_ciphertext", crypto.version(), cipher));
    cipher[cipher.length - 1] ^= 1;
    assertThrows(
        ApiFailure.class,
        () -> crypto.decrypt(id, "real_name_ciphertext", crypto.version(), cipher));
  }

  /** 检查逐对象审计及删除前失败保护。 */
  @Test
  void cleanupAuditCoversEveryObjectAndFailsClosedBeforeDelete() throws Exception {
    D2ApplicationTest.User user = user();
    Receipt receipt =
        app.getBean(VerificationMaterials.class)
            .upload(user.id(), "student_card", "student-material-v1", true, png());
    VerificationMaterialCleanup cleanup = app.getBean(VerificationMaterialCleanup.class);
    D2ApplicationTest.Fault fault = app.getBean(Fault.class);
    Instant original = clock.at;
    try {
      Report young = cleanup.scan(true);
      assertCleanupDetails(young);
      clock.at = original.plus(Duration.ofHours(48));
      Report report = cleanup.scan(true);
      assertCleanupDetails(report);
      assertTrue(
          db.queryForObject(
                  "SELECT COUNT(*) FROM admin_operation_audit WHERE request_id=? AND"
                      + " operation_reason='report_only_candidate'",
                  Integer.class,
                  report.runId())
              > 0);
      store.policy = false;
      Report denied = cleanup.scan(false);
      assertCleanupDetails(denied);
      assertTrue(store.objects.containsKey(receipt.objectKey()));
      store.policy = true;
      fault.table = "admin_operation_audit";
      Report failed = cleanup.scan(false);
      assertTrue(failed.unknown() > 0);
      assertTrue(store.objects.containsKey(receipt.objectKey()));
      fault.table = null;
      Report done = cleanup.scan(false);
      assertCleanupDetails(done);
      assertFalse(store.objects.containsKey(receipt.objectKey()));
      List<String> details =
          db.queryForList(
              "SELECT after_snapshot_json FROM admin_operation_audit WHERE request_id=?",
              String.class,
              done.runId());
      for (String value : details) {
        assertFalse(value.contains("dev/d2-a/verification/"));
        assertFalse(value.contains(user.id()));
        JsonNode n = json.readTree(value);
        assertEquals(64, n.path("objectKeyHash").asString().length());
        assertTrue(n.has("versionIdHash"));
        assertTrue(n.has("time"));
      }
    } finally {
      clock.at = original;
      store.policy = true;
      fault.table = null;
    }
  }

  /** 断言每条清理记录的状态及依据。 */
  void assertCleanupDetails(VerificationMaterialCleanup.Report report) {
    int terminal =
        db.queryForObject(
            "SELECT COUNT(*) FROM admin_operation_audit WHERE request_id=? AND"
                + " JSON_UNQUOTE(JSON_EXTRACT(after_snapshot_json,'$.result'))<>'delete_intent'",
            Integer.class,
            report.runId());
    assertEquals(report.scanned(), terminal);
    assertEquals(
        report.scanned(),
        report.retained() + report.skipped() + report.deleted() + report.unknown());
  }

  /** 用 Redis 窗口核对限流响应头和恢复。 */
  @Test
  void d2RateLimitHttpHeadersUseRedisWindowAndRecover() throws Exception {
    StringRedisTemplate redis =
        app.getBean(org.springframework.data.redis.core.StringRedisTemplate.class);
    D2RateLimiter limiter = app.getBean(com.mujian.backend.common.security.D2RateLimiter.class);
    String env = app.getEnvironment().getProperty("mj.environment");
    String ipKey = "mujian:" + env + ":d2budget:ip:" + MaterialCleanupAudit.digest("127.0.0.1");
    try {
      redis.delete(ipKey);
      for (int i = 0; i < 20; i++) limiter.check("ip", "127.0.0.1", 20);
      HttpResponse<String> response =
          http.send(
              HttpRequest.newBuilder(URI.create(base + "/api/public/auth/wechat-phone-login"))
                  .header("Content-Type", "application/json")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          json.writeValueAsString(
                              view(
                                  "loginCode",
                                  "invalid-code",
                                  "phoneCode",
                                  "invalid-phone",
                                  "consentAccepted",
                                  true,
                                  "acceptedRules",
                                  rules))))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(
          429,
          response.statusCode(),
          () -> json.readTree(response.body()).path("reasonCode").asString());
      int wait = Integer.parseInt(response.headers().firstValue("Retry-After").orElseThrow());
      assertTrue(wait >= 1 && wait <= 60);
      received("POST", "/api/public/auth/wechat-phone-login", response, 429);
      redis.expire(ipKey, Duration.ofMillis(150));
      Thread.sleep(200);
      HttpResponse<String> restored =
          http.send(
              HttpRequest.newBuilder(URI.create(base + "/api/public/auth/wechat-phone-login"))
                  .header("Content-Type", "application/json")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          json.writeValueAsString(
                              view(
                                  "loginCode",
                                  "invalid-code",
                                  "phoneCode",
                                  "invalid-phone",
                                  "consentAccepted",
                                  true,
                                  "acceptedRules",
                                  rules))))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertEquals(400, restored.statusCode());
      assertTrue(restored.headers().firstValue("Retry-After").isEmpty());
      for (String category : List.of("upload", "identity")) {
        String subject = "c14-" + Ids.next();
        for (int i = 0; i < 10; i++) limiter.check(category, subject, 10);
        ApiFailure apiFailure = assertThrows(ApiFailure.class, () -> limiter.check(category, subject, 10));
        assertEquals(429, apiFailure.status);
        assertTrue(apiFailure.retryAfterSeconds >= 1 && apiFailure.retryAfterSeconds <= 60);
        String key =
            "mujian:" + env + ":d2budget:" + category + ":" + MaterialCleanupAudit.digest(subject);
        redis.delete(key);
      }
    } finally {
      redis.delete(ipKey);
    }
  }

  /** 验证两类材料上传共用预算并可恢复。 */
  @Test
  void bothMaterialHttpRoutesShareUploadBudgetAndRecover() throws Exception {
    D2ApplicationTest.User user = user();
    D2RateLimiter limiter = app.getBean(com.mujian.backend.common.security.D2RateLimiter.class);
    StringRedisTemplate redis =
        app.getBean(org.springframework.data.redis.core.StringRedisTemplate.class);
    String key =
        "mujian:"
            + app.getEnvironment().getProperty("mj.environment")
            + ":d2budget:upload:"
            + MaterialCleanupAudit.digest(user.id());
    try {
      for (int i = 0; i < 10; i++) limiter.check("upload", user.id(), 10);
      upload(user, "realname", "front", 429);
      upload(user, "student", null, 429);
      redis.expire(key, Duration.ofMillis(150));
      Thread.sleep(200);
      assertFalse(upload(user, "realname", "front").isEmpty());
      assertFalse(upload(user, "student", null).isEmpty());
    } finally {
      redis.delete(key);
    }
  }

  /** 验证删除后审计失败保持结果未知。 */
  @Test
  void cleanupDeletedButOutcomeAuditFailureStaysUnknown() throws Exception {
    D2ApplicationTest.User user = user();
    Receipt receipt =
        app.getBean(VerificationMaterials.class)
            .upload(user.id(), "student_card", "student-material-v1", true, png());
    D2ApplicationTest.MemoryStore isolated = new MemoryStore(clock);
    isolated.objects.put(receipt.objectKey(), store.objects.get(receipt.objectKey()));
    PlatformTransactionManager tm =
        app.getBean(org.springframework.transaction.PlatformTransactionManager.class);
    MaterialCleanupAudit audit =
        new MaterialCleanupAudit(
            app.getBean(com.mujian.backend.casework.mapper.AdminOperationAuditMapper.class),
            json,
            clock,
            tm) {
          /** 取得对应的执行记录。 */
          @Override
          public void record(
              String run,
              String key,
              String purpose,
              String version,
              String result,
              String reason) {
            if (result.equals("deleted"))
              throw new org.springframework.dao.DataAccessResourceFailureException(
                  "controlled outcome audit fault");
            super.record(run, key, purpose, version, result, reason);
          }
        };
    VerificationMaterialCleanup cleanup =
        new VerificationMaterialCleanup(
            isolated,
            app.getBean(com.mujian.backend.account.mapper.LoginMapper.class),
            app.getBean(com.mujian.backend.material.mapper.MaterialReferenceMapper.class),
            clock,
            app.getEnvironment(),
            tm,
            audit);
    Instant original = clock.at;
    try {
      clock.at = original.plus(Duration.ofHours(48));
      Report report = cleanup.scan(false);
      assertEquals(1, report.unknown());
      assertEquals(0, report.deleted());
      assertFalse(isolated.objects.containsKey(receipt.objectKey()));
      assertEquals(
          1,
          db.queryForObject(
              "SELECT COUNT(*) FROM admin_operation_audit WHERE request_id=? AND"
                  + " operation_reason='delete_outcome_audit'",
              Integer.class,
              report.runId()));
      assertEquals(
          2,
          db.queryForObject(
              "SELECT COUNT(*) FROM admin_operation_audit WHERE request_id=?",
              Integer.class,
              report.runId()));
    } finally {
      clock.at = original;
    }
  }

  /** 通过实际 HTTP 验证基础设施异常协议。 */
  @Test
  void infrastructureFailuresAcrossActualHttpRoutes() throws Exception {
    for (String failure : List.of("database", "redis_lifecycle", "redis_network")) {
      D2ApplicationTest isolated = new D2ApplicationTest();
      isolated.start();
      try {
        D2ApplicationTest.User user = isolated.user();
        Map<String, Map<String, Object>> bodies =
            Map.of(
                "realname", isolated.body(user, "realname"), "student", isolated.body(user, "student"));
        String code = Ids.next();
        isolated.wx.identities.put(
            code, new WechatGateway.Identity("test-app", code, null, "+8613998765432", "86"));
        Map<String, Object> loginBody =
            view(
                "loginCode",
                code,
                "phoneCode",
                "fixture",
                "consentAccepted",
                true,
                "acceptedRules",
                isolated.rules);
        JsonNode contract =
            json.readTree(getClass().getResourceAsStream("/contracts/d2a.openapi.json"));
        if (failure.equals("database"))
          isolated.app.getBean(com.zaxxer.hikari.HikariDataSource.class).close();
        else {
          LettuceConnectionFactory factory =
              isolated.app.getBean(
                  org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory.class);
          if (failure.equals("redis_lifecycle")) factory.destroy();
          else {
            int unused;
            try (ServerSocket socket = new java.net.ServerSocket(0)) {
              unused = socket.getLocalPort();
            }
            factory.stop();
            factory.getStandaloneConfiguration().setPort(unused);
            factory.start();
          }
        }
        ArrayList<Executable> assertions =
            new ArrayList<org.junit.jupiter.api.function.Executable>();
        for (Entry<String, JsonNode> entry : contract.path("paths").properties()) {
          String template = entry.getKey(), path = template.replaceAll("\\{[^}]+}", Ids.next());
          Entry<String, JsonNode> operation = entry.getValue().properties().iterator().next();
          String method = operation.getKey().toUpperCase();
          if (failure.equals("redis_network") && template.startsWith("/api/public/rules")) continue;
          if (path.equals("/api/public/rules")) path += "?scene=register";
          String domain = path.contains("realname") ? "realname" : "student";
          Object body = null;
          if (method.equals("POST")) {
            if (path.endsWith("wechat-phone-login")) body = loginBody;
            else if (path.matches("/api/me/(realname|student)-verifications"))
              body = bodies.get(domain);
            else if (path.endsWith("/approve") || path.endsWith("/reject"))
              body =
                  view(
                      "caseId",
                      Ids.next(),
                      "expectedVersion",
                      0,
                      "expectedCaseVersion",
                      0,
                      "remark",
                      "隔离故障测试");
            else body = Map.of();
            if (path.contains("student") && path.endsWith("/approve"))
              ((Map<String, Object>) body).put("validYears", 1);
          }
          String target = path;
          Object payload = body;
          assertions.add(
              () -> {
                if (target.endsWith("-materials"))
                  isolated.upload(
                      user,
                      domain,
                      domain.equals("realname") ? "front" : null,
                      failure.equals("redis_lifecycle") ? 500 : 503);
                else
                  isolated.call(
                      method,
                      target,
                      payload,
                      user,
                      Ids.next(),
                      failure.equals("redis_lifecycle") ? 500 : 503);
              });
        }
        assertAll(failure + " actual HTTP routes", assertions);
      } finally {
        isolated.stop();
      }
    }
  }

  /** 通过实际 HTTP 核对账号准入限制。 */
  @Test
  void accountGuardsAcrossActualHttpRoutes() throws Exception {
    JsonNode contract =
        json.readTree(getClass().getResourceAsStream("/contracts/d2a.openapi.json"));
    D2ApplicationTest.User user = user();
    Map<String, Map<String, Object>> bodies =
        Map.of("realname", body(user, "realname"), "student", body(user, "student"));
    String originalCookie = adminCookie, originalCsrf = csrf;
    try {
      for (String mode : List.of("restricted", "context", "role")) {
        db.update(
            "UPDATE sys_user SET user_status=? WHERE id IN (?,?)",
            mode.equals("restricted") ? "locked" : "active",
            user.id(),
            admin);
        db.update(
            "UPDATE sys_user_role SET enabled=? WHERE user_id=?",
            mode.equals("restricted") ? 1 : 0,
            user.id());
        db.update(
            "UPDATE sys_user SET register_status=? WHERE id=?",
            mode.equals("role") ? "new" : "demander_bound",
            user.id());
        for (Entry<String, JsonNode> entry : contract.path("paths").properties()) {
          String template = entry.getKey();
          if (template.startsWith("/api/public/")) continue;
          boolean adminRoute = template.startsWith("/api/admin/");
          // Admin context corruption needs a separate admin hierarchy fixture; mini role checks
          // do not apply to admin routes. Only run declared, reachable guards here.
          if (adminRoute && !mode.equals("restricted")) continue;
          Entry<String, JsonNode> operation = entry.getValue().properties().iterator().next();
          String method = operation.getKey().toUpperCase(),
              domain = template.contains("realname") ? "realname" : "student";
          int status = mode.equals("context") ? 500 : 403;
          String reason =
              mode.equals("restricted")
                  ? "ACCOUNT_ACTION_RESTRICTED"
                  : mode.equals("context")
                      ? "ACCOUNT_CONTEXT_INCONSISTENT"
                      : domain.toUpperCase() + "_ROLE_NOT_ELIGIBLE";
          if (!operation
              .getValue()
              .path("responses")
              .path(Integer.toString(status))
              .path("description")
              .asString()
              .contains(reason)) continue;
          String path = template.replaceAll("\\{[^}]+}", Ids.next());
          if (adminRoute) {
            Handle handle = sessions.create(admin, "admin");
            adminCookie = "mj_admin_dev=" + handle.session().getId();
            csrf =
                call("GET", "/api/admin/auth/csrf", null, null, null, 200)
                    .path("csrfToken")
                    .asString();
          }
          Object payload = null;
          if (method.equals("POST")) {
            if (path.matches("/api/me/(realname|student)-verifications"))
              payload = bodies.get(domain);
            else if (path.endsWith("/approve") || path.endsWith("/reject")) {
              Map<String, Object> review =
                  view(
                      "caseId",
                      Ids.next(),
                      "expectedVersion",
                      0,
                      "expectedCaseVersion",
                      0,
                      "remark",
                      "隔离身份边界");
              if (domain.equals("student") && path.endsWith("/approve"))
                review.put("validYears", 1);
              payload = review;
            } else payload = Map.of();
          }
          if (path.endsWith("-materials"))
            upload(user, domain, domain.equals("realname") ? "front" : null, status);
          else call(method, path, payload, user, Ids.next(), status);
          assertEquals(reason, lastReason);
          if (adminRoute)
            assertNull(sessions.load(adminCookie.substring("mj_admin_dev=".length())));
        }
      }
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM user_realname_verification WHERE user_id=?",
              Integer.class,
              user.id()));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM user_student_verification WHERE user_id=?",
              Integer.class,
              user.id()));
    } finally {
      db.update("UPDATE sys_user SET user_status='active' WHERE id IN (?,?)", user.id(), admin);
      db.update("UPDATE sys_user_role SET enabled=1 WHERE user_id=?", user.id());
      db.update("UPDATE sys_user SET register_status='demander_bound' WHERE id=?", user.id());
      adminCookie = originalCookie;
      csrf = originalCsrf;
    }
  }

  /** 检查两类接口的材料及资源缺失错误。 */
  @Test
  void materialAndMissingResourceFailuresThroughBothHttpDomains() throws Exception {
    for (String domain : List.of("realname", "student")) {
      D2ApplicationTest.User user = user();
      String side = domain.equals("realname") ? "front" : null;
      upload(user, domain, side, 409, "obsolete-material-version", png());
      assertEquals("CONSENT_VERSION_CHANGED", lastReason);
      upload(user, domain, side, 400, domain + "-material-v1", new byte[] {1, 2, 3});
      assertEquals("MATERIAL_TYPE_UNSUPPORTED", lastReason);
      upload(user, domain, side, 400, domain + "-material-v1", new byte[5242881]);
      assertEquals("MATERIAL_TOO_LARGE", lastReason);
      Map<String, Object> input = body(user, domain);
      LinkedHashMap<String, Object> invalid = new LinkedHashMap<>(input);
      invalid.put("consentVersion", "obsolete-material-version");
      call("POST", "/api/me/" + domain + "-verifications", invalid, user, Ids.next(), 409);
      assertEquals("CONSENT_VERSION_CHANGED", lastReason);
      String first =
          domain.equals("realname")
              ? (String) input.get("frontUploadId")
              : (String) ((List<?>) input.get("materialUploadIds")).get(0);
      String receiptKey = "mujian:test:d2upload:" + first;
      StringRedisTemplate redis =
          app.getBean(org.springframework.data.redis.core.StringRedisTemplate.class);
      String original = redis.opsForValue().get(receiptKey);
      assertNotNull(original);
      try {
        redis.opsForValue().set(receiptKey, "invalid-receipt-json", Duration.ofMinutes(2));
        call("POST", "/api/me/" + domain + "-verifications", input, user, Ids.next(), 400);
        assertEquals("MATERIAL_REFERENCE_INVALID", lastReason);
        redis.delete(receiptKey);
        call("POST", "/api/me/" + domain + "-verifications", input, user, Ids.next(), 400);
        assertEquals("MATERIAL_REFERENCE_EXPIRED", lastReason);
      } finally {
        redis.opsForValue().set(receiptKey, original, Duration.ofHours(24));
      }
      store.failHead = true;
      try {
        call("POST", "/api/me/" + domain + "-verifications", input, user, Ids.next(), 503);
        assertEquals("OBJECT_STORAGE_UNAVAILABLE", lastReason);
      } finally {
        store.failHead = false;
      }
      String missingPath = "/api/admin/" + domain + "-verifications/" + Ids.next();
      for (String action : List.of("", "/sensitive-view", "/approve", "/reject")) {
        Object b = null;
        if (action.equals("/sensitive-view")) b = Map.of();
        else if (!action.isEmpty()) {
          Map<String, Object> review =
              view(
                  "caseId",
                  Ids.next(),
                  "expectedVersion",
                  0,
                  "expectedCaseVersion",
                  0,
                  "remark",
                  "虚构缺失记录");
          if (domain.equals("student") && action.equals("/approve")) review.put("validYears", 1);
          b = review;
        }
        call(action.isEmpty() ? "GET" : "POST", missingPath + action, b, null, Ids.next(), 404);
        assertEquals("RESOURCE_NOT_FOUND", lastReason);
      }
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM user_" + domain + "_verification WHERE user_id=?",
              Integer.class,
              user.id()));
    }
  }

  /** 验证所有相关 GET 拒绝未声明请求体。 */
  @Test
  void allGetRoutesRejectForbiddenBodiesWithinDeclaredContract() throws Exception {
    JsonNode contract =
        json.readTree(getClass().getResourceAsStream("/contracts/d2a.openapi.json"));
    for (Entry<String, JsonNode> entry : contract.path("paths").properties()) {
      if (!entry.getValue().has("get")) continue;
      String path = entry.getKey().replaceAll("\\{[^}]+}", Ids.next());
      if (path.equals("/api/public/rules")) path += "?scene=register";
      for (int size : List.of(1, 8192, 8193)) {
        HttpRequest request =
            HttpRequest.newBuilder(URI.create(base + path))
                .method("GET", HttpRequest.BodyPublishers.ofString("x".repeat(size)))
                .build();
        received("GET", path, http.send(request, HttpResponse.BodyHandlers.ofString()), 400);
        assertEquals("REQUEST_INVALID", lastReason);
      }
    }
  }

  /** 检查管理端拒绝损坏案件、旧版本命令和变更内容的重试。 */
  @Test
  void bothAdminDomainsRejectBrokenCasesStaleCommandsAndChangedReplay() throws Exception {
    for (String domain : List.of("realname", "student")) {
      D2ApplicationTest.User user = user();
      JsonNode made =
          call("POST", "/api/me/" + domain + "-verifications", body(user, domain), user, Ids.next(), 200);
      String id = made.path("application").path("id").asString(),
          path = "/api/admin/" + domain + "-verifications/" + id;
      JsonNode detail = call("GET", path, null, null, null, 200);
      String caseId = detail.path("caseId").asString();
      db.update("UPDATE admin_case SET buyer_user_id=? WHERE id=?", admin, caseId);
      try {
        call("GET", path, null, null, null, 500);
        assertEquals(domain.toUpperCase() + "_CASE_INCONSISTENT", lastReason);
        for (boolean approve : List.of(true, false)) {
          call(
              "POST",
              path + (approve ? "/approve" : "/reject"),
              reviewBody(detail, approve, domain),
              null,
              Ids.next(),
              500);
          assertEquals(domain.toUpperCase() + "_CASE_INCONSISTENT", lastReason);
        }
      } finally {
        db.update("UPDATE admin_case SET buyer_user_id=? WHERE id=?", user.id(), caseId);
      }
      for (boolean approve : List.of(true, false)) {
        Map<String, Object> stale = reviewBody(detail, approve, domain);
        stale.put("expectedVersion", 99);
        call("POST", path + (approve ? "/approve" : "/reject"), stale, null, Ids.next(), 409);
        assertEquals("VERSION_CONFLICT", lastReason);
      }
      String request = Ids.next();
      Map<String, Object> rejected = reviewBody(detail, false, domain);
      call("POST", path + "/reject", rejected, null, request, 200);
      for (boolean approve : List.of(true, false)) {
        Map<String, Object> changed = reviewBody(detail, approve, domain);
        changed.put("remark", "原编号变更内容");
        call("POST", path + (approve ? "/approve" : "/reject"), changed, null, request, 409);
        assertEquals("IDEMPOTENCY_KEY_REUSED", lastReason);
        call("POST", path + (approve ? "/approve" : "/reject"), changed, null, Ids.next(), 409);
        assertEquals(domain.toUpperCase() + "_ALREADY_REVIEWED", lastReason);
      }
      assertEquals(
          1,
          db.queryForObject(
              "SELECT COUNT(*) FROM admin_case_action WHERE target_id=?", Integer.class, id));
      assertEquals(
          "rejected",
          db.queryForObject(
              "SELECT verification_status FROM user_" + domain + "_verification WHERE id=?",
              String.class,
              id));
    }
  }

  /** 验证持有人锁超时后无部分写入且可用原键恢复。 */
  @Test
  void submissionOwnerLockTimeoutHasNoPartialWritesAndSameKeyRecovers() throws Exception {
    D2ApplicationTest isolated = new D2ApplicationTest();
    isolated.start(
        0, "--spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=1");
    ArrayList<Object> outcomes = new ArrayList<Object>();
    try {
      for (String domain : List.of("realname", "student")) {
        D2ApplicationTest.User user = isolated.user();
        Map<String, Object> input = isolated.body(user, domain);
        String key = Ids.next(), path = "/api/me/" + domain + "-verifications";
        try (Connection connection =
            isolated.app.getBean(javax.sql.DataSource.class).getConnection()) {
          connection.setAutoCommit(false);
          try (PreparedStatement statement =
              connection.prepareStatement("SELECT id FROM sys_user WHERE id=? FOR UPDATE")) {
            statement.setString(1, user.id());
            statement.executeQuery().close();
          }
          try {
            isolated.call("POST", path, input, user, key, 503);
            assertEquals("DATABASE_UNAVAILABLE", isolated.lastReason);
            assertEquals(
                0,
                isolated.db.queryForObject(
                    "SELECT COUNT(*) FROM user_"
                        + domain
                        + "_verification WHERE submit_request_id=?",
                    Integer.class,
                    key));
            assertEquals(
                0,
                isolated.db.queryForObject(
                    "SELECT COUNT(*) FROM admin_case WHERE buyer_user_id=?",
                    Integer.class,
                    user.id()));
          } finally {
            connection.rollback();
          }
        }
        JsonNode submitted = isolated.call("POST", path, input, user, key, 200);
        String application = submitted.path("application").path("id").asString();
        JsonNode replay = isolated.call("POST", path, input, user, key, 200);
        assertEquals("replayed", replay.path("outcome").asString());
        assertEquals(application, replay.path("application").path("id").asString());
        assertEquals(
            1,
            isolated.db.queryForObject(
                "SELECT COUNT(*) FROM user_" + domain + "_verification WHERE submit_request_id=?",
                Integer.class,
                key));
        assertEquals(
            1,
            isolated.db.queryForObject(
                "SELECT COUNT(*) FROM admin_case WHERE buyer_user_id=?", Integer.class, user.id()));
        outcomes.add(
            view(
                "domain",
                domain,
                "failureHttp",
                503,
                "failureReason",
                "DATABASE_UNAVAILABLE",
                "afterFailureApplications",
                0,
                "afterFailureCases",
                0,
                "sameRequestRetried",
                true,
                "afterRecoveryApplications",
                1,
                "afterRecoveryCases",
                1,
                "repeatOutcome",
                "replayed"));
      }
    } finally {
      isolated.stop();
    }
    String evidence = System.getenv("MJ_EVIDENCE_DIR");
    if (evidence != null)
      Files.writeString(
          Path.of(evidence, "c16-submit-lock-timeout.json"), json.writeValueAsString(outcomes));
  }

  /** 验证工厂资格占用与租赁身份绑定互斥。 */
  @Test
  void demanderBindingObservesConcurrentFactoryReservationWithoutAddingRole() throws Exception {
    String code = Ids.next();
    wx.identities.put(
        code,
        new WechatGateway.Identity(
            "test-app",
            code,
            null,
            "+8618" + String.format("%09d", Math.abs((long) code.hashCode()) % 1000000000L),
            "86"));
    JsonNode login =
        json.valueToTree(app.getBean(WechatPhoneLoginService.class).login(code, "fixture", rules));
    D2ApplicationTest.User user =
        new User(
            login.path("context").path("userId").asString(), login.path("sessionToken").asString());
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try (Connection connection = app.getBean(javax.sql.DataSource.class).getConnection()) {
      connection.setAutoCommit(false);
      try (PreparedStatement statement =
          connection.prepareStatement(
              "UPDATE sys_user SET register_status='factory_invite_locked' WHERE id=? AND"
                  + " register_status='new'")) {
        statement.setString(1, user.id());
        assertEquals(1, statement.executeUpdate());
      }
      Future<JsonNode> binding =
          pool.submit(() -> call("POST", "/api/me/roles/demander", Map.of(), user, null, 409));
      assertThrows(TimeoutException.class, () -> binding.get(200, TimeUnit.MILLISECONDS));
      connection.commit();
      binding.get(5, TimeUnit.SECONDS);
      assertEquals("IDENTITY_BINDING_CONFLICT", lastReason);
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM sys_user_role WHERE user_id=? AND enabled=1",
              Integer.class,
              user.id()));
      assertEquals(
          "factory_invite_locked",
          db.queryForObject(
              "SELECT register_status FROM sys_user WHERE id=?", String.class, user.id()));
    } finally {
      pool.shutdownNow();
    }
    String evidence = System.getenv("MJ_EVIDENCE_DIR");
    if (evidence != null)
      Files.writeString(
          Path.of(evidence, "c16-binding-race.json"),
          json.writeValueAsString(
              view(
                  "http",
                  409,
                  "reason",
                  "IDENTITY_BINDING_CONFLICT",
                  "activeRoleRows",
                  0,
                  "finalRegistrationStage",
                  "factory_invite_locked",
                  "boundary",
                  "actual A HTTP waits for real MySQL transaction reserving existing"
                      + " factory_invite_locked state; no B endpoint implemented or claimed")));
  }

  /** 模拟微信失败并验证恢复前不会建立账号。 */
  @Test
  void controlledWechatHttpFailuresDoNotCreateAccountsAndCanRecover() throws Exception {
    ArrayList<Object> outcomes = new ArrayList<Object>();
    List<String> scenarios =
        List.of(
            "login/http500",
            "login/bad_json",
            "login/too_large",
            "login/missing_fields",
            "login/credential_error",
            "login/upstream_error",
            "login/timeout",
            "token/http500",
            "token/missing_fields",
            "token/timeout",
            "phone/http500",
            "phone/wrong_appid",
            "phone/timeout",
            "phone/disconnect");
    int usersBefore = db.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class);
    int identitiesBefore =
        db.queryForObject("SELECT COUNT(*) FROM user_login_identity", Integer.class);
    int acceptancesBefore =
        db.queryForObject("SELECT COUNT(*) FROM user_rule_acceptance", Integer.class);
    Map<String, Object> input =
        view(
            "loginCode",
            "fictional-login-code",
            "phoneCode",
            "fictional-phone-code",
            "consentAccepted",
            true,
            "acceptedRules",
            rules);
    for (String scenario : scenarios) {
      String[] parts = scenario.split("/");
      try (WechatHttpFixture fixture =
          new com.mujian.backend.infrastructure.wechat.WechatHttpFixture(parts[0], parts[1])) {
        wx.delegate =
            fixture.gateway(
                json, app.getBean(com.mujian.backend.common.security.D2RateLimiter.class));
        int status = parts[1].equals("credential_error") ? 400 : 503;
        long start = System.nanoTime();
        call("POST", "/api/public/auth/wechat-phone-login", input, null, null, status);
        assertEquals(
            status == 400 ? "WECHAT_CREDENTIAL_INVALID" : "WECHAT_UPSTREAM_UNAVAILABLE",
            lastReason);
        Map<String, Integer> requests = fixture.requestCounts();
        assertEquals(1, requests.get("login"));
        if (parts[0].equals("login")) {
          assertFalse(requests.containsKey("token"));
          assertFalse(requests.containsKey("phone"));
        }
        if (parts[0].equals("token")) assertFalse(requests.containsKey("phone"));
        if (!parts[1].equals("disconnect"))
          assertTrue(requests.values().stream().allMatch(n -> n == 1), "No code retry");
        assertEquals(
            usersBefore, db.queryForObject("SELECT COUNT(*) FROM sys_user", Integer.class));
        assertEquals(
            identitiesBefore,
            db.queryForObject("SELECT COUNT(*) FROM user_login_identity", Integer.class));
        assertEquals(
            acceptancesBefore,
            db.queryForObject("SELECT COUNT(*) FROM user_rule_acceptance", Integer.class));
        outcomes.add(
            view(
                "scenario",
                scenario,
                "http",
                status,
                "reason",
                lastReason,
                "requests",
                requests,
                "elapsedMs",
                (System.nanoTime() - start) / 1000000,
                "noAccountIdentityAcceptanceWrites",
                true));
      } finally {
        wx.delegate = null;
      }
    }
    try (WechatHttpFixture fixture =
        new com.mujian.backend.infrastructure.wechat.WechatHttpFixture("none", "none")) {
      wx.delegate =
          fixture.gateway(
              json, app.getBean(com.mujian.backend.common.security.D2RateLimiter.class));
      JsonNode success =
          call("POST", "/api/public/auth/wechat-phone-login", input, null, null, 200);
      String user = success.path("context").path("userId").asString();
      assertFalse(user.isBlank());
      assertFalse(success.path("sessionToken").asString().isBlank());
      assertEquals(
          2,
          db.queryForObject(
              "SELECT COUNT(*) FROM user_login_identity WHERE user_id=?", Integer.class, user));
      assertEquals(
          5,
          db.queryForObject(
              "SELECT COUNT(*) FROM user_rule_acceptance WHERE user_id=?", Integer.class, user));
      outcomes.add(
          view(
              "scenario",
              "healthy_control",
              "http",
              200,
              "requests",
              fixture.requestCounts(),
              "identityRows",
              2,
              "acceptances",
              5));
    } finally {
      wx.delegate = null;
    }
    String evidence = System.getenv("MJ_EVIDENCE_DIR");
    if (evidence != null)
      Files.writeString(
          Path.of(evidence, "c16-controlled-wechat-http.json"),
          json.writeValueAsString(
              view(
                  "boundary",
                  "actual application HTTP plus production OfficialWechatGateway and JDK HTTP"
                      + " client against controlled loopback server; not natural WeChat failure",
                  "scenarios",
                  outcomes)));
  }

  /** 检查学生状态读取不会解密无关实名字段。 */
  @Test
  void studentCurrentStatusDoesNotDecryptUnneededRealnameFields() throws Exception {
    for (String realStatus : List.of("pending", "rejected", "verified")) {
      D2ApplicationTest.User user = user();
      String path = "/api/me/student-verifications/current";
      JsonNode empty = call("GET", path, null, user, null, 200);
      assertEquals("unverified", empty.path("realnameStatus").asString());
      assertTrue(empty.path("canSubmit").asBoolean());
      db.update("UPDATE sys_user SET user_status='disabled' WHERE id=?", user.id());
      JsonNode restricted = call("GET", path, null, user, null, 200);
      assertEquals("ACCOUNT_RESTRICTED", restricted.path("nextAction").asString());
      assertFalse(restricted.path("canSubmit").asBoolean());
      db.update("UPDATE sys_user SET user_status='active' WHERE id=?", user.id());
      String real =
          call("POST", "/api/me/realname-verifications", body(user, "realname"), user, Ids.next(), 200)
              .path("application")
              .path("id")
              .asString();
      if (!realStatus.equals("pending")) {
        String adminPath = "/api/admin/realname-verifications/" + real;
        JsonNode detail = call("GET", adminPath, null, null, null, 200);
        boolean approve = realStatus.equals("verified");
        call(
            "POST",
            adminPath + (approve ? "/approve" : "/reject"),
            reviewBody(detail, approve, "realname"),
            null,
            Ids.next(),
            200);
      }
      JsonNode healthy = call("GET", path, null, user, null, 200);
      assertEquals(realStatus, healthy.path("realnameStatus").asString());
      assertTrue(
          healthy.path("canSubmit").asBoolean(), "Realname status never gates student submission");
      String student =
          call("POST", "/api/me/student-verifications", body(user, "student"), user, Ids.next(), 200)
              .path("application")
              .path("id")
              .asString();
      String version =
          db.queryForObject(
              "SELECT encryption_key_version FROM user_realname_verification WHERE id=?",
              String.class,
              real);
      db.update(
          "UPDATE user_realname_verification SET encryption_key_version='c16-missing-key' WHERE"
              + " id=?",
          real);
      try {
        for (String accountStatus : List.of("active", "disabled")) {
          db.update("UPDATE sys_user SET user_status=? WHERE id=?", accountStatus, user.id());
          HttpResponse<String> response =
              http.send(
                  HttpRequest.newBuilder(URI.create(base + path))
                      .header("Authorization", "Bearer " + user.token())
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.ofString());
          JsonNode current = received("GET", path, response, response.statusCode());
          String evidence = System.getenv("MJ_EVIDENCE_DIR");
          if (evidence != null)
            Files.writeString(
                Path.of(
                    evidence,
                    response.statusCode() == 503
                        ? "c16-student-current-before.json"
                        : "c16-student-current-after.json"),
                json.writeValueAsString(
                    view(
                        "http",
                        response.statusCode(),
                        "body",
                        json.readTree(response.body()),
                        "realnameState",
                        realStatus,
                        "accountState",
                        accountStatus,
                        "boundary",
                        "isolated real HTTP/MySQL/Redis, fictional material")));
          assertEquals(
              200,
              response.statusCode(),
              "Student status must not require unrelated identity decryption");
          assertEquals(realStatus, current.path("realnameStatus").asString());
          assertEquals("pending", current.path("application").path("status").asString());
          assertFalse(current.path("canSubmit").asBoolean());
          assertEquals(
              accountStatus.equals("disabled") ? "ACCOUNT_RESTRICTED" : "WAIT_REVIEW",
              current.path("nextAction").asString());
        }
        call("GET", "/api/admin/student-verifications/" + student, null, null, null, 503);
        assertEquals("CRYPTO_KEY_UNAVAILABLE", lastReason);
        call(
            "POST",
            "/api/admin/student-verifications/" + student + "/sensitive-view",
            Map.of(),
            null,
            null,
            503);
        assertEquals("CRYPTO_KEY_UNAVAILABLE", lastReason);
        db.update("UPDATE sys_user SET user_status='locked' WHERE id=?", user.id());
        call("GET", path, null, user, null, 403);
        assertEquals("ACCOUNT_ACTION_RESTRICTED", lastReason);
      } finally {
        db.update(
            "UPDATE user_realname_verification SET encryption_key_version=? WHERE id=?",
            version,
            real);
        db.update("UPDATE sys_user SET user_status='active' WHERE id=?", user.id());
      }
      JsonNode detail =
          call("GET", "/api/admin/student-verifications/" + student, null, null, null, 200);
      assertFalse(detail.path("realNameMasked").asString().isBlank());
      assertEquals(realStatus, detail.path("realnameStatus").asString());
    }
  }

  /** 验证缺密钥时所有需要解密的读取均受限。 */
  @Test
  void missingEncryptionKeyFailsAllActualDecryptingHttpReads() throws Exception {
    D2ApplicationTest.User user = user();
    String request = Ids.next();
    Map<String, Object> input = body(user, "realname");
    String real =
        call("POST", "/api/me/realname-verifications", input, user, request, 200)
            .path("application")
            .path("id")
            .asString();
    String student =
        call("POST", "/api/me/student-verifications", body(user, "student"), user, Ids.next(), 200)
            .path("application")
            .path("id")
            .asString();
    String version =
        db.queryForObject(
            "SELECT encryption_key_version FROM user_realname_verification WHERE id=?",
            String.class,
            real);
    db.update(
        "UPDATE user_realname_verification SET encryption_key_version='c14-unavailable-key' WHERE"
            + " id=?",
        real);
    try {
      for (String path :
          List.of(
              "/api/me/realname-verifications/current",
              "/api/me/realname-verifications/by-request/" + request,
              "/api/admin/realname-verifications?keyword=RV" + real,
              "/api/admin/realname-verifications/" + real,
              "/api/admin/student-verifications/" + student)) {
        call("GET", path, null, user, null, 503);
        assertEquals("CRYPTO_KEY_UNAVAILABLE", lastReason);
      }
      call("POST", "/api/me/realname-verifications", input, user, request, 503);
      assertEquals("CRYPTO_KEY_UNAVAILABLE", lastReason);
      for (String path :
          List.of(
              "/api/admin/realname-verifications/" + real + "/sensitive-view",
              "/api/admin/student-verifications/" + student + "/sensitive-view")) {
        call("POST", path, Map.of(), null, null, 503);
        assertEquals("CRYPTO_KEY_UNAVAILABLE", lastReason);
      }
    } finally {
      db.update(
          "UPDATE user_realname_verification SET encryption_key_version=? WHERE id=?",
          version,
          real);
    }
    call("GET", "/api/me/realname-verifications/current", null, user, null, 200);
  }

  /** 检查对象写入、签名及访问失败对审核结果无影响。 */
  @Test
  void objectPutSignAndAdminPermissionsFailThroughHttpWithoutReviewEffects() throws Exception {
    for (String domain : List.of("realname", "student")) {
      D2ApplicationTest.User user = user();
      store.failPut = true;
      try {
        upload(user, domain, domain.equals("realname") ? "front" : null, 503);
        assertEquals("OBJECT_STORAGE_UNAVAILABLE", lastReason);
      } finally {
        store.failPut = false;
      }
      String id =
          call("POST", "/api/me/" + domain + "-verifications", body(user, domain), user, Ids.next(), 200)
              .path("application")
              .path("id")
              .asString();
      String path = "/api/admin/" + domain + "-verifications/" + id;
      JsonNode detail = call("GET", path, null, null, null, 200);
      store.failSign = true;
      try {
        call("POST", path + "/sensitive-view", Map.of(), null, null, 503);
        assertEquals("OBJECT_STORAGE_UNAVAILABLE", lastReason);
      } finally {
        store.failSign = false;
      }
      List<Map<String, Object>> grants =
          db.queryForList(
              "SELECT * FROM admin_account_role WHERE admin_account_id=(SELECT id FROM"
                  + " admin_account WHERE user_id=?)",
              admin);
      db.update(
          "DELETE FROM admin_account_role WHERE admin_account_id=(SELECT id FROM admin_account"
              + " WHERE user_id=?)",
          admin);
      try {
        call("GET", "/api/admin/" + domain + "-verifications", null, null, null, 403);
        assertEquals("PERMISSION_DENIED", lastReason);
        call("POST", path + "/approve", reviewBody(detail, true, domain), null, Ids.next(), 403);
        assertEquals("PERMISSION_DENIED", lastReason);
      } finally {
        for (Map<String, Object> g : grants)
          db.update(
              "INSERT INTO admin_account_role ("
                  + String.join(",", g.keySet())
                  + ") VALUES ("
                  + String.join(",", Collections.nCopies(g.size(), "?"))
                  + ")",
              g.values().toArray());
      }
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM admin_case_action WHERE target_id=?", Integer.class, id));
      assertEquals(
          "pending",
          db.queryForObject(
              "SELECT verification_status FROM user_" + domain + "_verification WHERE id=?",
              String.class,
              id));
    }
  }

  /** 通过真实请求验证规则和身份失效时回滚注册。 */
  @Test
  void ruleAndIdentityGuardsUseActualHttpAndRollbackRegistration() throws Exception {
    D2ApplicationTest.User user = user();
    String code = Ids.next();
    Map<String, Object> identity =
        db.queryForMap(
            "SELECT app_id,openid FROM user_login_identity WHERE user_id=? AND"
                + " identity_type='wechat_miniapp'",
            user.id());
    String phone =
        db.queryForObject(
            "SELECT phone FROM user_login_identity WHERE user_id=? AND identity_type='phone'",
            String.class,
            user.id());
    wx.identities.put(
        code,
        new WechatGateway.Identity(
            (String) identity.get("app_id"), (String) identity.get("openid"), null, phone, "86"));
    Map<String, Object> login =
        view(
            "loginCode",
            code,
            "phoneCode",
            "fixture",
            "consentAccepted",
            true,
            "acceptedRules",
            rules);
    String path = "/api/public/auth/wechat-phone-login";
    call("GET", "/api/public/rules/" + Ids.next(), null, null, null, 404);
    assertEquals("RESOURCE_NOT_FOUND", lastReason);
    db.update("UPDATE sys_user SET user_status='locked' WHERE id=?", user.id());
    try {
      call("POST", path, login, null, null, 403);
      assertEquals("ACCOUNT_ACTION_RESTRICTED", lastReason);
    } finally {
      db.update("UPDATE sys_user SET user_status='active' WHERE id=?", user.id());
    }
    db.update("UPDATE sys_user_role SET enabled=0 WHERE user_id=?", user.id());
    try {
      call("POST", path, login, null, null, 500);
      assertEquals("ACCOUNT_CONTEXT_INCONSISTENT", lastReason);
    } finally {
      db.update("UPDATE sys_user_role SET enabled=1 WHERE user_id=?", user.id());
    }
    D2ApplicationTest.User other = user();
    String otherPhone =
        db.queryForObject(
            "SELECT phone FROM user_login_identity WHERE user_id=? AND identity_type='phone'",
            String.class,
            other.id());
    wx.identities.put(
        code,
        new WechatGateway.Identity(
            (String) identity.get("app_id"),
            (String) identity.get("openid"),
            null,
            otherPhone,
            "86"));
    call("POST", path, login, null, null, 409);
    assertEquals("LOGIN_IDENTITY_CONFLICT", lastReason);
    wx.identities.put(
        code,
        new WechatGateway.Identity(
            (String) identity.get("app_id"), (String) identity.get("openid"), null, phone, "86"));
    ArrayList<AcceptedRule> badRules = new ArrayList<>(rules);
    badRules.set(
        0, new RuleAcceptanceService.AcceptedRule(rules.get(0).documentId(), "c14-obsolete"));
    login.put("acceptedRules", badRules);
    call("POST", path, login, null, null, 409);
    assertEquals("RULE_VERSION_CHANGED", lastReason);
    login.put("acceptedRules", rules);
    String document = rules.get(0).documentId();
    Map<String, Object> row =
        db.queryForMap(
            "SELECT effective_at,content_hash FROM platform_rule_document WHERE id=?", document);
    try {
      db.update(
          "UPDATE platform_rule_document SET effective_at=? WHERE id=?",
          now(clock).plusDays(1),
          document);
      for (String target :
          List.of("/api/public/rules?scene=register", path, "/api/me/roles/demander")) {
        call(
            target.startsWith("/api/public/rules") ? "GET" : "POST",
            target,
            target.equals(path) ? login : target.startsWith("/api/me/") ? Map.of() : null,
            user,
            null,
            503);
        assertEquals("RULE_DOCUMENT_UNAVAILABLE", lastReason);
      }
      db.update(
          "UPDATE platform_rule_document SET effective_at=?,content_hash=? WHERE id=?",
          row.get("effective_at"),
          "0".repeat(64),
          document);
      call("POST", "/api/me/roles/demander", Map.of(), user, null, 409);
      assertEquals("RULE_VERSION_CHANGED", lastReason);
    } finally {
      db.update(
          "UPDATE platform_rule_document SET effective_at=?,content_hash=? WHERE id=?",
          row.get("effective_at"),
          row.get("content_hash"),
          document);
    }
    db.update("UPDATE sys_user_role SET enabled=0 WHERE user_id=?", user.id());
    db.update("UPDATE sys_user SET register_status='factory_invite_locked' WHERE id=?", user.id());
    try {
      call("POST", "/api/me/roles/demander", Map.of(), user, null, 409);
      assertEquals("IDENTITY_BINDING_CONFLICT", lastReason);
    } finally {
      db.update("UPDATE sys_user_role SET enabled=1 WHERE user_id=?", user.id());
      db.update("UPDATE sys_user SET register_status='demander_bound' WHERE id=?", user.id());
    }
    assertEquals(
        2,
        db.queryForObject(
            "SELECT COUNT(*) FROM user_login_identity WHERE user_id=?", Integer.class, user.id()));
    assertEquals(
        phone, db.queryForObject("SELECT phone FROM sys_user WHERE id=?", String.class, user.id()));
  }

  /** 检查奖励前置条件不满足时实名审批完整回滚。 */
  @Test
  void activeRewardGuardBlocksRealnameHttpApprovalWithoutPartialWrites() throws Exception {
    D2ApplicationTest.User user = user();
    String id =
        call("POST", "/api/me/realname-verifications", body(user, "realname"), user, Ids.next(), 200)
            .path("application")
            .path("id")
            .asString();
    String path = "/api/admin/realname-verifications/" + id;
    JsonNode detail = call("GET", path, null, null, null, 200);
    String template = Ids.next(), rule = Ids.next();
    LocalDateTime at = now(clock);
    db.update(
        "INSERT INTO coupon_template"
            + " (id,template_code,template_name,template_version,coupon_type,face_amount_fen,threshold_rent_amount_fen,applicable_scope_type,cost_bearer,validity_type,valid_days,refund_return_policy,return_extension_days,issued_count,template_status,created_by_admin_user_id,created_at,updated_at)"
            + " VALUES"
            + " (?,?,'虚构HTTP门禁测试',1,'fixed_amount',100,0,'all','shared_by_settlement_ratio','relative_days',1,'return_with_extension',7,0,'draft',?,?,?)",
        template,
        "C14_" + template,
        admin,
        at,
        at);
    db.update(
        "INSERT INTO coupon_reward_rule"
            + " (id,event_type,rule_version,template_id,status,activated_at,created_by_admin_user_id,created_at)"
            + " VALUES (?,'first_realname_verified',?,?,'active',?,?,?)",
        rule,
        db.queryForObject(
            "SELECT COALESCE(MAX(rule_version),0)+1 FROM coupon_reward_rule WHERE"
                + " event_type='first_realname_verified'",
            Integer.class),
        template,
        at,
        admin,
        at);
    try {
      call("POST", path + "/approve", reviewBody(detail, true, "realname"), null, Ids.next(), 503);
      assertEquals("REWARD_PROCESSOR_UNAVAILABLE", lastReason);
      assertEquals(
          "pending",
          db.queryForObject(
              "SELECT verification_status FROM user_realname_verification WHERE id=?",
              String.class,
              id));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM admin_case_action WHERE target_id=?", Integer.class, id));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM coupon_reward_record WHERE user_id=?", Integer.class, user.id()));
    } finally {
      db.update(
          "UPDATE coupon_reward_rule SET status='ended',ended_at=activated_at WHERE id=?", rule);
    }
  }

  /** 核对公开 JSON 的中文编码及内容类型。 */
  @Test
  void publicJsonDeclaresUtf8AndKeepsChineseBytes() throws Exception {
    for (String path :
        List.of("/api/public/rules?scene=register", "/api/public/rules?scene=invalid")) {
      HttpResponse<String> response =
          http.send(
              HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
              HttpResponse.BodyHandlers.ofString(java.nio.charset.StandardCharsets.UTF_8));
      assertTrue(
          response
              .headers()
              .firstValue("Content-Type")
              .orElse("")
              .toLowerCase()
              .contains("charset=utf-8"));
      received("GET", path, response, path.endsWith("invalid") ? 400 : 200);
      if (path.endsWith("register")) {
        assertEquals("成功", json.readTree(response.body()).path("message").asString());
        assertTrue(response.body().contains("隐私政策"));
      }
    }
  }

  /** 覆盖清理年龄、异常分页及历史引用。 */
  @Test
  void cleanupThresholdPaginationFailureAndHistoricalReferences() throws Exception {
    D2ApplicationTest.User user = user();
    VerificationMaterials materials = app.getBean(VerificationMaterials.class);
    VerificationMaterialCleanup cleanup = app.getBean(VerificationMaterialCleanup.class);
    Map<String, Object> retainedBody = body(user, "student");
    call("POST", "/api/me/student-verifications", retainedBody, user, Ids.next(), 200);
    Instant original = clock.at;
    ArrayList<String> keys = new ArrayList<String>();
    try {
      for (int i = 0; i < 5; i++)
        keys.add(
            materials
                .upload(user.id(), "student_card", "student-material-v1", true, png())
                .objectKey());
      clock.at = original.plus(Duration.ofHours(48)).minusMillis(1);
      cleanup.scan(false);
      for (String k : keys) assertTrue(store.objects.containsKey(k));
      clock.at = original.plus(Duration.ofHours(48));
      Report report = cleanup.scan(true);
      assertTrue(report.completed());
      for (String k : keys) assertTrue(store.objects.containsKey(k));
      store.failDelete = true;
      Report failed = cleanup.scan(false);
      assertTrue(failed.unknown() >= 5);
      store.failDelete = false;
      Report done = cleanup.scan(false);
      assertTrue(done.deleted() >= 5);
      assertTrue(done.completed());
      for (String k : keys) assertFalse(store.objects.containsKey(k));
      assertTrue(store.objects.size() > 0, "all submitted historical objects retained");
    } finally {
      clock.at = original;
      store.failDelete = false;
    }
  }

  /** 覆盖六种登录匹配路径及停用绑定。 */
  @Test
  void sixLoginMatchesAndDisabledBindingGuard() {
    WechatPhoneLoginService login = app.getBean(WechatPhoneLoginService.class);
    LoginMapper loginMapper = app.getBean(com.mujian.backend.account.mapper.LoginMapper.class);
    String suffix = Ids.next(),
        phone = "+8615" + String.format("%09d", Math.abs(suffix.hashCode()) % 1000000000L),
        code = Ids.next();
    wx.identities.put(code, new WechatGateway.Identity("test-app", code, null, phone, "86"));
    JsonNode first = json.valueToTree(login.login(code, "fixture", rules));
    String user = first.path("context").path("userId").asString();
    assertEquals(
        user,
        json.valueToTree(login.login(code, "fixture", rules))
            .path("context")
            .path("userId")
            .asString());
    String another = Ids.next();
    wx.identities.put(another, new WechatGateway.Identity("test-app", another, null, phone, "86"));
    assertEquals(
        user,
        json.valueToTree(login.login(another, "fixture", rules))
            .path("context")
            .path("userId")
            .asString());
    String unbound = Ids.next(),
        onlyWx = Ids.next(),
        newPhone = "+8616" + String.format("%09d", Math.abs(unbound.hashCode()) % 1000000000L);
    loginMapper.insertRegisteredUser(unbound, now(clock));
    loginMapper.insertWechatIdentity(Ids.next(), unbound, "test-app", onlyWx, null, now(clock));
    wx.identities.put(onlyWx, new WechatGateway.Identity("test-app", onlyWx, null, newPhone, "86"));
    assertEquals(
        unbound,
        json.valueToTree(login.login(onlyWx, "fixture", rules))
            .path("context")
            .path("userId")
            .asString());
    wx.identities.put(
        code,
        new WechatGateway.Identity(
            "test-app",
            code,
            null,
            "+8617000" + String.format("%06d", Math.abs(suffix.hashCode()) % 1000000),
            "86"));
    JsonNode retained = json.valueToTree(login.login(code, "fixture", rules));
    assertEquals("PHONE_UNCHANGED", retained.path("noticeCode").asString());
    assertEquals(phone, loginMapper.selectUserPhoneById(user));
    wx.identities.put(code, new WechatGateway.Identity("test-app", code, null, newPhone, "86"));
    assertEquals(
        "LOGIN_IDENTITY_CONFLICT",
        assertThrows(ApiFailure.class, () -> login.login(code, "fixture", rules)).reason);
    db.update("UPDATE sys_user SET user_status='disabled' WHERE id=?", user);
    wx.identities.put(code, new WechatGateway.Identity("test-app", code, null, phone, "86"));
    assertEquals(
        "restricted",
        json.valueToTree(login.login(code, "fixture", rules))
            .path("context")
            .path("accessMode")
            .asString());
    String added = Ids.next();
    wx.identities.put(added, new WechatGateway.Identity("test-app", added, null, phone, "86"));
    assertEquals(
        "ACCOUNT_ACTION_RESTRICTED",
        assertThrows(ApiFailure.class, () -> login.login(added, "fixture", rules)).reason);
    assertNull(loginMapper.selectWechatIdentityByAppAndOpenId("test-app", added));
  }

  /** 验证首次登录与两类提交的并发行为。 */
  @Test
  void concurrentFirstLoginsAndBothSubmissionDomains() throws Exception {
    String common = Ids.next(),
        phone = "+8618" + String.format("%09d", Math.abs(common.hashCode()) % 1000000000L);
    String a = Ids.next(), b = Ids.next();
    for (String c : List.of(a, b))
      wx.identities.put(c, new WechatGateway.Identity("test-app", c, null, phone, "86"));
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CyclicBarrier barrier = new CyclicBarrier(2);
    try {
      Future<String> fa =
          pool.submit(
              () -> {
                barrier.await();
                return json.valueToTree(
                        app.getBean(WechatPhoneLoginService.class).login(a, "fixture", rules))
                    .path("context")
                    .path("userId")
                    .asString();
              });
      Future<String> fb =
          pool.submit(
              () -> {
                barrier.await();
                return json.valueToTree(
                        app.getBean(WechatPhoneLoginService.class).login(b, "fixture", rules))
                    .path("context")
                    .path("userId")
                    .asString();
              });
      assertEquals(fa.get(15, TimeUnit.SECONDS), fb.get(15, TimeUnit.SECONDS));
      assertEquals(
          1,
          db.queryForObject(
              "SELECT COUNT(*) FROM user_login_identity WHERE active_phone_guard=?",
              Integer.class,
              phone));
      for (String d : List.of("realname", "student")) {
        D2ApplicationTest.User user = user();
        Map<String, Object> input = body(user, d);
        CyclicBarrier start = new CyclicBarrier(2);
        ArrayList<Future<JsonNode>> runs = new ArrayList<Future<JsonNode>>();
        for (int n = 0; n < 2; n++)
          runs.add(
              pool.submit(
                  () -> {
                    start.await();
                    Object value =
                        d.equals("realname")
                            ? app.getBean(RealnameSubmissionService.class)
                                .submit(
                                    user.id(),
                                    Ids.next(),
                                    json.convertValue(
                                        input, RealnameSubmissionService.SubmitRequest.class))
                            : app.getBean(StudentSubmissionService.class)
                                .submit(
                                    user.id(),
                                    Ids.next(),
                                    json.convertValue(
                                        input, StudentSubmissionService.SubmitRequest.class));
                    return json.valueToTree(value);
                  }));
        JsonNode first = runs.get(0).get(15, TimeUnit.SECONDS);
        JsonNode second = runs.get(1).get(15, TimeUnit.SECONDS);
        assertEquals(first.path("application").path("id"), second.path("application").path("id"));
        assertEquals(
            Set.of("created", "existing_pending"),
            Set.of(first.path("outcome").asString(), second.path("outcome").asString()));
      }
    } finally {
      pool.shutdownNow();
    }
  }

  /** 验证同微信注册与绑定的并发幂等。 */
  @Test
  void sameWechatRegistrationAndDemanderBindingAreConcurrentIdempotent() throws Exception {
    WechatPhoneLoginService login = app.getBean(WechatPhoneLoginService.class);
    DemanderBindingService binding = app.getBean(DemanderBindingService.class);
    ExecutorService pool = Executors.newFixedThreadPool(4);
    try {
      for (int round = 0; round < 3; round++) {
        String code = Ids.next();
        String phone =
            "+8619" + String.format("%09d", Math.abs((long) code.hashCode()) % 1000000000L);
        wx.identities.put(code, new WechatGateway.Identity("test-app", code, null, phone, "86"));
        CyclicBarrier barrier = new CyclicBarrier(4);
        ArrayList<Future<String>> registrations = new ArrayList<Future<String>>();
        for (int i = 0; i < 4; i++)
          registrations.add(
              pool.submit(
                  () -> {
                    barrier.await();
                    return json.valueToTree(login.login(code, "fixture", rules))
                        .path("context")
                        .path("userId")
                        .asString();
                  }));
        HashSet<String> users = new HashSet<String>();
        for (Future<String> result : registrations) users.add(result.get(20, TimeUnit.SECONDS));
        assertEquals(1, users.size());
        String user = users.iterator().next();
        assertEquals(
            2,
            db.queryForObject(
                "SELECT COUNT(*) FROM user_login_identity WHERE user_id=?", Integer.class, user));
        assertEquals(
            5,
            db.queryForObject(
                "SELECT COUNT(*) FROM user_rule_acceptance WHERE user_id=?", Integer.class, user));
        CyclicBarrier bindBarrier = new CyclicBarrier(4);
        ArrayList<Future<JsonNode>> bindings = new ArrayList<Future<JsonNode>>();
        for (int i = 0; i < 4; i++)
          bindings.add(
              pool.submit(
                  () -> {
                    bindBarrier.await();
                    return json.valueToTree(binding.bind(user));
                  }));
        for (Future<JsonNode> result : bindings)
          assertEquals("demander", result.get(20, TimeUnit.SECONDS).path("roleCode").asString());
        assertEquals(
            1,
            db.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role WHERE user_id=?", Integer.class, user));
        assertEquals(
            "demander_bound",
            db.queryForObject(
                "SELECT register_status FROM sys_user WHERE id=?", String.class, user));
      }
    } finally {
      pool.shutdownNow();
    }
  }

  /** 验证锁定管理员及系统账号无法通过其他身份入口登录。 */
  @Test
  void lockedAdminAndSystemCannotLoginThroughAnyExistingIdentityMatch() {
    WechatPhoneLoginService login = app.getBean(WechatPhoneLoginService.class);
    LoginMapper loginMapper = app.getBean(com.mujian.backend.account.mapper.LoginMapper.class);
    TransactionTemplate tx =
        new org.springframework.transaction.support.TransactionTemplate(
            app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    for (String kind : List.of("locked", "admin", "system")) {
      for (String match : List.of("wechat", "phone", "both")) {
        tx.executeWithoutResult(
            status -> {
              try {
                String user =
                    kind.equals("system")
                        ? com.mujian.backend.common.support.SystemIdentity.SYSTEM_USER_ID
                        : kind.equals("admin") ? admin : Ids.next();
                if (kind.equals("locked")) {
                  loginMapper.insertRegisteredUser(user, now(clock));
                  db.update("UPDATE sys_user SET user_status='locked' WHERE id=?", user);
                }
                String code = Ids.next();
                String phone =
                    "+8617" + String.format("%09d", Math.abs((long) code.hashCode()) % 1000000000L);
                if (!match.equals("phone"))
                  loginMapper.insertWechatIdentity(Ids.next(), user, "test-app", code, null, now(clock));
                if (!match.equals("wechat"))
                  loginMapper.insertPhoneIdentity(Ids.next(), user, phone, "86", now(clock));
                wx.identities.put(
                    code, new WechatGateway.Identity("test-app", code, null, phone, "86"));
                int before =
                    db.queryForObject(
                        "SELECT COUNT(*) FROM user_login_identity WHERE user_id=?",
                        Integer.class,
                        user);
                int acceptedBefore =
                    db.queryForObject(
                        "SELECT COUNT(*) FROM user_rule_acceptance WHERE user_id=?",
                        Integer.class,
                        user);
                assertEquals(
                    "ACCOUNT_ACTION_RESTRICTED",
                    assertThrows(ApiFailure.class, () -> login.login(code, "fixture", rules))
                        .reason,
                    kind + "/" + match);
                assertEquals(
                    before,
                    db.queryForObject(
                        "SELECT COUNT(*) FROM user_login_identity WHERE user_id=?",
                        Integer.class,
                        user));
                assertEquals(
                    acceptedBefore,
                    db.queryForObject(
                        "SELECT COUNT(*) FROM user_rule_acceptance WHERE user_id=?",
                        Integer.class,
                        user));
              } finally {
                status.setRollbackOnly();
              }
            });
      }
    }
  }

  /** 逐个注入写入故障并核对事务回滚。 */
  @Test
  void faultsRollBackEachLocalWriteBoundary() throws Exception {
    for (String d : List.of("realname", "student")) {
      D2ApplicationTest.User user = user();
      Map<String, Object> input = body(user, d);
      String key = Ids.next();
      app.getBean(Fault.class).table = "admin_case_relation";
      try {
        assertThrows(
            RuntimeException.class,
            () -> {
              if (d.equals("realname"))
                app.getBean(RealnameSubmissionService.class)
                    .submit(
                        user.id(),
                        key,
                        json.convertValue(input, RealnameSubmissionService.SubmitRequest.class));
              else
                app.getBean(StudentSubmissionService.class)
                    .submit(
                        user.id(),
                        key,
                        json.convertValue(input, StudentSubmissionService.SubmitRequest.class));
            });
        assertEquals(
            0,
            db.queryForObject(
                "SELECT COUNT(*) FROM user_" + d + "_verification WHERE user_id=?",
                Integer.class,
                user.id()));
        assertEquals(
            0,
            db.queryForObject(
                "SELECT COUNT(*) FROM admin_case WHERE buyer_user_id=?", Integer.class, user.id()));
      } finally {
        app.getBean(Fault.class).table = null;
      }
      JsonNode made = call("POST", "/api/me/" + d + "-verifications", input, user, key, 200);
      String id = made.path("application").path("id").asString();
      JsonNode detail =
          call("GET", "/api/admin/" + d + "-verifications/" + id, null, null, null, 200);
      for (String table :
          d.equals("realname")
              ? List.of("admin_case_action", "admin_operation_audit", "coupon_reward_record")
              : List.of("admin_case_action", "admin_operation_audit")) {
        app.getBean(Fault.class).table = table;
        try {
          assertThrows(
              RuntimeException.class,
              () -> {
                if (d.equals("realname"))
                  app.getBean(RealnameReviewService.class)
                      .review(
                          admin,
                          id,
                          Ids.next(),
                          true,
                          new RealnameReviewService.ReviewCommand(
                              detail.path("caseId").asString(), 0, 0, null, null),
                          Ids.next());
                else
                  app.getBean(StudentReviewService.class)
                      .review(
                          admin,
                          id,
                          Ids.next(),
                          true,
                          new StudentReviewService.ReviewCommand(
                              detail.path("caseId").asString(), 0, 0, null, 3),
                          Ids.next());
              });
          assertEquals(
              "pending",
              db.queryForObject(
                  "SELECT verification_status FROM user_" + d + "_verification WHERE id=?",
                  String.class,
                  id));
          assertEquals(
              "pending",
              db.queryForObject(
                  "SELECT case_status FROM admin_case WHERE id=?",
                  String.class,
                  detail.path("caseId").asString()));
          assertEquals(
              0,
              db.queryForObject(
                  "SELECT COUNT(*) FROM admin_case_action WHERE target_id=?", Integer.class, id));
        } finally {
          app.getBean(Fault.class).table = null;
        }
      }
      int before = store.signed;
      app.getBean(Fault.class).table = "admin_operation_audit";
      try {
        assertThrows(
            RuntimeException.class,
            () -> {
              if (d.equals("realname"))
                app.getBean(VerificationSensitiveViews.class).realname(admin, id, Ids.next());
              else app.getBean(VerificationSensitiveViews.class).student(admin, id, Ids.next());
            });
        assertEquals(before, store.signed, "No signatures issued before committed audit");
      } finally {
        app.getBean(Fault.class).table = null;
      }
    }
  }

  /** 验证所有者隔离、传输及年限参数拒绝。 */
  @Test
  void ownerIsolationTransportAndYearRejections() throws Exception {
    for (String d : List.of("realname", "student")) {
      D2ApplicationTest.User user = user();
      D2ApplicationTest.User other = user();
      Map<String, Object> input = body(user, d);
      String key = Ids.next();
      JsonNode made = call("POST", "/api/me/" + d + "-verifications", input, user, key, 200);
      call("GET", "/api/me/" + d + "-verifications/by-request/" + key, null, other, null, 404);
      call("POST", "/api/me/" + d + "-verifications", input, other, key, 404);
      call("GET", "/api/me/" + d + "-verifications/by-request/" + Ids.next(), null, user, null, 404);
      String id = made.path("application").path("id").asString();
      JsonNode detail =
          call("GET", "/api/admin/" + d + "-verifications/" + id, null, null, null, 200);
      LinkedHashMap<String, Object> forged = new LinkedHashMap<>(input);
      forged.put("userId", other.id());
      call("POST", "/api/me/" + d + "-verifications", forged, user, Ids.next(), 400);
      if (d.equals("student")) {
        for (Object years : Arrays.asList(null, 0, 6, 1.5, "2")) {
          Map<String, Object> review = reviewBody(detail, true, d);
          review.put("validYears", years);
          call(
              "POST",
              "/api/admin/student-verifications/" + id + "/approve",
              review,
              null,
              Ids.next(),
              400);
        }
        Map<String, Object> reject = reviewBody(detail, false, d);
        reject.put("validYears", 1);
        call(
            "POST",
            "/api/admin/student-verifications/" + id + "/reject",
            reject,
            null,
            Ids.next(),
            400);
      }
      db.update("UPDATE sys_user SET user_status='disabled' WHERE id=?", user.id());
      call("GET", "/api/me/" + d + "-verifications/current", null, user, null, 200);
      assertEquals(
          "replayed",
          call("POST", "/api/me/" + d + "-verifications", input, user, key, 200)
              .path("outcome")
              .asString());
      call("POST", "/api/me/" + d + "-verifications", input, user, Ids.next(), 403);
    }
  }

  /** 验证读取、审批和实名敏感权限的独立组合。 */
  @Test
  void allPermissionCombinationsAndSeparateRealnameAuthorization() throws Exception {
    D2ApplicationTest.User user = user();
    JsonNode real =
        call("POST", "/api/me/realname-verifications", body(user, "realname"), user, Ids.next(), 200);
    JsonNode student =
        call("POST", "/api/me/student-verifications", body(user, "student"), user, Ids.next(), 200);
    List<Map<String, Object>> grants =
        db.queryForList(
            "SELECT * FROM admin_role_menu WHERE admin_menu_id IN (SELECT id FROM admin_menu WHERE"
                + " permission_code LIKE 'admin:realname:%' OR permission_code LIKE"
                + " 'admin:student:%')");
    try {
      for (String d : List.of("realname", "student")) {
        String id =
            (d.equals("realname") ? real : student).path("application").path("id").asString();
        String path = "/api/admin/" + d + "-verifications/" + id;
        for (int mask = 0; mask < 8; mask++) {
          db.update(
              "DELETE FROM admin_role_menu WHERE admin_menu_id IN (SELECT id FROM admin_menu WHERE"
                  + " permission_code LIKE 'admin:"
                  + d
                  + ":%')");
          for (Map<String, Object> g : grants) {
            String permission =
                db.queryForObject(
                    "SELECT permission_code FROM admin_menu WHERE id=?",
                    String.class,
                    g.get("admin_menu_id"));
            int bit = permission.endsWith(":read") ? 1 : permission.endsWith(":sensitive") ? 2 : 4;
            if (permission.startsWith("admin:" + d + ":") && (mask & bit) != 0) restoreGrant(g);
          }
          call("GET", path, null, null, null, (mask & 1) != 0 ? 200 : 403);
          call("POST", path + "/sensitive-view", Map.of(), null, null, (mask & 3) == 3 ? 200 : 403);
          Map<String, Object> command =
              view(
                  "caseId",
                  Ids.next(),
                  "expectedVersion",
                  99,
                  "expectedCaseVersion",
                  99,
                  "remark",
                  "权限验证");
          // A deliberately stale command distinguishes authorized review (409) from denied review
          // (403), without changing the row.
          call("POST", path + "/reject", command, null, Ids.next(), (mask & 4) != 0 ? 409 : 403);
        }
      }
      db.update(
          "DELETE FROM admin_role_menu WHERE admin_menu_id IN (SELECT id FROM admin_menu WHERE"
              + " permission_code LIKE 'admin:realname:%')");
      JsonNode limited =
          call(
              "POST",
              "/api/admin/student-verifications/"
                  + student.path("application").path("id").asString()
                  + "/sensitive-view",
              Map.of(),
              null,
              null,
              200);
      assertEquals("not_authorized", limited.path("realnameView").path("access").asString());
      assertFalse(limited.toString().contains("11010519491231002X"));
    } finally {
      db.update(
          "DELETE FROM admin_role_menu WHERE admin_menu_id IN (SELECT id FROM admin_menu WHERE"
              + " permission_code LIKE 'admin:realname:%' OR permission_code LIKE"
              + " 'admin:student:%')");
      for (Map<String, Object> grant : grants) restoreGrant(grant);
    }
  }

  /** 恢复用例临时调整的授权。 */
  void restoreGrant(Map<String, Object> grant) {
    db.update(
        "INSERT INTO admin_role_menu ("
            + String.join(",", grant.keySet())
            + ") VALUES ("
            + String.join(",", Collections.nCopies(grant.size(), "?"))
            + ")",
        grant.values().toArray());
  }

  /** 验证过期任务不会覆盖重新提交的申请。 */
  @Test
  void expiryTaskRacesWithResubmissionWithoutChangingNewApplication() throws Exception {
    D2ApplicationTest.User user = user();
    String own = "/api/me/student-verifications";
    JsonNode made = call("POST", own, body(user, "student"), user, Ids.next(), 200);
    String old = made.path("application").path("id").asString();
    JsonNode detail = call("GET", "/api/admin/student-verifications/" + old, null, null, null, 200);
    call(
        "POST",
        "/api/admin/student-verifications/" + old + "/approve",
        reviewBody(detail, true, "student"),
        null,
        Ids.next(),
        200);
    Instant original = clock.at;
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      clock.at =
          db.queryForObject(
                  "SELECT expire_at FROM user_student_verification WHERE id=?",
                  java.sql.Timestamp.class,
                  old)
              .toLocalDateTime()
              .atZone(ZONE)
              .toInstant();
      // Query reflects expiry even before the background task changes stored status.
      JsonNode current = json.valueToTree(app.getBean(StudentQueryService.class).current(user.id()));
      assertEquals("expired", current.path("application").path("status").asString());
      assertTrue(current.path("canSubmit").asBoolean());
      D2ApplicationTest.User refreshed =
          new User(user.id(), sessions.create(user.id(), "miniapp").session().getId());
      Map<String, Object> input = body(refreshed, "student");
      CyclicBarrier barrier = new CyclicBarrier(2);
      Future<Integer> expire =
          pool.submit(
              () -> {
                barrier.await();
                return app.getBean(StudentExpiryService.class).expireBatch();
              });
      Future<Object> submit =
          pool.submit(
              () -> {
                barrier.await();
                return json.valueToTree(
                    app.getBean(StudentSubmissionService.class)
                        .submit(
                            user.id(),
                            Ids.next(),
                            json.convertValue(
                                input, StudentSubmissionService.SubmitRequest.class)));
              });
      String fresh =
          ((JsonNode) submit.get(15, TimeUnit.SECONDS)).path("application").path("id").asString();
      expire.get(15, TimeUnit.SECONDS);
      app.getBean(StudentExpiryService.class).expireBatch();
      assertNotEquals(old, fresh);
      assertEquals(
          "expired",
          db.queryForObject(
              "SELECT verification_status FROM user_student_verification WHERE id=?",
              String.class,
              old));
      assertEquals(
          "pending",
          db.queryForObject(
              "SELECT verification_status FROM user_student_verification WHERE id=?",
              String.class,
              fresh));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT version FROM user_student_verification WHERE id=?", Integer.class, fresh));
    } finally {
      clock.at = original;
      pool.shutdownNow();
    }
  }

  /** 验证跨所有者请求键冲突不泄露申请。 */
  @Test
  void concurrentCrossOwnerRequestKeyCollisionIsOpaque() throws Exception {
    for (String d : List.of("realname", "student")) {
      D2ApplicationTest.User user = user();
      D2ApplicationTest.User b = user();
      Map<String, Object> first = body(user, d);
      Map<String, Object> second = body(b, d);
      String key = Ids.next();
      ExecutorService pool = Executors.newFixedThreadPool(2);
      CyclicBarrier barrier = new CyclicBarrier(2);
      try {
        ArrayList<Future<String>> tasks = new ArrayList<Future<String>>();
        for (D2ApplicationTest.User u : List.of(user, b)) {
          Map<String, Object> input = u == user ? first : second;
          tasks.add(
              pool.submit(
                  () -> {
                    barrier.await();
                    try {
                      if (d.equals("realname"))
                        app.getBean(RealnameSubmissionService.class)
                            .submit(
                                u.id(),
                                key,
                                json.convertValue(
                                    input, RealnameSubmissionService.SubmitRequest.class));
                      else
                        app.getBean(StudentSubmissionService.class)
                            .submit(
                                u.id(),
                                key,
                                json.convertValue(
                                    input, StudentSubmissionService.SubmitRequest.class));
                      return "OK";
                    } catch (ApiFailure e) {
                      return Integer.toString(e.status);
                    }
                  }));
        }
        HashSet<String> outcomes = new HashSet<String>();
        for (Future<String> t : tasks) outcomes.add(t.get(15, TimeUnit.SECONDS));
        assertEquals(Set.of("OK", "404"), outcomes);
        assertEquals(
            1,
            db.queryForObject(
                "SELECT COUNT(*) FROM user_" + d + "_verification WHERE submit_request_id=?",
                Integer.class,
                key));
      } finally {
        pool.shutdownNow();
      }
    }
  }

  /** 验证会话保存失败仍保留已提交的注册结果。 */
  @Test
  void committedRegistrationSurvivesSessionPersistenceFailure() {
    String code = Ids.next();
    wx.identities.put(
        code,
        new WechatGateway.Identity(
            "test-app",
            code,
            null,
            "+8617" + String.format("%09d", Math.abs(code.hashCode()) % 1000000000L),
            "86"));
    SessionService failedSessions = org.mockito.Mockito.mock(SessionService.class);
    org.mockito.Mockito.when(
            failedSessions.create(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("miniapp")))
        .thenThrow(ApiFailure.redis());
    WechatPhoneLoginService login =
        new WechatPhoneLoginService(
            app.getBean(com.mujian.backend.account.mapper.LoginMapper.class),
            app.getBean(com.mujian.backend.account.mapper.AccountMapper.class),
            app.getBean(RuleAcceptanceService.class),
            app.getBean(AccountContextService.class),
            failedSessions,
            wx,
            app.getBean(org.springframework.transaction.PlatformTransactionManager.class),
            clock);
    assertEquals(
        "SESSION_STORE_UNAVAILABLE",
        assertThrows(ApiFailure.class, () -> login.login(code, "fixture", rules)).reason);
    String user =
        db.queryForObject(
            "SELECT user_id FROM user_login_identity WHERE openid=?", String.class, code);
    assertEquals(
        5,
        db.queryForObject(
            "SELECT COUNT(*) FROM user_rule_acceptance WHERE user_id=?", Integer.class, user));
    JsonNode retry =
        json.valueToTree(
            app.getBean(WechatPhoneLoginService.class).login(code, "new-authorization", rules));
    assertEquals(user, retry.path("context").path("userId").asString());
    assertEquals(
        2,
        db.queryForObject(
            "SELECT COUNT(*) FROM user_login_identity WHERE user_id=?", Integer.class, user));
  }

  /** 用实际行锁验证规则发布与注册竞争。 */
  @Test
  void rulePublicationCompetesWithRegistrationUnderRealRowLock() throws Exception {
    String old = rules.get(0).documentId(), replacement = Ids.next(), code = Ids.next();
    wx.identities.put(
        code,
        new WechatGateway.Identity(
            "test-app",
            code,
            null,
            "+8614" + String.format("%09d", Math.abs(code.hashCode()) % 1000000000L),
            "86"));
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try (Connection c = app.getBean(javax.sql.DataSource.class).getConnection()) {
      c.setAutoCommit(false);
      try (PreparedStatement s =
          c.prepareStatement(
              "UPDATE platform_rule_document SET rule_status='archived' WHERE id=?")) {
        s.setString(1, old);
        s.executeUpdate();
      }
      try (PreparedStatement s =
          c.prepareStatement(
              "INSERT INTO platform_rule_document"
                  + " (id,rule_code,rule_title,rule_version,rule_scope,content_type,content_text,content_hash,is_required,force_reaccept,rule_status,effective_at,created_at,updated_at)"
                  + " SELECT"
                  + " ?,rule_code,rule_title,?,rule_scope,content_type,content_text,content_hash,is_required,force_reaccept,'active',effective_at,created_at,updated_at"
                  + " FROM platform_rule_document WHERE id=?")) {
        s.setString(1, replacement);
        s.setString(2, "race-" + replacement.substring(0, 16));
        s.setString(3, old);
        s.executeUpdate();
      }
      CountDownLatch entered = new CountDownLatch(1);
      Future<String> login =
          pool.submit(
              () -> {
                entered.countDown();
                try {
                  app.getBean(WechatPhoneLoginService.class).login(code, "fixture", rules);
                  return "OK";
                } catch (ApiFailure e) {
                  return e.reason;
                }
              });
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> login.get(200, TimeUnit.MILLISECONDS));
      c.commit();
      assertEquals("RULE_VERSION_CHANGED", login.get(10, TimeUnit.SECONDS));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM user_login_identity WHERE openid=?", Integer.class, code));
    } finally {
      pool.shutdownNow();
      db.update("DELETE FROM platform_rule_document WHERE id=?", replacement);
      db.update("UPDATE platform_rule_document SET rule_status='active' WHERE id=?", old);
    }
  }

  /** 验证引用查询失败不会触发删除。 */
  @Test
  void cleanupFailsClosedOnReferenceFailureAndProtectsEveryReferencedObject() throws Exception {
    D2ApplicationTest.User user = user();
    VerificationMaterials materials = app.getBean(VerificationMaterials.class);
    Receipt receipt = materials.upload(user.id(), "student_card", "student-material-v1", true, png());
    Instant original = clock.at;
    try {
      clock.at = original.plus(Duration.ofHours(48));
      MaterialReferenceMapper broken =
          org.mockito.Mockito.mock(
              com.mujian.backend.material.mapper.MaterialReferenceMapper.class);
      org.mockito.Mockito.when(
              broken.countMaterialReferencesByOwnerAndKey(
                  org.mockito.ArgumentMatchers.anyString(),
                  org.mockito.ArgumentMatchers.anyString()))
          .thenThrow(
              new org.springframework.dao.DataAccessResourceFailureException(
                  "controlled reference query failure"));
      VerificationMaterialCleanup cleanup =
          new VerificationMaterialCleanup(
              store,
              app.getBean(com.mujian.backend.account.mapper.LoginMapper.class),
              broken,
              clock,
              app.getEnvironment(),
              app.getBean(org.springframework.transaction.PlatformTransactionManager.class),
              app.getBean(MaterialCleanupAudit.class));
      assertTrue(cleanup.scan(false).unknown() > 0);
      assertTrue(store.objects.containsKey(receipt.objectKey()));
      MaterialReferenceMapper actual =
          app.getBean(com.mujian.backend.material.mapper.MaterialReferenceMapper.class);
      List<String> referenced =
          store.objects.values().stream()
              .filter(
                  o ->
                      actual.countMaterialReferencesByOwnerAndKey(
                              o.metadata().get("owner"), o.key())
                          > 0)
              .map(PrivateMaterialStore.Stored::key)
              .toList();
      app.getBean(VerificationMaterialCleanup.class).scan(false);
      for (String key : referenced) assertTrue(store.objects.containsKey(key));
      assertFalse(store.objects.containsKey(receipt.objectKey()));
      // Redis can retain the receipt, but the immutable deadline still prevents introducing an
      // expired key.
      assertEquals(
          "MATERIAL_REFERENCE_EXPIRED",
          assertThrows(ApiFailure.class, () -> materials.validateNewReference(receipt)).reason);
    } finally {
      clock.at = original;
    }
  }

  /** 验证待审、驳回、通过和过期等历史材料仍被保留。 */
  @Test
  void cleanupRetainsExplicitPendingRejectedVerifiedApprovedAndExpiredHistory() throws Exception {
    Instant original = clock.at;
    HashSet<String> retained = new HashSet<String>();
    try {
      for (String domain : List.of("realname", "student")) {
        D2ApplicationTest.User user = user();
        String own = "/api/me/" + domain + "-verifications",
            path = "/api/admin/" + domain + "-verifications/";
        JsonNode pending = call("POST", own, body(user, domain), user, Ids.next(), 200);
        String id = pending.path("application").path("id").asString();
        JsonNode detail = call("GET", path + id, null, null, null, 200);
        call(
            "POST",
            path + id + "/reject",
            reviewBody(detail, false, domain),
            null,
            Ids.next(),
            200);
        JsonNode second = call("POST", own, body(user, domain), user, Ids.next(), 200);
        id = second.path("application").path("id").asString();
        detail = call("GET", path + id, null, null, null, 200);
        call(
            "POST",
            path + id + "/approve",
            reviewBody(detail, true, domain),
            null,
            Ids.next(),
            200);
        D2ApplicationTest.User pendingUser = user();
        call("POST", own, body(pendingUser, domain), pendingUser, Ids.next(), 200);
        store.objects.values().stream()
            .filter(o -> Set.of(user.id(), pendingUser.id()).contains(o.metadata().get("owner")))
            .forEach(o -> retained.add(o.key()));
      }
      assertEquals(12, retained.size());
      clock.at = original.plus(Duration.ofHours(48));
      Report first = app.getBean(VerificationMaterialCleanup.class).scan(false);
      assertTrue(first.completed());
      for (String key : retained) assertTrue(store.objects.containsKey(key), key);
      clock.at = original.atZone(ZONE).plusYears(1).plusDays(1).toInstant();
      app.getBean(StudentExpiryService.class).expireBatch();
      assertTrue(
          db.queryForObject(
                  "SELECT COUNT(*) FROM user_student_verification WHERE"
                      + " verification_status='expired'",
                  Integer.class)
              > 0);
      Report second = app.getBean(VerificationMaterialCleanup.class).scan(false);
      assertTrue(second.completed());
      for (String key : retained) assertTrue(store.objects.containsKey(key), key);
    } finally {
      clock.at = original;
    }
  }

  /** 验证删除响应丢失和分页异常均可恢复。 */
  @Test
  void cleanupLostDeleteResponseAndBrokenPaginationStayUnknownAndRecoverable() throws Exception {
    D2ApplicationTest.User user = user();
    Receipt receipt =
        app.getBean(VerificationMaterials.class)
            .upload(user.id(), "student_card", "student-material-v1", true, png());
    Instant original = clock.at;
    D2ApplicationTest.MemoryStore isolatedStore =
        new MemoryStore(clock) {
          /** 删除隔离夹具中的对象。 */
          @Override
          public void delete(String key, String version) {
            super.delete(key, version);
            throw unavailable("OBJECT_STORAGE_UNAVAILABLE");
          }
        };
    isolatedStore.objects.put(receipt.objectKey(), store.objects.get(receipt.objectKey()));
    LoginMapper users = app.getBean(com.mujian.backend.account.mapper.LoginMapper.class);
    MaterialReferenceMapper refs =
        app.getBean(com.mujian.backend.material.mapper.MaterialReferenceMapper.class);
    PlatformTransactionManager tm =
        app.getBean(org.springframework.transaction.PlatformTransactionManager.class);
    try {
      clock.at = original.plus(Duration.ofHours(48));
      VerificationMaterialCleanup cleanup =
          new VerificationMaterialCleanup(
              isolatedStore,
              users,
              refs,
              clock,
              app.getEnvironment(),
              tm,
              app.getBean(MaterialCleanupAudit.class));
      Report lost = cleanup.scan(false);
      assertEquals(1, lost.unknown());
      assertEquals(0, lost.deleted());
      assertFalse(isolatedStore.objects.containsKey(receipt.objectKey()));
      Report retry = cleanup.scan(false);
      assertTrue(retry.completed());
      assertEquals(0, retry.scanned());
      assertEquals(0, retry.unknown());
      D2ApplicationTest.MemoryStore loop =
          new MemoryStore(clock) {
            /** 列出本用例可访问的对象。 */
            @Override
            public PrivateMaterialStore.Page list(String marker) {
              return new PrivateMaterialStore.Page(List.of(), "same-marker");
            }
          };
      Report loopReport =
          new VerificationMaterialCleanup(
                  loop,
                  users,
                  refs,
                  clock,
                  app.getEnvironment(),
                  tm,
                  app.getBean(MaterialCleanupAudit.class))
              .scan(false);
      assertFalse(loopReport.completed());
      assertEquals(1, loopReport.unknown());
      assertEquals(0, loopReport.deleted());
      D2ApplicationTest.MemoryStore failedList =
          new MemoryStore(clock) {
            /** 列出本用例可访问的对象。 */
            @Override
            public PrivateMaterialStore.Page list(String marker) {
              throw unavailable("OBJECT_STORAGE_UNAVAILABLE");
            }
          };
      Report failedReport =
          new VerificationMaterialCleanup(
                  failedList,
                  users,
                  refs,
                  clock,
                  app.getEnvironment(),
                  tm,
                  app.getBean(MaterialCleanupAudit.class))
              .scan(false);
      assertFalse(failedReport.completed());
      assertEquals(1, failedReport.unknown());
      assertEquals(0, failedReport.deleted());
    } finally {
      clock.at = original;
    }
  }

  /** 验证生产环境缺凭据或使用 root 时拒绝启动。 */
  @Test
  void productionConfigurationRejectsMissingSecretsAndRootBeforeDataAccess() {
    MockEnvironment env =
        new org.springframework.mock.env.MockEnvironment()
            .withProperty("mj.environment", "production")
            .withProperty("spring.datasource.username", "runtime");
    StartupVerification check =
        new com.mujian.backend.common.config.StartupVerification(null, env, null);
    assertTrue(
        assertThrows(IllegalStateException.class, () -> check.run(null))
            .getMessage()
            .contains("missing mj.wechat.app-id"));
    env.setProperty("spring.datasource.username", "root");
    assertTrue(
        assertThrows(IllegalStateException.class, () -> check.run(null))
            .getMessage()
            .contains("must not run as root"));
    env.setProperty("spring.datasource.username", "runtime");
    env.setActiveProfiles("local", "prod");
    assertTrue(
        assertThrows(IllegalStateException.class, () -> check.run(null))
            .getMessage()
            .contains("mutually exclusive"));
  }

  /** 验证启用奖励规则时保留原首次事实并可启动。 */
  @Test
  void activeRewardRulePinsFirstFactAndAllowsStartup() {
    TransactionTemplate tx =
        new org.springframework.transaction.support.TransactionTemplate(
            app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    tx.executeWithoutResult(
        t -> {
          try {
            String template = Ids.next(), rule = Ids.next();
            LocalDateTime at = java.time.LocalDateTime.now(clock);
            db.update(
                "INSERT INTO coupon_template"
                    + " (id,template_code,template_name,template_version,coupon_type,face_amount_fen,threshold_rent_amount_fen,applicable_scope_type,cost_bearer,validity_type,valid_days,refund_return_policy,return_extension_days,issued_count,template_status,created_by_admin_user_id,created_at,updated_at)"
                    + " VALUES"
                    + " (?,?,'虚构保护测试',1,'fixed_amount',100,0,'all','shared_by_settlement_ratio','relative_days',1,'return_with_extension',7,0,'draft',?,?,?)",
                template,
                "GUARD_" + template,
                admin,
                at,
                at);
            db.update(
                "INSERT INTO coupon_reward_rule"
                    + " (id,event_type,rule_version,template_id,status,activated_at,created_by_admin_user_id,created_at)"
                    + " VALUES (?,'first_realname_verified',2147483001,?,'active',?,?,?)",
                rule,
                template,
                at,
                admin,
                at);
            FirstRewardCaptureService firstRewardCaptureService =
                app.getBean(com.mujian.backend.reward.service.FirstRewardCaptureService.class);
            String source = Ids.next();
            firstRewardCaptureService.capture(com.mujian.backend.common.enums.FirstRewardEvent.first_realname_verified,
                admin, source, true, true, at);
            assertEquals(rule, db.queryForObject("SELECT rule_id FROM coupon_reward_record WHERE source_id=?", String.class, source));
            MockEnvironment env =
                new org.springframework.mock.env.MockEnvironment()
                    .withProperty("mj.environment", "test")
                    .withProperty("spring.datasource.username", "dedicated_test");
            StartupVerification startup =
                new com.mujian.backend.common.config.StartupVerification(
                    app.getBean(com.mujian.backend.account.mapper.AccountMapper.class), env, db);
            startup.run(null);
          } finally {
            t.setRollbackOnly();
          }
        });
    assertEquals(
        0,
        db.queryForObject(
            "SELECT COUNT(*) FROM coupon_reward_rule WHERE status='active'", Integer.class));
  }

  /** 验证未提交材料恢复及清理与提交的锁竞争。 */
  @Test
  void uncommittedRecoveryAndBothSubmitCleanupLockRaces() throws Exception {
    for (String domain : List.of("realname", "student")) {
      D2ApplicationTest.User user = user();
      Map<String, Object> input = body(user, domain);
      String key = Ids.next();
      D2ApplicationTest.Fault fault = app.getBean(Fault.class);
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      fault.blockTable = "admin_case_relation";
      fault.entered = entered;
      fault.release = release;
      ExecutorService pool = Executors.newFixedThreadPool(2);
      Instant original = clock.at;
      try {
        Future<Object> submitting =
            pool.submit(
                () ->
                    domain.equals("realname")
                        ? app.getBean(RealnameSubmissionService.class)
                            .submit(
                                user.id(),
                                key,
                                json.convertValue(
                                    input, RealnameSubmissionService.SubmitRequest.class))
                        : app.getBean(StudentSubmissionService.class)
                            .submit(
                                user.id(),
                                key,
                                json.convertValue(
                                    input, StudentSubmissionService.SubmitRequest.class)));
        assertTrue(entered.await(10, TimeUnit.SECONDS));
        call("GET", "/api/me/" + domain + "-verifications/by-request/" + key, null, user, null, 404);
        clock.at = original.plus(Duration.ofHours(48));
        Future<Report> cleaning =
            pool.submit(() -> app.getBean(VerificationMaterialCleanup.class).scan(false));
        assertThrows(TimeoutException.class, () -> cleaning.get(200, TimeUnit.MILLISECONDS));
        release.countDown();
        submitting.get(10, TimeUnit.SECONDS);
        assertTrue(cleaning.get(10, TimeUnit.SECONDS).completed());
        List<Stored> owned =
            store.objects.values().stream()
                .filter(o -> user.id().equals(o.metadata().get("owner")))
                .toList();
        assertEquals(2, owned.size());
        clock.at = original;
        call("GET", "/api/me/" + domain + "-verifications/by-request/" + key, null, user, null, 200);
      } finally {
        release.countDown();
        fault.blockTable = null;
        clock.at = original;
        pool.shutdownNow();
      }
    }
  }

  /** 验证清理先获锁时过期上传不可提交。 */
  @Test
  void cleanupFirstOwnerLockBlocksBothSubmissionsAndExpiredReferenceCannotCommit()
      throws Exception {
    LoginMapper users = app.getBean(com.mujian.backend.account.mapper.LoginMapper.class);
    MaterialReferenceMapper refs =
        app.getBean(com.mujian.backend.material.mapper.MaterialReferenceMapper.class);
    PlatformTransactionManager tm =
        app.getBean(org.springframework.transaction.PlatformTransactionManager.class);
    for (String domain : List.of("realname", "student")) {
      D2ApplicationTest.User user = user();
      Map<String, Object> input = body(user, domain);
      D2ApplicationTest.MemoryStore isolatedStore = new MemoryStore(clock);
      store.objects.values().stream()
          .filter(o -> user.id().equals(o.metadata().get("owner")))
          .forEach(o -> isolatedStore.objects.put(o.key(), o));
      CountDownLatch entered = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      MaterialReferenceMapper blockedRefs = org.mockito.Mockito.mock(MaterialReferenceMapper.class);
      org.mockito.Mockito.when(
              blockedRefs.countMaterialReferencesByOwnerAndKey(
                  org.mockito.ArgumentMatchers.anyString(),
                  org.mockito.ArgumentMatchers.anyString()))
          .thenAnswer(
              invocation -> {
                String owner = invocation.getArgument(0), key = invocation.getArgument(1);
                entered.countDown();
                try {
                  if (!release.await(8, TimeUnit.SECONDS))
                    throw new IllegalStateException("cleanup barrier timeout");
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(e);
                }
                return refs.countMaterialReferencesByOwnerAndKey(owner, key);
              });
      VerificationMaterialCleanup cleanup =
          new VerificationMaterialCleanup(
              isolatedStore,
              users,
              blockedRefs,
              clock,
              app.getEnvironment(),
              tm,
              app.getBean(MaterialCleanupAudit.class));
      ExecutorService pool = Executors.newFixedThreadPool(2);
      Instant original = clock.at;
      try {
        clock.at = original.plus(Duration.ofHours(48));
        Future<Report> cleaning = pool.submit(() -> cleanup.scan(true));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        Future<JsonNode> submitting =
            pool.submit(
                () ->
                    call(
                        "POST", "/api/me/" + domain + "-verifications", input, user, Ids.next(), 400));
        assertThrows(TimeoutException.class, () -> submitting.get(200, TimeUnit.MILLISECONDS));
        release.countDown();
        Report result = cleaning.get(10, TimeUnit.SECONDS);
        submitting.get(10, TimeUnit.SECONDS);
        assertEquals("MATERIAL_REFERENCE_EXPIRED", lastReason);
        assertTrue(result.completed());
        assertEquals(2, result.skipped());
        assertEquals(0, result.deleted());
        assertEquals(
            0,
            db.queryForObject(
                "SELECT COUNT(*) FROM user_" + domain + "_verification WHERE user_id=?",
                Integer.class,
                user.id()));
        assertEquals(2, isolatedStore.objects.size());
      } finally {
        release.countDown();
        clock.at = original;
        pool.shutdownNow();
      }
    }
  }

  /** 验证真实数据库锁超时后保留对象并审计未知结果。 */
  @Test
  void cleanupActualMysqlOwnerLockTimeoutRetainsObjectAndAuditsUnknown() throws Exception {
    D2ApplicationTest.User user = user();
    String upload = upload(user, "student", null);
    D2ApplicationTest.MemoryStore isolatedStore = new MemoryStore(clock);
    store.objects.values().stream()
        .filter(o -> user.id().equals(o.metadata().get("owner")))
        .forEach(o -> isolatedStore.objects.put(o.key(), o));
    LoginMapper users = app.getBean(com.mujian.backend.account.mapper.LoginMapper.class);
    LoginMapper wrapped =
        (com.mujian.backend.account.mapper.LoginMapper)
            java.lang.reflect.Proxy.newProxyInstance(
                users.getClass().getClassLoader(),
                new Class[] {com.mujian.backend.account.mapper.LoginMapper.class},
                (proxy, method, args) -> {
                  Integer previous = null;
                  if (method.getName().equals("lockUser")) {
                    previous =
                        db.queryForObject(
                            "SELECT @@session.innodb_lock_wait_timeout", Integer.class);
                    db.execute("SET SESSION innodb_lock_wait_timeout=1");
                  }
                  try {
                    return method.invoke(users, args);
                  } catch (java.lang.reflect.InvocationTargetException e) {
                    throw e.getCause();
                  } finally {
                    if (previous != null)
                      db.execute("SET SESSION innodb_lock_wait_timeout=" + previous);
                  }
                });
    VerificationMaterialCleanup cleanup =
        new VerificationMaterialCleanup(
            isolatedStore,
            wrapped,
            app.getBean(com.mujian.backend.material.mapper.MaterialReferenceMapper.class),
            clock,
            app.getEnvironment(),
            app.getBean(org.springframework.transaction.PlatformTransactionManager.class),
            app.getBean(MaterialCleanupAudit.class));
    Instant original = clock.at;
    try (Connection connection = app.getBean(javax.sql.DataSource.class).getConnection()) {
      connection.setAutoCommit(false);
      try (PreparedStatement statement =
          connection.prepareStatement("SELECT id FROM sys_user WHERE id=? FOR UPDATE")) {
        statement.setString(1, user.id());
        statement.executeQuery().close();
      }
      try {
        clock.at = original.plus(Duration.ofHours(48));
        Report result = cleanup.scan(false);
        assertEquals(1, result.unknown());
        assertEquals(0, result.deleted());
        assertEquals(1, isolatedStore.objects.size());
        assertEquals(
            1,
            db.queryForObject(
                "SELECT COUNT(*) FROM admin_operation_audit WHERE request_id=? AND"
                    + " operation_reason='inspect'",
                Integer.class,
                result.runId()));
      } finally {
        connection.rollback();
        clock.at = original;
      }
    }
    assertTrue(
        isolatedStore.objects.containsKey(VerificationMaterials.key(user.id(), "student", upload)));
  }

  /** 检查通配查询、分页和两千条历史记录。 */
  @Test
  void listPaginationWildcardAndMeasuredTwoThousandRowHistory() throws Exception {
    ArrayList<Object> results = new ArrayList<Object>();
    TransactionTemplate tx =
        new org.springframework.transaction.support.TransactionTemplate(
            app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    for (String domain : List.of("realname", "student")) {
      D2ApplicationTest.User user = user();
      JsonNode made =
          call("POST", "/api/me/" + domain + "-verifications", body(user, domain), user, Ids.next(), 200);
      String source = made.path("application").path("id").asString();
      tx.executeWithoutResult(
          transaction -> {
            try {
              String table = "user_" + domain + "_verification";
              LinkedHashMap<String, Object> baseRow =
                  new LinkedHashMap<>(
                      db.queryForMap("SELECT * FROM " + table + " WHERE id=?", source));
              baseRow.remove("active_user_guard");
              baseRow.remove("pending_user_guard");
              String marker = "D2性能%_" + Ids.next();
              db.update("UPDATE sys_user SET nickname=? WHERE id=?", marker, user.id());
              ArrayList<Object[]> batch = new ArrayList<Object[]>();
              VerificationCrypto crypto =
                  app.getBean(com.mujian.backend.infrastructure.crypto.VerificationCrypto.class);
              for (int n = 0; n < 2000; n++) {
                LinkedHashMap<String, Object> row = new LinkedHashMap<>(baseRow);
                String id = Ids.next();
                row.put("id", id);
                row.put("submit_request_id", Ids.next());
                row.put("verification_status", "rejected");
                row.put("reviewed_by_admin_user_id", admin);
                row.put("reviewed_at", now(clock));
                row.put("reject_reason", "虚构性能夹具");
                row.put("version", 1);
                if (domain.equals("realname")) {
                  row.put("audit_remark", "虚构性能夹具");
                  row.put(
                      "real_name_ciphertext",
                      crypto.encrypt(id, "real_name_ciphertext", crypto.version(), "性能样本"));
                  row.put(
                      "id_card_ciphertext",
                      crypto.encrypt(
                          id, "id_card_ciphertext", crypto.version(), "11010519491231002X"));
                } else {
                  row.put("application_no", "SV" + id);
                  row.put("school_name", marker);
                }
                batch.add(row.values().toArray());
              }
              db.batchUpdate(
                  "INSERT INTO "
                      + table
                      + " ("
                      + String.join(",", baseRow.keySet())
                      + ") VALUES ("
                      + String.join(",", Collections.nCopies(baseRow.size(), "?"))
                      + ")",
                  batch);
              com.mujian.backend.verification.dto.VerificationListQueryDTO verificationListQueryDTO =
                  new com.mujian.backend.verification.dto.VerificationListQueryDTO()
                      .status("rejected")
                      .keyword(user.id())
                      .page("1")
                      .pageSize("20");
              ArrayList<Double> durations = new ArrayList<Double>();
              JsonNode first = null;
              for (int i = 0; i < 35; i++) {
                long start = System.nanoTime();
                Object response =
                    domain.equals("realname")
                        ? app.getBean(RealnameQueryService.class).list(admin, verificationListQueryDTO)
                        : app.getBean(StudentQueryService.class).list(admin, verificationListQueryDTO);
                double ms = (System.nanoTime() - start) / 1_000_000.0;
                JsonNode sample = json.valueToTree(response);
                assertEquals("2000", sample.path("total").asString());
                assertEquals(20, sample.path("items").size());
                if (first == null) first = sample;
                else assertEquals(first.path("items"), sample.path("items"));
                if (i >= 5) durations.add(ms);
              }
              verificationListQueryDTO.setPage("2");
              JsonNode second =
                  json.valueToTree(
                      domain.equals("realname")
                          ? app.getBean(RealnameQueryService.class).list(admin, verificationListQueryDTO)
                          : app.getBean(StudentQueryService.class).list(admin, verificationListQueryDTO));
              HashSet<String> ids = new HashSet<String>();
              for (JsonNode item : first.path("items")) ids.add(item.path("id").asString());
              for (JsonNode item : second.path("items"))
                assertFalse(ids.contains(item.path("id").asString()));
              verificationListQueryDTO.setPage("1");
              verificationListQueryDTO.setKeyword(marker);
              assertEquals(
                  "2000",
                  json.valueToTree(
                          domain.equals("realname")
                              ? app.getBean(RealnameQueryService.class).list(admin, verificationListQueryDTO)
                              : app.getBean(StudentQueryService.class).list(admin, verificationListQueryDTO))
                      .path("total")
                      .asString());
              Collections.sort(durations);
              double p95 = durations.get(28);
              assertTrue(p95 < 500, "local diagnostic p95 must remain below 500ms");
              results.add(
                  view(
                      "domain",
                      domain,
                      "fixtureHistoryRows",
                      2000,
                      "pageSize",
                      20,
                      "warmups",
                      5,
                      "samples",
                      30,
                      "p50Ms",
                      durations.get(14),
                      "p95Ms",
                      p95,
                      "maxMs",
                      durations.get(29),
                      "diagnosticThresholdMs",
                      500,
                      "boundary",
                      "real database + service mapping/crypto; same transaction; excludes HTTP,"
                          + " cloud and production load; rolled back"));
            } finally {
              transaction.setRollbackOnly();
            }
          });
    }
    if (System.getenv("MJ_EVIDENCE_DIR") != null)
      Files.writeString(
          Path.of(System.getenv("MJ_EVIDENCE_DIR"), "list-performance.json"),
          json.writeValueAsString(results) + "\n");
  }
}
