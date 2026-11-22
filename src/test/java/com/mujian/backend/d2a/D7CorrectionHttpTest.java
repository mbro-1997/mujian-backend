package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessException;
import tools.jackson.databind.JsonNode;

class D7CorrectionHttpTest {
  private final D2BApplicationTest factory = new D2BApplicationTest();
  private final D2ApplicationTest f = factory.f;
  private static final String LIST = "/api/me/service-sources", CARDS = "/api/me/service-contexts";

  /** 只在原独立 MySQL、Redis 及合成会话上运行。 */
  @BeforeEach
  void start() throws Exception {
    String path = System.getenv("MJ_TEST_CONFIG");
    Assumptions.assumeTrue(path != null);
    String config = Files.readString(Path.of(path));
    assertTrue(config.contains("127.0.0.1:33317/darkmujian_d1_test_d4"));
    assertTrue(config.contains("spring.data.redis.port=16397"));
    factory.start();
  }

  /** 释放本组创建的资源。 */
  @AfterEach
  void stop() {
    factory.stop();
  }

  /** 把合成凭据从标准输入交给客户端，避免写入命令行和证据。 */
  private void client(String mode, Map<String, Object> input) throws Exception {
    input.put("mode", mode);
    input.put("base", f.base);
    input.put("cookie", f.adminCookie);
    input.put("csrf", f.csrf);
    Process p =
        new ProcessBuilder("node", "../mujian-frontend/tests/d7-corrections.cjs")
            .redirectErrorStream(true)
            .redirectOutput(Path.of("target/d7r", mode + ".log").toFile())
            .start();
    try {
      try (java.io.OutputStream stdin = p.getOutputStream()) {
        stdin.write(f.json.writeValueAsBytes(input));
      }
      assertTrue(p.waitFor(100, TimeUnit.SECONDS));
      assertEquals(0, p.exitValue());
    } finally {
      p.destroyForcibly();
    }
  }

  /** 从首页到领取后的本人券，三个 BP 边界和金额券经同一批实际活动核对。 */
  @Test
  void couponDisplayAcrossEntries() throws Exception {
    User buyer = f.user();
    List<Map<String, Object>> offers = new ArrayList<>();
    for (int bp : List.of(1, 1000, 10000, 0)) {
      Map<String, Object> spec =
          view(
              "templateCode",
              Ids.next(),
              "templateName",
              "租金减免核验" + Ids.next(),
              "couponType",
              bp == 0 ? "fixed_amount" : "percentage",
              "faceAmountFen",
              bp == 0 ? 1250 : null,
              "discountRateBps",
              bp == 0 ? null : bp,
              "thresholdRentAmountFen",
              0,
              "applicableScopeType",
              "all",
              "factoryIds",
              List.of(),
              "validityType",
              "relative_days",
              "validDays",
              4,
              "validStartAt",
              null,
              "validEndAt",
              null);
      String templates = "/api/admin/coupon-templates", activities = "/api/admin/coupon-activities";
      JsonNode draft = f.call("POST", templates, spec, null, Ids.next(), 200);
      String tpl = draft.get("id").asString();
      f.call(
          "POST",
          templates + "/" + tpl + "/publish",
          Map.of("snapshotToken", draft.get("snapshotToken").asString()),
          null,
          Ids.next(),
          200);
      JsonNode act =
          f.call(
              "POST",
              activities,
              view(
                  "templateId",
                  tpl,
                  "condition",
                  "all_active",
                  "startsAt",
                  time(now(f.clock)),
                  "endsAt",
                  time(now(f.clock).plusMinutes(1)),
                  "reason",
                  "整改表示核验"),
              null,
              Ids.next(),
              200);
      String id = act.get("id").asString();
      f.call(
          "POST", activities + "/" + id + "/publish", Map.of("version", 0), null, Ids.next(), 200);
      offers.add(view("bp", bp, "id", id, "template", tpl, "name", spec.get("templateName")));
    }
    client("rates", view("offers", offers, "token", buyer.token(), "user", buyer.id()));
  }

