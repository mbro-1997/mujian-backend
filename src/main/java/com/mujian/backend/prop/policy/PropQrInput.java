package com.mujian.backend.prop.policy;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 与既有小程序扫码入口一致，只接受平台scene或平台业务码，不访问码内URL。 */
public final class PropQrInput {
  private static final Pattern PATH = Pattern.compile("^/?pages/props/scan/index\\?scene=([^&]+)$");

  /** 仅承载本领域的结构定义或纯计算。 */
  private PropQrInput() {}

  /** 解码一次并严格校验UUIDv4，不接受任意SN或外部网址。 */
  public static String parse(String raw) {
    if (raw == null) throw new IllegalArgumentException("invalid qr");
    Matcher matcher = PATH.matcher(raw);
    String code =
        URLDecoder.decode(matcher.matches() ? matcher.group(1) : raw, StandardCharsets.UTF_8);
    if (!code.matches("[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}"))
      throw new IllegalArgumentException("invalid qr");
    return code;
  }
}
