package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.application.service.admin.AdminInitializationService;
import com.mujian.backend.common.support.Ids;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.JsonNode;

class D7AdminLoginHttpTest {
  private final D2ApplicationTest f = new D2ApplicationTest();

  /** 通过真实 Cookie/CSRF 登录，验证码明文仅从专用隔离 Redis 适配读取。 */
  @Test
  void boundConsumedOnceAndRefreshRetainsSession() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeTrue(System.getenv("MJ_TEST_CONFIG") != null);
    String conf =
        java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("MJ_TEST_CONFIG")));
    assertTrue(
        conf.contains(":33317/darkmujian_d1_test_d4")
            && conf.contains("spring.data.redis.port=16397"));
    f.start();
    try {
      String name = "d7." + Ids.next(), password = UUID.randomUUID().toString();
      f.app.getBean(AdminInitializationService.class).initialize(name, password);
      HttpResponse<String> anonymous = request("GET", "/api/admin/auth/csrf", null, null, null);
      String cookie = anonymous.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
      String csrf = f.json.readTree(anonymous.body()).at("/data/csrfToken").asString();
      String cache = "mj:test:admin-captcha:" + cookie.substring(cookie.indexOf('=') + 1);
      StringRedisTemplate redis = f.app.getBean(StringRedisTemplate.class);
      // 该测试进程仅允许独立 33317 / 16397 合成夹具，清理本机测试 IP 的上次失败窗口。
      String ipHash =
          java.util.HexFormat.of()
              .formatHex(
                  java.security.MessageDigest.getInstance("SHA-256")
                      .digest("127.0.0.1".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      redis.delete("mujian:test:{login-budget}:ip:failed:" + ipHash);
      String path = "/api/admin/auth/login";
      JsonNode old =
          f.json
              .readTree(request("GET", "/api/admin/auth/captcha", cookie, null, null).body())
              .path("data");
      JsonNode fresh =
          f.json
              .readTree(request("GET", "/api/admin/auth/captcha", cookie, null, null).body())
              .path("data");
      assertTrue(fresh.path("imageData").asString().startsWith("data:image/png;base64,"));
      assertEquals(3, fresh.size());
      assertEquals(
          "CAPTCHA_EXPIRED",
          reason(request("POST", path, cookie, csrf, body(name, password, old, "wrong"))));
      assertEquals(
          "CSRF_INVALID",
          reason(request("POST", path, cookie, "wrong", body(name, password, fresh, "wrong"))));
      assertEquals(
          "CAPTCHA_INVALID",
          reason(request("POST", path, cookie, csrf, body(name, password, fresh, "wrong"))));
      assertNull(redis.opsForValue().get(cache));
      JsonNode exp =
          f.json
              .readTree(request("GET", "/api/admin/auth/captcha", cookie, null, null).body())
              .path("data");
      assertTrue(redis.getExpire(cache) > 0 && redis.getExpire(cache) <= 120);
      redis.expire(cache, java.time.Duration.ofMillis(1));
      Thread.sleep(15);
      assertEquals(
          "CAPTCHA_EXPIRED",
          reason(request("POST", path, cookie, csrf, body(name, password, exp, "wrong"))));
      JsonNode challenge =
          f.json
              .readTree(request("GET", "/api/admin/auth/captcha", cookie, null, null).body())
              .path("data");
      String answer = Objects.requireNonNull(redis.opsForValue().get(cache)).substring(33);
      String data = body(name, password, challenge, answer);
      ExecutorService pool = Executors.newFixedThreadPool(2);
      try {
        CountDownLatch gate = new CountDownLatch(1);
        Callable<HttpResponse<String>> attempt =
            () -> {
              gate.await();
              return request("POST", path, cookie, csrf, data);
            };
        Future<HttpResponse<String>> first = pool.submit(attempt), second = pool.submit(attempt);
        gate.countDown();
        List<HttpResponse<String>> both = List.of(first.get(), second.get());
        assertEquals(1, both.stream().filter(x -> x.statusCode() == 200).count());
        assertTrue(
            Set.of("CAPTCHA_EXPIRED", "CSRF_INVALID")
                .contains(
                    reason(
                        both.stream()
                            .filter(x -> x.statusCode() != 200)
                            .findFirst()
                            .orElseThrow())));
        HttpResponse<String> ok =
            both.stream().filter(x -> x.statusCode() == 200).findFirst().orElseThrow();
        String valid = ok.headers().firstValue("set-cookie").orElseThrow().split(";")[0];
        assertNotEquals(cookie, valid);
        assertEquals(200, request("GET", "/api/admin/me", valid, null, null).statusCode());
        assertEquals(200, request("GET", "/api/admin/me", valid, null, null).statusCode());
        assertEquals(401, request("GET", "/api/admin/me", cookie, null, null).statusCode());
      } finally {
        pool.shutdownNow();
      }
    } finally {
      f.stop();
    }
  }

  /** 构造符合当前场景的请求正文。 */
  private String body(String name, String password, JsonNode challenge, String answer) {
    return f.json.writeValueAsString(
        Map.of(
            "username",
            name,
            "password",
            password,
            "captchaId",
            challenge.get("captchaId").asString(),
            "captchaAnswer",
            answer));
  }

  /** 读取返回的机器原因码。 */
  private String reason(HttpResponse<String> r) {
    return f.json.readTree(r.body()).path("reasonCode").asString();
  }

  /** 发送请求并收集测试响应。 */
  private HttpResponse<String> request(
      String method, String path, String cookie, String csrf, String body) throws Exception {
    HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(f.base + path));
    if (cookie != null) b.header("Cookie", cookie);
    if (csrf != null) b.header("X-CSRF-Token", csrf);
    if (body != null) b.header("Content-Type", "application/json");
    return f.http.send(
        b.method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
