package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import com.mujian.backend.prop.service.PropQrImages;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class D2CR01Test {
  final D2CApplicationTest c = new D2CApplicationTest();
  final D2ApplicationTest f = c.f;

  /** 初始化本组隔离依赖。 */
  @BeforeAll
  void start() throws Exception {
    c.start();
  }

  /** 释放本组创建的资源。 */
  @AfterAll
  void stop() {
    c.stop();
  }

  /** 读取指定测试资源。 */
  JsonNode get(String path, D2ApplicationTest.User user) throws Exception {
    return c.call("GET", path, null, user, null, 200);
  }

  /** 以测试身份提交请求。 */
  JsonNode post(String path, Object body, D2ApplicationTest.User user) throws Exception {
    return c.call("POST", path, body, user, Ids.next(), 200);
  }

  /** 检查结构化错误响应。 */
  void error(String path, Object body, D2ApplicationTest.User user, int status, String reason)
      throws Exception {
    c.call("POST", path, body, user, Ids.next(), status);
    assertEquals(reason, f.lastReason);
  }

  /** 构造案件决定用于后续验证。 */
  Object decision(int v) {
    return view("expectedVersion", v, "reason", "R01定向核验");
  }

  /** 提交本场景的问题记录。 */
  String report(String qr) throws Exception {
    return post(
            "/api/admin/prop-qr-exceptions",
            view(
                "qrId",
                qr,
                "exceptionType",
                "suspected_leak",
                "description",
                "R01隔离风险停用",
                "evidence",
                List.of()),
            null)
        .path("targetId")
        .asString();
  }

  /** 组装前端提交的业务字段。 */
  JsonNode form(Object input) throws Exception {
    Process process =
        new ProcessBuilder(
                System.getenv().getOrDefault("MJ_NODE", "node"),
                "../mujian-frontend/tests/support/prop-form-runner.cjs")
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start();
    try (OutputStream out = process.getOutputStream()) {
      out.write(f.json.writeValueAsBytes(input));
    }
    byte[] bytes = process.getInputStream().readAllBytes();
    assertTrue(process.waitFor(20, TimeUnit.SECONDS));
    assertEquals(0, process.exitValue());
    return f.json.readTree(bytes);
  }

  /** 验证页面投影支撑重新提交和创建请求。 */
  @Test
  void pageProjectedResubmitAndCreateReachRealHttp() throws Exception {
    User user = c.supplier();
    JsonNode made = c.create(user, "B_CONSUMABLE", 3);
    String sid = made.path("skuId").asString(), aid = made.path("targetId").asString();
    post("/api/admin/prop-reviews/sku_audit/" + aid + "/reject", decision(0), null);
    JsonNode options = get("/api/me/factory-props/options", user);
    JsonNode sku = get("/api/me/factory-props/" + sid, user);
    JsonNode review = get("/api/me/prop-reviews/sku_audit/" + aid, user);
    JsonNode upload = c.uploadHttp(user, "prop_image", false, D2ApplicationTest.png(), 200);
    JsonNode sent =
        form(
            view(
                "options",
                options,
                "sku",
                sku,
                "review",
                review,
                "uploadId",
                upload.path("uploadId").asString(),
                "addImage",
                true));
    JsonNode fields = sent.path("body").path("fields");
    for (String key : List.of("typeLabel", "styleLabels", "fireResistantLabel"))
      assertFalse(fields.has(key));
    assertEquals(2, fields.path("images").size());
    post("/api/" + sent.path("path").asString(), sent.path("body"), user);
    assertEquals("resubmit_sku", sent.path("operation").asString());
    Map<String, Object> fresh = c.fields(user);
    fresh.put("typeLabel", "只读展示");
    fresh.put("futureDisplayField", "不得写入");
    JsonNode created = form(view("options", options, "fields", fresh, "assetType", "A_ASSET"));
    post("/api/" + created.path("path").asString(), created.path("body"), user);
    assertEquals("create_sku", created.path("operation").asString());
    String evidence = System.getenv("MJ_EVIDENCE_DIR");
    if (evidence != null)
      Files.writeString(
          Path.of(evidence, "c-r01-page-http-requests.json"),
          f.json.writeValueAsString(
              view(
                  "boundary",
                  "真实页面函数与真实HTTP读写；wx及上传传输桩，不代表原生页面验收",
                  "requests",
                  List.of(sent, created))));
  }

  /** 检查风险撤销和正常经营操作的不同门禁。 */
  @Test
  void riskRevocationIgnoresOperatingStateButNormalOperationsKeepGates() throws Exception {
    for (String state : List.of("active", "suspended", "disabled", "owner_disabled")) {
      User user = c.supplier();
      JsonNode made = c.create(user, "B_CONSUMABLE", 5);
      String sid = made.path("skuId").asString();
      c.approve("sku_audit", made.path("targetId").asString(), 0);
      JsonNode sku = get("/api/me/factory-props/" + sid, user);
      String factory = sku.path("factoryId").asString(), qid = sku.path("currentQrId").asString();
      String stock =
          post(
                  "/api/me/factory-props/" + sid + "/stock-adjustments",
                  view("adjustmentType", "increase", "quantity", 1, "reason", "门禁核验"),
                  user)
              .path("targetId")
              .asString();
      String rid = report(qid), rp = "/api/admin/prop-qr-exceptions/" + rid;
      if (state.equals("owner_disabled"))
        f.db.update("UPDATE sys_user SET user_status='disabled' WHERE id=?", user.id());
      else f.db.update("UPDATE factory_profile SET factory_status=? WHERE id=?", state, factory);
      try {
        assertTrue(get(rp, null).path("canApprove").asBoolean(), state);
        if (state.equals("disabled")) {
          List<Map<String, Object>> grants =
              f.db.queryForList(
                  "SELECT * FROM admin_role_menu WHERE admin_menu_id IN (SELECT id FROM admin_menu"
                      + " WHERE permission_code='admin:prop-qr:review')");
          assertFalse(grants.isEmpty());
          f.db.update(
              "DELETE FROM admin_role_menu WHERE admin_menu_id IN (SELECT id FROM admin_menu WHERE"
                  + " permission_code='admin:prop-qr:review')");
          try {
            assertFalse(get(rp, null).path("canApprove").asBoolean());
            error(rp + "/approve", decision(0), null, 403, "PERMISSION_DENIED");
          } finally {
            for (Map<String, Object> grant : grants) f.restoreGrant(grant);
          }
        }
        error(rp + "/approve", decision(99), null, 409, "VERSION_CONFLICT");
        post(rp + "/approve", decision(0), null);
        assertEquals(
            "revoked",
            f.db.queryForObject("SELECT status FROM prop_qr_code WHERE id=?", String.class, qid));
        assertFalse(get(rp, null).path("canApprove").asBoolean());
        Map<String, Object> issue =
            view("targetType", "SKU", "targetId", sid, "supersedesQrId", qid, "reason", "门禁核验");
        String sp = "/api/admin/prop-reviews/stock_adjustment/" + stock;
        if (state.equals("active")) {
          post("/api/admin/prop-qr/issue", issue, null);
          post(sp + "/approve", decision(0), null);
        } else {
          String reason =
              state.equals("owner_disabled")
                  ? "ACCOUNT_ACTION_RESTRICTED"
                  : "FACTORY_NOT_OPERATING";
          error("/api/admin/prop-qr/issue", issue, null, 403, reason);
          JsonNode detail = get(sp, null);
          assertFalse(detail.path("canApprove").asBoolean());
          assertEquals(reason, detail.path("approvalBlockReason").asString());
          error(sp + "/approve", decision(0), null, 403, reason);
        }
      } finally {
        f.db.update("UPDATE factory_profile SET factory_status='active' WHERE id=?", factory);
        f.db.update("UPDATE sys_user SET user_status='active' WHERE id=?", user.id());
      }
    }
  }

  /** 通过实际请求验证新增错误分支无副作用。 */
  @Test
  void newlyDeclaredErrorBranchesUseRealHttpAndLeaveEffectsUnchanged() throws Exception {
    User user = c.supplier();
    Map<String, Object> missing = c.fields(user);
    missing.put(
        "images",
        List.of(
            view(
                "kind",
                "existing",
                "locator",
                view(
                    "sourceType",
                    "sku",
                    "sourceId",
                    Ids.next(),
                    "section",
                    "current",
                    "index",
                    0))));
    error(
        "/api/me/factory-props",
        view(
            "assetType",
            "B_CONSUMABLE",
            "fields",
            missing,
            "initialQuantity",
            2,
            "applyRemark",
            null),
        user,
        404,
        "RESOURCE_NOT_FOUND");
    JsonNode pending = c.create(user, "B_CONSUMABLE", 2);
    f.store.failHead = true;
    try {
      error(
          "/api/admin/prop-reviews/sku_audit/" + pending.path("targetId").asString() + "/approve",
          decision(0),
          null,
          503,
          "OBJECT_STORAGE_UNAVAILABLE");
    } finally {
      f.store.failHead = false;
    }
    assertEquals(
        "pending",
        f.db.queryForObject(
            "SELECT audit_status FROM prop_sku_audit WHERE id=?",
            String.class,
            pending.path("targetId").asString()));
    JsonNode made = c.create(user, "A_ASSET", 1);
    String sid = made.path("skuId").asString();
    c.approve("sku_audit", made.path("targetId").asString(), 0);
    JsonNode sn = get("/api/me/factory-props/" + sid + "/instances", user).path("items").get(0);
    String snid = sn.path("id").asString(), qid = sn.path("currentQrId").asString();
    JsonNode qr = get("/api/me/prop-qr/" + qid, user);
    Map<String, Object> label = view("expectedVersion", 0, "qrCode", qr.path("qrCode").asString());
    Map<String, Object> issue = view("targetType", "SN", "targetId", snid, "supersedesQrId", qid, "reason", "定向补码");
    f.db.update("UPDATE prop_sku SET sku_status='disabled' WHERE id=?", sid);
    try {
      error(
          "/api/me/prop-instances/" + snid + "/repair-complete",
          decision(0),
          user,
          409,
          "SKU_STATE_CONFLICT");
      error(
          "/api/me/prop-instances/" + snid + "/label-confirm", label, user, 409, "SKU_STATE_CONFLICT");
      error("/api/me/prop-qr/" + qid + "/retry-image", null, user, 409, "SKU_STATE_CONFLICT");
    } finally {
      f.db.update("UPDATE prop_sku SET sku_status='online' WHERE id=?", sid);
    }
    JsonNode down =
        post(
            "/api/me/factory-props/" + sid + "/status-applications",
            view("expectedSkuVersion", 1, "actionType", "down", "applyRemark", "门禁"),
            user);
    error("/api/me/prop-qr/" + qid + "/retry-image", null, user, 409, "SKU_AUDITING");
    error("/api/admin/prop-qr/issue", issue, null, 409, "SKU_AUDITING");
    post(
        "/api/me/prop-reviews/sku_audit/" + down.path("targetId").asString() + "/cancel",
        decision(0),
        user);
    f.wx.delegate = c.pngGateway();
    try {
      assertTrue(f.app.getBean(PropQrImages.class).generate(qid));
    } finally {
      f.wx.delegate = null;
    }
    // Controlled historical mixed-state fixture: normal commands do not create this combination.
    f.db.update("UPDATE prop_sn_instance SET instance_status='idle' WHERE id=?", snid);
    JsonNode adjust =
        post(
            "/api/me/factory-props/" + sid + "/sn-adjustments",
            view("adjustmentType", "repair", "snIds", List.of(snid), "reason", "历史状态守卫"),
            user);
    f.db.update("UPDATE prop_sn_instance SET instance_status='pending_label' WHERE id=?", snid);
    error(
        "/api/me/prop-instances/" + snid + "/label-confirm",
        label,
        user,
        409,
        "SN_ADJUSTMENT_PENDING");
    post(
        "/api/me/prop-reviews/sn_adjustment/" + adjust.path("targetId").asString() + "/cancel",
        decision(0),
        user);
    String rid = report(qid);
    f.db.update("UPDATE prop_qr_code SET status='revoked' WHERE id=?", qid);
    assertFalse(get("/api/admin/prop-qr-exceptions/" + rid, null).path("canApprove").asBoolean());
    error(
        "/api/admin/prop-qr-exceptions/" + rid + "/approve", decision(0), null, 409, "QR_REVOKED");
    f.db.update("UPDATE prop_sn_instance SET instance_status='lost' WHERE id=?", snid);
    try {
      error("/api/admin/prop-qr/issue", issue, null, 409, "SN_STATE_CONFLICT");
    } finally {
      f.db.update("UPDATE prop_sn_instance SET instance_status='pending_label' WHERE id=?", snid);
    }
    assertEquals(
        1,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM prop_qr_code WHERE sku_id=?", Integer.class, sid));
  }
}
