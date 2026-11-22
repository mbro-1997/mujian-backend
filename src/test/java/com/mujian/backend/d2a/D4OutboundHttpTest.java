package com.mujian.backend.d2a;

import java.util.ArrayList;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.delivery.service.DisputeGuard;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.JsonNode;

/** 真HTTP/隔离MySQL；只在测试源提供明确异议事实，不开放真实D5门禁。 */
class D4OutboundHttpTest {
  static class KnownFacts implements DisputeGuard {
    volatile boolean ready = true, paused = false, claim = false;

    /** 提供测试设置的订单限制集合。 */
    @Override
    public java.util.Optional<java.util.Set<String>> blockedOrders(String buyer, String factory) {
      return ready ? java.util.Optional.of(java.util.Set.of()) : java.util.Optional.empty();
    }

    /** 让列表和详情读取同一组测试事实。 */
    @Override
    public java.util.Map<String, Facts> pageFacts(java.util.List<String> ids) {
      java.util.Map<String, Facts> facts = new java.util.HashMap<>();
      for (String id : ids) facts.put(id, inspect(id));
      return facts;
    }

    /** 测试可控的异议事实，不打包进入生产代码。 */
    @Override
    public Facts inspect(String orderId) {
      return new Facts(ready, paused, claim, false);
    }
  }

  @TestConfiguration
  static class Ready {
    /** 隔离环境显式注入能力齐备。 */
    @Bean
    @Primary
    KnownFacts knownFacts() {
      return new KnownFacts();
    }
  }

  /** 默认门禁在鉴权后阻断写入；上下文读取无副作用。 */
  @Test
  void closedGateAndReadOwnershipDoNotCreateDrafts() throws Exception {
    D3BQuoteTest fixture = new D3BQuoteTest();
    try {
      fixture.start();
      String order = fixture.paidOrder();
      String path = "/api/me/factory-orders/" + order;
      fixture.call(
          "POST",
          path + "/accept",
          view("expectedVersion", 0),
          fixture.supplier,
          fixture.key(),
          200);
      assertEquals(
          2,
          fixture.f.db.queryForObject(
              "SELECT COUNT(*) FROM order_transport_leg WHERE rental_order_id=? AND"
                  + " leg_status='ready'",
              Integer.class,
              order));
      JsonNode context =
          fixture.call("GET", path + "/fulfillment", null, fixture.supplier, null, 200);
      assertTrue(context.path("outbound").path("id").isNull());
      assertEquals(0, context.path("outbound").path("version").asInt());
      assertFalse(context.path("disputeProtectionReady").asBoolean());
      fixture.call(
          "GET", "/api/me/rental-orders/" + order + "/fulfillment", null, fixture.buyer, null, 200);
      fixture.call(
          "GET",
          "/api/me/rental-orders/" + order + "/fulfillment",
          null,
          fixture.f.user(),
          null,
          404);
      fixture.call(
          "POST",
          path + "/outbound/save",
          view("expectedVersion", 0, "remark", null),
          fixture.supplier,
          fixture.key(),
          503);
      assertEquals("FULFILLMENT_NOT_OPEN", fixture.f.lastReason);
      assertEquals(
          0,
          fixture.f.db.queryForObject(
              "SELECT COUNT(*) FROM order_outbound_record WHERE rental_order_id=?",
              Integer.class,
              order));
    } finally {
      cleanup(fixture);
    }
  }

