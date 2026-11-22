package com.mujian.backend.infrastructure.wechat;

import com.mujian.backend.common.security.D2RateLimiter;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import tools.jackson.databind.ObjectMapper;

/** Controlled local upstream, with the real JDK HTTP client and the production response mapping. */
public final class WechatHttpFixture implements AutoCloseable {
  private final HttpServer server;
  private final ExecutorService executor = Executors.newCachedThreadPool();
  private final CountDownLatch timeoutRelease = new CountDownLatch(1);
  private final String id = UUID.randomUUID().toString();
  private final String stage, fault;
  private final Map<String, Integer> requests = new ConcurrentHashMap<>();

  public WechatHttpFixture(String stage, String fault) throws IOException {
    this.stage = stage;
    this.fault = fault;
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setExecutor(executor);
    server.createContext(
        "/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          String actualStage =
              switch (path) {
                case "/sns/jscode2session" -> "login";
                case "/cgi-bin/token" -> "token";
                case "/wxa/business/getuserphonenumber" -> "phone";
                default -> "unexpected";
              };
          requests.merge(actualStage, 1, Integer::sum);
          // Never retain query strings, request bodies, tokens or credentials.
          exchange.getRequestBody().readAllBytes();
          int status = 200;
          String body =
              switch (actualStage) {
                case "login" -> "{\"openid\":\"fictional-" + id + "\"}";
                case "token" -> "{\"access_token\":\"fictional-token\",\"expires_in\":7200}";
                case "phone" ->
                    "{\"phone_info\":{\"countryCode\":\"86\",\"purePhoneNumber\":\"000"
                        + String.format("%08d", Math.abs((long) id.hashCode()) % 100000000L)
                        + "\",\"watermark\":{\"appid\":\"c16-fixture-app\"}}}";
                default -> "{}";
              };
          if (actualStage.equals(stage)) {
            switch (fault) {
              case "http500" -> {
                status = 500;
                body = "{}";
              }
              case "bad_json" -> body = "not-json";
              case "too_large" -> body = "x".repeat(65537);
              case "credential_error" -> body = "{\"errcode\":40029}";
              case "upstream_error" -> body = "{\"errcode\":45009}";
              case "missing_fields" -> body = "{}";
              case "wrong_appid" -> body = body.replace("c16-fixture-app", "wrong-app");
              case "timeout" -> {
                try {
                  timeoutRelease.await(3, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }
              case "disconnect" -> {
                exchange.close();
                return;
              }
              default -> {}
            }
          }
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          try {
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
          } catch (IOException ignored) {
            // A timed-out client has closed the socket; this is the intended fixture outcome.
          } finally {
            exchange.close();
          }
        });
    server.start();
  }

  /** 装配隔离渠道适配器。 */
  public WechatGateway gateway(ObjectMapper json, D2RateLimiter limiter) {
    return new OfficialWechatGateway(
        "c16-fixture-app",
        "fictional-secret",
        json,
        limiter,
        URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
        Duration.ofMillis(250));
  }

  /** 读取本次请求计数。 */
  public Map<String, Integer> requestCounts() {
    return Map.copyOf(requests);
  }

  /** 关闭夹具持有的资源。 */
  @Override
  public void close() {
    timeoutRelease.countDown();
    server.stop(0);
    executor.shutdownNow();
  }
}
