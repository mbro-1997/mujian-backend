package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.security.D2Contract;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class D3PlanningTest {
  final D2CApplicationTest fixture = new D2CApplicationTest();
  final D2ApplicationTest f = fixture.f;
  User actor, other, supplier;
  String sku;

  /** 初始化本组隔离依赖。 */
  @BeforeAll
  void start() throws Exception {
    Files.createDirectories(Path.of("target/d3a-20261118/legacy-affected"));
    f.start(
        0,
        "--mj.prop.qr-worker-enabled=false",
        "--mj.planning.pdf.directory=/tmp/mujian-d3a-test-pdf",
        "--spring.session.data.redis.namespace=mujian:test:d3a:session");
    actor = f.user();
    other = f.user();
    supplier = fixture.supplier();
    JsonNode made = fixture.create(supplier, "B_CONSUMABLE", 6);
    sku = made.path("skuId").asString();
    fixture.approve("sku_audit", made.path("targetId").asString(), 0);
    f.store.visualSigner = (key, until) -> "https://example.invalid/" + key;
  }

  /** 释放本组创建的资源。 */
  @AfterAll
  void stop() {
    if (f.app != null) {
      com.mujian.backend.scheme.service.PdfFiles pdfFiles =
          f.app.getBean(com.mujian.backend.scheme.service.PdfFiles.class);
      org.springframework.data.redis.core.StringRedisTemplate redis =
          f.app.getBean(org.springframework.data.redis.core.StringRedisTemplate.class);
      for (String user : f.createdUsers) {
        for (String id :
            f.db.queryForList(
                "SELECT id FROM scheme_export WHERE created_by=?", String.class, user)) {
          try {
            pdfFiles.delete(id);
          } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
          }
        }
        for (String id :
            f.db.queryForList("SELECT id FROM scheme WHERE owner_user_id=?", String.class, user)) {
          Set<String> keys = redis.keys("mj:test:scheme-pdf-export:" + id + ":*");
          if (keys != null && !keys.isEmpty()) redis.delete(keys);
        }
      }
      D3BFixtureCleanup.remove(f);
    }
    fixture.stop();
  }

  /** 生成本次用例的独立标识。 */
  String key() {
    return UUID.randomUUID().toString();
  }

  /** 执行请求并检查预期响应。 */
  JsonNode call(String method, String path, Object body, User user, String key, int status)
      throws Exception {
    JsonNode data = f.call(method, path, body, user, key, status);
    if (status == 200) {
      D2Contract contract = f.app.getBean(D2Contract.class);
      D2Contract.Route route = contract.route(path.split("\\?")[0], method);
      contract.validate(
          route
              .operation()
              .path("responses")
              .path("200")
              .path("content")
              .path("application/json")
              .path("schema"),
          f.json.valueToTree(
              view("code", "OK", "message", "成功", "data", data, "traceId", Ids.next())));
    }
    if (status == 200) {
      Path evidence = Path.of("target/d3a-20261118/legacy-affected/http-success.jsonl");
      Files.createDirectories(evidence.getParent());
      Files.writeString(
          evidence,
          f.json.writeValueAsString(
                  view("method", method, "path", path, "status", status, "data", data))
              + "\n",
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    }
    return data;
  }

  /** 读取指定测试资源。 */
  JsonNode get(String path, User user) throws Exception {
    return call("GET", path, null, user, null, 200);
  }

  /** 以测试身份提交请求。 */
  JsonNode post(String path, Object body, User user) throws Exception {
    return call("POST", path, body, user, key(), 200);
  }

  /** 构造供测试使用的方案。 */
  String scheme(User user) throws Exception {
    return post("/api/me/schemes", view("name", "中文道具方案", "description", null), user)
        .path("resourceId")
        .asString();
  }

  /** 静态入口和同源匿名API使用实际隔离HTTP，浏览器验证关闭、隐藏及返回。 */
  @Test
  void independentHtmlShareUsesExactStaticBoundaryAndRealApi() throws Exception {
    for (String resource : List.of("scheme.html", "scheme.js", "scheme.css")) {
      HttpResponse<byte[]> response = raw("GET", "/share/" + resource, null, null, null);
      assertEquals(200, response.statusCode());
      assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(""));
      assertTrue(
          response
              .headers()
              .firstValue("Content-Security-Policy")
              .orElse("")
              .contains("connect-src 'self'"));
    }
    assertEquals(404, raw("POST", "/share/scheme.html", null, null, null).statusCode());
    assertEquals(404, raw("GET", "/share/private.js", null, null, null).statusCode());
    assertNotEquals(
        200,
        raw("POST", "/api/public/scheme-shares/" + "a".repeat(43), view(), null, key())
            .statusCode());
    User user = f.user();
    String id = scheme(user);
    post(
        "/api/me/schemes/" + id + "/items",
        view("expectedVersion", 0, "skuId", sku, "quantity", 1, "remark", "HTML只读验证"),
        user);
    Process process =
        new ProcessBuilder(
                "/Users/mbro/.local/nodejs/current/bin/node",
                "../mujian-frontend/tests/html-share-browser.cjs")
            .redirectOutput(
                Path.of("target/d3a-20261118/legacy-affected/html-share-browser.log").toFile())
            .redirectErrorStream(true)
            .start();
    try (java.io.OutputStream input = process.getOutputStream()) {
      input.write(
          f.json.writeValueAsBytes(
              view(
                  "base",
                  f.base,
                  "id",
                  id,
                  "owner",
                  user.token(),
                  "evidence",
                  "target/d3a-20261118/legacy-affected")));
    }
    boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
    if (!finished) process.destroyForcibly();
    assertTrue(finished, "browser verification timeout");
    assertEquals(0, process.exitValue(), "see redacted browser log");
  }

  /** 验证主链契约、身份隔离和请求回放。 */
  @Test
  void mainChainSchemaIsolationAndReplay() throws Exception {
    String id = scheme(actor), base = "/api/me/schemes/" + id;
    String request = key();
    Object input = view("expectedVersion", 0, "skuId", sku, "quantity", 2, "remark", null);
    post(base + "/items", input, actor);
    JsonNode detail = get(base, actor);
    assertEquals(1, detail.path("items").size());
    assertEquals(20000, detail.path("estimatedDailyRentFen").asInt());
    String item = detail.path("items").get(0).path("id").asString();
    post(base + "/items/reorder", view("expectedVersion", 1, "itemIds", List.of(item)), actor);
    call(
        "POST",
        base + "/update",
        view(
            "actions",
            List.of(
                view("action", "updateSchemeInfo", "name", "保存后", "description", null),
                view(
                    "action",
                    "updateItem",
                    "itemId",
                    item,
                    "quantity",
                    3,
                    "remark",
                    "备注",
                    "sortOrder",
                    0))),
        actor,
        request,
        200);
    call(
        "POST",
        base + "/update",
        view(
            "actions",
            List.of(
                view("action", "updateSchemeInfo", "name", "保存后", "description", null),
                view(
                    "action",
                    "updateItem",
                    "itemId",
                    item,
                    "quantity",
                    3,
                    "remark",
                    "备注",
                    "sortOrder",
                    0))),
        actor,
        request,
        200);
    assertEquals(
        "recovered",
        get("/api/me/planning-operations/by-request/" + request, actor).path("outcome").asString());
    post(base + "/share", view("expectedVersion", 2, "hidePrice", true), actor);
    String token = get(base, actor).path("share").path("token").asString();
    JsonNode shared = get("/api/public/scheme-shares/" + token, null);
    assertFalse(shared.toString().contains("prices"));
    assertFalse(shared.toString().contains("RentFen"));
    call("GET", base, null, other, null, 404);
    call("GET", base, null, supplier, null, 403);
    String start = now(f.clock).toLocalDate().plusDays(20).toString(),
        end = now(f.clock).toLocalDate().plusDays(24).toString();
    String checklist =
        post(
                "/api/me/checklists",
                view(
                    "sourceType",
                    "scheme",
                    "schemeId",
                    id,
                    "expectedSchemeVersion",
                    3,
                    "startDate",
                    start,
                    "endDate",
                    end),
                actor)
            .path("resourceId")
            .asString();
    JsonNode cd = get("/api/me/checklists/" + checklist, actor);
    assertEquals("RENTAL_WINDOW_NOT_OPEN", cd.path("submission").path("reasonCode").asString());
    assertEquals(150000, cd.path("rentEstimateFen").asInt());
    assertEquals(
        checklist,
        get("/api/me/planning-context", actor).path("currentChecklist").path("id").asString());
    get("/api/me/schemes", actor);
    get("/api/me/checklists", actor);
    String address =
        post(
                "/api/me/addresses",
                view(
                    "receiverName",
                    "联系人",
                    "phone",
                    "13800000001",
                    "regionText",
                    "上海市 静安区",
                    "detailAddress",
                    "测试路1号",
                    "isDefault",
                    true),
                actor)
            .path("resourceId")
            .asString();
    get("/api/me/addresses", actor);
    get("/api/me/addresses/" + address, actor);
    post(
        "/api/me/addresses/" + address + "/update",
        view(
            "expectedVersion",
            0,
            "receiverName",
            "联系人二",
            "phone",
            "13800000002",
            "regionText",
            "上海市 静安区",
            "detailAddress",
            "测试路2号",
            "isDefault",
            true),
        actor);
    post("/api/me/addresses/" + address + "/set-default", view("expectedVersion", 1), actor);
    String deleteKey = key();
    call(
        "POST",
        "/api/me/addresses/" + address + "/delete",
        view("expectedVersion", 1),
        actor,
        deleteKey,
        200);
    assertEquals(
        "replayed",
        call(
                "POST",
                "/api/me/addresses/" + address + "/delete",
                view("expectedVersion", 1),
                actor,
                deleteKey,
                200)
            .path("outcome")
            .asString());
    call("GET", "/api/me/addresses/" + address, null, actor, null, 404);
    post(base + "/share/disable", view("expectedVersion", 3), actor);
    call("GET", "/api/public/scheme-shares/" + token, null, null, null, 404);
    assertEquals("SHARE_INVALID", f.lastReason);
    post(base + "/archive", view("expectedVersion", 4), actor);
    call(
        "POST",
        base + "/items",
        view("expectedVersion", 5, "skuId", sku, "quantity", 1, "remark", null),
        actor,
        key(),
        409);
    assertEquals("SCHEME_ARCHIVED", f.lastReason);
  }

  /** 读取未转换的原始响应。 */
  HttpResponse<byte[]> raw(String method, String path, Object body, User user, String request)
      throws Exception {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(f.base + path))
            .timeout(java.time.Duration.ofSeconds(130));
    if (user != null) builder.header("Authorization", "Bearer " + user.token());
    if (request != null) builder.header("Idempotency-Key", request);
    if (body != null) builder.header("Content-Type", "application/json");
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(f.json.writeValueAsString(body)));
    return f.http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
  }

  /** 并发检查槽位与默认值的版本约束。 */
  @Test
  void slotsConcurrentAndDefaultsVersioned() throws Exception {
    User user = f.user();
    java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newFixedThreadPool(6);
    try {
      List<java.util.concurrent.Callable<Integer>> calls = new ArrayList<>();
      for (int i = 0; i < 11; i++)
        calls.add(
            () ->
                raw(
                        "POST",
                        "/api/me/schemes",
                        view("name", "并发槽位", "description", null),
                        user,
                        key())
                    .statusCode());
      List<java.util.concurrent.Future<Integer>> results = executor.invokeAll(calls);
      int ok = 0, conflict = 0;
      for (java.util.concurrent.Future<Integer> result : results) {
        int status = result.get();
        if (status == 200) ok++;
        else if (status == 409) conflict++;
        else fail("unexpected " + status);
      }
      assertEquals(10, ok);
      assertEquals(1, conflict);
      String date = now(f.clock).toLocalDate().plusDays(10).toString();
      calls.clear();
      for (int i = 0; i < 4; i++)
        calls.add(
            () ->
                raw(
                        "POST",
                        "/api/me/checklists",
                        view("sourceType", "manual", "startDate", date, "endDate", date),
                        user,
                        key())
                    .statusCode());
      results = executor.invokeAll(calls);
      ok = 0;
      conflict = 0;
      for (java.util.concurrent.Future<Integer> result : results) {
        if (result.get() == 200) ok++;
        else if (result.get() == 409) conflict++;
        else fail("unexpected status");
      }
      assertEquals(3, ok);
      assertEquals(1, conflict);
      assertEquals(
          1,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM rental_checklist WHERE owner_user_id=? AND current_owner_guard"
                  + " IS NOT NULL",
              Integer.class,
              user.id()));
      JsonNode checks = get("/api/me/checklists", user);
      for (JsonNode row : checks.path("items"))
        if (row.path("status").asString().equals("suspended"))
          post(
              "/api/me/checklists/" + row.path("id").asString() + "/activate",
              view("expectedVersion", row.path("version").asInt()),
              user);
      String a =
          post(
                  "/api/me/addresses",
                  view(
                      "receiverName",
                      "甲",
                      "phone",
                      "13800000001",
                      "regionText",
                      "上海",
                      "detailAddress",
                      "一号",
                      "isDefault",
                      false),
                  user)
              .path("resourceId")
              .asString();
      String b =
          post(
                  "/api/me/addresses",
                  view(
                      "receiverName",
                      "乙",
                      "phone",
                      "13800000002",
                      "regionText",
                      "上海",
                      "detailAddress",
                      "二号",
                      "isDefault",
                      false),
                  user)
              .path("resourceId")
              .asString();
      results =
          executor.invokeAll(
              List.of(
                  () ->
                      raw(
                              "POST",
                              "/api/me/addresses/" + a + "/set-default",
                              view("expectedVersion", 0),
                              user,
                              key())
                          .statusCode(),
                  () ->
                      raw(
                              "POST",
                              "/api/me/addresses/" + b + "/set-default",
                              view("expectedVersion", 0),
                              user,
                              key())
                          .statusCode()));
      for (java.util.concurrent.Future<Integer> result : results) assertEquals(200, result.get());
      assertEquals(
          1,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM shipping_address WHERE user_id=? AND is_default=1",
              Integer.class,
              user.id()));
      call(
          "POST",
          "/api/me/addresses/" + a + "/delete",
          view("expectedVersion", 0),
          user,
          key(),
          409);
      assertEquals("VERSION_CONFLICT", f.lastReason);
      User racer = f.user();
      String first =
          post(
                  "/api/me/checklists",
                  view("sourceType", "manual", "startDate", date, "endDate", date),
                  racer)
              .path("resourceId")
              .asString();
      post(
          "/api/me/checklists",
          view("sourceType", "manual", "startDate", date, "endDate", date),
          racer);
      int firstVersion = get("/api/me/checklists/" + first, racer).path("version").asInt();
      results =
          executor.invokeAll(
              List.of(
                  () ->
                      raw(
                              "POST",
                              "/api/me/checklists",
                              view("sourceType", "manual", "startDate", date, "endDate", date),
                              racer,
                              key())
                          .statusCode(),
                  () ->
                      raw(
                              "POST",
                              "/api/me/checklists/" + first + "/activate",
                              view("expectedVersion", firstVersion),
                              racer,
                              key())
                          .statusCode()));
      for (java.util.concurrent.Future<Integer> result : results) assertEquals(200, result.get());
      assertEquals(
          3,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM rental_checklist WHERE owner_user_id=?",
              Integer.class,
              racer.id()));
      assertEquals(
          1,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM rental_checklist WHERE current_owner_guard=?",
              Integer.class,
              racer.id()));
    } finally {
      executor.shutdownNow();
    }
  }

  /** 验证整单保存回滚且查询租期不会创建占用。 */
  @Test
  void fullSaveRollbackDatesAndInventoryAreNotReservations() throws Exception {
    User user = f.user();
    String id = scheme(user), base = "/api/me/schemes/" + id;
    post(
        base + "/items",
        view("expectedVersion", 0, "skuId", sku, "quantity", 1, "remark", null),
        user);
    String originalItem = get(base, user).path("items").get(0).path("id").asString();
    call(
        "POST",
        base + "/update",
        view(
            "actions",
            List.of(
                view("action", "updateSchemeInfo", "name", "不应保存", "description", null),
                view(
                    "action",
                    "updateItem",
                    "itemId",
                    originalItem,
                    "quantity",
                    2,
                    "remark",
                    null,
                    "sortOrder",
                    0),
                view(
                    "action",
                    "addItem",
                    "skuId",
                    Ids.next(),
                    "quantity",
                    1,
                    "remark",
                    null,
                    "sortOrder",
                    1))),
        user,
        key(),
        409);
    assertEquals("中文道具方案", get(base, user).path("name").asString());
    assertEquals(1, get(base, user).path("version").asInt());
    List<Map<String, Object>> items = new ArrayList<>();
    String columns =
        "factory_id,prop_name,asset_type,image_url,type_code,size_desc,length_cm,width_cm,height_cm,material_desc,fire_resistant_code,weight_kg,transport_suggestion,daily_rent_price_fen,deposit_amount_fen,sku_status,fill_status,remark,created_at,version,updated_at";
    for (int index = 0; index < 15; index++) {
      String copy = Ids.next();
      f.db.update(
          "INSERT INTO prop_sku(id,sku_code,"
              + columns
              + ") SELECT ?,?,"
              + columns
              + " FROM prop_sku WHERE id=?",
          copy,
          "D3" + copy.substring(0, 18),
          sku);
      items.add(
          view(
              "action",
              "addItem",
              "skuId",
              copy,
              "quantity",
              1,
              "remark",
              "中".repeat(255),
              "sortOrder",
              index));
    }
    items.add(view("action", "removeItem", "itemId", originalItem));
    items.add(view("action", "updateSchemeInfo", "name", "完整大包体", "description", null));
    Map<String, Object> body = view("actions", items);
    assertTrue(f.json.writeValueAsBytes(body).length > 8192);
    post(base + "/update", body, user);
    assertEquals(15, get(base, user).path("items").size());
    HttpResponse<byte[]> tooLarge =
        raw(
            "POST",
            base + "/update",
            view(
                "actions",
                List.of(
                    view(
                        "action",
                        "updateSchemeInfo",
                        "name",
                        "不应写入",
                        "description",
                        "中".repeat(400000)))),
            user,
            key());
    assertEquals(413, tooLarge.statusCode());
    assertEquals(2, get(base, user).path("version").asInt());
    String date = now(f.clock).toLocalDate().plusDays(20).toString();
    String end = now(f.clock).toLocalDate().plusDays(140).toString();
    call(
        "POST",
        "/api/me/checklists",
        view("sourceType", "manual", "startDate", date, "endDate", end),
        user,
        key(),
        400);
    String list =
        post(
                "/api/me/checklists",
                view(
                    "sourceType",
                    "manual",
                    "startDate",
                    date,
                    "endDate",
                    now(f.clock).toLocalDate().plusDays(139).toString()),
                user)
            .path("resourceId")
            .asString();
    String cb = "/api/me/checklists/" + list;
    post(
        cb + "/items",
        view("expectedVersion", 0, "skuId", sku, "quantity", 6, "remark", null),
        user);
    JsonNode detail = get(cb, user);
    String item = detail.path("groups").get(0).path("items").get(0).path("id").asString();
    post(
        cb + "/items/" + item + "/update",
        view("expectedVersion", 1, "quantity", 5, "remark", "整理"),
        user);
    call(
        "POST",
        cb + "/save",
        view(
            "expectedVersion",
            2,
            "items",
            List.of(
                view("itemId", item, "quantity", 1, "remark", null),
                view("itemId", Ids.next(), "quantity", 1, "remark", null))),
        user,
        key(),
        404);
    assertEquals(2, get(cb, user).path("version").asInt());
    f.db.update("UPDATE prop_sku SET sku_status='offline' WHERE id=?", sku);
    post(
        cb + "/save",
        view(
            "expectedVersion",
            2,
            "items",
            List.of(view("itemId", item, "quantity", 3, "remark", "下架后减量"))),
        user);
    post(cb + "/items/" + item + "/remove", view("expectedVersion", 3), user);
    f.db.update("UPDATE prop_sku SET sku_status='online' WHERE id=?", sku);
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM inventory_reservation WHERE sku_id=?", Integer.class, sku));
  }

  /** 检查真实PDF、三分钟缓存与五分钟文件、同POST重试。 */
  @Test
  void pdfRealFileCacheHiddenExpiryAndOriginalReplay() throws Exception {
    User user = f.user();
    String id = scheme(user), base = "/api/me/schemes/" + id;
    post(
        base + "/items",
        view("expectedVersion", 0, "skuId", sku, "quantity", 2, "remark", "中文备注"),
        user);
    String request = key();
    HttpResponse<byte[]> generated =
        raw("POST", base + "/exports", view("hidePrice", false), user, request);
    assertEquals(200, generated.statusCode(), () -> new String(generated.body()));
    assertEquals("application/pdf", generated.headers().firstValue("Content-Type").orElse(""));
    assertEquals("%PDF-", new String(generated.body(), 0, 5));
    String export = generated.headers().firstValue("X-Export-Id").orElseThrow();
    assertEquals("false", generated.headers().firstValue("X-Cache-Hit").orElseThrow());
    Path evidence = Path.of("target/d3a-20261118/legacy-affected");
    Files.createDirectories(evidence);
    Files.write(evidence.resolve("scheme-priced.pdf"), generated.body());
    try (org.apache.pdfbox.pdmodel.PDDocument document =
        org.apache.pdfbox.Loader.loadPDF(generated.body())) {
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
      assertTrue(text.contains("中文道具方案"));
      assertTrue(text.contains("幕间方案参考"));
      assertTrue(text.contains("日租"));
    }
    f.clock.at = f.clock.at.plusSeconds(120);
    HttpResponse<byte[]> cached =
        raw("POST", base + "/exports", view("hidePrice", false), user, key());
    assertEquals(200, cached.statusCode());
    assertEquals(export, cached.headers().firstValue("X-Export-Id").orElseThrow());
    assertArrayEquals(generated.body(), cached.body());
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM scheme_export WHERE created_by=?", Integer.class, user.id()));
    f.clock.at = f.clock.at.plusSeconds(60);
    HttpResponse<byte[]> hidden =
        raw("POST", base + "/exports", view("hidePrice", true), user, key());
    assertEquals(200, hidden.statusCode(), () -> new String(hidden.body()));
    Files.write(evidence.resolve("scheme-hidden.pdf"), hidden.body());
    String hiddenId = hidden.headers().firstValue("X-Export-Id").orElseThrow();
    assertFalse(
        f.db
            .queryForObject(
                "SELECT snapshot_json FROM scheme_export WHERE id=?", String.class, hiddenId)
            .contains("prices"));
    try (org.apache.pdfbox.pdmodel.PDDocument document =
        org.apache.pdfbox.Loader.loadPDF(hidden.body())) {
      String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);
      assertFalse(text.contains("日租"));
      assertFalse(text.contains("押金"));
    }
    f.clock.at = f.clock.at.plusSeconds(300);
    assertFalse(get("/api/me/scheme-exports/" + export, user).path("canDownload").asBoolean());
    assertEquals(
        409,
        raw("GET", "/api/me/scheme-exports/" + export + "/file", null, user, null).statusCode());
    assertEquals(
        200, raw("POST", base + "/exports", view("hidePrice", false), user, null).statusCode());
    assertEquals(
        3,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM scheme_export WHERE created_by=?", Integer.class, user.id()));
    assertEquals(
        404,
        raw("GET", "/api/me/scheme-exports/" + hiddenId + "/file", null, other, null).statusCode());
  }

  /** 用可控时间验证并发额度、跨日及冷却。 */
  @Test
  void pdfQuotaConcurrencyMidnightAndCooldownBoundaries() throws Exception {
    java.time.Instant original = f.clock.at;
    java.util.concurrent.ExecutorService executor =
        java.util.concurrent.Executors.newFixedThreadPool(2);
    try {
      f.clock.at = now(f.clock).toLocalDate().atTime(23, 58).atZone(ZONE).toInstant();
      User user = f.user();
      List<String> schemes = new ArrayList<>();
      for (int i = 0; i < 6; i++) schemes.add(scheme(user));
      for (int i = 0; i < 4; i++)
        assertEquals(
            200,
            raw(
                    "POST",
                    "/api/me/schemes/" + schemes.get(i) + "/exports",
                    view("hidePrice", false),
                    user,
                    key())
                .statusCode());
      List<java.util.concurrent.Future<Integer>> results =
          executor.invokeAll(
              List.of(
                  () ->
                      raw(
                              "POST",
                              "/api/me/schemes/" + schemes.get(4) + "/exports",
                              view("hidePrice", false),
                              user,
                              key())
                          .statusCode(),
                  () ->
                      raw(
                              "POST",
                              "/api/me/schemes/" + schemes.get(5) + "/exports",
                              view("hidePrice", false),
                              user,
                              key())
                          .statusCode()));
      Set<Integer> statuses = new HashSet<>();
      for (java.util.concurrent.Future<Integer> result : results) statuses.add(result.get());
      assertEquals(Set.of(200, 429), statuses);
      assertEquals(
          5,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM scheme_export WHERE created_by=?", Integer.class, user.id()));
      String base = "/api/me/schemes/" + schemes.get(0) + "/exports";
      assertEquals(200, raw("POST", base, view("hidePrice", false), user, key()).statusCode());
      f.clock.at = f.clock.at.plusSeconds(120); // 上海00:00，配额刷新但同方案仍需等到原尝试+180秒。
      assertEquals(429, raw("POST", base, view("hidePrice", true), user, key()).statusCode());
      f.clock.at = f.clock.at.plusSeconds(59);
      assertEquals(429, raw("POST", base, view("hidePrice", true), user, key()).statusCode());
      f.clock.at = f.clock.at.plusSeconds(1);
      assertEquals(200, raw("POST", base, view("hidePrice", true), user, key()).statusCode());
      assertEquals(
          6,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM scheme_export WHERE created_by=?", Integer.class, user.id()));
    } finally {
      f.clock.at = original;
      executor.shutdownNow();
    }
  }

  /** 检查 PDF 容量不足、文件缺失及中断恢复。 */
  @Test
  void pdfCapacityFailureMissingFileAndCrashRecovery() throws Exception {
    com.mujian.backend.scheme.service.PdfFiles files =
        f.app.getBean(com.mujian.backend.scheme.service.PdfFiles.class);
    com.mujian.backend.scheme.mapper.SchemeExportMapper schemeExportMapper =
        f.app.getBean(com.mujian.backend.scheme.mapper.SchemeExportMapper.class);
    com.mujian.backend.scheme.service.SchemeExportService schemeExportService =
        f.app.getBean(com.mujian.backend.scheme.service.SchemeExportService.class);
    java.time.Instant original = f.clock.at;
    User user = f.user();
    String id = scheme(user), base = "/api/me/schemes/" + id + "/exports";
    assertTrue(files.acquire());
    assertTrue(files.acquire());
    try {
      assertEquals(503, raw("POST", base, view("hidePrice", false), user, key()).statusCode());
      assertEquals(
          0,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM scheme_export WHERE created_by=?", Integer.class, user.id()));
    } finally {
      files.release();
      files.release();
    }
    String failedKey = key();
    try {
      org.springframework.test.util.ReflectionTestUtils.setField(files, "maxFileBytes", 128L);
      assertEquals(503, raw("POST", base, view("hidePrice", false), user, failedKey).statusCode());
    } finally {
      org.springframework.test.util.ReflectionTestUtils.setField(files, "maxFileBytes", 20971520L);
    }
    String failed =
        f.db.queryForObject("SELECT id FROM scheme_export WHERE scheme_id=?", String.class, id);
    assertEquals("failed", get("/api/me/scheme-exports/" + failed, user).path("status").asString());
    assertFalse(Files.exists(files.path(failed, true)));
    assertFalse(Files.exists(files.path(failed, false)));
    assertEquals(429, raw("POST", base, view("hidePrice", false), user, failedKey).statusCode());
    assertEquals(429, raw("POST", base, view("hidePrice", false), user, key()).statusCode());
    try {
      f.clock.at = f.clock.at.plusSeconds(180);
      HttpResponse<byte[]> success = raw("POST", base, view("hidePrice", false), user, key());
      assertEquals(200, success.statusCode());
      String export = success.headers().firstValue("X-Export-Id").orElseThrow();
      // 下载持有真实文件读租约时，清理线程不能夺锁删除。
      try (com.mujian.backend.scheme.service.PdfFiles.Lease lease =
          files.open(schemeExportMapper.selectExportById(export))) {
        java.util.concurrent.ExecutorService executor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
          assertFalse(
              executor
                  .submit(
                      () -> {
                        java.util.concurrent.locks.ReentrantLock lock = files.lock(export);
                        boolean acquired = lock.tryLock();
                        if (acquired) lock.unlock();
                        return acquired;
                      })
                  .get());
          assertEquals('%', lease.input().read());
        } finally {
          executor.shutdownNow();
        }
      }
      Files.delete(files.path(export, false));
      assertEquals(
          "success", get("/api/me/scheme-exports/" + export, user).path("status").asString());
      assertFalse(get("/api/me/scheme-exports/" + export, user).path("canDownload").asBoolean());
      assertEquals(
          409,
          raw("GET", "/api/me/scheme-exports/" + export + "/file", null, user, null).statusCode());
      f.clock.at = f.clock.at.plusSeconds(180);
      String crashKey = key();
      com.mujian.backend.scheme.service.PdfReservationService.Reservation reservation =
          f.app
              .getBean(com.mujian.backend.scheme.service.PdfReservationService.class)
              .reserve(
                  user.id(), id, new com.mujian.backend.scheme.dto.SchemeDtos.ExportPdf(false));
      files.release(); // 模拟进程在预留提交后退出；持久processing和.tmp仍在。
      Files.writeString(files.path(reservation.exportId(), true), "unfinished");
      call("POST", base, view("hidePrice", false), user, crashKey, 409);
      assertEquals("PDF_PROCESSING", f.lastReason);
      assertEquals(409, raw("POST", base, view("hidePrice", true), user, key()).statusCode());
      assertEquals(
          "processing",
          get("/api/me/scheme-exports/" + reservation.exportId(), user).path("status").asString());
      f.clock.at = f.clock.at.plusSeconds(120);
      ((com.mujian.backend.scheme.service.impl.SchemeExportServiceImpl) schemeExportService)
          .cleanup();
      assertEquals(
          "failed", schemeExportMapper.selectExportById(reservation.exportId()).exportStatus());
      assertEquals(
          "PDF_GENERATION_TIMEOUT",
          schemeExportMapper.selectExportById(reservation.exportId()).errorMessage());
      assertFalse(Files.exists(files.path(reservation.exportId(), true)));
      assertEquals(
          0,
          schemeExportMapper.completeExport(
              reservation.exportId(),
              now(f.clock),
              now(f.clock).plusMinutes(5),
              reservation.exportId() + ".pdf"));
      f.clock.at = f.clock.at.plus(java.time.Duration.ofDays(7));
      ((com.mujian.backend.scheme.service.impl.SchemeExportServiceImpl) schemeExportService)
          .cleanup();
      assertNull(schemeExportMapper.selectExportById(reservation.exportId()));
      assertEquals(200, raw("POST", base, view("hidePrice", false), user, null).statusCode());
      call("GET", "/api/me/planning-operations/by-request/" + crashKey, null, user, null, 404);
    } finally {
      f.clock.at = original;
    }
  }

  /** 检查多页 PDF 和规范化请求的等价性。 */
  @Test
  void pdfMultiplePagesAndNormalizedIntent() throws Exception {
    User user = f.user();
    String id = scheme(user), base = "/api/me/schemes/" + id, request = key();
    Map<String, Object> body =
        view(
            "actions",
            List.of(
                view("action", "updateSchemeInfo", "name", " 多页方案 ", "description", ""),
                view(
                    "action",
                    "addItem",
                    "skuId",
                    sku,
                    "quantity",
                    1,
                    "remark",
                    "",
                    "sortOrder",
                    0)));
    call("POST", base + "/update", body, user, request, 200);
    assertEquals(
        "replayed",
        call(
                "POST",
                base + "/update",
                view(
                    "actions",
                    List.of(
                        view("description", null, "name", "多页方案", "action", "updateSchemeInfo"),
                        view(
                            "remark",
                            null,
                            "quantity",
                            1,
                            "skuId",
                            sku,
                            "sortOrder",
                            0,
                            "action",
                            "addItem"))),
                user,
                request,
                200)
            .path("outcome")
            .asString());
    call("POST", base + "/update", view("actions", List.of()), user, request, 409);
    assertEquals("IDEMPOTENCY_KEY_REUSED", f.lastReason);
    List<com.mujian.backend.scheme.domain.PdfSnapshot.Item> items = new ArrayList<>();
    com.mujian.backend.planning.domain.SnapshotV1 snapshot =
        f.app
            .getBean(com.mujian.backend.planning.service.PlanningSnapshots.class)
            .current(List.of(sku))
            .get(sku)
            .snapshot();
    for (int i = 0; i < 24; i++)
      items.add(
          new com.mujian.backend.scheme.domain.PdfSnapshot.Item(
              "跨页中文道具 " + i,
              "测试工厂",
              snapshot.typeLabel(),
              snapshot.styleLabels(),
              "大号规格 / 200厘米",
              "实木材质",
              List.of(),
              3,
              "备注不可截断 ".repeat(25),
              snapshot.prices()));
    com.mujian.backend.scheme.domain.PdfSnapshot pdf =
        new com.mujian.backend.scheme.domain.PdfSnapshot(
            "完整跨页中文方案",
            "方案简介 ".repeat(90),
            1,
            "scheme_v1",
            false,
            "幕间方案参考 / MUHOU",
            time(now(f.clock)),
            "测试导出人",
            items);
    Path path = Path.of("target/d3a-20261118/legacy-affected/scheme-multipage.pdf");
    try (java.io.OutputStream output = Files.newOutputStream(path)) {
      f.app
          .getBean(com.mujian.backend.scheme.service.SchemePdfRenderer.class)
          .render(pdf, output, f.clock.instant().plusSeconds(120));
    }
    try (org.apache.pdfbox.pdmodel.PDDocument document =
        org.apache.pdfbox.Loader.loadPDF(path.toFile())) {
      assertTrue(document.getNumberOfPages() > 2);
      org.apache.pdfbox.text.PDFTextStripper stripper =
          new org.apache.pdfbox.text.PDFTextStripper();
      for (int i = 1; i <= document.getNumberOfPages(); i++) {
        stripper.setStartPage(i);
        stripper.setEndPage(i);
        String text = stripper.getText(document);
        assertTrue(text.contains("幕间方案参考"));
        assertTrue(text.contains("第 " + i + " / "));
      }
    }
  }

  /** 检查过期日期、不可用副本和清理边界。 */
  @Test
  void datesPastCleanupUnavailableCopiesAndStrictBoundary() throws Exception {
    java.time.Instant original = f.clock.at;
    User user = f.user(), second = f.user();
    String scheme = scheme(user), base = "/api/me/schemes/" + scheme;
    post(
        base + "/items",
        view("expectedVersion", 0, "skuId", sku, "quantity", 7, "remark", null),
        user);
    String member = get(base, user).path("items").get(0).path("id").asString();
    String start = now(f.clock).toLocalDate().plusDays(20).toString();
    String id =
        post(
                "/api/me/checklists",
                view(
                    "sourceType",
                    "scheme",
                    "schemeId",
                    scheme,
                    "expectedSchemeVersion",
                    1,
                    "startDate",
                    start,
                    "endDate",
                    start),
                user)
            .path("resourceId")
            .asString();
    String cb = "/api/me/checklists/" + id;
    JsonNode copied = get(cb, user);
    assertEquals(7, copied.path("groups").get(0).path("items").get(0).path("quantity").asInt());
    assertEquals(
        "STOCK_NOT_ENOUGH",
        copied
            .path("groups")
            .get(0)
            .path("items")
            .get(0)
            .path("availability")
            .path("reasonCode")
            .asString());
    String item = copied.path("groups").get(0).path("items").get(0).path("id").asString();
    post(
        cb + "/save",
        view(
            "expectedVersion",
            0,
            "items",
            List.of(view("itemId", item, "quantity", 6, "remark", null))),
        user);
    String otherList =
        post(
                "/api/me/checklists",
                view("sourceType", "manual", "startDate", start, "endDate", start),
                second)
            .path("resourceId")
            .asString();
    post(
        "/api/me/checklists/" + otherList + "/items",
        view("expectedVersion", 0, "skuId", sku, "quantity", 6, "remark", null),
        second);
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM inventory_reservation WHERE sku_id=?", Integer.class, sku));
    try {
      java.time.LocalDate date = java.time.LocalDate.parse(start);
      f.clock.at = date.minusDays(14).atStartOfDay(ZONE).toInstant();
      assertEquals(
          "TRADING_NOT_ENABLED", get(cb, user).path("submission").path("reasonCode").asString());
      f.clock.at = date.minusDays(2).atTime(10, 59, 59).atZone(ZONE).toInstant();
      assertEquals(
          "TRADING_NOT_ENABLED", get(cb, user).path("submission").path("reasonCode").asString());
      f.clock.at = f.clock.at.plusSeconds(1);
      assertEquals(
          "RENTAL_WINDOW_CLOSED", get(cb, user).path("submission").path("reasonCode").asString());
      f.clock.at = date.plusDays(1).atStartOfDay(ZONE).toInstant();
      post(
          cb + "/save",
          view(
              "expectedVersion",
              1,
              "items",
              List.of(view("itemId", item, "quantity", 5, "remark", "过去租期整理"))),
          user);
      call(
          "POST",
          cb + "/items/" + item + "/update",
          view("expectedVersion", 2, "quantity", 6, "remark", null),
          user,
          key(),
          409);
      assertEquals("RENTAL_DATE_PASSED", f.lastReason);
    } finally {
      f.clock.at = original;
    }
    post(base + "/items/" + member + "/remove", view("expectedVersion", 1), user);
    assertEquals(0, get(base, user).path("items").size());
    HttpRequest duplicate =
        HttpRequest.newBuilder(URI.create(f.base + "/api/me/schemes"))
            .header("Authorization", "Bearer " + user.token())
            .header("Idempotency-Key", key())
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"name\":\"第一\",\"name\":\"第二\",\"description\":null}"))
            .build();
    assertEquals(400, f.http.send(duplicate, HttpResponse.BodyHandlers.ofString()).statusCode());
    call("GET", "/api/me/schemes?page=1&page=2", null, user, null, 400);
    call(
        "POST",
        "/api/me/schemes",
        view("name", "越权字段", "description", null, "ownerUserId", second.id()),
        user,
        key(),
        400);
    call("GET", base, null, null, null, 401);
    post(base + "/share", view("expectedVersion", 2, "hidePrice", false), user);
    String token = get(base, user).path("share").path("token").asString();
    assertTrue(get("/api/public/scheme-shares/" + token, null).has("estimatedDailyRentFen"));
    post(base + "/share/disable", view("expectedVersion", 3), user);
    post(base + "/share", view("expectedVersion", 4, "hidePrice", false), user);
    assertNotEquals(token, get(base, user).path("share").path("token").asString());
    call("GET", "/api/public/scheme-shares/" + token, null, null, null, 404);
  }

  /** 确认已开始的文件响应保持原始 PDF 流。 */
  @Test
  void committedPdfStreamIsNeverWrappedOrReplacedWithJson() throws Exception {
    org.springframework.mock.web.MockHttpServletRequest request =
        new org.springframework.mock.web.MockHttpServletRequest(
            "GET", "/api/me/scheme-exports/" + Ids.next() + "/file");
    org.springframework.mock.web.MockHttpServletResponse response =
        new org.springframework.mock.web.MockHttpServletResponse();
    com.mujian.backend.common.security.ApiBoundaryFilter filter =
        f.app.getBean(com.mujian.backend.common.security.ApiBoundaryFilter.class);
    assertThrows(
        java.io.IOException.class,
        () ->
            filter.doFilter(
                request,
                response,
                (req, res) -> {
                  assertSame(response, res);
                  res.setContentType("application/pdf");
                  res.getOutputStream()
                      .write("%PDF-partial".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                  res.flushBuffer();
                  throw new java.io.IOException("injected disconnect");
                }));
    assertEquals("%PDF-partial", response.getContentAsString());
    assertEquals("no-store", response.getHeader("Cache-Control"));
  }
}