  /** 草稿重入、设置总量、版本冲突与原号恢复均经真实接口验证。 */
  @Test
  void draftQuantityReplayAndRecoveryUseOneRoot() throws Exception {
    D3BQuoteTest fixture = new D3BQuoteTest();
    fixture.extraSources = new Class<?>[] {Ready.class};
    fixture.extraArguments = new String[] {"--mj.fulfillment.enabled=true"};
    try {
      fixture.start();
      String order = fixture.paidOrder();
      String path = "/api/me/factory-orders/" + order;
      fixture.call(
          "POST",
          path + "/accept",
          view("expectedVersion", 0),
          fixture.supplier,
          fixture.key(),
          200);
      String key = fixture.key();
      JsonNode saved =
          fixture.call(
              "POST",
              path + "/outbound/save",
              view("expectedVersion", 0, "remark", "  木桌护角  "),
              fixture.supplier,
              key,
              200);
      assertEquals(1, saved.path("resourceVersion").asInt());
      String item =
          fixture.f.db.queryForObject(
              "SELECT id FROM rental_order_item WHERE rental_order_id=?", String.class, order);
      fixture.call(
          "POST",
          path + "/outbound/quantity",
          view("expectedVersion", 1, "itemId", item, "quantity", 3),
          fixture.supplier,
          fixture.key(),
          400);
      fixture.call(
          "POST",
          path + "/outbound/quantity",
          view("expectedVersion", 0, "itemId", item, "quantity", 2),
          fixture.supplier,
          fixture.key(),
          409);
      String quantityKey = fixture.key();
      fixture.call(
          "POST",
          path + "/outbound/quantity",
          view("expectedVersion", 1, "itemId", item, "quantity", 2),
          fixture.supplier,
          quantityKey,
          200);
      JsonNode context =
          fixture.call("GET", path + "/fulfillment", null, fixture.supplier, null, 200);
      assertEquals("木桌护角", context.path("outbound").path("remark").asString());
      assertTrue(context.path("outbound").path("items").get(0).path("complete").asBoolean());
      fixture.f.app.getBean(KnownFacts.class).ready = false;
      JsonNode replay =
          fixture.call(
              "POST",
              path + "/outbound/quantity",
              view("expectedVersion", 1, "itemId", item, "quantity", 2),
              fixture.supplier,
              quantityKey,
              200);
      assertEquals("replayed", replay.path("outcome").asString());
      assertEquals(2, replay.path("resourceVersion").asInt());
      JsonNode recovered =
          fixture.call(
              "GET",
              "/api/me/trading-operations/by-request/" + quantityKey,
              null,
              fixture.supplier,
              null,
              200);
      assertEquals(saved.path("resourceId").asString(), recovered.path("resourceId").asString());
      fixture.call(
          "POST",
          path + "/outbound/quantity",
          view("expectedVersion", 1, "itemId", item, "quantity", 1),
          fixture.supplier,
          quantityKey,
          409);
      assertEquals("IDEMPOTENCY_KEY_REUSED", fixture.f.lastReason);
      assertEquals(
          1,
          fixture.f.db.queryForObject(
              "SELECT COUNT(*) FROM order_outbound_record WHERE rental_order_id=?",
              Integer.class,
              order));
      assertEquals(
          2,
          fixture.f.db.queryForObject(
              "SELECT checked_quantity FROM order_outbound_item_check WHERE rental_order_id=?",
              Integer.class,
              order));
    } finally {
      cleanup(fixture);
    }
  }

