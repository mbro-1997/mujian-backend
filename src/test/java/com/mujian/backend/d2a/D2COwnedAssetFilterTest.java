package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.d2a.D2ApplicationTest.User;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

/** 用本次隔离夹具创建的双工厂库存验证仓库筛选。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class D2COwnedAssetFilterTest {
  final D2CApplicationTest fixture = new D2CApplicationTest();
  final D2ApplicationTest f = fixture.f;
  final List<D2ApplicationTest.User> sessions = new ArrayList<>();
  int requests;

  /** 初始化本组隔离依赖。 */
  @BeforeAll
  void start() throws Exception {
    fixture.start();
  }

  /** 释放本组创建的资源。 */
  @AfterAll
  void stop() {
    try {
      for (User user : sessions) {
        f.sessions.revoke(user.token());
        assertNull(f.sessions.load(user.token()));
      }
      if (f.adminCookie != null) f.sessions.revoke(f.adminCookie.substring("mj_admin_dev=".length()));
    } finally {
      fixture.stop();
    }
  }

  /** 创建隔离工厂并登记本次会话以便撤销。 */
  User supplier() throws Exception {
    User user = fixture.supplier();
    sessions.add(user);
    return user;
  }

  /** 读取指定测试资源。 */
  JsonNode get(D2ApplicationTest.User user, String query, int status) throws Exception {
    requests++;
    return f.call("GET", "/api/me/factory-props" + query, null, user, null, status);
  }

  /** 用独立 SQL 对照每页的总数、顺序、归属和越界空页。 */
  void check(D2ApplicationTest.User user, String factory, String query, String predicate,
      Object... values) throws Exception {
    ArrayList<Object> args = new ArrayList<Object>();
    args.add(factory);
    args.addAll(Arrays.asList(values));
    List<String> ids = f.db.queryForList("SELECT s.id FROM prop_sku s WHERE s.factory_id=? " + predicate
        + " ORDER BY s.created_at DESC,s.id DESC", String.class, args.toArray());
    HashSet<String> seen = new HashSet<String>();
    LinkedHashSet<Integer> pages = new LinkedHashSet<Integer>(List.of(1, 2, ids.size() + 1));
    for (int page : pages) {
      JsonNode result = get(user, "?pageSize=1&page=" + page + query, 200);
      assertEquals(Integer.toString(ids.size()), result.path("total").asString());
      assertEquals(page, result.path("page").asInt());
      JsonNode rows = result.path("items");
      assertEquals(page <= ids.size() ? 1 : 0, rows.size());
      if (!rows.isEmpty()) {
        assertEquals(ids.get(page - 1), rows.get(0).path("id").asString());
        assertEquals(factory, rows.get(0).path("factoryId").asString());
        assertTrue(seen.add(rows.get(0).path("id").asString()));
      }
    }
  }

  /** 核对资产类型在总数及分页之前过滤。 */
  @Test
  void optionalAssetTypeFiltersBeforeTotalAndPagination() throws Exception {
    String database = f.db.queryForObject("SELECT DATABASE()", String.class);
    assertTrue(database.startsWith("darkmujian_d1_test_"));
    assertEquals("test", f.app.getEnvironment().getProperty("mj.environment"));
    User user = supplier();
    User other = supplier();
    JsonNode asset = fixture.create(user, "A_ASSET", 1);
    fixture.approve("sku_audit", asset.get("targetId").asString(), 0);
    String assetId = asset.get("skuId").asString();
    String consumableId = fixture.create(user, "B_CONSUMABLE", 2).get("skuId").asString();
    String otherSkuId = fixture.create(other, "A_ASSET", 1).get("skuId").asString();
    String factory = f.db.queryForObject("SELECT id FROM factory_profile WHERE owner_user_id=?", String.class, user.id());
    String otherFactory = f.db.queryForObject("SELECT id FROM factory_profile WHERE owner_user_id=?", String.class, other.id());
    assertNotEquals(factory, otherFactory);
    List<Map<String, Object>> before = f.db.queryForList("SELECT * FROM prop_sku WHERE factory_id=? ORDER BY id", factory);
    assertEquals(2, before.size());
    check(user, factory, "", ""); // omitted parameter retains the existing all-types behavior
    for (String type : List.of("A_ASSET", "B_CONSUMABLE")) {
      check(user, factory, "&assetType=" + type, "AND s.asset_type=?", type);
      for (String status : List.of("online", "offline")) {
        check(user, factory, "&assetType=" + type + "&skuStatus=" + status,
            "AND s.asset_type=? AND s.sku_status=?", type, status);
      }
      for (boolean pending : List.of(true, false)) {
        String exists = "EXISTS(SELECT 1 FROM prop_sku_audit a WHERE a.sku_id=s.id AND a.audit_status='pending')";
        check(user, factory, "&assetType=" + type + "&auditing=" + pending,
            "AND s.asset_type=? AND " + (pending ? "" : "NOT ") + exists, type);
      }
      String skuCode = f.db.queryForObject(
          "SELECT sku_code FROM prop_sku WHERE id=? AND factory_id=? AND asset_type=?",
          String.class, type.equals("A_ASSET") ? assetId : consumableId, factory, type);
      check(user, factory, "&assetType=" + type + "&keyword=" + skuCode,
          "AND s.asset_type=? AND (LOCATE(?,s.prop_name)>0 OR LOCATE(?,CONVERT(s.sku_code USING utf8mb4))>0)",
          type, skuCode, skuCode);
      String opposite = type.equals("A_ASSET") ? "B_CONSUMABLE" : "A_ASSET";
      check(user, factory, "&assetType=" + opposite + "&keyword=" + skuCode,
          "AND s.asset_type=? AND (LOCATE(?,s.prop_name)>0 OR LOCATE(?,CONVERT(s.sku_code USING utf8mb4))>0)",
          opposite, skuCode, skuCode);
    }
    for (String query : List.of("assetType=", "assetType=all", "assetType=A", "assetType=a_asset",
        "assetType=%20A_ASSET", "assetType=A_ASSET%20", "assetType=A_ASSET,B_CONSUMABLE",
        "assetType=%5B%22A_ASSET%22%5D", "assetType%5B%5D=A_ASSET",
        "assetType=A_ASSET&assetType=B_CONSUMABLE", "assetType=A_ASSET&assetType=A_ASSET")) {
      get(user, "?" + query, 400);
      assertEquals("REQUEST_INVALID", f.lastReason);
    }
    // 注入外厂 ID 会被拒绝，双方登录后只能浏览各自的 SKU。
    get(user, "?assetType=A_ASSET&factoryId=" + otherFactory, 400);
    assertEquals("REQUEST_INVALID", f.lastReason);
    assertEquals(otherSkuId, get(other, "?assetType=A_ASSET", 200).path("items").get(0).get("id").asString());
    get(user, "/" + otherSkuId, 404);
    get(other, "/" + assetId, 404);
    for (String type : List.of("A_ASSET", "B_CONSUMABLE"))
      check(other, otherFactory, "&assetType=" + type, "AND s.asset_type=?", type);
    assertEquals(before, f.db.queryForList("SELECT * FROM prop_sku WHERE factory_id=? ORDER BY id", factory));
    String evidence = System.getenv("MJ_EVIDENCE_DIR");
    if (evidence != null) Files.writeString(Path.of(evidence, "nav-owned-asset-summary.json"),
        f.json.writeValueAsString(Map.of("database", database, "factory", factory, "actor", user.id(),
            "requests", requests, "fixtureFactories", 2, "fixtureSkus", 3,
            "skuRowsUnchanged", true, "httpBase", f.base,
            "boundary", "real HTTP/MySQL/Redis; existing test-only material adapter; no frontend/native/phone")) + "\n");
  }

  /** 核对待贴标存在性、本厂范围以及与其他条件组合后的分页。 */
  @Test
  void pendingLabelFiltersBeforeTotalAndPagination() throws Exception {
    User user = supplier();
    User other = supplier();
    JsonNode first = fixture.create(user, "A_ASSET", 2);
    JsonNode second = fixture.create(user, "A_ASSET", 1);
    JsonNode hasOnlyIdle = fixture.create(user, "A_ASSET", 1);
    JsonNode consumable = fixture.create(user, "B_CONSUMABLE", 2);
    JsonNode foreign = fixture.create(other, "A_ASSET", 1);
    for (JsonNode sku : List.of(first, second, hasOnlyIdle, consumable, foreign)) {
      fixture.approve("sku_audit", sku.get("targetId").asString(), 0);
    }
    String firstId = first.get("skuId").asString();
    String secondId = second.get("skuId").asString();
    assertEquals(1, f.db.update("UPDATE prop_sn_instance SET instance_status='idle' WHERE sku_id=?",
        hasOnlyIdle.get("skuId").asString()));
    String factory = f.db.queryForObject(
        "SELECT id FROM factory_profile WHERE owner_user_id=?", String.class, user.id());
    check(user, factory, "&hasPendingLabel=true", "AND s.id IN (?,?)", firstId, secondId);
    check(user, factory, "&hasPendingLabel=true&assetType=A_ASSET&skuStatus=online",
        "AND s.id IN (?,?)", firstId, secondId);
    String skuCode = f.db.queryForObject("SELECT sku_code FROM prop_sku WHERE id=?", String.class, firstId);
    check(user, factory, "&hasPendingLabel=true&skuStatus=online&keyword=" + skuCode,
        "AND s.id=?", firstId);
    check(user, factory, "&hasPendingLabel=true&assetType=B_CONSUMABLE", "AND 1=0");
    check(user, factory, "&hasPendingLabel=false", "");
    check(user, factory, "", "");
    assertEquals("4", get(user, "", 200).path("total").asString());
    assertFalse(get(user, "?hasPendingLabel=true", 200).path("items").get(0)
        .path("inventorySummary").has("pendingLabel"));
    get(user, "?hasPendingLabel=1", 400);
  }
}
