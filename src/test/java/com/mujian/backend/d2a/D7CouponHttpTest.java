package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.now;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import java.io.OutputStream;
import java.net.http.HttpRequest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

class D7CouponHttpTest {
  private final D2ApplicationTest f = new D2ApplicationTest();
  private final String template = Ids.next();
  private static final String ISSUE = "/api/admin/coupon-distributions/issue";

  /** 只运行于本轮隔离端口及合成库。 */
  @BeforeEach
  void start() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("MJ_TEST_CONFIG") != null);
    String conf =
        java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("MJ_TEST_CONFIG")));
    assertTrue(conf.contains("127.0.0.1:33317/darkmujian_d1_test_d4"));
    assertTrue(conf.contains("spring.data.redis.port=16397"));
    f.start();
    f.db.update(
        "INSERT INTO"
            + " coupon_template(id,template_code,template_name,template_version,coupon_type,face_amount_fen,threshold_rent_amount_fen,applicable_scope_type,cost_bearer,validity_type,valid_days,refund_return_policy,return_extension_days,template_status,created_by_admin_user_id,published_at,created_at,updated_at)"
            + " VALUES(?,?,'D7合成活动券',1,'fixed_amount',100,0,'all','shared_by_settlement_ratio','relative_days',30,'return_with_extension',7,'active',?,NOW(3),NOW(3),NOW(3))",
        template,
        template,
        f.admin);
  }

  /** 释放本组创建的资源。 */
  @AfterEach
  void stop() {
    f.stop();
  }

  /** 各入口并发、读回已过期券、精确结束边界与另一身份拒绝。 */
  @Test
  void claimAcrossTwoEntries() throws Exception {
    D2ApplicationTest.User buyer = f.user();
    LocalDateTime at = now(f.clock);
    String activity = Ids.next(), path = "/api/coupon-activities/";
    String key = Ids.next();
    Map<String, Object> config =
        Map.of(
            "templateId",
            template,
            "condition",
            "all_active",
            "startsAt",
            com.mujian.backend.common.support.D2Values.time(at),
            "endsAt",
            com.mujian.backend.common.support.D2Values.time(at.plusSeconds(30)),
            "reason",
            "隔离测试");
    JsonNode draft = f.call("POST", "/api/admin/coupon-activities", config, null, key, 200);
    assertEquals(draft, f.call("POST", "/api/admin/coupon-activities", config, null, key, 200));
    activity = draft.path("id").asString();
    f.call(
        "POST",
        "/api/admin/coupon-activities/" + activity + "/publish",
        Map.of("version", 0),
        null,
        Ids.next(),
        200);
    String claim = path + activity + "/claim";
    JsonNode offers = f.call("GET", "/api/coupon-activities", null, buyer, null, 200);
    assertTrue(offers.toString().contains(activity));
    assertEquals(0, count("SELECT issued_count FROM coupon_template WHERE id=?", template));
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      CountDownLatch gate = new CountDownLatch(1);
      Callable<JsonNode> click =
          () -> {
            gate.await();
            return f.call("POST", claim, null, buyer, null, 200);
          };
      Future<JsonNode> home = pool.submit(click), wallet = pool.submit(click);
      gate.countDown();
      JsonNode first = home.get(), second = wallet.get();
      assertEquals(first.get("userCouponId"), second.get("userCouponId"));
      assertNotEquals(first.get("alreadyClaimed"), second.get("alreadyClaimed"));
      assertTrue(
          f.call("GET", "/api/me/coupons", null, buyer, null, 200)
              .toString()
              .contains(first.path("userCouponId").asString()));
    } finally {
      pool.shutdownNow();
    }
    assertEquals(1, count("SELECT issued_quantity FROM coupon_distribution WHERE id=?", activity));
    assertEquals(1, count("SELECT COUNT(*) FROM coupon_operation_log WHERE biz_id=?", activity));
    assertFalse(
        f.call("GET", "/api/coupon-activities", null, buyer, null, 200)
            .toString()
            .contains(activity));
    D2ApplicationTest.User other = f.user();
    f.db.update("UPDATE coupon_template SET template_status='paused' WHERE id=?", template);
    f.call("POST", claim, null, other, null, 409);
    assertEquals("TEMPLATE_NOT_ISSUABLE", f.lastReason);
    f.clock.at = f.clock.at.plusSeconds(30);
    f.call("POST", claim, null, other, null, 409);
    assertEquals("ACTIVITY_NOT_OPEN", f.lastReason);
    f.clock.at = f.clock.at.plus(Duration.ofDays(31));
    D2ApplicationTest.User renewed =
        new D2ApplicationTest.User(
            buyer.id(), f.sessions.create(buyer.id(), "miniapp").session().getId());
    assertEquals(
        "expired", f.call("POST", claim, null, renewed, null, 200).path("couponStatus").asString());
    f.call("POST", claim, null, null, null, 401);
  }

  /** 运行隔离 HTTP 下的原生小程序组件。 */
  @Test
  void actualMiniComponentOnIsolatedHttp() throws Exception {
    User buyer = f.user();
    User other = f.user();
    LocalDateTime at = now(f.clock);
    JsonNode draft =
        f.call(
            "POST",
            "/api/admin/coupon-activities",
            Map.of(
                "templateId",
                template,
                "condition",
                "all_active",
                "startsAt",
                com.mujian.backend.common.support.D2Values.time(at),
                "endsAt",
                com.mujian.backend.common.support.D2Values.time(at.plusMinutes(1)),
                "reason",
                "组件集成"),
            null,
            Ids.next(),
            200);
    String id = draft.get("id").asString();
    f.call(
        "POST",
        "/api/admin/coupon-activities/" + id + "/publish",
        Map.of("version", 0),
        null,
        Ids.next(),
        200);
    Process process =
        new ProcessBuilder("node", "../mujian-frontend/tests/d7-coupon-component-http.cjs")
            .redirectErrorStream(true)
            .redirectOutput(new java.io.File("target/d7/mini-http.log"))
            .start();
    try (OutputStream pipe = process.getOutputStream()) {
      pipe.write(
          f.json.writeValueAsBytes(
              Map.of(
                  "base",
                  f.base,
                  "token",
                  buyer.token(),
                  "userId",
                  buyer.id(),
                  "other",
                  other.token(),
                  "activity",
                  id)));
    }
    try {
      assertTrue(process.waitFor(50, TimeUnit.SECONDS));
      assertEquals(0, process.exitValue());
    } finally {
      process.destroyForcibly();
    }
    assertEquals(1, count("SELECT issued_quantity FROM coupon_distribution WHERE id=?", id));
  }

  /** 检查零对象及已确认操作的重试。 */
  @Test
  void zeroTargetsAndConfirmedRetry() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(
        java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("MJ_TEST_CONFIG")))
            .contains("/darkmujian_d1_test_d4_v163"));
    List<String> active =
        f.db.queryForList(
            "SELECT u.id FROM sys_user u JOIN sys_user_role r ON r.user_id=u.id AND"
                + " r.role_code='demander' AND r.enabled=1 WHERE u.user_status='active'",
            String.class);
    String key = Ids.next();
    Map<String, Object> intent =
        Map.of(
            "templateId",
            template,
            "mode",
            "all_users",
            "audienceFilter",
            Map.of("accountStatus", "active", "businessRole", "demander"),
            "quantityPerUser",
            1,
            "reason",
            "零人后重取集合");
    try {
      for (String id : active)
        f.db.update("UPDATE sys_user SET user_status='disabled' WHERE id=?", id);
      f.call("POST", ISSUE, intent, null, key, 409);
      assertEquals("NO_ELIGIBLE_USERS", f.lastReason);
      assertEquals(0, count("SELECT COUNT(*) FROM coupon_distribution WHERE request_id=?", key));
      User user = f.user();
      JsonNode ok = f.call("POST", ISSUE, intent, null, key, 200);
      assertEquals("1", ok.path("targetCount").asString());
      f.user();
      assertEquals(ok, f.call("POST", ISSUE, intent, null, key, 200));
      f.call(
          "POST",
          ISSUE,
          Map.of(
              "mode",
              "selected_users",
              "templateId",
              template,
              "quantityPerUser",
              1,
              "reason",
              "非法名单",
              "userIds",
              List.of("bad")),
          null,
          Ids.next(),
          400);
      assertEquals("TARGET_USER_INVALID", f.lastReason);
    } finally {
      for (String id : active)
        f.db.update("UPDATE sys_user SET user_status='active' WHERE id=?", id);
    }
  }

  /** 验证模板与奖励事件版本。 */
  @Test
  void templateAndEventVersions() throws Exception {
    String code = Ids.next(), url = "/api/admin/coupon-templates";
    HashMap<String, Object> spec = new HashMap<String, Object>();
    spec.put("templateCode", code);
    spec.put("templateName", "隔离配置券");
    spec.put("couponType", "fixed_amount");
    spec.put("faceAmountFen", 90);
    spec.put("discountRateBps", null);
    spec.put("thresholdRentAmountFen", 0);
    spec.put("applicableScopeType", "all");
    spec.put("factoryIds", List.of());
    spec.put("validityType", "relative_days");
    spec.put("validDays", 8);
    spec.put("validStartAt", null);
    spec.put("validEndAt", null);
    JsonNode draft = f.call("POST", url, spec, null, Ids.next(), 200);
    String path = url + "/" + draft.path("id").asString();
    spec.put("templateName", "修改草稿");
    HashMap<String, Object> edit = new HashMap<>(spec);
    edit.put("snapshotToken", draft.get("snapshotToken").asString());
    JsonNode saved = f.call("POST", path + "/save", edit, null, Ids.next(), 200);
    f.call("POST", path + "/save", edit, null, Ids.next(), 409);
    assertEquals("VERSION_CONFLICT", f.lastReason);
    JsonNode published =
        f.call(
            "POST",
            path + "/publish",
            Map.of("snapshotToken", saved.path("snapshotToken").asString()),
            null,
            Ids.next(),
            200);
    JsonNode next = f.call("POST", url, spec, null, Ids.next(), 200);
    assertEquals(2, next.get("templateVersion").asInt());
    assertEquals("active", published.path("status").asString());
    for (String q :
        List.of(
            "coupon-templates",
            "coupon-reward-rules",
            "coupon-reward-records",
            "coupon-distributions",
            "user-coupons",
            "coupon-activities"))
      f.call("GET", "/api/admin/" + q + "?pageSize=1", null, null, null, 200);
    String rules = "/api/admin/coupon-reward-rules";
    Map<String, String> input =
        Map.of("eventType", "first_realname_verified", "templateId", draft.path("id").asString());
    String last = null;
    try {
      for (int i = 0; i < 2; i++) {
        JsonNode row = f.call("POST", rules, input, null, Ids.next(), 200);
        JsonNode active =
            f.call(
                "POST",
                rules + "/" + row.path("id").asString() + "/activate",
                Map.of("snapshotToken", row.get("snapshotToken").asString()),
                null,
                Ids.next(),
                200);
        if (last != null)
          assertEquals(
              "ended",
              f.call("GET", rules + "/" + last, null, null, null, 200).get("status").asString());
        last = active.get("id").asString();
      }
    } finally {
      if (last != null)
        f.db.update(
            "UPDATE coupon_reward_rule SET status='ended',ended_at=? WHERE id=?",
            now(f.clock),
            last);
    }
  }

  /** 记录实际服务器语句、请求时间及行锁等待总时长，核对成功及中途回滚。 */
  @Test
  void issueThousandAndTenThousand() throws Exception {
    List<String> users = new ArrayList<>();
    for (int i = 0; i < 10000; i++) users.add(Ids.next());
    String data = f.json.writeValueAsString(users);
    String table = " FROM JSON_TABLE(?, '$[*]' COLUMNS(uid VARCHAR(32) PATH '$')) t";
    f.db.update(
        "INSERT INTO sys_user(id,nickname,user_status,register_status,created_at,updated_at) SELECT"
            + " uid,'D7合成','active','demander_bound',NOW(3),NOW(3)"
            + table,
        data);
    f.db.update(
        "INSERT INTO sys_user_role(id,user_id,role_code,enabled,created_at,updated_at) SELECT"
            + " uid,uid,'demander',1,NOW(3),NOW(3)"
            + table,
        data);
    for (int size : List.of(1000, 10000)) {
      Map<String, Object> intent =
          Map.of(
              "templateId",
              template,
              "mode",
              "selected_users",
              "reason",
              "合成批次",
              "quantityPerUser",
              1,
              "userIds",
              users.subList(0, size));
      long before = count("SELECT issued_count FROM coupon_template WHERE id=?", template);
      String key = Ids.next();
      JsonNode result = measure(intent, key, size, 200);
      assertEquals(Integer.toString(size), result.path("targetCount").asString());
      assertEquals(result, f.call("POST", ISSUE, intent, null, key, 200));
      assertEquals(
          before + size, count("SELECT issued_count FROM coupon_template WHERE id=?", template));
      String batch = result.path("distributionId").asString();
      assertEquals(size, count("SELECT COUNT(*) FROM user_coupon WHERE issue_source_id=?", batch));
      assertEquals(size, count("SELECT COUNT(*) FROM coupon_operation_log WHERE biz_id=?", batch));
      assertEquals(1, count("SELECT COUNT(*) FROM admin_operation_audit WHERE target_id=?", batch));
      f.db.update("UPDATE coupon_template SET issued_count=? WHERE id=?", Long.MAX_VALUE, template);
      String failedKey = Ids.next();
      measure(intent, failedKey, size, 500);
      assertEquals("ISSUE_ROLLED_BACK", f.lastReason);
      assertEquals(
          0, count("SELECT COUNT(*) FROM coupon_distribution WHERE request_id=?", failedKey));
      assertEquals(
          before + size, count("SELECT COUNT(*) FROM user_coupon WHERE template_id=?", template));
      assertEquals(
          Long.MAX_VALUE, count("SELECT issued_count FROM coupon_template WHERE id=?", template));
      assertEquals(
          0,
          count("SELECT COUNT(*) FROM admin_operation_audit WHERE idempotency_key=?", failedKey));
      assertEquals(
          before + size,
          count(
              "SELECT COUNT(*) FROM coupon_operation_log l JOIN user_coupon c ON"
                  + " c.id=l.user_coupon_id WHERE c.template_id=?",
              template));
      f.db.update("UPDATE coupon_template SET issued_count=? WHERE id=?", before + size, template);
    }
  }

  /** 模拟主动发券回执丢失。 */
  @Test
  void lostManualIssueReceipt() throws Exception {
    User user = f.user();
    String key = Ids.next();
    HashMap<String, Object> intent =
        new HashMap<String, Object>(
            Map.of(
                "mode",
                "selected_users",
                "templateId",
                template,
                "quantityPerUser",
                1,
                "userIds",
                List.of(user.id()),
                "reason",
                "验证回执丢失"));
    HttpRequest request =
        java.net.http.HttpRequest.newBuilder(java.net.URI.create(f.base + ISSUE))
            .header("Cookie", f.adminCookie)
            .header("X-CSRF-Token", f.csrf)
            .header("Content-Type", "application/json")
            .header("Idempotency-Key", key)
            .POST(
                java.net.http.HttpRequest.BodyPublishers.ofString(
                    f.json.writeValueAsString(intent)))
            .build();
    // 调用方放弃首次回执，通过同键恢复后再核对服务端事实。
    f.http.send(request, java.net.http.HttpResponse.BodyHandlers.discarding());
    JsonNode replay = f.call("POST", ISSUE, intent, null, key, 200);
    assertEquals(1, count("SELECT COUNT(*) FROM coupon_distribution WHERE request_id=?", key));
    String id = replay.get("distributionId").asString();
    assertEquals(1, count("SELECT COUNT(*) FROM coupon_operation_log WHERE biz_id=?", id));
    assertEquals(1, count("SELECT COUNT(*) FROM admin_operation_audit WHERE target_id=?", id));
    intent.put("reason", "另一笔操作");
    f.call("POST", ISSUE, intent, null, key, 409);
    assertEquals("ISSUE_CONTENT_MISMATCH", f.lastReason);
    JsonNode next = f.call("POST", ISSUE, intent, null, Ids.next(), 200);
    assertNotEquals(id, next.get("distributionId").asString());
    assertEquals(2, count("SELECT COUNT(*) FROM user_coupon WHERE template_id=?", template));
  }

  /** 验证实际领券时再次核对学生资格。 */
  @Test
  void studentEligibilityIsCheckedAgainAtClaim() throws Exception {
    User buyer = f.user();
    LocalDateTime at = now(f.clock);
    String url = "/api/admin/coupon-activities";
    Map<String, String> spec =
        Map.of(
            "templateId",
            template,
            "condition",
            "student_verified",
            "startsAt",
            com.mujian.backend.common.support.D2Values.time(at),
            "endsAt",
            com.mujian.backend.common.support.D2Values.time(at.plusDays(1)),
            "reason",
            "隔离学生资格");
    String activity = f.call("POST", url, spec, null, Ids.next(), 200).get("id").asString();
    f.call("POST", url + "/" + activity + "/publish", Map.of("version", 0), null, Ids.next(), 200);
    String list = "/api/coupon-activities", claim = list + "/" + activity + "/claim";
    f.call("POST", claim, null, buyer, null, 409);
    assertEquals("CLAIM_NOT_ELIGIBLE", f.lastReason);
    for (String domain : List.of("realname", "student")) {
      JsonNode application =
          f.call(
              "POST",
              "/api/me/" + domain + "-verifications",
              f.body(buyer, domain),
              buyer,
              Ids.next(),
              200);
      String path =
          "/api/admin/" + domain + "-verifications/" + application.at("/application/id").asString();
      JsonNode detail = f.call("GET", path, null, null, null, 200);
      f.call("POST", path + "/approve", f.reviewBody(detail, true, domain), null, Ids.next(), 200);
    }
    assertTrue(
        f.call("GET", list + "?pageSize=100", null, buyer, null, 200)
            .toString()
            .contains(activity));
    f.db.update("UPDATE sys_user SET user_status='disabled' WHERE id=?", buyer.id());
    f.call("POST", claim, null, buyer, null, 403);
    assertEquals("ACCOUNT_ACTION_RESTRICTED", f.lastReason);
    f.db.update("UPDATE sys_user SET user_status='active' WHERE id=?", buyer.id());
    LocalDateTime expiry =
        f.db.queryForObject(
            "SELECT expire_at FROM user_student_verification WHERE user_id=?",
            LocalDateTime.class,
            buyer.id());
    // 保持审核日期/有效期事实不变，用行为时钟走到学生到期日，活动窗口单独延至其后。
    f.db.update(
        "UPDATE coupon_distribution SET ends_at=? WHERE id=?", expiry.plusDays(1), activity);
    f.clock.at = expiry.atZone(com.mujian.backend.common.support.D2Values.ZONE).toInstant();
    User renewed =
        new D2ApplicationTest.User(
            buyer.id(), f.sessions.create(buyer.id(), "miniapp").session().getId());
    assertFalse(
        f.call("GET", list + "?pageSize=100", null, renewed, null, 200)
            .toString()
            .contains(activity));
    f.call("POST", claim, null, renewed, null, 409);
    assertEquals("CLAIM_NOT_ELIGIBLE", f.lastReason);
    assertEquals(0, count("SELECT issued_quantity FROM coupon_distribution WHERE id=?", activity));
  }

  /** 核对指定条件的记录数量。 */
  private long count(String sql, String value) {
    return f.db.queryForObject(sql, Long.class, value);
  }

  /** 读取一次执行的测量值。 */
  private long metric(String name) {
    return f.db.queryForObject("SHOW GLOBAL STATUS LIKE '" + name + "'", (rs, k) -> rs.getLong(2));
  }

  /** 统计指定链路的执行次数或耗时。 */
  private JsonNode measure(Map<String, Object> intent, String key, int size, int status)
      throws Exception {
    long locks = metric("Innodb_row_lock_time"),
        questions = metric("Questions"),
        start = System.nanoTime();
    AtomicInteger sql = f.app.getBean(D2ApplicationTest.Fault.class).sqlCalls;
    long before = sql.get();
    JsonNode result = f.call("POST", ISSUE, intent, null, key, status);
    long mapperSql = sql.get() - before;
    long millis = (System.nanoTime() - start) / 1000000;
    long statements = metric("Questions") - questions - 1,
        wait = metric("Innodb_row_lock_time") - locks;
    System.out.printf(
        "D7_SQL users=%d http=%d elapsed_ms=%d mysql_commands=%d mybatis_prepared=%d"
            + " row_lock_ms=%d%n",
        size, status, millis, statements, mapperSql, wait);
    return result;
  }
}