  /** 本单A类扫码、重复新请求及原号结果均经过HTTP及真实唯一索引。 */
  @Test
  void assetScansUseOriginalAssignmentAndFrozenReplay() throws Exception {
    D3BQuoteTest fixture = new D3BQuoteTest();
    fixture.extraSources = new Class<?>[] {Ready.class};
    fixture.extraArguments = new String[] {"--mj.fulfillment.enabled=true"};
    try {
      fixture.start();
      JsonNode made = fixture.fixture.create(fixture.supplier, "A_ASSET", 2);
      String sku = made.path("skuId").asString();
      fixture.fixture.approve("sku_audit", made.path("targetId").asString(), 0);
      // 仅测试身份的贴标就绪事实，微信码图渠道另有旧待验。
      fixture.f.db.update("UPDATE prop_sn_instance SET instance_status='idle' WHERE sku_id=?", sku);
      fixture.call(
          "POST",
          "/api/me/checklists/" + fixture.checklist + "/items",
          view("expectedVersion", 1, "skuId", sku, "quantity", 2, "remark", null),
          fixture.buyer,
          fixture.key(),
          200);
      fixture.input.put("expectedVersion", 2);
      String order = fixture.paidOrder();
      String path = "/api/me/factory-orders/" + order;
      fixture.call(
          "POST",
          path + "/accept",
          view("expectedVersion", 0),
          fixture.supplier,
          fixture.key(),
          200);
      List<String> codes =
          fixture.f.db.queryForList(
              "SELECT qr_code_id FROM prop_qr_code WHERE sku_id=? AND target_type='SN' ORDER BY id",
              String.class,
              sku);
      assertEquals(2, codes.size());
      fixture.call(
          "POST",
          path + "/outbound/scans",
          view("expectedVersion", 0, "code", "external://sn"),
          fixture.supplier,
          fixture.key(),
          409);
      assertEquals("QR_INVALID", fixture.f.lastReason);
      String firstKey = fixture.key();
      JsonNode first =
          fixture.call(
              "POST",
              path + "/outbound/scans",
              view("expectedVersion", 0, "code", "pages/props/scan/index?scene=" + codes.get(0)),
              fixture.supplier,
              firstKey,
              200);
      assertFalse(first.path("duplicate").asBoolean());
      assertEquals(1, first.path("checkedQuantity").asInt());
      JsonNode duplicate =
          fixture.call(
              "POST",
              path + "/outbound/scans",
              view("expectedVersion", 1, "code", codes.get(0)),
              fixture.supplier,
              fixture.key(),
              200);
      assertTrue(duplicate.path("duplicate").asBoolean());
      assertEquals(1, duplicate.path("version").asInt());
      fixture.call(
          "POST",
          path + "/outbound/scans",
          view("expectedVersion", 1, "code", codes.get(1)),
          fixture.supplier,
          fixture.key(),
          200);
      fixture.f.app.getBean(KnownFacts.class).ready = false;
      JsonNode replay =
          fixture.call(
              "POST",
              path + "/outbound/scans",
              view("expectedVersion", 0, "code", "pages/props/scan/index?scene=" + codes.get(0)),
              fixture.supplier,
              firstKey,
              200);
      assertEquals(1, replay.path("checkedQuantity").asInt());
      assertEquals(1, replay.path("version").asInt());
      assertFalse(replay.path("duplicate").asBoolean());
      JsonNode scans =
          fixture.call(
              "GET", path + "/outbound/scans?page=2&pageSize=1", null, fixture.supplier, null, 200);
      assertEquals("2", scans.path("total").asString());
      assertEquals(1, scans.path("items").size());
      fixture.call("GET", path + "/outbound/scans", null, fixture.buyer, null, 403);
      fixture.f.app.getBean(KnownFacts.class).ready = true;
      fixture.f.db.update(
          "UPDATE prop_qr_code SET status='revoked' WHERE qr_code_id=?", codes.get(0));
      fixture.call(
          "POST",
          path + "/outbound/scans",
          view("expectedVersion", 2, "code", codes.get(0)),
          fixture.supplier,
          fixture.key(),
          409);
      assertEquals("QR_NOT_ACTIVE", fixture.f.lastReason);
      assertEquals(
          2,
          fixture.f.db.queryForObject(
              "SELECT COUNT(*) FROM order_outbound_scan_record WHERE rental_order_id=?",
              Integer.class,
              order));
      String bItem =
          fixture.f.db.queryForObject(
              "SELECT id FROM rental_order_item WHERE rental_order_id=? AND sku_id=?",
              String.class,
              order,
              fixture.sku);
      fixture.call(
          "POST",
          path + "/outbound/quantity",
          view("expectedVersion", 2, "itemId", bItem, "quantity", 2),
          fixture.supplier,
          fixture.key(),
          200);
      ArrayList<Object> pics = new java.util.ArrayList<>();
      for (int n = 0; n < 3; n++)
        pics.add(
            D4EvidenceHttpTest.part(
                D4EvidenceHttpTest.upload(fixture, path, "outbound", fixture.supplier, 200),
                "outbound"));
      fixture.call(
          "POST",
          path + "/evidence",
          view("expectedVersion", 3, "materials", pics),
          fixture.supplier,
          fixture.key(),
          200);
      fixture.call(
          "POST",
          path + "/outbound/confirm",
          view("expectedVersion", 4),
          fixture.supplier,
          fixture.key(),
          200);
      fixture.call(
          "POST",
          "/api/me/rental-orders/" + order + "/receipt/confirm",
          view("expectedVersion", 0),
          fixture.buyer,
          fixture.key(),
          200);
      String replaced = com.mujian.backend.common.support.Ids.next();
      // 仅布置已有换码事实，归还必须核对同一 SN 的真实出库记录。
      fixture.f.db.update(
          "INSERT INTO"
              + " prop_qr_code(id,qr_code_id,code_type,target_type,target_id,sku_id,supersedes_qr_code_id,status,created_at,updated_at)"
              + " SELECT"
              + " ?,?,code_type,target_type,target_id,sku_id,id,'bound',created_at,updated_at FROM"
              + " prop_qr_code WHERE qr_code_id=?",
          com.mujian.backend.common.support.Ids.next(),
          replaced,
          codes.get(0));
      String rc = path + "/return-check";
      fixture.call(
          "POST",
          rc + "/scans",
          view("expectedVersion", 0, "code", codes.get(0)),
          fixture.supplier,
          fixture.key(),
          409);
      assertEquals("QR_NOT_ACTIVE", fixture.f.lastReason);
      fixture.call(
          "POST",
          rc + "/scans",
          view("expectedVersion", 0, "code", replaced),
          fixture.supplier,
          fixture.key(),
          200);
      JsonNode repeated =
          fixture.call(
              "POST",
              rc + "/scans",
              view("expectedVersion", 1, "code", replaced),
              fixture.supplier,
              fixture.key(),
              200);
      assertTrue(repeated.path("duplicate").asBoolean());
      fixture.call(
          "POST",
          rc + "/scans",
          view("expectedVersion", 1, "code", codes.get(1)),
          fixture.supplier,
          fixture.key(),
          200);
      fixture.call(
          "POST",
          rc + "/quantity",
          view("expectedVersion", 2, "itemId", bItem, "quantity", 2),
          fixture.supplier,
          fixture.key(),
          200);
      fixture.f.db.update(
          "UPDATE prop_sn_instance SET instance_status='repairing' WHERE id=(SELECT target_id FROM"
              + " prop_qr_code WHERE qr_code_id=?)",
          replaced);
      D4OutboundHttpTest.KnownFacts facts = fixture.f.app.getBean(KnownFacts.class);
      facts.claim = true;
      fixture.call(
          "POST",
          rc + "/confirm",
          view("expectedVersion", 3),
          fixture.supplier,
          fixture.key(),
          409);
      assertEquals("RETURN_CLAIM_EXISTS", fixture.f.lastReason);
      facts.claim = false;
      facts.paused = true;
      fixture.call(
          "POST",
          rc + "/confirm",
          view("expectedVersion", 3),
          fixture.supplier,
          fixture.key(),
          409);
      assertEquals("FULFILLMENT_PAUSED", fixture.f.lastReason);
      facts.paused = false;
      fixture.call(
          "POST",
          rc + "/confirm",
          view("expectedVersion", 3),
          fixture.supplier,
          fixture.key(),
          200);
      assertEquals(
          1,
          fixture.f.db.queryForObject(
              "SELECT COUNT(*) FROM prop_sn_instance WHERE sku_id=? AND"
                  + " instance_status='repairing'",
              Integer.class,
              sku));
      assertEquals(
          1,
          fixture.f.db.queryForObject(
              "SELECT COUNT(*) FROM prop_sn_instance WHERE sku_id=? AND instance_status='idle'",
              Integer.class,
              sku));
    } finally {
      cleanup(fixture);
    }
  }

