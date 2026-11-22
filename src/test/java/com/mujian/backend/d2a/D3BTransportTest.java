package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.security.D2Contract;
import com.mujian.backend.common.support.Ids;
import java.util.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.JsonNode;

/** 车型、运价和持久恢复的真实隔离HTTP验证。 */
class D3BTransportTest {
  final D2ApplicationTest f = new D2ApplicationTest();

  /** 初始化本组隔离依赖。 */
  @BeforeEach
  void start() throws Exception {
    f.start(0, "--mj.prop.qr-worker-enabled=false");
  }

  /** 释放本组创建的资源。 */
  @AfterEach
  void stop() {
    try {
      D3BFixtureCleanup.remove(f);
    } finally {
      f.stop();
    }
  }

  /** 执行请求并检查预期响应。 */
  JsonNode call(String method, String path, Object body, String key, int status) throws Exception {
    JsonNode result = f.call(method, path, body, null, key, status);
    if (status == 200) {
      D2Contract contract = f.app.getBean(D2Contract.class);
      contract.validate(
          contract
              .route(path.split("\\?")[0], method)
              .operation()
              .path("responses")
              .path("200")
              .path("content")
              .path("application/json")
              .path("schema"),
          f.json.valueToTree(
              view("code", "OK", "message", "成功", "data", result, "traceId", Ids.next())));
    }
    return result;
  }

  /** 核对版本价格与操作回执。 */
  @Test
  void versionedPricesAndReceipts() throws Exception {
    String requestId = UUID.randomUUID().toString();
    Map<String, Object> vehicle =
        view(
            "tierCode",
            "test_" + Ids.next().substring(0, 10),
            "tierName",
            "隔离运输车型",
            "cargoLengthMm",
            2000,
            "cargoWidthMm",
            1500,
            "cargoHeightMm",
            1800,
            "effectiveVolumeCm3",
            "5000000",
            "safePayloadGram",
            "1000000",
            "sortNo",
            900,
            "status",
            "enabled");
    String path = "/api/admin/transport-vehicles";
    JsonNode created = call("POST", path, vehicle, requestId, 200);
    String id = created.path("resourceId").asString();
    call("POST", path, vehicle, UUID.randomUUID().toString(), 400);
    assertEquals(
        "replayed", call("POST", path, vehicle, requestId, 200).path("outcome").asString());
    assertEquals(
        "recovered",
        call("GET", "/api/admin/trading-operations/by-request/" + requestId, null, null, 200)
            .path("outcome")
            .asString());
    call("GET", path + "?keyword=" + vehicle.get("tierCode"), null, null, 200);
    call("GET", path + "/" + id, null, null, 200);
    vehicle.put("tierName", "隔离运输车型修改");
    call("POST", path, vehicle, requestId, 409);
    vehicle.put("expectedVersion", 0);
    call("POST", path + "/" + id + "/update", vehicle, UUID.randomUUID().toString(), 200);
    call("POST", path + "/" + id + "/update", vehicle, UUID.randomUUID().toString(), 409);
    String rules = "/api/admin/transport-price-rules";
    Map<String, Object> rule =
        view(
            "ruleVersion",
            "test_" + Ids.next().substring(0, 8),
            "cityCode",
            "310100",
            "vehicleTierId",
            id,
            "distanceMinMeter",
            0,
            "distanceMaxMeter",
            null,
            "includedDistanceMeter",
            10000,
            "guideMinFen",
            4000,
            "guideMaxFen",
            5000,
            "ownVehicleSettleFen",
            3500,
            "extraKmFen",
            300,
            "effectiveAt",
            time(now(f.clock).plusMinutes(1)),
            "expireAt",
            null,
            "extraFees",
            List.of());
    JsonNode made = call("POST", rules, rule, UUID.randomUUID().toString(), 200);
    String rid = made.path("resourceId").asString();
    call("GET", rules + "?vehicleTierId=" + id, null, null, 200);
    call("GET", rules + "/" + rid, null, null, 200);
    rule.put("expectedVersion", 0);
    call("POST", rules + "/" + rid + "/update", rule, UUID.randomUUID().toString(), 200);
    call(
        "POST",
        rules + "/" + rid + "/publish",
        view("expectedVersion", 1),
        UUID.randomUUID().toString(),
        200);
    rule.put("expectedVersion", 2);
    call("POST", rules + "/" + rid + "/update", rule, UUID.randomUUID().toString(), 409);
    rule.remove("expectedVersion");
    rule.put("ruleVersion", "test_" + Ids.next().substring(0, 8));
    String overlap =
        call("POST", rules, rule, UUID.randomUUID().toString(), 200).path("resourceId").asString();
    call(
        "POST",
        rules + "/" + overlap + "/publish",
        view("expectedVersion", 0),
        UUID.randomUUID().toString(),
        409);
    call(
        "POST",
        rules + "/" + rid + "/end",
        view("expectedVersion", 2, "expireAt", time(now(f.clock).plusHours(1))),
        UUID.randomUUID().toString(),
        200);
    f.clock.at = f.clock.at.plusSeconds(3600);
    assertEquals(
        "expired", call("GET", rules + "/" + rid, null, null, 200).path("status").asString());
    assertEquals(
        "1",
        call("GET", rules + "?vehicleTierId=" + id + "&status=expired", null, null, 200)
            .path("total")
            .asString());
    assertEquals(
        "0",
        call("GET", rules + "?vehicleTierId=" + id + "&status=active", null, null, 200)
            .path("total")
            .asString());
    assertEquals(
        7,
        f.db.queryForObject(
            "SELECT COUNT(*) FROM trading_operation_record WHERE resource_id IN (?,?,?)",
            Integer.class,
            id,
            rid,
            overlap));
  }
}
