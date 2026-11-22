package com.mujian.backend.common.support;

import static com.mujian.backend.common.support.D2Codes.*;

import com.mujian.backend.common.api.ApiFailure;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.LinkedHashMap;

public final class D2Values {
  public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  /** 取统一业务时钟的当前时间。 */
  public static LocalDateTime now(Clock clock) {
    return LocalDateTime.ofInstant(clock.instant(), ZONE)
        .truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
  }

  /** 将业务时间转换为接口格式。 */
  public static String time(LocalDateTime t) {
    return t == null
        ? null
        : t.atOffset(ZoneOffset.ofHours(8))
            .format(DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX"));
  }

  /** 校验并读取业务主键。 */
  public static String id(String s) {
    if (s == null || !s.matches("[0-9a-f]{12}4[0-9a-f]{3}[89ab][0-9a-f]{15}"))
      throw ApiFailure.invalid();
    return s;
  }

  /** 校验输入文本及其长度。 */
  public static String text(String s, int max) {
    if (s == null) throw ApiFailure.invalid();
    s = s.trim();
    if (s.isEmpty() || s.codePointCount(0, s.length()) > max) throw ApiFailure.invalid();
    return s;
  }

  /** 生成可展示的脱敏号码。 */
  public static String maskPhone(String s) {
    return s == null
        ? "未绑定"
        : s.substring(0, Math.min(3, s.length()))
            + "****"
            + s.substring(Math.max(3, s.length() - 4));
  }

  /** 按字段顺序构造接口展示数据。 */
  public static Map<String, Object> view(Object... pairs) {
    if (pairs == null || pairs.length % 2 != 0)
      throw new IllegalArgumentException("view requires key/value pairs");
    LinkedHashMap<String, Object> m = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) {
      if (!(pairs[i] instanceof String key) || key.isBlank())
        throw new IllegalArgumentException("view key must be a nonblank String at index " + i);
      if (m.containsKey(key))
        throw new IllegalArgumentException("duplicate view key at index " + i);
      m.put(key, pairs[i + 1]);
    }
    return m;
  }

  /** 生成携带业务原因的状态冲突。 */
  public static ApiFailure conflict(String r) {
    return new ApiFailure(409, CODE_STATE_CONFLICT, r);
  }

  /** 报告依赖能力暂不可用。 */
  public static ApiFailure unavailable(String r) {
    return new ApiFailure(503, CODE_SERVICE_UNAVAILABLE, r);
  }

  /** 报告依赖能力暂不可用。 */
  public static ApiFailure unavailable(String reason, Throwable cause) {
    return unavailable(reason).causedBy(cause);
  }

  /** 报告指定资源不存在或不可见。 */
  public static ApiFailure missing() {
    return new ApiFailure(404, CODE_NOT_FOUND, CODE_RESOURCE_NOT_FOUND);
  }
}
