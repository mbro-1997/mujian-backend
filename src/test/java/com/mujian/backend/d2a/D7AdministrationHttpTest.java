package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;

class D7AdministrationHttpTest {
  final D2ApplicationTest f = new D2ApplicationTest();

  /** 初始化本组隔离依赖。 */
  @BeforeEach
  void start() throws Exception {
    Assumptions.assumeTrue(System.getenv("MJ_TEST_CONFIG") != null);
    assertTrue(
        java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("MJ_TEST_CONFIG")))
            .contains(":33317/darkmujian_d1_test_d4"));
    f.start();
  }

  /** 释放本组创建的资源。 */
  @AfterEach
  void stop() {
    f.stop();
  }

  /** 读取指定测试资源。 */
  JsonNode get(String path) throws Exception {
    return f.call("GET", "/api/admin/" + path, null, null, null, 200);
  }

  /** 以测试身份提交请求。 */
  JsonNode post(String path, Object body) throws Exception {
    return f.call("POST", "/api/admin/" + path, body, null, Ids.next(), 200);
  }

  /** 准备后台操作入口。 */
  @Test
  void administration() throws Exception {
    User buyer = f.user();
    for (String path :
        List.of(
            "dashboard",
            "cases",
            "system/menus",
            "system/accounts",
            "system/role-grants",
            "platform-rules",
            "users?pageSize=1",
            "system/audits?pageSize=1")) get(path);
    assertFalse(get("users/" + buyer.id()).toString().contains("password"));
    String userId = Ids.next();
    f.db.update(
        "INSERT INTO sys_user(id,nickname,user_status,register_status,created_at,updated_at)"
            + " VALUES(?,'隔离账号','active','new',NOW(3),NOW(3))",
        userId);
    String key = Ids.next();
    HashMap<String, Object> body =
        new HashMap<String, Object>(
            Map.of(
                "userId",
                userId,
                "username",
                "  D7." + Ids.next() + " ",
                "password",
                "only-synthetic-d7",
                "reason",
                "隔离验收"));
    JsonNode account = f.call("POST", "/api/admin/system/accounts", body, null, key, 200);
    assertEquals(account, f.call("POST", "/api/admin/system/accounts", body, null, key, 200));
    body.put("password", "different");
    f.call("POST", "/api/admin/system/accounts", body, null, key, 409);
    String accountPath = "system/accounts/" + account.path("id").asString();
    assertTrue(get(accountPath).path("enabled").asBoolean());
    post(
        accountPath + "/status",
        Map.of(
            "enabled",
            false,
            "snapshotToken",
            account.path("snapshotToken").asString(),
            "reason",
            "撤销"));
    JsonNode grants = get("system/role-grants");
    post(
        "system/role-grants/" + grants.path("roleId").asString() + "/save",
        Map.of(
            "snapshotToken",
            grants.path("snapshotToken").asString(),
            "reason",
            "保留现行授权",
            "menuIds",
            grants.get("menuIds")));
    JsonNode menu = get("system/menus/d01c25de0fe44841babaf183bf3ff8b8");
    assertEquals("/system/menus", menu.get("routePath").asString());
    HashMap<String, Object> draft = new HashMap<String, Object>();
    draft.put("ruleCode", Ids.next());
    draft.put("ruleTitle", "隔离合成规则");
    draft.put("ruleVersion", "v1");
    draft.put("ruleScope", "all_user");
    draft.put("contentType", "markdown");
    draft.put("contentText", "隔离规则正文");
    draft.put("contentUrl", null);
    draft.put("isRequired", false);
    draft.put("forceReaccept", false);
    JsonNode rule = post("platform-rules", draft);
    String path = "platform-rules/" + rule.path("id").asString();
    assertEquals(rule, get(path));
    HashMap<String, Object> updated = new HashMap<>(draft);
    updated.put("ruleTitle", "修改规则");
    updated.put("snapshotToken", rule.path("snapshotToken").asString());
    JsonNode saved = post(path + "/save", updated);
    f.call(
        "POST",
        "/api/admin/" + path + "/publish",
        Map.of("snapshotToken", rule.path("snapshotToken").asString()),
        null,
        Ids.next(),
        409);
    post(path + "/publish", Map.of("snapshotToken", saved.path("snapshotToken").asString()));
    draft.put("ruleVersion", "v2");
    JsonNode next = post("platform-rules", draft);
    post(
        "platform-rules/" + next.path("id").asString() + "/publish",
        Map.of("snapshotToken", next.path("snapshotToken").asString()));
    assertEquals("archived", get(path).get("status").asString());
    for (String p : List.of("users/export", "system/audits/export")) {
      HttpRequest req =
          java.net.http.HttpRequest.newBuilder(java.net.URI.create(f.base + "/api/admin/" + p))
              .header("Cookie", f.adminCookie)
              .GET()
              .build();
      HttpResponse<String> res =
          f.http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
      assertEquals(200, res.statusCode());
      assertTrue(res.headers().firstValue("Content-Type").orElse("").contains("text/csv"));
      assertFalse(res.body().contains("only-synthetic-d7"));
    }
    JsonNode audit = get("system/audits?pageSize=1").at("/items/0");
    get("system/audits/" + audit.path("id").asString());
  }

  /** 核对契约登记与撤销能力。 */
  @Test
  void checkContractRegistrationsAndRevocation() throws Exception {
    RequestMappingHandlerMapping mapping =
        f.app.getBean(
            "requestMappingHandlerMapping",
            org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
                .class);
    JsonNode formal = f.json.readTree(getClass().getResourceAsStream("/contracts/d7.openapi.json"));
    ArrayList<String> evidence = new ArrayList<String>();
    for (Entry<String, JsonNode> path : formal.path("paths").properties())
      for (Entry<String, JsonNode> op : path.getValue().properties()) {
        MockHttpServletRequest request =
            new org.springframework.mock.web.MockHttpServletRequest(
                op.getKey().toUpperCase(java.util.Locale.ROOT),
                path.getKey().replaceAll("\\{[^}]+}", "534505ce87e4482c94be274e2d506b6c"));
        org.springframework.web.util.ServletRequestPathUtils.parseAndCache(request);
        HandlerExecutionChain chain = mapping.getHandler(request);
        assertNotNull(chain, path.getKey());
        HandlerMethod method = (org.springframework.web.method.HandlerMethod) chain.getHandler();
        String perm = op.getValue().path("x-required-permission").asString("");
        if (!perm.isEmpty())
          assertEquals(
              1,
              f.db.queryForObject(
                  "SELECT COUNT(*) FROM admin_menu WHERE permission_code=?", Integer.class, perm));
        evidence.add(
            op.getKey().toUpperCase()
                + " "
                + path.getKey()
                + " "
                + method.getBeanType().getSimpleName()
                + "."
                + method.getMethod().getName()
                + " "
                + perm);
      }
    assertEquals(65, evidence.size());
    java.nio.file.Files.write(
        java.nio.file.Path.of("target/d7/registered-operations.txt"), evidence);
    AtomicInteger sql = f.app.getBean(D2ApplicationTest.Fault.class).sqlCalls;
    long t = sql.get();
    get("users?pageSize=1");
    long one = sql.get() - t;
    t = sql.get();
    get("users?pageSize=20");
    long many = sql.get() - t;
    assertEquals(one, many);
    System.out.println("D7_USER_LIST mapper: one=" + one + " many=" + many);
    String perm = "admin:user:read";
    f.db.update("UPDATE admin_menu SET enabled=0 WHERE permission_code=?", perm);
    try {
      f.call("GET", "/api/admin/users", null, null, null, 403);
      assertEquals("PERMISSION_DENIED", f.lastReason);
    } finally {
      f.db.update("UPDATE admin_menu SET enabled=1 WHERE permission_code=?", perm);
    }
  }

  /** 建立独立案件用于隔离检查。 */
  @Test
  void independentCase() throws Exception {
    User buyer = f.user();
    HashMap<String, Object> input = new HashMap<String, Object>();
    input.put("userId", buyer.id());
    input.put("demand", "订单沟通");
    input.put("mainConflict", "核对交付时间");
    input.put("source", null);
    input.put("contextId", null);
    JsonNode detail = post("customer-service-cases", input);
    String id = detail.get("id").asString();
    assertEquals(detail, get("cases/" + id));
    Map<String, Object> resolution =
        Map.of(
            "version",
            detail.get("version").asInt(),
            "resolutionSummary",
            "已核对沟通记录",
            "resultRefs",
            List.of());
    String key = Ids.next();
    String path = "/api/admin/customer-service-cases/" + id + "/resolve";
    JsonNode closed = f.call("POST", path, resolution, null, key, 200);
    assertEquals("closed", closed.get("caseStatus").asString());
    assertEquals(closed, f.call("POST", path, resolution, null, key, 200));
    f.call("POST", path, resolution, null, Ids.next(), 409);
    f.call("GET", "/api/me/service-sources", null, buyer, null, 200);
  }
}
