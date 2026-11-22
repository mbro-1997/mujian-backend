package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.view;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.mujian.backend.infrastructure.payment.wechat.PaymentGateway;
import com.mujian.backend.order.service.*;
import com.mujian.backend.payment.dto.PaymentAccount;
import com.mujian.backend.payment.mapper.*;
import com.mujian.backend.payment.service.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import org.junit.jupiter.api.Test;

/** 生产小程序JS和响应校验对接真HTTP；仅wx及资金渠道边界受控，绝不作为原生/真机证据。 */
class D3BMiniHttpTest {
  /** 通过隔离 HTTP 验证小程序实际交易页面。 */
  @Test
  void productionMiniPagesCompleteTradingAgainstIsolatedHttp() throws Exception {
    D3BQuoteTest fixture = new D3BQuoteTest();
    fixture.start();
    HttpServer controls = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    Path state =
        Files.createTempFile(
            "mujian-d3b-mini-",
            ".json",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    try {
      PaymentGateway gateway = fixture.f.app.getBean(PaymentGateway.class);
      doReturn(
              new PaymentAccount(
                  "test-app",
                  "test-platform",
                  "https://example.invalid/api/wechat-pay/notifications/payment"))
          .when(gateway)
          .requireReady();
      doReturn("isolated-prepay").when(gateway).create(any());
      doReturn(
              new PaymentGateway.Parameters(
                  "test-app",
                  "1234567890",
                  "isolated-nonce",
                  "prepay_id=isolated-prepay",
                  "RSA",
                  "isolated-signature"))
          .when(gateway)
          .parameters(anyString(), anyString());
      doAnswer(
              invocation -> {
                PaymentGateway.Reference reference = invocation.getArgument(0);
                List<PaymentGateway.SuborderFact> facts = new ArrayList<>();
                for (PaymentGateway.Suborder suborder : reference.suborders())
                  facts.add(
                      new PaymentGateway.SuborderFact(
                          reference.account().mchId(),
                          suborder.subMchid(),
                          suborder.outTradeNo(),
                          "wx-" + suborder.outTradeNo(),
                          "SUCCESS",
                          suborder.totalFen(),
                          "CNY",
                          suborder.totalFen(),
                          "CNY",
                          fixture.f.clock.instant()));
                return new PaymentGateway.PaymentFact(
                    reference.account().appId(),
                    reference.account().mchId(),
                    reference.outTradeNo(),
                    facts,
                    false);
              })
          .when(gateway)
          .query(any());
      doAnswer(
              invocation -> {
                PaymentGateway.RefundRequest request = invocation.getArgument(0);
                return new PaymentGateway.RefundFact(
                    request.account().mchId(),
                    request.subMchid(),
                    request.outTradeNo(),
                    request.transactionId(),
                    request.refundNo(),
                    "wx-r-" + request.refundNo(),
                    "SUCCESS",
                    request.totalFen(),
                    request.refundFen(),
                    "CNY",
                    fixture.f.clock.instant(),
                    false);
              })
          .when(gateway)
          .refund(any());
      controls.createContext(
          "/paid",
          exchange -> {
            try {
              if (!exchange.getRequestMethod().equals("POST")) throw new IllegalArgumentException();
              String batch =
                  fixture.f.db.queryForObject(
                      "SELECT id FROM checkout_batch WHERE buyer_user_id=?",
                      String.class,
                      fixture.buyer.id());
              fixture.f.clock.at = fixture.f.clock.at.plusSeconds(31);
              fixture.f.app.getBean(PaymentExecution.class).execute(batch);
              assertEquals(
                  1,
                  fixture
                      .f
                      .app
                      .getBean(com.mujian.backend.order.mapper.RentalOrderMapper.class)
                      .selectOrderIdsByBatch(batch, false)
                      .size());
              exchange.sendResponseHeaders(204, -1);
            } catch (Throwable failure) {
              System.out.println("ISOLATED_CONTROL_FAILED " + failure.getClass().getSimpleName());
              exchange.sendResponseHeaders(500, -1);
            } finally {
              exchange.close();
            }
          });
      controls.createContext(
          "/refund",
          exchange -> {
            try {
              if (!exchange.getRequestMethod().equals("POST")) throw new IllegalArgumentException();
              List<String> ids =
                  fixture.f.db.queryForList(
                      "SELECT r.id FROM refund_order r JOIN payment_order p ON"
                          + " p.id=r.payment_order_id WHERE p.buyer_user_id=?",
                      String.class,
                      fixture.buyer.id());
              assertEquals(1, ids.size());
              for (String id : ids) fixture.f.app.getBean(RefundExecution.class).execute(id);
              exchange.sendResponseHeaders(204, -1);
            } catch (Throwable failure) {
              System.out.println("ISOLATED_CONTROL_FAILED " + failure.getClass().getSimpleName());
              exchange.sendResponseHeaders(500, -1);
            } finally {
              exchange.close();
            }
          });
      controls.start();
      Files.writeString(
          state,
          fixture.f.json.writeValueAsString(
              view(
                  "baseUrl",
                  fixture.f.base,
                  "controlUrl",
                  "http://127.0.0.1:" + controls.getAddress().getPort(),
                  "buyerToken",
                  fixture.buyer.token(),
                  "supplierToken",
                  fixture.supplier.token(),
                  "checklistId",
                  fixture.checklist,
                  "couponId",
                  fixture.coupon)));
      Path frontend = Path.of("../mujian-frontend").toAbsolutePath().normalize();
      Process process =
          new ProcessBuilder(
                  "/Users/mbro/.local/nodejs/current/bin/node",
                  frontend.resolve("tests/trading-http-flow.cjs").toString(),
                  state.toString())
              .directory(frontend.toFile())
              .redirectErrorStream(true)
              .redirectOutput(Path.of("target/d3b-mini-http.log").toFile())
              .start();
      if (!process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS)) {
        process.destroyForcibly();
        fail("mini HTTP test exceeded bounded time");
      }
      assertEquals(0, process.exitValue(), "see target/d3b-mini-http.log");
    } finally {
      controls.stop(0);
      Files.deleteIfExists(state);
      fixture.stop();
    }
  }
}
