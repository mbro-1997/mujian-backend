package com.mujian.backend.realname.policy;

import static com.mujian.backend.common.support.D2Values.*;

import com.mujian.backend.common.api.ApiFailure;
import java.time.*;
import java.util.*;

public final class RealnamePolicy {
  /** 校验并规范化申请姓名。 */
  public static String name(String value) {
    return text(value, 64);
  }

  /** 校验证件格式、出生日期和校验位。 */
  public static String card(String value) {
    String s = text(value, 18).toUpperCase(Locale.ROOT);
    if (!s.matches("[0-9]{17}[0-9X]")) throw ApiFailure.invalid();
    try {
      LocalDate.of(
          Integer.parseInt(s.substring(6, 10)),
          Integer.parseInt(s.substring(10, 12)),
          Integer.parseInt(s.substring(12, 14)));
    } catch (Exception e) {
      throw ApiFailure.invalid();
    }
    int[] w = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    int sum = 0;
    for (int i = 0; i < 17; i++) sum += (s.charAt(i) - '0') * w[i];
    if ("10X98765432".charAt(sum % 11) != s.charAt(17)) throw ApiFailure.invalid();
    return s;
  }

  /** 按Unicode字符数量脱敏姓名。 */
  public static String maskedName(String name) {
    int n = name.codePointCount(0, name.length());
    return name.substring(0, name.offsetByCodePoints(0, 1)) + "*".repeat(Math.max(1, n - 1));
  }

  /** 保留证件尾号，其余字符脱敏。 */
  public static String maskedCard(String card) {
    return "*".repeat(14) + card.substring(14);
  }
}
