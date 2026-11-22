package com.mujian.backend.order.service;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import com.mujian.backend.common.enums.*;
import com.mujian.backend.common.support.D2Codes;
import com.mujian.backend.order.domain.CancellationQuoteData;
import com.mujian.backend.order.domain.CheckoutQuoteData;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** 有版本、独立用途密钥和恒时签名的短期确认凭据；不输出到日志。 */
@Service
public class TradingTokens {
  private final Environment environment;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  /** 仅复用安全配置根密钥，签名使用按用途派生的新密钥。 */
  public TradingTokens(Environment environment, ObjectMapper objectMapper, Clock clock) {
    this.environment = environment;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  /** 报价无数据库落表，正文保持白名单并限制总大小。 */
  public String quote(CheckoutQuoteData checkoutQuoteData) {
    String kid = environment.getProperty("mj.verification.crypto.active-version", "");
    if (!kid.matches("[A-Za-z0-9_-]{1,64}"))
      throw unavailable(TradingReason.PAYMENT_CHANNEL_NOT_READY.name());
    String payload =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(objectMapper.writeValueAsBytes(checkoutQuoteData));
    String signed = kid + "." + payload;
    String token =
        signed
            + "."
            + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(sign(kid, "checkout-quote", signed));
    if (token.length() > 1048576)
      throw new ApiFailure(413, D2Codes.CODE_PAYLOAD_TOO_LARGE, D2Codes.CODE_PAYLOAD_TOO_LARGE);
    return token;
  }

  /** 在新请求入口验签和有效期；旧命令必须先查回执再调用此方法。 */
  public CheckoutQuoteData readQuote(String actor, String token) {
    if (token == null || token.length() > 1048576) throw ApiFailure.invalid();
    String[] parts = token.split("\\.", -1);
    if (parts.length != 3 || !parts[0].matches("[A-Za-z0-9_-]{1,64}")) throw ApiFailure.invalid();
    try {
      byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
      if (!MessageDigest.isEqual(
          sign(parts[0], "checkout-quote", parts[0] + "." + parts[1]), signature))
        throw ApiFailure.invalid();
      CheckoutQuoteData checkoutQuoteData =
          objectMapper.readValue(Base64.getUrlDecoder().decode(parts[1]), CheckoutQuoteData.class);
      if (checkoutQuoteData.schemaVersion() != 1 || !actor.equals(checkoutQuoteData.buyerId()))
        throw ApiFailure.invalid();
      if (!now(clock).isBefore(checkoutQuoteData.expiresAt()))
        throw conflict(TradingReason.QUOTE_EXPIRED.name());
      return checkoutQuoteData;
    } catch (IllegalArgumentException | tools.jackson.core.JacksonException exception) {
      throw ApiFailure.invalid();
    }
  }

  /** 取消与结算使用独立派生密钥，不能交叉作为确认凭据。 */
  public String cancellation(CancellationQuoteData cancellationQuoteDataData) {
    String kid = environment.getProperty("mj.verification.crypto.active-version", "");
    if (!kid.matches("[A-Za-z0-9_-]{1,64}"))
      throw unavailable(TradingReason.PAYMENT_CHANNEL_NOT_READY.name());
    String signed =
        kid
            + "."
            + Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(cancellationQuoteDataData));
    return signed
        + "."
        + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(sign(kid, "order-cancellation", signed));
  }

  /** 同号回执在本方法之前读取；新确认遇到过期或版本变化必须重新报价。 */
  public CancellationQuoteData readCancellation(String actor, String orderId, String token) {
    if (token == null || token.length() > 1048576) throw ApiFailure.invalid();
    String[] parts = token.split("\\.", -1);
    if (parts.length != 3 || !parts[0].matches("[A-Za-z0-9_-]{1,64}")) throw ApiFailure.invalid();
    try {
      if (!MessageDigest.isEqual(
          sign(parts[0], "order-cancellation", parts[0] + "." + parts[1]),
          Base64.getUrlDecoder().decode(parts[2]))) throw ApiFailure.invalid();
      CancellationQuoteData cancellationQuoteDataData =
          objectMapper.readValue(
              Base64.getUrlDecoder().decode(parts[1]), CancellationQuoteData.class);
      if (cancellationQuoteDataData.schemaVersion() != 1
          || !actor.equals(cancellationQuoteDataData.buyerId())
          || !orderId.equals(cancellationQuoteDataData.orderId())) throw ApiFailure.invalid();
      if (!now(clock).isBefore(cancellationQuoteDataData.expiresAt()))
        throw conflict(TradingReason.CANCELLATION_QUOTE_CHANGED.name());
      return cancellationQuoteDataData;
    } catch (IllegalArgumentException | tools.jackson.core.JacksonException exception) {
      throw ApiFailure.invalid();
    }
  }

  /** 只携带版本、到期秒和签名，签名绑定查看人、批次与同一事务的资金摘要。 */
  public String settlement(String actor, String batch, String digest) {
    String kid = environment.getProperty("mj.verification.crypto.active-version", "");
    if (!kid.matches("[A-Za-z0-9_-]{1,64}")) throw unavailable(D2Codes.CODE_CRYPTO_KEY_UNAVAILABLE);
    String prefix = kid + "." + clock.instant().plusSeconds(600).getEpochSecond();
    return prefix
        + "."
        + Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                sign(kid, "settlement-ack", prefix + "|" + actor + "|" + batch + "|" + digest));
  }

  /** 核对结算令牌与当前请求是否一致。 */
  public void verifySettlement(String actor, String batch, String digest, String token) {
    if (token == null || !token.matches("[A-Za-z0-9_-]{1,64}\\.[0-9]{1,12}\\.[A-Za-z0-9_-]{43}"))
      throw conflict(FulfillmentReason.SETTLEMENT_SNAPSHOT_CHANGED.name());
    String[] p = token.split("\\.");
    if (!environment.containsProperty("mj.verification.crypto.keys." + p[0]))
      throw conflict(FulfillmentReason.SETTLEMENT_SNAPSHOT_CHANGED.name());
    byte[] expected =
        sign(p[0], "settlement-ack", p[0] + "." + p[1] + "|" + actor + "|" + batch + "|" + digest);
    if (!MessageDigest.isEqual(expected, Base64.getUrlDecoder().decode(p[2])))
      throw conflict(FulfillmentReason.SETTLEMENT_SNAPSHOT_CHANGED.name());
    if (clock.instant().getEpochSecond() >= Long.parseLong(p[1]))
      throw conflict(FulfillmentReason.SETTLEMENT_SNAPSHOT_EXPIRED.name());
  }

  /** 密钥先做用途隔离，再签名kid和原始载荷，历史kid可用于短期恢复。 */
  private byte[] sign(String kid, String purpose, String payload) {
    try {
      byte[] master =
          Base64.getDecoder()
              .decode(environment.getRequiredProperty("mj.verification.crypto.keys." + kid));
      if (master.length != 32) throw new IllegalArgumentException();
      Mac derive = Mac.getInstance("HmacSHA256");
      derive.init(new SecretKeySpec(master, "HmacSHA256"));
      byte[] key = derive.doFinal(("mujian:d3b:v1:" + purpose).getBytes(StandardCharsets.UTF_8));
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw unavailable(TradingReason.PAYMENT_CHANNEL_NOT_READY.name());
    }
  }
}
