package com.mujian.backend.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.MujianBackendApplication;
import com.mujian.backend.common.security.SessionService;
import com.mujian.backend.common.security.SessionService.Handle;
import java.io.*;
import java.io.InputStream;
import java.net.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.http.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.*;
import java.util.Properties;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "MJ_TEST_CONFIG", matches = ".+")
class ServiceFailureIntegrationTest {
  static final class Proxy implements AutoCloseable {
    final int target;
    int port;
    ServerSocket server;
    final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    final ExecutorService threads = Executors.newCachedThreadPool();

    Proxy(int target) throws Exception {
      this.target = target;
      start();
    }

    /** 初始化本组隔离依赖。 */
    void start() throws Exception {
      ServerSocket listener = new ServerSocket();
      listener.setReuseAddress(true);
      listener.bind(new InetSocketAddress("127.0.0.1", port));
      port = listener.getLocalPort();
      server = listener;
      threads.submit(
          () -> {
            while (!listener.isClosed()) {
              try {
                Socket a = listener.accept();
                Socket b = new Socket("127.0.0.1", target);
                sockets.add(a);
                sockets.add(b);
                threads.submit(() -> pump(a, b));
                threads.submit(() -> pump(b, a));
              } catch (IOException e) {
                break;
              }
            }
          });
    }

    /** 驱动测试任务继续处理。 */
    void pump(Socket a, Socket b) {
      try {
        a.getInputStream().transferTo(b.getOutputStream());
      } catch (IOException ignored) {
      } finally {
        try {
          a.close();
          b.close();
        } catch (IOException ignored) {
        }
        sockets.remove(a);
        sockets.remove(b);
      }
    }

    /** 在指定边界截断或中止输入。 */
    void cut() throws Exception {
      server.close();
      for (Socket s : sockets) s.close();
      sockets.clear();
    }

    /** 关闭夹具持有的资源。 */
    public void close() throws Exception {
      cut();
      threads.shutdownNow();
    }
  }

  /** 模拟网络断开并检查 503 及恢复后的身份。 */
  @Test
  void realNetworkDisconnectsReturn503AndRecoverWithoutClientIdentityFallback() throws Exception {
    com.mujian.backend.support.TestDatabase.prepare();
    Properties p = new Properties();
    try (InputStream in = Files.newInputStream(Path.of(System.getenv("MJ_TEST_CONFIG")))) {
      p.load(in);
    }
    assertTrue(p.getProperty("spring.datasource.url").contains("/darkmujian_d1_test_"));
    try (ServiceFailureIntegrationTest.Proxy mysql = new Proxy(3306);
        ServiceFailureIntegrationTest.Proxy redis = new Proxy(6379)) {
      String url =
          p.getProperty("spring.datasource.url").replace(":3306/", ":" + mysql.port + "/")
              + "&connectTimeout=1000&socketTimeout=1000";
      try (ConfigurableApplicationContext ctx =
          new SpringApplication(MujianBackendApplication.class)
              .run(
                  "--spring.config.additional-location="
                      + Path.of(System.getenv("MJ_TEST_CONFIG")).toUri(),
                  "--spring.datasource.url=" + url,
                  "--spring.data.redis.port=" + redis.port,
                  "--spring.data.redis.timeout=1s",
                  "--spring.data.redis.connect-timeout=1s",
                  "--spring.datasource.hikari.connection-timeout=1000",
                  "--spring.datasource.hikari.minimum-idle=0",
                  "--server.port=0",
                  "--mj.prop.qr-worker-enabled=false",
                  "--mj.environment=test")) {
        SessionService sessionService = ctx.getBean(SessionService.class);
        JdbcTemplate jdbc = ctx.getBean(JdbcTemplate.class);
        ObjectMapper json = ctx.getBean(ObjectMapper.class);
        HttpClient client = HttpClient.newHttpClient();
        String base =
            "http://127.0.0.1:" + ((WebServerApplicationContext) ctx).getWebServer().getPort();
        String username = "fault." + com.mujian.backend.common.support.Ids.next();
        ctx.getBean(com.mujian.backend.application.service.admin.AdminInitializationService.class)
            .initialize(username, UUID.randomUUID().toString());
        String user =
            jdbc.queryForObject(
                "SELECT user_id FROM admin_account WHERE username=?", String.class, username);
        Handle handle = sessionService.create(user, "admin");
        HttpRequest request =
            HttpRequest.newBuilder(URI.create(base + "/api/admin/me"))
                .header("Cookie", sessionService.cookieName() + "=" + handle.session().getId())
                .timeout(java.time.Duration.ofSeconds(10))
                .GET()
                .build();
        assertEquals(200, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
        mysql.cut();
        HttpResponse<String> dbFailure = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(503, dbFailure.statusCode());
        assertEquals(
            "DATABASE_UNAVAILABLE", json.readTree(dbFailure.body()).path("reasonCode").asString());
        mysql.start();
        // TCP代理恢复不等于池中旧连接已全部淘汰。只在隔离测试等待恢复，不给业务请求加自动重试。
        long recoverBefore = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        HttpResponse<String> recovered;
        do {
          recovered = client.send(request, HttpResponse.BodyHandlers.ofString());
          if (recovered.statusCode() == 200) break;
          assertEquals(503, recovered.statusCode());
          assertEquals(
              "DATABASE_UNAVAILABLE",
              json.readTree(recovered.body()).path("reasonCode").asString());
          Thread.sleep(100);
        } while (System.nanoTime() < recoverBefore);
        assertEquals(200, recovered.statusCode());
        redis.cut();
        HttpResponse<String> redisFailure = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(503, redisFailure.statusCode());
        assertEquals(
            "SESSION_STORE_UNAVAILABLE",
            json.readTree(redisFailure.body()).path("reasonCode").asString());
        redis.start();
        // 恢复后只使用原有服务端会话，不接受 userId 充当新凭据。
        HttpRequest forged =
            HttpRequest.newBuilder(URI.create(base + "/api/me/context"))
                .header("Authorization", "Bearer " + user)
                .GET()
                .build();
        assertEquals(401, client.send(forged, HttpResponse.BodyHandlers.ofString()).statusCode());
        sessionService.revoke(handle.session().getId());
        com.mujian.backend.support.TestDatabase.removeAdmin(jdbc, username);
        System.out.println(
            "SESSION-04 PASS: real TCP disconnect DB=>503 DATABASE_UNAVAILABLE; Redis=>503"
                + " SESSION_STORE_UNAVAILABLE; recovery never accepts userId as credential");
      }
    }
  }
}
