package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.support.Ids;
import com.mujian.backend.material.service.VerificationMaterialCleanup;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** 真实HTTP/隔离MySQL与私有对象替身，多节点照片及保护引用准确区分。 */
class D4EvidenceHttpTest {
  /** 实际上传图片、受控签名、草稿移除及正式引用保护；使用活动会话恢复原号。 */
  @Test
  void attachViewRemoveDispatchAndReturnUseOnePrivateReference() throws Exception {
    D3BQuoteTest fixture = new D3BQuoteTest();
    fixture.extraSources = new Class<?>[] {D4OutboundHttpTest.Ready.class};
    fixture.extraArguments =
        new String[] {
          "--mj.fulfillment.enabled=true", "--mj.fulfillment.recovery-delay-ms=3600000"
        };
    try {
      fixture.start();
      fixture.f.db.update("UPDATE prop_sku SET deposit_amount_fen=500 WHERE id=?", fixture.sku);
      String order = fixture.paidOrder(),
          factory = "/api/me/factory-orders/" + order,
          buyer = "/api/me/rental-orders/" + order;
      fixture.call(
          "POST",
          factory + "/accept",
          view("expectedVersion", 0),
          fixture.supplier,
          fixture.key(),
          200);
      assertEquals(
          "1",
          fixture
              .call(
                  "GET",
                  "/api/me/factory-orders?view=factory_pending",
                  null,
                  fixture.supplier,
                  null,
                  200)
              .path("total")
              .asString());
      assertEquals(
          "1",
          fixture
              .call("GET", "/api/me/factory-orders/overview", null, fixture.supplier, null, 200)
              .path("pendingOutboundCount")
              .asString());
      String upload = upload(fixture, factory, "outbound", fixture.supplier, 200);
      upload(fixture, buyer, "outbound", fixture.buyer, 400);
      upload(fixture, factory, "return_sent", fixture.supplier, 400);
      fixture.call(
          "POST",
          buyer + "/evidence",
          view("expectedVersion", 0, "materials", List.of(part(upload, "receipt_check"))),
          fixture.buyer,
          fixture.key(),
          409);
      String key = fixture.key();
      Map<String, Object> attach =
          view("expectedVersion", 0, "materials", List.of(part(upload, "outbound")));
      fixture.call("POST", factory + "/evidence", attach, fixture.supplier, key, 200);
      fixture.call("POST", factory + "/evidence", attach, fixture.supplier, key, 200);
      JsonNode page =
          fixture.call("GET", buyer + "/evidence?node=outbound", null, fixture.buyer, null, 200);
      assertEquals("1", page.path("total").asString());
      String evidenceId = page.path("items").get(0).path("id").asString();
      assertFalse(page.toString().contains("file_storage_key"));
      fixture.call(
          "POST",
          buyer + "/evidence/" + evidenceId + "/view",
          Map.of(),
          fixture.f.user(),
          null,
          404);
      JsonNode signed =
          fixture.call(
              "POST",
              buyer + "/evidence/" + evidenceId + "/view",
              Map.of(),
              fixture.buyer,
              null,
              200);
      assertEquals(
          time(now(fixture.f.clock).plusSeconds(120)), signed.path("expiresAt").asString());
      fixture.call(
          "POST",
          factory + "/evidence/" + evidenceId + "/remove",
          view("expectedVersion", 1),
          fixture.supplier,
          fixture.key(),
          200);
      fixture.call(
          "POST", buyer + "/evidence/" + evidenceId + "/view", Map.of(), fixture.buyer, null, 404);
      List<Map<String, Object>> inputs = new ArrayList<>();
      for (int i = 0; i < 3; i++)
        inputs.add(part(upload(fixture, factory, "outbound", fixture.supplier, 200), "outbound"));
      fixture.call(
          "POST",
          factory + "/evidence",
          view("expectedVersion", 2, "materials", inputs),
          fixture.supplier,
          fixture.key(),
          200);
      String item =
          fixture.f.db.queryForObject(
              "SELECT id FROM rental_order_item WHERE rental_order_id=?", String.class, order);
      fixture.call(
          "POST",
          factory + "/outbound/quantity",
          view("expectedVersion", 3, "itemId", item, "quantity", 2),
          fixture.supplier,
          fixture.key(),
          200);
      fixture.call(
          "POST",
          factory + "/outbound/confirm",
          view("expectedVersion", 4),
          fixture.supplier,
          fixture.key(),
          200);
      JsonNode after = fixture.call("GET", buyer + "/evidence", null, fixture.buyer, null, 200);
      fixture.call(
          "POST",
          factory + "/evidence/" + after.path("items").get(0).path("id").asString() + "/remove",
          view("expectedVersion", 5),
          fixture.supplier,
          fixture.key(),
          409);
      fixture.call(
          "POST",
          buyer + "/receipt/confirm",
          view("expectedVersion", 0),
          fixture.buyer,
          fixture.key(),
          200);
      // 已收货的订单允许到厂后立即开始核验，仍是工厂可操作项。
      assertEquals(
          "1",
          fixture
              .call(
                  "GET",
                  "/api/me/factory-orders?view=factory_pending",
                  null,
                  fixture.supplier,
                  null,
                  200)
              .path("total")
              .asString());
      String packaging = upload(fixture, buyer, "return_sent", fixture.buyer, 200);
      fixture.call(
          "POST",
          buyer + "/evidence",
          view("expectedVersion", 0, "materials", List.of(part(packaging, "return_sent"))),
          fixture.buyer,
          fixture.key(),
          200);
      Map<String, Object> shipment =
          view(
              "expectedVersion",
              1,
              "sentAt",
              time(now(fixture.f.clock)),
              "trackingNo",
              "归还沪Ab901",
              "carrierName",
              null,
              "remark",
              "同城交接");
      fixture.call(
          "POST", buyer + "/return-shipment/save", shipment, fixture.buyer, fixture.key(), 200);
      JsonNode draft =
          fixture
              .call("GET", buyer + "/fulfillment", null, fixture.buyer, null, 200)
              .path("shipment");
      assertEquals("归还沪Ab901", draft.path("trackingNo").asString());
      assertTrue(draft.path("expectedCheckStartAt").isNull());
      assertEquals(1, draft.path("photoCount").asInt());
      boolean nativeSaved = nativeDraft(fixture, order, fixture.buyer.token(), "shipment");
      shipment.put("expectedVersion", nativeSaved ? 3 : 2);
      fixture.call(
          "POST", buyer + "/return-shipment/send", shipment, fixture.buyer, fixture.key(), 200);
      assertEquals(
          time(now(fixture.f.clock).plusHours(12)),
          fixture
              .call("GET", buyer + "/fulfillment", null, fixture.buyer, null, 200)
              .path("shipment")
              .path("expectedCheckStartAt")
              .asString());
      String checkPath = factory + "/return-check";
      fixture.call(
          "POST",
          checkPath + "/quantity",
          view("expectedVersion", 0, "itemId", item, "quantity", 0),
          fixture.supplier,
          fixture.key(),
          409);
      assertEquals(
          0,
          fixture.f.db.queryForObject(
              "SELECT COUNT(*) FROM order_return_check WHERE rental_order_id=?",
              Integer.class,
              order));
      fixture.call(
          "POST",
          checkPath + "/quantity",
          view("expectedVersion", 0, "itemId", item, "quantity", 1),
          fixture.supplier,
          fixture.key(),
          200);
      fixture.call(
          "POST",
          checkPath + "/confirm",
          view("expectedVersion", 1),
          fixture.supplier,
          fixture.key(),
          409);
      JsonNode early =
          fixture
              .call("GET", buyer + "/fulfillment", null, fixture.buyer, null, 200)
              .path("returnCheck");
      assertEquals(time(now(fixture.f.clock)), early.path("startAt").asString());
      assertEquals(time(now(fixture.f.clock).plusHours(72)), early.path("autoCloseAt").asString());
      fixture.call(
          "POST",
          checkPath + "/quantity",
          view("expectedVersion", 1, "itemId", item, "quantity", 2),
          fixture.supplier,
          fixture.key(),
          200);
      String closeKey = fixture.key();
      D2ApplicationTest.Fault fault = fixture.f.app.getBean(D2ApplicationTest.Fault.class);
      fault.table = "trading_operation_record";
      try {
        fixture.call(
            "POST",
            checkPath + "/confirm",
            view("expectedVersion", 2),
            fixture.supplier,
            closeKey,
            500);
      } finally {
        fault.table = null;
      }
      assertEquals(
          2,
          fixture.f.db.queryForObject(
              "SELECT renting_quantity FROM prop_sku_stock WHERE sku_id=?",
              Integer.class,
              fixture.sku));
      assertEquals(
          0,
          fixture.f.db.queryForObject(
              "SELECT COUNT(*) FROM refund_order WHERE rental_order_id=?", Integer.class, order));
      JsonNode closed =
          fixture.call(
              "POST",
              checkPath + "/confirm",
              view("expectedVersion", 2),
              fixture.supplier,
              closeKey,
              200);
      assertTrue(closed.path("inventoryReleased").asBoolean());
      assertTrue(closed.path("refundObligationRecorded").asBoolean());
      assertEquals(
          "return_checking",
          fixture.f.db.queryForObject(
              "SELECT order_status FROM rental_order WHERE id=?", String.class, order));
      assertEquals(
          0,
          fixture.f.db.queryForObject(
              "SELECT renting_quantity FROM prop_sku_stock WHERE sku_id=?",
              Integer.class,
              fixture.sku));
      assertEquals(
          6,
          fixture.f.db.queryForObject(
              "SELECT available_quantity FROM prop_sku_stock WHERE sku_id=?",
              Integer.class,
              fixture.sku));
      assertEquals(
          "0",
          fixture
              .call(
                  "GET",
                  "/api/me/factory-orders?view=factory_pending",
                  null,
                  fixture.supplier,
                  null,
                  200)
              .path("total")
              .asString());
      String refund = closed.path("refundIds").get(0).asString();
      com.mujian.backend.payment.service.RefundWorkTransaction work =
          fixture.f.app.getBean(com.mujian.backend.payment.service.RefundWorkTransaction.class);
      com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.RefundRequest request =
          work.claim(refund).request();
      com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.RefundFact success =
          new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.RefundFact(
              request.account().mchId(),
              request.subMchid(),
              request.outTradeNo(),
              request.transactionId(),
              request.refundNo(),
              "wx-test-only",
              "SUCCESS",
              request.totalFen(),
              request.refundFen(),
              "CNY",
              fixture.f.clock.instant(),
              false);
      work.observe(refund, success);
      work.observe(refund, success);
      assertEquals(
          "completed",
          fixture.f.db.queryForObject(
              "SELECT order_status FROM rental_order WHERE id=?", String.class, order));
      JsonNode closeReplay =
          fixture.call(
              "POST",
              checkPath + "/confirm",
              view("expectedVersion", 2),
              fixture.supplier,
              closeKey,
              200);
      assertEquals(closed.path("refundIds"), closeReplay.path("refundIds"));
      assertEquals(
          2,
          fixture.f.db.queryForObject(
              "SELECT COUNT(*) FROM prop_stock_movement WHERE related_order_id=?",
              Integer.class,
              order));
      String removedKey =
          fixture.f.db.queryForObject(
              "SELECT file_storage_key FROM order_evidence_record WHERE id=?",
              String.class,
              evidenceId);
      String retained =
          fixture.f.db.queryForObject(
              "SELECT file_storage_key FROM order_evidence_record WHERE upload_receipt_id=?",
              String.class,
              packaging);
      fixture.f.clock.at = fixture.f.clock.at.plus(Duration.ofHours(49));
      fixture.f.store.policy = true;
      fixture.f.app.getBean(VerificationMaterialCleanup.class).scan(false);
      assertTrue(fixture.f.store.objects.containsKey(retained));
      assertFalse(fixture.f.store.objects.containsKey(removedKey));
    } finally {
      if (fixture.f.db != null && fixture.factoryId != null) {
        fixture.f.db.update(
            "DELETE i FROM order_return_item_check i JOIN order_return_check c ON"
                + " c.id=i.return_check_id WHERE c.factory_id=?",
            fixture.factoryId);
        fixture.f.db.update("DELETE FROM order_return_check WHERE factory_id=?", fixture.factoryId);
        fixture.f.db.update(
            "DELETE s FROM order_return_shipment s JOIN rental_order o ON o.id=s.rental_order_id"
                + " WHERE o.factory_id=?",
            fixture.factoryId);
      }
      new D4OutboundHttpTest().cleanup(fixture);
    }
  }