  /** 用隔离照片持久记录验证出库门槛、跨根原子效果和收货到期恢复。 */
  @Test
  void dispatchAndDueReceiptShareOneInventoryAndEventEffect() throws Exception {
    D3BQuoteTest fixture = new D3BQuoteTest();
    fixture.extraSources = new Class<?>[] {Ready.class};
    fixture.extraArguments =
        new String[] {
          "--mj.fulfillment.enabled=true", "--mj.fulfillment.recovery-delay-ms=3600000"
        };
    try {
      fixture.start();
      String order = fixture.paidOrder(), path = "/api/me/factory-orders/" + order;
      fixture.call(
          "POST",
          path + "/accept",
          view("expectedVersion", 0),
          fixture.supplier,
          fixture.key(),
          200);
      fixture.call(
          "POST",
          path + "/outbound/confirm",
          view("expectedVersion", 0),
          fixture.supplier,
          fixture.key(),
          409);
      assertEquals("CHECK_INCOMPLETE", fixture.f.lastReason);
      String item =
          fixture.f.db.queryForObject(
              "SELECT id FROM rental_order_item WHERE rental_order_id=?", String.class, order);
      fixture.call(
          "POST",
          path + "/outbound/quantity",
          view("expectedVersion", 0, "itemId", item, "quantity", 2),
          fixture.supplier,
          fixture.key(),
          200);
      fixture.call(
          "POST",
          path + "/outbound/confirm",
          view("expectedVersion", 1),
          fixture.supplier,
          fixture.key(),
          409);
      assertEquals("OUTBOUND_PHOTOS_REQUIRED", fixture.f.lastReason);
      for (int index = 0; index < 3; index++)
        fixture.f.db.update(
            "INSERT INTO"
                + " order_evidence_record(id,rental_order_id,evidence_node,evidence_type,file_storage_key,upload_receipt_id,uploaded_by_user_id,uploaded_role,created_at)"
                + " VALUES(?,?,'outbound','photo',?,?,?,'factory',?)",
            com.mujian.backend.common.support.Ids.next(),
            order,
            "test-private/" + fixture.key(),
            com.mujian.backend.common.support.Ids.next(),
            fixture.supplier.id(),
            now(fixture.f.clock));
      String key = fixture.key();
      fixture.call(
          "POST",
          path + "/outbound/confirm",
          view("expectedVersion", 1),
          fixture.supplier,
          key,
          200);
      fixture.call(
          "POST",
          path + "/outbound/confirm",
          view("expectedVersion", 1),
          fixture.supplier,
          key,
          200);
      assertEquals(
          "fulfilling",
          fixture.f.db.queryForObject(
              "SELECT order_status FROM rental_order WHERE id=?", String.class, order));
      assertEquals(
          "renting",
          fixture.f.db.queryForObject(
              "SELECT reservation_status FROM inventory_reservation WHERE rental_order_id=?",
              String.class,
              order));
      assertEquals(
          1,
          fixture.f.db.queryForObject(
              "SELECT COUNT(*) FROM prop_stock_movement WHERE related_order_id=?",
              Integer.class,
              order));
      assertEquals(
          2,
          fixture.f.db.queryForObject(
              "SELECT renting_quantity FROM prop_sku_stock WHERE sku_id=?",
              Integer.class,
              fixture.sku));
      assertEquals(
          6,
          fixture.f.db.queryForObject(
              "SELECT rentable_base_quantity FROM prop_sku_stock WHERE sku_id=?",
              Integer.class,
              fixture.sku));
      String buyerPath = "/api/me/rental-orders/" + order;
      JsonNode receipt =
          fixture
              .call("GET", buyerPath + "/fulfillment", null, fixture.buyer, null, 200)
              .path("receipt");
      assertEquals(time(now(fixture.f.clock).plusHours(12)), receipt.path("deadlineAt").asString());
      com.mujian.backend.delivery.service.FulfillmentRecovery job =
          fixture.f.app.getBean(com.mujian.backend.delivery.service.FulfillmentRecovery.class);
      fixture.f.clock.at = fixture.f.clock.at.plus(java.time.Duration.ofHours(12)).minusMillis(1);
      job.recoverReceipts();
      assertEquals(
          "pending",
          fixture.f.db.queryForObject(
              "SELECT check_status FROM order_receipt_check WHERE rental_order_id=?",
              String.class,
              order));
      fixture.f.clock.at = fixture.f.clock.at.plusMillis(1);
      fixture.f.app.getBean(KnownFacts.class).ready = false;
      job.recoverReceipts();
      assertEquals(
          "pending",
          fixture.f.db.queryForObject(
              "SELECT check_status FROM order_receipt_check WHERE rental_order_id=?",
              String.class,
              order));
      fixture.f.app.getBean(KnownFacts.class).ready = true;
      job.recoverReceipts();
      job.recoverReceipts();
      assertEquals(
          "auto_confirmed",
          fixture.f.db.queryForObject(
              "SELECT check_status FROM order_receipt_check WHERE rental_order_id=?",
              String.class,
              order));
      assertEquals(
          2,
          fixture.f.db.queryForObject(
              "SELECT COUNT(*) FROM order_fulfillment_event WHERE rental_order_id=?",
              Integer.class,
              order));
      fixture.f.clock.at = fixture.f.clock.at.minus(java.time.Duration.ofHours(12));
      JsonNode events =
          fixture.call("GET", buyerPath + "/fulfillment/events", null, fixture.buyer, null, 200);
      assertEquals("receipt_auto_confirmed", events.path("items").get(0).path("type").asString());
    } finally {
      cleanup(fixture);
    }
  }

