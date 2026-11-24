package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.security.D2Contract;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import com.mujian.backend.infrastructure.storage.BoundedMaterialReader;
import com.mujian.backend.scheme.domain.PdfSnapshot;
import com.mujian.backend.scheme.dto.SchemeDtos.ExportPdf;
import com.mujian.backend.scheme.service.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;

/** 本轮保存/快照/PDF差异的真实HTTP、MySQL、Redis和文件验证，不重跑交易。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class D3SchemeRevisionTest {
  final D2CApplicationTest fixture = new D2CApplicationTest();
  final D2ApplicationTest f = fixture.f;
  final List<String> schemes = new ArrayList<>();
  User actor, other, supplier;
  String sku, sku2, sku3;
  Path pdfDirectory;
  ControlledRenderer renderer;

  static class ControlledRenderer extends SchemePdfRenderer {
    volatile boolean fail;
    volatile CountDownLatch entered, release;

    /** 保留真实Java渲染，仅在显式场景注入故障/在途屏障。 */
    ControlledRenderer(BoundedMaterialReader reader, Clock clock) {
      super(reader, clock);
    }

    /** 故障与阻塞均发生于任务已提交之后。 */
    @Override
    public void render(PdfSnapshot snapshot, OutputStream output, Instant deadline)
        throws IOException {
      if (entered != null) {
        entered.countDown();
        try {
          if (!release.await(15, TimeUnit.SECONDS)) throw new IOException("test barrier timeout");
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          throw new IOException(exception);
        }
      }
      if (fail) throw new IOException("injected render failure");
      super.render(snapshot, output, deadline);
    }
  }

  @TestConfiguration
  static class RendererConfiguration {
    /** 当前用例注入可控渲染器，不改生产渲染实现。 */
    @Bean
    @Primary
    ControlledRenderer controlledRenderer(BoundedMaterialReader reader, Clock clock) {
      return new ControlledRenderer(reader, clock);
    }
  }

  /** 复用既有隔离配置，先旧版本造两类历史样本，再只应用V22。 */
  @BeforeAll
  void start() throws Exception {
    pdfDirectory = Files.createTempDirectory("mujian-d3a-20261118-");
    f.additionalSources = new Class<?>[] {RendererConfiguration.class};
    f.start(
        0,
        "--mj.prop.qr-worker-enabled=false",
        "--mj.trading.recovery-enabled=false",
        "--mj.planning.pdf.directory=" + pdfDirectory);
    actor = f.user();
    other = f.user();
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(Path.of(System.getenv("MJ_TEST_CONFIG")))) {
      properties.load(input);
    }
    int already =
        f.db.queryForObject(
            "SELECT COUNT(*) FROM flyway_schema_history WHERE version='22' AND success=1",
            Integer.class);
    String legacyScheme = null, legacyLink = null, legacyExport = null;
    if (already == 0) {
      legacyScheme = scheme(actor);
      legacyLink = Ids.next();
      legacyExport = Ids.next();
      f.db.update(
          "INSERT INTO"
              + " scheme_share(id,scheme_id,share_token,share_status,hide_price,expires_at,view_count,created_by,created_at,updated_at)"
              + " VALUES(?,?,?,'active',0,NULL,0,?,?,?)",
          legacyLink,
          legacyScheme,
          "L".repeat(43),
          actor.id(),
          now(f.clock),
          now(f.clock));
      f.db.update(
          "INSERT INTO"
              + " scheme_export(id,scheme_id,export_status,source_content_version,template_version,hide_price,watermark_enabled,watermark_text,file_expire_at,local_file_key,processing_deadline_at,record_expire_at,generated_at,snapshot_json,created_by,created_at,updated_at)"
              + " VALUES(?,?,'success',1,'scheme_v1',0,1,'test',?,?,?, ?,?,'{}',?,?,?)",
          legacyExport,
          legacyScheme,
          now(f.clock).plusMinutes(10),
          legacyExport + ".pdf",
          now(f.clock).plusSeconds(120),
          now(f.clock).plusDays(7),
          now(f.clock),
          actor.id(),
          now(f.clock),
          now(f.clock));
    }
    Flyway flyway =
        Flyway.configure()
            .dataSource(
                properties.getProperty("spring.datasource.url"),
                properties.getProperty("spring.datasource.username"),
                properties.getProperty("spring.datasource.password"))
            .cleanDisabled(true)
            .load();
    int migrations = flyway.migrate().migrationsExecuted;
    flyway.validate();
    assertEquals(0, flyway.migrate().migrationsExecuted);
    if (already == 0) {
      assertEquals(1, migrations);
      assertEquals(
          "expired",
          f.db.queryForObject(
              "SELECT share_status FROM scheme_share WHERE id=?", String.class, legacyLink));
      assertEquals(
          0,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM scheme_share_snapshot WHERE scheme_id=?",
              Integer.class,
              legacyScheme));
      assertEquals(
          300,
          f.db.queryForObject(
              "SELECT TIMESTAMPDIFF(SECOND,generated_at,file_expire_at) FROM scheme_export WHERE"
                  + " id=?",
              Integer.class,
              legacyExport));
      Files.writeString(
          Path.of("target/d3a-20261118/migration-result.json"),
          "{\"from\":\"21.2\",\"to\":\"22\",\"executed\":1,\"repeat\":0,\"legacyLinkExpiredWithoutInventedSnapshot\":true,\"historicalFileLifetimeSeconds\":300}");
    }
    supplier = fixture.supplier();
    List<String> skus = new ArrayList<>();
    for (int i = 0; i < 3; i++) {
      JsonNode made = fixture.create(supplier, "B_CONSUMABLE", 6);
      skus.add(made.path("skuId").asString());
      fixture.approve("sku_audit", made.path("targetId").asString(), 0);
    }
    sku = skus.get(0);
    sku2 = skus.get(1);
    sku3 = skus.get(2);
    f.store.visualSigner = (key, until) -> "https://example.invalid/" + key;
    renderer = f.app.getBean(ControlledRenderer.class);
  }

  /** 只撤销本次临时会话、清理本次外键闭包和专属文件。 */
  @AfterAll
  void stop() throws Exception {
    if (f.app != null) {
      StringRedisTemplate redis = f.app.getBean(StringRedisTemplate.class);
      for (String id : schemes) {
        Set<String> keys = redis.keys("mj:test:scheme-pdf-export:" + id + ":*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
      }
      D3BFixtureCleanup.remove(f);
      f.stop();
    }
    if (pdfDirectory != null) {
      try (java.util.stream.Stream<Path> files = Files.list(pdfDirectory)) {
        for (Path path : files.toList()) Files.delete(path);
      }
      Files.delete(pdfDirectory);
    }
  }

  /** 覆盖整份方案的引用、名额和失效入口。保留实际导出任务及来源清单。 */
  @Test
  void physicalDeleteKeepsHistoryAndReplay() throws Exception {
    actor = f.user();
    String id = scheme(actor), path = "/api/me/schemes/" + id;
    save(id, List.of(addItem(sku, 2, 1)));
    int version = detail(id).path("version").asInt();
    String day = now(f.clock).toLocalDate().plusDays(5).toString(), copyKey = key();
    var source =
        view(
            "sourceType",
            "scheme",
            "schemeId",
            id,
            "expectedSchemeVersion",
            version,
            "startDate",
            day,
            "endDate",
            day);
    String list =
        call("POST", "/api/me/checklists", source, actor, copyKey, 200)
            .path("resourceId")
            .asString();
    String listPath = "/api/me/checklists/" + list;
    JsonNode listBefore = call("GET", listPath, null, actor, null, 200);
    var shareBody = view("expectedVersion", version, "hidePrice", true);
    String shareKey = key();
    JsonNode link = call("POST", path + "/share", shareBody, actor, shareKey, 200);
    String publicPath = "/api/public/scheme-shares/" + link.path("share").path("token").asString();
    call("GET", publicPath, null, null, null, 200);
    version = detail(id).path("version").asInt();
    var pdf = pdf(id, false, actor);
    assertEquals(200, pdf.statusCode());
    String exportId = pdf.headers().firstValue("X-Export-Id").orElseThrow();
    String exportPath = "/api/me/scheme-exports/" + exportId;
    assertTrue(call("GET", exportPath, null, actor, null, 200).path("canDownload").asBoolean());
    var history = f.db.queryForMap("SELECT * FROM scheme_export WHERE id=?", exportId);
    String deleteKey = key();
    var body = view("expectedVersion", version);
    call("POST", path + "/delete", body, other, key(), 404);
    call("POST", path + "/delete", view("expectedVersion", version - 1), actor, key(), 409);
    int slots = activeSchemeCount();
    JsonNode receipt = call("POST", path + "/delete", body, actor, deleteKey, 200);
    assertEquals("deleteScheme", receipt.path("operation").asString());
    assertTrue(receipt.path("resourceVersion").isNull());
    assertEquals("applied", receipt.path("outcome").asString());
    JsonNode replay = call("POST", path + "/delete", body, actor, deleteKey, 200);
    assertEquals("replayed", replay.path("outcome").asString());
    assertEquals(receipt.path("appliedAt"), replay.path("appliedAt"));
    call("POST", path + "/delete", body, actor, key(), 404);
    call("POST", path + "/delete", view("expectedVersion", version + 1), actor, deleteKey, 409);
    assertEquals("IDEMPOTENCY_KEY_REUSED", f.lastReason);
    assertEquals(slots - 1, activeSchemeCount());
    for (String table : List.of("scheme_item", "scheme_share", "scheme_share_snapshot"))
      assertEquals(
          0,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM " + table + " WHERE scheme_id=?", Integer.class, id));
    assertEquals(
        0, f.db.queryForObject("SELECT COUNT(*) FROM scheme WHERE id=?", Integer.class, id));
    call("GET", path, null, actor, null, 404);
    call("GET", publicPath, null, null, null, 404);
    call("POST", path + "/share", shareBody, actor, shareKey, 404);
    call("POST", path + "/update", view("actions", List.of()), actor, key(), 404);
    assertEquals(listBefore, call("GET", listPath, null, actor, null, 200));
    assertEquals(
        list,
        call("POST", "/api/me/checklists", source, actor, copyKey, 200)
            .path("resourceId")
            .asString());
    call("POST", "/api/me/checklists", source, actor, key(), 404);
    assertEquals(history, f.db.queryForMap("SELECT * FROM scheme_export WHERE id=?", exportId));
    assertFalse(call("GET", exportPath, null, actor, null, 200).path("canDownload").asBoolean());
    call("GET", exportPath + "/file", null, actor, null, 404);
    assertEquals(404, pdf(id, false, actor).statusCode());
    // 再建四份并各导出一次，已删除方案仍占当天第一次。
    for (int attempt = 0; attempt < 4; attempt++)
      assertEquals(200, pdf(scheme(actor), false, actor).statusCode());
    assertEquals("PDF_DAILY_LIMIT", reason(pdf(scheme(actor), false, actor)));
  }

  private int activeSchemeCount() {
    return f.db.queryForObject(
        "SELECT COUNT(*) FROM scheme WHERE owner_user_id=? AND active_slot_no IS NOT NULL",
        Integer.class,
        actor.id());
  }

  /** 归档仍可分享和导出，删除已归档方案不再次释放名额。 */
  @Test
  void archivedSchemeCanBeDeletedDirectly() throws Exception {
    actor = f.user();
    String id = scheme(actor), path = "/api/me/schemes/" + id;
    save(id, List.of(addItem(sku, 1, 0)));
    call(
        "POST",
        path + "/archive",
        view("expectedVersion", detail(id).path("version").asInt()),
        actor,
        key(),
        200);
    assertEquals("archived", detail(id).path("status").asString());
    call(
        "POST",
        path + "/share",
        view("expectedVersion", detail(id).path("version").asInt(), "hidePrice", false),
        actor,
        key(),
        200);
    assertEquals(200, pdf(id, false, actor).statusCode());
    int slots = activeSchemeCount();
    call(
        "POST",
        path + "/delete",
        view("expectedVersion", detail(id).path("version").asInt()),
        actor,
        key(),
        200);
    assertEquals(slots, activeSchemeCount());
    call("GET", path, null, actor, null, 404);
  }

  /** 生成已经预留后删除先提交，渲染完成只能保留导出历史，不能发布下载或缓存。 */
  @Test
  void deleteWhileRenderingRevokesGrant() throws Exception {
    User buyer = f.user();
    String id = scheme(buyer), path = "/api/me/schemes/" + id;
    int version = call("GET", path, null, buyer, null, 200).path("version").asInt();
    renderer.entered = new CountDownLatch(1);
    renderer.release = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      var pending = executor.submit(() -> pdf(id, false, buyer));
      assertTrue(renderer.entered.await(10, TimeUnit.SECONDS));
      call("POST", path + "/delete", view("expectedVersion", version), buyer, key(), 200);
      renderer.release.countDown();
      assertEquals(404, pending.get(20, TimeUnit.SECONDS).statusCode());
      String export =
          f.db.queryForObject("SELECT id FROM scheme_export WHERE scheme_id=?", String.class, id);
      call("GET", "/api/me/scheme-exports/" + export + "/file", null, buyer, null, 404);
      SchemePdfCache cache = f.app.getBean(SchemePdfCache.class);
      assertNull(cache.get(cache.key(id, 1, false)));
    } finally {
      renderer.release.countDown();
      renderer.entered = null;
      renderer.release = null;
      executor.shutdownNow();
    }
  }

  /** 用同一版本同时提交删除与保存／签发。先成功的请求决定另一个请求见到的根状态。 */
  @Test
  void deleteRacesWithSaveAndShare() throws Exception {
    User buyer = f.user();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (boolean share : List.of(false, true)) {
        String id = scheme(buyer), path = "/api/me/schemes/" + id;
        int version = call("GET", path, null, buyer, null, 200).path("version").asInt();
        var guard = view("expectedVersion", version);
        var change =
            share
                ? view("expectedVersion", version, "hidePrice", true)
                : view(
                    "actions",
                    List.of(
                        view("action", "updateSchemeInfo", "name", "并发保存", "description", null)));
        CyclicBarrier gate = new CyclicBarrier(2);
        Future<HttpResponse<String>> deleting =
            pool.submit(() -> concurrentPost(gate, buyer, path + "/delete", guard));
        Future<HttpResponse<String>> editing =
            pool.submit(
                () -> concurrentPost(gate, buyer, path + (share ? "/share" : "/update"), change));
        var deleted = deleting.get(15, TimeUnit.SECONDS);
        var edited = editing.get(15, TimeUnit.SECONDS);
        if (deleted.statusCode() == 200) {
          assertEquals(404, edited.statusCode());
        } else {
          assertEquals(409, deleted.statusCode());
          assertEquals(
              "VERSION_CONFLICT", f.json.readTree(deleted.body()).path("reasonCode").asString());
          assertEquals(200, edited.statusCode());
          call("POST", path + "/delete", view("expectedVersion", version + 1), buyer, key(), 200);
          if (share) {
            String token = f.json.readTree(edited.body()).at("/data/share/token").asString();
            call("GET", "/api/public/scheme-shares/" + token, null, null, null, 404);
          }
        }
        for (String scope : List.of("all", "editing", "archived")) {
          var page = call("GET", "/api/me/schemes?status=" + scope, null, buyer, null, 200);
          assertEquals(0, page.path("items").size());
          assertEquals("0", page.path("total").asString());
        }
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private HttpResponse<String> concurrentPost(
      CyclicBarrier gate, User user, String path, Object body) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create(f.base + path))
            .timeout(Duration.ofSeconds(12))
            .header("Authorization", "Bearer " + user.token())
            .header("Idempotency-Key", key())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(f.json.writeValueAsString(body)))
            .build();
    gate.await(5, TimeUnit.SECONDS);
    return f.http.send(request, HttpResponse.BodyHandlers.ofString());
  }

  /** HTTP返回验证同一运行契约并登记无凭据证据。 */
  JsonNode call(String method, String path, Object body, User user, String key, int status)
      throws Exception {
    JsonNode result = f.call(method, path, body, user, key, status);
    if (status == 200) {
      D2Contract contract = f.app.getBean(D2Contract.class);
      contract.validate(
          contract
              .route(path.split("\\?", 2)[0], method)
              .operation()
              .path("responses")
              .path("200")
              .path("content")
              .path("application/json")
              .path("schema"),
          f.json.valueToTree(
              view("code", "OK", "message", "成功", "data", result, "traceId", "test")));
    }
    return result;
  }

  /** 生成独立幂等键。 */
  String key() {
    return UUID.randomUUID().toString();
  }

  /** 建立空方案。 */
  String scheme(User user) throws Exception {
    String id =
        f.call("POST", "/api/me/schemes", view("name", "方案", "description", null), user, key(), 200)
            .path("resourceId")
            .asString();
    schemes.add(id);
    return id;
  }

  /** 读取当前版本和稳定明细。 */
  JsonNode detail(String id) throws Exception {
    return call("GET", "/api/me/schemes/" + id, null, actor, null, 200);
  }

  /** 构造新增动作。 */
  Map<String, Object> addItem(String id, int quantity, int sort) {
    return view(
        "action", "addItem", "skuId", id, "quantity", quantity, "remark", null, "sortOrder", sort);
  }

  /** 构造修改动作。 */
  Map<String, Object> updateItem(String id, int quantity, String remark, int sort) {
    return view(
        "action",
        "updateItem",
        "itemId",
        id,
        "quantity",
        quantity,
        "remark",
        remark,
        "sortOrder",
        sort);
  }

  /** 发送一次保存。 */
  JsonNode save(String id, List<?> actions) throws Exception {
    return call(
        "POST", "/api/me/schemes/" + id + "/update", view("actions", actions), actor, key(), 200);
  }

  /** PDF直接HTTP请求，不传前端幂等键。 */
  HttpResponse<byte[]> pdf(String id, boolean hidden, User user) throws Exception {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create(f.base + "/api/me/schemes/" + id + "/exports"))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json");
    if (user != null) request.header("Authorization", "Bearer " + user.token());
    return f.http.send(
        request
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    f.json.writeValueAsString(view("hidePrice", hidden))))
            .build(),
        HttpResponse.BodyHandlers.ofByteArray());
  }

  /** 解析错误原因并验证JSON媒体类型。 */
  String reason(HttpResponse<byte[]> response) {
    assertTrue(
        response.headers().firstValue("Content-Type").orElse("").contains("application/json"));
    return f.json.readTree(response.body()).path("reasonCode").asString();
  }

  /** 计数仅限本次方案。 */
  int exports(String id) {
    return f.db.queryForObject(
        "SELECT COUNT(*) FROM scheme_export WHERE scheme_id=?", Integer.class, id);
  }

  /** 混合保存、稳定ID、原快照、只改所需行、空变化、重放与整事务失败。 */
  @Test
  @Order(1)
  void incrementalSaveAtomicityAndOwnership() throws Exception {
    String id = scheme(actor);
    save(id, List.of(addItem(sku, 1, 0), addItem(sku2, 2, 1)));
    JsonNode before = detail(id);
    String first = before.path("items").get(0).path("id").asString(),
        second = before.path("items").get(1).path("id").asString();
    String snapshot =
        f.db.queryForObject(
            "SELECT sku_snapshot_json FROM scheme_item WHERE id=?", String.class, first);
    Object untouched = f.db.queryForMap("SELECT * FROM scheme_item WHERE id=?", second);
    f.clock.at = f.clock.at.plusSeconds(1);
    f.app.getBean(D2ApplicationTest.Fault.class).observedRequestPath =
        "/api/me/schemes/" + id + "/update";
    f.app.getBean(D2ApplicationTest.Fault.class).preparedSql.clear();
    save(id, List.of(updateItem(first, 3, "修改", 0)));
    assertEquals(untouched, f.db.queryForMap("SELECT * FROM scheme_item WHERE id=?", second));
    assertEquals(
        snapshot,
        f.db.queryForObject(
            "SELECT sku_snapshot_json FROM scheme_item WHERE id=?", String.class, first));
    assertEquals(
        1,
        f.app.getBean(D2ApplicationTest.Fault.class).preparedSql.stream()
            .filter(x -> x.startsWith("UPDATE scheme_item "))
            .count());
    assertTrue(
        f.app.getBean(D2ApplicationTest.Fault.class).preparedSql.stream()
            .noneMatch(
                x ->
                    x.startsWith("DELETE FROM scheme_item")
                        || x.startsWith("INSERT INTO scheme_item")));
    JsonNode same = detail(id);
    save(id, List.of(updateItem(first, 3, "修改", 0)));
    save(id, List.of());
    assertEquals(same, detail(id));
    List<?> actions =
        List.of(
            updateItem(first, 4, "排序", 3),
            view("action", "removeItem", "itemId", second),
            addItem(sku3, 2, 1),
            view("action", "updateSchemeInfo", "name", "新名字", "description", "保存后"));
    String requestId = key();
    Object body = view("actions", actions);
    JsonNode applied =
        call("POST", "/api/me/schemes/" + id + "/update", body, actor, requestId, 200);
    JsonNode after = detail(id);
    assertEquals(same.path("contentVersion").asInt() + 1, after.path("contentVersion").asInt());
    assertEquals("新名字", after.path("name").asString());
    assertEquals(2, after.path("items").size());
    assertEquals(
        "replayed",
        call("POST", "/api/me/schemes/" + id + "/update", body, actor, requestId, 200)
            .path("outcome")
            .asString());
    assertEquals(after, detail(id));
    call(
        "POST",
        "/api/me/schemes/" + id + "/update",
        view("actions", List.of()),
        actor,
        requestId,
        409);
    assertEquals("IDEMPOTENCY_KEY_REUSED", f.lastReason);
    String foreign = scheme(actor);
    save(foreign, List.of(addItem(sku, 1, 0)));
    String foreignItem = detail(foreign).path("items").get(0).path("id").asString();
    call(
        "POST",
        "/api/me/schemes/" + id + "/update",
        view(
            "actions",
            List.of(
                updateItem(first, 8, null, 0),
                view("action", "removeItem", "itemId", foreignItem))),
        actor,
        key(),
        404);
    assertEquals(after, detail(id));
    f.app.getBean(D2ApplicationTest.Fault.class).table = "planning_operation_record";
    try {
      call(
          "POST",
          "/api/me/schemes/" + id + "/update",
          view("actions", List.of(updateItem(first, 9, null, 0))),
          actor,
          key(),
          500);
    } finally {
      f.app.getBean(D2ApplicationTest.Fault.class).table = null;
      f.app.getBean(D2ApplicationTest.Fault.class).observedRequestPath = null;
    }
    assertEquals(after, detail(id));
    call(
        "POST", "/api/me/schemes/" + id + "/update", view("actions", List.of()), other, key(), 404);
    call(
        "POST",
        "/api/me/schemes/" + id + "/update",
        view("actions", List.of(updateItem(first, 0, null, 0))),
        actor,
        key(),
        400);
    call(
        "POST",
        "/api/me/schemes/" + id + "/update",
        view("expectedVersion", 0, "actions", List.of()),
        actor,
        key(),
        400);
    call(
        "POST",
        "/api/me/schemes/" + id + "/archive",
        view("expectedVersion", after.path("version").asInt()),
        actor,
        key(),
        200);
    call(
        "POST", "/api/me/schemes/" + id + "/update", view("actions", List.of()), actor, key(), 409);
    assertEquals("SCHEME_ARCHIVED", f.lastReason);
  }

  /** 多次签发复用内容但独立链接；重放、改后不变、隐价存储及七天边界。 */
  @Test
  @Order(2)
  void immutableSharesIndependentExpiryAndRetry() throws Exception {
    String id = scheme(actor);
    save(id, List.of(addItem(sku, 2, 0)));
    int version = detail(id).path("version").asInt();
    String request = key();
    Object input = view("expectedVersion", version, "hidePrice", true);
    JsonNode issued = call("POST", "/api/me/schemes/" + id + "/share", input, actor, request, 200);
    JsonNode link = issued.path("share");
    String token = link.path("token").asString();
    JsonNode original = call("GET", "/api/public/scheme-shares/" + token, null, null, null, 200);
    assertFalse(original.toString().contains("PriceFen"));
    f.clock.at = f.clock.at.plusSeconds(60);
    JsonNode second =
        call(
                "POST",
                "/api/me/schemes/" + id + "/share",
                view("expectedVersion", version + 1, "hidePrice", true),
                actor,
                key(),
                200)
            .path("share");
    assertNotEquals(token, second.path("token").asString());
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM scheme_share_snapshot WHERE scheme_id=?", Integer.class, id));
    String json =
        f.db.queryForObject(
            "SELECT snapshot_json FROM scheme_share_snapshot WHERE scheme_id=?", String.class, id);
    assertFalse(json.contains("prices"));
    assertFalse(json.contains("AmountFen"));
    JsonNode replay =
        call("POST", "/api/me/schemes/" + id + "/share", input, actor, request, 200).path("share");
    assertEquals(token, replay.path("token").asString());
    assertEquals(link.path("expiresAt"), replay.path("expiresAt"));
    JsonNode detail = detail(id);
    save(id, List.of(updateItem(detail.path("items").get(0).path("id").asString(), 5, null, 0)));
    JsonNode priced =
        call(
                "POST",
                "/api/me/schemes/" + id + "/share",
                view("expectedVersion", detail(id).path("version").asInt(), "hidePrice", false),
                actor,
                key(),
                200)
            .path("share");
    JsonNode again = call("GET", "/api/public/scheme-shares/" + token, null, null, null, 200);
    assertEquals(
        original.path("items").get(0).path("display").path("image").path("cacheKey"),
        again.path("items").get(0).path("display").path("image").path("cacheKey"));
    ((tools.jackson.databind.node.ObjectNode)
            original.path("items").get(0).path("display").path("image"))
        .remove("expiresAt");
    ((tools.jackson.databind.node.ObjectNode)
            again.path("items").get(0).path("display").path("image"))
        .remove("expiresAt");
    assertEquals(original, again);
    JsonNode visible =
        call(
            "GET",
            "/api/public/scheme-shares/" + priced.path("token").asString(),
            null,
            null,
            null,
            200);
    assertEquals(5, visible.path("items").get(0).path("quantity").asInt());
    assertTrue(visible.has("estimatedDailyRentFen"));
    Instant old = f.clock.at;
    try {
      f.clock.at = OffsetDateTime.parse(link.path("expiresAt").asString()).toInstant();
      call("GET", "/api/public/scheme-shares/" + token, null, null, null, 404);
      assertEquals("SHARE_INVALID", f.lastReason);
      call(
          "GET",
          "/api/public/scheme-shares/" + second.path("token").asString(),
          null,
          null,
          null,
          200);
    } finally {
      f.clock.at = old;
    }
  }

  /** Redis未命中不得回查旧成品；三/五分钟、当前保存版本和权限分别核对。 */
  @Test
  @Order(3)
  void pdfSinglePostCacheLifetimesCurrentVersionAndPermissions() throws Exception {
    String id = scheme(actor);
    save(id, List.of(addItem(sku, 1, 0)));
    assertEquals(401, pdf(id, false, null).statusCode());
    assertEquals(404, pdf(id, false, other).statusCode());
    assertEquals(403, pdf(id, false, supplier).statusCode());
    HttpResponse<byte[]> first = pdf(id, false, actor);
    assertEquals(200, first.statusCode());
    assertEquals("application/pdf", first.headers().firstValue("Content-Type").orElse(""));
    assertTrue(
        new String(first.body(), java.nio.charset.StandardCharsets.ISO_8859_1).startsWith("%PDF"));
    String exportId = first.headers().firstValue("X-Export-Id").orElseThrow();
    Instant firstAt = f.clock.at;
    StringRedisTemplate redis = f.app.getBean(StringRedisTemplate.class);
    SchemePdfCache cache = f.app.getBean(SchemePdfCache.class);
    String cacheKey = cache.key(id, detail(id).path("contentVersion").asInt(), false);
    Long ttl = redis.getExpire(cacheKey, TimeUnit.MILLISECONDS);
    assertTrue(ttl > 175000 && ttl <= 180000);
    HttpResponse<byte[]> cached = pdf(id, false, actor);
    assertEquals("true", cached.headers().firstValue("X-Cache-Hit").orElse(""));
    assertArrayEquals(first.body(), cached.body());
    assertEquals(1, exports(id));
    assertTrue(redis.getExpire(cacheKey, TimeUnit.MILLISECONDS) <= ttl);
    assertEquals("PDF_COOLDOWN", reason(pdf(id, true, actor)));
    redis.delete(cacheKey);
    assertEquals("PDF_COOLDOWN", reason(pdf(id, false, actor)));
    assertEquals(1, exports(id));
    f.clock.at = firstAt.plusSeconds(180);
    HttpResponse<byte[]> next = pdf(id, false, actor);
    assertEquals(200, next.statusCode());
    assertEquals(2, exports(id));
    assertNotEquals(exportId, next.headers().firstValue("X-Export-Id").orElse(""));
    PdfFiles files = f.app.getBean(PdfFiles.class);
    assertTrue(Files.exists(files.path(exportId, false)));
    f.clock.at = firstAt.plusSeconds(300);
    f.app.getBean(SchemeExportService.class).cleanup();
    assertFalse(Files.exists(files.path(exportId, false)));
    JsonNode detail = detail(id);
    save(id, List.of(updateItem(detail.path("items").get(0).path("id").asString(), 2, null, 0)));
    assertEquals("PDF_COOLDOWN", reason(pdf(id, false, actor)));
    f.clock.at = firstAt.plusSeconds(360);
    HttpResponse<byte[]> hidden = pdf(id, true, actor);
    assertEquals(200, hidden.statusCode());
    String hid = hidden.headers().firstValue("X-Export-Id").orElseThrow();
    assertFalse(
        f.db
            .queryForObject("SELECT snapshot_json FROM scheme_export WHERE id=?", String.class, hid)
            .contains("prices"));
    assertEquals(
        detail(id).path("contentVersion").asInt(),
        f.db.queryForObject(
            "SELECT source_content_version FROM scheme_export WHERE id=?", Integer.class, hid));
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM planning_operation_record WHERE user_id=? AND"
                + " operation_code='exportSchemePdf'",
            Integer.class,
            actor.id()));
  }

  /** 在途不同价格模式不重复生成；失败计次、丢文件、每日第五次命中仍可取。 */
  @Test
  @Order(4)
  void pdfInflightFailureQuotaAndMissingFile() throws Exception {
    User user = f.user();
    String id = scheme(user);
    Instant start = f.clock.at;
    renderer.entered = new CountDownLatch(1);
    renderer.release = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<HttpResponse<byte[]>> generating = executor.submit(() -> pdf(id, false, user));
      assertTrue(renderer.entered.await(10, TimeUnit.SECONDS));
      assertEquals("PDF_PROCESSING", reason(pdf(id, true, user)));
      assertEquals(1, exports(id));
      renderer.release.countDown();
      assertEquals(200, generating.get(20, TimeUnit.SECONDS).statusCode());
    } finally {
      renderer.release.countDown();
      renderer.entered = null;
      renderer.release = null;
      executor.shutdownNow();
    }
    SchemePdfCache cache = f.app.getBean(SchemePdfCache.class);
    String key = cache.key(id, 1, false);
    String first = cache.get(key).id();
    f.app.getBean(PdfFiles.class).delete(first);
    assertEquals("PDF_COOLDOWN", reason(pdf(id, false, user)));
    assertNull(cache.get(key));
    f.clock.at = start.plusSeconds(180);
    renderer.fail = true;
    try {
      assertEquals("PDF_GENERATION_FAILED", reason(pdf(id, false, user)));
    } finally {
      renderer.fail = false;
    }
    assertEquals(2, exports(id));
    assertEquals("PDF_COOLDOWN", reason(pdf(id, false, user)));
    for (int i = 2; i < 5; i++) {
      f.clock.at = start.plusSeconds(i * 180L);
      assertEquals(200, pdf(id, false, user).statusCode());
    }
    assertEquals(5, exports(id));
    assertEquals(200, pdf(id, false, user).statusCode());
    f.clock.at = start.plusSeconds(5 * 180L);
    assertEquals("PDF_DAILY_LIMIT", reason(pdf(id, false, user)));
    assertEquals(5, exports(id));
  }

  /** 已提交processing在超时后终结原行；旧GET只读仍获权，旧版本字段拒绝。 */
  @Test
  @Order(5)
  void interruptedTaskAndLegacyBoundaries() throws Exception {
    User user = f.user();
    String id = scheme(user);
    PdfReservationService.Reservation reservation =
        f.app.getBean(PdfReservationService.class).reserve(user.id(), id, new ExportPdf(false));
    f.app.getBean(PdfFiles.class).release();
    assertEquals("PDF_PROCESSING", reason(pdf(id, false, user)));
    f.clock.at = f.clock.at.plusSeconds(120);
    f.app.getBean(SchemeExportService.class).cleanup();
    assertEquals(
        "failed",
        f.db.queryForObject(
            "SELECT export_status FROM scheme_export WHERE id=?",
            String.class,
            reservation.exportId()));
    assertEquals("PDF_COOLDOWN", reason(pdf(id, false, user)));
    assertEquals(1, exports(id));
    f.clock.at = f.clock.at.plusSeconds(60);
    HttpResponse<byte[]> result = pdf(id, false, user);
    assertEquals(200, result.statusCode());
    String eid = result.headers().firstValue("X-Export-Id").orElseThrow();
    call("GET", "/api/me/scheme-exports/" + eid, null, user, null, 200);
    call("GET", "/api/me/scheme-exports/" + eid, null, other, null, 404);
    call(
        "POST",
        "/api/me/schemes/" + id + "/exports",
        view("expectedVersion", 0, "hidePrice", false),
        user,
        null,
        400);
    assertEquals(2, exports(id));
  }

  /** 同一次并发签发只落一条链接；不同主动签发复用快照，数据库拒绝非法新数据。 */
  @Test
  @Order(6)
  void concurrentShareRetryAndDatabaseConstraints() throws Exception {
    User user = f.user();
    String id = scheme(user);
    String request = key();
    Object body = view("expectedVersion", 0, "hidePrice", true);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<JsonNode> one =
          executor.submit(
              () -> call("POST", "/api/me/schemes/" + id + "/share", body, user, request, 200));
      Future<JsonNode> two =
          executor.submit(
              () -> call("POST", "/api/me/schemes/" + id + "/share", body, user, request, 200));
      assertEquals(one.get().path("share").path("token"), two.get().path("share").path("token"));
    } finally {
      executor.shutdownNow();
    }
    JsonNode link =
        call(
                "POST",
                "/api/me/schemes/" + id + "/share",
                view("expectedVersion", 1, "hidePrice", true),
                user,
                key(),
                200)
            .path("share");
    assertEquals(
        2,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM scheme_share WHERE scheme_id=?", Integer.class, id));
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM scheme_share_snapshot WHERE scheme_id=?", Integer.class, id));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            f.db.update(
                "INSERT INTO"
                    + " scheme_share_snapshot(id,scheme_id,content_version,hide_price,snapshot_json,created_at)"
                    + " SELECT ?,scheme_id,content_version,hide_price,snapshot_json,created_at FROM"
                    + " scheme_share_snapshot WHERE scheme_id=?",
                Ids.next(),
                id));
    assertThrows(
        org.springframework.dao.DataIntegrityViolationException.class,
        () ->
            f.db.update(
                "UPDATE scheme_share SET expires_at=NULL WHERE id=?", link.path("id").asString()));
    assertEquals(
        3819,
        assertThrows(
                org.springframework.jdbc.UncategorizedSQLException.class,
                () ->
                    f.db.update(
                        "UPDATE scheme_share SET expires_at=DATE_ADD(created_at,INTERVAL 8 DAY)"
                            + " WHERE id=?",
                        link.path("id").asString()))
            .getSQLException()
            .getErrorCode());
    call(
        "POST",
        "/api/me/schemes/" + id + "/share/disable",
        view("expectedVersion", 2),
        user,
        key(),
        200);
    call(
        "GET", "/api/public/scheme-shares/" + link.path("token").asString(), null, null, null, 404);
    assertEquals("SHARE_INVALID", f.lastReason);
  }

  /** 同用户并发争最后一次额度只创建一个任务，失去额度仍可读取第五次缓存。 */
  @Test
  @Order(7)
  void concurrentFifthSixthExportQuota() throws Exception {
    User user = f.user();
    for (int i = 0; i < 4; i++) assertEquals(200, pdf(scheme(user), false, user).statusCode());
    String one = scheme(user), two = scheme(user);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    HttpResponse<byte[]> first, second;
    try {
      Future<HttpResponse<byte[]>> a = executor.submit(() -> pdf(one, false, user));
      Future<HttpResponse<byte[]>> b = executor.submit(() -> pdf(two, false, user));
      first = a.get();
      second = b.get();
    } finally {
      executor.shutdownNow();
    }
    assertEquals(
        java.util.Set.of(200, 429), java.util.Set.of(first.statusCode(), second.statusCode()));
    assertEquals("PDF_DAILY_LIMIT", reason(first.statusCode() == 429 ? first : second));
    assertEquals(
        5,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM scheme_export WHERE created_by=?", Integer.class, user.id()));
    String successful = first.statusCode() == 200 ? one : two;
    HttpResponse<byte[]> download = pdf(successful, false, user);
    assertEquals(200, download.statusCode());
    assertEquals("true", download.headers().firstValue("X-Cache-Hit").orElse(""));
    String eid = download.headers().firstValue("X-Export-Id").orElseThrow();
    assertEquals(
        3819,
        assertThrows(
                org.springframework.jdbc.UncategorizedSQLException.class,
                () ->
                    f.db.update(
                        "UPDATE scheme_export SET file_expire_at=DATE_ADD(generated_at,INTERVAL 10"
                            + " MINUTE) WHERE id=?",
                        eid))
            .getSQLException()
            .getErrorCode());
  }

  /** 共同record字段机制：方案和清单详情都必须输出原契约actions。 */
  @Test
  @Order(8)
  void detailActionWireContractAndDuplicateActions() throws Exception {
    String id = scheme(actor);
    save(id, List.of(addItem(sku, 1, 0)));
    JsonNode original = detail(id);
    String item = original.path("items").get(0).path("id").asString();
    assertTrue(original.has("actions"));
    assertFalse(original.has("schemeActions"));
    call(
        "POST",
        "/api/me/schemes/" + id + "/update",
        view("actions", List.of(updateItem(item, 2, null, 1), updateItem(item, 3, null, 2))),
        actor,
        key(),
        400);
    assertEquals(original, detail(id));
    call(
        "POST",
        "/api/me/schemes/" + id + "/update",
        view("actions", List.of(addItem(sku2, 1, 0), addItem(sku2, 2, 1))),
        actor,
        key(),
        400);
    assertEquals(original, detail(id));
    String day = now(f.clock).toLocalDate().plusDays(5).toString();
    String checklist =
        call(
                "POST",
                "/api/me/checklists",
                view("sourceType", "manual", "startDate", day, "endDate", day),
                actor,
                key(),
                200)
            .path("resourceId")
            .asString();
    JsonNode response = call("GET", "/api/me/checklists/" + checklist, null, actor, null, 200);
    assertTrue(response.has("actions"));
    assertFalse(response.has("checklistActions"));
  }
}
