package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.account.service.*;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.Fault;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import com.mujian.backend.factory.dto.FactoryDtos.*;
import com.mujian.backend.factory.dto.FactoryDtos.UploadResult;
import com.mujian.backend.factory.service.*;
import com.mujian.backend.factory.service.FactoryCasework;
import com.mujian.backend.infrastructure.wechat.WechatGateway;
import com.mujian.backend.material.mapper.MaterialReferenceMapper;
import com.mujian.backend.material.service.VerificationMaterialCleanup.Report;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.time.Instant;
import java.util.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.junit.jupiter.api.*;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.*;
import tools.jackson.databind.JsonNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class D2BApplicationTest {
  D2ApplicationTest f = new D2ApplicationTest();

  /** 初始化本组隔离依赖。 */
  @BeforeAll
  void start() throws Exception {
    f.start();
  }

  /** 释放本组创建的资源。 */
  @AfterAll
  void stop() {
    f.stop();
  }

  /** 建立本场景使用的账号。 */
  D2ApplicationTest.User user() {
    String code = Ids.next();
    f.wx.identities.put(
        code,
        new WechatGateway.Identity(
            "test-app",
            code,
            null,
            "+86000" + String.format("%08d", Math.abs((long) code.hashCode()) % 100000000L),
            "86"));
    JsonNode n =
        f.json.valueToTree(
            f.app.getBean(WechatPhoneLoginService.class).login(code, "fixture", f.rules));
    D2ApplicationTest.User user =
        new D2ApplicationTest.User(
            n.path("context").path("userId").asString(), n.path("sessionToken").asString());
    f.createdUsers.add(user.id());
    f.createdSessions.add(user);
    return user;
  }

  /** 执行请求并检查预期响应。 */
  JsonNode call(
      String method, String path, Object body, D2ApplicationTest.User user, String key, int status)
      throws Exception {
    return f.call(method, path, body, user, key, status);
  }

  /** 创建入驻使用的邀请码。 */
  JsonNode invite() throws Exception {
    return call(
        "POST",
        "/api/admin/factory-invitations",
        view("expireAt", null, "remark", "隔离B测试"),
        null,
        Ids.next(),
        200);
  }

  /** 执行对应来源的认领操作。 */
  void claim(D2ApplicationTest.User user, JsonNode invitation) throws Exception {
    call(
        "POST",
        "/api/me/factory-invitations/claim",
        view("inviteCode", invitation.path("inviteCode").asString()),
        user,
        null,
        200);
  }

  /** 上传场景所需材料。 */
  Object upload(D2ApplicationTest.User user, String purpose) throws Exception {
    UploadResult uploadResult =
        f.app.getBean(FactoryMaterials.class).upload(user.id(), purpose, D2ApplicationTest.png());
    return view("kind", "upload", "uploadId", uploadResult.uploadId());
  }

  /** 构造业务字段供提交使用。 */
  Map<String, Object> fields(D2ApplicationTest.User user) throws Exception {
    JsonNode options = call("GET", "/api/me/factory-options", null, user, null, 200);
    return view(
        "factoryName",
        "测试工厂" + Ids.next().substring(0, 4),
        "unifiedSocialCreditCode",
        Ids.next().substring(0, 18).toUpperCase(),
        "factoryAvatar",
        upload(user, "factory_avatar"),
        "businessLicense",
        upload(user, "business_license"),
        "factoryPhotos",
        List.of(upload(user, "factory_photo")),
        "contactName",
        "测试联系人",
        "factoryAddress",
        "测试专用地址",
        "mainBusiness",
        null,
        "workerCount",
        0,
        "specialtyCategoryIds",
        List.of(options.path("items").get(0).path("id").asString()),
        "cases",
        List.of(
            view(
                "title",
                "隔离案例",
                "description",
                "材料仅用于测试",
                "images",
                List.of(upload(user, "service_case")))),
        "remark",
        null);
  }

  /** 准备本用例的业务参数。 */
  Object input(D2ApplicationTest.User user, Object fields, String previous) throws Exception {
    JsonNode rules = call("GET", "/api/me/factory-rules", null, user, null, 200);
    ArrayList<Object> a = new ArrayList<>();
    for (JsonNode r : rules.path("rules"))
      a.add(
          view(
              "documentId",
              r.path("documentId").asString(),
              "version",
              r.path("version").asString(),
              "contentHash",
              r.path("contentHash").asString()));
    return view("previousApplicationId", previous, "acceptedRules", a, "materials", fields);
  }

  /** 取得已存在的测试业务记录。 */
  Map<String, Object> existing(JsonNode fields) {
    LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
    for (Entry<String, JsonNode> e : fields.properties()) {
      String k = e.getKey();
      if (k.equals("specialtyCategoryNames")) continue;
      Object value = f.json.convertValue(e.getValue(), Object.class);
      if (Set.of("factoryAvatar", "businessLicense").contains(k))
        value = view("kind", "existing", "locator", value);
      if (k.equals("factoryPhotos")) {
        ArrayList<Object> list = new ArrayList<>();
        e.getValue()
            .forEach(
                v ->
                    list.add(
                        view("kind", "existing", "locator", f.json.convertValue(v, Object.class))));
        value = list;
      }
      if (k.equals("cases")) {
        ArrayList<Object> list = new ArrayList<>();
        for (JsonNode c : e.getValue()) {
          ArrayList<Object> imgs = new ArrayList<>();
          c.path("images")
              .forEach(
                  v ->
                      imgs.add(
                          view(
                              "kind",
                              "existing",
                              "locator",
                              f.json.convertValue(v, Object.class))));
          list.add(
              view(
                  "title",
                  c.path("title").asString(),
                  "description",
                  c.path("description").asString(),
                  "images",
                  imgs));
        }
        value = list;
      }
      m.put(k, value);
    }
    return m;
  }

  /** 以审核身份通过指定申请。 */
  String approve(D2ApplicationTest.User user, JsonNode application) throws Exception {
    JsonNode r =
        call(
            "POST",
            "/api/admin/factory-onboarding/" + application.path("id").asString() + "/approve",
            view("expectedVersion", 0, "remark", "测试审核"),
            null,
            Ids.next(),
            200);
    return r.path("factoryId").asString();
  }

  /** 验证工厂入驻与常规资料变更可独立于短信推进。 */
  @Test
  void completeFactoryOnboardingAndOrdinaryProfileChangeWithPhoneDisabled() throws Exception {
    User user = user();
    JsonNode invitation = invite();
    claim(user, invitation);
    Object body = input(user, fields(user), null);
    String key = Ids.next();
    JsonNode submitted = call("POST", "/api/me/factory-onboarding", body, user, key, 200);
    String id = submitted.path("application").path("id").asString();
    assertEquals(
        "replayed",
        call("POST", "/api/me/factory-onboarding", body, user, key, 200).path("outcome").asString());
    call("GET", "/api/admin/factory-onboarding", null, null, null, 200);
    call("GET", "/api/admin/factory-onboarding/" + id, null, null, null, 200);
    String factory = approve(user, submitted.path("application"));
    assertEquals(
        "supplier", call("GET", "/api/me/context", null, user, null, 200).path("roleCode").asString());
    JsonNode profile = call("GET", "/api/me/factory-profile", null, user, null, 200);
    assertFalse(profile.path("phoneChange").path("available").asBoolean());
    call(
        "POST",
        "/api/me/factory-phone/sms",
        view("phone", "+8613000000001", "ticket", "fictional", "randstr", "fictional"),
        user,
        Ids.next(),
        503);
    assertEquals("PHONE_VERIFICATION_NOT_CONFIGURED", f.lastReason);
    call(
        "POST",
        "/api/me/factory-phone/verify",
        view("challengeId", Ids.next(), "code", "999999"),
        user,
        Ids.next(),
        503);
    Map<String, Object> proposed = existing(profile.path("materials"));
    proposed.put("remark", "修改其他资料");
    Map<String, Object> change =
        view(
            "previousApplicationId",
            null,
            "baseProfileVersion",
            "1",
            "phoneVerificationProof",
            null,
            "proposed",
            proposed);
    proposed.put("contactPhone", "+8613000000002");
    call("POST", "/api/me/factory-profile-changes", change, user, Ids.next(), 503);
    proposed.put("contactPhone", profile.path("materials").path("contactPhone").asString());
    change.put("phoneVerificationProof", "x".repeat(32));
    call("POST", "/api/me/factory-profile-changes", change, user, Ids.next(), 503);
    change.put("phoneVerificationProof", null);
    JsonNode changed = call("POST", "/api/me/factory-profile-changes", change, user, Ids.next(), 200);
    String changeId = changed.path("application").path("id").asString();
    call("GET", "/api/admin/factory-profile-changes/" + changeId, null, null, null, 200);
    String reviewKey = Ids.next();
    Map<String, Object> approve =
        view(
            "expectedVersion",
            0,
            "baseProfileVersion",
            "1",
            "sameLegalEntityConfirmed",
            false,
            "remark",
            null);
    call(
        "POST",
        "/api/admin/factory-profile-changes/" + changeId + "/approve",
        approve,
        null,
        reviewKey,
        200);
    call(
        "POST",
        "/api/admin/factory-profile-changes/" + changeId + "/approve",
        approve,
        null,
        reviewKey,
        200);
    call("GET", "/api/admin/factory-review-actions/by-request/" + reviewKey, null, null, null, 200);
    JsonNode result = call("GET", "/api/me/factory-profile", null, user, null, 200);
    assertEquals("2", result.path("profileVersion").asString());
    assertEquals("修改其他资料", result.path("materials").path("remark").asString());
    assertTrue(
        call("GET", "/api/me/factory-onboarding/" + id, null, user, null, 200)
            .path("materials")
            .path("remark")
            .isNull());
    call("GET", "/api/admin/factories/" + factory, null, null, null, 200);
    call("GET", "/api/admin/factories", null, null, null, 200);
    call("POST", "/api/admin/factories/" + factory + "/sensitive-view", null, null, null, 200);
    call(
        "POST",
        "/api/me/factory-materials/view",
        result.path("materials").path("factoryAvatar"),
        user,
        null,
        200);
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM sys_user_role WHERE user_id=? AND role_code='supplier'",
            Integer.class,
            user.id()));
    assertEquals(
        2,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM admin_case_action WHERE target_id IN (?,?)",
            Integer.class,
            id,
            changeId));
  }

  /** 通过测试入口完成工厂入驻。 */
  JsonNode onboard(D2ApplicationTest.User user) throws Exception {
    claim(user, invite());
    return call("POST", "/api/me/factory-onboarding", input(user, fields(user), null), user, Ids.next(), 200)
        .path("application");
  }

  /** 检查邀请码恢复、撤销及并发认领。 */
  @Test
  void invitationRecoveryRevocationAndActualClaimRace() throws Exception {
    String key = Ids.next();
    Map<String, Object> body = view("expireAt", null, "remark", "原邀请码");
    JsonNode made = call("POST", "/api/admin/factory-invitations", body, null, key, 200);
    String id = made.path("invitation").path("id").asString();
    assertTrue(
        made.path("inviteCode").asString().matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{20}"));
    assertEquals(
        made,
        call("GET", "/api/admin/factory-invitations/by-request/" + key, null, null, null, 200));
    call(
        "POST",
        "/api/admin/factory-invitations",
        view("expireAt", null, "remark", "变了"),
        null,
        key,
        409);
    User user = user();
    User b = user();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch gate = new CountDownLatch(1);
    ArrayList<Future<Integer>> futures = new ArrayList<Future<Integer>>();
    for (User u : List.of(user, b))
      futures.add(
          pool.submit(
              () -> {
                gate.await();
                HttpRequest request =
                    java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(f.base + "/api/me/factory-invitations/claim"))
                        .header("Authorization", "Bearer " + u.token())
                        .header("Content-Type", "application/json")
                        .POST(
                            java.net.http.HttpRequest.BodyPublishers.ofString(
                                f.json.writeValueAsString(
                                    view("inviteCode", made.path("inviteCode").asString()))))
                        .build();
                HttpResponse<String> response =
                    f.http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                f.received(
                    "POST", "/api/me/factory-invitations/claim", response, response.statusCode());
                return response.statusCode();
              }));
    gate.countDown();
    List<Integer> statuses = List.of(futures.get(0).get(), futures.get(1).get());
    pool.shutdownNow();
    assertTrue(statuses.contains(200) && statuses.contains(409));
    f.app
        .getBean(org.springframework.data.redis.core.StringRedisTemplate.class)
        .delete("mujian:test:factory-invite:" + id);
    JsonNode unavailable = call("POST", "/api/admin/factory-invitations", body, null, key, 200);
    assertEquals("unavailable", unavailable.path("codeAvailability").asString());
    assertEquals(id, unavailable.path("invitation").path("id").asString());
    String revoke = Ids.next();
    Map<String, Object> rb = view("reason", "测试作废");
    call("POST", "/api/admin/factory-invitations/" + id + "/revoke", rb, null, revoke, 200);
    call("POST", "/api/admin/factory-invitations/" + id + "/revoke", rb, null, revoke, 200);
    call("GET", "/api/admin/factory-invitations", null, null, null, 200);
  }

  /** 验证驳回后沿用原邀请码、历史和规则限制。 */
  @Test
  void rejectionUsesOriginalInviteAndHistoryAndRuleFailures() throws Exception {
    User user = user();
    JsonNode inv = invite();
    claim(user, inv);
    Map<String, Object> materials = fields(user);
    Map<String, Object> body = (Map<String, Object>) input(user, materials, null);
    Object accepted = body.get("acceptedRules");
    body.put("acceptedRules", List.of());
    call("POST", "/api/me/factory-onboarding", body, user, Ids.next(), 400);
    body.put("acceptedRules", accepted);
    JsonNode rule = call("GET", "/api/me/factory-rules", null, user, null, 200).path("rules").get(0);
    String ruleId = rule.path("documentId").asString();
    call("GET", "/api/me/factory-rules/" + ruleId, null, user, null, 200);
    try {
      f.db.update(
          "UPDATE platform_rule_document SET content_hash=? WHERE id=?", "a".repeat(64), ruleId);
      call("POST", "/api/me/factory-onboarding", body, user, Ids.next(), 409);
      assertEquals("RULE_VERSION_CHANGED", f.lastReason);
    } finally {
      f.db.update(
          "UPDATE platform_rule_document SET content_hash=? WHERE id=?",
          rule.path("contentHash").asString(),
          ruleId);
    }
    String key = Ids.next();
    JsonNode first =
        call("POST", "/api/me/factory-onboarding", body, user, key, 200).path("application");
    String id = first.path("id").asString();
    call("GET", "/api/me/factory-onboarding/by-request/" + key, null, user, null, 200);
    call("GET", "/api/me/factory-onboarding/current", null, user, null, 200);
    call(
        "POST",
        "/api/admin/factory-invitations/"
            + inv.path("invitation").path("id").asString()
            + "/revoke",
        view("reason", "禁止"),
        null,
        Ids.next(),
        409);
    call(
        "POST",
        "/api/admin/factory-onboarding/" + id + "/reject",
        view("expectedVersion", 0, "reason", "请补充资料"),
        null,
        Ids.next(),
        200);
    f.db.update(
        "UPDATE factory_invite_code SET expire_at=DATE_ADD(created_at,INTERVAL 1 SECOND) WHERE"
            + " id=?",
        inv.path("invitation").path("id").asString());
    f.clock.at = f.clock.at.plusSeconds(2);
    Map<String, Object> retained = existing(first.path("materials"));
    retained.remove("contactPhone");
    JsonNode second =
        call("POST", "/api/me/factory-onboarding", input(user, retained, id), user, Ids.next(), 200)
            .path("application");
    assertEquals(id, second.path("previousApplicationId").asString());
    approve(user, second);
    call("GET", "/api/me/factory-onboarding/" + id, null, user, null, 200);
  }

  /** 检查工单故障保留来源且审批写入共同回滚。 */
  @Test
  void sourceSurvivesCaseFailureAndApprovalWritesRollbackTogether() throws Exception {
    User user = user();
    claim(user, invite());
    Object body = input(user, fields(user), null);
    Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    String key = Ids.next();
    JsonNode source;
    fault.table = "admin_case";
    try {
      source = call("POST", "/api/me/factory-onboarding", body, user, key, 200).path("application");
    } finally {
      fault.table = null;
    }
    String id = source.path("id").asString();
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM admin_case_relation WHERE relation_id=?", Integer.class, id));
    call("GET", "/api/me/factory-onboarding/by-request/" + key, null, user, null, 200);
    FactoryCasework cases = f.app.getBean(FactoryCasework.class);
    cases.reconcile(com.mujian.backend.common.enums.FactorySource.ONBOARDING, id);
    cases.reconcile(com.mujian.backend.common.enums.FactorySource.ONBOARDING, id);
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM admin_case_relation WHERE relation_id=?", Integer.class, id));
    for (String table :
        List.of("factory_profile", "sys_user_role", "admin_case_action", "admin_operation_audit")) {
      fault.table = table;
      try {
        call(
            "POST",
            "/api/admin/factory-onboarding/" + id + "/approve",
            view("expectedVersion", 0, "remark", null),
            null,
            Ids.next(),
            500);
      } finally {
        fault.table = null;
      }
      assertEquals(
          "pending",
          f.db.queryForObject(
              "SELECT audit_status FROM factory_onboarding_audit WHERE id=?", String.class, id));
      assertEquals(
          0,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM factory_profile WHERE owner_user_id=?", Integer.class, user.id()));
      assertEquals(
          0,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM sys_user_role WHERE user_id=? AND role_code='supplier'",
              Integer.class,
              user.id()));
    }
    approve(user, source);
  }

  /** 验证审核竞争及信用代码唯一约束。 */
  @Test
  void reviewRaceAndCreditUniquenessAreAtomic() throws Exception {
    User user = user();
    JsonNode app = onboard(user);
    String id = app.path("id").asString();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch gate = new CountDownLatch(1);
    ArrayList<Future<Integer>> future = new ArrayList<Future<Integer>>();
    for (boolean approve : List.of(true, false))
      future.add(
          pool.submit(
              () -> {
                gate.await();
                HttpRequest request =
                    java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create(
                                f.base
                                    + "/api/admin/factory-onboarding/"
                                    + id
                                    + (approve ? "/approve" : "/reject")))
                        .header("Cookie", f.adminCookie)
                        .header("X-CSRF-Token", f.csrf)
                        .header("Idempotency-Key", Ids.next())
                        .header("Content-Type", "application/json")
                        .POST(
                            java.net.http.HttpRequest.BodyPublishers.ofString(
                                f.json.writeValueAsString(
                                    approve
                                        ? view("expectedVersion", 0, "remark", null)
                                        : view("expectedVersion", 0, "reason", "竞态驳回"))))
                        .build();
                HttpResponse<String> response =
                    f.http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
                f.received(
                    "POST",
                    "/api/admin/factory-onboarding/" + id + (approve ? "/approve" : "/reject"),
                    response,
                    response.statusCode());
                return response.statusCode();
              }));
    gate.countDown();
    List<Integer> statuses = List.of(future.get(0).get(), future.get(1).get());
    pool.shutdownNow();
    assertTrue(statuses.contains(200) && statuses.contains(409));
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM admin_case_action WHERE target_id=?", Integer.class, id));
    User owner = user();
    JsonNode original = onboard(owner);
    approve(owner, original);
    User other = user();
    claim(other, invite());
    Map<String, Object> otherFields = fields(other);
    otherFields.put(
        "unifiedSocialCreditCode",
        original.path("materials").path("unifiedSocialCreditCode").asString());
    JsonNode duplicate =
        call(
                "POST",
                "/api/me/factory-onboarding",
                input(other, otherFields, null),
                other,
                Ids.next(),
                200)
            .path("application");
    call(
        "POST",
        "/api/admin/factory-onboarding/" + duplicate.path("id").asString() + "/approve",
        view("expectedVersion", 0, "remark", null),
        null,
        Ids.next(),
        409);
    assertEquals("FACTORY_CREDIT_CODE_IN_USE", f.lastReason);
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM factory_profile WHERE owner_user_id=?",
            Integer.class,
            other.id()));
  }

  /** 验证停用工厂可纠错并保留历史归属。 */
  @Test
  void disabledFactoryCanCorrectWithoutReactivationAndHistoryIsOwned() throws Exception {
    User user = user();
    JsonNode original = onboard(user);
    String factory = approve(user, original);
    f.db.update("UPDATE factory_profile SET factory_status='disabled' WHERE id=?", factory);
    JsonNode p = call("GET", "/api/me/factory-profile", null, user, null, 200);
    assertTrue(p.path("canEdit").asBoolean());
    Map<String, Object> fields = existing(p.path("materials"));
    fields.put("remark", "停用期间更正");
    String key = Ids.next();
    Map<String, Object> body =
        view(
            "previousApplicationId",
            null,
            "baseProfileVersion",
            "1",
            "phoneVerificationProof",
            null,
            "proposed",
            fields);
    JsonNode change =
        call("POST", "/api/me/factory-profile-changes", body, user, key, 200).path("application");
    String id = change.path("id").asString();
    call("GET", "/api/me/factory-profile-changes/by-request/" + key, null, user, null, 200);
    call("GET", "/api/me/factory-profile-changes/" + id, null, user, null, 200);
    call("GET", "/api/me/factory-profile-changes/current", null, user, null, 200);
    call("GET", "/api/admin/factory-profile-changes", null, null, null, 200);
    call(
        "POST",
        "/api/admin/factory-profile-changes/" + id + "/sensitive-view",
        null,
        null,
        null,
        200);
    call(
        "POST",
        "/api/admin/factory-profile-changes/" + id + "/reject",
        view("expectedVersion", 0, "reason", "补充备注"),
        null,
        Ids.next(),
        200);
    f.clock.at = f.clock.at.plusMillis(10);
    body.put("previousApplicationId", id);
    fields.put("remark", "停用期间再次更正");
    JsonNode resubmitted =
        call("POST", "/api/me/factory-profile-changes", body, user, Ids.next(), 200)
            .path("application");
    call(
        "POST",
        "/api/admin/factory-profile-changes/" + resubmitted.path("id").asString() + "/approve",
        view(
            "expectedVersion",
            0,
            "baseProfileVersion",
            "1",
            "sameLegalEntityConfirmed",
            false,
            "remark",
            null),
        null,
        Ids.next(),
        200);
    assertEquals(
        "disabled",
        f.db.queryForObject(
            "SELECT factory_status FROM factory_profile WHERE id=?", String.class, factory));
    User other = user();
    call("GET", "/api/me/factory-profile-changes/" + id, null, other, null, 404);
    call(
        "POST",
        "/api/me/factory-materials/view",
        p.path("materials").path("factoryAvatar"),
        other,
        null,
        404);
    call("GET", "/api/me/factory-phone/capability", null, user, null, 200);
    f.db.update("UPDATE sys_user SET user_status='disabled' WHERE id=?", user.id());
    try {
      call("GET", "/api/me/factory-profile", null, user, null, 200);
      call("POST", "/api/me/factory-profile-changes", body, user, Ids.next(), 403);
    } finally {
      f.db.update("UPDATE sys_user SET user_status='active' WHERE id=?", user.id());
    }
  }

  /** 核对独立权限和材料预览审计。 */
  @Test
  void permissionsAreIndependentAndSensitiveImageIsAudited() throws Exception {
    User user = user();
    JsonNode app = onboard(user);
    String id = app.path("id").asString(), path = "/api/admin/factory-onboarding/" + id;
    List<Map<String, Object>> grants =
        f.db.queryForList(
            "SELECT * FROM admin_role_menu WHERE admin_menu_id IN(SELECT id FROM admin_menu WHERE"
                + " permission_code LIKE 'admin:factory-onboarding:%')");
    try {
      for (int mask = 0; mask < 8; mask++) {
        f.db.update(
            "DELETE FROM admin_role_menu WHERE admin_menu_id IN(SELECT id FROM admin_menu WHERE"
                + " permission_code LIKE 'admin:factory-onboarding:%')");
        for (Map<String, Object> g : grants) {
          String permission =
              f.db.queryForObject(
                  "SELECT permission_code FROM admin_menu WHERE id=?",
                  String.class,
                  g.get("admin_menu_id"));
          int bit = permission.endsWith(":read") ? 1 : permission.endsWith(":sensitive") ? 2 : 4;
          if ((mask & bit) != 0) f.restoreGrant(g);
        }
        call("GET", path, null, null, null, (mask & 1) != 0 ? 200 : 403);
        call("POST", path + "/sensitive-view", null, null, null, (mask & 3) == 3 ? 200 : 403);
        call(
            "POST",
            path + "/reject",
            view("expectedVersion", 99, "reason", "权限矩阵"),
            null,
            Ids.next(),
            (mask & 5) == 5 ? 409 : 403);
        call(
            "POST",
            "/api/admin/factory-materials/view",
            app.path("materials").path("businessLicense"),
            null,
            null,
            (mask & 3) == 3 ? 200 : 403);
      }
    } finally {
      f.db.update(
          "DELETE FROM admin_role_menu WHERE admin_menu_id IN(SELECT id FROM admin_menu WHERE"
              + " permission_code LIKE 'admin:factory-onboarding:%')");
      for (Map<String, Object> g : grants) f.restoreGrant(g);
    }
  }

  /** 检查停用字典历史回显、新写拒绝和信用代码确认。 */
  @Test
  void dictionaryRetainsDisabledHistoryButRejectsNewAndCreditRequiresConfirmation()
      throws Exception {
    User user = user();
    JsonNode app = onboard(user);
    approve(user, app);
    JsonNode p = call("GET", "/api/me/factory-profile", null, user, null, 200);
    String option = p.path("materials").path("specialtyCategoryIds").get(0).asString();
    Map<String, Object> old =
        f.db.queryForMap("SELECT enabled,option_label FROM option_dictionary WHERE id=?", option);
    try {
      f.db.update("UPDATE option_dictionary SET enabled=0,option_label='改名不漂移' WHERE id=?", option);
      Map<String, Object> fields = existing(p.path("materials"));
      fields.put("remark", "保留停用品类");
      fields.put("unifiedSocialCreditCode", Ids.next().substring(0, 18).toUpperCase());
      Map<String, Object> body =
          view(
              "previousApplicationId",
              null,
              "baseProfileVersion",
              "1",
              "phoneVerificationProof",
              null,
              "proposed",
              fields);
      JsonNode change =
          call("POST", "/api/me/factory-profile-changes", body, user, Ids.next(), 200)
              .path("application");
      assertEquals(
          p.path("materials").path("specialtyCategoryNames"),
          change.path("proposed").path("specialtyCategoryNames"));
      String id = change.path("id").asString();
      Map<String, Object> command =
          view(
              "expectedVersion",
              0,
              "baseProfileVersion",
              "1",
              "sameLegalEntityConfirmed",
              false,
              "remark",
              null);
      call(
          "POST",
          "/api/admin/factory-profile-changes/" + id + "/approve",
          command,
          null,
          Ids.next(),
          409);
      command.put("sameLegalEntityConfirmed", true);
      command.put("remark", "已核对同一主体");
      call(
          "POST",
          "/api/admin/factory-profile-changes/" + id + "/approve",
          command,
          null,
          Ids.next(),
          200);
      User other = user();
      claim(other, invite());
      Map<String, Object> fresh = fields(other);
      fresh.put("specialtyCategoryIds", List.of(option));
      call("POST", "/api/me/factory-onboarding", input(other, fresh, null), other, Ids.next(), 400);
    } finally {
      f.db.update(
          "UPDATE option_dictionary SET enabled=?,option_label=? WHERE id=?",
          old.get("enabled"),
          old.get("option_label"),
          option);
    }
  }

  /** 组装含文件的 HTTP 请求。 */
  String multipart(D2ApplicationTest.User user, String purpose, byte[] bytes, int expected)
      throws Exception {
    String boundary = "B" + Ids.next();
    ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    out.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\n"
                + purpose
                + "\r\n--"
                + boundary
                + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"isolated.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n")
            .getBytes());
    out.write(bytes);
    out.write(("\r\n--" + boundary + "--\r\n").getBytes());
    HttpRequest request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(f.base + "/api/me/factory-materials"))
            .header("Authorization", "Bearer " + user.token())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
            .build();
    HttpResponse<String> response =
        f.http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    return f.received("POST", "/api/me/factory-materials", response, expected)
        .path("uploadId")
        .asString();
  }

  /** 检查真实大表单上传及历史材料引用。 */
  @Test
  void actualMultipartLargeWholeFormAndHistoryCleanupReference() throws Exception {
    User user = user();
    claim(user, invite());
    Map<String, Object> fields = fields(user);
    fields.put(
        "factoryAvatar",
        view(
            "kind",
            "upload",
            "uploadId",
            multipart(user, "factory_avatar", D2ApplicationTest.png(), 200)));
    multipart(user, "service_case", new byte[] {1, 2, 3}, 400);
    ArrayList<Object> cases = new ArrayList<>();
    for (int n = 0; n < 10; n++)
      cases.add(view("title", "边界案例" + n, "description", "中".repeat(2000), "images", List.of()));
    fields.put("cases", cases);
    Object body = input(user, fields, null);
    assertTrue(f.json.writeValueAsBytes(body).length > 8192);
    String key = Ids.next();
    JsonNode a = call("POST", "/api/me/factory-onboarding", body, user, key, 200).path("application");
    String id = a.path("id").asString();
    call("POST", "/api/me/factory-onboarding", body, user, Ids.next(), 200);
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM factory_onboarding_audit WHERE applicant_user_id=? AND"
                + " audit_status='pending'",
            Integer.class,
            user.id()));
    call(
        "POST",
        "/api/me/factory-invitations/claim",
        view("inviteCode", "x".repeat(9000)),
        user,
        null,
        413);
    MaterialReferenceMapper refs =
        f.app.getBean(com.mujian.backend.material.mapper.MaterialReferenceMapper.class);
    String avatar =
        f.db.queryForObject(
            "SELECT factory_avatar_url FROM factory_onboarding_audit WHERE id=?", String.class, id);
    assertEquals(1, refs.countMaterialReferencesByOwnerAndKey(user.id(), avatar));
    approve(user, a);
    assertEquals(2, refs.countMaterialReferencesByOwnerAndKey(user.id(), avatar));
    JsonNode p = call("GET", "/api/me/factory-profile", null, user, null, 200);
    Map<String, Object> proposed = existing(p.path("materials"));
    proposed.put("remark", "历史保护");
    JsonNode change =
        call(
                "POST",
                "/api/me/factory-profile-changes",
                view(
                    "previousApplicationId",
                    null,
                    "baseProfileVersion",
                    "1",
                    "phoneVerificationProof",
                    null,
                    "proposed",
                    proposed),
                user,
                Ids.next(),
                200)
            .path("application");
    assertEquals(3, refs.countMaterialReferencesByOwnerAndKey(user.id(), avatar));
    call(
        "POST",
        "/api/admin/factory-profile-changes/" + change.path("id").asString() + "/reject",
        view("expectedVersion", 0, "reason", "仍保留历史"),
        null,
        Ids.next(),
        200);
    assertEquals(3, refs.countMaterialReferencesByOwnerAndKey(user.id(), avatar));
    Instant now = f.clock.at;
    try {
      f.clock.at = now.plus(Duration.ofHours(49));
      Report report =
          f.app
              .getBean(com.mujian.backend.material.service.VerificationMaterialCleanup.class)
              .scan(true);
      assertTrue(report.completed());
      assertTrue(report.retained() > 0);
      assertEquals(0, report.deleted());
      assertNotNull(f.store.head(avatar));
    } finally {
      f.clock.at = now;
    }
  }

  /** 验证首次提交过期或规则停用时拒绝处理。 */
  @Test
  void firstSubmissionExpiryAndDisabledRuleFailClosed() throws Exception {
    User user = user();
    JsonNode invitation =
        call(
            "POST",
            "/api/admin/factory-invitations",
            view(
                "expireAt",
                time(java.time.LocalDateTime.ofInstant(f.clock.at.plusSeconds(1), ZONE)),
                "remark",
                null),
            null,
            Ids.next(),
            200);
    claim(user, invitation);
    Object body = input(user, fields(user), null);
    Instant now = f.clock.at;
    try {
      f.clock.at = now.plusSeconds(2);
      call("POST", "/api/me/factory-onboarding", body, user, Ids.next(), 409);
      assertEquals("INVITATION_EXPIRED", f.lastReason);
    } finally {
      f.clock.at = now;
    }
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM factory_onboarding_audit WHERE applicant_user_id=?",
            Integer.class,
            user.id()));
    String rule =
        call("GET", "/api/me/factory-rules", null, user, null, 200)
            .path("rules")
            .get(0)
            .path("documentId")
            .asString();
    try {
      f.db.update("UPDATE platform_rule_document SET rule_status='archived' WHERE id=?", rule);
      call("GET", "/api/me/factory-rules", null, user, null, 503);
      call("POST", "/api/me/factory-onboarding", body, user, Ids.next(), 503);
    } finally {
      f.db.update("UPDATE platform_rule_document SET rule_status='active' WHERE id=?", rule);
    }
  }

  /** 验证邀请码预留与绑定租赁身份的竞争。 */
  @Test
  void claimReservationCompetesWithRealDemanderBinding() throws Exception {
    User user = user();
    JsonNode invitation = invite();
    Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    TransactionTemplate transaction =
        new org.springframework.transaction.support.TransactionTemplate(
            f.app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(3);
    try {
      Future<Object> blocker =
          pool.submit(
              () ->
                  transaction.execute(
                      status -> {
                        f.db.queryForObject(
                            "SELECT id FROM sys_user WHERE id=? FOR UPDATE", String.class, user.id());
                        locked.countDown();
                        try {
                          release.await(5, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                          throw new RuntimeException(e);
                        }
                        return null;
                      }));
      assertTrue(locked.await(3, TimeUnit.SECONDS));
      ArrayList<Future<Integer>> futures = new ArrayList<Future<Integer>>();
      for (boolean claim : List.of(true, false))
        futures.add(
            pool.submit(
                () -> {
                  String path =
                      claim ? "/api/me/factory-invitations/claim" : "/api/me/roles/demander";
                  HttpRequest req =
                      java.net.http.HttpRequest.newBuilder(java.net.URI.create(f.base + path))
                          .header("Authorization", "Bearer " + user.token())
                          .header("Content-Type", "application/json")
                          .POST(
                              java.net.http.HttpRequest.BodyPublishers.ofString(
                                  f.json.writeValueAsString(
                                      claim
                                          ? view(
                                              "inviteCode",
                                              invitation.path("inviteCode").asString())
                                          : Map.of())))
                          .build();
                  HttpResponse<String> response =
                      f.http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
                  f.received("POST", path, response, response.statusCode());
                  return response.statusCode();
                }));
      release.countDown();
      blocker.get();
      List<Integer> outcomes = List.of(futures.get(0).get(), futures.get(1).get());
      assertEquals(1, outcomes.stream().filter(n -> n == 200).count());
      assertTrue(outcomes.stream().allMatch(n -> n == 200 || n == 403 || n == 409));
      JsonNode context = call("GET", "/api/me/context", null, user, null, 200);
      assertTrue(
          "demander".equals(context.path("roleCode").asString())
              || "factory_invite_locked".equals(context.path("registrationStage").asString()));
    } finally {
      release.countDown();
      pool.shutdownNow();
    }
  }

  /** 验证并发提交只留一条待审申请且原键可回放。 */
  @Test
  void concurrentSubmissionKeepsOnePendingAndCanonicalRequestReplay() throws Exception {
    User user = user();
    claim(user, invite());
    Map<String, Object> fields = fields(user);
    Object body = input(user, fields, null);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    ArrayList<Future<JsonNode>> results = new ArrayList<Future<JsonNode>>();
    try {
      for (int n = 0; n < 2; n++)
        results.add(
            pool.submit(
                () -> {
                  start.await();
                  return call("POST", "/api/me/factory-onboarding", body, user, Ids.next(), 200);
                }));
      start.countDown();
      JsonNode a = results.get(0).get();
      JsonNode b = results.get(1).get();
      assertEquals(a.path("application").path("id"), b.path("application").path("id"));
      assertEquals(
          Set.of("submitted", "existing_pending"),
          Set.of(a.path("outcome").asString(), b.path("outcome").asString()));
      assertEquals(
          1,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM factory_onboarding_audit WHERE applicant_user_id=? AND"
                  + " audit_status='pending'",
              Integer.class,
              user.id()));
    } finally {
      pool.shutdownNow();
    }
    User other = user();
    claim(other, invite());
    Map<String, Object> original = fields(other);
    String key = Ids.next();
    Map<String, Object> input = (Map<String, Object>) input(other, original, null);
    call("POST", "/api/me/factory-onboarding", input, other, key, 200);
    original.put("factoryName", "  " + original.get("factoryName") + "  ");
    ArrayList<?> rules = new ArrayList<>((List<?>) input.get("acceptedRules"));
    Collections.reverse(rules);
    input.put("acceptedRules", rules);
    assertEquals(
        "replayed",
        call("POST", "/api/me/factory-onboarding", input, other, key, 200)
            .path("outcome")
            .asString());
    original.put("factoryName", "内容确实变了");
    call("POST", "/api/me/factory-onboarding", input, other, key, 409);
    assertEquals("IDEMPOTENCY_KEY_REUSED", f.lastReason);
  }

  /** 拒绝基于旧资料或伪造手机号凭证的审批。 */
  @Test
  void staleProfileAndForgedStoredPhoneProofCannotBeApproved() throws Exception {
    User user = user();
    JsonNode a = onboard(user);
    String factory = approve(user, a);
    JsonNode p = call("GET", "/api/me/factory-profile", null, user, null, 200);
    Map<String, Object> fields = existing(p.path("materials"));
    fields.put("remark", "版本与存储篡改探针");
    Map<String, Object> body =
        view(
            "previousApplicationId",
            null,
            "baseProfileVersion",
            "1",
            "phoneVerificationProof",
            null,
            "proposed",
            fields);
    body.put("ownerUserId", Ids.next());
    call("POST", "/api/me/factory-profile-changes", body, user, Ids.next(), 400);
    body.remove("ownerUserId");
    JsonNode change =
        call("POST", "/api/me/factory-profile-changes", body, user, Ids.next(), 200)
            .path("application");
    String id = change.path("id").asString(),
        path = "/api/admin/factory-profile-changes/" + id + "/approve";
    Map<String, Object> command =
        view(
            "expectedVersion",
            0,
            "baseProfileVersion",
            "1",
            "sameLegalEntityConfirmed",
            false,
            "remark",
            null);
    String snapshot =
        f.db.queryForObject(
            "SELECT change_payload_json FROM factory_profile_change_audit WHERE id=?",
            String.class,
            id);
    try {
      f.db.update("UPDATE factory_profile SET profile_version=2 WHERE id=?", factory);
      call("POST", path, command, null, Ids.next(), 409);
      assertEquals("FACTORY_PROFILE_BASE_CHANGED", f.lastReason);
      f.db.update("UPDATE factory_profile SET profile_version=1 WHERE id=?", factory);
      f.db.update(
          "UPDATE factory_profile_change_audit SET"
              + " change_payload_json=JSON_SET(change_payload_json,'$.proposed.contact_phone','+8613000000009','$.phone_verification',JSON_OBJECT('proof','forged'))"
              + " WHERE id=?",
          id);
      call("POST", path, command, null, Ids.next(), 503);
      assertEquals("PHONE_VERIFICATION_NOT_CONFIGURED", f.lastReason);
      assertEquals(
          1,
          f.db.queryForObject(
              "SELECT profile_version FROM factory_profile WHERE id=?", Integer.class, factory));
      assertEquals(
          "pending",
          f.db.queryForObject(
              "SELECT audit_status FROM factory_profile_change_audit WHERE id=?",
              String.class,
              id));
    } finally {
      f.db.update(
          "UPDATE factory_profile_change_audit SET change_payload_json=? WHERE id=?", snapshot, id);
      f.db.update("UPDATE factory_profile SET profile_version=1 WHERE id=?", factory);
    }
  }

  /** 验证清理等待提交锁并保留已提交来源。 */
  @Test
  void cleanupWaitsForFactorySubmissionOwnerLockAndRetainsCommittedSource() throws Exception {
    User user = user();
    claim(user, invite());
    Map<String, Object> fields = fields(user);
    Object body = input(user, fields, null);
    Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    fault.blockTable = "factory_onboarding_audit";
    fault.entered = new CountDownLatch(1);
    fault.release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    Instant originalTime = f.clock.at;
    try {
      Future<JsonNode> submitted =
          pool.submit(() -> call("POST", "/api/me/factory-onboarding", body, user, Ids.next(), 200));
      assertTrue(fault.entered.await(5, TimeUnit.SECONDS));
      f.clock.at = originalTime.plus(Duration.ofHours(49));
      Future<Report> cleanup =
          pool.submit(
              () ->
                  f.app
                      .getBean(
                          com.mujian.backend.material.service.VerificationMaterialCleanup.class)
                      .scan(true));
      Thread.sleep(150);
      assertFalse(cleanup.isDone(), "清理需等待正在提交的账号锁");
      fault.release.countDown();
      JsonNode result = submitted.get(10, TimeUnit.SECONDS);
      Report report = cleanup.get(15, TimeUnit.SECONDS);
      assertTrue(report.completed());
      assertTrue(report.retained() > 0);
      String key =
          f.db.queryForObject(
              "SELECT factory_avatar_url FROM factory_onboarding_audit WHERE id=?",
              String.class,
              result.path("application").path("id").asString());
      assertNotNull(f.store.head(key));
      assertEquals(
          1,
          f.app
              .getBean(com.mujian.backend.material.mapper.MaterialReferenceMapper.class)
              .countMaterialReferencesByOwnerAndKey(user.id(), key));
    } finally {
      fault.release.countDown();
      fault.blockTable = null;
      f.clock.at = originalTime;
      pool.shutdownNow();
    }
  }

  /** 通过实际请求和存储故障验证增量错误协议。 */
  @Test
  void incrementalOperationErrorMappingsUseRealHttpAndStorageFault() throws Exception {
    User user = user();
    JsonNode invitation = invite();
    claim(user, invitation);
    Map<String, Object> fields = fields(user);
    call("POST", "/api/me/factory-onboarding", input(user, fields, Ids.next()), user, Ids.next(), 404);
    assertEquals("RESOURCE_NOT_FOUND", f.lastReason);
    f.store.failHead = true;
    try {
      call("POST", "/api/me/factory-onboarding", input(user, fields, null), user, Ids.next(), 503);
      assertEquals("OBJECT_STORAGE_UNAVAILABLE", f.lastReason);
    } finally {
      f.store.failHead = false;
    }
    JsonNode a =
        call("POST", "/api/me/factory-onboarding", input(user, fields, null), user, Ids.next(), 200)
            .path("application");
    call(
        "POST",
        "/api/me/factory-invitations/claim",
        view("inviteCode", invitation.path("inviteCode").asString()),
        user,
        null,
        409);
    assertEquals("INVITATION_HAS_APPLICATION", f.lastReason);
    String aId = a.path("id").asString();
    call(
        "POST",
        "/api/admin/factory-onboarding/" + aId + "/reject",
        view("expectedVersion", 0, "reason", "隔离错误归属测试"),
        null,
        Ids.next(),
        200);
    call("POST", "/api/me/factory-onboarding", input(user, fields, Ids.next()), user, Ids.next(), 409);
    assertEquals("FACTORY_ALREADY_REVIEWED", f.lastReason);
    f.clock.at = f.clock.at.plusMillis(1);
    fields = existing(a.path("materials"));
    fields.remove("contactPhone");
    a =
        call("POST", "/api/me/factory-onboarding", input(user, fields, aId), user, Ids.next(), 200)
            .path("application");
    approve(user, a);
    JsonNode profile = call("GET", "/api/me/factory-profile", null, user, null, 200);
    Map<String, Object> proposed = existing(profile.path("materials"));
    proposed.put("remark", "增量契约验证");
    Map<String, Object> body =
        view(
            "previousApplicationId",
            Ids.next(),
            "baseProfileVersion",
            "1",
            "phoneVerificationProof",
            null,
            "proposed",
            proposed);
    call("POST", "/api/me/factory-profile-changes", body, user, Ids.next(), 404);
    assertEquals("RESOURCE_NOT_FOUND", f.lastReason);
    body.put("previousApplicationId", null);
    proposed.put("factoryAvatar", upload(user, "factory_avatar"));
    f.store.failHead = true;
    try {
      call("POST", "/api/me/factory-profile-changes", body, user, Ids.next(), 503);
      assertEquals("OBJECT_STORAGE_UNAVAILABLE", f.lastReason);
    } finally {
      f.store.failHead = false;
    }
    JsonNode change =
        call("POST", "/api/me/factory-profile-changes", body, user, Ids.next(), 200)
            .path("application");
    String id = change.path("id").asString();
    call(
        "POST",
        "/api/admin/factory-profile-changes/" + id + "/reject",
        view("expectedVersion", 0, "reason", "隔离错误归属测试"),
        null,
        Ids.next(),
        200);
    body.put("previousApplicationId", Ids.next());
    call("POST", "/api/me/factory-profile-changes", body, user, Ids.next(), 409);
    assertEquals("FACTORY_ALREADY_REVIEWED", f.lastReason);
  }

  /** 验证撤销在锁后能识别期间发生的认领。 */
  @Test
  void incrementalRevokeDetectsClaimBetweenHintAndLock() throws Exception {
    JsonNode invitation = invite();
    User user = user();
    String id = invitation.path("invitation").path("id").asString();
    Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    fault.inviteReadTarget = id;
    fault.inviteReadEntered = new CountDownLatch(1);
    fault.inviteReadRelease = new CountDownLatch(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<JsonNode> revoked =
          executor.submit(
              () ->
                  call(
                      "POST",
                      "/api/admin/factory-invitations/" + id + "/revoke",
                      view("reason", "并发锁定后作废"),
                      null,
                      Ids.next(),
                      409));
      assertTrue(fault.inviteReadEntered.await(5, TimeUnit.SECONDS));
      claim(user, invitation);
      fault.inviteReadRelease.countDown();
      revoked.get(5, TimeUnit.SECONDS);
      assertEquals("VERSION_CONFLICT", f.lastReason);
      assertEquals(
          "locked",
          f.db.queryForObject(
              "SELECT invite_status FROM factory_invite_code WHERE id=?", String.class, id));
    } finally {
      fault.inviteReadTarget = null;
      fault.inviteReadRelease.countDown();
      executor.shutdownNow();
    }
  }

  /** 验证旧基线的驳回和重提不会获得批准权限。 */
  @Test
  void incrementalStaleBaselineAllowsRejectionAndResubmissionWithoutApprovePermissionLeak()
      throws Exception {
    User user = user();
    String factory = approve(user, onboard(user));
    JsonNode profile = call("GET", "/api/me/factory-profile", null, user, null, 200);
    Map<String, Object> fields = existing(profile.path("materials"));
    fields.put("remark", "旧版本待审申请");
    Map<String, Object> body =
        view(
            "previousApplicationId",
            null,
            "baseProfileVersion",
            "1",
            "phoneVerificationProof",
            null,
            "proposed",
            fields);
    JsonNode change =
        call("POST", "/api/me/factory-profile-changes", body, user, Ids.next(), 200)
            .path("application");
    String id = change.path("id").asString(), path = "/api/admin/factory-profile-changes/" + id;
    f.db.update(
        "UPDATE factory_profile SET"
            + " profile_version=2,factory_name='隔离当前新版',factory_status='suspended' WHERE id=?",
        factory);
    JsonNode detail = call("GET", path, null, null, null, 200);
    assertTrue(detail.path("canReview").asBoolean());
    assertEquals("1", detail.path("baseProfileVersion").asString());
    assertEquals("2", detail.path("currentProfileVersion").asString());
    Map<String, Object> approve =
        view(
            "expectedVersion",
            0,
            "baseProfileVersion",
            "1",
            "sameLegalEntityConfirmed",
            false,
            "remark",
            null);
    call("POST", path + "/approve", approve, null, Ids.next(), 409);
    assertEquals("FACTORY_PROFILE_BASE_CHANGED", f.lastReason);
    assertEquals(
        "隔离当前新版",
        f.db.queryForObject(
            "SELECT factory_name FROM factory_profile WHERE id=?", String.class, factory));
    List<Map<String, Object>> grants =
        f.db.queryForList(
            "SELECT * FROM admin_role_menu WHERE admin_menu_id IN(SELECT id FROM admin_menu WHERE"
                + " permission_code='admin:factory-profile-change:review')");
    try {
      f.db.update(
          "DELETE FROM admin_role_menu WHERE admin_menu_id IN(SELECT id FROM admin_menu WHERE"
              + " permission_code='admin:factory-profile-change:review')");
      assertFalse(call("GET", path, null, null, null, 200).path("canReview").asBoolean());
      call("POST", path + "/approve", approve, null, Ids.next(), 403);
      call(
          "POST",
          path + "/reject",
          view("expectedVersion", 0, "reason", "请以新版本重提"),
          null,
          Ids.next(),
          403);
    } finally {
      for (Map<String, Object> grant : grants) f.restoreGrant(grant);
    }
    call("GET", "/api/admin/factories?status=suspended", null, null, null, 200);
    call(
        "POST",
        path + "/reject",
        view("expectedVersion", 0, "reason", "请以新版本重提"),
        null,
        Ids.next(),
        200);
    profile = call("GET", "/api/me/factory-profile", null, user, null, 200);
    fields = existing(profile.path("materials"));
    fields.put("remark", "基于新版重提");
    f.clock.at = f.clock.at.plusMillis(1);
    JsonNode retried =
        call(
                "POST",
                "/api/me/factory-profile-changes",
                view(
                    "previousApplicationId",
                    id,
                    "baseProfileVersion",
                    "2",
                    "phoneVerificationProof",
                    null,
                    "proposed",
                    fields),
                user,
                Ids.next(),
                200)
            .path("application");
    assertEquals(id, retried.path("previousApplicationId").asString());
    assertEquals("2", retried.path("baseProfileVersion").asString());
    assertEquals("pending", retried.path("auditStatus").asString());
    call("GET", "/api/admin/factory-onboarding", null, null, null, 200);
    call("GET", "/api/admin/factory-profile-changes", null, null, null, 200);
  }
}