  /** 仅删除本夹具工厂的D4子记录，再由既有D3夹具回收父记录。 */
  void cleanup(D3BQuoteTest fixture) {
    if (fixture.f.db != null && fixture.factoryId != null) {
      fixture.f.db.update(
          "DELETE s FROM order_return_scan_record s JOIN order_return_check c ON"
              + " c.id=s.return_check_id WHERE c.factory_id=?",
          fixture.factoryId);
      fixture.f.db.update(
          "DELETE i FROM order_return_item_check i JOIN order_return_check c ON"
              + " c.id=i.return_check_id WHERE c.factory_id=?",
          fixture.factoryId);
      fixture.f.db.update("DELETE FROM order_return_check WHERE factory_id=?", fixture.factoryId);
      for (String table :
          List.of("order_evidence_record", "order_receipt_check", "order_fulfillment_event"))
        fixture.f.db.update(
            "DELETE d FROM "
                + table
                + " d JOIN rental_order o ON o.id=d.rental_order_id WHERE o.factory_id=?",
            fixture.factoryId);
      fixture.f.db.update(
          "DELETE d FROM prop_stock_movement d JOIN rental_order o ON o.id=d.related_order_id WHERE"
              + " o.factory_id=?",
          fixture.factoryId);
      fixture.f.db.update(
          "DELETE i FROM order_outbound_scan_record i JOIN order_outbound_record r ON"
              + " r.id=i.outbound_record_id WHERE r.factory_id=?",
          fixture.factoryId);
      fixture.f.db.update(
          "DELETE i FROM order_outbound_item_check i JOIN order_outbound_record r ON"
              + " r.id=i.outbound_record_id WHERE r.factory_id=?",
          fixture.factoryId);
      fixture.f.db.update(
          "DELETE FROM order_outbound_record WHERE factory_id=?", fixture.factoryId);
    }
    fixture.stop();
  }
}
