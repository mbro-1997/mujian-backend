package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.view;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.CountDownLatch;

/** 只运行显式隔离库，给正式前端联调提供可回收账号与已付款订单；不进入运行JAR。 */
public final class D3BFrontendFixturesCli {
  private D3BFrontendFixturesCli() {}

  /** 浏览器通过回环引导使用测试管理员；不接触本人登录态、生产渠道或开发库数据。 */
  public static void main(String[] args) throws Exception {
    D3BQuoteTest d3BQuoteTest = new D3BQuoteTest();
    d3BQuoteTest.f.manualShutdown = true;
    d3BQuoteTest.start();
    boolean nativeMode = java.util.Arrays.asList(args).contains("--native");
    NativeScenario scenario = nativeMode ? new NativeScenario(d3BQuoteTest) : null;
    boolean refundedSeed = java.util.Arrays.asList(args).contains("--native-refunded");
    boolean listSeed = java.util.Arrays.asList(args).contains("--native-list");
    String order =
        nativeMode
                && !java.util.Arrays.asList(args).contains("--native-paid")
                && !refundedSeed
                && !listSeed
            ? ""
            : d3BQuoteTest.paidOrder();
    if (scenario != null) {
      if (refundedSeed) scenario.refundSeed(order);
      if (listSeed) {
        for (int index = 1; index < 21; index++) {
          scenario.refundSeed(order);
          scenario.control("draft");
          d3BQuoteTest.input.put("checklistId", d3BQuoteTest.checklist);
          order = d3BQuoteTest.paidOrder();
        }
      }
    }
    String payload =
        d3BQuoteTest.f.json.writeValueAsString(
            view(
                "baseUrl",
                d3BQuoteTest.f.base,
                "buyerToken",
                d3BQuoteTest.buyer.token(),
                "supplierToken",
                d3BQuoteTest.supplier.token(),
                "orderId",
                order,
                "checklistId",
                d3BQuoteTest.checklist,
                "skuId",
                d3BQuoteTest.sku,
                "factoryId",
                d3BQuoteTest.factoryId));
    Path state = Path.of(".local/test/d3b-frontend-runtime.json");
    Files.deleteIfExists(state);
    Files.createFile(
        state, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    Files.writeString(state, payload);
    HttpServer bootstrap = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    bootstrap.createContext(
        "/admin",
        exchange -> {
          if (!"GET".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
          }
          exchange.getResponseHeaders().set("Cache-Control", "no-store");
          exchange
              .getResponseHeaders()
              .add("Set-Cookie", d3BQuoteTest.f.adminCookie + "; HttpOnly; SameSite=Strict; Path=/");
          exchange.getResponseHeaders().set("Location", "http://127.0.0.1:5181/trading/orders");
          exchange.sendResponseHeaders(302, -1);
          exchange.close();
        });
    bootstrap.createContext(
        "/bootstrap",
        exchange -> {
          byte[] body = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.getResponseHeaders().set("Cache-Control", "no-store");
          exchange.sendResponseHeaders(200, body.length);
          try (java.io.OutputStream output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    if (scenario != null) {
      bootstrap.createContext(
          "/control",
          exchange -> {
            if (!"POST".equals(exchange.getRequestMethod())) {
              exchange.sendResponseHeaders(405, -1);
              exchange.close();
              return;
            }
            try {
              String action =
                  new String(
                      exchange.getRequestBody().readAllBytes(),
                      java.nio.charset.StandardCharsets.UTF_8);
              scenario.control(action);
              byte[] body =
                  d3BQuoteTest.f.json.writeValueAsBytes(
                      view("ok", true, "action", action, "checklistId", d3BQuoteTest.checklist));
              exchange.getResponseHeaders().set("Content-Type", "application/json");
              exchange.sendResponseHeaders(200, body.length);
              exchange.getResponseBody().write(body);
            } catch (Exception failure) {
              exchange.sendResponseHeaders(500, -1);
              failure.printStackTrace();
            } finally {
              exchange.close();
            }
          });
    }
    bootstrap.start();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  bootstrap.stop(0);
                  d3BQuoteTest.stop();
                  try {
                    Files.deleteIfExists(state);
                  } catch (java.io.IOException ignored) {
                  }
                }));
    System.out.println(
        "D3B_FRONTEND_READY bootstrapPort="
            + bootstrap.getAddress().getPort()
            + " api="
            + d3BQuoteTest.f.base
            + "; isolated fixtures only");
    new CountDownLatch(1).await();
  }

  /** 仅测试进程的渠道替身与场景推进；正式页面仍走原HTTP/事务，控制端仅绑定回环。 */
  private static final class NativeScenario {
    private final D3BQuoteTest fixture;
    private volatile boolean paid;
    private volatile boolean refunded;
    private final java.util.Set<String> closed = java.util.concurrent.ConcurrentHashMap.newKeySet();

    NativeScenario(D3BQuoteTest d3BQuoteTest) {
      this.fixture = d3BQuoteTest;
      com.mujian.backend.infrastructure.payment.wechat.PaymentGateway gateway =
          d3BQuoteTest.f.app.getBean(
              com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.class);
      org.mockito.Mockito.doReturn(
              new com.mujian.backend.payment.dto.PaymentAccount(
                  "test-app", "test-platform", "https://example.invalid/native/payment"))
          .when(gateway)
          .requireReady();
      org.mockito.Mockito.doReturn("native-isolated-prepay")
          .when(gateway)
          .create(org.mockito.ArgumentMatchers.any());
      org.mockito.Mockito.doReturn(
              new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.Parameters(
                  "test-app",
                  "1234567890",
                  "native-isolated",
                  "prepay_id=native-isolated-prepay",
                  "RSA",
                  "synthetic-test-signature"))
          .when(gateway)
          .parameters(
              org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
      org.mockito.Mockito.doAnswer(
              invocation -> {
                com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.Reference
                    reference =
                        invocation.getArgument(
                            0,
                            com.mujian.backend.infrastructure.payment.wechat.PaymentGateway
                                .Reference.class);
                String status =
                    paid
                        ? "SUCCESS"
                        : closed.contains(reference.outTradeNo()) ? "CLOSED" : "NOTPAY";
                return new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway
                    .PaymentFact(
                    reference.account().appId(),
                    reference.account().mchId(),
                    reference.outTradeNo(),
                    reference.suborders().stream()
                        .map(
                            sub ->
                                new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway
                                    .SuborderFact(
                                    reference.account().mchId(),
                                    sub.subMchid(),
                                    sub.outTradeNo(),
                                    paid ? "native-txn-" + sub.outTradeNo() : null,
                                    status,
                                    sub.totalFen(),
                                    "CNY",
                                    paid ? sub.totalFen() : null,
                                    paid ? "CNY" : null,
                                    paid ? d3BQuoteTest.f.clock.instant() : null))
                        .toList(),
                    false);
              })
          .when(gateway)
          .query(org.mockito.ArgumentMatchers.any());
      org.mockito.Mockito.doAnswer(
              invocation -> {
                com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.Reference
                    reference =
                        invocation.getArgument(
                            0,
                            com.mujian.backend.infrastructure.payment.wechat.PaymentGateway
                                .Reference.class);
                closed.add(reference.outTradeNo());
                return com.mujian.backend.common.enums.PaymentCloseResult.accepted;
              })
          .when(gateway)
          .close(org.mockito.ArgumentMatchers.any());
      org.mockito.stubbing.Answer<
              com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.RefundFact>
          refund =
              invocation -> {
                com.mujian.backend.infrastructure.payment.wechat.PaymentGateway.RefundRequest
                    request =
                        invocation.getArgument(
                            0,
                            com.mujian.backend.infrastructure.payment.wechat.PaymentGateway
                                .RefundRequest.class);
                return new com.mujian.backend.infrastructure.payment.wechat.PaymentGateway
                    .RefundFact(
                    request.account().mchId(),
                    request.subMchid(),
                    request.outTradeNo(),
                    request.transactionId(),
                    request.refundNo(),
                    "native-refund-" + request.refundNo(),
                    refunded ? "SUCCESS" : "PROCESSING",
                    request.totalFen(),
                    request.refundFen(),
                    "CNY",
                    refunded ? d3BQuoteTest.f.clock.instant() : null,
                    false);
              };
      org.mockito.Mockito.doAnswer(refund).when(gateway).refund(org.mockito.ArgumentMatchers.any());
      org.mockito.Mockito.doAnswer(refund)
          .when(gateway)
          .queryRefund(org.mockito.ArgumentMatchers.any());
      String vehicle = com.mujian.backend.common.support.Ids.next();
      String rule = com.mujian.backend.common.support.Ids.next();
      d3BQuoteTest.f.cleanupSeeds.put("transport_vehicle_tier", java.util.Set.of(vehicle));
      d3BQuoteTest.f.db.update(
          "INSERT INTO"
              + " transport_vehicle_tier(id,tier_code,tier_name,cargo_length_mm,cargo_width_mm,cargo_height_mm,effective_volume_cm3,safe_payload_gram,sort_no,tier_status,created_at,updated_at)"
              + " VALUES (?,?,'原生隔离车型',9000,9000,9000,999999999999,999999999999,0,'enabled',?,?)",
          vehicle,
          "native_" + vehicle.substring(0, 10),
          com.mujian.backend.common.support.D2Values.now(d3BQuoteTest.f.clock),
          com.mujian.backend.common.support.D2Values.now(d3BQuoteTest.f.clock));
      d3BQuoteTest.f.db.update(
          "INSERT INTO"
              + " transport_pricing_rule(id,rule_version,city_code,vehicle_tier_id,distance_min_meter,included_distance_meter,guide_min_amount_fen,guide_max_amount_fen,own_vehicle_settle_amount_fen,extra_km_amount_fen,allowed_extra_fee_json,rule_status,effective_at,created_at,updated_at)"
              + " VALUES (?,?,'310100',?,0,10000,4000,5000,3500,300,'[]','active',?,?,?)",
          rule,
          rule,
          vehicle,
          com.mujian.backend.common.support.D2Values.now(d3BQuoteTest.f.clock).minusMinutes(1),
          com.mujian.backend.common.support.D2Values.now(d3BQuoteTest.f.clock),
          com.mujian.backend.common.support.D2Values.now(d3BQuoteTest.f.clock));
    }

    /** 准备可追溯的退款测试数据。 */
    void refundSeed(String order) throws Exception {
      int version =
          fixture
              .call("GET", "/api/me/factory-orders/" + order, null, fixture.supplier, null, 200)
              .path("version")
              .asInt();
      fixture.call(
          "POST",
          "/api/me/factory-orders/" + order + "/reject",
          view("expectedVersion", version, "reason", "隔离原生结果页与分页夹具"),
          fixture.supplier,
          fixture.key(),
          200);
      control("refunded");
    }

    /** 设置本用例的受控执行条件。 */
    void control(String action) throws Exception {
      switch (action) {
        case "paid" -> paid = true;
        case "refunded" -> refunded = true;
        case "draft" -> {
          paid = false;
          refunded = false;
          String date =
              com.mujian.backend.common.support.D2Values.now(fixture.f.clock)
                  .toLocalDate()
                  .plusDays(5)
                  .toString();
          fixture.checklist =
              fixture
                  .call(
                      "POST",
                      "/api/me/checklists",
                      view("sourceType", "manual", "startDate", date, "endDate", date),
                      fixture.buyer,
                      fixture.key(),
                      200)
                  .path("resourceId")
                  .asString();
          fixture.call(
              "POST",
              "/api/me/checklists/" + fixture.checklist + "/items",
              view("expectedVersion", 0, "skuId", fixture.sku, "quantity", 2, "remark", null),
              fixture.buyer,
              fixture.key(),
              200);
        }
        case "recover", "expire" -> {}
        default -> throw new IllegalArgumentException("unknown native fixture action");
      }
      if (!"draft".equals(action)) {
        java.time.Instant next = fixture.f.clock.at.plusSeconds("expire".equals(action) ? 660 : 35);
        fixture.f.clock.at = next.isAfter(java.time.Instant.now()) ? next : java.time.Instant.now();
        // 调度在隔离环境禁用；只执行本次租赁方的原批次/退款，绝不扫描共享隔离库。
        for (String batch :
            fixture.f.db.queryForList(
                "SELECT id FROM checkout_batch WHERE buyer_user_id=?",
                String.class,
                fixture.buyer.id())) {
          fixture
              .f
              .app
              .getBean(com.mujian.backend.payment.service.PaymentExecution.class)
              .execute(batch);
        }
        for (String refundId :
            fixture.f.db.queryForList(
                "SELECT r.id FROM refund_order r JOIN rental_order o ON o.id=r.rental_order_id"
                    + " WHERE o.buyer_user_id=?",
                String.class,
                fixture.buyer.id())) {
          fixture
              .f
              .app
              .getBean(com.mujian.backend.payment.service.RefundExecution.class)
              .execute(refundId);
        }
      }
    }
  }
}
