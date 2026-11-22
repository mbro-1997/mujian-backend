package com.mujian.backend.infrastructure.crypto;

import com.mujian.backend.common.support.D2Codes;

import static com.mujian.backend.common.support.D2Values.*;

import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.*;
import javax.crypto.Cipher;
import javax.crypto.spec.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class VerificationCrypto {
  private final Environment env;
  private final SecureRandom random = new SecureRandom();

  public VerificationCrypto(Environment e) {
    env = e;
  }

  /** 返回当前认证加密密钥版本。 */
  public String version() {
    String v = env.getProperty("mj.verification.crypto.active-version", "");
    if (!v.matches("[A-Za-z0-9_-]{1,64}")) throw unavailable(D2Codes.CODE_CRYPTO_KEY_UNAVAILABLE);
    return v;
  }

  /** 读取对应版本的认证加密密钥。 */
  private SecretKey key(String v) {
    try {
      byte[] k =
          Base64.getDecoder().decode(env.getRequiredProperty("mj.verification.crypto.keys." + v));
      if (k.length != 32) throw new IllegalArgumentException();
      return new SecretKeySpec(k, "AES");
    } catch (Exception e) {
      throw unavailable(D2Codes.CODE_CRYPTO_KEY_UNAVAILABLE, e);
    }
  }

  /** 构造认证材料加密的附加验证数据。 */
  private byte[] aad(String id, String field, String version) {
    return ("user_realname_verification|" + id + "|" + field + "|" + version)
        .getBytes(StandardCharsets.UTF_8);
  }

  /** 使用当前密钥加密敏感字段。 */
  public byte[] encrypt(String id, String field, String version, String value) {
    try {
      byte[] iv = new byte[12];
      random.nextBytes(iv);
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(Cipher.ENCRYPT_MODE, key(version), new GCMParameterSpec(128, iv));
      c.updateAAD(aad(id, field, version));
      byte[] encrypted = c.doFinal(value.getBytes(StandardCharsets.UTF_8));
      return ByteBuffer.allocate(13 + encrypted.length)
          .put((byte) 1)
          .put(iv)
          .put(encrypted)
          .array();
    } catch (Exception e) {
      throw unavailable(D2Codes.CODE_CRYPTO_KEY_UNAVAILABLE, e);
    }
  }

  /** 按记录的版本解密敏感字段。 */
  public String decrypt(String id, String field, String version, byte[] value) {
    try {
      if (value.length < 30 || value[0] != 1) throw new IllegalArgumentException();
      Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
      c.init(
          Cipher.DECRYPT_MODE,
          key(version),
          new GCMParameterSpec(128, Arrays.copyOfRange(value, 1, 13)));
      c.updateAAD(aad(id, field, version));
      return new String(
          c.doFinal(Arrays.copyOfRange(value, 13, value.length)), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw unavailable(D2Codes.CODE_CRYPTO_KEY_UNAVAILABLE, e);
    }
  }
}
