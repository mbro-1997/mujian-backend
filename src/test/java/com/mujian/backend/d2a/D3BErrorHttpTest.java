package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.payment.dto.PaymentAccount;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 新报价入口共享拒绝机制以真实隔离HTTP验证，所有写入仅属于本次夹具。 */
class D3BErrorHttpTest {
  /** 每项变更先持久化到本次夹具，再同时核对报价/选券/提交的真实错误响应。 */
  @Test
  void quoteSourcesRejectChangedUnavailableAndExpiredInputs() throws Exception {
    D3BQuoteTest d3BQuoteTest = new D3BQuoteTest();
    d3BQuoteTest.start();
    try {
      d3BQuoteTest.input.put("couponId", d3BQuoteTest.coupon);
      PaymentGateway gateway = d3BQuoteTest.f.app.getBean(PaymentGateway.class);
      doReturn(
              new PaymentAccount(
                  "test-app",
                  "test-platform",
                  "https://example.invalid/api/wechat-pay/notifications/payment"))
          .when(gateway)
          .requireReady();
      String token =
          d3BQuoteTest.call("POST", "/api/me/checkout-quotes", d3BQuoteTest.input, d3BQuoteTest.buyer, null, 200)
              .path("quoteToken")
              .asString();
      d3BQuoteTest.f.db.update("UPDATE shipping_address SET version=version+1 WHERE id=?", d3BQuoteTest.address);
      reject(d3BQuoteTest, token, "ADDRESS_CHANGED", true);
      d3BQuoteTest.f.db.update("UPDATE shipping_address SET version=version-1 WHERE id=?", d3BQuoteTest.address);
      d3BQuoteTest.f.db.update(
          "UPDATE rental_checklist SET checklist_status='suspended' WHERE id=?", d3BQuoteTest.checklist);
      reject(d3BQuoteTest, token, "CHECKLIST_READ_ONLY", true);
      d3BQuoteTest.f.db.update(
          "UPDATE rental_checklist SET checklist_status='selecting' WHERE id=?", d3BQuoteTest.checklist);
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET packaging_factor_bps=NULL WHERE id=?", d3BQuoteTest.sku);
      reject(d3BQuoteTest, token, "PACKAGING_DATA_MISSING", true);
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET packaging_factor_bps=12000 WHERE id=?", d3BQuoteTest.sku);
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET sku_status='offline' WHERE id=?", d3BQuoteTest.sku);
      reject(d3BQuoteTest, token, "SKU_NOT_PUBLIC", true);
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET sku_status='online' WHERE id=?", d3BQuoteTest.sku);
      d3BQuoteTest.f.db.update(
          "UPDATE factory_wechat_merchant_account SET profit_sharing_status='pending' WHERE"
              + " factory_id=?",
          d3BQuoteTest.factoryId);
      reject(d3BQuoteTest, token, "MERCHANT_NOT_READY", true);
      d3BQuoteTest.f.db.update(
          "UPDATE factory_wechat_merchant_account SET profit_sharing_status='active' WHERE"
              + " factory_id=?",
          d3BQuoteTest.factoryId);
      d3BQuoteTest.f.db.update("UPDATE user_coupon SET coupon_status='invalid' WHERE id=?", d3BQuoteTest.coupon);
      reject(d3BQuoteTest, token, "COUPON_UNAVAILABLE", true);
      d3BQuoteTest.f.db.update("UPDATE user_coupon SET coupon_status='available' WHERE id=?", d3BQuoteTest.coupon);
      d3BQuoteTest.f.db.update(
          "UPDATE prop_sku_stock SET total_quantity=1,rentable_base_quantity=1,available_quantity=1"
              + " WHERE sku_id=?",
          d3BQuoteTest.sku);
      reject(d3BQuoteTest, token, "STOCK_NOT_ENOUGH", true);
      d3BQuoteTest.f.db.update(
          "UPDATE prop_sku_stock SET total_quantity=6,rentable_base_quantity=6,available_quantity=6"
              + " WHERE sku_id=?",
          d3BQuoteTest.sku);
      int rent =
          d3BQuoteTest.f.db.queryForObject(
              "SELECT daily_rent_price_fen FROM prop_sku WHERE id=?", Integer.class, d3BQuoteTest.sku);
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET daily_rent_price_fen=2147483647 WHERE id=?", d3BQuoteTest.sku);
      reject(d3BQuoteTest, token, "AMOUNT_OUT_OF_RANGE", true);
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET daily_rent_price_fen=? WHERE id=?", rent, d3BQuoteTest.sku);
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET version=version+1 WHERE id=?", d3BQuoteTest.sku);
      d3BQuoteTest.call("POST", "/api/me/checkout-batches", view("quoteToken", token), d3BQuoteTest.buyer, d3BQuoteTest.key(), 409);
      assertEquals("QUOTE_CHANGED", d3BQuoteTest.f.lastReason);
      d3BQuoteTest.f.db.update("UPDATE prop_sku SET version=version-1 WHERE id=?", d3BQuoteTest.sku);
      Instant original = d3BQuoteTest.f.clock.at;
      d3BQuoteTest.f.clock.at = original.plusSeconds(301);
      d3BQuoteTest.call("POST", "/api/me/checkout-batches", view("quoteToken", token), d3BQuoteTest.buyer, d3BQuoteTest.key(), 409);
      assertEquals("QUOTE_EXPIRED", d3BQuoteTest.f.lastReason);
      d3BQuoteTest.f.clock.at = original.minusSeconds(10 * 86400);
      reject(d3BQuoteTest, token, "RENTAL_WINDOW_NOT_OPEN", false);
      d3BQuoteTest.f.clock.at = original.plusSeconds(4 * 86400);
      reject(d3BQuoteTest, token, "RENTAL_WINDOW_CLOSED", false);
      d3BQuoteTest.f.clock.at = original;
      d3BQuoteTest.f.db.update("DELETE FROM rental_checklist_item WHERE checklist_id=?", d3BQuoteTest.checklist);
      reject(d3BQuoteTest, token, "CHECKLIST_EMPTY", false);
      assertEquals(
          0,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM checkout_batch WHERE buyer_user_id=?",
              Integer.class,
              d3BQuoteTest.buyer.id()));
    } finally {
      d3BQuoteTest.stop();
    }
  }

  /** 同一源规则由两个只读入口和有需要的提交入口分别返回机器契约允许的业务拒绝。 */
  private void reject(D3BQuoteTest d3BQuoteTest, String token, String reason, boolean submit)
      throws Exception {
    for (String endpoint : List.of("checkout-quotes", "checkout-coupons")) {
      d3BQuoteTest.call("POST", "/api/me/" + endpoint, d3BQuoteTest.input, d3BQuoteTest.buyer, null, 409);
      assertEquals(reason, d3BQuoteTest.f.lastReason);
    }
    if (submit) {
      d3BQuoteTest.call("POST", "/api/me/checkout-batches", view("quoteToken", token), d3BQuoteTest.buyer, d3BQuoteTest.key(), 409);
      assertEquals(reason, d3BQuoteTest.f.lastReason);
    }
  }

  /** 缺车型/规则/地图的报价必须失败；提交复用原路线，不重新请求地图。 */
  @Test
  void transportMissingResourcesNeverBecomeFreeDelivery() throws Exception {
    D3BQuoteTest d3BQuoteTest = new D3BQuoteTest();
    d3BQuoteTest.start();
    try {
      d3BQuoteTest.input.put(
          "deliveries",
          List.of(
              view(
                  "factoryId",
                  d3BQuoteTest.factoryId,
                  "outboundService",
                  "factory_delivery",
                  "returnService",
                  "user_return",
                  "buyerRemark",
                  null)));
      com.mujian.backend.delivery.mapper.TransportMapper transportMapper =
          spy(d3BQuoteTest.f.app.getBean(com.mujian.backend.delivery.mapper.TransportMapper.class));
      org.springframework.test.util.ReflectionTestUtils.setField(
          d3BQuoteTest.f.app.getBean(com.mujian.backend.order.service.CheckoutSourceReader.class),
          "transportMapper",
          transportMapper);
      doReturn(List.of()).when(transportMapper).selectEnabledVehicles();
      reject(d3BQuoteTest, null, "VEHICLE_NOT_MATCHED", false);
      reset(transportMapper);
      doReturn(List.of())
          .when(transportMapper)
          .selectActivePriceRules(
              org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.isNull());
      reject(d3BQuoteTest, null, "PRICE_RULE_NOT_FOUND", false);
      org.springframework.test.util.ReflectionTestUtils.setField(
          d3BQuoteTest.f.app.getBean(com.mujian.backend.delivery.service.DeliveryQuotation.class),
          "routeGateway",
          d3BQuoteTest.f.app.getBean(com.mujian.backend.infrastructure.map.TencentRouteGateway.class));
      for (String endpoint : List.of("checkout-quotes", "checkout-coupons")) {
        d3BQuoteTest.call("POST", "/api/me/" + endpoint, d3BQuoteTest.input, d3BQuoteTest.buyer, null, 503);
        assertEquals("MAP_CHANNEL_NOT_READY", d3BQuoteTest.f.lastReason);
      }
    } finally {
      d3BQuoteTest.stop();
    }
  }

  /** 原支付已过截止拒绝继续，渠道异常保持原号和原占用而不是新建支付。 */
  @Test
  void expiredPaymentAndChannelUnavailableRemainRecoverable() throws Exception {
    D3BQuoteTest d3BQuoteTest = new D3BQuoteTest();
    d3BQuoteTest.start();
    try {
      String batch = d3BQuoteTest.submitQuoted(d3BQuoteTest.buyer, d3BQuoteTest.input);
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.plusSeconds(601);
      d3BQuoteTest.call(
          "POST",
          "/api/me/checkout-batches/" + batch + "/pay",
          view("expectedVersion", 0),
          d3BQuoteTest.buyer,
          d3BQuoteTest.key(),
          409);
      assertEquals("PAYMENT_WINDOW_EXPIRED", d3BQuoteTest.f.lastReason);
      d3BQuoteTest.f.clock.at = d3BQuoteTest.f.clock.at.minusSeconds(601);
      PaymentGateway gateway = d3BQuoteTest.f.app.getBean(PaymentGateway.class);
      doThrow(unavailable("PAYMENT_CHANNEL_UNAVAILABLE")).when(gateway).requireReady();
      d3BQuoteTest.call(
          "POST",
          "/api/me/checkout-batches/" + batch + "/pay",
          view("expectedVersion", 0),
          d3BQuoteTest.buyer,
          d3BQuoteTest.key(),
          503);
      assertEquals("PAYMENT_CHANNEL_UNAVAILABLE", d3BQuoteTest.f.lastReason);
      assertEquals(
          1,
          d3BQuoteTest.f.db.queryForObject(
              "SELECT COUNT(*) FROM payment_order WHERE checkout_batch_id=?",
              Integer.class,
              batch));
      assertEquals(
          "held",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT reservation_status FROM inventory_reservation WHERE checkout_batch_id=?",
              String.class,
              batch));
    } finally {
      d3BQuoteTest.stop();
    }
  }

  /** 更新申请拒绝无效图片和非可重提原申请，沿用原审核而不改变现行SKU。 */
  @Test
  void skuUpdateRejectsInvalidImageAndInvalidPreviousApplication() throws Exception {
    D3BQuoteTest d3BQuoteTest = new D3BQuoteTest();
    d3BQuoteTest.start();
    try {
      Map<String, Object> fields = d3BQuoteTest.fixture.fields(d3BQuoteTest.supplier);
      fields.remove("typeCode");
      fields.remove("styleCodes");
      fields.remove("transportSuggestion");
      tools.jackson.databind.JsonNode validImages = d3BQuoteTest.f.json.valueToTree(fields.get("images"));
      fields.put(
          "images",
          List.of(
              view("kind", "upload", "uploadId", com.mujian.backend.common.support.Ids.next())));
      int version =
          d3BQuoteTest.f.db.queryForObject("SELECT version FROM prop_sku WHERE id=?", Integer.class, d3BQuoteTest.sku);
      Map<String, Object> body =
          view(
              "expectedSkuVersion",
              version,
              "previousApplicationId",
              null,
              "fields",
              fields,
              "applyRemark",
              "隔离更新拒绝验证");
      d3BQuoteTest.call(
          "POST",
          "/api/me/factory-props/" + d3BQuoteTest.sku + "/change-applications",
          body,
          d3BQuoteTest.supplier,
          com.mujian.backend.common.support.Ids.next(),
          409);
      assertEquals("PROP_IMAGE_INVALID", d3BQuoteTest.f.lastReason);
      fields.put("images", validImages);
      body.put(
          "previousApplicationId",
          d3BQuoteTest.f.db.queryForObject(
              "SELECT id FROM prop_sku_audit WHERE sku_id=? AND action_type='create'",
              String.class,
              d3BQuoteTest.sku));
      d3BQuoteTest.call(
          "POST",
          "/api/me/factory-props/" + d3BQuoteTest.sku + "/change-applications",
          body,
          d3BQuoteTest.supplier,
          com.mujian.backend.common.support.Ids.next(),
          409);
      assertEquals("SKU_CHANGE_NOT_ALLOWED", d3BQuoteTest.f.lastReason);
      assertEquals(
          version,
          d3BQuoteTest.f.db.queryForObject("SELECT version FROM prop_sku WHERE id=?", Integer.class, d3BQuoteTest.sku));
    } finally {
      d3BQuoteTest.stop();
    }
  }

  /** 导入/未来赔付事实与51厂边界通过显式来源注入验证，不制造开发库或本人业务事实。 */
  @Test
  void sourceBoundaryAndOutstandingCompensationRejectAllCheckoutEntrypoints() throws Exception {
    D3BQuoteTest d3BQuoteTest = new D3BQuoteTest();
    d3BQuoteTest.start();
    try {
      doReturn(
              new PaymentAccount(
                  "test-app",
                  "test-platform",
                  "https://example.invalid/api/wechat-pay/notifications/payment"))
          .when(d3BQuoteTest.f.app.getBean(PaymentGateway.class))
          .requireReady();
      String token =
          d3BQuoteTest.call("POST", "/api/me/checkout-quotes", d3BQuoteTest.input, d3BQuoteTest.buyer, null, 200)
              .path("quoteToken")
              .asString();
      com.mujian.backend.planning.mapper.ChecklistMapper checklistMapper =
          spy(d3BQuoteTest.f.app.getBean(com.mujian.backend.planning.mapper.ChecklistMapper.class));
      com.mujian.backend.planning.domain.RentalChecklistItemDO original =
          checklistMapper.selectChecklistItemsByChecklistIds(List.of(d3BQuoteTest.checklist)).get(0);
      List<com.mujian.backend.planning.domain.RentalChecklistItemDO> many = new ArrayList<>();
      for (int i = 0; i < 51; i++)
        many.add(
            new com.mujian.backend.planning.domain.RentalChecklistItemDO(
                com.mujian.backend.common.support.Ids.next(),
                original.checklistId(),
                original.skuId(),
                com.mujian.backend.common.support.Ids.next(),
                original.quantity(),
                original.remark(),
                i,
                original.maxRentableQuantitySnapshot(),
                original.unavailableReason(),
                original.skuSnapshotJson(),
                original.createdAt(),
                original.updatedAt()));
      doReturn(many).when(checklistMapper).selectChecklistItemsByChecklistIds(List.of(d3BQuoteTest.checklist));
      org.springframework.test.util.ReflectionTestUtils.setField(
          d3BQuoteTest.f.app.getBean(com.mujian.backend.order.service.CheckoutSourceReader.class),
          "checklistMapper",
          checklistMapper);
      reject(d3BQuoteTest, token, "FACTORY_COUNT_EXCEEDED", true);
      reset(checklistMapper);
      com.mujian.backend.order.mapper.CheckoutMapper checkout =
          spy(d3BQuoteTest.f.app.getBean(com.mujian.backend.order.mapper.CheckoutMapper.class));
      doReturn(true).when(checkout).existsOutstandingCompensation(d3BQuoteTest.buyer.id());
      org.springframework.test.util.ReflectionTestUtils.setField(
          d3BQuoteTest.f.app.getBean(com.mujian.backend.order.service.TradingAccess.class),
          "checkoutMapper",
          checkout);
      reject(d3BQuoteTest, token, "COMPENSATION_OUTSTANDING", true);
    } finally {
      d3BQuoteTest.stop();
    }
  }
}
