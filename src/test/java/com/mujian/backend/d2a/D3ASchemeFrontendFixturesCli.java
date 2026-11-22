package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.view;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import tools.jackson.databind.JsonNode;

/** 复用D2隔离账号/会话，仅在测试classpath提供本轮方案页面联调；不进入运行JAR。 */
public final class D3ASchemeFrontendFixturesCli {
  private D3ASchemeFrontendFixturesCli() {}

  /** 临时凭据仅写600私有文件/回环引导，停止时回收本次数据与会话。 */
  public static void main(String[] args) throws Exception {
    D3SchemeRevisionTest fixture = new D3SchemeRevisionTest();
    fixture.f.manualShutdown = true;
    Path state = Path.of(".local/test/d3a-20261118-frontend-runtime.json");
    HttpServer bootstrap = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    try {
      fixture.start();
      String schemeId = fixture.scheme(fixture.actor);
      fixture.save(
          schemeId,
          List.of(fixture.addItem(fixture.sku, 2, 0), fixture.addItem(fixture.sku2, 1, 1)));
      Instant current = fixture.f.clock.at;
      JsonNode expired;
      try {
        fixture.f.clock.at = current.minusSeconds(7 * 86400L);
        expired =
            fixture
                .call(
                    "POST",
                    "/api/me/schemes/" + schemeId + "/share",
                    view("expectedVersion", 1, "hidePrice", true),
                    fixture.actor,
                    fixture.key(),
                    200)
                .path("share");
      } finally {
        fixture.f.clock.at = current;
      }
      JsonNode active =
          fixture
              .call(
                  "POST",
                  "/api/me/schemes/" + schemeId + "/share",
                  view("expectedVersion", 2, "hidePrice", false),
                  fixture.actor,
                  fixture.key(),
                  200)
              .path("share");
      String payload =
          fixture.f.json.writeValueAsString(
              view(
                  "baseUrl",
                  fixture.f.base,
                  "buyerToken",
                  fixture.actor.token(),
                  "buyerId",
                  fixture.actor.id(),
                  "schemeId",
                  schemeId,
                  "skuId",
                  fixture.sku,
                  "otherSkuId",
                  fixture.sku2,
                  "newSkuId",
                  fixture.sku3,
                  "activeShareToken",
                  active.path("token").asString(),
                  "expiredShareToken",
                  expired.path("token").asString(),
                  "bootstrapPort",
                  bootstrap.getAddress().getPort(),
                  "contractVersion",
                  "2026-11-18.1"));
      Files.deleteIfExists(state);
      Files.createFile(
          state,
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      Files.writeString(state, payload);
      bootstrap.createContext(
          "/bootstrap",
          exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
              exchange.sendResponseHeaders(405, -1);
              exchange.close();
              return;
            }
            byte[] body = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.sendResponseHeaders(200, body.length);
            try (java.io.OutputStream output = exchange.getResponseBody()) {
              output.write(body);
            }
          });
      bootstrap.createContext(
          "/advance",
          exchange -> {
            try {
              if (!"POST".equals(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
              }
              int seconds =
                  Integer.parseInt(
                      new String(
                          exchange.getRequestBody().readNBytes(20),
                          java.nio.charset.StandardCharsets.UTF_8));
              if (seconds < 0 || seconds > 8 * 86400) {
                exchange.sendResponseHeaders(400, -1);
                return;
              }
              fixture.f.clock.at = fixture.f.clock.at.plusSeconds(seconds);
              exchange.sendResponseHeaders(204, -1);
            } catch (RuntimeException exception) {
              exchange.sendResponseHeaders(400, -1);
            } finally {
              exchange.close();
            }
          });
      bootstrap.start();
      Runtime.getRuntime()
          .addShutdownHook(
              new Thread(
                  () -> {
                    bootstrap.stop(0);
                    try {
                      fixture.stop();
                      Files.deleteIfExists(state);
                    } catch (Exception exception) {
                      throw new IllegalStateException("isolated cleanup failed", exception);
                    }
                  }));
      System.out.println(
          "D3A_FRONTEND_READY api="
              + fixture.f.base
              + " bootstrapPort="
              + bootstrap.getAddress().getPort()
              + "; private fixture file ready; no shared service changes");
      new CountDownLatch(1).await();
    } catch (Exception exception) {
      bootstrap.stop(0);
      fixture.stop();
      Files.deleteIfExists(state);
      throw exception;
    }
  }
}
