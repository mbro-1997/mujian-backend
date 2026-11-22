package com.mujian.backend.infrastructure.wechat;

import com.mujian.backend.common.enums.BusinessFailureReason;
import static com.mujian.backend.common.support.D2Codes.*;
import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.security.D2RateLimiter;
import java.net.*;
import java.net.http.*;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.*;
import tools.jackson.databind.JsonNode;

/** Official endpoints only. Codes are never retried or logged. */
@Component
public class OfficialWechatGateway implements WechatGateway {
  private final String appId, secret;
  private final ObjectMapper json;
  private final D2RateLimiter limiter;
  private final URI origin;
  private final Duration requestTimeout;

  @Value("${mj.wechat.qr-check-path:true}")
  private boolean qrCheckPath = true;

  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(3))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();
  private String accessToken;
  private Instant tokenUntil = Instant.EPOCH;

  @Autowired
  public OfficialWechatGateway(
      @Value("${mj.wechat.app-id:}") String a,
      @Value("${mj.wechat.app-secret:}") String s,
      ObjectMapper j,
      D2RateLimiter l) {
    this(a, s, j, l, URI.create("https://api.weixin.qq.com"), Duration.ofSeconds(5));
  }

  // Package-scoped test seam; production Spring wiring always uses the official HTTPS origin.
  OfficialWechatGateway(
      String a, String s, ObjectMapper j, D2RateLimiter l, URI origin, Duration requestTimeout) {
    boolean official = origin.equals(URI.create("https://api.weixin.qq.com"));
    boolean loopback =
        "http".equals(origin.getScheme())
            && "127.0.0.1".equals(origin.getHost())
            && origin.getPort() > 0
            && origin.getRawQuery() == null
            && origin.getRawUserInfo() == null
            && origin.getRawFragment() == null
            && origin.getPath().isEmpty();
    if (!official && !loopback) throw new IllegalArgumentException("Unsupported WeChat origin");
    if (requestTimeout.isNegative() || requestTimeout.isZero())
      throw new IllegalArgumentException("Positive request timeout required");
    this.origin = origin;
    this.requestTimeout = requestTimeout;
    appId = a;
    secret = s;
    json = j;
    limiter = l;
  }

  /** 将参数编码为可传输的 URL 值。 */
  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  /** 发起请求并核对渠道响应。 */
  private JsonNode call(String url, Object body) {
    try {
      Builder r = HttpRequest.newBuilder(URI.create(url)).timeout(requestTimeout);
      if (body != null)
        r.header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
      HttpResponse<String> response = client.send(r.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200 || response.body().length() > 65536)
        throw unavailable(CODE_WECHAT_UPSTREAM_UNAVAILABLE);
      JsonNode n = json.readTree(response.body());
      int code = n.path("errcode").asInt(0);
      if (code != 0) {
        if (Set.of(40029, 40163, 40013).contains(code))
          throw new ApiFailure(400, CODE_INVALID_ARGUMENT, CODE_WECHAT_CREDENTIAL_INVALID);
        throw unavailable(CODE_WECHAT_UPSTREAM_UNAVAILABLE);
      }
      return n;
    } catch (ApiFailure e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw unavailable(CODE_WECHAT_UPSTREAM_UNAVAILABLE, e);
    } catch (Exception e) {
      throw unavailable(CODE_WECHAT_UPSTREAM_UNAVAILABLE, e);
    }
  }

  /** 获取后续调用所需的渠道令牌。 */
  private synchronized String token() {
    if (accessToken != null && Instant.now().isBefore(tokenUntil)) return accessToken;
    JsonNode n =
        call(
            origin
                + "/cgi-bin/token?grant_type=client_credential&appid="
                + enc(appId)
                + "&secret="
                + enc(secret),
            null);
    String t = n.path("access_token").asString("");
    int ttl = n.path("expires_in").asInt(0);
    if (t.isEmpty() || ttl <= 120) throw unavailable(CODE_WECHAT_UPSTREAM_UNAVAILABLE);
    accessToken = t;
    tokenUntil = Instant.now().plusSeconds(ttl - 120);
    return t;
  }

  /** 为指定场景生成小程序码。 */
  @Override
  public byte[] miniCode(String scene, String page, String environment) {
    if (appId.isBlank()
        || secret.isBlank()
        || !scene.matches("[0-9a-f]{32}")
        || !page.equals("pages/props/scan/index")
        || !Set.of("release", "trial", "develop").contains(environment))
      throw unavailable(BusinessFailureReason.QR_GENERATION_UNAVAILABLE.name());
    try {
      HttpRequest request =
          HttpRequest.newBuilder(
                  URI.create(origin + "/wxa/getwxacodeunlimit?access_token=" + enc(token())))
              .timeout(requestTimeout)
              .header("Content-Type", "application/json")
              .POST(
                  HttpRequest.BodyPublishers.ofString(
                      json.writeValueAsString(
                          Map.of(
                              "scene",
                              scene,
                              "page",
                              page,
                              "env_version",
                              environment,
                              "check_path",
                              qrCheckPath,
                              "width",
                              430))))
              .build();
      HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
      byte[] data = response.body();
      if (response.statusCode() != 200
          || !response
              .headers()
              .firstValue("Content-Type")
              .orElse("")
              .toLowerCase(Locale.ROOT)
              .startsWith("image/")
          || data.length > 5 * 1024 * 1024) throw unavailable(BusinessFailureReason.QR_GENERATION_UNAVAILABLE.name());
      return com.mujian.backend.infrastructure.wechat.QrImage.png(data);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw unavailable(BusinessFailureReason.QR_GENERATION_UNAVAILABLE.name(), e);
    } catch (Exception e) {
      throw unavailable(BusinessFailureReason.QR_GENERATION_UNAVAILABLE.name(), e);
    }
  }

  private JsonNode loginIdentity(String loginCode) {
    if (appId.isBlank() || secret.isBlank()) throw unavailable(CODE_WECHAT_UPSTREAM_UNAVAILABLE);
    JsonNode w =
        call(
            origin
                + "/sns/jscode2session?grant_type=authorization_code&appid="
                + enc(appId)
                + "&secret="
                + enc(secret)
                + "&js_code="
                + enc(loginCode),
            null);
    String openid = w.path("openid").asString("");
    if (openid.isBlank() || openid.length() > 128)
      throw unavailable(CODE_WECHAT_UPSTREAM_UNAVAILABLE);
    return w;
  }

  /** 校验当前小程序的一次性微信登录码，供本地开发工具核对开发者身份。 */
  public String exchangeOpenId(String loginCode) {
    return loginIdentity(loginCode).path("openid").asString();
  }

  /** 使用登录凭据换取微信身份。 */
  public Identity exchange(String loginCode, String phoneCode) {
    JsonNode w = loginIdentity(loginCode);
    String openid = w.path("openid").asString();
    limiter.check("identity", appId + ":" + openid, 10);
    JsonNode p =
        call(
                origin + "/wxa/business/getuserphonenumber?access_token=" + enc(token()),
                Map.of("code", phoneCode))
            .path("phone_info");
    String country = p.path("countryCode").asString("");
    String pure = p.path("purePhoneNumber").asString("");
    if (!appId.equals(p.path("watermark").path("appid").asString())
        || !country.matches("[1-9][0-9]{0,3}")
        || !pure.matches("[0-9]+")) throw unavailable(CODE_WECHAT_UPSTREAM_UNAVAILABLE);
    String phone = "+" + country + pure;
    if (!phone.matches("[+][1-9][0-9]{1,14}")) throw unavailable(CODE_WECHAT_UPSTREAM_UNAVAILABLE);
    String union = w.path("unionid").asString(null);
    return new Identity(appId, openid, union, phone, "+" + country);
  }
}
