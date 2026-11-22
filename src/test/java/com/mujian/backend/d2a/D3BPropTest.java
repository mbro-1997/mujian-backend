package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.security.D2Contract;
import com.mujian.backend.common.support.Ids;
import com.mujian.backend.d2a.D2ApplicationTest.User;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

/** 本批包装审核增量使用真实隔离HTTP和数据库；不重跑无关D2场景。 */
class D3BPropTest {
  final D2CApplicationTest fixture = new D2CApplicationTest();
  final D2ApplicationTest f = fixture.f;

  /** 初始化本组隔离依赖。 */
  @BeforeEach
  void start() throws Exception {
    f.start(0, "--mj.prop.qr-worker-enabled=false", "--mj.trading.recovery-enabled=false");
  }

  /** 释放本组创建的资源。 */
  @AfterEach
  void stop() {
    try {
      D3BFixtureCleanup.remove(fixture.f);
    } finally {
      fixture.stop();
    }
  }

  /** 验证已审核更新回放保持库存。 */
  @Test
  void auditedUpdateReplaysAndKeepsStock() throws Exception {
    User supplier = fixture.supplier();
    JsonNode created = fixture.create(supplier, "B_CONSUMABLE", 7);
    String skuId = created.path("skuId").asString();
    fixture.approve("sku_audit", created.path("targetId").asString(), 0);
    JsonNode before = f.call("GET", "/api/me/factory-props/" + skuId, null, supplier, null, 200);
    Map<String, Object> fields = fixture.fields(supplier);
    fields.remove("typeCode");
    fields.remove("styleCodes");
    fields.remove("transportSuggestion");
    fields.put("packagingFactorBps", 13500);
    Map<String, Object> body =
        view(
            "expectedSkuVersion",
            before.path("version").asInt(),
            "previousApplicationId",
            null,
            "fields",
            fields,
            "applyRemark",
            "隔离验证包装资料更新");
    String key = Ids.next(), path = "/api/me/factory-props/" + skuId + "/change-applications";
    JsonNode applied = f.call("POST", path, body, supplier, key, 200);
    assertEquals("update_sku", applied.path("operation").asString());
    D2Contract contract = f.app.getBean(D2Contract.class);
    contract.validate(
        contract
            .route(path, "POST")
            .operation()
            .path("responses")
            .path("200")
            .path("content")
            .path("application/json")
            .path("schema"),
        f.json.valueToTree(
            view("code", "OK", "message", "成功", "data", applied, "traceId", Ids.next())));
    assertEquals(
        12000,
        f.call("GET", "/api/me/factory-props/" + skuId, null, supplier, null, 200)
            .path("fields")
            .path("packagingFactorBps")
            .asInt());
    assertEquals(
        applied.path("targetId"), f.call("POST", path, body, supplier, key, 200).path("targetId"));
    f.call("POST", path, body, supplier, Ids.next(), 409);
    assertEquals("SKU_REVIEW_PENDING", f.lastReason);
    fields.put("packagingFactorBps", 14000);
    f.call("POST", path, body, supplier, key, 409);
    fields.put("packagingFactorBps", 13500);
    fixture.approve("sku_audit", applied.path("targetId").asString(), 0);
    JsonNode after = f.call("GET", "/api/me/factory-props/" + skuId, null, supplier, null, 200);
    assertEquals(13500, after.path("fields").path("packagingFactorBps").asInt());
    assertEquals(before.path("skuStatus"), after.path("skuStatus"));
    assertEquals(before.path("inventorySummary"), after.path("inventorySummary"));
    assertEquals(
        applied.path("targetId"),
        f.call(
                "GET",
                "/api/me/prop-operations/by-request/" + key + "?operation=update_sku",
                null,
                supplier,
                null,
                200)
            .path("targetId"));
    f.call("POST", path, body, f.user(), Ids.next(), 403);
  }
}
