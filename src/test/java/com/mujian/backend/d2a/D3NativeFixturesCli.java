package com.mujian.backend.d2a;

import static com.mujian.backend.common.support.D2Values.view;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.CountDownLatch;

/** 复用受保护测试库和虚构渠道，供正式项目在开发工具内集中验证；不进入运行JAR。 */
public final class D3NativeFixturesCli {
  /** 启动本测试命令行入口。 */
  public static void main(String[] args) throws Exception {
    D3PlanningTest fixture = new D3PlanningTest();
    fixture.start();
    String id = fixture.scheme(fixture.actor);
    fixture.post(
        "/api/me/schemes/" + id + "/items",
        view("expectedVersion", 0, "skuId", fixture.sku, "quantity", 2, "remark", "原生隔离验证"),
        fixture.actor);
    fixture.post(
        "/api/me/schemes/" + id + "/share",
        view("expectedVersion", 1, "hidePrice", true),
        fixture.actor);
    String token =
        fixture.get("/api/me/schemes/" + id, fixture.actor).path("share").path("token").asString();
    String payload =
        fixture.f.json.writeValueAsString(
            view(
                "baseUrl",
                fixture.f.base,
                "token",
                fixture.actor.token(),
                "schemeId",
                id,
                "skuId",
                fixture.sku,
                "shareToken",
                token));
    Path state = Path.of(".local/test/d3-native-runtime.json");
    Files.deleteIfExists(state);
    Files.createFile(
        state, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
    Files.writeString(state, payload);
    HttpServer bootstrap = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
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
    bootstrap.start();
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  bootstrap.stop(0);
                  fixture.stop();
                }));
    System.out.println(
        "D3_NATIVE_READY bootstrapPort="
            + bootstrap.getAddress().getPort()
            + " api="
            + fixture.f.base
            + "; isolated database only");
    new CountDownLatch(1).await();
  }
}
