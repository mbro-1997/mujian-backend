package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** 对47操作中的45业务操作发真实HTTP；两个专用通知另验签名/持久化。 */
class D3BHttpBoundaryTest {
  /** 验证各命令拒绝匿名身份和未声明参数。 */
  @Test
  void everyBusinessOperationRejectsAnonymousAndUnknownQuery() throws Exception {
    D2ApplicationTest fixture = new D2ApplicationTest();
    fixture.start();
    try {
      JsonNode samples =
          fixture.json.readTree(
              getClass().getResourceAsStream("/contracts/d3b-request-samples.json"));
      int checked = 0;
      for (JsonNode sample : samples) {
        String path = sample.path("path").asString(), method = sample.path("method").asString();
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(fixture.base + path));
        for (Map.Entry<String, JsonNode> entry : sample.path("headers").properties())
          request.header(entry.getKey(), entry.getValue().asString());
        if ("POST".equals(method)) request.header("Content-Type", "application/json");
        request.method(
            method,
            "POST".equals(method)
                ? HttpRequest.BodyPublishers.ofString(
                    fixture.json.writeValueAsString(sample.get("body")))
                : HttpRequest.BodyPublishers.noBody());
        HttpResponse<String> response =
            fixture.http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        fixture.received(method, path, response, 401);
        String invalid = path + (path.contains("?") ? "&" : "?") + "undeclared=1";
        request.uri(URI.create(fixture.base + invalid));
        fixture.received(
            method,
            invalid,
            fixture.http.send(request.build(), HttpResponse.BodyHandlers.ofString()),
            400);
        checked++;
      }
      assertEquals(45, checked);
    } finally {
      try {
        D3BFixtureCleanup.remove(fixture);
      } finally {
        fixture.stop();
      }
    }
  }

  /** 新增管理操作逐项拒绝无菜单授权的隔离管理员；不使用本人账号。 */
  @Test
  void allNewAdminOperationsEnforceAssignedMenus() throws Exception {
    D2ApplicationTest fixture = new D2ApplicationTest();
    fixture.start();
    try {
      fixture.db.update(
          "DELETE ar FROM admin_account_role ar JOIN admin_account a ON a.id=ar.admin_account_id"
              + " WHERE a.user_id=?",
          fixture.admin);
      JsonNode samples =
          fixture.json.readTree(
              getClass().getResourceAsStream("/contracts/d3b-request-samples.json"));
      int checked = 0;
      for (JsonNode sample : samples) {
        String path = sample.path("path").asString();
        if (!path.startsWith("/api/admin/")) continue;
        fixture.call(
            sample.path("method").asString(),
            path,
            sample.path("method").asString().equals("GET") ? null : sample.get("body"),
            null,
            sample.path("headers").has("Idempotency-Key")
                ? java.util.UUID.randomUUID().toString()
                : null,
            403);
        assertEquals("PERMISSION_DENIED", fixture.lastReason);
        checked++;
      }
      assertEquals(20, checked);
    } finally {
      try {
        D3BFixtureCleanup.remove(fixture);
      } finally {
        fixture.stop();
      }
    }
  }
}
