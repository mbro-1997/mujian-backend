package com.mujian.backend.infrastructure.payment.wechat;

import static org.junit.jupiter.api.Assertions.*;

import com.mujian.backend.payment.dto.PaymentAccount;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.spec.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

/** 官方协议在受控HTTP服务器上验签验证；无真实资金或渠道调用。 */
public abstract class SignedWechatHttpFixture {
  @TempDir Path directory;
  KeyPair merchant, platform;
  Clock clock = Clock.fixed(Instant.parse("2026-11-07T09:00:00Z"), ZoneOffset.UTC);
  MockEnvironment environment;
  protected ObjectMapper json = JsonMapper.builder().build();
  HttpServer server;
  AtomicReference<String> lastBody = new AtomicReference<>(), lastPath = new AtomicReference<>();
  protected AtomicReference<String> reply =
      new AtomicReference<>("{\"prepay_id\":\"isolated-prepay\"}");
  boolean badSignature;
  int replyStatus = 200;
  protected WechatPaymentGateway gateway;
  WechatPaymentCrypto crypto;
  protected PaymentAccount account;

  protected final List<String> requests = new java.util.concurrent.CopyOnWriteArrayList<>();

  /** 初始化本组隔离依赖。 */
  @BeforeEach
  protected void start() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    merchant = generator.generateKeyPair();
    platform = generator.generateKeyPair();
    Files.writeString(
        directory.resolve("private.pem"), pem("PRIVATE KEY", merchant.getPrivate().getEncoded()));
    Files.writeString(
        directory.resolve("public.pem"), pem("PUBLIC KEY", platform.getPublic().getEncoded()));
    environment =
        new MockEnvironment()
            .withProperty("mj.payment.wechat.merchant-serial", "TESTMERCHANT")
            .withProperty(
                "mj.payment.wechat.private-key-file", directory.resolve("private.pem").toString())
            .withProperty("mj.payment.wechat.platform-serial", "PUB_KEY_ID_TEST")
            .withProperty(
                "mj.payment.wechat.platform-keys.PUB_KEY_ID_TEST",
                directory.resolve("public.pem").toString())
            .withProperty("mj.payment.wechat.api-v3-key", "0123456789abcdef0123456789abcdef")
            .withProperty("mj.payment.wechat.app-id", "wx-test")
            .withProperty("mj.payment.wechat.mch-id", "190000001")
            .withProperty(
                "mj.payment.wechat.notify-url",
                "https://example.invalid/api/wechat-pay/notifications/payment");
    crypto = new WechatPaymentCrypto(environment, clock);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          try {
            String
                body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                path = exchange.getRequestURI().toString();
            requests.add(exchange.getRequestMethod() + " " + path);
            lastBody.set(body);
            lastPath.set(path);
            String authorization = exchange.getRequestHeaders().getFirst("Authorization");
            Map<String, String> values = new HashMap<>();
            java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("([a-z_]+)=\"([^\"]*)\"").matcher(authorization);
            while (matcher.find()) values.put(matcher.group(1), matcher.group(2));
            Signature verify = Signature.getInstance("SHA256withRSA");
            verify.initVerify(merchant.getPublic());
            verify.update(
                (exchange.getRequestMethod()
                        + "\n"
                        + path
                        + "\n"
                        + values.get("timestamp")
                        + "\n"
                        + values.get("nonce_str")
                        + "\n"
                        + body
                        + "\n")
                    .getBytes(StandardCharsets.UTF_8));
            assertTrue(verify.verify(Base64.getDecoder().decode(values.get("signature"))));
            byte[] response = reply.get().getBytes(StandardCharsets.UTF_8);
            String timestamp = Long.toString(clock.instant().getEpochSecond()),
                nonce = "response-nonce";
            exchange.getResponseHeaders().add("Wechatpay-Timestamp", timestamp);
            exchange.getResponseHeaders().add("Wechatpay-Nonce", nonce);
            exchange.getResponseHeaders().add("Wechatpay-Serial", "PUB_KEY_ID_TEST");
            exchange
                .getResponseHeaders()
                .add(
                    "Wechatpay-Signature",
                    sign(
                        platform.getPrivate(),
                        timestamp
                            + "\n"
                            + nonce
                            + "\n"
                            + (badSignature ? "tampered" : reply.get())
                            + "\n"));
            exchange.sendResponseHeaders(replyStatus, replyStatus == 204 ? -1 : response.length);
            if (replyStatus != 204) exchange.getResponseBody().write(response);
          } catch (Exception exception) {
            throw new RuntimeException(exception);
          } finally {
            exchange.close();
          }
        });
    server.start();
    gateway =
        new WechatPaymentGateway(
            environment,
            json,
            clock,
            crypto,
            URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    account = gateway.requireReady();
  }

  /** 释放本组创建的资源。 */
  @AfterEach
  protected void stop() {
    if (server != null) server.stop(0);
  }

  /** 生成用于测试的 PEM 密钥文本。 */
  String pem(String kind, byte[] bytes) {
    return "-----BEGIN "
        + kind
        + "-----\n"
        + Base64.getMimeEncoder().encodeToString(bytes)
        + "\n-----END "
        + kind
        + "-----";
  }

  /** 生成测试访问凭据。 */
  String sign(PrivateKey key, String value) throws Exception {
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(key);
    signature.update(value.getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(signature.sign());
  }
}