  /** 本人待审、历史归属、原键重试及审批后重新授权。 */
  @Test
  void applicantScopeAndTransition() throws Exception {
    User person = factory.user(), other = factory.user(), empty = factory.user();
    JsonNode application = factory.onboard(person), otherApp = factory.onboard(other);
    String id = application.get("id").asString(), key = Ids.next();
    Map<String, Object> source = view("sourceType", "factory_onboarding", "sourceId", id),
        input = view("source", source);
    String exact = LIST + "?sourceType=factory_onboarding&sourceId=" + id;
    assertEquals("1", f.call("GET", LIST, null, person, null, 200).get("total").asString());
    assertEquals("0", f.call("GET", exact, null, other, null, 200).get("total").asString());
    f.call("GET", exact, null, null, null, 401);
    f.call("GET", LIST, null, empty, null, 403);
    f.call("GET", LIST + "?sourceType=rental_order", null, person, null, 403);
    f.call(
        "POST",
        CARDS,
        view("source", view("sourceType", "rental_order", "sourceId", id)),
        person,
        Ids.next(),
        403);
    f.call("POST", CARDS, input, other, Ids.next(), 404);
    f.call(
        "POST",
        CARDS,
        view(
            "source",
            view("sourceType", "factory_onboarding", "sourceId", otherApp.get("id").asString())),
        person,
        Ids.next(),
        404);
    JsonNode card = f.call("POST", CARDS, input, person, key, 200);
    assertEquals(card, f.call("POST", CARDS, input, person, key, 200));
    String cid = card.at("/context/id").asString();
    assertEquals("/pages/factory/status?id=" + id, card.at("/context/returnPagePath").asString());
    assertEquals(
        cid, f.call("GET", exact, null, person, null, 200).at("/items/0/contextId").asString());
    f.call("GET", CARDS + "/" + cid, null, other, null, 404);
    f.call("GET", CARDS + "/" + cid, null, empty, null, 403);
    assertEquals(
        "applicant",
        f.db.queryForObject(
            "SELECT active_role FROM customer_service_context WHERE id=?", String.class, cid));
    DataAccessException contextConstraint =
        assertThrows(
            DataAccessException.class,
            () ->
                f.db.update(
                    "UPDATE customer_service_context SET source_type='rental_order' WHERE id=?",
                    cid));
    assertTrue(
        contextConstraint
            .getMostSpecificCause()
            .getMessage()
            .contains("ck_service_context_role_source"));
    String session = Ids.next();
    f.db.update(
        "INSERT INTO"
            + " customer_service_session(id,session_no,channel_type,channel_user_key,user_id,active_role,session_status,created_at,updated_at)"
            + " VALUES(?,?,'wechat_customer_service','synthetic',?,'applicant','active',NOW(3),NOW(3))",
        session,
        session,
        person.id());
    f.call("GET", "/api/me/factory-profile", null, person, null, 403);
    f.call(
        "POST",
        "/api/admin/customer-service-cases",
        view(
            "userId",
            other.id(),
            "contextId",
            cid,
            "source",
            source,
            "demand",
            "核验",
            "mainConflict",
            "测试"),
        null,
        Ids.next(),
        409);
    String factoryId = factory.approve(person, application);
    DataAccessException sessionConstraint =
        assertThrows(
            DataAccessException.class,
            () ->
                f.db.update(
                    "UPDATE customer_service_session SET factory_id=? WHERE id=?",
                    factoryId,
                    session));
    assertTrue(
        sessionConstraint
            .getMostSpecificCause()
            .getMessage()
            .contains("ck_service_session_role_factory"));
    f.db.update("DELETE FROM customer_service_session WHERE id=?", session);
    assertEquals(
        factoryId,
        f.call("GET", "/api/me/factory-profile", null, person, null, 200).get("id").asString());
    assertEquals(
        cid, f.call("GET", exact, null, person, null, 200).at("/items/0/contextId").asString());
    assertEquals(
        cid, f.call("GET", CARDS + "/" + cid, null, person, null, 200).get("id").asString());
    f.call("POST", CARDS, input, person, key, 404);
    JsonNode current = f.call("POST", CARDS, input, person, Ids.next(), 200);
    assertEquals(
        "factory",
        f.db.queryForObject(
            "SELECT active_role FROM customer_service_context WHERE id=?",
            String.class,
            current.at("/context/id").asString()));
    assertNotEquals(current.get("contextToken"), card.get("contextToken"));
    f.call("GET", exact, null, other, null, 200);
  }

  /** 微信无回调时，页面仍能读到已登记、已解决及来源独立登记的真实案件。 */
  @Test
  void progressAfterReturnAndReentry() throws Exception {
    User person = factory.user(), other = factory.user();
    String id = factory.onboard(person).get("id").asString();
    factory.onboard(other);
    client(
        "progress",
        view("id", id, "token", person.token(), "user", person.id(), "other", other.token()));
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT count(*) FROM customer_service_context WHERE user_id=? AND (sent_at IS NOT NULL"
                + " OR session_id IS NOT NULL)",
            Integer.class,
            person.id()));
    Map<String, Object> input =
        view("source", view("sourceType", "factory_onboarding", "sourceId", id));
    f.clock.at = f.clock.at.plusMillis(1);
    String key = Ids.next(),
        cid = f.call("POST", CARDS, input, person, key, 200).at("/context/id").asString();
    f.clock.at = f.clock.at.plusSeconds(601);
    assertEquals(
        "expired",
        f.call("GET", CARDS + "/" + cid, null, person, null, 200).get("contextStatus").asString());
    f.call("POST", CARDS, input, person, key, 409);
    assertEquals("CONTEXT_EXPIRED", f.lastReason);
    client(
        "reconnect", view("id", id, "token", person.token(), "user", person.id(), "expired", cid));
  }
}