  /** 仅将隔离后端地址和夹具会话通过子进程标准输入传给原生页面运行器。 */
  static boolean nativeDraft(D3BQuoteTest fixture, String order, String token, String mode)
      throws Exception {
    if (!"1".equals(System.getenv("MJ_D4_NATIVE_TEST"))) return false;
    Process process =
        new ProcessBuilder(
                System.getenv("MJ_D4_NODE"), "../mujian-frontend/tests/d4-native-draft-http.cjs")
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .redirectOutput(ProcessBuilder.Redirect.INHERIT)
            .start();
    try (java.io.OutputStream out = process.getOutputStream()) {
      out.write(
          fixture.f.json.writeValueAsBytes(
              view("base", fixture.f.base, "order", order, "token", token, "mode", mode)));
    }
    assertTrue(process.waitFor(35, java.util.concurrent.TimeUnit.SECONDS));
    assertEquals(0, process.exitValue());
    return true;
  }

  /** 每个照片引用只提交机器契约允许的元数据。 */
  static Map<String, Object> part(String id, String node) {
    return view(
        "receiptId",
        id,
        "node",
        node,
        "scene",
        "overall",
        "itemId",
        null,
        "statementId",
        null,
        "transportLegId",
        null,
        "capturedAt",
        null,
        "text",
        null);
  }

  /** multipart实际发送两个字段，不绕开边界过滤器。 */
  static String upload(
      D3BQuoteTest fixture, String root, String node, D2ApplicationTest.User actor, int status)
      throws Exception {
    String boundary = "Order" + Ids.next(), path = root + "/evidence/uploads";
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    out.write(
        ("--"
                + boundary
                + "\r\nContent-Disposition: form-data; name=\"node\"\r\n\r\n"
                + node
                + "\r\n--"
                + boundary
                + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"test.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n")
            .getBytes(StandardCharsets.UTF_8));
    out.write(D2ApplicationTest.png());
    out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(fixture.f.base + path))
            .header("Authorization", "Bearer " + actor.token())
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .POST(HttpRequest.BodyPublishers.ofByteArray(out.toByteArray()))
            .build();
    return fixture
        .f
        .received(
            "POST",
            path,
            fixture.f.http.send(request, HttpResponse.BodyHandlers.ofString()),
            status)
        .path("receiptId")
        .asString();
  }
}
