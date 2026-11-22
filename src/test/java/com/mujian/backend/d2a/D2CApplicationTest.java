package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.Fault;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import com.mujian.backend.infrastructure.wechat.WechatGateway;
import com.mujian.backend.material.mapper.MaterialReferenceMapper;
import com.mujian.backend.material.service.VerificationMaterialCleanup.Report;
import com.mujian.backend.material.service.VerificationMaterials.Receipt;
import com.mujian.backend.prop.converter.*;
import com.mujian.backend.prop.policy.*;
import com.mujian.backend.prop.policy.PropAvailability.Dates;
import com.mujian.backend.prop.service.*;
import com.mujian.backend.prop.service.PropCommandService;
import com.mujian.backend.prop.service.PropMaterialService;
import com.mujian.backend.prop.service.PropQrImages;
import com.mujian.backend.prop.service.PropReviewService;
import com.mujian.backend.prop.utils.*;
import java.io.ByteArrayOutputStream;
import java.net.http.HttpRequest.Builder;
import java.time.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class D2CApplicationTest {
  final D2BApplicationTest b = new D2BApplicationTest();
  final D2ApplicationTest f = b.f;

  /** 初始化本组隔离依赖。 */
  @BeforeAll
  void start() throws Exception {
    f.start(0, "--mj.prop.qr-worker-enabled=false");
    f.wx.delegate = null;
  }

  /** 释放本组创建的资源。 */
  @AfterAll
  void stop() {
    f.stop();
  }

  /** 准备工厂角色及其归属数据。 */
  D2ApplicationTest.User supplier() throws Exception {
    User user = b.user();
    b.approve(user, b.onboard(user));
    return user;
  }

  /** 执行请求并检查预期响应。 */
  JsonNode call(
      String method, String path, Object body, D2ApplicationTest.User user, String key, int status)
      throws Exception {
    return f.call(method, path, body, user, key, status);
  }

  /** 构造业务字段供提交使用。 */
  Map<String, Object> fields(D2ApplicationTest.User user) throws Exception {
    JsonNode options = call("GET", "/api/me/factory-props/options", null, user, null, 200);
    JsonNode upload =
        f.json.valueToTree(
            f.app
                .getBean(PropMaterialService.class)
                .upload(user.id(), "prop_image", D2ApplicationTest.png(), false));
    return view(
        "propName",
        "隔离道具" + Ids.next().substring(0, 4),
        "typeCode",
        options.path("types").get(0).path("code").asString(),
        "styleCodes",
        List.of("modern"),
        "sizeDesc",
        "尺寸说明",
        "lengthCm",
        "100.00",
        "widthCm",
        "50.00",
        "heightCm",
        "80.00",
        "materialDesc",
        "木质",
        "fireResistantCode",
        "no",
        "weightKg",
        "10.00",
        "packagingFactorBps",
        12000,
        "transportSuggestion",
        "专车运输",
        "dailyRentPriceFen",
        10000,
        "depositAmountFen",
        0,
        "images",
        List.of(view("kind", "upload", "uploadId", upload.path("uploadId").asString())),
        "remark",
        null);
  }

  /** 建立用例所需业务记录。 */
  JsonNode create(D2ApplicationTest.User user, String type, int quantity) throws Exception {
    return call(
        "POST",
        "/api/me/factory-props",
        view(
            "assetType",
            type,
            "fields",
            fields(user),
            "initialQuantity",
            quantity,
            "applyRemark",
            null),
        user,
        Ids.next(),
        200);
  }

  /** 以审核身份通过指定申请。 */
  JsonNode approve(String type, String id, int version) throws Exception {
    return call(
        "POST",
        "/api/admin/prop-reviews/" + type + "/" + id + "/approve",
        view("expectedVersion", version, "reason", "隔离测试批准"),
        null,
        Ids.next(),
        200);
  }

  /** 按本场景条件查询记录。 */
  String query(String value) {
    return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
  }

  // C-27 12.9.7: reuse existing isolated identities, capture only missing layered evidence.
  /** 采集不可租及跨工厂访问的页面证据。 */
  @Test
  void remainingUnavailableAndCrossFactoryPageEvidence() throws Exception {
    String database = f.db.queryForObject("SELECT DATABASE()", String.class);
    assertTrue(database.startsWith("darkmujian_d1_test_"));
    assertEquals("test", f.app.getEnvironment().getProperty("mj.environment"));
    List<String> owners =
        f.db.queryForList(
            "SELECT u.id FROM sys_user u JOIN sys_user_role r ON r.user_id=u.id AND"
                + " r.role_code='supplier' AND r.enabled=1 JOIN factory_profile p ON"
                + " p.owner_user_id=u.id WHERE u.user_status='active' AND"
                + " u.register_status='factory_bound' AND p.factory_status='active' AND EXISTS"
                + " (SELECT 1 FROM prop_sku sk JOIN prop_qr_code q ON q.sku_id=sk.id WHERE"
                + " sk.factory_id=p.id AND q.status='bound') ORDER BY u.id LIMIT 2",
            String.class);
    assertEquals(2, owners.size(), "reuse two existing isolated approved factory accounts");
    List<String> renterIds =
        f.db.queryForList(
            "SELECT u.id FROM sys_user u JOIN sys_user_role r ON r.user_id=u.id AND"
                + " r.role_code='demander' AND r.enabled=1 WHERE u.user_status='active' AND"
                + " u.register_status='demander_bound' ORDER BY u.id LIMIT 1",
            String.class);
    assertEquals(1, renterIds.size());
    User alpha =
        new D2ApplicationTest.User(
            owners.get(0), f.sessions.create(owners.get(0), "miniapp").session().getId());
    User beta =
        new D2ApplicationTest.User(
            owners.get(1), f.sessions.create(owners.get(1), "miniapp").session().getId());
    User renter =
        new D2ApplicationTest.User(
            renterIds.get(0), f.sessions.create(renterIds.get(0), "miniapp").session().getId());
    LinkedHashMap<String, Object> captures = new LinkedHashMap<String, Object>();
    captures.put(
        "environment",
        view(
            "database",
            database,
            "sessionNamespace",
            "mujian:test:session",
            "httpBase",
            f.base,
            "external",
            "test-only WeChat/material adapters; real HTTP/MySQL/Redis"));
    captures.put("actors", view("alpha", alpha.id(), "beta", beta.id(), "renter", renter.id()));
    try {
      List<String> ownIds =
          f.db.queryForList(
              "SELECT q.id FROM prop_qr_code q JOIN prop_sku s ON s.id=q.sku_id JOIN"
                  + " factory_profile p ON p.id=s.factory_id WHERE p.owner_user_id=? AND"
                  + " q.status='bound' ORDER BY q.id LIMIT 1",
              String.class,
              alpha.id());
      assertEquals(1, ownIds.size(), "reuse alpha bound label");
      JsonNode ownQr = call("GET", "/api/me/prop-qr/" + ownIds.get(0), null, alpha, null, 200);
      captures.put("ownQr", ownQr);
      captures.put(
          "ownSku",
          call(
              "GET",
              "/api/me/factory-props/" + ownQr.path("skuId").asString(),
              null,
              alpha,
              null,
              200));
      if (ownQr.path("targetType").asString().equals("SKU"))
        captures.put(
            "ownStock",
            call(
                "GET",
                "/api/me/factory-props/" + ownQr.path("skuId").asString() + "/stock",
                null,
                alpha,
                null,
                200));
      String aid = System.getProperty("mj.c1297.reuseA"),
          bid = System.getProperty("mj.c1297.reuseB");
      if (aid == null || bid == null) {
        JsonNode a = create(beta, "A_ASSET", 1);
        JsonNode bSku = create(beta, "B_CONSUMABLE", 1);
        aid = a.path("skuId").asString();
        bid = bSku.path("skuId").asString();
        approve("sku_audit", a.path("targetId").asString(), 0);
        approve("sku_audit", bSku.path("targetId").asString(), 0);
      }
      JsonNode decrease =
          call(
              "POST",
              "/api/me/factory-props/" + bid + "/stock-adjustments",
              view(
                  "adjustmentType",
                  "decrease",
                  "quantity",
                  1,
                  "reason",
                  "C1297 isolated zero stock"),
              beta,
              Ids.next(),
              200);
      approve("stock_adjustment", decrease.path("targetId").asString(), 0);
      JsonNode sn =
          call("GET", "/api/me/factory-props/" + aid + "/instances", null, beta, null, 200)
              .path("items")
              .get(0);
      JsonNode qr =
          call(
              "GET", "/api/me/prop-qr/" + sn.path("currentQrId").asString(), null, beta, null, 200);
      String snid = sn.path("id").asString(), qid = qr.path("id").asString();
      String day = now(f.clock).toLocalDate().plusDays(1).toString(),
          dates = "?startDate=" + day + "&endDate=" + day;
      captures.put(
          "samples",
          view(
              "a",
              aid,
              "b",
              bid,
              "sn",
              snid,
              "qr",
              qid,
              "scene",
              qr.path("qrCode").asString(),
              "stockAdjustment",
              decrease.path("targetId").asString(),
              "dates",
              dates));
      for (String sid : List.of(aid, bid)) {
        JsonNode detail = call("GET", "/api/me/props/" + sid + dates, null, renter, null, 200);
        assertEquals("unavailable", detail.path("availability").path("rentableStatus").asString());
        assertEquals(0, detail.path("availability").path("maxRentableQuantity").asInt());
        assertFalse(detail.path("details").path("images").isEmpty());
        captures.put(sid.equals(aid) ? "aUnavailable" : "bUnavailable", detail);
        String name = detail.path("prop").path("propName").asString();
        JsonNode list =
            call(
                "GET",
                "/api/me/props/rentable" + dates + "&keyword=" + query(name),
                null,
                renter,
                null,
                200);
        captures.put(sid.equals(aid) ? "aList" : "bList", list);
      }
      call("GET", "/api/me/props/" + Ids.next() + dates, null, renter, null, 404);
      assertEquals("RESOURCE_NOT_FOUND", f.lastReason);
      Map<String, Object> before = remainingSnapshot(aid, bid);
      for (String path :
          List.of(
              "/api/me/factory-props/" + aid,
              "/api/me/prop-instances/" + snid + "/logs",
              "/api/me/prop-reviews/stock_adjustment/" + decrease.path("targetId").asString(),
              "/api/me/factory-props/" + bid + "/stock",
              "/api/me/factory-props/" + bid + "/movements",
              "/api/me/prop-qr/" + qid)) {
        call("GET", path, null, alpha, null, 404);
        assertEquals("RESOURCE_NOT_FOUND", f.lastReason);
      }
      call(
          "POST",
          "/api/me/prop-qr/resolve",
          view("qrCode", qr.path("qrCode").asString()),
          alpha,
          null,
          404);
      assertEquals("RESOURCE_NOT_FOUND", f.lastReason);
      call(
          "POST",
          "/api/me/prop-instances/" + snid + "/label-confirm",
          view("expectedVersion", 0, "qrCode", qr.path("qrCode").asString()),
          alpha,
          Ids.next(),
          404);
      assertEquals("RESOURCE_NOT_FOUND", f.lastReason);
      call(
          "POST",
          "/api/me/factory-props/" + bid + "/stock-adjustments",
          view("adjustmentType", "increase", "quantity", 1, "reason", "foreign denied"),
          alpha,
          Ids.next(),
          404);
      assertEquals("RESOURCE_NOT_FOUND", f.lastReason);
      Map<String, Object> after = remainingSnapshot(aid, bid);
      assertEquals(before, after, "denied writes must preserve SN/stock/QR/audits/movements");
      captures.put("deniedBefore", before);
      captures.put("deniedAfter", after);
      captures.put(
          "ownResolved",
          call(
              "POST",
              "/api/me/prop-qr/resolve",
              view("qrCode", ownQr.path("qrCode").asString()),
              alpha,
              null,
              200));
      f.wx.delegate =
          new WechatGateway() {
            /** 模拟微信登录凭据交换。 */
            public Identity exchange(String a, String b) {
              throw new UnsupportedOperationException();
            }

            /** 返回测试用小程序码结果。 */
            public byte[] miniCode(String scene, String page, String env) {
              try {
                return D2ApplicationTest.png();
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
          };
      assertTrue(f.app.getBean(PropQrImages.class).generate(qid));
      f.wx.delegate = null;
      call(
          "POST",
          "/api/me/prop-instances/" + snid + "/label-confirm",
          view("expectedVersion", 0, "qrCode", qr.path("qrCode").asString()),
          beta,
          Ids.next(),
          200);
      JsonNode increase =
          call(
              "POST",
              "/api/me/factory-props/" + bid + "/stock-adjustments",
              view("adjustmentType", "increase", "quantity", 1, "reason", "C1297 restore sample"),
              beta,
              Ids.next(),
              200);
      approve("stock_adjustment", increase.path("targetId").asString(), 0);
      for (String sid : List.of(aid, bid)) {
        JsonNode detail = call("GET", "/api/me/props/" + sid + dates, null, renter, null, 200);
        assertEquals("rentable", detail.path("availability").path("rentableStatus").asString());
        assertEquals(1, detail.path("availability").path("maxRentableQuantity").asInt());
        captures.put(sid.equals(aid) ? "aRestored" : "bRestored", detail);
      }
    } finally {
      f.wx.delegate = null;
      for (User user : List.of(alpha, beta, renter)) {
        f.sessions.revoke(user.token());
        assertNull(f.sessions.load(user.token()));
      }
      f.sessions.revoke(f.adminCookie.substring("mj_admin_dev=".length()));
      captures.put("newSessionsRevoked", 4);
      String evidence = System.getenv("MJ_EVIDENCE_DIR");
      if (evidence != null)
        java.nio.file.Files.writeString(
            java.nio.file.Path.of(evidence, "c1297-scenarios.json"),
            f.json.writeValueAsString(captures));
    }
  }

  /** 获取剩余额度快照。 */
  Map<String, Object> remainingSnapshot(String a, String b) {
    LinkedHashMap<String, Object> out = new LinkedHashMap<String, Object>();
    for (String table :
        List.of(
            "prop_sku",
            "prop_sn_instance",
            "prop_sku_stock",
            "prop_qr_code",
            "prop_stock_movement",
            "prop_stock_adjustment_audit",
            "prop_sn_adjustment_audit",
            "prop_sku_audit")) {
      String col = table.equals("prop_sku") ? "id" : "sku_id";
      out.put(
          table,
          f.db.queryForList(
              "SELECT * FROM " + table + " WHERE " + col + " IN (?,?) ORDER BY id", a, b));
    }
    out.put(
        "snLogs",
        f.db.queryForList(
            "SELECT l.* FROM prop_instance_status_log l JOIN prop_sn_instance s ON"
                + " s.id=l.prop_instance_id WHERE s.sku_id=? ORDER BY l.id",
            a));
    return out;
  }

  /** 核对中文关键词与 ASCII 业务编码混合查询。 */
  @Test
  void unicodeKeywordsAcrossAsciiCodeSearches() throws Exception {
    User user = supplier();
    String marker = "中文编号检索" + Ids.next();
    ArrayList<String> ids = new ArrayList<String>();
    for (String type : List.of("A_ASSET", "B_CONSUMABLE")) {
      Map<String, Object> values = fields(user);
      values.put("propName", marker + type);
      JsonNode made =
          call(
              "POST",
              "/api/me/factory-props",
              view("assetType", type, "fields", values, "initialQuantity", 1, "applyRemark", null),
              user,
              Ids.next(),
              200);
      ids.add(made.path("skuId").asString());
      approve("sku_audit", made.path("targetId").asString(), 0);
    }
    JsonNode owned = call("GET", "/api/me/factory-props/" + ids.get(1), null, user, null, 200);
    String factory = owned.path("factoryId").asString();
    String code = owned.path("skuCode").asString();
    for (String root : List.of("/api/admin/prop-assets", "/api/me/factory-props")) {
      User actor = root.contains("/me/") ? user : null;
      String scope = root.contains("/admin/") ? "&factoryId=" + factory : "";
      for (String term : List.of(marker, marker + "%", code, code.toLowerCase(Locale.ROOT))) {
        int expected = term.equals(marker) ? 2 : term.equals(code) ? 1 : 0;
        assertEquals(
            expected,
            call("GET", root + "?keyword=" + query(term) + scope, null, actor, null, 200)
                .path("total")
                .asInt(),
            root + " " + term);
      }
    }
    for (String root : List.of("/api/admin/prop-reviews")) {
      User actor = root.contains("/me/") ? user : null;
      String scope = root.contains("/admin/") ? "&factoryId=" + factory : "";
      for (String source : List.of("", "sku_audit", "sn_adjustment", "stock_adjustment")) {
        int expected = source.isEmpty() ? 4 : source.equals("sku_audit") ? 2 : 1;
        JsonNode result =
            call(
                "GET",
                root
                    + "?keyword="
                    + query(marker)
                    + "&auditStatus=approved"
                    + scope
                    + (source.isEmpty() ? "" : "&sourceType=" + source),
                null,
                actor,
                null,
                200);
        assertEquals(expected, result.path("total").asInt(), root + " " + source);
        assertEquals(0, result.path("pendingCounts").path("total").asInt());
      }
    }
    call("GET", "/api/me/prop-reviews?keyword=" + query(marker), null, user, null, 400);
    LocalDate day = now(f.clock).toLocalDate();
    reservation(ids.get(1), null, 1, day, day, "paid_locked");
    assertEquals(
        1,
        call(
                "GET",
                "/api/admin/inventory-reservations?keyword=" + query(marker),
                null,
                null,
                null,
                200)
            .path("total")
            .asInt());
    String qr =
        f.db.queryForObject("SELECT id FROM prop_qr_code WHERE sku_id=?", String.class, ids.get(0));
    call(
        "POST",
        "/api/admin/prop-qr-exceptions",
        view("qrId", qr, "exceptionType", "other", "description", "隔离中文检索", "evidence", List.of()),
        null,
        Ids.next(),
        200);
    assertEquals(
        0,
        call(
                "GET",
                "/api/admin/prop-qr-exceptions?factoryId=" + factory + "&keyword=" + query(marker),
                null,
                null,
                null,
                200)
            .path("total")
            .asInt());
    String snCode =
        f.db.queryForObject(
            "SELECT sn_code FROM prop_sn_instance WHERE sku_id=?", String.class, ids.get(0));
    assertEquals(
        1,
        call(
                "GET",
                "/api/admin/prop-qr-exceptions?factoryId=" + factory + "&keyword=" + query(snCode),
                null,
                null,
                null,
                200)
            .path("total")
            .asInt());
  }

  /** 核对工厂编码搜索的中文关键词。 */
  @Test
  void unicodeKeywordsAcrossFactoryCodeSearches() throws Exception {
    User user = supplier();
    JsonNode profile = call("GET", "/api/me/factory-profile", null, user, null, 200);
    String name = profile.path("materials").path("factoryName").asString();
    assertTrue(
        call(
                    "GET",
                    "/api/admin/factory-onboarding?status=approved&keyword=" + query(name),
                    null,
                    null,
                    null,
                    200)
                .path("total")
                .asInt()
            >= 1);
    Map<String, Object> values = b.existing(profile.path("materials"));
    String marker = "中文变更检索" + Ids.next();
    values.put("factoryName", marker);
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
            values),
        user,
        Ids.next(),
        200);
    assertEquals(
        1,
        call(
                "GET",
                "/api/admin/factory-profile-changes?keyword=" + query(marker),
                null,
                null,
                null,
                200)
            .path("total")
            .asInt());
    assertEquals(
        0,
        call("GET", "/api/admin/factory-invitations?keyword=" + query("中文"), null, null, null, 200)
            .path("total")
            .asInt());
  }

  /** 检查跨版本历史名称、筛选、库存与日期。 */
  @Test
  void crossRevisionHistoryLabelsFiltersInventoryAndDates() throws Exception {
    User user = supplier();
    User demander = f.user();
    Map<String, Object> fields = fields(user);
    String marker = "交叉" + Ids.next(), literal = marker + "%_";
    fields.put("propName", marker);
    fields.put("materialDesc", literal);
    fields.put("styleCodes", List.of("modern", "retro"));
    JsonNode created =
        call(
            "POST",
            "/api/me/factory-props",
            view(
                "assetType",
                "B_CONSUMABLE",
                "fields",
                fields,
                "initialQuantity",
                8,
                "applyRemark",
                "首次八件"),
            user,
            Ids.next(),
            200);
    String sid = created.path("skuId").asString(), aid = created.path("targetId").asString();
    String path = "/api/admin/prop-reviews/sku_audit/" + aid;
    JsonNode sku = call("GET", "/api/me/factory-props/" + sid, null, user, null, 200);
    String factory = sku.path("factoryId").asString(), code = sku.path("skuCode").asString();
    assertTrue(sku.path("inventorySummary").isNull());
    assertFalse(sku.path("canAdjustInventory").asBoolean());
    JsonNode pending = call("GET", "/api/me/prop-reviews", null, user, null, 200);
    assertEquals(1, pending.path("pendingCounts").path("sku").asInt());
    String group = "prop_sku_style-style";
    String old =
        f.db.queryForObject(
            "SELECT option_label FROM option_dictionary WHERE option_group=? AND"
                + " option_code='modern'",
            String.class,
            group);
    f.db.update(
        "UPDATE option_dictionary SET option_label=? WHERE option_group=? AND option_code='modern'",
        marker,
        group);
    assertTrue(f.app.getBean(com.mujian.backend.common.dictionary.DictionaryCache.class).refresh());
    try {
      JsonNode review = call("GET", path, null, null, null, 200);
      assertEquals(old, review.path("proposed").path("styleLabels").get(0).asString());
      assertEquals(marker, review.path("current").path("styleLabels").get(0).asString());
      assertEquals("filled", review.path("currentFillStatus").asString());
      assertTrue(review.hasNonNull("currentUpdatedAt"));
      List<Map<String, Object>> grants =
          f.db.queryForList(
              "SELECT * FROM admin_role_menu WHERE admin_menu_id IN (SELECT id FROM admin_menu"
                  + " WHERE permission_code LIKE 'admin:prop-asset:%')");
      f.db.update(
          "DELETE FROM admin_role_menu WHERE admin_menu_id IN (SELECT id FROM admin_menu WHERE"
              + " permission_code LIKE 'admin:prop-asset:%')");
      try {
        JsonNode locator = review.path("current").path("images").get(0).path("locator");
        assertEquals("sku_audit", locator.path("sourceType").asString());
        call("POST", "/api/admin/prop-materials/view", locator, null, null, 200);
      } finally {
        for (Map<String, Object> grant : grants) f.restoreGrant(grant);
      }
      approve("sku_audit", aid, 0);
      for (String keyword : List.of(marker, literal)) {
        JsonNode found =
            call("GET", "/api/me/props?keyword=" + query(keyword), null, demander, null, 200);
        if (keyword.equals(literal)) assertEquals(0, found.path("total").asInt());
        else assertTrue(found.path("total").asInt() > 0);
      }
      assertEquals(
          0,
          call("GET", "/api/me/props?keyword=" + query(marker + "__"), null, demander, null, 200)
              .path("total")
              .asInt());
    } finally {
      f.db.update(
          "UPDATE option_dictionary SET option_label=? WHERE option_group=? AND"
              + " option_code='modern'",
          old,
          group);
      assertTrue(
          f.app.getBean(com.mujian.backend.common.dictionary.DictionaryCache.class).refresh());
    }
    sku = call("GET", "/api/me/factory-props/" + sid, null, user, null, 200);
    assertEquals(8, sku.path("inventorySummary").path("totalQuantity").asInt());
    assertTrue(sku.path("canAdjustInventory").asBoolean());
    String suffix = "?factoryId=" + factory + "&keyword=" + query(code);
    JsonNode list =
        call(
            "GET",
            "/api/admin/prop-assets" + suffix + "&styleCodes=modern&styleCodes=retro",
            null,
            null,
            null,
            200);
    assertEquals(1, list.path("total").asInt());
    assertFalse(list.path("items").get(0).path("canAdjustInventory").asBoolean());
    for (String bad :
        List.of(
            "styleCode=modern", "styleCodes=modern,retro", "styleCodes=modern&styleCodes=modern"))
      call("GET", "/api/admin/prop-assets" + suffix + "&" + bad, null, null, null, 400);
    assertEquals(
        1,
        call("GET", "/api/me/factory-props?keyword=" + query(code), null, user, null, 200)
            .path("total")
            .asInt());
    // 当前契约取消字典存在性预检；合法但无关联的风格编码返回空页。
    assertEquals(
        0,
        call(
                "GET",
                "/api/admin/prop-assets" + suffix + "&styleCodes=unknown",
                null,
                null,
                null,
                200)
            .path("total")
            .asInt());
    JsonNode history = call("GET", "/api/me/prop-reviews", null, user, null, 200);
    assertEquals(2, history.path("total").asInt());
    assertEquals(0, history.path("pendingCounts").path("total").asInt());
    JsonNode initial =
        call(
            "GET",
            "/api/admin/prop-reviews?factoryId="
                + factory
                + "&auditStatus=all&reviewCategory=inventory",
            null,
            null,
            null,
            200);
    assertEquals(1, initial.path("total").asInt());
    assertEquals(aid, initial.path("items").get(0).path("relatedSkuAuditId").asString());
    assertEquals(8, initial.path("items").get(0).path("quantityDelta").asInt());
    JsonNode adjust =
        call(
            "POST",
            "/api/me/factory-props/" + sid + "/stock-adjustments",
            view("adjustmentType", "increase", "quantity", 2, "reason", "补两件"),
            user,
            Ids.next(),
            200);
    JsonNode stats =
        call(
            "GET",
            "/api/admin/prop-reviews?factoryId=" + factory + "&auditStatus=approved&pageSize=1",
            null,
            null,
            null,
            200);
    assertEquals(2, stats.path("total").asInt());
    assertEquals(1, stats.path("pendingCounts").path("inventory").asInt());
    call(
        "GET",
        "/api/admin/prop-reviews/stock_adjustment/" + adjust.path("targetId").asString(),
        null,
        null,
        null,
        200);
    approve("stock_adjustment", adjust.path("targetId").asString(), 0);
    String day = now(f.clock).toLocalDate().toString();
    for (String root : List.of("/api/me/factory-props/", "/api/admin/prop-assets/")) {
      User actor = root.contains("/me/") ? user : null;
      JsonNode movements =
          call(
              "GET",
              root
                  + sid
                  + "/movements?startDate="
                  + day
                  + "&sourceType=manual_adjustment&movementType=increase",
              null,
              actor,
              null,
              200);
      assertEquals(2, movements.path("total").asInt());
      boolean delta = false;
      for (JsonNode m : movements.path("items"))
        if (m.path("beforeQuantity").asInt() == 8 && m.path("afterQuantity").asInt() == 10)
          delta = true;
      assertTrue(delta);
      assertEquals(
          2,
          call("GET", root + sid + "/movements?endDate=" + day, null, actor, null, 200)
              .path("total")
              .asInt());
      call(
          "GET",
          root + sid + "/movements?startDate=2027-01-02&endDate=2027-01-01",
          null,
          actor,
          null,
          400);
    }
    reservation(
        sid, null, 1, now(f.clock).toLocalDate(), now(f.clock).toLocalDate(), "paid_locked");
    assertEquals(
        1,
        call(
                "GET",
                "/api/admin/inventory-reservations?keyword=" + query(code),
                null,
                null,
                null,
                200)
            .path("total")
            .asInt());
    call("GET", "/api/admin/inventory-reservations?skuId=" + Ids.next(), null, null, null, 404);
    call("GET", "/api/admin/inventory-reservations?keyword=%20%20", null, null, null, 400);
    assertThrows(
        org.springframework.dao.DataAccessException.class,
        () -> f.db.update("UPDATE prop_qr_code SET status='unbound' WHERE sku_id=?", sid));
    f.db.update("UPDATE prop_qr_code SET status='unused' WHERE sku_id=?", sid);
    f.db.update("UPDATE prop_qr_code SET status='bound' WHERE sku_id=?", sid);
  }

  /** 核对不同版本的签名链及异常数量。 */
  @Test
  void crossRevisionLatestChainSigningAndExceptionCounts() throws Exception {
    User user = supplier();
    JsonNode c = create(user, "B_CONSUMABLE", 5);
    String sid = c.path("skuId").asString();
    approve("sku_audit", c.path("targetId").asString(), 0);
    JsonNode sku = call("GET", "/api/me/factory-props/" + sid, null, user, null, 200);
    String first = sku.path("currentQrId").asString(), factory = sku.path("factoryId").asString();
    assertEquals(first, sku.path("latestQrId").asString());
    f.wx.delegate = pngGateway();
    try {
      assertTrue(f.app.getBean(PropQrImages.class).generate(first));
    } finally {
      f.wx.delegate = null;
    }
    JsonNode qr = call("GET", "/api/me/prop-qr/" + first, null, user, null, 200);
    assertTrue(qr.path("snCode").isNull());
    assertTrue(qr.path("instanceStatus").isNull());
    f.store.failSign = true;
    try {
      for (String root : List.of("/api/me/prop-qr/", "/api/admin/prop-qr/")) {
        User actor = root.contains("/me/") ? user : null;
        call("GET", root + first, null, actor, null, 503);
        assertEquals("OBJECT_STORAGE_UNAVAILABLE", f.lastReason);
        call("POST", root + first + "/retry-image", null, actor, null, 503);
      }
      call(
          "POST",
          "/api/me/prop-qr/resolve",
          view("qrCode", qr.path("qrCode").asString()),
          user,
          null,
          503);
    } finally {
      f.store.failSign = false;
    }
    String latest = first;
    for (int i = 0; i < 2; i++) {
      JsonNode report =
          call(
              "POST",
              "/api/admin/prop-qr-exceptions",
              view(
                  "qrId",
                  latest,
                  "exceptionType",
                  "suspected_leak",
                  "description",
                  "疑似泄露核查",
                  "evidence",
                  List.of()),
              null,
              Ids.next(),
              200);
      JsonNode stats =
          call(
              "GET",
              "/api/admin/prop-qr-exceptions?factoryId=" + factory + "&status=all",
              null,
              null,
              null,
              200);
      assertEquals(1, stats.path("counts").path("reported").asInt());
      assertEquals(1, stats.path("counts").path("suspectedLeak").asInt());
      call(
          "POST",
          "/api/admin/prop-qr-exceptions/" + report.path("targetId").asString() + "/approve",
          view("expectedVersion", 0, "reason", "停用"),
          null,
          Ids.next(),
          200);
      JsonNode detail =
          call(
              "GET",
              "/api/admin/prop-qr-exceptions/" + report.path("targetId").asString(),
              null,
              null,
              null,
              200);
      assertTrue(detail.hasNonNull("resolvedByAdminUserId"));
      assertTrue(detail.path("replacementQrId").isNull());
      sku = call("GET", "/api/me/factory-props/" + sid, null, user, null, 200);
      assertTrue(sku.path("currentQrId").isNull());
      assertEquals(latest, sku.path("latestQrId").asString());
      f.store.failSign = true;
      try {
        assertTrue(
            call("GET", "/api/me/prop-qr/" + latest, null, user, null, 200).path("image").isNull());
      } finally {
        f.store.failSign = false;
      }
      if (i == 0) {
        latest =
            call(
                    "POST",
                    "/api/admin/prop-qr/issue",
                    view(
                        "targetType",
                        "SKU",
                        "targetId",
                        sid,
                        "supersedesQrId",
                        latest,
                        "reason",
                        "替换"),
                    null,
                    Ids.next(),
                    200)
                .path("targetId")
                .asString();
      }
    }
    call(
        "POST",
        "/api/admin/prop-qr/issue",
        view("targetType", "SKU", "targetId", sid, "supersedesQrId", first, "reason", "旧页面"),
        null,
        Ids.next(),
        409);
    assertEquals("QR_TARGET_MISMATCH", f.lastReason);
    JsonNode end =
        call(
            "GET",
            "/api/admin/prop-qr-exceptions?factoryId=" + factory + "&status=reported",
            null,
            null,
            null,
            200);
    assertEquals(0, end.path("total").asInt());
    assertEquals(2, end.path("counts").path("resolvedToday").asInt());
    assertEquals(
        5,
        call("GET", "/api/me/factory-props/" + sid + "/stock", null, user, null, 200)
            .path("totalQuantity")
            .asInt());
  }

  /** 检查 B 类首次上架、流水及状态回放。 */
  @Test
  void initialBApprovalStockMovementAndStatusReplay() throws Exception {
    User user = supplier();
    Map<String, Object> input =
        view(
            "assetType",
            "B_CONSUMABLE",
            "fields",
            fields(user),
            "initialQuantity",
            10,
            "applyRemark",
            null);
    String key = Ids.next();
    JsonNode created = call("POST", "/api/me/factory-props", input, user, key, 200);
    String sid = created.path("skuId").asString(), aid = created.path("targetId").asString();
    assertEquals("submitted", created.path("outcome").asString());
    assertEquals(
        0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM prop_sku_stock WHERE sku_id=?", Integer.class, sid));
    call("GET", "/api/admin/prop-reviews/sku_audit/" + aid, null, null, null, 200);
    approve("sku_audit", aid, 0);
    assertEquals(
        "replayed",
        call("POST", "/api/me/factory-props", input, user, key, 200).path("outcome").asString());
    JsonNode stock = call("GET", "/api/me/factory-props/" + sid + "/stock", null, user, null, 200);
    assertEquals(10, stock.path("totalQuantity").asInt());
    assertEquals(
        1,
        call("GET", "/api/me/factory-props/" + sid + "/movements", null, user, null, 200)
            .path("items")
            .size());
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM prop_stock_adjustment_audit WHERE related_sku_audit_id=? AND"
                + " audit_status='approved' AND version=1",
            Integer.class,
            aid));
    call("GET", "/api/public/props/" + sid, null, null, null, 200);
    JsonNode down =
        call(
            "POST",
            "/api/me/factory-props/" + sid + "/status-applications",
            view("expectedSkuVersion", 1, "actionType", "down", "applyRemark", "暂停"),
            user,
            Ids.next(),
            200);
    call("GET", "/api/public/props/" + sid, null, null, null, 404);
    call(
        "POST",
        "/api/me/factory-props/" + sid + "/stock-adjustments",
        view("adjustmentType", "increase", "quantity", 1, "reason", "增补"),
        user,
        Ids.next(),
        409);
    assertEquals("SKU_AUDITING", f.lastReason);
    approve("sku_audit", down.path("targetId").asString(), 0);
    assertEquals(
        "offline",
        call("GET", "/api/me/factory-props/" + sid, null, user, null, 200)
            .path("skuStatus")
            .asString());
  }

  /** 检查 A 类上架后的二维码重试、标签及维修。 */
  @Test
  void initialAThenQrFailureRetryLabelAndRepair() throws Exception {
    User user = supplier();
    JsonNode created = create(user, "A_ASSET", 3);
    String sid = created.path("skuId").asString();
    approve("sku_audit", created.path("targetId").asString(), 0);
    JsonNode list = call("GET", "/api/me/factory-props/" + sid + "/instances", null, user, null, 200);
    assertEquals(3, list.path("items").size());
    assertEquals(
        0,
        call("GET", "/api/me/factory-props/" + sid + "/instance-summary", null, user, null, 200)
            .path("total")
            .asInt());
    JsonNode sn = list.path("items").get(0);
    String snid = sn.path("id").asString();
    JsonNode qr =
        call("GET", "/api/me/prop-qr/" + sn.path("currentQrId").asString(), null, user, null, 200);
    String qid = qr.path("id").asString();
    call(
        "POST",
        "/api/me/prop-instances/" + snid + "/label-confirm",
        view("expectedVersion", 0, "qrCode", qr.path("qrCode").asString()),
        user,
        Ids.next(),
        409);
    assertEquals("QR_IMAGE_NOT_READY", f.lastReason);
    PropQrImages worker = f.app.getBean(PropQrImages.class);
    assertFalse(worker.generate(qid));
    call("POST", "/api/me/prop-qr/" + qid + "/retry-image", null, user, null, 200);
    f.wx.delegate =
        new WechatGateway() {
          /** 模拟微信登录凭据交换。 */
          public Identity exchange(String a, String b) {
            throw new UnsupportedOperationException();
          }

          /** 返回测试用小程序码结果。 */
          public byte[] miniCode(String scene, String page, String env) {
            try {
              return D2ApplicationTest.png();
            } catch (Exception e) {
              throw new RuntimeException(e);
            }
          }
        };
    assertTrue(worker.generate(qid));
    f.wx.delegate = null;
    String labelKey = Ids.next();
    Map<String, Object> label = view("expectedVersion", 0, "qrCode", qr.path("qrCode").asString());
    call("POST", "/api/me/prop-instances/" + snid + "/label-confirm", label, user, labelKey, 200);
    call("POST", "/api/me/prop-instances/" + snid + "/label-confirm", label, user, labelKey, 200);
    JsonNode repair =
        call(
            "POST",
            "/api/me/factory-props/" + sid + "/sn-adjustments",
            view("adjustmentType", "repair", "snIds", List.of(snid), "reason", "维修"),
            user,
            Ids.next(),
            200);
    approve("sn_adjustment", repair.path("targetId").asString(), 0);
    call(
        "POST",
        "/api/me/prop-instances/" + snid + "/repair-complete",
        view("expectedVersion", 2, "reason", "维修完成"),
        user,
        Ids.next(),
        200);
    assertEquals(
        3,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM prop_sn_instance WHERE sku_id=?", Integer.class, sid));
    assertEquals(
        4,
        call("GET", "/api/me/prop-instances/" + snid + "/logs", null, user, null, 200)
            .path("items")
            .size());
  }

  /** 验证驳回重提、取消及数据归属。 */
  @Test
  void rejectionResubmitCancelAndOwnership() throws Exception {
    User user = supplier();
    User other = supplier();
    JsonNode c = create(user, "B_CONSUMABLE", 4);
    String sid = c.path("skuId").asString(), aid = c.path("targetId").asString();
    call("GET", "/api/me/factory-props/" + sid, null, other, null, 404);
    call("GET", "/api/me/prop-reviews/sku_audit/" + aid, null, other, null, 404);
    call(
        "POST",
        "/api/admin/prop-reviews/sku_audit/" + aid + "/reject",
        view("expectedVersion", 0, "reason", "补充材料"),
        null,
        Ids.next(),
        200);
    JsonNode old = call("GET", "/api/me/prop-reviews/sku_audit/" + aid, null, user, null, 200);
    Map<String, Object> input =
        view(
            "previousApplicationId",
            aid,
            "expectedSkuVersion",
            0,
            "fields",
            fields(user),
            "initialQuantity",
            7,
            "applyRemark",
            "已补充");
    String key = Ids.next();
    JsonNode r = call("POST", "/api/me/factory-props/" + sid + "/resubmit", input, user, key, 200);
    JsonNode repeated =
        call("POST", "/api/me/factory-props/" + sid + "/resubmit", input, user, Ids.next(), 200);
    assertEquals("existing_pending", repeated.path("outcome").asString());
    LinkedHashMap<String, Object> changed = new LinkedHashMap<>(input);
    changed.put("initialQuantity", 8);
    call("POST", "/api/me/factory-props/" + sid + "/resubmit", changed, user, key, 409);
    assertEquals("IDEMPOTENCY_KEY_REUSED", f.lastReason);
    String next = r.path("targetId").asString();
    call(
        "POST",
        "/api/me/prop-reviews/sku_audit/" + next + "/cancel",
        view("expectedVersion", 0, "reason", "撤回"),
        user,
        Ids.next(),
        200);
    assertEquals(
        "cancelled",
        call("GET", "/api/me/prop-reviews/sku_audit/" + next, null, user, null, 200)
            .path("auditStatus")
            .asString());
    assertEquals(
        old.path("proposed").path("propName"),
        call("GET", "/api/me/prop-reviews/sku_audit/" + aid, null, user, null, 200)
            .path("proposed")
            .path("propName"));
    call("GET", "/api/me/prop-reviews?auditStatus=cancelled", null, user, null, 200);
    call("GET", "/api/admin/prop-reviews?auditStatus=rejected", null, null, null, 200);
  }

  /** 验证并发库存审批不超扣且可回放。 */
  @Test
  void stockConcurrentApprovalCannotOverdrawAndReplay() throws Exception {
    User user = supplier();
    JsonNode c = create(user, "B_CONSUMABLE", 10);
    String sid = c.path("skuId").asString();
    approve("sku_audit", c.path("targetId").asString(), 0);
    JsonNode x =
        call(
            "POST",
            "/api/me/factory-props/" + sid + "/stock-adjustments",
            view(
                "adjustmentType",
                "abnormal_decrease",
                "quantity",
                7,
                "abnormalResult",
                "lost",
                "reason",
                "盘亏"),
            user,
            Ids.next(),
            200);
    JsonNode y =
        call(
            "POST",
            "/api/me/factory-props/" + sid + "/stock-adjustments",
            view(
                "adjustmentType",
                "abnormal_decrease",
                "quantity",
                7,
                "abnormalResult",
                "scrapped",
                "reason",
                "损坏"),
            user,
            Ids.next(),
            200);
    PropReviewService review = f.app.getBean(PropReviewService.class);
    CountDownLatch latch = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    ArrayList<Future<String>> tasks = new ArrayList<Future<String>>();
    for (JsonNode a : List.of(x, y))
      tasks.add(
          pool.submit(
              () -> {
                latch.await();
                try {
                  review.decide(
                      f.admin,
                      "stock_adjustment",
                      a.path("targetId").asString(),
                      Ids.next(),
                      true,
                      new com.mujian.backend.prop.dto.ReviewDecisionDTO(0, "并发批准"),
                      Ids.next());
                  return "ok";
                } catch (com.mujian.backend.common.api.ApiFailure e) {
                  return e.reason;
                }
              }));
    latch.countDown();
    List<String> outcomes =
        List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS));
    pool.shutdownNow();
    assertEquals(1, outcomes.stream().filter("ok"::equals).count());
    assertTrue(outcomes.contains("STOCK_NOT_ENOUGH"));
    JsonNode stock = call("GET", "/api/me/factory-props/" + sid + "/stock", null, user, null, 200);
    assertEquals(3, stock.path("totalQuantity").asInt());
    assertEquals(3, stock.path("rentableBaseQuantity").asInt());
    assertEquals(7, stock.path("lostQuantity").asInt() + stock.path("scrappedQuantity").asInt());
    assertEquals(
        2,
        call("GET", "/api/me/factory-props/" + sid + "/movements", null, user, null, 200)
            .path("items")
            .size());
  }

  /** 准备库存占用记录。 */
  void reservation(String sid, String sn, int quantity, LocalDate a, LocalDate z, String state) {
    String id = Ids.next();
    f.db.update(
        "INSERT INTO"
            + " inventory_reservation(id,sku_id,sn_id,quantity,rental_start_date,rental_end_date,occupy_start_date,occupy_end_date,reservation_status,expires_at,idempotency_key,created_at,updated_at)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        sid,
        sn,
        quantity,
        a,
        z,
        a,
        z,
        state,
        now(f.clock).minusDays(1),
        "test:" + id,
        now(f.clock),
        now(f.clock));
  }

  /** 对照实际 SQL 检查每日峰值、状态及损失去重。 */
  @Test
  void actualSqlDailyPeakInclusiveStatesWideAndNoDoubleLoss() throws Exception {
    User user = supplier();
    JsonNode c = create(user, "B_CONSUMABLE", 100);
    String sid = c.path("skuId").asString();
    approve("sku_audit", c.path("targetId").asString(), 0);
    LocalDate today = now(f.clock).toLocalDate();
    ArrayList<Object[]> specs = new ArrayList<Object[]>();
    Random rand = new Random(282026);
    String[] states = {
      "held",
      "paid_locked",
      "renting",
      "overdue_holding",
      "disputed_holding",
      "released",
      "cancelled",
      "expired",
      "completed"
    };
    for (int n = 0; n < 45; n++) {
      int a = rand.nextInt(15), z = a + rand.nextInt(6), quantity = 1 + rand.nextInt(8);
      String state = states[n % states.length];
      specs.add(new Object[] {a, z, quantity, state});
      reservation(sid, null, quantity, today.plusDays(a), today.plusDays(z), state);
    }
    for (int a = 0; a < 15; a++) {
      int z = Math.min(20, a + 5), peak = 0;
      for (int day = a; day <= z; day++) {
        int sum = 0;
        for (Object[] v : specs)
          if ((int) v[0] <= day
              && (int) v[1] >= day
              && Set.of(states[0], states[1], states[2], states[3], states[4]).contains(v[3]))
            sum += (int) v[2];
        peak = Math.max(peak, sum);
      }
      JsonNode response =
          call(
              "GET",
              "/api/admin/prop-assets/"
                  + sid
                  + "/availability?startDate="
                  + today.plusDays(a)
                  + "&endDate="
                  + today.plusDays(z),
              null,
              null,
              null,
              200);
      assertEquals(Math.max(100 - peak, 0), response.path("maxRentableQuantity").asInt());
    }
    reservation(sid, null, Integer.MAX_VALUE, today, today, "held");
    reservation(sid, null, Integer.MAX_VALUE, today, today, "held");
    assertEquals(
        0,
        call(
                "GET",
                "/api/admin/prop-assets/"
                    + sid
                    + "/availability?startDate="
                    + today
                    + "&endDate="
                    + today,
                null,
                null,
                null,
                200)
            .path("maxRentableQuantity")
            .asInt());
    call(
        "GET",
        "/api/admin/inventory-reservations?skuId="
            + sid
            + "&startDate="
            + today
            + "&endDate="
            + today.plusDays(20),
        null,
        null,
        null,
        200);
    call("GET", "/api/public/props?keyword=绕过", null, null, null, 400);
    call(
        "GET",
        "/api/admin/prop-assets/"
            + sid
            + "/availability?startDate="
            + today
            + "&endDate="
            + today.plusDays(120),
        null,
        null,
        null,
        400);
  }

  /** 验证二维码异常停用和独立补发对库存的影响。 */
  @Test
  void qrExceptionDisablesOnlyAndSeparateIssueKeepsInventory() throws Exception {
    User user = supplier();
    JsonNode c = create(user, "B_CONSUMABLE", 9);
    String sid = c.path("skuId").asString();
    approve("sku_audit", c.path("targetId").asString(), 0);
    String qr =
        f.db.queryForObject("SELECT id FROM prop_qr_code WHERE sku_id=?", String.class, sid);
    JsonNode report =
        call(
            "POST",
            "/api/admin/prop-qr-exceptions",
            view(
                "qrId",
                qr,
                "exceptionType",
                "suspected_leak",
                "description",
                "隔离测试",
                "evidence",
                List.of()),
            null,
            Ids.next(),
            200);
    String aid = report.path("targetId").asString();
    call("GET", "/api/admin/prop-qr-exceptions/" + aid, null, null, null, 200);
    call("GET", "/api/admin/prop-qr-exceptions", null, null, null, 200);
    call(
        "POST",
        "/api/admin/prop-qr-exceptions/" + aid + "/approve",
        view("expectedVersion", 0, "reason", "停用原码"),
        null,
        Ids.next(),
        200);
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM prop_qr_code WHERE sku_id=?", Integer.class, sid));
    String key = Ids.next();
    Map<String, Object> body =
        view("targetType", "SKU", "targetId", sid, "supersedesQrId", qr, "reason", "独立补码");
    JsonNode issued = call("POST", "/api/admin/prop-qr/issue", body, null, key, 200);
    call("POST", "/api/admin/prop-qr/issue", body, null, key, 200);
    call(
        "GET",
        "/api/admin/prop-operations/by-request/" + key + "?operation=issue_qr",
        null,
        null,
        null,
        200);
    call("GET", "/api/admin/prop-qr/" + issued.path("targetId").asString(), null, null, null, 200);
    assertEquals(
        2,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM prop_qr_code WHERE sku_id=?", Integer.class, sid));
    assertEquals(
        9,
        call("GET", "/api/me/factory-props/" + sid + "/stock", null, user, null, 200)
            .path("totalQuantity")
            .asInt());
  }

  /** 验证审批与撤回竞争仅一个终态生效。 */
  @Test
  void approveWithdrawRaceHasOnlyOneTerminalEffect() throws Exception {
    User user = supplier();
    JsonNode c = create(user, "B_CONSUMABLE", 5);
    String aid = c.path("targetId").asString(), sid = c.path("skuId").asString();
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch latch = new CountDownLatch(1);
    Future<String> approved =
        pool.submit(
            () -> {
              latch.await();
              try {
                f.app
                    .getBean(PropReviewService.class)
                    .decide(
                        f.admin,
                        "sku_audit",
                        aid,
                        Ids.next(),
                        true,
                        new com.mujian.backend.prop.dto.ReviewDecisionDTO(0, "同意"),
                        Ids.next());
                return "ok";
              } catch (com.mujian.backend.common.api.ApiFailure e) {
                return e.reason;
              }
            });
    Future<String> cancelled =
        pool.submit(
            () -> {
              latch.await();
              try {
                f.app
                    .getBean(PropCommandService.class)
                    .cancel(
                        user.id(),
                        "sku_audit",
                        aid,
                        Ids.next(),
                        new com.mujian.backend.prop.dto.VersionReasonDTO(0, "撤回"));
                return "ok";
              } catch (com.mujian.backend.common.api.ApiFailure e) {
                return e.reason;
              }
            });
    latch.countDown();
    List<String> out =
        List.of(approved.get(20, TimeUnit.SECONDS), cancelled.get(20, TimeUnit.SECONDS));
    pool.shutdownNow();
    assertEquals(1, out.stream().filter("ok"::equals).count());
    String state =
        f.db.queryForObject(
            "SELECT audit_status FROM prop_sku_audit WHERE id=?", String.class, aid);
    assertTrue(Set.of("approved", "cancelled").contains(state));
    assertEquals(
        state.equals("approved") ? 1 : 0,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM prop_sku_stock WHERE sku_id=?", Integer.class, sid));
    assertEquals(
        1,
        f.db.queryForObject("SELECT version FROM prop_sku_audit WHERE id=?", Integer.class, aid));
  }

  /** 验证第三个实例失败时整批回滚且旧材料可回放。 */
  @Test
  void thirdSnFailureRollsBackWholeApprovalAndReplaySurvivesMaterialExpiry() throws Exception {
    User user = supplier();
    JsonNode c = create(user, "A_ASSET", 5);
    String sid = c.path("skuId").asString(), aid = c.path("targetId").asString();
    Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    fault.table = "prop_instance_status_log";
    fault.failOnInsert = 3;
    fault.insertAttempts.set(0);
    String key = Ids.next();
    Map<String, Object> body = view("expectedVersion", 0, "reason", "整批测试");
    try {
      // SQLSTATE 45000 is an explicit database application fault, not a lost connection.
      // Keep the already-confirmed FailureResolver classification; verify rollback and replay
      // below.
      call("POST", "/api/admin/prop-reviews/sku_audit/" + aid + "/approve", body, null, key, 500);
    } finally {
      fault.table = null;
      fault.failOnInsert = 1;
    }
    assertEquals(
        "pending",
        f.db.queryForObject(
            "SELECT audit_status FROM prop_sku_audit WHERE id=?", String.class, aid));
    for (String table : List.of("prop_sn_instance", "prop_qr_code", "prop_instance_status_log"))
      assertEquals(
          0,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM " + table + " WHERE sku_id=?", Integer.class, sid));
    call("POST", "/api/admin/prop-reviews/sku_audit/" + aid + "/approve", body, null, key, 200);
    f.store.failHead = true;
    try {
      call("POST", "/api/admin/prop-reviews/sku_audit/" + aid + "/approve", body, null, key, 200);
    } finally {
      f.store.failHead = false;
    }
    assertEquals(
        5,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM prop_sn_instance WHERE sku_id=?", Integer.class, sid));
    call("GET", "/api/admin/prop-review-actions/by-request/" + key, null, null, null, 200);
  }

  /** 同一实例的待审调整必须互斥。 */
  @Test
  void sameSnCompetingPendingAdjustmentsAreExclusive() throws Exception {
    User user = supplier();
    JsonNode c = create(user, "A_ASSET", 2);
    String sid = c.path("skuId").asString();
    approve("sku_audit", c.path("targetId").asString(), 0);
    List<String> sn =
        f.db.queryForList(
            "SELECT id FROM prop_sn_instance WHERE sku_id=? ORDER BY id", String.class, sid);
    f.db.update("UPDATE prop_sn_instance SET instance_status='idle' WHERE sku_id=?", sid);
    PropCommandService propCommandService = f.app.getBean(PropCommandService.class);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch latch = new CountDownLatch(1);
    ArrayList<Future<String>> futures = new ArrayList<Future<String>>();
    for (String type : List.of("repair", "decrease"))
      futures.add(
          pool.submit(
              () -> {
                latch.await();
                try {
                  propCommandService.snAdjustment(
                      user.id(),
                      sid,
                      Ids.next(),
                      new com.mujian.backend.prop.dto.SnAdjustmentDTO(
                          type, null, "隔离竞争", List.of(sn.get(0)), null));
                  return "ok";
                } catch (com.mujian.backend.common.api.ApiFailure e) {
                  return e.reason;
                }
              }));
    latch.countDown();
    List<String> outcomes =
        List.of(futures.get(0).get(20, TimeUnit.SECONDS), futures.get(1).get(20, TimeUnit.SECONDS));
    pool.shutdownNow();
    assertEquals(1, outcomes.stream().filter("ok"::equals).count());
    assertTrue(outcomes.contains("SN_ADJUSTMENT_PENDING"));
    Dates dates =
        f.app
            .getBean(PropAvailability.class)
            .dates(
                now(f.clock).toLocalDate().toString(),
                now(f.clock).toLocalDate().toString(),
                false);
    assertEquals(1, f.app.getBean(PropAvailability.class).quantities(List.of(sid), dates).get(sid));
  }

  /** 验证活动码可定位首次下载及后续换码。 */
  @Test
  void currentQrIdsLocateFirstDownloadAndFollowActiveIdentity() throws Exception {
    User user = supplier();
    JsonNode b1 = create(user, "B_CONSUMABLE", 8);
    String sid = b1.path("skuId").asString();
    assertTrue(
        call("GET", "/api/me/factory-props/" + sid, null, user, null, 200)
            .path("currentQrId")
            .isNull());
    approve("sku_audit", b1.path("targetId").asString(), 0);
    String qid =
        call("GET", "/api/me/factory-props/" + sid, null, user, null, 200)
            .path("currentQrId")
            .asString();
    JsonNode q = call("GET", "/api/me/prop-qr/" + qid, null, user, null, 200);
    assertNotEquals(qid, q.path("qrCode").asString());
    assertEquals("pending", q.path("imageStatus").asString());
    assertEquals(
        qid,
        call("GET", "/api/admin/prop-assets/" + sid, null, null, null, 200)
            .path("currentQrId")
            .asString());
    PropQrImages worker = f.app.getBean(PropQrImages.class);
    assertFalse(worker.generate(qid));
    assertEquals(
        qid,
        call("GET", "/api/me/factory-props/" + sid, null, user, null, 200)
            .path("currentQrId")
            .asString());
    assertEquals(
        "failed",
        call("GET", "/api/admin/prop-qr/" + qid, null, null, null, 200)
            .path("imageStatus")
            .asString());
    call("POST", "/api/me/prop-qr/" + qid + "/retry-image", null, user, null, 200);
    f.wx.delegate = pngGateway();
    try {
      assertTrue(worker.generate(qid));
    } finally {
      f.wx.delegate = null;
    }
    JsonNode ready = call("GET", "/api/me/prop-qr/" + qid, null, user, null, 200);
    assertEquals("ready", ready.path("imageStatus").asString());
    assertFalse(ready.path("image").isNull());
    assertNull(
        f.db.queryForObject("SELECT used_at FROM prop_qr_code WHERE id=?", Object.class, qid));
    JsonNode a = create(user, "A_ASSET", 2);
    String asid = a.path("skuId").asString();
    approve("sku_audit", a.path("targetId").asString(), 0);
    assertTrue(
        call("GET", "/api/me/factory-props/" + asid, null, user, null, 200)
            .path("currentQrId")
            .isNull());
    JsonNode sn =
        call("GET", "/api/me/factory-props/" + asid + "/instances", null, user, null, 200)
            .path("items")
            .get(0);
    assertNotEquals(sn.path("qrCodeId").asString(), sn.path("currentQrId").asString());
    JsonNode aq =
        call(
            "GET",
            "/api/admin/prop-qr/" + sn.path("currentQrId").asString(),
            null,
            null,
            null,
            200);
    assertEquals(sn.path("qrCodeId"), aq.path("qrCode"));
    assertEquals(
        sn.path("currentQrId"),
        call("GET", "/api/admin/prop-assets/" + asid + "/instances", null, null, null, 200)
            .path("items")
            .get(0)
            .path("currentQrId"));
    Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    fault.activeQrQueries.set(0);
    JsonNode page = call("GET", "/api/me/factory-props", null, user, null, 200);
    assertEquals(1, fault.activeQrQueries.get());
    assertEquals(2, page.path("items").size());
    fault.activeQrQueries.set(0);
    call(
        "GET",
        "/api/admin/prop-assets?factoryId="
            + page.path("items").get(0).path("factoryId").asString(),
        null,
        null,
        null,
        200);
    assertEquals(1, fault.activeQrQueries.get());
    fault.activeQrQueries.set(0);
    call("GET", "/api/admin/prop-assets/" + asid + "/instances", null, null, null, 200);
    assertEquals(1, fault.activeQrQueries.get());
    for (String old : List.of(qid, sn.path("currentQrId").asString())) {
      JsonNode before = call("GET", "/api/admin/prop-qr/" + old, null, null, null, 200);
      JsonNode reported =
          call(
              "POST",
              "/api/admin/prop-qr-exceptions",
              view(
                  "qrId",
                  old,
                  "exceptionType",
                  "unreadable_untrusted",
                  "description",
                  "标签损坏",
                  "evidence",
                  List.of()),
              null,
              Ids.next(),
              200);
      call(
          "POST",
          "/api/admin/prop-qr-exceptions/" + reported.path("targetId").asString() + "/approve",
          view("expectedVersion", 0, "reason", "停用损坏标签"),
          null,
          Ids.next(),
          200);
      if (old.equals(qid))
        assertTrue(
            call("GET", "/api/me/factory-props/" + sid, null, user, null, 200)
                .path("currentQrId")
                .isNull());
      else
        assertTrue(
            call("GET", "/api/me/factory-props/" + asid + "/instances", null, user, null, 200)
                .path("items")
                .get(0)
                .path("currentQrId")
                .isNull());
      assertEquals(
          "revoked",
          call("GET", "/api/me/prop-qr/" + old, null, user, null, 200).path("status").asString());
      JsonNode issued =
          call(
              "POST",
              "/api/admin/prop-qr/issue",
              view(
                  "targetType",
                  before.path("targetType").asString(),
                  "targetId",
                  before.path("targetId").asString(),
                  "supersedesQrId",
                  old,
                  "reason",
                  "独立发新标签"),
              null,
              Ids.next(),
              200);
      String next = issued.path("targetId").asString();
      assertNotEquals(old, next);
      if (old.equals(qid))
        assertEquals(
            next,
            call("GET", "/api/me/factory-props/" + sid, null, user, null, 200)
                .path("currentQrId")
                .asString());
      else
        assertEquals(
            next,
            call("GET", "/api/me/factory-props/" + asid + "/instances", null, user, null, 200)
                .path("items")
                .get(0)
                .path("currentQrId")
                .asString());
    }
    assertEquals(
        8,
        call("GET", "/api/me/factory-props/" + sid + "/stock", null, user, null, 200)
            .path("totalQuantity")
            .asInt());
    assertEquals(
        0,
        call("GET", "/api/me/factory-props/" + asid + "/instance-summary", null, user, null, 200)
            .path("total")
            .asInt());
    User other = supplier();
    call("GET", "/api/me/prop-qr/" + qid, null, other, null, 404);
  }

  /** 装配返回指定图片的小程序码网关。 */
  WechatGateway pngGateway() {
    return new WechatGateway() {
      /** 模拟微信登录凭据交换。 */
      public Identity exchange(String a, String b) {
        throw new UnsupportedOperationException();
      }

      /** 返回测试用小程序码结果。 */
      public byte[] miniCode(String scene, String page, String env) {
        try {
          return D2ApplicationTest.png();
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
  }

  /** 验证二维码租约恢复、迟到发布及无效图片的边界。 */
  @Test
  void qrLeaseRecoveryStalePublishAndInvalidImageAreBounded() throws Exception {
    User user = supplier();
    JsonNode c = create(user, "B_CONSUMABLE", 3);
    String sid = c.path("skuId").asString();
    approve("sku_audit", c.path("targetId").asString(), 0);
    String qid =
        call("GET", "/api/me/factory-props/" + sid, null, user, null, 200)
            .path("currentQrId")
            .asString();
    PropQrImages worker = f.app.getBean(PropQrImages.class);
    f.db.update(
        "UPDATE prop_qr_code SET"
            + " image_generation_status='generating',image_attempts=5,image_lease_until=? WHERE"
            + " id=?",
        now(f.clock).minusSeconds(1),
        qid);
    assertFalse(worker.generate(qid));
    assertEquals(
        "failed",
        call("GET", "/api/me/prop-qr/" + qid, null, user, null, 200).path("imageStatus").asString());
    call("POST", "/api/me/prop-qr/" + qid + "/retry-image", null, user, null, 200);
    f.wx.delegate =
        new WechatGateway() {
          /** 模拟微信登录凭据交换。 */
          public Identity exchange(String a, String b) {
            throw new UnsupportedOperationException();
          }

          /** 返回测试用小程序码结果。 */
          public byte[] miniCode(String s, String p, String e) {
            return "{\"errcode\":40013}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
          }
        };
    try {
      assertFalse(worker.generate(qid));
    } finally {
      f.wx.delegate = null;
    }
    assertNull(
        f.db.queryForObject(
            "SELECT qr_image_storage_key FROM prop_qr_code WHERE id=?", String.class, qid));
    call("POST", "/api/me/prop-qr/" + qid + "/retry-image", null, user, null, 200);
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    f.wx.delegate =
        new WechatGateway() {
          /** 模拟微信登录凭据交换。 */
          public Identity exchange(String a, String b) {
            throw new UnsupportedOperationException();
          }

          /** 返回测试用小程序码结果。 */
          public byte[] miniCode(String s, String p, String e) {
            entered.countDown();
            try {
              assertTrue(release.await(10, TimeUnit.SECONDS));
              return D2ApplicationTest.png();
            } catch (Exception x) {
              throw new RuntimeException(x);
            }
          }
        };
    ExecutorService pool = Executors.newSingleThreadExecutor();
    Future<Boolean> stale = pool.submit(() -> worker.generate(qid));
    assertTrue(entered.await(5, TimeUnit.SECONDS));
    assertFalse(worker.generate(qid));
    f.db.update(
        "UPDATE prop_qr_code SET image_lease_until=? WHERE id=?",
        now(f.clock).minusSeconds(1),
        qid);
    call("POST", "/api/me/prop-qr/" + qid + "/retry-image", null, user, null, 200);
    release.countDown();
    assertFalse(stale.get(10, TimeUnit.SECONDS));
    pool.shutdownNow();
    f.wx.delegate = null;
    assertEquals(
        "pending",
        call("GET", "/api/me/prop-qr/" + qid, null, user, null, 200).path("imageStatus").asString());
    f.wx.delegate = pngGateway();
    try {
      assertTrue(worker.generate(qid));
    } finally {
      f.wx.delegate = null;
    }
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM prop_qr_code WHERE sku_id=?", Integer.class, sid));
    assertEquals(
        3,
        call("GET", "/api/me/factory-props/" + sid + "/stock", null, user, null, 200)
            .path("totalQuantity")
            .asInt());
  }

  /** 核对读取、审核、敏感材料及跨厂权限。 */
  @Test
  void readReviewSensitivePermissionsAndCrossFactoryMaterials() throws Exception {
    User user = supplier();
    User other = supplier();
    JsonNode c = create(user, "B_CONSUMABLE", 6);
    String aid = c.path("targetId").asString(),
        sid = c.path("skuId").asString(),
        path = "/api/admin/prop-reviews/sku_audit/" + aid;
    JsonNode detail = call("GET", path, null, null, null, 200);
    JsonNode locator = detail.path("proposed").path("images").get(0).path("locator");
    call("POST", "/api/me/prop-materials/view", locator, other, null, 404);
    List<Map<String, Object>> grants =
        f.db.queryForList(
            "SELECT * FROM admin_role_menu WHERE admin_menu_id IN (SELECT id FROM admin_menu WHERE"
                + " permission_code LIKE 'admin:prop-review:%')");
    try {
      for (int mask = 0; mask < 8; mask++) {
        f.db.update(
            "DELETE FROM admin_role_menu WHERE admin_menu_id IN (SELECT id FROM admin_menu WHERE"
                + " permission_code LIKE 'admin:prop-review:%')");
        for (Map<String, Object> g : grants) {
          String code =
              f.db.queryForObject(
                  "SELECT permission_code FROM admin_menu WHERE id=?",
                  String.class,
                  g.get("admin_menu_id"));
          int bit = code.endsWith(":read") ? 1 : code.endsWith(":sensitive") ? 2 : 4;
          if ((mask & bit) != 0) f.restoreGrant(g);
        }
        JsonNode r = call("GET", path, null, null, null, (mask & 1) == 1 ? 200 : 403);
        if ((mask & 1) == 1) {
          assertEquals(
              (mask & 2) == 0, r.path("proposed").path("images").get(0).path("preview").isNull());
          assertEquals((mask & 4) != 0, r.path("canReject").asBoolean());
        }
        call(
            "POST",
            "/api/admin/prop-materials/view",
            locator,
            null,
            null,
            (mask & 3) == 3 ? 200 : 403);
        call(
            "POST",
            path + "/reject",
            view("expectedVersion", 99, "reason", "权限核对"),
            null,
            Ids.next(),
            (mask & 5) == 5 ? 409 : 403);
      }
    } finally {
      f.db.update(
          "DELETE FROM admin_role_menu WHERE admin_menu_id IN (SELECT id FROM admin_menu WHERE"
              + " permission_code LIKE 'admin:prop-review:%')");
      for (Map<String, Object> g : grants) f.restoreGrant(g);
    }
    approve("sku_audit", aid, 0);
    JsonNode sku = call("GET", "/api/me/factory-props/" + sid, null, user, null, 200);
    JsonNode fields = sku.path("fields");
    Map<String, Object> body =
        view(
            "expectedSkuVersion",
            1,
            "typeCode",
            fields.path("typeCode").asString(),
            "styleCodes",
            List.of("modern"),
            "transportSuggestion",
            fields.path("transportSuggestion").asString());
    String key = Ids.next();
    assertEquals(
        1,
        call("POST", "/api/me/factory-props/" + sid + "/edit-options", body, user, key, 200)
            .path("version")
            .asInt());
    call(
        "GET",
        "/api/me/prop-operations/by-request/" + key + "?operation=edit_sku_options",
        null,
        user,
        null,
        200);
    assertEquals(
        1,
        call("POST", "/api/me/factory-props/" + sid + "/edit-options", body, user, Ids.next(), 200)
            .path("version")
            .asInt());
  }

  /** 检查大批量候选和初始实例处理。 */
  @Test
  void thousandAndTenThousandCandidatesAndInitialInstances() throws Exception {
    User user = supplier();
    ArrayList<Map<String, Object>> metrics = new ArrayList<Map<String, Object>>();
    Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    for (int count : List.of(1000, 10000)) {
      JsonNode c = create(user, "A_ASSET", count);
      String sid = c.path("skuId").asString();
      fault.sqlCalls.set(0);
      fault.batchCalls.set(0);
      long start = System.nanoTime();
      approve("sku_audit", c.path("targetId").asString(), 0);
      long millis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
      assertEquals(
          count,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM prop_sn_instance WHERE sku_id=?", Integer.class, sid));
      assertEquals(
          count,
          f.db.queryForObject(
              "SELECT COUNT(*) FROM prop_instance_status_log WHERE sku_id=?", Integer.class, sid));
      assertEquals(3 * ((count + 499) / 500), fault.batchCalls.get());
      metrics.add(
          view(
              "scenario",
              "initial_sn",
              "count",
              count,
              "elapsedMs",
              millis,
              "namedJdbcCalls",
              fault.sqlCalls.get(),
              "batchCalls",
              fault.batchCalls.get()));
    }
    JsonNode c = create(user, "B_CONSUMABLE", 1);
    String base = c.path("skuId").asString();
    approve("sku_audit", c.path("targetId").asString(), 0);
    Map<String, Object> original = f.db.queryForMap("SELECT * FROM prop_sku WHERE id=?", base);
    String marker = "scale" + Ids.next();
    ArrayList<String> ids = new ArrayList<String>();
    ArrayList<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
    ArrayList<Map<String, Object>> stock = new ArrayList<Map<String, Object>>();
    ArrayList<Map<String, Object>> styles = new ArrayList<Map<String, Object>>();
    ArrayList<Map<String, Object>> images = new ArrayList<Map<String, Object>>();
    for (int n = 0; n < 10000; n++) {
      String id = Ids.next();
      ids.add(id);
      LinkedHashMap<String, Object> r = new LinkedHashMap<String, Object>(original);
      r.put("id", id);
      r.put("sku_code", "SKU-" + id);
      r.put("prop_name", marker);
      r.put("created_at", now(f.clock).minusDays(n + 1));
      rows.add(r);
      styles.add(
          view(
              "id",
              Ids.next(),
              "sku_id",
              id,
              "style_code",
              "modern",
              "sort_order",
              0,
              "created_at",
              now(f.clock)));
      images.add(
          view(
              "id",
              Ids.next(),
              "sku_id",
              id,
              "image_url",
              r.get("image_url"),
              "sort_order",
              0,
              "created_at",
              now(f.clock)));
      stock.add(
          view(
              "id",
              Ids.next(),
              "sku_id",
              id,
              "total_quantity",
              n == 9999 || n == 999 ? 1 : 0,
              "rentable_base_quantity",
              n == 9999 || n == 999 ? 1 : 0,
              "available_quantity",
              n == 9999 || n == 999 ? 1 : 0,
              "locked_quantity",
              0,
              "renting_quantity",
              0,
              "lost_quantity",
              0,
              "scrapped_quantity",
              0,
              "created_at",
              now(f.clock),
              "updated_at",
              now(f.clock)));
      if (rows.size() == 500) {
        new org.springframework.jdbc.core.simple.SimpleJdbcInsert(f.db)
            .withTableName("prop_sku")
            .usingColumns(rows.get(0).keySet().toArray(String[]::new))
            .executeBatch(rows.toArray(Map[]::new));
        new org.springframework.jdbc.core.simple.SimpleJdbcInsert(f.db)
            .withTableName("prop_sku_stock")
            .usingColumns(stock.get(0).keySet().toArray(String[]::new))
            .executeBatch(stock.toArray(Map[]::new));
        new org.springframework.jdbc.core.simple.SimpleJdbcInsert(f.db)
            .withTableName("prop_sku_style")
            .usingColumns(styles.get(0).keySet().toArray(String[]::new))
            .executeBatch(styles.toArray(Map[]::new));
        new org.springframework.jdbc.core.simple.SimpleJdbcInsert(f.db)
            .withTableName("prop_image")
            .usingColumns(images.get(0).keySet().toArray(String[]::new))
            .executeBatch(images.toArray(Map[]::new));
        rows.clear();
        stock.clear();
        styles.clear();
        images.clear();
      }
      if (n == 999 || n == 9999) {
        User demander = f.user();
        String q =
            "/api/me/props/rentable?keyword="
                + marker
                + "&startDate="
                + now(f.clock).toLocalDate()
                + "&endDate="
                + now(f.clock).toLocalDate();
        fault.sqlCalls.set(0);
        long start = System.nanoTime();
        JsonNode result = call("GET", q, null, demander, null, 200);
        long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertEquals(n + 1, result.path("total").asInt());
        assertEquals(
            ids.get(999), result.path("items").get(0).path("prop").path("skuId").asString());
        if (n == 9999)
          assertEquals(
              ids.get(9999), result.path("items").get(1).path("prop").path("skuId").asString());
        metrics.add(
            view(
                "scenario",
                "rental_candidates",
                "count",
                n + 1,
                "elapsedMs",
                elapsed,
                "namedJdbcCalls",
                fault.sqlCalls.get(),
                "returned",
                result.path("items").size()));
      }
    }
    String evidence = System.getenv("MJ_EVIDENCE_DIR");
    if (evidence != null)
      java.nio.file.Files.writeString(
          java.nio.file.Path.of(evidence, "c-scale.json"),
          f.json.writeValueAsString(
              view(
                  "environment",
                  "local MySQL 8.4 / Java 17; isolated test database; WeChat and storage fixtures",
                  "metrics",
                  metrics)));
  }

  /** 核对历史材料保留及错误写入约束。 */
  @Test
  void cHistoricalMaterialsRetainedAndConstraintsRejectBadWrites() throws Exception {
    User user = supplier();
    User other = supplier();
    PropMaterialService mats = f.app.getBean(PropMaterialService.class);
    Map<String, Object> input = fields(user);
    Map<String, Object> alien = fields(other);
    input.put("images", alien.get("images"));
    call(
        "POST",
        "/api/me/factory-props",
        view(
            "assetType",
            "B_CONSUMABLE",
            "fields",
            input,
            "initialQuantity",
            1,
            "applyRemark",
            null),
        user,
        Ids.next(),
        400);
    assertEquals("MATERIAL_REFERENCE_INVALID", f.lastReason);
    JsonNode c = create(user, "B_CONSUMABLE", 4);
    String sid = c.path("skuId").asString(), aid = c.path("targetId").asString();
    call(
        "POST",
        "/api/me/prop-reviews/sku_audit/" + aid + "/cancel",
        view("expectedVersion", 0, "reason", "保留撤回历史"),
        user,
        Ids.next(),
        200);
    String old =
        f.db.queryForObject("SELECT image_url FROM prop_sku WHERE id=?", String.class, sid);
    JsonNode next =
        call(
            "POST",
            "/api/me/factory-props/" + sid + "/resubmit",
            view(
                "previousApplicationId",
                aid,
                "expectedSkuVersion",
                0,
                "fields",
                fields(user),
                "initialQuantity",
                4,
                "applyRemark",
                null),
            user,
            Ids.next(),
            200);
    approve("sku_audit", next.path("targetId").asString(), 0);
    assertNotEquals(
        old, f.db.queryForObject("SELECT image_url FROM prop_sku WHERE id=?", String.class, sid));
    MaterialReferenceMapper refs =
        f.app.getBean(com.mujian.backend.material.mapper.MaterialReferenceMapper.class);
    assertTrue(refs.countMaterialReferencesByOwnerAndKey(user.id(), old) > 0);
    JsonNode unused =
        f.json.valueToTree(mats.upload(user.id(), "prop_image", D2ApplicationTest.png(), false));
    Receipt receipt =
        f.app
            .getBean(com.mujian.backend.material.service.VerificationMaterials.class)
            .receipt(user.id(), "prop_image", "props-material-v1", unused.path("uploadId").asString());
    Instant original = f.clock.at;
    try {
      f.clock.at = original.plus(Duration.ofHours(49));
      Report report =
          f.app
              .getBean(com.mujian.backend.material.service.VerificationMaterialCleanup.class)
              .scan(false);
      assertTrue(report.completed());
      assertTrue(f.store.objects.containsKey(old));
      assertFalse(f.store.objects.containsKey(receipt.objectKey()));
    } finally {
      f.clock.at = original;
    }
    assertThrows(
        org.springframework.dao.DataAccessException.class,
        () ->
            f.db.update(
                "INSERT INTO prop_sku_stock SELECT"
                    + " REPLACE(UUID(),'-',''),sku_id,total_quantity,rentable_base_quantity,available_quantity,locked_quantity,renting_quantity,lost_quantity,scrapped_quantity,last_counted_at,last_counted_by,created_at,updated_at"
                    + " FROM prop_sku_stock WHERE sku_id=?",
                sid));
    assertThrows(
        org.springframework.dao.DataAccessException.class,
        () -> f.db.update("UPDATE prop_sku_stock SET available_quantity=-1 WHERE sku_id=?", sid));
    assertThrows(
        org.springframework.dao.DataAccessException.class,
        () -> f.db.update("UPDATE prop_sku SET factory_id=? WHERE id=?", Ids.next(), sid));
    assertEquals(
        4,
        f.db.queryForObject(
            "SELECT total_quantity FROM prop_sku_stock WHERE sku_id=?", Integer.class, sid));
  }

  /** 检查多风格或筛选不会造成跨入口分页及库存重复。 */
  @Test
  void multiStyleOrFilterAcrossAllThreeEntrancesNeverDuplicatesStockOrPaging() throws Exception {
    User user = supplier();
    User demander = f.user();
    ArrayList<String> ids = new ArrayList<String>();
    List<List<String>> sets =
        List.of(
            List.of("retro"),
            List.of("modern"),
            List.of("retro", "modern"),
            List.of("special_asset"));
    String keyword = "筛选" + Ids.next();
    for (List<String> styles : sets) {
      Map<String, Object> fields = fields(user);
      fields.put("styleCodes", styles);
      fields.put("propName", keyword);
      JsonNode c =
          call(
              "POST",
              "/api/me/factory-props",
              view(
                  "assetType",
                  "B_CONSUMABLE",
                  "fields",
                  fields,
                  "initialQuantity",
                  7,
                  "applyRemark",
                  null),
              user,
              Ids.next(),
              200);
      ids.add(c.path("skuId").asString());
      approve("sku_audit", c.path("targetId").asString(), 0);
    }
    String date = now(f.clock).toLocalDate().toString();
    for (String root : List.of("/api/public/props", "/api/me/props", "/api/me/props/rentable")) {
      boolean pub = root.contains("public"), rent = root.endsWith("rentable");
      String prefix =
          root
              + "?pageSize=100&assetType=B_CONSUMABLE"
              + (rent ? "&startDate=" + date + "&endDate=" + date : "")
              + (pub
                  ? ""
                  : "&keyword="
                      + java.net.URLEncoder.encode(
                          keyword, java.nio.charset.StandardCharsets.UTF_8));
      for (List<String> selected :
          List.of(
              List.<String>of(),
              List.of("retro"),
              List.of("retro", "modern"),
              List.of("retro", "modern", "special_asset"))) {
        String query =
            prefix + selected.stream().map(x -> "&styleCodes=" + x).reduce("", String::concat);
        JsonNode response = call("GET", query, null, pub ? null : demander, null, 200);
        HashSet<String> seen = new HashSet<String>();
        for (JsonNode item : response.path("items")) {
          JsonNode prop = rent ? item.path("prop") : item;
          assertTrue(seen.add(prop.path("skuId").asString()));
          if (ids.contains(prop.path("skuId").asString()) && rent)
            assertEquals(7, item.path("availability").path("maxRentableQuantity").asInt());
        }
        for (int i = 0; i < ids.size(); i++)
          assertEquals(
              selected.isEmpty() || sets.get(i).stream().anyMatch(selected::contains),
              seen.contains(ids.get(i)));
        if (!pub)
          assertEquals(
              selected.isEmpty() || selected.size() == 3 ? 4 : selected.size() == 1 ? 2 : 3,
              response.path("total").asInt());
      }
      for (String bad :
          List.of(
              "styleCodes=",
              "styleCodes=modern&styleCodes=modern",
              "styleCodes=retro&styleCodes=modern&styleCodes=special_asset&styleCodes=other",
              "styleCodes=unknown_style",
              "styleCode=modern",
              "styleCodes%5B%5D=modern",
              "styleCodes=retro,modern",
              "styleCodes=%5B%22modern%22%5D",
              "page=1&page=2")) {
        if (bad.equals("styleCodes=unknown_style")) {
          call("GET", prefix + "&" + bad, null, pub ? null : demander, null, 200);
          continue;
        }
        call("GET", prefix + "&" + bad, null, pub ? null : demander, null, 400);
        assertEquals("REQUEST_INVALID", f.lastReason);
      }
    }
    f.db.update(
        "UPDATE option_dictionary SET enabled=0 WHERE option_group='prop_sku_style-style' AND"
            + " option_code='retro'");
    assertTrue(f.app.getBean(com.mujian.backend.common.dictionary.DictionaryCache.class).refresh());
    try {
      JsonNode r =
          call(
              "GET",
              "/api/me/props?keyword="
                  + java.net.URLEncoder.encode(keyword, java.nio.charset.StandardCharsets.UTF_8)
                  + "&styleCodes=retro",
              null,
              demander,
              null,
              200);
      assertEquals(2, r.path("total").asInt());
    } finally {
      f.db.update(
          "UPDATE option_dictionary SET enabled=1 WHERE option_group='prop_sku_style-style' AND"
              + " option_code='retro'");
      assertTrue(
          f.app.getBean(com.mujian.backend.common.dictionary.DictionaryCache.class).refresh());
    }
    call("GET", "/api/public/props?styleCodes=modern&keyword=test", null, null, null, 400);
    call("GET", "/api/public/props/" + ids.get(0), null, null, null, 200);
    reservation(
        ids.get(0), null, 7, now(f.clock).toLocalDate(), now(f.clock).toLocalDate(), "paid_locked");
    JsonNode unavailable =
        call(
            "GET",
            "/api/admin/prop-assets/"
                + ids.get(0)
                + "/availability?startDate="
                + date
                + "&endDate="
                + date,
            null,
            null,
            null,
            200);
    assertEquals("unavailable", unavailable.path("rentableStatus").asString());
  }

  /** 使用实际 HTTP 提交测试材料。 */
  JsonNode uploadHttp(
      D2ApplicationTest.User user, String purpose, boolean admin, byte[] bytes, int expected)
      throws Exception {
    String path = admin ? "/api/admin/prop-materials" : "/api/me/prop-materials",
        boundary = "C" + Ids.next();
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
    Builder req =
        java.net.http.HttpRequest.newBuilder(java.net.URI.create(f.base + path))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary);
    if (admin) req.header("Cookie", f.adminCookie).header("X-CSRF-Token", f.csrf);
    else req.header("Authorization", "Bearer " + user.token());
    return f.received(
        "POST",
        path,
        f.http.send(
            req.POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
                .build(),
            java.net.http.HttpResponse.BodyHandlers.ofString()),
        expected);
  }

  /** 验证读取、上传、处置及异常拒绝入口。 */
  @Test
  void remainingReadUploadResolveAndExceptionRejectionEntrances() throws Exception {
    User user = supplier();
    User demander = f.user();
    call("GET", "/api/public/props/options", null, null, null, 200);
    JsonNode image = uploadHttp(user, "prop_image", false, D2ApplicationTest.png(), 200);
    uploadHttp(user, "business_license", false, D2ApplicationTest.png(), 400);
    uploadHttp(user, "prop_image", false, new byte[] {1, 2, 3}, 400);
    JsonNode evidence =
        uploadHttp(null, "qr_exception_evidence", true, D2ApplicationTest.png(), 200);
    uploadHttp(null, "prop_image", true, D2ApplicationTest.png(), 400);
    Map<String, Object> fields = fields(user);
    fields.put(
        "images", List.of(view("kind", "upload", "uploadId", image.path("uploadId").asString())));
    JsonNode c =
        call(
            "POST",
            "/api/me/factory-props",
            view(
                "assetType",
                "B_CONSUMABLE",
                "fields",
                fields,
                "initialQuantity",
                10,
                "applyRemark",
                null),
            user,
            Ids.next(),
            200);
    String sid = c.path("skuId").asString();
    approve("sku_audit", c.path("targetId").asString(), 0);
    call("GET", "/api/me/props/" + sid, null, demander, null, 200);
    call("GET", "/api/admin/prop-assets/" + sid + "/stock", null, null, null, 200);
    call("GET", "/api/admin/prop-assets/" + sid + "/movements", null, null, null, 200);
    JsonNode owned = call("GET", "/api/me/factory-props/" + sid, null, user, null, 200);
    String qid = owned.path("currentQrId").asString();
    JsonNode qr = call("GET", "/api/me/prop-qr/" + qid, null, user, null, 200);
    call(
        "POST",
        "/api/me/prop-qr/resolve",
        view("qrCode", qr.path("qrCode").asString()),
        user,
        null,
        200);
    call("POST", "/api/admin/prop-qr/" + qid + "/retry-image", null, null, null, 200);
    JsonNode report =
        call(
            "POST",
            "/api/admin/prop-qr-exceptions",
            view(
                "qrId",
                qid,
                "exceptionType",
                "other",
                "description",
                "隔离证据回收验证",
                "evidence",
                List.of(view("kind", "upload", "uploadId", evidence.path("uploadId").asString()))),
            null,
            Ids.next(),
            200);
    String rid = report.path("targetId").asString();
    call(
        "POST",
        "/api/admin/prop-qr-exceptions/" + rid + "/reject",
        view("expectedVersion", 0, "reason", "核对无需停用"),
        null,
        Ids.next(),
        200);
    assertEquals(
        "bound",
        call("GET", "/api/me/prop-qr/" + qid, null, user, null, 200).path("status").asString());
    JsonNode a = create(user, "A_ASSET", 1);
    String asid = a.path("skuId").asString();
    approve("sku_audit", a.path("targetId").asString(), 0);
    call("GET", "/api/admin/prop-assets/" + asid + "/instance-summary", null, null, null, 200);
    for (String type : List.of("increase", "decrease", "abnormal_decrease", "abnormal_decrease")) {
      String abnormal =
          type.equals("abnormal_decrease")
              ? (f.db.queryForObject(
                          "SELECT lost_quantity FROM prop_sku_stock WHERE sku_id=?",
                          Integer.class,
                          sid)
                      == 0
                  ? "lost"
                  : "scrapped")
              : null;
      Map<String, Object> body = view("adjustmentType", type, "quantity", 2, "reason", "台账链路");
      if (abnormal != null) body.put("abnormalResult", abnormal);
      JsonNode adjust =
          call(
              "POST",
              "/api/me/factory-props/" + sid + "/stock-adjustments",
              body,
              user,
              Ids.next(),
              200);
      approve("stock_adjustment", adjust.path("targetId").asString(), 0);
    }
    JsonNode stock = call("GET", "/api/me/factory-props/" + sid + "/stock", null, user, null, 200);
    assertEquals(6, stock.path("totalQuantity").asInt());
    assertEquals(2, stock.path("lostQuantity").asInt());
    assertEquals(2, stock.path("scrappedQuantity").asInt());
  }

  /** 验证提交与材料清理共用持有人锁。 */
  @Test
  void cSubmissionAndCleanupShareOwnerLock() throws Exception {
    User user = supplier();
    Map<String, Object> input =
        view(
            "assetType",
            "B_CONSUMABLE",
            "fields",
            fields(user),
            "initialQuantity",
            3,
            "applyRemark",
            null);
    Fault fault = f.app.getBean(D2ApplicationTest.Fault.class);
    fault.blockTable = "prop_sku_audit";
    fault.entered = new CountDownLatch(1);
    fault.release = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    Instant original = f.clock.at;
    try {
      Future<JsonNode> submit =
          pool.submit(() -> call("POST", "/api/me/factory-props", input, user, Ids.next(), 200));
      assertTrue(fault.entered.await(5, TimeUnit.SECONDS));
      f.clock.at = original.plus(Duration.ofHours(49));
      Future<Report> cleanup =
          pool.submit(
              () ->
                  f.app
                      .getBean(
                          com.mujian.backend.material.service.VerificationMaterialCleanup.class)
                      .scan(false));
      Thread.sleep(150);
      assertFalse(cleanup.isDone());
      fault.release.countDown();
      JsonNode result = submit.get(15, TimeUnit.SECONDS);
      assertTrue(cleanup.get(20, TimeUnit.SECONDS).completed());
      String key =
          f.db.queryForObject(
              "SELECT image_url FROM prop_sku WHERE id=?",
              String.class,
              result.path("skuId").asString());
      assertNotNull(f.store.head(key));
      assertTrue(
          f.app
                  .getBean(com.mujian.backend.material.mapper.MaterialReferenceMapper.class)
                  .countMaterialReferencesByOwnerAndKey(user.id(), key)
              > 0);
    } finally {
      fault.release.countDown();
      fault.blockTable = null;
      f.clock.at = original;
      pool.shutdownNow();
    }
  }

  /** 验证待审道具对工厂经营入口的限制。 */
  @Test
  void pendingSkuBlocksEveryFactoryOperatingConsumer() throws Exception {
    User user = supplier();
    for (String type : List.of("A_ASSET", "B_CONSUMABLE")) {
      JsonNode c = create(user, type, 3);
      String sid = c.path("skuId").asString();
      approve("sku_audit", c.path("targetId").asString(), 0);
      JsonNode old = call("GET", "/api/me/factory-props/" + sid, null, user, null, 200);
      call(
          "POST",
          "/api/me/factory-props/" + sid + "/status-applications",
          view("expectedSkuVersion", 1, "actionType", "down", "applyRemark", "待审守卫"),
          user,
          Ids.next(),
          200);
      JsonNode sku = call("GET", "/api/me/factory-props/" + sid, null, user, null, 200);
      assertFalse(sku.path("canEditOptions").asBoolean());
      assertFalse(sku.path("canApplyDown").asBoolean());
      call("GET", "/api/public/props/" + sid, null, null, null, 404);
      call(
          "POST",
          "/api/me/factory-props/" + sid + "/edit-options",
          view(
              "expectedSkuVersion",
              1,
              "typeCode",
              old.path("fields").path("typeCode").asString(),
              "styleCodes",
              List.of("modern"),
              "transportSuggestion",
              "建议"),
          user,
          Ids.next(),
          409);
      assertEquals("SKU_AUDITING", f.lastReason);
      if (type.equals("B_CONSUMABLE")) {
        call(
            "POST",
            "/api/me/factory-props/" + sid + "/stock-adjustments",
            view("adjustmentType", "increase", "quantity", 1, "reason", "增补"),
            user,
            Ids.next(),
            409);
        assertEquals("SKU_AUDITING", f.lastReason);
      } else {
        JsonNode sn =
            call("GET", "/api/me/factory-props/" + sid + "/instances", null, user, null, 200)
                .path("items")
                .get(0);
        String id = sn.path("id").asString();
        assertFalse(sn.path("canConfirmLabel").asBoolean());
        assertFalse(sn.path("canSelectForRepair").asBoolean());
        JsonNode qr =
            call("GET", "/api/me/prop-qr/" + sn.path("currentQrId").asString(), null, user, null, 200);
        assertFalse(qr.path("canConfirmLabel").asBoolean());
        call(
            "POST",
            "/api/me/prop-instances/" + id + "/label-confirm",
            view("expectedVersion", 0, "qrCode", qr.path("qrCode").asString()),
            user,
            Ids.next(),
            409);
        assertEquals("SKU_AUDITING", f.lastReason);
        call(
            "POST",
            "/api/me/prop-instances/" + id + "/repair-complete",
            view("expectedVersion", 0, "reason", "完成"),
            user,
            Ids.next(),
            409);
        assertEquals("SKU_AUDITING", f.lastReason);
        call(
            "POST",
            "/api/me/factory-props/" + sid + "/sn-adjustments",
            view("adjustmentType", "increase", "quantity", 1, "reason", "增补"),
            user,
            Ids.next(),
            409);
        assertEquals("SKU_AUDITING", f.lastReason);
      }
    }
  }
}
