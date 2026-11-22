package com.mujian.backend.infrastructure.payment.wechat;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.enums.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.spec.*;
import java.time.Clock;
import java.util.*;
import java.util.Date;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 商户私钥签名、按serial选择公钥验签及APIv3解密；不向诊断附加密钥或原文。 */
@Component
public class WechatPaymentCrypto {
  private final Environment environment;
  private final Clock clock;

  /** 只读取既有安全配置，支持新旧平台公钥并存轮换。 */
  public WechatPaymentCrypto(Environment environment, Clock clock) {
    this.environment = environment;
    this.clock = clock;
  }

  /** 商户证书序列号用于请求认证，内容只允许稳定编码。 */
  public String merchantSerial() {
    String serial = environment.getProperty("mj.payment.wechat.merchant-serial", "");
    if (!serial.matches("[A-Za-z0-9_-]{1,128}"))
      throw unavailable(TradingReason.PAYMENT_CHANNEL_NOT_READY.name());
    return serial;
  }

  /** 明确验证签名、验签、解密配置都齐备，避免已占用后才发现基础配置缺失。 */
  public void requireReady() {
    merchantSerial();
    sign("configuration-check");
    apiKey();
    String serial = environment.getProperty("mj.payment.wechat.platform-serial", "");
    publicKey(serial);
  }

  /** PKCS8商户RSA私钥仅在进程中使用。 */
  public String sign(String message) {
    try {
      String pem =
          Files.readString(
              Path.of(environment.getRequiredProperty("mj.payment.wechat.private-key-file")));
      byte[] der =
          Base64.getDecoder()
              .decode(
                  pem.replace("-----BEGIN PRIVATE KEY-----", "")
                      .replace("-----END PRIVATE KEY-----", "")
                      .replaceAll("\\s", ""));
      PrivateKey privateKey =
          KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initSign(privateKey);
      signature.update(message.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(signature.sign());
    } catch (Exception exception) {
      throw unavailable(TradingReason.PAYMENT_CHANNEL_NOT_READY.name());
    }
  }

  /** 验证未经改写的原始字节，拒绝过期头、未知serial和探测签名。 */
  public void verify(PaymentGateway.Headers headers, byte[] body) {
    try {
      if (headers.timestamp() == null
          || !headers.timestamp().matches("[0-9]{10}")
          || Math.abs(clock.instant().getEpochSecond() - Long.parseLong(headers.timestamp())) > 300
          || headers.nonce() == null
          || headers.nonce().length() > 256
          || headers.signature() == null
          || headers.signature().length() > 1024) throw new GeneralSecurityException();
      Signature signature = Signature.getInstance("SHA256withRSA");
      signature.initVerify(publicKey(headers.serial()));
      signature.update(
          (headers.timestamp() + "\n" + headers.nonce() + "\n").getBytes(StandardCharsets.UTF_8));
      signature.update(body);
      signature.update((byte) '\n');
      if (!signature.verify(Base64.getDecoder().decode(headers.signature())))
        throw new GeneralSecurityException();
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    }
  }

  /** 平台公钥ID或有效平台证书serial精确匹配配置，不根据来报地址下载任意公钥。 */
  private PublicKey publicKey(String serial) {
    try {
      if (serial == null || !serial.matches("[A-Za-z0-9_-]{1,128}"))
        throw new GeneralSecurityException();
      String pem =
          Files.readString(
              Path.of(
                  environment.getRequiredProperty("mj.payment.wechat.platform-keys." + serial)));
      if (pem.contains("BEGIN CERTIFICATE")) {
        java.security.cert.X509Certificate certificate =
            (java.security.cert.X509Certificate)
                CertificateFactory.getInstance("X.509")
                    .generateCertificate(
                        new java.io.ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
        certificate.checkValidity(Date.from(clock.instant()));
        if (!certificate.getSerialNumber().toString(16).equalsIgnoreCase(serial))
          throw new GeneralSecurityException();
        return certificate.getPublicKey();
      }
      byte[] der =
          Base64.getDecoder()
              .decode(
                  pem.replace("-----BEGIN PUBLIC KEY-----", "")
                      .replace("-----END PUBLIC KEY-----", "")
                      .replaceAll("\\s", ""));
      return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    } catch (Exception exception) {
      throw unavailable(TradingReason.PAYMENT_CHANNEL_NOT_READY.name());
    }
  }

  /** 回调仅解密已验签的AEAD资源，AAD和nonce按原值参与认证。 */
  public byte[] decrypt(String nonce, String aad, String ciphertext) {
    try {
      Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
      cipher.init(
          Cipher.DECRYPT_MODE,
          new SecretKeySpec(apiKey(), "AES"),
          new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
      cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
      return cipher.doFinal(Base64.getDecoder().decode(ciphertext));
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw unavailable(TradingReason.PAYMENT_CHANNEL_UNAVAILABLE.name());
    }
  }

  /** APIv3固定32字节，仅用于通知解密，不复用登录或报价密钥。 */
  private byte[] apiKey() {
    String value = environment.getProperty("mj.payment.wechat.api-v3-key", "");
    byte[] key = value.getBytes(StandardCharsets.UTF_8);
    if (key.length != 32) throw unavailable(TradingReason.PAYMENT_CHANNEL_NOT_READY.name());
    return key;
  }
}
