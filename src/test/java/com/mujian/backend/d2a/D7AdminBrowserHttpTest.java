package com.mujian.backend.d2a;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.common.support.Ids;
import java.io.OutputStream;
import java.util.*;
import org.junit.jupiter.api.*;

class D7AdminBrowserHttpTest {
  final D3BQuoteTest rental = new D3BQuoteTest();
  final D2ApplicationTest f = rental.f;

  /** 初始化本组隔离依赖。 */
  @BeforeEach
  void start() throws Exception {
    Assumptions.assumeTrue(System.getenv("MJ_TEST_CONFIG") != null);
    assertTrue(
        java.nio.file.Files.readString(java.nio.file.Path.of(System.getenv("MJ_TEST_CONFIG")))
            .contains(":33317/darkmujian_d1_test_d4"));
    rental.start();
  }

  /** 释放本组创建的资源。 */
  @AfterEach
  void stop() {
    if (f.app != null) rental.stop();
  }

  /** 通过浏览器验证后台登录和运营页面。 */
  @Test
  void browserLoginAndOperationalPages() throws Exception {
    rental.paidOrder();
    String usage =
        f.db.queryForObject(
            "SELECT id FROM coupon_usage WHERE user_coupon_id=?", String.class, rental.coupon);
    String ipHash =
        java.util.HexFormat.of()
            .formatHex(
                java.security.MessageDigest.getInstance("SHA-256")
                    .digest("127.0.0.1".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    f.app
        .getBean(org.springframework.data.redis.core.StringRedisTemplate.class)
        .delete("mujian:test:{login-budget}:ip:failed:" + ipHash);
    String name = "d7." + Ids.next(), password = UUID.randomUUID().toString();
    f.app
        .getBean(com.mujian.backend.application.service.admin.AdminInitializationService.class)
        .initialize(name, password);
    Process process =
        new ProcessBuilder("node", "../mujian-frontend/tests/d7-admin-browser.cjs")
            .redirectErrorStream(true)
            .redirectOutput(new java.io.File("target/d7/browser-actions.log"))
            .start();
    try (OutputStream writer = process.getOutputStream()) {
      writer.write(
          f.json.writeValueAsBytes(
              Map.of(
                  "usage",
                  Map.of("usageId", usage, "couponId", rental.coupon, "owner", rental.buyer.id()),
                  "api",
                  f.base,
                  "username",
                  name,
                  "password",
                  password,
                  "out",
                  java.nio.file.Path.of("target/d7").toAbsolutePath().toString())));
    }
    try {
      assertTrue(process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(0, process.exitValue());
    } finally {
      process.destroyForcibly();
    }
  }
}
